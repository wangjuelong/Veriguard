package io.veriguard.rest.security_validation;

import io.veriguard.database.model.VeriguardSandbox;
import java.util.ArrayList;

public final class SandboxMapper {

  private SandboxMapper() {}

  public static SecurityValidationDtos.SandboxOutput toOutput(VeriguardSandbox sandbox) {
    return new SecurityValidationDtos.SandboxOutput(
        sandbox.getId(),
        sandbox.getName(),
        sandbox.getDescription(),
        sandbox.getNetworkPolicy(),
        sandbox.getNetworkRules(),
        sandbox.isAutoRestoreEnabled(),
        sandbox.getSupportedSampleTypes(),
        sandbox.getStatus(),
        sandbox.getCreatedAt(),
        sandbox.getUpdatedAt());
  }

  public static void updateEntity(VeriguardSandbox sandbox, SandboxInput input) {
    sandbox.setName(input.name());
    sandbox.setDescription(input.description());
    sandbox.setNetworkPolicy(input.networkPolicy());
    sandbox.setNetworkRules(new ArrayList<>(input.networkRules()));
    sandbox.setAutoRestoreEnabled(input.autoRestoreEnabled());
    sandbox.setSupportedSampleTypes(new ArrayList<>(input.supportedSampleTypes()));
    sandbox.setStatus(input.status());
  }
}
