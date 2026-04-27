package io.veriguard.integration.sandbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import org.junit.jupiter.api.Test;

class NotImplementedSandboxDriverTest {

  private final NotImplementedSandboxDriver driver = new NotImplementedSandboxDriver();

  @Test
  void healthCheck_throwsIntegrationException() {
    assertThatThrownBy(driver::healthCheck)
        .isInstanceOf(SandboxIntegrationException.class)
        .extracting("reasonCode")
        .isEqualTo(SandboxIntegrationException.ReasonCode.NOT_IMPLEMENTED);
  }

  @Test
  void listMachines_throwsIntegrationException() {
    assertThatThrownBy(driver::listMachines).isInstanceOf(SandboxIntegrationException.class);
  }

  @Test
  void submitSample_throwsIntegrationException() {
    SampleSubmissionRequest request =
        new SampleSubmissionRequest(
            "preset-1", "RANSOMWARE", "demo.exe", "deadbeef", new byte[] {0x4d, 0x5a}, null, null);
    assertThatThrownBy(() -> driver.submitSample(request))
        .isInstanceOf(SandboxIntegrationException.class);
  }

  @Test
  void fetchTaskStatus_throwsIntegrationException() {
    assertThatThrownBy(() -> driver.fetchTaskStatus(1L))
        .isInstanceOf(SandboxIntegrationException.class);
  }
}
