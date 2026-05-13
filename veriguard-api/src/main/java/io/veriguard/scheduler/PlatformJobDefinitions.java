package io.veriguard.scheduler;

import static io.veriguard.scheduler.jobs.user_event.UserEventRetentionJob.USER_EVENT_RETENTION_JOB;
import static org.quartz.JobKey.jobKey;

import io.veriguard.scheduler.jobs.*;
import io.veriguard.scheduler.jobs.user_event.UserEventRetentionJob;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class PlatformJobDefinitions {

  @Bean
  public JobDetail getAttackChainNodesExecution() {
    return JobBuilder.newJob(AttackChainNodesExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("InjectsExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail getComchecksExecution() {
    return JobBuilder.newJob(ComchecksExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("ComchecksExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail getAttackChainExecution() {
    return JobBuilder.newJob(AttackChainExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("ScenarioExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail getEngineSyncExecution() {
    return JobBuilder.newJob(EngineSyncExecutionJob.class)
        .storeDurably()
        .withIdentity(jobKey("EngineSyncExecutionJob"))
        .build();
  }

  @Bean
  public JobDetail managerIntegrationsSync() {
    return JobBuilder.newJob(ManagerIntegrationsSyncJob.class)
        .storeDurably()
        .withIdentity(jobKey("managerIntegrationsSync"))
        .build();
  }

  @Bean
  public JobDetail userEventRetentionJobDetail() {
    return JobBuilder.newJob(UserEventRetentionJob.class)
        .withIdentity(USER_EVENT_RETENTION_JOB)
        .storeDurably()
        .build();
  }

  /**
   * Create the job for the requeue system of the execution traces
   *
   * @return the job
   */
  @Bean
  public JobDetail getExecutionTracesBatchRequeueJob() {
    return JobBuilder.newJob(ExecutionTracesBatchRequeueJob.class)
        .withIdentity("executionTracesBatchRequeueJob")
        .storeDurably()
        .build();
  }

  /**
   * IPv6 安全验证系统 §3.6 PR D2 —— 攻击组合任务超时扫描 job
   *
   * @return the job
   */
  @Bean
  public JobDetail getCombinationTimeoutJob() {
    return JobBuilder.newJob(CombinationTimeoutJob.class)
        .withIdentity(CombinationTimeoutJob.JOB_NAME)
        .storeDurably()
        .build();
  }

  /**
   * IPv6 安全验证系统 §3.2 PR C4 —— 边界策略常态化监控调度 job（每分钟 tick）.
   *
   * @return the job
   */
  @Bean
  public JobDetail getBoundaryMonitoringJob() {
    return JobBuilder.newJob(BoundaryMonitoringJob.class)
        .withIdentity(BoundaryMonitoringJob.JOB_NAME)
        .storeDurably()
        .build();
  }

  /**
   * IPv6 安全验证系统 §3.2 PR C4 —— 监控历史回填 job（每分钟扫描 triggered 状态）.
   *
   * @return the job
   */
  @Bean
  public JobDetail getMonitoringHistoryUpdaterJob() {
    return JobBuilder.newJob(MonitoringHistoryUpdaterJob.class)
        .withIdentity(MonitoringHistoryUpdaterJob.JOB_NAME)
        .storeDurably()
        .build();
  }
}
