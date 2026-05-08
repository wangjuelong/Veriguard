package io.veriguard.helper;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Stream.concat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.specification.AttackChainNodeSpecification;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.execution.ExecutionContext;
import io.veriguard.execution.ExecutionContextService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Helper component for attackChainNode operations and execution context building.
 *
 * <p>Provides methods for retrieving pending attackChainNodes, converting attackChainNodes to
 * executable form, and building execution contexts with team and user information. This component
 * is central to the attackChainNode execution pipeline.
 *
 * @see io.veriguard.execution.ExecutableNode
 * @see io.veriguard.execution.ExecutionContext
 */
@Component
@RequiredArgsConstructor
public class AttackChainNodeHelper {

  @Resource protected ObjectMapper mapper;

  private final AttackChainNodeRepository attackChainNodeRepository;
  private final ExecutionContextService executionContextService;

  /**
   * Retrieves the teams targeted by an attackChainNode.
   *
   * <p>If the attackChainNode targets all teams, returns all teams from the attackChainRun.
   * Otherwise, returns only the specifically targeted teams. Also initializes users within teams
   * for player expectation processing.
   *
   * @param attackChainNode the attackChainNode to get teams for
   * @return list of targeted teams with initialized users
   */
  private List<Team> getAttackChainNodeTeams(@NotNull final AttackChainNode attackChainNode) {
    AttackChainRun attackChainRun = attackChainNode.getAttackChainRun();
    if (attackChainNode
        .isAllTeams()) { // In order to process expectations from players, we also need to load
      // players into teams
      attackChainRun.getTeams().forEach(team -> Hibernate.initialize(team.getUsers()));
      return attackChainRun.getTeams();
    } else {
      attackChainNode.getTeams().forEach(team -> Hibernate.initialize(team.getUsers()));
      return attackChainNode.getTeams();
    }
  }

  // -- INJECTION --

  private Stream<Tuple2<User, String>> getUsersFromInjection(Injection injection) {
    if (injection instanceof AttackChainNode attackChainNode) {
      List<Team> teams = getAttackChainNodeTeams(attackChainNode);
      // We get all the teams for this attackChainNode
      // But those team can be used in other attackChainRuns with different players enabled
      // So we need to focus on team players only enabled in the context of the current
      // attackChainRun
      if (attackChainNode.isAtomicTesting()) {
        return teams.stream()
            .flatMap(team -> team.getUsers().stream().map(user -> Tuples.of(user, team.getName())));
      }
      return teams.stream()
          .flatMap(
              team ->
                  team.getAttackChainRunTeamUsers().stream()
                      .filter(
                          attackChainRunTeamUser ->
                              attackChainRunTeamUser
                                  .getAttackChainRun()
                                  .getId()
                                  .equals(injection.getAttackChainRun().getId()))
                      .map(
                          attackChainRunTeamUser ->
                              Tuples.of(attackChainRunTeamUser.getUser(), team.getName())));
    }
    throw new UnsupportedOperationException("Unsupported type of Injection");
  }

  private List<ExecutionContext> usersFromInjection(Injection injection) {
    return getUsersFromInjection(injection).collect(groupingBy(Tuple2::getT1)).entrySet().stream()
        .map(
            entry ->
                this.executionContextService.executionContext(
                    entry.getKey(),
                    injection,
                    entry.getValue().stream().flatMap(ua -> Stream.of(ua.getT2())).toList()))
        .toList();
  }

  private boolean isBeforeOrEqualsNow(Injection injection) {
    Instant now = Instant.now();
    Instant attackChainNodeWhen = injection.getDate().orElseThrow();
    return attackChainNodeWhen.equals(now) || attackChainNodeWhen.isBefore(now);
  }

  /**
   * Retrieves all pending attackChainNodes within a time threshold.
   *
   * <p>Finds attackChainNodes that are pending execution and scheduled within the specified number
   * of minutes from now. Used for pre-loading upcoming attackChainNodes.
   *
   * @param thresholdMinutes the time window in minutes to look ahead
   * @return list of pending attackChainNodes scheduled within the threshold
   */
  public List<AttackChainNode> getAllPendingAttackChainNodesWithThresholdMinutes(
      int thresholdMinutes) {
    return this.attackChainNodeRepository.findAll(
        AttackChainNodeSpecification.pendingAttackChainNodeWithThresholdMinutes(thresholdMinutes));
  }

  // -- EXECUTABLE INJECT --

  private ExecutableNode toExecutableNode(AttackChainNode attackChainNode) {
    // TODO This is inefficient, we need to refactor this with our own query
    Hibernate.initialize(attackChainNode.getTags());
    Hibernate.initialize(attackChainNode.getUser());
    return new ExecutableNode(
        true,
        false,
        attackChainNode,
        getAttackChainNodeTeams(attackChainNode),
        attackChainNode
            .getAssets(), // TODO There is also inefficient lazy loading inside this get function
        attackChainNode.getAssetGroups(),
        usersFromInjection(attackChainNode));
  }

  /**
   * Retrieves all attackChainNodes that are ready for execution.
   *
   * <p>Combines regular attackChainRun attackChainNodes and atomic testing attackChainNodes that
   * are scheduled for now or earlier, converting them to executable form with all necessary context
   * (teams, assets, users).
   *
   * <p>This method runs in a transaction to ensure consistent data loading.
   *
   * @return list of executable attackChainNodes ready to run, sorted by execution order
   */
  @Transactional
  public List<ExecutableNode> getAttackChainNodesToRun() {
    // Get attackChainNodes
    List<AttackChainNode> attackChainNodes =
        this.attackChainNodeRepository.findAll(AttackChainNodeSpecification.executable());
    Stream<ExecutableNode> executableAttackChainNodes =
        attackChainNodes.stream()
            .filter(this::isBeforeOrEqualsNow)
            .sorted(AttackChainNode.executionComparator)
            .map(this::toExecutableNode);
    // Get atomic testing attackChainNodes
    List<AttackChainNode> atomicTests =
        this.attackChainNodeRepository.findAll(AttackChainNodeSpecification.forAtomicTesting());
    Stream<ExecutableNode> executableAtomicTests =
        atomicTests.stream()
            .filter(this::isBeforeOrEqualsNow)
            .sorted(AttackChainNode.executionComparator)
            .map(this::toExecutableNode);
    // Combine attackChainNodes
    return concat(executableAttackChainNodes, executableAtomicTests).collect(Collectors.toList());
  }
}
