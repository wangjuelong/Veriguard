package io.openaev.rest.veriguard;

import io.openaev.database.model.VeriguardSandbox;
import java.util.ArrayList;

public final class VeriguardSandboxMapper {

  private VeriguardSandboxMapper() {}

  public static VeriguardDtos.SandboxOutput toOutput(VeriguardSandbox sandbox) {
    return new VeriguardDtos.SandboxOutput(
        sandbox.getId(),
        sandbox.getName(),
        sandbox.getDescription(),
        sandbox.getProviderType(),
        sandbox.getEndpoint(),
        sandbox.getNetworkPolicy(),
        sandbox.getNetworkRules(),
        sandbox.isAutoRestoreEnabled(),
        sandbox.getSupportedSampleTypes(),
        sandbox.getStatus(),
        sandbox.getCreatedAt(),
        sandbox.getUpdatedAt());
  }

  public static void updateEntity(VeriguardSandbox sandbox, VeriguardSandboxInput input) {
    sandbox.setName(input.name());
    sandbox.setDescription(input.description());
    sandbox.setProviderType(input.providerType());
    sandbox.setEndpoint(input.endpoint());
    sandbox.setNetworkPolicy(input.networkPolicy());
    sandbox.setNetworkRules(new ArrayList<>(input.networkRules()));
    sandbox.setAutoRestoreEnabled(input.autoRestoreEnabled());
    sandbox.setSupportedSampleTypes(new ArrayList<>(input.supportedSampleTypes()));
    sandbox.setStatus(input.status());
  }
}
