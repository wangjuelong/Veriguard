package io.veriguard.combination.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.combination.CombinationInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimitedDispatcherTest {

  @Test
  void rate_limiter_constrains_throughput() {
    int instanceCount = 30;
    int rate = 10; // 10 req/s → ≥ 2.9s for 30 instances (RateLimiter allows initial burst of 1)
    int concurrency = 4;

    List<CombinationInstance> instances = newInstances(instanceCount);
    AtomicInteger processed = new AtomicInteger(0);
    RateLimitedDispatcher dispatcher = new RateLimitedDispatcher("test-run", rate, concurrency);
    try {
      long start = System.nanoTime();
      dispatcher.runAll(instances, i -> processed.incrementAndGet());
      long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
      assertThat(processed.get()).isEqualTo(instanceCount);
      // Minimum elapsed for 30 dispatches at 10 req/s ≈ 2.9s (allow generous margin)
      assertThat(elapsedMillis).isGreaterThanOrEqualTo(2500);
      assertThat(dispatcher.dispatched()).isEqualTo(instanceCount);
    } finally {
      dispatcher.shutdown();
    }
  }

  @Test
  void cancel_stops_dispatch_loop_immediately() throws InterruptedException {
    int instanceCount = 1000;
    int rate = 50;
    List<CombinationInstance> instances = newInstances(instanceCount);
    AtomicInteger processed = new AtomicInteger(0);
    RateLimitedDispatcher dispatcher = new RateLimitedDispatcher("cancel-run", rate, 4);
    Thread runner = new Thread(() -> dispatcher.runAll(instances, i -> {
      try {
        Thread.sleep(20);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      processed.incrementAndGet();
    }));
    runner.start();
    Thread.sleep(300);
    dispatcher.cancel();
    runner.join(5000);
    assertThat(runner.isAlive()).isFalse();
    assertThat(dispatcher.isCancelled()).isTrue();
    assertThat(processed.get()).isLessThan(instanceCount);
    dispatcher.shutdown();
  }

  @Test
  void pause_resume_round_trip() throws InterruptedException {
    int instanceCount = 100;
    int rate = 50;
    List<CombinationInstance> instances = newInstances(instanceCount);
    AtomicInteger processed = new AtomicInteger(0);
    RateLimitedDispatcher dispatcher = new RateLimitedDispatcher("pause-run", rate, 4);
    Thread runner = new Thread(() -> dispatcher.runAll(instances, i -> processed.incrementAndGet()));
    runner.start();
    Thread.sleep(200);
    dispatcher.pause();
    int processedBeforePause = processed.get();
    Thread.sleep(500);
    int processedDuringPause = processed.get();
    // Pause should halt new dispatch; allow a small tail-off for inflight
    assertThat(processedDuringPause - processedBeforePause).isLessThan(10);
    dispatcher.resume();
    runner.join(10_000);
    assertThat(processed.get()).isEqualTo(instanceCount);
    dispatcher.shutdown();
  }

  @Test
  void invalid_rate_throws() {
    assertThatThrownBy(() -> new RateLimitedDispatcher("r", 0, 4))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RateLimitedDispatcher("r", -1, 4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalid_concurrency_throws() {
    assertThatThrownBy(() -> new RateLimitedDispatcher("r", 10, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RateLimitedDispatcher("r", 10, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void current_rate_exposes_configured_value() {
    RateLimitedDispatcher dispatcher = new RateLimitedDispatcher("rate-check", 50, 4);
    try {
      assertThat(dispatcher.getCurrentRate()).isEqualTo(50.0);
    } finally {
      dispatcher.shutdown();
    }
  }

  private static List<CombinationInstance> newInstances(int n) {
    List<CombinationInstance> list = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      list.add(
          new CombinationInstance(
              "run-1",
              "sql_injection:dim-" + i,
              "sql_injection",
              "dim-" + i,
              "asset-1",
              "payload-" + i,
              "payload-" + i));
    }
    return list;
  }
}
