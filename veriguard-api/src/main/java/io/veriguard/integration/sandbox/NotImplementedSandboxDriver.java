package io.veriguard.integration.sandbox;

import io.veriguard.integration.sandbox.dto.MachineSnapshot;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotImplementedSandboxDriver implements SandboxDriver {

  @Override
  public void healthCheck() {
    throw notImplemented("healthCheck");
  }

  @Override
  public List<MachineSnapshot> listMachines() {
    throw notImplemented("listMachines");
  }

  @Override
  public SubmissionResult submitSample(SampleSubmissionRequest request) {
    throw notImplemented("submitSample");
  }

  @Override
  public SandboxTaskStatus fetchTaskStatus(long capeTaskId) {
    throw notImplemented("fetchTaskStatus");
  }

  private SandboxIntegrationException notImplemented(String operation) {
    return new SandboxIntegrationException(
        SandboxIntegrationException.ReasonCode.NOT_IMPLEMENTED,
        "Sandbox driver operation '" + operation + "' is not implemented in M1.");
  }
}
