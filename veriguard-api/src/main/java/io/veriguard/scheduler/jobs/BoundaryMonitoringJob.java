package io.veriguard.scheduler.jobs;

import io.veriguard.database.model.monitoring.MonitoringJob;
import io.veriguard.database.repository.MonitoringJobRepository;
import io.veriguard.monitoring.CronValidator;
import io.veriguard.monitoring.MonitoringTriggerService;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * 边界策略常态化监控调度 Job —— PR C4 招标 §3.2.
 *
 * <p>设计：每分钟 tick 一次（{@link io.veriguard.scheduler.PlatformTriggers}），
 * 拉所有 enabled monitoring_job，比较「上次触发时间到当前」之间是否跨过该 cron 的下一个触发点。
 * 跨过则触发一次 {@link MonitoringTriggerService#trigger}.
 *
 * <p>不动态注册 Quartz triggers per-job，原因：CRUD 时需要同步 scheduler 状态，复杂度
 * 高且与 Quartz JobStore 的事务边界冲突；单一 tick + 内部 cron 计算更简单且足以满足
 * "按小时 / 按天" 的最小粒度。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class BoundaryMonitoringJob implements Job {

  public static final String JOB_NAME = "BoundaryMonitoringJob";
  public static final String TRIGGER_NAME = "BoundaryMonitoringTrigger";

  private final MonitoringJobRepository jobRepository;
  private final MonitoringTriggerService triggerService;

  public BoundaryMonitoringJob(
      MonitoringJobRepository jobRepository, MonitoringTriggerService triggerService) {
    this.jobRepository = jobRepository;
    this.triggerService = triggerService;
  }

  @Override
  public void execute(JobExecutionContext context) {
    sweepAndTrigger();
  }

  /** 测试 / 显式调用入口；返回本次触发的 job 数. */
  public int sweepAndTrigger() {
    Instant now = Instant.now();
    List<MonitoringJob> enabled = jobRepository.findAllByEnabledTrue();
    if (enabled.isEmpty()) {
      return 0;
    }
    int triggered = 0;
    for (MonitoringJob job : enabled) {
      if (shouldFire(job, now)) {
        try {
          triggerService.trigger(job);
          triggered++;
        } catch (RuntimeException e) {
          log.error(
              "Unhandled trigger failure for monitoring job {}: {}", job.getId(), e.getMessage(), e);
        }
      }
    }
    if (triggered > 0) {
      log.info("BoundaryMonitoringJob fired {} of {} enabled jobs at {}", triggered, enabled.size(), now);
    }
    return triggered;
  }

  /** 是否应在 now 触发该 job —— last_triggered_at（或 created_at）后的下一个 cron 时间 ≤ now. */
  static boolean shouldFire(MonitoringJob job, Instant now) {
    Instant anchor = job.getLastTriggeredAt() != null ? job.getLastTriggeredAt() : job.getCreatedAt();
    if (anchor == null) {
      anchor = now;
    }
    try {
      CronExpression cron = new CronExpression(job.getCronExpression());
      cron.setTimeZone(TimeZone.getTimeZone("UTC"));
      Date next = cron.getNextValidTimeAfter(Date.from(anchor));
      if (next == null) {
        return false;
      }
      return !next.toInstant().isAfter(now);
    } catch (java.text.ParseException e) {
      // Job 进库时 CronValidator 已校验；理论不可达，但防止 Quartz job 整体崩溃
      log.warn(
          "Skipping monitoring job {} due to invalid cron '{}': {}",
          job.getId(),
          job.getCronExpression(),
          e.getMessage());
      return false;
    }
  }

  /** 暴露常量供单测 / API 引用. */
  public static long minIntervalSeconds() {
    return CronValidator.MIN_INTERVAL_SECONDS;
  }
}
