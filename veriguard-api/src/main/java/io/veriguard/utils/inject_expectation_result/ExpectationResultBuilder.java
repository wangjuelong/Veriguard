package io.veriguard.utils.inject_expectation_result;

import static io.veriguard.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.*;
import static io.veriguard.expectation.ExpectationType.VULNERABILITY;
import static io.veriguard.service.AttackChainNodeExpectationService.COLLECTOR;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import io.veriguard.database.model.Collector;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.NodeExpectationResult;
import io.veriguard.rest.exercise.form.ExpectationUpdateInput;
import io.veriguard.rest.inject.form.AttackChainNodeExpectationUpdateInput;
import io.veriguard.service.AttackChainNodeExpectationUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for building and managing NodeExpectationResult objects.
 *
 * <p>This class provides methods for:
 *
 * <ul>
 *   <li>Building result objects for different sources (media pressure, manual validation, etc.)
 *   <li>Computing scores from result collections
 *   <li>Managing result lifecycle (add, delete, expire)
 *   <li>Checking result status conditions
 * </ul>
 *
 * <p>Note: For aggregating expectation results by types and computing averages, see {@link
 * io.veriguard.utils.NodeExpectationResultUtils}.
 */
public final class ExpectationResultBuilder {

  private ExpectationResultBuilder() {}

  // Source type and ID constants
  public static final String MEDIA_PRESSURE_SOURCE_ID = "media-pressure";
  public static final String MEDIA_PRESSURE_SOURCE_TYPE = "media-pressure";
  public static final String MEDIA_PRESSURE_SOURCE_NAME = "Media pressure read";

  public static final String PLAYER_MANUAL_VALIDATION_SOURCE_ID = "player-manual-validation";
  public static final String PLAYER_MANUAL_VALIDATION_SOURCE_TYPE = "player-manual-validation";
  public static final String PLAYER_MANUAL_VALIDATION_SOURCE_NAME = "Player Manual Validation";

  public static final String TEAM_MANUAL_VALIDATION_SOURCE_ID = "team-manual-validation";
  public static final String TEAM_MANUAL_VALIDATION_SOURCE_TYPE = "team-manual-validation";
  public static final String TEAM_MANUAL_VALIDATION_SOURCE_NAME = "Team Manual Validation";

  private static final String NOT_APPLICABLE = null;
  private static final String NO_RESULT = null;
  private static final Double NO_SCORE = null;
  private static final String NO_ASSET = null;

  // -- SCORE --

  /**
   * Evaluate overall status from per-source results.
   *
   * <p>* SUCCESS if any expected source reports expectedScore
   *
   * <p>* NO_DATA if no success and at least one expected source is missing
   *
   * <p>* ERROR if no success and all expected sources reported but none matched
   */
  public static Double computeScore(
      @NotNull final List<NodeExpectationResult> results,
      @NotNull final AttackChainNodeExpectation expectation) {
    final Double expectedScore = expectation.getExpectedScore();
    if (expectedScore == null) {
      return null;
    }
    if (hasNoResults(results) || hasAnyEmptyResult(results)) {
      return null;
    }

    return Collections.max(results.stream().map(NodeExpectationResult::getScore).toList());
  }

  // -- SETUP --

  private static NodeExpectationResult setUp(
      @NotNull final String sourceId,
      @NotNull final String sourceName,
      @NotNull final String sourcePlatform,
      @NotNull final String sourceAssetId) {
    return NodeExpectationResult.builder()
        .sourceId(sourceId)
        .sourceType(COLLECTOR)
        .sourceName(sourceName)
        .sourcePlatform(sourcePlatform)
        .sourceAssetId(sourceAssetId)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static List<NodeExpectationResult> setUpFromCollectors(
      @NotNull final List<Collector> collectors) {
    return collectors.stream()
        .map(
            c ->
                setUp(
                    c.getId(),
                    c.getName(),
                    Optional.ofNullable(c.getSecurityPlatform())
                        .map(sp -> sp.getSecurityPlatformType().name())
                        .orElse(null),
                    getSourceAssetId(c)))
        .toList();
  }

  // -- BUILD --

  public static void addResult(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation,
      @NotNull final ExpectationUpdateInput input,
      @NotNull final String resultMsg) {
    NodeExpectationResult existing =
        findResultBySourceId(attackChainNodeExpectation.getResults(), input.getSourceId());
    if (existing != null) {
      existing.setResult(resultMsg);
      existing.setScore(input.getScore());
    } else {
      existing =
          NodeExpectationResult.builder()
              .sourceId(input.getSourceId())
              .sourceType(input.getSourceType())
              .sourceName(input.getSourceName())
              .sourcePlatform(input.getSourcePlatform())
              .sourceAssetId(input.getSourceId())
              .result(resultMsg)
              .date(now().toString())
              .score(input.getScore())
              .build();
      attackChainNodeExpectation.getResults().add(existing);
    }
  }

  public static void addResult(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation,
      @NotNull final AttackChainNodeExpectationUpdateInput input,
      @NotNull final Collector collector) {
    final double score =
        AttackChainNodeExpectationUtils.computeScore(attackChainNodeExpectation, input.getIsSuccess());

    NodeExpectationResult existing =
        findResultBySourceId(attackChainNodeExpectation.getResults(), collector.getId());

    if (existing != null) {
      existing.setResult(input.getResult());
      existing.setScore(score);
      existing.setMetadata(input.getMetadata());
    } else {
      existing =
          NodeExpectationResult.builder()
              .sourceId(collector.getId())
              .sourceType(COLLECTOR)
              .sourceName(collector.getName())
              .sourcePlatform(
                  Optional.ofNullable(collector.getSecurityPlatform())
                      .map(sp -> sp.getSecurityPlatformType().name())
                      .orElse(null))
              .sourceAssetId(getSourceAssetId(collector))
              .result(input.getResult())
              .date(Instant.now().toString())
              .score(score)
              .metadata(input.getMetadata())
              .build();
      attackChainNodeExpectation.getResults().add(existing);
    }
  }

  public static void deleteResult(
      @NotNull final AttackChainNodeExpectation expectation, @NotBlank final String sourceId) {
    expectation.setResults(
        expectation.getResults().stream().filter(r -> !sourceId.equals(r.getSourceId())).toList());

    final Double score = computeScore(expectation.getResults(), expectation);
    expectation.setScore(score);
  }

  private static NodeExpectationResult buildForMediaPressure(
      @Nullable final String result, @Nullable final Double score) {
    return NodeExpectationResult.builder()
        .sourceId(MEDIA_PRESSURE_SOURCE_ID)
        .sourceType(MEDIA_PRESSURE_SOURCE_TYPE)
        .sourceName(MEDIA_PRESSURE_SOURCE_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .sourceAssetId(NO_ASSET)
        .result(result)
        .date(Instant.now().toString())
        .score(score)
        .build();
  }

  private static String getSourceAssetId(Collector collector) {
    return collector.isExternal() && collector.getSecurityPlatform() != null
        ? collector.getSecurityPlatform().getId()
        : null;
  }

  public static NodeExpectationResult buildForMediaPressure(
      @NotNull final AttackChainNodeExpectation attackChainNodeExpectation) {
    return buildForMediaPressure(Instant.now().toString(), attackChainNodeExpectation.getExpectedScore());
  }

  public static NodeExpectationResult buildDefaultForMediaPressure() {
    return buildForMediaPressure(NO_RESULT, NO_SCORE);
  }

  public static NodeExpectationResult buildForVulnerabilityManager(
      @Nullable final String result, @Nullable final Double score) {
    return NodeExpectationResult.builder()
        .sourceId(EXPECTATIONS_VULNERABILITY_COLLECTOR_ID)
        .sourceType(EXPECTATIONS_VULNERABILITY_COLLECTOR_TYPE)
        .sourceName(EXPECTATIONS_VULNERABILITY_COLLECTOR_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .sourceAssetId(NO_ASSET)
        .score(score)
        .result(result)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static NodeExpectationResult buildForVulnerabilityManagerInFailed() {
    return buildForVulnerabilityManager(VULNERABILITY.failureLabel, 0.0);
  }

  public static NodeExpectationResult buildDefaultForVulnerabilityManagerInFailed() {
    return buildForVulnerabilityManager(NO_RESULT, NO_SCORE);
  }

  public static NodeExpectationResult buildDefaultForPlayerManualValidation() {
    return buildForPlayerManualValidation(NO_RESULT, NO_SCORE);
  }

  public static NodeExpectationResult buildForPlayerManualValidation(
      @NotNull final String result, @NotNull final Double score) {
    return NodeExpectationResult.builder()
        .sourceId(PLAYER_MANUAL_VALIDATION_SOURCE_ID)
        .sourceType(PLAYER_MANUAL_VALIDATION_SOURCE_TYPE)
        .sourceName(PLAYER_MANUAL_VALIDATION_SOURCE_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .sourceAssetId(NO_ASSET)
        .result(result)
        .score(score)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static NodeExpectationResult buildForTeamManualValidation(
      @NotNull final String result, @NotNull final Double score) {
    return NodeExpectationResult.builder()
        .sourceId(TEAM_MANUAL_VALIDATION_SOURCE_ID)
        .sourceType(TEAM_MANUAL_VALIDATION_SOURCE_TYPE)
        .sourceName(TEAM_MANUAL_VALIDATION_SOURCE_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .sourceAssetId(NO_ASSET)
        .result(result)
        .score(score)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  // -- CLOSE --

  public static void expireEmptyResults(
      @NotNull final List<NodeExpectationResult> results,
      final Double score,
      final String result) {
    results.stream()
        .filter(r -> !hasText(r.getResult()))
        .forEach(
            r -> {
              r.setScore(score);
              r.setResult(result);
            });
  }

  // -- GETTER --

  public static NodeExpectationResult findResultBySourceId(
      @NotNull final List<NodeExpectationResult> results, @NotBlank final String sourceId) {
    return results.stream().filter(r -> sourceId.equals(r.getSourceId())).findFirst().orElse(null);
  }

  // -- RESULT --

  public static boolean hasNoResult(
      @NotNull final List<NodeExpectationResult> results, @NotBlank final String sourceId) {
    return results.stream()
        .noneMatch(
            r -> {
              if (sourceId.equals(r.getSourceId())) {
                return hasText(r.getResult());
              }
              return false;
            });
  }

  public static boolean hasNoResults(@NotNull final List<NodeExpectationResult> results) {
    return results.isEmpty() || results.stream().noneMatch(r -> hasText(r.getResult()));
  }

  public static boolean hasAnyEmptyResult(@NotNull List<NodeExpectationResult> results) {
    return results.isEmpty() || results.stream().anyMatch(r -> !hasText(r.getResult()));
  }

  public static boolean hasValidResults(@NotNull final List<NodeExpectationResult> results) {
    return !results.isEmpty() && results.stream().allMatch(r -> hasText(r.getResult()));
  }

  public static boolean hasValidResultFromSource(
      @NotNull final List<NodeExpectationResult> results, @NotBlank final String sourceId) {
    return results.stream()
        .anyMatch(r -> sourceId.equals(r.getSourceId()) && hasText(r.getResult()));
  }
}
