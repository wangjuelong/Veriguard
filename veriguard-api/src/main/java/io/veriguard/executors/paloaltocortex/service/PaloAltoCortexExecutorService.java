package io.veriguard.executors.paloaltocortex.service;

import static io.veriguard.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_TYPE;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.AssetGroup;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.Executor;
import io.veriguard.executors.model.AgentRegisterInput;
import io.veriguard.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.veriguard.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.veriguard.executors.paloaltocortex.model.PaloAltoCortexEndpoint;
import io.veriguard.service.AgentService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaloAltoCortexExecutorService implements Runnable {
  private final PaloAltoCortexExecutorClient client;
  private final PaloAltoCortexExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;

  private Executor executor = null;

  public static Endpoint.PLATFORM_TYPE toPlatform(@NotBlank final String platform) {
    return switch (platform.toLowerCase()) {
      case "agent_os_linux" -> Endpoint.PLATFORM_TYPE.Linux;
      case "agent_os_windows" -> Endpoint.PLATFORM_TYPE.Windows;
      case "agent_os_mac" -> Endpoint.PLATFORM_TYPE.MacOS;
      default -> Endpoint.PLATFORM_TYPE.Unknown;
    };
  }

  public PaloAltoCortexExecutorService(
      Executor executor,
      PaloAltoCortexExecutorClient client,
      PaloAltoCortexExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService) {
    this.executor = executor;
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
  }

  @Override
  public void run() {
    log.info("Running Palo Alto Cortex executor endpoints gathering...");
    List<String> groupNames = Stream.of(this.config.getGroupName().split(",")).distinct().toList();
    for (String groupName : groupNames) {
      List<PaloAltoCortexEndpoint> paloAltoCortexEndpoints = this.client.endpoints(groupName);
      if (!paloAltoCortexEndpoints.isEmpty()) {
        Optional<AssetGroup> existingAssetGroup =
            assetGroupService.findByExternalReference(
                PALOALTOCORTEX_EXECUTOR_TYPE + "_" + groupName);
        AssetGroup assetGroup;
        if (existingAssetGroup.isPresent()) {
          assetGroup = existingAssetGroup.get();
        } else {
          assetGroup = new AssetGroup();
          assetGroup.setExternalReference(PALOALTOCORTEX_EXECUTOR_TYPE + "_" + groupName);
        }
        assetGroup.setName(groupName);
        log.info(
            "Palo alto cortex executor provisioning based on "
                + paloAltoCortexEndpoints.size()
                + " assets for the group "
                + assetGroup.getName());
        List<Agent> agents =
            endpointService.syncAgentsEndpoints(
                toAgentEndpoint(paloAltoCortexEndpoints),
                agentService.getAgentsByExecutorType(PALOALTOCORTEX_EXECUTOR_TYPE));
        assetGroup.setAssets(agents.stream().map(Agent::getAsset).toList());
        assetGroupService.createOrUpdateAssetGroupWithoutDynamicAssets(assetGroup);
      }
    }
  }

  private List<AgentRegisterInput> toAgentEndpoint(List<PaloAltoCortexEndpoint> endpoints) {
    return endpoints.stream()
        .map(
            paloAltoCortexEndpoint -> {
              AgentRegisterInput input = new AgentRegisterInput();
              input.setExecutor(executor);
              input.setExternalReference(paloAltoCortexEndpoint.getEndpoint_id());
              input.setElevated(true);
              input.setService(true);
              input.setName(paloAltoCortexEndpoint.getEndpoint_name());
              input.setSeenIp(paloAltoCortexEndpoint.getPublic_ip());
              input.setIps(paloAltoCortexEndpoint.getIp());
              input.setMacAddresses(paloAltoCortexEndpoint.getMac_address());
              input.setHostname(paloAltoCortexEndpoint.getEndpoint_name());
              input.setPlatform(toPlatform(paloAltoCortexEndpoint.getOs_type()));
              input.setArch(Endpoint.PLATFORM_ARCH.x86_64); // No arch from API
              input.setExecutedByUser(
                  Endpoint.PLATFORM_TYPE.Windows.equals(input.getPlatform())
                      ? Agent.ADMIN_SYSTEM_WINDOWS
                      : Agent.ADMIN_SYSTEM_UNIX);
              input.setLastSeen(Instant.ofEpochMilli(paloAltoCortexEndpoint.getLast_seen()));
              return input;
            })
        .collect(Collectors.toList());
  }
}
