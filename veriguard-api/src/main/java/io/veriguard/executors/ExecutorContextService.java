package io.veriguard.executors;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeStatus;
import io.veriguard.rest.exception.AgentException;
import java.util.List;
import java.util.Set;

public abstract class ExecutorContextService {

  /**
   * Launch one attack for one agent, deprecation in progress, only used by Caldera/Veriguard agent
   *
   * @param attackChainNode to launch for the attack
   * @param assetEndpoint concerned by the attack
   * @param agent concerned by the attack
   * @throws AgentException if problem
   */
  public abstract void launchExecutorSubprocess(AttackChainNode attackChainNode, Endpoint assetEndpoint, Agent agent)
      throws AgentException;

  /**
   * To use when possible for better performance, launch one attack for X agents, used by
   * Tanium/SentinelOne/Crowdstrike/PaloAltoCortex
   *
   * @param attackChainNode to launch for the attack
   * @param agents concerned by the attack
   * @param attackChainNodeStatus concerned by the attack
   * @return agents
   * @throws InterruptedException if problem
   */
  public abstract List<Agent> launchBatchExecutorSubprocess(
      AttackChainNode attackChainNode, Set<Agent> agents, AttackChainNodeStatus attackChainNodeStatus);
}
