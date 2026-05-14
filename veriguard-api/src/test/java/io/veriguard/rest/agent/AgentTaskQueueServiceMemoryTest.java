package io.veriguard.rest.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.rest.agent.AgentTaskQueueService.ReceivedResult;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Memory-leak regression tests for {@link AgentTaskQueueService} (Important #2 fix).
 *
 * <p>Locks the two bounding invariants:
 *
 * <ul>
 *   <li>{@code receivedResults} is drained on first {@link AgentTaskQueueService#awaitResult}
 *       (one-shot race buffer; second await registers a fresh future instead of returning the same
 *       stale result).
 *   <li>{@code pendingFutures} is auto-evicted by the TTL scanner once an entry exceeds {@link
 *       AgentTaskQueueService#PENDING_FUTURE_MAX_AGE}; the abandoned future is exceptionally
 *       completed with {@link TimeoutException}.
 * </ul>
 */
class AgentTaskQueueServiceMemoryTest {

  private AgentTaskQueueService service;

  @BeforeEach
  void setUp() {
    service = new AgentTaskQueueService();
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  @DisplayName("receivedResults: 第一次 awaitResult 后 drain，第二次 awaitResult 不会重复 return 同一结果")
  void receivedResults_evictsAfterAwaitConsumed() throws Exception {
    String taskId = "T-race-1";
    AgentDtos.ResultInput input =
        new AgentDtos.ResultInput(
            "completed", 0, "ok", "", "2026-05-14T10:00:00Z", "2026-05-14T10:00:05Z", null);

    // Race path: result arrives BEFORE awaitResult registers a future.
    service.acceptResult(taskId, "agent-1", input);
    assertThat(service.receivedResultBufferCount()).isEqualTo(1);

    // First awaitResult drains the race buffer.
    CompletableFuture<ReceivedResult> first = service.awaitResult(taskId);
    assertThat(first.isDone()).isTrue();
    assertThat(first.get().input()).isEqualTo(input);
    assertThat(service.receivedResultBufferCount())
        .as("race buffer must drain to 0 after first awaitResult")
        .isEqualTo(0);

    // Second awaitResult must NOT return the same stale result — it should register a
    // fresh pending future awaiting the NEXT acceptResult call.
    CompletableFuture<ReceivedResult> second = service.awaitResult(taskId);
    assertThat(second.isDone())
        .as("second awaitResult on a consumed task must register a fresh pending future")
        .isFalse();
    assertThat(service.pendingFutureCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("pendingFutures: 超过 max age 的 entry 被 TTL 扫描 evict 并 exceptionally complete")
  void pendingFutures_evictsAfterMaxAge() {
    String taskId = "T-leak-1";
    // Register a future and never consume it (mirrors a caller that timed out at
    // future.get(30s) without notifying the queue).
    CompletableFuture<ReceivedResult> future = service.awaitResult(taskId);
    assertThat(service.pendingFutureCount()).isEqualTo(1);
    assertThat(future.isDone()).isFalse();

    // Drive the TTL scan with a future-dated cutoff: every entry created before "now+1s"
    // qualifies as expired. The test-only hook avoids waiting 5 minutes.
    service.evictPendingFuturesOlderThan(Instant.now().plusSeconds(1));

    assertThat(service.pendingFutureCount())
        .as("expired entry must be evicted from pendingFutures")
        .isEqualTo(0);
    assertThat(future.isDone()).as("evicted future must be completed (exceptionally)").isTrue();
    assertThatThrownBy(() -> future.get(100, TimeUnit.MILLISECONDS))
        .as("evicted future must complete with TimeoutException for clean caller signaling")
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  @DisplayName("pendingFutures: 未过期的 entry 不会被 evict（保护正常 in-flight 任务）")
  void pendingFutures_doesNotEvict_youngEntries() {
    String taskId = "T-young-1";
    CompletableFuture<ReceivedResult> future = service.awaitResult(taskId);
    assertThat(service.pendingFutureCount()).isEqualTo(1);

    // Cutoff in the past — no entry should qualify as expired.
    service.evictPendingFuturesOlderThan(Instant.now().minusSeconds(60));

    assertThat(service.pendingFutureCount())
        .as("entry younger than cutoff must remain in pendingFutures")
        .isEqualTo(1);
    assertThat(future.isDone()).isFalse();
  }

  @Test
  @DisplayName("acceptResult: 当有 pending future 时不写 receivedResults（避免 30k 永驻泄漏）")
  void acceptResult_doesNotPollute_receivedResults_whenFutureWaiting() throws Exception {
    String taskId = "T-normal-1";
    AgentDtos.ResultInput input =
        new AgentDtos.ResultInput(
            "completed", 0, "ok", "", "2026-05-14T10:00:00Z", "2026-05-14T10:00:05Z", null);

    // Normal path: awaitResult registers a future FIRST, then result arrives.
    CompletableFuture<ReceivedResult> future = service.awaitResult(taskId);
    assertThat(service.pendingFutureCount()).isEqualTo(1);

    service.acceptResult(taskId, "agent-1", input);

    // Future completes, pendingFutures drains, AND receivedResults stays empty
    // (no permanent storage of every result — this is the 30k×N leak we fixed).
    assertThat(future.isDone()).isTrue();
    assertThat(future.get().input()).isEqualTo(input);
    assertThat(service.pendingFutureCount()).isEqualTo(0);
    assertThat(service.receivedResultBufferCount())
        .as("in normal path receivedResults stays empty — no permanent storage")
        .isEqualTo(0);
  }
}
