package io.veriguard.rest.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory mock task queue for the Veriguard Agent (C1) Mode A polling path.
 *
 * <p><strong>SCAFFOLD MODE — C1-Platform-1 / C1-Platform-2</strong>:
 *
 * <ul>
 *   <li>Tasks are stored in a {@link ConcurrentHashMap} keyed by {@code agent_id → List<Task>}
 *   <li>Results are stored in a separate map for tests to inspect, no DB write
 *   <li>C1-Platform-2 adds {@link #enqueue} (public) + {@link #awaitResult} (CompletableFuture
 *       resolution) so the 4 executor dispatch services ({@link
 *       io.veriguard.injectors.web_attack.service.WebAttackDispatchService} etc.) can dispatch +
 *       block-with-timeout on the agent result without owning a separate queue
 *   <li>NO inject_execution / execution_traces wiring (that is C1-Platform-3 scope)
 * </ul>
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
public class AgentTaskQueueService {

  private final Map<String, List<AgentDtos.AgentTask>> pendingByAgentId = new ConcurrentHashMap<>();

  private final Map<String, ReceivedResult> receivedResults = new ConcurrentHashMap<>();

  /**
   * Pending result futures keyed by task_id — dispatch services {@link #enqueue} a task then
   * register a future via {@link #awaitResult}; when the agent posts a result, {@link
   * #acceptResult} completes the matching future.
   */
  private final Map<String, CompletableFuture<ReceivedResult>> pendingFutures =
      new ConcurrentHashMap<>();

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
   * returns.
   *
   * @return {@code true} if accepted (always accepted in scaffold mode — caller may store audit)
   */
  public boolean acceptResult(String taskId, String agentId, AgentDtos.ResultInput result) {
    ReceivedResult received = new ReceivedResult(taskId, agentId, result, Instant.now());
    receivedResults.put(taskId, received);
    CompletableFuture<ReceivedResult> future = pendingFutures.remove(taskId);
    if (future != null) {
      future.complete(received);
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
   * pre-completed.
   */
  public CompletableFuture<ReceivedResult> awaitResult(String taskId) {
    if (taskId == null) {
      throw new IllegalArgumentException("taskId must not be null");
    }
    ReceivedResult existing = receivedResults.get(taskId);
    if (existing != null) {
      return CompletableFuture.completedFuture(existing);
    }
    return pendingFutures.computeIfAbsent(taskId, k -> new CompletableFuture<>());
  }

  /**
   * Test hook — enqueue a task into the mock queue (package-private legacy entry used by some
   * pre-C1-Platform-2 tests that pre-dated the public {@link #enqueue}).
   */
  void enqueueTask(String agentId, AgentDtos.AgentTask task) {
    enqueue(agentId, task);
  }

  /** Test hook — inspect received results. */
  Optional<ReceivedResult> findResult(String taskId) {
    return Optional.ofNullable(receivedResults.get(taskId));
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
}
