package io.veriguard.integration.sandbox;

import io.veriguard.integration.sandbox.dto.MachineSnapshot;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * M1 fallback driver — throws {@code NOT_IMPLEMENTED} for every SPI call.
 *
 * <p>Selection: registered only when no other {@link SandboxDriver} bean exists. Once {@code
 * veriguard.sandbox.cape.endpoint} is configured the M2 {@link
 * io.veriguard.integration.sandbox.cape.CapeV2SandboxDriver} bean appears with {@code @Primary} and
 * takes over; this M1 stub stops being registered (see {@link ConditionalOnMissingBean}). Dev / CI
 * hosts that do not configure CAPEv2 keep getting this stub, which preserves a clear "sandbox
 * driver not implemented" error path instead of silent no-ops.
 */
@Component
@ConditionalOnMissingBean(value = SandboxDriver.class, ignored = NotImplementedSandboxDriver.class)
@Primary
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
