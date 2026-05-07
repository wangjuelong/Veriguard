package io.veriguard.executors.caldera.service;

import io.veriguard.database.model.*;
import io.veriguard.executors.ExecutorContextService;
import io.veriguard.executors.caldera.client.CalderaExecutorClient;
import io.veriguard.executors.caldera.client.model.Ability;
import io.veriguard.executors.caldera.config.CalderaExecutorConfig;
import io.veriguard.rest.exception.AgentException;
import io.veriguard.service.NodeExecutorService;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CalderaExecutorContextService extends ExecutorContextService {

  private final CalderaExecutorConfig calderaExecutorConfig;
  private final NodeExecutorService nodeExecutorService;
  private final CalderaExecutorClient calderaExecutorClient;

  public final Map<String, Ability> nodeExecutorExecutorAbilities = new HashMap<>();
  public final Map<String, Ability> nodeExecutorExecutorClearAbilities = new HashMap<>();

  public void registerAbilities() {
    // Create the abilities if not exist for all nodeExecutors that need it
    List<Ability> abilities = this.abilities();

    Iterable<NodeExecutor> nodeExecutors = nodeExecutorService.getAllConnectors();
    nodeExecutors.forEach(
        nodeExecutor -> {
          if (nodeExecutor.getExecutorCommands() != null) {
            List<Ability> filteredAbilities =
                abilities.stream()
                    .filter(
                        ability ->
                            ability.getName().equals("caldera-subprocessor-" + nodeExecutor.getName()))
                    .toList();
            if (!filteredAbilities.isEmpty()) {
              Ability existingAbility = filteredAbilities.getFirst();
              calderaExecutorClient.deleteAbility(existingAbility);
            }
            Ability ability = calderaExecutorClient.createSubprocessorAbility(nodeExecutor);
            this.nodeExecutorExecutorAbilities.put(nodeExecutor.getId(), ability);
          }
          if (nodeExecutor.getExecutorClearCommands() != null) {
            List<Ability> filteredAbilities =
                abilities.stream()
                    .filter(
                        ability -> ability.getName().equals("caldera-clear-" + nodeExecutor.getName()))
                    .toList();
            if (!filteredAbilities.isEmpty()) {
              Ability existingAbility = filteredAbilities.getFirst();
              calderaExecutorClient.deleteAbility(existingAbility);
            }
            Ability ability = calderaExecutorClient.createClearAbility(nodeExecutor);
            this.nodeExecutorExecutorClearAbilities.put(nodeExecutor.getId(), ability);
          }
        });
  }

  public void launchExecutorSubprocess(
      @NotNull final AttackChainNode attackChainNode,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent)
      throws AgentException {

    if (!this.calderaExecutorConfig.isEnable()) {
      throw new AgentException("Fatal error: Caldera executor is not enabled", agent);
    }

    attackChainNode
        .getNodeContract()
        .map(NodeContract::getNodeExecutor)
        .ifPresent(
            nodeExecutor -> {
              if (this.nodeExecutorExecutorAbilities.containsKey(nodeExecutor.getId())) {
                List<Map<String, String>> additionalFields =
                    List.of(
                        Map.of("trait", "inject", "value", attackChainNode.getId()),
                        Map.of("trait", "agent", "value", agent.getId()));
                calderaExecutorClient.exploit(
                    "base64",
                    agent.getExternalReference(),
                    this.nodeExecutorExecutorAbilities.get(nodeExecutor.getId()).getAbility_id(),
                    additionalFields);
              }
            });
  }

  public List<Agent> launchBatchExecutorSubprocess(
      AttackChainNode attackChainNode, Set<Agent> agents, AttackChainNodeStatus attackChainNodeStatus) {
    return new ArrayList<>();
  }

  public void launchExecutorClear(@NotNull final NodeExecutor nodeExecutor, @NotNull final Agent agent) {
    if (this.nodeExecutorExecutorAbilities.containsKey(nodeExecutor.getId())) {
      calderaExecutorClient.exploit(
          "base64",
          agent.getExternalReference(),
          this.nodeExecutorExecutorClearAbilities.get(nodeExecutor.getId()).getAbility_id(),
          List.of());
    }
  }

  private List<Ability> abilities() {
    return calderaExecutorClient.abilities();
  }
}
