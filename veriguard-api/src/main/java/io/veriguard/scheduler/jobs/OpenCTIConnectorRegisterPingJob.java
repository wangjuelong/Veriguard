package io.veriguard.scheduler.jobs;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.opencti.connectors.service.OpenCTIConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class OpenCTIConnectorRegisterPingJob implements Job {
  private final OpenCTIConnectorService openCTIConnectorService;

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    openCTIConnectorService.registerOrPingAllConnectors();
  }
}
