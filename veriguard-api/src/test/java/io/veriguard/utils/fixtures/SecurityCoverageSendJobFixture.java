package io.veriguard.utils.fixtures;

import io.veriguard.database.model.SecurityCoverageSendJob;

public class SecurityCoverageSendJobFixture {
  public static SecurityCoverageSendJob createDefaultSecurityCoverageSendJob() {
    SecurityCoverageSendJob securityCoverageSendJob = new SecurityCoverageSendJob();
    securityCoverageSendJob.setStatus("PENDING");
    return securityCoverageSendJob;
  }
}
