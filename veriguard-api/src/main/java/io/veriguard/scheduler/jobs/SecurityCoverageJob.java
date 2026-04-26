package io.veriguard.scheduler.jobs;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.database.model.SecurityCoverageSendJob;
import io.veriguard.opencti.connectors.service.OpenCTIConnectorService;
import io.veriguard.service.SecurityCoverageSendJobService;
import io.veriguard.service.stix.SecurityCoverageService;
import io.veriguard.stix.objects.Bundle;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class SecurityCoverageJob implements Job {
  private final SecurityCoverageSendJobService securityCoverageSendJobService;
  private final SecurityCoverageService securityCoverageService;
  private final OpenCTIConnectorService openCTIConnectorService;

  @Override
  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    List<SecurityCoverageSendJob> jobs =
        securityCoverageSendJobService.getPendingSecurityCoverageSendJobs();
    List<SecurityCoverageSendJob> successfulJobs = new ArrayList<>();
    for (SecurityCoverageSendJob securityCoverageSendJob : jobs) {
      try {
        // send bundle
        Bundle resultBundle =
            securityCoverageService.createBundleFromSendJobs(List.of(securityCoverageSendJob));
        openCTIConnectorService.pushSecurityCoverageStixBundle(resultBundle);
        successfulJobs.add(securityCoverageSendJob);
      } catch (Exception e) {
        // don't crash the job
        log.error(
            "Could not create the STIX bundle for coverage of simulation {}",
            securityCoverageSendJob.getSimulation().getId(),
            e);
      }
    }
    if (!successfulJobs.isEmpty()) {
      securityCoverageSendJobService.consumeJobs(successfulJobs);
    }
  }
}
