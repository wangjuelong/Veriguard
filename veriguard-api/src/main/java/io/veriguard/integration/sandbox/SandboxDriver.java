package io.veriguard.integration.sandbox;

import io.veriguard.integration.sandbox.dto.MachineSnapshot;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import java.util.List;

public interface SandboxDriver {

  void healthCheck();

  List<MachineSnapshot> listMachines();

  SubmissionResult submitSample(SampleSubmissionRequest request);

  SandboxTaskStatus fetchTaskStatus(long capeTaskId);
}
