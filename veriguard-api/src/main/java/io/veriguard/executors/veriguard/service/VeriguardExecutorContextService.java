package io.veriguard.executors.veriguard.service;

import static io.veriguard.executors.ExecutorHelper.replaceArgs;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.AssetAgentJobRepository;
import io.veriguard.executors.ExecutorContextService;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class VeriguardExecutorContextService extends ExecutorContextService {

  private final AssetAgentJobRepository assetAgentJobRepository;

  private String computeCommand(
      @NotNull final AttackChainNode attackChainNode,
      String agentId,
      Endpoint.PLATFORM_TYPE platform,
      Endpoint.PLATFORM_ARCH arch) {
    NodeExecutor nodeExecutor =
        attackChainNode
            .getNodeContract()
            .map(NodeContract::getNodeExecutor)
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

    return switch (platform) {
      case Windows, Linux, MacOS -> {
        String executorCommandKey = platform.name() + "." + arch.name();
        String cmd = nodeExecutor.getExecutorCommands().get(executorCommandKey);
        yield replaceArgs(platform, cmd, attackChainNode.getId(), agentId);
      }
      default -> throw new RuntimeException("Unsupported platform: " + platform);
    };
  }

  public void launchExecutorSubprocess(
      @NotNull final AttackChainNode attackChainNode,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent) {
    Endpoint.PLATFORM_TYPE platform = assetEndpoint.getPlatform();
    Endpoint.PLATFORM_ARCH arch = assetEndpoint.getArch();
    if (platform == null) {
      throw new RuntimeException("Unsupported null platform");
    }
    AssetAgentJob assetAgentJob = new AssetAgentJob();
    assetAgentJob.setCommand(computeCommand(attackChainNode, agent.getId(), platform, arch));
    assetAgentJob.setAgent(agent);
    assetAgentJob.setAttackChainNode(attackChainNode);
    assetAgentJobRepository.save(assetAgentJob);
  }

  public List<Agent> launchBatchExecutorSubprocess(
      AttackChainNode attackChainNode,
      Set<Agent> agents,
      AttackChainNodeStatus attackChainNodeStatus) {
    return new ArrayList<>();
  }
}
