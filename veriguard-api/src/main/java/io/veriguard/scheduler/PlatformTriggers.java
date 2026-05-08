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
}
