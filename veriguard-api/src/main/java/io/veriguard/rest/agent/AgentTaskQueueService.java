package io.veriguard.rest.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory mock task queue for the Veriguard Agent (C1) Mode A polling path.
 *
 * <p><strong>SCAFFOLD MODE — C1-Platform-1</strong>:
 *
 * <ul>
 *   <li>Tasks are stored in a {@link ConcurrentHashMap} keyed by {@code agent_id → List<Task>}
 *   <li>Results are stored in a separate map for tests to inspect, no DB write
 *   <li>NO inject_execution / execution_traces wiring (that is C1-Platform-2 scope)
 * </ul>
 *
 * <p>TODO C1-Platform-2 — replace this with:
 *
 * <ul>
 *   <li>JPA {@code Inject} / {@code InjectExecution} lookups filtered by agent.id + capabilities
 *   <li>{@code execution_traces} write on result POST
 *   <li>{@code @RBAC} on AGENT for read-side (or signature-only auth)
 * </ul>
 *
 * <p>Test-only methods {@link #enqueueTask} / {@link #drainResults} are kept package-private to
 * avoid leaking into production code paths.
 */
@Service
public class AgentTaskQueueService {

  private final Map<String, List<AgentDtos.AgentTask>> pendingByAgentId = new ConcurrentHashMap<>();

  private final Map<String, ReceivedResult> receivedResults = new ConcurrentHashMap<>();

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
   * @return {@code true} if accepted (always accepted in scaffold mode — caller may store audit)
   */
  public boolean acceptResult(String taskId, String agentId, AgentDtos.ResultInput result) {
    receivedResults.put(taskId, new ReceivedResult(taskId, agentId, result, Instant.now()));
    return true;
  }

  /** Test hook — enqueue a task into the mock queue. */
  void enqueueTask(String agentId, AgentDtos.AgentTask task) {
    pendingByAgentId.computeIfAbsent(agentId, k -> new ArrayList<>()).add(task);
  }

  /** Test hook — inspect received results. */
  Optional<ReceivedResult> findResult(String taskId) {
    return Optional.ofNullable(receivedResults.get(taskId));
  }

  /** Test hook — clear all state (used in @DirtiesContext flows). */
  void clearAll() {
    pendingByAgentId.clear();
    receivedResults.clear();
  }

  Collection<ReceivedResult> allResults() {
    return receivedResults.values();
  }

  /** Snapshot of an accepted result + arrival timestamp. */
  public record ReceivedResult(
      String taskId, String agentId, AgentDtos.ResultInput input, Instant receivedAt) {}
}
