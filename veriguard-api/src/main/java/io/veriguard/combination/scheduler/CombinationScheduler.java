package io.veriguard.combination.scheduler;

import io.veriguard.combination.AttackCombinationGenerator;
import io.veriguard.combination.CombinationInstance;
import io.veriguard.combination.executor.CombinationExecutorRouter;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.database.model.combination.AttackCombinationResult;
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.repository.AttackCombinationResultRepository;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.BypassDimensionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 攻击组合任务调度器 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>职责：
 * <ul>
 *   <li>给定 run id，加载 dimensions / asset_ids，预生成 result 行（pending）</li>
 *   <li>启动 {@link RateLimitedDispatcher} 拉 generator 流并派发到 {@link CombinationExecutorRouter}</li>
 *   <li>累积每 N 个结果批量 flush（默认 100），减少 DB 往返</li>
 *   <li>暂停 / 恢复 / 取消由 run id → dispatcher 映射承担</li>
 *   <li>重试（指数退避 1/5/30s）：result.retry_count++ 直到 max_retries 后标 failed</li>
 * </ul>
 *
 * <p>不引入 RabbitMQ —— 在进程内用 fixed-pool 派发即可满足 ≤500 req/s 目标；
 * 真要跨进程 scale-out 时再切回 BatchQueueService.
 */
@Slf4j
@Component
public class CombinationScheduler {

  /** 重试退避（秒）—— 索引 = 已重试次数. */
  private static final int[] RETRY_BACKOFF_SECONDS = {1, 5, 30};

  /** 批量 flush 阈值. */
  private static final int RESULT_FLUSH_BATCH = 100;

  private final AttackCombinationRunRepository runRepository;
  private final AttackCombinationResultRepository resultRepository;
  private final BypassDimensionRepository dimensionRepository;
  private final AttackCombinationGenerator generator;
  private final CombinationExecutorRouter router;
  private final TransactionTemplate transactionTemplate;
  private final int runnerPoolSize;

  /** 当前正在运行的任务 dispatcher（runId -&gt; dispatcher）. */
  private final Map<String, RateLimitedDispatcher> active = new ConcurrentHashMap<>();

  /** 全局 runner 线程池（每个 run 占一个 runner 线程）. */
  private final ExecutorService runnerPool;

  public CombinationScheduler(
      AttackCombinationRunRepository runRepository,
      AttackCombinationResultRepository resultRepository,
      BypassDimensionRepository dimensionRepository,
      AttackCombinationGenerator generator,
      CombinationExecutorRouter router,
      TransactionTemplate transactionTemplate,
      @Value("${veriguard.combination.scheduler.runner-pool-size:4}") int runnerPoolSize) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.dimensionRepository = dimensionRepository;
    this.generator = generator;
    this.router = router;
    this.transactionTemplate = transactionTemplate;
    this.runnerPoolSize = Math.max(1, runnerPoolSize);
    this.runnerPool = Executors.newFixedThreadPool(this.runnerPoolSize);
  }

  /**
   * 异步启动任务（pending -&gt; running）.
   *
   * @return true 如果成功转入 running；false 如果状态不允许
   */
  public boolean start(String runId) {
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    if (run.getStatus() != AttackCombinationRunStatus.pending) {
      log.warn("Cannot start run {} from status {}", runId, run.getStatus());
      return false;
    }
    transactionTemplate.executeWithoutResult(
        status -> {
          run.setStatus(AttackCombinationRunStatus.running);
          run.setStartedAt(Instant.now());
          runRepository.save(run);
        });
    runnerPool.submit(() -> safeExecute(runId));
    return true;
  }

  /** 暂停 running -&gt; paused. */
  public boolean pause(String runId) {
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    if (!run.getStatus().canPause()) {
      return false;
    }
    RateLimitedDispatcher dispatcher = active.get(runId);
    if (dispatcher != null) {
      dispatcher.pause();
    }
    transactionTemplate.executeWithoutResult(
        status -> {
          run.setStatus(AttackCombinationRunStatus.paused);
          runRepository.save(run);
        });
    return true;
  }

  /** 恢复 paused -&gt; running. */
  public boolean resume(String runId) {
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    if (!run.getStatus().canResume()) {
      return false;
    }
    transactionTemplate.executeWithoutResult(
        status -> {
          run.setStatus(AttackCombinationRunStatus.running);
          runRepository.save(run);
        });
    RateLimitedDispatcher dispatcher = active.get(runId);
    if (dispatcher != null) {
      dispatcher.resume();
    }
    return true;
  }

  /** 取消（pending/running/paused -&gt; cancelled）. */
  public boolean cancel(String runId) {
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    if (!run.getStatus().canCancel()) {
      return false;
    }
    RateLimitedDispatcher dispatcher = active.get(runId);
    if (dispatcher != null) {
      dispatcher.cancel();
    }
    transactionTemplate.executeWithoutResult(
        status -> {
          run.setStatus(AttackCombinationRunStatus.cancelled);
          run.setCompletedAt(Instant.now());
          runRepository.save(run);
        });
    return true;
  }

  /** 测试 / 同步运行入口：阻塞直到 run 完成. */
  public void runSynchronously(String runId) {
    safeExecute(runId);
  }

  // ============================================================
  // 内部
  // ============================================================

  private void safeExecute(String runId) {
    try {
      executeRun(runId);
    } catch (Exception e) {
      log.error("Run {} failed with unexpected error: {}", runId, e.getMessage(), e);
      transactionTemplate.executeWithoutResult(
          status ->
              runRepository
                  .findById(runId)
                  .ifPresent(
                      r -> {
                        if (!r.getStatus().isTerminal()) {
                          r.setStatus(AttackCombinationRunStatus.failed);
                          r.setCompletedAt(Instant.now());
                          runRepository.save(r);
                        }
                      }));
    } finally {
      active.remove(runId);
    }
  }

  private void executeRun(String runId) {
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    if (run.getStatus().isTerminal()) {
      log.info("Run {} already terminal ({}), skipping", runId, run.getStatus());
      return;
    }
    if (run.getStatus() == AttackCombinationRunStatus.pending) {
      // For synchronous tests bypassing start()
      transactionTemplate.executeWithoutResult(
          s -> {
            run.setStatus(AttackCombinationRunStatus.running);
            run.setStartedAt(Instant.now());
            runRepository.save(run);
          });
    }

    List<BypassDimension> dimensions = loadDimensionsInOrder(run.getBypassDimensionIds());
    List<String> assetIds = run.getAssetIds();
    Iterator<CombinationInstance> instances = generator.generate(run, dimensions, assetIds).iterator();

    RateLimitedDispatcher dispatcher =
        new RateLimitedDispatcher(runId, run.getRateLimitPerSecond(), run.getConcurrency());
    active.put(runId, dispatcher);
    try {
      List<AttackCombinationResult> batch = new CopyOnWriteArrayList<>();
      dispatcher.run(
          instances,
          instance -> {
            AttackCombinationResult result = dispatchWithRetry(instance, run.getMaxRetries());
            batch.add(result);
            if (batch.size() >= RESULT_FLUSH_BATCH) {
              flushBatch(runId, batch);
            }
          });
      // Drain remaining
      if (!batch.isEmpty()) {
        flushBatch(runId, batch);
      }
    } finally {
      dispatcher.shutdown();
    }

    finalizeStatus(runId);
  }

  private List<BypassDimension> loadDimensionsInOrder(List<String> dimIds) {
    List<BypassDimension> result = new ArrayList<>(dimIds.size());
    for (String id : dimIds) {
      BypassDimension dim =
          dimensionRepository
              .findById(id)
              .orElseThrow(
                  () -> new IllegalArgumentException("Unknown bypass_dimension_id: " + id));
      result.add(dim);
    }
    return result;
  }

  /**
   * 派发 + 重试.
   *
   * <p>每次失败前 sleep 退避时间；总尝试次数 = 1 + maxRetries.
   */
  private AttackCombinationResult dispatchWithRetry(
      CombinationInstance instance, int maxRetries) {
    AttackCombinationResult result = new AttackCombinationResult();
    result.setRunId(instance.runId());
    result.setCombinationId(instance.combinationId());
    result.setBaseAttackType(instance.baseAttackType());
    result.setBypassDimensionId(instance.bypassDimensionId());
    result.setAssetId(instance.assetId());
    result.setPayloadSample(instance.payloadSample());

    int attempt = 0;
    Exception lastError = null;
    while (attempt <= maxRetries) {
      try {
        AttackCombinationHitState state = router.dispatch(instance);
        result.setHitState(state);
        result.setRetryCount(attempt);
        result.setExecutedAt(Instant.now());
        return result;
      } catch (RuntimeException e) {
        lastError = e;
        log.warn(
            "Combination dispatch attempt {} failed for instance {}: {}",
            attempt,
            instance.combinationId(),
            e.getMessage());
        attempt++;
        if (attempt <= maxRetries) {
          backoffSleep(attempt);
        }
      }
    }
    result.setHitState(AttackCombinationHitState.failed);
    result.setRetryCount(maxRetries);
    result.setExecutedAt(Instant.now());
    result.setErrorMessage(lastError == null ? "unknown" : lastError.getMessage());
    return result;
  }

  private void backoffSleep(int attempt) {
    int idx = Math.min(attempt - 1, RETRY_BACKOFF_SECONDS.length - 1);
    int seconds = RETRY_BACKOFF_SECONDS[idx];
    try {
      Thread.sleep(seconds * 1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void flushBatch(String runId, List<AttackCombinationResult> batch) {
    List<AttackCombinationResult> snapshot = new ArrayList<>(batch);
    batch.clear();
    transactionTemplate.executeWithoutResult(
        status -> {
          resultRepository.saveAll(snapshot);
          // 更新 run 进度
          runRepository
              .findById(runId)
              .ifPresent(
                  run -> {
                    int newCompleted = run.getCompletedCount() + snapshot.size();
                    int newFailed = run.getFailedCount();
                    for (AttackCombinationResult r : snapshot) {
                      if (r.getHitState() == AttackCombinationHitState.failed) {
                        newFailed++;
                      }
                    }
                    run.setCompletedCount(newCompleted);
                    run.setFailedCount(newFailed);
                    runRepository.save(run);
                  });
        });
  }

  private void finalizeStatus(String runId) {
    transactionTemplate.executeWithoutResult(
        status -> {
          Optional<AttackCombinationRun> opt = runRepository.findById(runId);
          if (opt.isEmpty()) {
            return;
          }
          AttackCombinationRun run = opt.get();
          if (run.getStatus().isTerminal()) {
            return;
          }
          if (run.getStatus() == AttackCombinationRunStatus.paused) {
            // 用户手动暂停 —— 不自动转 completed
            return;
          }
          run.setStatus(AttackCombinationRunStatus.completed);
          run.setCompletedAt(Instant.now());
          runRepository.save(run);
        });
  }
}
