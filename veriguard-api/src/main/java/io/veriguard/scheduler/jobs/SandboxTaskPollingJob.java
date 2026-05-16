package io.veriguard.scheduler.jobs;

import io.veriguard.rest.security_validation.SandboxTaskService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * 沙箱任务轮询 Job —— C1-Platform-5 招标 §8.1 / §8.2 / §8.3.
 *
 * <p>每 30s tick 一次（{@code PlatformTriggers#sandboxTaskPollingTrigger}），扫所有 {@link
 * io.veriguard.database.model.VeriguardSandboxTask.Status#QUEUED} / {@link
 * io.veriguard.database.model.VeriguardSandboxTask.Status#RUNNING} 行，调 {@link
 * io.veriguard.integration.sandbox.SandboxDriver#fetchTaskStatus} 刷新状态。
 *
 * <p>不并发执行（{@link DisallowConcurrentExecution}）—— 同一时刻只允许一个轮询窗口在飞，避免 长时间 CAPE 调用堆积导致 N+1 个 worker
 * 抢同一行。
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class SandboxTaskPollingJob implements Job {

  public static final String JOB_NAME = "SandboxTaskPollingJob";
  public static final String TRIGGER_NAME = "SandboxTaskPollingTrigger";

  private final SandboxTaskService taskService;

  public SandboxTaskPollingJob(SandboxTaskService taskService) {
    this.taskService = taskService;
  }

  @Override
  public void execute(JobExecutionContext context) {
    try {
      int transitioned = taskService.pollAllActive();
      if (transitioned > 0) {
        log.info("SandboxTaskPollingJob: {} task(s) transitioned state", transitioned);
      }
    } catch (RuntimeException ex) {
      // Single-row failures are already swallowed inside pollAllActive(); reaching here means a
      // global failure (e.g. DB connection lost). Log + return so Quartz schedules the next tick.
      log.error("SandboxTaskPollingJob run failed: {}", ex.getMessage(), ex);
    }
  }
}
