package io.veriguard.rest.agent;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * In-memory mock task queue for the Veriguard Agent (C1) Mode A polling path.
 *
 * <p><strong>SCAFFOLD MODE — C1-Platform-1 / C1-Platform-2</strong>:
 *
 * <ul>
 *   <li>Tasks are stored in a {@link ConcurrentHashMap} keyed by {@code agent_id → List<Task>}
 *   <li>C1-Platform-2 adds {@link #enqueue} (public) + {@link #awaitResult} (CompletableFuture
 *       resolution) so the 4 executor dispatch services ({@link
 *       io.veriguard.injectors.web_attack.service.WebAttackDispatchService} etc.) can dispatch +
 *       block-with-timeout on the agent result without owning a separate queue
 *   <li>NO inject_execution / execution_traces wiring (that is C1-Platform-3 scope)
 * </ul>
 *
 * <p><strong>Memory bounding (Important #2 fix)</strong>: {@code receivedResults} is a transient
 * one-shot race buffer — entries land there only when {@link #acceptResult} arrives before {@link
 * #awaitResult} registers a future. The first {@link #awaitResult} for that {@code taskId} drains
 * the entry. {@code pendingFutures} is bounded by a periodic TTL scan that evicts (and
 * exceptionally completes) abandoned futures whose callers timed-out without consuming them.
 *
 * <p>TODO C1-Platform-3 — replace this with:
 *
 * <ul>
 *   <li>JPA {@code Inject} / {@code InjectExecution} lookups filtered by agent.id + capabilities
 *   <li>{@code execution_traces} write on result POST
 *   <li>{@code @RBAC} on AGENT for read-side (or signature-only auth)
 * </ul>
 */
@Service
@Slf4j
public class AgentTaskQueueService {

  /** Max age for a {@code pendingFutures} entry before the TTL scan evicts it. */
  static final Duration PENDING_FUTURE_MAX_AGE = Duration.ofMinutes(5);

  /** TTL scan period — wakes up every minute and evicts entries older than the max age. */
  static final Duration TTL_SCAN_PERIOD = Duration.ofMinutes(1);

  private final Map<String, List<AgentDtos.AgentTask>> pendingByAgentId = new ConcurrentHashMap<>();

  /**
   * Race-only one-shot buffer for results that arrived before {@link #awaitResult} registered a
   * future. First {@link #awaitResult} drains the entry (see {@link #awaitResult} {@code remove}).
   * Steady-state size: 0; transient size bounded by in-flight race window.
   */
  private final Map<String, ReceivedResult> receivedResults = new ConcurrentHashMap<>();

  /**
   * Pending result futures keyed by task_id — dispatch services {@link #enqueue} a task then
   * register a future via {@link #awaitResult}; when the agent posts a result, {@link
   * #acceptResult} completes the matching future. Bounded by {@link #PENDING_FUTURE_MAX_AGE} TTL
   * scan so caller-side timeouts never leak entries.
   */
  private final Map<String, PendingFuture> pendingFutures = new ConcurrentHashMap<>();

  private final ScheduledExecutorService ttlScanner;

  public AgentTaskQueueService() {
    this.ttlScanner =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "agent-task-queue-ttl-scan");
              t.setDaemon(true);
              return t;
            });
    this.ttlScanner.scheduleAtFixedRate(
        this::evictExpiredPendingFutures,
        TTL_SCAN_PERIOD.toMillis(),
        TTL_SCAN_PERIOD.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Fetch (and drain) pending tasks for an agent. Idempotent only within a single call — once
   * returned, tasks are removed from queue. This mirrors the upstream OpenAEV manage_jobs "claim +
   * execute" semantics.
   */
  public List<AgentDtos.AgentTask> drainTasks(String agentId) {
    if (agentId == null) {
      return List.of();
    }
    List<AgentDtos.AgentTask> queue = pendingByAgentId.remove(agentId);
    return queue == null ? List.of() : List.copyOf(queue);
  }

  /**
   * Accept a task execution result.
   *
   * <p>Side-effect: if a dispatch service is currently {@code awaitResult}-ing on this {@code
   * taskId}, completes the matching {@link CompletableFuture} so {@code dispatch().get(timeout)}
   * returns. If no future is registered yet (race — result arrived first), the result is briefly
   * parked in {@link #receivedResults} for the first subsequent {@link #awaitResult} to drain.
   *
   * @return {@code true} if accepted (always accepted in scaffold mode — caller may store audit)
   */
  public boolean acceptResult(String taskId, String agentId, AgentDtos.ResultInput result) {
    ReceivedResult received = new ReceivedResult(taskId, agentId, result, Instant.now());
    PendingFuture pending = pendingFutures.remove(taskId);
    if (pending != null) {
      pending.future().complete(received);
    } else {
      // Race: result arrived before awaitResult registered a future. Park for the first
      // awaitResult to drain (one-shot — see awaitResult.remove). No future-less results
      // pile up because awaitResult always either pre-completes or registers a future.
      receivedResults.put(taskId, received);
    }
    return true;
  }

  /**
   * Enqueue a task for an agent (public — used by dispatch services). The agent will receive this
   * task on its next {@code /api/agent/poll}.
   */
  public void enqueue(String agentId, AgentDtos.AgentTask task) {
    if (agentId == null || task == null) {
      throw new IllegalArgumentException("agentId and task must not be null");
    }
    pendingByAgentId.computeIfAbsent(agentId, k -> new ArrayList<>()).add(task);
  }

  /**
   * Register a {@link CompletableFuture} to be completed when {@link #acceptResult} arrives for the
   * given {@code taskId}.
   *
   * <p>If the result has already arrived (race with a fast agent), the returned future is
   * pre-completed and the entry drained from the race buffer (one-shot).
   *
   * <p>The returned future is tracked with a creation timestamp; if the caller times out and never
   * consumes the result, the periodic TTL scan evicts and exceptionally completes the entry after
   * {@link #PENDING_FUTURE_MAX_AGE}.
   */
  public CompletableFuture<ReceivedResult> awaitResult(String taskId) {
    if (taskId == null) {
      throw new IllegalArgumentException("taskId must not be null");
    }
    // Drain (one-shot remove) any pre-arrived result. Subsequent awaitResult on the same
    // taskId will register a fresh future instead of returning the same stale result.
    ReceivedResult existing = receivedResults.remove(taskId);
    if (existing != null) {
      return CompletableFuture.completedFuture(existing);
    }
    return pendingFutures
        .computeIfAbsent(taskId, k -> new PendingFuture(new CompletableFuture<>(), Instant.now()))
        .future();
  }

  /**
   * Evict pending futures older than {@link #PENDING_FUTURE_MAX_AGE}. Called periodically by the
   * TTL scanner so caller-side timeouts (e.g., {@code HttpInjectExecutor.get(30s)}) never leak
   * abandoned futures into the map.
   *
   * <p>Package-private for test access.
   */
  void evictExpiredPendingFutures() {
    evictPendingFuturesOlderThan(Instant.now().minus(PENDING_FUTURE_MAX_AGE));
  }

  /**
   * Evict pending futures with {@code createdAt < cutoff}. Package-private for tests so they can
   * deterministically drive evictions without sleeping for 5 minutes.
   *
   * <p>Any evicted future is exceptionally completed with {@link TimeoutException} so callers
   * blocked on {@code future.get(timeout)} see a clean error instead of hanging indefinitely.
   */
  void evictPendingFuturesOlderThan(Instant cutoff) {
    try {
      pendingFutures.forEach(
          (taskId, pending) -> {
            if (pending.createdAt().isBefore(cutoff)) {
              PendingFuture removed = pendingFutures.remove(taskId);
              if (removed != null) {
                removed
                    .future()
                    .completeExceptionally(
                        new TimeoutException(
                            "Pending result future for task "
                                + taskId
                                + " expired after "
                                + PENDING_FUTURE_MAX_AGE));
                log.debug(
                    "AgentTaskQueueService: evicted abandoned pending future task_id={} (age>{})",
                    taskId,
                    PENDING_FUTURE_MAX_AGE);
              }
            }
          });
    } catch (RuntimeException ex) {
      // Never let scanner crashes kill the scheduled task.
      log.warn("AgentTaskQueueService TTL scan failed", ex);
    }
  }

  @PreDestroy
  void shutdown() {
    ttlScanner.shutdownNow();
  }

  /**
   * Test hook — enqueue a task into the mock queue (package-private legacy entry used by some
   * pre-C1-Platform-2 tests that pre-dated the public {@link #enqueue}).
   */
  void enqueueTask(String agentId, AgentDtos.AgentTask task) {
    enqueue(agentId, task);
  }

  /**
   * Test hook — inspect race-buffer results (results that arrived before any awaitResult). Note:
   * once {@link #awaitResult} drains an entry, this returns {@link Optional#empty()}.
   */
  Optional<ReceivedResult> findResult(String taskId) {
    return Optional.ofNullable(receivedResults.get(taskId));
  }

  /** Test hook — count of pending futures (for memory-leak regression tests). */
  int pendingFutureCount() {
    return pendingFutures.size();
  }

  /** Test hook — count of race-buffer entries (for memory-leak regression tests). */
  int receivedResultBufferCount() {
    return receivedResults.size();
  }

  /** Test hook — clear all state (used in @DirtiesContext flows). */
  void clearAll() {
    pendingByAgentId.clear();
    receivedResults.clear();
    pendingFutures.clear();
  }

  Collection<ReceivedResult> allResults() {
    return receivedResults.values();
  }

  /** Snapshot of an accepted result + arrival timestamp. */
  public record ReceivedResult(
      String taskId, String agentId, AgentDtos.ResultInput input, Instant receivedAt) {}

  /** Pending future + creation timestamp for TTL bookkeeping. */
  private record PendingFuture(CompletableFuture<ReceivedResult> future, Instant createdAt) {}
}
