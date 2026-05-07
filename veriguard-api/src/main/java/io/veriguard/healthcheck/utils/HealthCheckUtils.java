package io.veriguard.healthcheck.utils;

import static java.time.Instant.now;

import io.veriguard.database.model.*;
import io.veriguard.executors.utils.ExecutorUtils;
import io.veriguard.healthcheck.dto.HealthCheck;
import io.veriguard.healthcheck.enums.ExternalServiceDependency;
import io.veriguard.helper.NodeModelHelper;
import io.veriguard.rest.inject.output.AgentsAndAssetsAgentless;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HealthCheckUtils {

  private final ExecutorUtils executorUtils;

  /**
   * Run all mail service checks for one attackChainNode
   *
   * @param attackChainNode to test
   * @param service to verify
   * @param isServiceAvailable status
   * @param type of healthcheck
   * @param status of healthcheck
   * @return found healthchecks
   */
  public List<HealthCheck> runMailServiceChecks(
      AttackChainNode attackChainNode,
      ExternalServiceDependency service,
      boolean isServiceAvailable,
      HealthCheck.Type type,
      HealthCheck.Status status) {
    List<HealthCheck> result = new ArrayList<>();
    NodeContract nodeContract = attackChainNode.getNodeContract().orElse(null);
    NodeExecutor nodeExecutor = nodeContract != null ? nodeContract.getNodeExecutor() : null;

    if (nodeExecutor != null
        && ArrayUtils.contains(nodeExecutor.getDependencies(), service)
        && !isServiceAvailable) {
      result.add(new HealthCheck(type, HealthCheck.Detail.SERVICE_UNAVAILABLE, status, now()));
    }

    return result;
  }

  /**
   * Run all Executors checks for one attackChainNode
   *
   * @param attackChainNode to test
   * @param agentsAndAssetsAgentless data to verify if there is at least one agent up
   * @return all found executors healthchecks issues
   */
  public List<HealthCheck> runExecutorChecks(
      AttackChainNode attackChainNode, AgentsAndAssetsAgentless agentsAndAssetsAgentless) {
    List<HealthCheck> result = new ArrayList<>();
    NodeContract nodeContract = attackChainNode.getNodeContract().orElse(null);
    Set<Agent> agents = agentsAndAssetsAgentless.agents();
    agents = executorUtils.removeInactiveAgentsFromAgents(agents);
    agents = executorUtils.removeAgentsWithoutExecutorFromAgents(agents);

    if (nodeContract != null && nodeContract.getNeedsExecutor() && agents.isEmpty()) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.AGENT_OR_EXECUTOR,
              HealthCheck.Detail.EMPTY,
              HealthCheck.Status.ERROR,
              now()));
    }

    return result;
  }

  /**
   * Run all Collectors checks for one attackChainNode
   *
   * @param attackChainNode to test
   * @param collectors all available collectors
   * @return all found collectors healthchecks issues
   */
  public List<HealthCheck> runCollectorChecks(AttackChainNode attackChainNode, List<Collector> collectors) {
    List<HealthCheck> result = new ArrayList<>();
    boolean isDetectionOrPrenvention =
        NodeModelHelper.isDetectionOrPrevention(attackChainNode.getContent());

    if (isDetectionOrPrenvention && collectors.isEmpty()) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR,
              HealthCheck.Detail.EMPTY,
              HealthCheck.Status.ERROR,
              now()));
    }

    return result;
  }

  /**
   * Launch all the nodeExecutor checks on one attackChainNode
   *
   * @param attackChainNode to test
   * @param nodeExecutors all available nodeExecutors
   * @return list of the healthcheck result
   */
  public List<HealthCheck> runAllNodeExecutorChecks(
      @NotNull final AttackChainNode attackChainNode, @NotNull final List<NodeExecutor> nodeExecutors) {

    List<HealthCheck> results = new ArrayList<>();
    results.addAll(
        runNodeExecutorCheck(attackChainNode, nodeExecutors, ExternalServiceDependency.NMAP, HealthCheck.Type.NMAP));
    results.addAll(
        runNodeExecutorCheck(
            attackChainNode, nodeExecutors, ExternalServiceDependency.NUCLEI, HealthCheck.Type.NUCLEI));
    return results;
  }

  /**
   * Verify whether an nodeExecutor contract depends on an nodeExecutor and whether that nodeExecutor is
   * registered; if not, add an error to the health check.
   *
   * @param attackChainNode
   * @param nodeExecutors
   * @param externalServiceDependency
   * @param type
   * @return
   */
  public List<HealthCheck> runNodeExecutorCheck(
      @NotNull final AttackChainNode attackChainNode,
      @NotNull final List<NodeExecutor> nodeExecutors,
      @NotNull final ExternalServiceDependency externalServiceDependency,
      @NotNull final HealthCheck.Type type) {
    List<HealthCheck> result = new ArrayList<>();
    NodeContract contract = attackChainNode.getNodeContract().orElse(null);
    if (contract != null
        && contract.getNodeExecutor() != null
        && contract.getNodeExecutor().getDependencies() != null
        && Arrays.asList(contract.getNodeExecutor().getDependencies())
            .contains(externalServiceDependency)) {
      boolean isNodeExecutorRegistered =
          nodeExecutors.stream()
              .anyMatch(
                  nodeExecutor ->
                      Objects.equals(nodeExecutor.getType(), externalServiceDependency.getValue()));

      // if the nodeExecutor is not registered we add an error in the health check
      if (!isNodeExecutorRegistered) {
        result.add(
            new HealthCheck(
                type, HealthCheck.Detail.SERVICE_UNAVAILABLE, HealthCheck.Status.ERROR, now()));
      }
    }
    return result;
  }

  /**
   * Run all missing content checks for one attackChain
   *
   * @param attackChain to test
   * @return all found missing content issues
   */
  public List<HealthCheck> runMissingContentChecks(@NotNull final AttackChain attackChain) {
    List<HealthCheck> result = new ArrayList<>();
    boolean atLeastOneAttackChainNodeIsNotReady =
        attackChain.getAttackChainNodes().stream().anyMatch(attackChainNode -> !attackChainNode.isReady());

    if (atLeastOneAttackChainNodeIsNotReady) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.INJECT,
              HealthCheck.Detail.NOT_READY,
              HealthCheck.Status.WARNING,
              now()));
    }

    return result;
  }

  /**
   * Run all teams checks for one attackChain
   *
   * @param attackChain to test
   * @return all found teams issues
   */
  public List<HealthCheck> runTeamsChecks(@NotNull final AttackChain attackChain) {
    List<HealthCheck> result = new ArrayList<>();
    boolean isMailSender =
        attackChain.getAttackChainNodes().stream()
            .filter(
                attackChainNode ->
                    attackChainNode.getNodeContract() != null
                        && attackChainNode.getNodeContract().isPresent()
                        && attackChainNode.getNodeContract().get().getNodeExecutor() != null
                        && attackChainNode.getNodeContract().get().getNodeExecutor().getDependencies()
                            != null)
            .flatMap(
                attackChainNode ->
                    Arrays.stream(
                        attackChainNode.getNodeContract().get().getNodeExecutor().getDependencies()))
            .anyMatch(
                dependency ->
                    ExternalServiceDependency.SMTP.equals(dependency)
                        || ExternalServiceDependency.IMAP.equals(dependency));

    if (isMailSender) {
      boolean isMissingTeamsOrEnabledPlayers =
          attackChain.getTeams().isEmpty()
              || attackChain.getTeams().stream().allMatch(team -> team.getUsers().isEmpty())
              || attackChain.getTeamUsers().isEmpty();

      if (isMissingTeamsOrEnabledPlayers) {
        result.add(
            new HealthCheck(
                HealthCheck.Type.TEAMS,
                HealthCheck.Detail.EMPTY,
                HealthCheck.Status.WARNING,
                now()));
      }
    }

    return result;
  }
}
