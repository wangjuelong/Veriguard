package io.veriguard.scheduler.jobs;

import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.model.coverage.CoverageRunStatus;
import io.veriguard.database.model.monitoring.MonitoringRunHistory;
import io.veriguard.database.model.monitoring.MonitoringRunStatus;
import io.veriguard.database.repository.CoverageRunRepository;
import io.veriguard.database.repository.MonitoringRunHistoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 监控历史回填 Job —— PR C4 招标 §3.2.
 *
 * <p>每分钟扫描 status=triggered 且 scheduled_at 已过期（&gt; 1min）的 history，
 * 检查关联 {@link CoverageRun}：
 *
 * <ul>
 *   <li>terminal（completed/failed/cancelled）→ 回填 4 计数 + status=completed
 *   <li>running/pending → 跳过（下次扫描再处理）
 *   <li>关联 coverage_run 已被删除 → status=failed，error_message 标记
 * </ul>
 *
 * <p>同时设置一个安全上界：scheduled_at &gt; 24h 仍未完成的 → 强制 status=failed (timeout)。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class MonitoringHistoryUpdaterJob implements Job {

  public static final String JOB_NAME = "MonitoringHistoryUpdaterJob";
  public static final String TRIGGER_NAME = "MonitoringHistoryUpdaterTrigger";

  /** scheduled_at 距今 ≥ 1min 才考虑（避免与刚触发的 run 抢锁）. */
  private static final long MIN_AGE_SECONDS = 60L;

  /** scheduled_at 距今 &gt; 24h 仍 triggered → 强制 failed. */
  static final long FORCED_FAIL_HOURS = 24L;

  private final MonitoringRunHistoryRepository historyRepository;
  private final CoverageRunRepository coverageRunRepository;
  private final TransactionTemplate transactionTemplate;

  public MonitoringHistoryUpdaterJob(
      MonitoringRunHistoryRepository historyRepository,
      CoverageRunRepository coverageRunRepository,
      TransactionTemplate transactionTemplate) {
    this.historyRepository = historyRepository;
    this.coverageRunRepository = coverageRunRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public void execute(JobExecutionContext context) {
    sweepAndUpdate();
  }

  /** 测试 / 显式调用入口；返回本次回填 / 完结的 history 数. */
  public int sweepAndUpdate() {
    Instant now = Instant.now();
    Instant cutoff = now.minusSeconds(MIN_AGE_SECONDS);
    List<MonitoringRunHistory> pending =
        historyRepository.findAllByStatusAndScheduledAtBefore(
            MonitoringRunStatus.triggered, cutoff);
    if (pending.isEmpty()) {
      return 0;
    }
    int updated = 0;
    for (MonitoringRunHistory h : pending) {
      if (updateOne(h, now)) {
        updated++;
      }
    }
    if (updated > 0) {
      log.info("MonitoringHistoryUpdater finalized {} of {} pending histories", updated, pending.size());
    }
    return updated;
  }

  private boolean updateOne(MonitoringRunHistory h, Instant now) {
    String coverageRunId = h.getCoverageRunId();
    if (coverageRunId == null) {
      // 没有关联 run（理论不该存在 triggered 状态），保险起见标 failed
      finalizeAsFailed(h, "missing coverage_run_id", now);
      return true;
    }
    Optional<CoverageRun> runOpt = coverageRunRepository.findById(coverageRunId);
    if (runOpt.isEmpty()) {
      finalizeAsFailed(h, "linked coverage_run was deleted", now);
      return true;
    }
    CoverageRun run = runOpt.get();
    if (!run.getStatus().isTerminal()) {
      // 仍 running / pending —— 检查是否超过 forced-fail 上界
      long ageHours = ChronoUnit.HOURS.between(h.getScheduledAt(), now);
      if (ageHours >= FORCED_FAIL_HOURS) {
        finalizeAsFailed(
            h,
            String.format("coverage_run still %s after %dh", run.getStatus(), ageHours),
            now);
        return true;
      }
      return false;
    }
    // run 已终结 → 回填计数
    transactionTemplate.executeWithoutResult(
        s -> {
          MonitoringRunHistory fresh = historyRepository.findById(h.getId()).orElse(h);
          fresh.setHitCount(run.getHitCount());
          fresh.setMissCount(run.getMissCount());
          fresh.setTimeoutCount(run.getTimeoutCount());
          fresh.setOutOfScopeCount(run.getOutOfScopeCount());
          fresh.setTotalCount(run.getTotalCells());
          fresh.setFinishedAt(run.getFinishedAt() != null ? run.getFinishedAt() : now);
          if (run.getStatus() == CoverageRunStatus.failed) {
            fresh.setStatus(MonitoringRunStatus.failed);
            fresh.setErrorMessage(
                run.getErrorMessage() != null ? run.getErrorMessage() : "coverage_run failed");
          } else {
            // completed / cancelled 都视为 completed（失败 vs 取消的语义已在 coverage_run 层固化）
            fresh.setStatus(MonitoringRunStatus.completed);
          }
          historyRepository.save(fresh);
        });
    return true;
  }

  private void finalizeAsFailed(MonitoringRunHistory h, String message, Instant now) {
    transactionTemplate.executeWithoutResult(
        s -> {
          MonitoringRunHistory fresh = historyRepository.findById(h.getId()).orElse(h);
          fresh.setStatus(MonitoringRunStatus.failed);
          fresh.setErrorMessage(message);
          fresh.setFinishedAt(now);
          historyRepository.save(fresh);
        });
  }
}
