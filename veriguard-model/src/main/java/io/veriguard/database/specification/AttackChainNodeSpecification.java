package io.veriguard.database.specification;

import static io.veriguard.database.model.AttackChainRunStatus.RUNNING;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.CollectExecutionStatus;
import io.veriguard.database.model.ExecutionStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

public class AttackChainNodeSpecification {

  private AttackChainNodeSpecification() {}

  // -- FROM PARENT --

  public static Specification<AttackChainNode> fromSimulation(String simulationId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), simulationId);
  }

  public static Specification<AttackChainNode> fromRunningSimulation() {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("status"), RUNNING);
  }

  public static Specification<AttackChainNode> fromAttackChain(String attackChainId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), attackChainId);
  }

  /**
   * Get attackChainNodes from a attackChain or a simulation
   *
   * @param attackChainOrSimulationId the id of the attackChain or the simulation
   * @return the constructed specification
   */
  public static Specification<AttackChainNode> fromAttackChainOrSimulation(
      String attackChainOrSimulationId) {
    if (StringUtils.isBlank(attackChainOrSimulationId)) {
      // Return an empty specification
      return Specification.where(null);
    }
    return fromSimulation(attackChainOrSimulationId).or(fromAttackChain(attackChainOrSimulationId));
  }

  // -- STATUS --

  public static Specification<AttackChainNode> next() {
    return (root, query, cb) -> {
      Path<Object> attackChainRunPath = root.get("exercise");
      return cb.and(
          cb.equal(root.get("enabled"), true), // isEnable
          cb.isNotNull(attackChainRunPath.get("start")), // fromScheduled
          cb.isNull(root.join("status", JoinType.LEFT).get("name")) // notExecuted
          );
    };
  }

  public static Specification<AttackChainNode> executable() {
    return (root, query, cb) -> {
      Path<Object> attackChainRunPath = root.get("exercise");
      return cb.and(
          // cb.notEqual(root.get("type"), ManualContract.TYPE),  // notManual
          cb.equal(root.get("enabled"), true), // isEnable
          cb.isNotNull(attackChainRunPath.get("start")), // fromScheduled
          cb.equal(attackChainRunPath.get("status"), RUNNING), // fromRunningAttackChainRun
          cb.isNull(root.join("status", JoinType.LEFT).get("name")) // notExecuted
          );
    };
  }

  public static Specification<AttackChainNode> forAtomicTesting() {
    return Specification.where(isAtomicTesting())
        .and((root, query, cb) -> cb.equal(root.get("status").get("name"), ExecutionStatus.QUEUING))
        .and(
            (root, query, cb) ->
                cb.notEqual(root.get("status").get("name"), ExecutionStatus.PENDING));
  }

  public static Specification<AttackChainNode> pendingAttackChainNodeWithThresholdMinutes(
      int thresholdMinutes) {
    return (root, query, cb) -> {
      Instant thresholdInstant = Instant.now().minus(Duration.ofMinutes(thresholdMinutes));
      return cb.and(
          cb.equal(root.get("status").get("name"), ExecutionStatus.PENDING),
          cb.lessThan(root.get("status").get("trackingSentDate"), thresholdInstant));
    };
  }

  public static Specification<AttackChainNode> hasStatus(List<ExecutionStatus> statuses) {
    return (root, query, cb) -> root.get("status").get("name").in(statuses);
  }

  public static Specification<AttackChainNode> hasCollectingStatus(
      List<CollectExecutionStatus> statuses) {
    return (root, query, cb) -> root.get("collectExecutionStatus").in(statuses);
  }

  // -- CONTRACT --

  public static Specification<AttackChainNode> fromContract(@NotBlank final String contract) {
    return (root, query, cb) -> cb.equal(root.get("injectorContract").get("id"), contract);
  }

  // -- TEST --

  public static final Set<String> VALID_TESTABLE_TYPES =
      new HashSet<>(Arrays.asList("veriguard_email"));

  public static Specification<AttackChainNode> testable() {
    return (root, query, cb) ->
        root.get("injectorContract").get("injector").get("type").in(VALID_TESTABLE_TYPES);
  }

  public static Specification<AttackChainNode> isAtomicTesting() {
    return (root, query, cb) ->
        cb.and(cb.isNull(root.get("scenario")), cb.isNull(root.get("exercise")));
  }
}
