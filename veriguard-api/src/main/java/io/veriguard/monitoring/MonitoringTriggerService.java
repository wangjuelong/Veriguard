package io.veriguard.monitoring;

import io.veriguard.coverage.CoverageRunner;
import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.model.monitoring.MonitoringJob;
import io.veriguard.database.model.monitoring.MonitoringRunHistory;
import io.veriguard.database.model.monitoring.MonitoringRunStatus;
import io.veriguard.database.repository.MonitoringJobRepository;
import io.veriguard.database.repository.MonitoringRunHistoryRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 监控触发服务 —— PR C4 招标 §3.2.
 *
 * <p>职责：把「Quartz 定时调用」「REST 立即触发」收敛成同一入口。每次触发：
 *
 * <ol>
 *   <li>用 {@link CoverageRunner#createRun(String)} 同步建一条 pending coverage_run
 *   <li>写一条 {@link MonitoringRunHistory}（status=triggered，关联 coverage_run_id）
 *   <li>用 {@link CoverageRunner#runAsync(String)} 触发异步执行（fire-and-forget）
 * </ol>
 *
 * <p>异步完成后由 {@link MonitoringHistoryUpdater} 扫描回填计数 + status=completed.
 * 触发本身失败（baseline 已删等）写一条 status=failed history。
 */
@Slf4j
@Component
public class MonitoringTriggerService {

  private final MonitoringJobRepository jobRepository;
  private final MonitoringRunHistoryRepository historyRepository;
  private final CoverageRunner coverageRunner;
  private final TransactionTemplate transactionTemplate;

  public MonitoringTriggerService(
      MonitoringJobRepository jobRepository,
      MonitoringRunHistoryRepository historyRepository,
      CoverageRunner coverageRunner,
      TransactionTemplate transactionTemplate) {
    this.jobRepository = jobRepository;
    this.historyRepository = historyRepository;
    this.coverageRunner = coverageRunner;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * 触发一次 monitoring_job. 如果触发失败（baseline 已删 / runner 抛异常），写一条 failed history.
   *
   * @return 新建的 history 记录
   */
  public MonitoringRunHistory trigger(MonitoringJob job) {
    Instant scheduledAt = Instant.now();
    MonitoringRunHistory history = new MonitoringRunHistory();
    history.setId(UUID.randomUUID().toString());
    history.setJobId(job.getId());
    history.setScheduledAt(scheduledAt);
    history.setStatus(MonitoringRunStatus.triggered);

    try {
      CoverageRun run =
          transactionTemplate.execute(s -> coverageRunner.createRun(job.getBaselineId()));
      if (run == null) {
        throw new IllegalStateException("coverage runner returned null run");
      }
      history.setCoverageRunId(run.getId());
      transactionTemplate.executeWithoutResult(
          s -> {
            historyRepository.save(history);
            MonitoringJob fresh = jobRepository.findById(job.getId()).orElse(job);
            fresh.setLastTriggeredAt(scheduledAt);
            jobRepository.save(fresh);
          });
      // fire-and-forget async 执行
      coverageRunner.runAsync(job.getBaselineId());
      log.info(
          "Monitoring job {} triggered coverage_run {} at {}",
          job.getId(),
          run.getId(),
          scheduledAt);
      return history;
    } catch (RuntimeException e) {
      log.warn(
          "Monitoring job {} trigger failed: {} (baseline={})",
          job.getId(),
          e.getMessage(),
          job.getBaselineId());
      history.setStatus(MonitoringRunStatus.failed);
      history.setErrorMessage(e.getMessage());
      history.setFinishedAt(Instant.now());
      transactionTemplate.executeWithoutResult(s -> historyRepository.save(history));
      return history;
    }
  }
}
