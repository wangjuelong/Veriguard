package io.veriguard.combination.scheduler;

import com.google.common.util.concurrent.RateLimiter;
import io.veriguard.combination.CombinationInstance;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流 + 并发派发器 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>从一个 {@link Iterator} 拉组合实例，按 token-bucket 限流（Guava {@link RateLimiter}）控制
 * 每秒投放数，配合固定大小线程池实现并发执行.
 *
 * <p>支持暂停 / 恢复 / 取消：
 * <ul>
 *   <li>{@link #pause()} —— 派发线程不再 acquire / dispatch，但已派发任务继续跑完</li>
 *   <li>{@link #resume()} —— 重置 pause flag</li>
 *   <li>{@link #cancel()} —— 派发线程立即退出循环；executor 线程池调 {@link ExecutorService#shutdownNow()} 中断当前任务</li>
 * </ul>
 *
 * <p>非 Spring Bean —— 每个 run 单独 new 一个；CombinationScheduler 负责生命周期.
 */
@Slf4j
public class RateLimitedDispatcher {

  private final String runId;
  private final RateLimiter rateLimiter;
  private final ExecutorService workerPool;
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicInteger inflight = new AtomicInteger(0);
  private final AtomicLong dispatched = new AtomicLong(0);

  /**
   * @param runId 任务 id（仅用于日志 / 线程命名）
   * @param ratePerSecond 限流 req/s（&gt; 0）
   * @param concurrency 并发度（&gt; 0；固定线程池大小）
   */
  public RateLimitedDispatcher(String runId, int ratePerSecond, int concurrency) {
    if (ratePerSecond <= 0) {
      throw new IllegalArgumentException("ratePerSecond must be > 0, got " + ratePerSecond);
    }
    if (concurrency <= 0) {
      throw new IllegalArgumentException("concurrency must be > 0, got " + concurrency);
    }
    this.runId = runId;
    this.rateLimiter = RateLimiter.create(ratePerSecond);
    ThreadFactory factory =
        new ThreadFactory() {
          private final AtomicInteger seq = new AtomicInteger(0);

          @Override
          public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "combination-worker-" + runId + "-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
          }
        };
    this.workerPool = Executors.newFixedThreadPool(concurrency, factory);
  }

  /**
   * 同步运行：阻塞直到迭代器耗尽或被取消.
   *
   * @param iterator 待派发实例流（由 generator 提供）
   * @param onInstance 单实例处理回调（在 worker 线程内调用）
   */
  public void run(Iterator<CombinationInstance> iterator, Consumer<CombinationInstance> onInstance) {
    while (iterator.hasNext() && !cancelled.get()) {
      // 暂停轮询
      while (paused.get() && !cancelled.get()) {
        sleepQuietly(200);
      }
      if (cancelled.get()) {
        break;
      }
      CombinationInstance instance = iterator.next();
      rateLimiter.acquire();
      if (cancelled.get()) {
        break;
      }
      inflight.incrementAndGet();
      dispatched.incrementAndGet();
      try {
        workerPool.execute(
            () -> {
              try {
                onInstance.accept(instance);
              } finally {
                inflight.decrementAndGet();
              }
            });
      } catch (RuntimeException e) {
        inflight.decrementAndGet();
        log.error("Dispatcher run={} failed to enqueue instance: {}", runId, e.getMessage(), e);
      }
    }
    // 等待 inflight 排空（取消时跳过等待）
    if (!cancelled.get()) {
      awaitDrain();
    }
  }

  /**
   * 关闭线程池（阻塞最多 30s 等其完成）.
   */
  public void shutdown() {
    workerPool.shutdown();
    try {
      if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
        log.warn("Dispatcher run={} did not terminate cleanly in 30s; forcing shutdownNow", runId);
        workerPool.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      workerPool.shutdownNow();
    }
  }

  public void pause() {
    paused.set(true);
  }

  public void resume() {
    paused.set(false);
  }

  public void cancel() {
    cancelled.set(true);
    paused.set(false);
    workerPool.shutdownNow();
  }

  public boolean isPaused() {
    return paused.get();
  }

  public boolean isCancelled() {
    return cancelled.get();
  }

  public int inflight() {
    return inflight.get();
  }

  public long dispatched() {
    return dispatched.get();
  }

  /** 暴露给测试观测限流速率. */
  public double getCurrentRate() {
    return rateLimiter.getRate();
  }

  /**
   * 等待所有 inflight 任务结束（轮询；取消由 cancel() 处理）.
   */
  private void awaitDrain() {
    while (inflight.get() > 0 && !cancelled.get()) {
      sleepQuietly(100);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 测试辅助：用 List 作为来源 + 同步运行.
   */
  public void runAll(List<CombinationInstance> instances, Consumer<CombinationInstance> onInstance) {
    run(instances.iterator(), onInstance);
  }
}
