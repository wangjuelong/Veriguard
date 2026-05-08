package io.veriguard.execution;

import static io.veriguard.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_NAME;
import static io.veriguard.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_TYPE;
import static io.veriguard.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;
import static io.veriguard.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_TYPE;
import static io.veriguard.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_NAME;
import static io.veriguard.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;
import static io.veriguard.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;
import static io.veriguard.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_TYPE;

import com.google.common.annotations.VisibleForTesting;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.ExecutionTraceRepository;
import io.veriguard.executors.ExecutorContextService;
import io.veriguard.executors.utils.ExecutorUtils;
import io.veriguard.integration.ComponentRequest;
import io.veriguard.integration.ManagerFactory;
import io.veriguard.rest.attack_chain_node.output.AgentsAndAssetsAgentless;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.exception.AgentException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExecutionExecutorService {

  private final ManagerFactory managerFactory;
  private final ExecutionTraceRepository executionTraceRepository;
  private final AttackChainNodeService attackChainNodeService;
  private final ExecutorUtils executorUtils;

  public void launchExecutorContext(AttackChainNode attackChainNode) {
    AttackChainNodeStatus attackChainNodeStatus =
        attackChainNode
            .getStatus()
            .orElseThrow(() -> new IllegalArgumentException("Status should exist"));
    // First, get the agents and the assets agentless of this attackChainNode
    AgentsAndAssetsAgentless agentsAndAssetsAgentless =
        this.attackChainNodeService.getAgentsAndAgentlessAssetsByAttackChainNode(attackChainNode);
    Set<Agent> agents = agentsAndAssetsAgentless.agents();
    Set<Asset> assetsAgentless = agentsAndAssetsAgentless.assetsAgentless();
    // Manage agentless assets
    saveAgentlessAssetsTraces(assetsAgentless, attackChainNodeStatus);
    // Filter each list to do something for each specific case and then remove the specific agents
    // from the main "agents" list to execute payloads at the end for the remaining "normal" agents
    Set<Agent> inactiveAgents = executorUtils.findInactiveAgents(agents);
    agents.removeAll(inactiveAgents);
    Set<Agent> agentsWithoutExecutor = executorUtils.findAgentsWithoutExecutor(agents);
    agents.removeAll(agentsWithoutExecutor);
    Set<Agent> crowdstrikeAgents =
        executorUtils.findAgentsByExecutorType(agents, CROWDSTRIKE_EXECUTOR_TYPE);
    agents.removeAll(crowdstrikeAgents);
    Set<Agent> sentineloneAgents =
        executorUtils.findAgentsByExecutorType(agents, SENTINELONE_EXECUTOR_TYPE);
    agents.removeAll(sentineloneAgents);
    Set<Agent> taniumAgents = executorUtils.findAgentsByExecutorType(agents, TANIUM_EXECUTOR_TYPE);
    agents.removeAll(taniumAgents);
    Set<Agent> cortexAgents =
        executorUtils.findAgentsByExecutorType(agents, PALOALTOCORTEX_EXECUTOR_TYPE);
    agents.removeAll(cortexAgents);

    AtomicBoolean atLeastOneExecution = new AtomicBoolean(false);
    // Manage inactive agents
    saveInactiveAgentsTraces(inactiveAgents, attackChainNodeStatus);
    // Manage without executor agents
    saveWithoutExecutorAgentsTraces(agentsWithoutExecutor, attackChainNodeStatus);
    // Manage Crowdstrike agents for batch execution
    launchBatchExecutorContextForAgent(
        crowdstrikeAgents,
        CROWDSTRIKE_EXECUTOR_NAME,
        attackChainNode,
        attackChainNodeStatus,
        atLeastOneExecution);
    // Manage Sentinelone agents for batch execution
    launchBatchExecutorContextForAgent(
        sentineloneAgents,
        SENTINELONE_EXECUTOR_NAME,
        attackChainNode,
        attackChainNodeStatus,
        atLeastOneExecution);
    // Manage Tanium agents for batch execution
    launchBatchExecutorContextForAgent(
        taniumAgents,
        TANIUM_EXECUTOR_NAME,
        attackChainNode,
        attackChainNodeStatus,
        atLeastOneExecution);
    // Manage Palo Alto Cortex agents for batch execution
    launchBatchExecutorContextForAgent(
        cortexAgents,
        PALOALTOCORTEX_EXECUTOR_NAME,
        attackChainNode,
        attackChainNodeStatus,
        atLeastOneExecution);
    // Manage remaining agents
    agents.forEach(
        agent -> {
          try {
            launchExecutorContextForAgent(attackChainNode, agent);
            atLeastOneExecution.set(true);
          } catch (AgentException e) {
            log.error("launchExecutorContextForAgent error: {}", e.getMessage());
            saveAgentErrorTrace(e, attackChainNodeStatus);
          }
        });
    if (!atLeastOneExecution.get()) {
      throw new ExecutionExecutorException("No asset executed");
    }
  }

  private void launchBatchExecutorContextForAgent(
      Set<Agent> agents,
      String executorName,
      AttackChainNode attackChainNode,
      AttackChainNodeStatus attackChainNodeStatus,
      AtomicBoolean atLeastOneExecution) {
    if (!agents.isEmpty()) {
      try {
        ExecutorContextService executorContextService =
            managerFactory
                .getManager()
                .request(new ComponentRequest(executorName), ExecutorContextService.class);
        executorContextService.launchBatchExecutorSubprocess(
            attackChainNode, agents, attackChainNodeStatus);
        atLeastOneExecution.set(true);
      } catch (Exception e) {
        log.error("{} launchBatchExecutorSubprocess error: {}", executorName, e.getMessage());
        saveAgentsErrorTraces(e, agents, attackChainNodeStatus);
      }
    }
  }

  @VisibleForTesting
  public void saveAgentErrorTrace(AgentException e, AttackChainNodeStatus attackChainNodeStatus) {
    executionTraceRepository.save(
        new ExecutionTrace(
            attackChainNodeStatus,
            ExecutionTraceStatus.ERROR,
            List.of(),
            e.getMessage(),
            ExecutionTraceAction.COMPLETE,
            e.getAgent(),
            null));
  }

  @VisibleForTesting
  public void saveAgentsErrorTraces(
      Exception e, Set<Agent> agents, AttackChainNodeStatus attackChainNodeStatus) {
    executionTraceRepository.saveAll(
        agents.stream()
            .map(
                agent ->
                    new ExecutionTrace(
                        attackChainNodeStatus,
                        ExecutionTraceStatus.ERROR,
                        List.of(),
                        e.getMessage(),
                        ExecutionTraceAction.COMPLETE,
                        agent,
                        null))
            .toList());
  }

  @VisibleForTesting
  public void saveWithoutExecutorAgentsTraces(
      Set<Agent> agentsWithoutExecutor, AttackChainNodeStatus attackChainNodeStatus) {
    if (!agentsWithoutExecutor.isEmpty()) {
      executionTraceRepository.saveAll(
          agentsWithoutExecutor.stream()
              .map(
                  agent ->
                      new ExecutionTrace(
                          attackChainNodeStatus,
                          ExecutionTraceStatus.ERROR,
                          List.of(),
                          "Cannot find the executor for the agent "
                              + agent.getExecutedByUser()
                              + " from the asset "
                              + agent.getAsset().getName(),
                          ExecutionTraceAction.COMPLETE,
                          agent,
                          null))
              .toList());
    }
  }

  @VisibleForTesting
  public void saveInactiveAgentsTraces(
      Set<Agent> inactiveAgents, AttackChainNodeStatus attackChainNodeStatus) {
    if (!inactiveAgents.isEmpty()) {
      executionTraceRepository.saveAll(
          inactiveAgents.stream()
              .map(
                  agent ->
                      new ExecutionTrace(
                          attackChainNodeStatus,
                          ExecutionTraceStatus.AGENT_INACTIVE,
                          List.of(),
                          "Agent "
                              + agent.getExecutedByUser()
                              + " is inactive for the asset "
                              + agent.getAsset().getName(),
                          ExecutionTraceAction.COMPLETE,
                          agent,
                          null))
              .toList());
    }
  }

  @VisibleForTesting
  public void saveAgentlessAssetsTraces(
      Set<Asset> assetsAgentless, AttackChainNodeStatus attackChainNodeStatus) {
    if (!assetsAgentless.isEmpty()) {
      executionTraceRepository.saveAll(
          assetsAgentless.stream()
              .map(
                  asset ->
                      new ExecutionTrace(
                          attackChainNodeStatus,
                          ExecutionTraceStatus.ASSET_AGENTLESS,
                          List.of(asset.getId()),
                          "Asset " + asset.getName() + " has no agent, unable to launch the inject",
                          ExecutionTraceAction.COMPLETE,
                          null,
                          null))
              .toList());
    }
  }

  private void launchExecutorContextForAgent(AttackChainNode attackChainNode, Agent agent)
      throws AgentException {
    try {
      Endpoint assetEndpoint = (Endpoint) Hibernate.unproxy(agent.getAsset());
      ExecutorContextService executorContextService =
          managerFactory
              .getManager()
              .request(
                  new ComponentRequest(agent.getExecutor().getName()),
                  ExecutorContextService.class);
      executorContextService.launchExecutorSubprocess(attackChainNode, assetEndpoint, agent);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new AgentException("Fatal error: " + e.getMessage(), agent);
    }
  }
}
