package io.veriguard.scheduler;

import static io.veriguard.scheduler.jobs.user_event.UserEventRetentionJob.USER_EVENT_RETENTION_TRIGGER;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.*;
import static org.quartz.TriggerBuilder.newTrigger;

import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
public class PlatformTriggers {

  private PlatformJobDefinitions platformJobs;

  @Autowired
  public void setPlatformJobs(PlatformJobDefinitions platformJobs) {
    this.platformJobs = platformJobs;
  }

  @Bean
  public Trigger attackChainNodesExecutionTrigger() {
    return newTrigger()
        .forJob(platformJobs.getAttackChainNodesExecution())
        .withIdentity("InjectsExecutionTrigger")
        .withSchedule(cronSchedule("0 0/1 * * * ?")) // Every minute align on clock
        .build();
  }

  @Bean
  public Trigger comchecksExecutionTrigger() {
    return newTrigger()
        .forJob(platformJobs.getComchecksExecution())
        .withIdentity("ComchecksExecutionTrigger")
        .withSchedule(repeatMinutelyForever())
        .build();
  }

  @Bean
  public Trigger attackChainExecutionTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getAttackChainExecution())
        .withIdentity("ScenarioExecutionTrigger")
        .withSchedule(repeatMinutelyForever())
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger elasticSyncExecutionTrigger() {
    SimpleScheduleBuilder _15_seconds = simpleSchedule().withIntervalInSeconds(15).repeatForever();
    return newTrigger()
        .forJob(this.platformJobs.getEngineSyncExecution())
        .withIdentity("engineSyncExecutionTrigger")
        .withSchedule(_15_seconds.withMisfireHandlingInstructionNextWithRemainingCount())
        .build();
  }

  @Bean
  @Profile("!test")
  public Trigger managerIntegrationsSyncTrigger() {
    SimpleScheduleBuilder _15_seconds = simpleSchedule().withIntervalInSeconds(15).repeatForever();
    return newTrigger()
        .forJob(this.platformJobs.managerIntegrationsSync())
        .withIdentity("managerIntegrationsSync")
        .withSchedule(_15_seconds.withMisfireHandlingInstructionNextWithRemainingCount())
        .build();
  }

  @Bean
  public Trigger userEventRetentionTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.userEventRetentionJobDetail())
        .withIdentity(USER_EVENT_RETENTION_TRIGGER)
        .withSchedule(cronSchedule("0 0 0 * * ?"))
        .build();
  }

  /**
   * Create a trigger to run the requeue system for the execution traces
   *
   * @return the trigger
   */
  @Bean
  public Trigger executionTracesBatchRequeueTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getExecutionTracesBatchRequeueJob())
        .withIdentity("ExecutionTracesBatchRequeueTrigger")
        .withSchedule(repeatSecondlyForever(15))
        .build();
  }

  /**
   * IPv6 安全验证系统 §3.6 PR D2 —— 每 5 min 扫描超时的攻击组合任务
   *
   * @return the trigger
   */
  @Bean
  @Profile("!test")
  public Trigger combinationTimeoutTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getCombinationTimeoutJob())
        .withIdentity(io.veriguard.scheduler.jobs.CombinationTimeoutJob.TRIGGER_NAME)
        .withSchedule(cronSchedule("0 0/5 * * * ?"))
        .build();
  }

  /**
   * IPv6 安全验证系统 §3.2 PR C4 —— 边界策略监控调度（每分钟 tick）
   *
   * @return the trigger
   */
  @Bean
  @Profile("!test")
  public Trigger boundaryMonitoringTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getBoundaryMonitoringJob())
        .withIdentity(io.veriguard.scheduler.jobs.BoundaryMonitoringJob.TRIGGER_NAME)
        .withSchedule(cronSchedule("0 0/1 * * * ?"))
        .build();
  }

  /**
   * IPv6 安全验证系统 §3.2 PR C4 —— 监控历史回填扫描（每分钟）
   *
   * @return the trigger
   */
  @Bean
  @Profile("!test")
  public Trigger monitoringHistoryUpdaterTrigger() {
    return newTrigger()
        .forJob(this.platformJobs.getMonitoringHistoryUpdaterJob())
        .withIdentity(io.veriguard.scheduler.jobs.MonitoringHistoryUpdaterJob.TRIGGER_NAME)
        .withSchedule(cronSchedule("0 0/1 * * * ?"))
        .build();
  }
}
