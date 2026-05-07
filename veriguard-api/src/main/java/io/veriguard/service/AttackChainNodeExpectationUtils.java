package io.veriguard.service;

import static io.veriguard.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService.EXPIRED;
import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE.*;
import static io.veriguard.utils.inject_expectation_result.ExpectationResultBuilder.expireEmptyResults;
import static java.util.Optional.ofNullable;

import io.veriguard.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.veriguard.database.model.*;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.expectation.ExpectationPropertiesConfig;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.*;
import io.veriguard.utils.StringUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AttackChainNodeExpectationUtils {

  private AttackChainNodeExpectationUtils() {}

  public static final double FAILED_SCORE_VALUE = 0.0;

  // -- VALIDATION --

  /**
   * Validates that an expectation has meaningful data. An expectation without at least a name,
   * description, or positive score is considered empty/invalid and should not be persisted — see
   * Veriguard-Platform/veriguard#4891.
   */
  public static boolean isExpectationValid(AttackChainNodeExpectation expectation) {
    if (expectation == null) {
      return false;
    }
    return !StringUtils.isBlank(expectation.getName())
        || !StringUtils.isBlank(expectation.getDescription())
        || (expectation.getExpectedScore() != null && expectation.getExpectedScore() > 0);
  }

  // -- SCORE --

  public static double computeScore(
      @NotNull final AttackChainNodeExpectation expectation, final boolean success) {
    return success ? expectation.getExpectedScore() : FAILED_SCORE_VALUE;
  }

  // -- CONVERTER --

  public static AttackChainNodeExpectation expectationConverter(
      @NotNull final ExecutableNode executableAttackChainNode,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    return expectationConverter(
        attackChainNodeExpectation, executableAttackChainNode, expectation, expectationPropertiesConfig);
  }

  public static AttackChainNodeExpectation expectationConverter(
      @NotNull final Team team,
      @NotNull final ExecutableNode executableAttackChainNode,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setTeam(team);
    return expectationConverter(
        attackChainNodeExpectation, executableAttackChainNode, expectation, expectationPropertiesConfig);
  }

  public static AttackChainNodeExpectation expectationConverter(
      @NotNull final Team team,
      @NotNull final User user,
      @NotNull final ExecutableNode executableAttackChainNode,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setTeam(team);
    attackChainNodeExpectation.setUser(user);
    return expectationConverter(
        attackChainNodeExpectation, executableAttackChainNode, expectation, expectationPropertiesConfig);
  }

  private static AttackChainNodeExpectation expectationConverter(
      @NotNull AttackChainNodeExpectation attackChainNodeExpectation,
      @NotNull final ExecutableNode executableAttackChainNode,
      @NotNull final Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {

    attackChainNodeExpectation.setAttackChainRun(executableAttackChainNode.getInjection().getAttackChainRun());
    attackChainNodeExpectation.setAttackChainNode(executableAttackChainNode.getInjection().getAttackChainNode());
    attackChainNodeExpectation.setExpectedScore(expectation.getScore());
    attackChainNodeExpectation.setExpectationGroup(expectation.isExpectationGroup());
    attackChainNodeExpectation.setName(expectation.getName());
    attackChainNodeExpectation.setExpirationTime(
        ofNullable(expectation.getExpirationTime())
            .orElse(expectationPropertiesConfig.getExpirationTimeByType(expectation.type())));

    switch (expectation) {
      case Expectation ignored when expectation.type() == DOCUMENT -> {
        attackChainNodeExpectation.setType(DOCUMENT);
      }
      case Expectation ignored when expectation.type() == TEXT -> {
        attackChainNodeExpectation.setType(TEXT);
      }
      case DetectionExpectation e when expectation.type() == DETECTION -> {
        attackChainNodeExpectation.setDetection(e.getAgent(), e.getAsset(), e.getAssetGroup());
        attackChainNodeExpectation.setSignatures(e.getNodeExpectationSignatures());
      }
      case PreventionExpectation e when expectation.type() == PREVENTION -> {
        attackChainNodeExpectation.setPrevention(e.getAgent(), e.getAsset(), e.getAssetGroup());
        attackChainNodeExpectation.setSignatures(e.getNodeExpectationSignatures());
      }
      case VulnerabilityExpectation e when expectation.type() == VULNERABILITY -> {
        attackChainNodeExpectation.setVulnerability(e.getAgent(), e.getAsset(), e.getAssetGroup());
        attackChainNodeExpectation.setSignatures(e.getNodeExpectationSignatures());
      }
      case ManualExpectation e when expectation.type() == MANUAL -> {
        attackChainNodeExpectation.setManual(e.getAgent(), e.getAsset(), e.getAssetGroup());
        attackChainNodeExpectation.setDescription(e.getDescription());
      }
      default -> throw new IllegalStateException("Unexpected value: " + expectation);
    }
    return attackChainNodeExpectation;
  }

  // -- RULES OF ENGAGEMENT --

  public static void computeScores(
      @NotNull final List<AttackChainNodeExpectation> childrenExpectations,
      @NotNull final List<AttackChainNodeExpectation> parentExpectations,
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation,
      @Nullable final Function<Double, NodeExpectationResult> addResult) {
    @NotNull Double expectedScore = attackChainNodeExpectation.getExpectedScore();
    boolean isGroup = attackChainNodeExpectation.isExpectationGroup();

    final boolean noExpectationScore = noExpectationScore(childrenExpectations);
    final boolean allSuccess =
        allExpectationsMatch(childrenExpectations, score -> score >= expectedScore);
    final boolean anySuccess =
        anyExpectationsMatch(childrenExpectations, score -> score >= expectedScore);
    final boolean allError =
        allExpectationsMatch(childrenExpectations, score -> score < expectedScore);
    final boolean anyError =
        anyExpectationsMatch(childrenExpectations, score -> score < expectedScore);

    parentExpectations.forEach(
        expectation -> {
          if (noExpectationScore) {
            expectation.setScore(null);
            return;
          }

          Double score = null;
          if (isGroup) {
            if (anySuccess) {
              score = AttackChainNodeExpectationUtils.computeScore(expectation, true);
            } else if (allError) {
              score = AttackChainNodeExpectationUtils.computeScore(expectation, false);
            }
          } else {
            if (allSuccess) {
              score = AttackChainNodeExpectationUtils.computeScore(expectation, true);
            } else if (anyError) {
              score = AttackChainNodeExpectationUtils.computeScore(expectation, false);
            }
          }
          expectation.setScore(score);
          if (addResult != null) {
            NodeExpectationResult newResultToAdd = addResult.apply(score);
            Optional<NodeExpectationResult> existingResult =
                expectation.getResults().stream()
                    .filter(result -> newResultToAdd.getSourceId().equals(result.getSourceId()))
                    .findFirst();
            existingResult.ifPresent(
                nodeExpectationResult ->
                    expectation.getResults().remove(nodeExpectationResult));
            expectation.getResults().add(newResultToAdd);

            // IF RESULT TO ADD IS EXPIRATION MANAGER => SO I EXPIRE ALL the attackChainNode expectation with
            // no result to expired
            if (ExpectationsExpirationManagerConfig.COLLECTOR_ID.equals(
                newResultToAdd.getSourceId())) {
              expireEmptyResults(expectation.getResults(), FAILED_SCORE_VALUE, EXPIRED);
            }
          }
        });
  }

  /**
   * Retrieve all asset ids from a stream of attackChainNode expectations
   *
   * @param attackChainNodeExpectations stream of attackChainNode expecations to extract
   * @return distinct asset ids list
   */
  public static Set<String> extractAssetIdsFromAttackChainNodeExpectationsResults(
      @NotNull final Stream<AttackChainNodeExpectation> attackChainNodeExpectations) {
    return attackChainNodeExpectations
        .flatMap(expectation -> expectation.getResults().stream())
        .map(NodeExpectationResult::getSourceAssetId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static boolean noExpectationScore(final List<AttackChainNodeExpectation> expectations) {
    if (expectations == null || expectations.isEmpty()) {
      return true;
    }
    return expectations.stream().map(AttackChainNodeExpectation::getScore).allMatch(Objects::isNull);
  }

  private static boolean allExpectationsMatch(
      final List<AttackChainNodeExpectation> expectations, final Predicate<Double> predicate) {
    if (expectations == null || expectations.isEmpty()) {
      return false;
    }
    return expectations.stream()
        .map(AttackChainNodeExpectation::getScore)
        .allMatch(score -> score != null && predicate.test(score));
  }

  private static boolean anyExpectationsMatch(
      final List<AttackChainNodeExpectation> expectations, final Predicate<Double> predicate) {
    if (expectations == null || expectations.isEmpty()) {
      return false;
    }
    return expectations.stream()
        .map(AttackChainNodeExpectation::getScore)
        .filter(Objects::nonNull)
        .anyMatch(predicate);
  }
}
