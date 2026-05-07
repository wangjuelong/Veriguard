package io.veriguard.rest.attack_chain.service;

import io.veriguard.database.raw.RawFinishedAttackChainRunWithAttackChainNodes;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.rest.attack_chain.response.GlobalScoreBySimulationEndDate;
import io.veriguard.rest.attack_chain.response.AttackChainStatistic;
import io.veriguard.rest.attack_chain.response.SimulationsResultsLatest;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.veriguard.utils.NodeExpectationResultUtils.ResultDistribution;
import io.veriguard.utils.ResultUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttackChainStatisticService {

  private final AttackChainRunRepository attackChainRunRepository;

  private final ResultUtils resultUtils;

  private static final int GLOBAL_SCORE_PERCENTAGE_NUMBER_OF_DECIMALS = 1;
  private static final int PERCENTAGE_MULTIPLIER = 100;

  public AttackChainStatistic getStatistics(String attackChainId) {
    return new AttackChainStatistic(getSimulationsResultsLatest(attackChainId));
  }

  private SimulationsResultsLatest getSimulationsResultsLatest(String attackChainId) {
    List<FinishedAttackChainRunWithAttackChainNodes> orderedFinishedAttackChainRuns =
        getOrderedFinishedAttackChainRuns(attackChainId);

    Map<ExpectationType, List<GlobalScoreBySimulationEndDate>> globalScoresByExpectationTypes =
        getGlobalScoresByExpectationTypes(orderedFinishedAttackChainRuns);

    return new SimulationsResultsLatest(globalScoresByExpectationTypes);
  }

  private Map<ExpectationType, List<GlobalScoreBySimulationEndDate>>
      getGlobalScoresByExpectationTypes(List<FinishedAttackChainRunWithAttackChainNodes> finishedAttackChainRuns) {

    List<ExpectationTypeAndGlobalScore> allGlobalScores = getAllGlobalScores(finishedAttackChainRuns);

    Map<ExpectationType, List<GlobalScoreBySimulationEndDate>> result = new HashMap<>();

    for (ExpectationType type : ExpectationType.values()) {
      List<GlobalScoreBySimulationEndDate> scores =
          getGlobalScoresForExpectationType(allGlobalScores, type);
      if (!scores.isEmpty()) {
        result.put(type, scores);
      }
    }

    return result;
  }

  private List<ExpectationTypeAndGlobalScore> getAllGlobalScores(
      List<FinishedAttackChainRunWithAttackChainNodes> finishedAttackChainRuns) {
    return finishedAttackChainRuns.stream().flatMap(this::getExpectationTypeAndGlobalScores).toList();
  }

  private Stream<ExpectationTypeAndGlobalScore> getExpectationTypeAndGlobalScores(
      FinishedAttackChainRunWithAttackChainNodes finishedAttackChainRun) {
    return resultUtils.computeGlobalExpectationResults(finishedAttackChainRun.attackChainNodeIds()).stream()
        .map(
            expectationResultByType ->
                getExpectationTypeAndGlobalScore(finishedAttackChainRun, expectationResultByType));
  }

  private static ExpectationTypeAndGlobalScore getExpectationTypeAndGlobalScore(
      FinishedAttackChainRunWithAttackChainNodes finishedAttackChainRun,
      ExpectationResultsByType expectationResultByType) {
    return new ExpectationTypeAndGlobalScore(
        expectationResultByType.type(),
        new GlobalScoreBySimulationEndDate(
            finishedAttackChainRun.endDate(), getPercentageOfAttackChainNodesOnSuccess(expectationResultByType)));
  }

  private static List<GlobalScoreBySimulationEndDate> getGlobalScoresForExpectationType(
      List<ExpectationTypeAndGlobalScore> allGlobalScores, ExpectationType expectationType) {
    return allGlobalScores.stream()
        .filter(typeAndScore -> typeAndScore.expectationType == expectationType)
        .map(typeAndScore -> typeAndScore.globalScoreBySimulationEndDate)
        .toList();
  }

  private List<FinishedAttackChainRunWithAttackChainNodes> getOrderedFinishedAttackChainRuns(String attackChainId) {
    List<RawFinishedAttackChainRunWithAttackChainNodes> rawFinishedAttackChainRuns =
        attackChainRunRepository.rawLatestFinishedAttackChainRunsWithAttackChainNodesByAttackChainId(attackChainId);
    return rawFinishedAttackChainRuns.stream()
        .map(
            attackChainRun ->
                new FinishedAttackChainRunWithAttackChainNodes(
                    attackChainRun.getExercise_end_date(), attackChainRun.getInject_ids()))
        .sorted(Collections.reverseOrder())
        .toList();
  }

  private record ExpectationTypeAndGlobalScore(
      ExpectationType expectationType,
      GlobalScoreBySimulationEndDate globalScoreBySimulationEndDate) {}

  private static float getPercentageOfAttackChainNodesOnSuccess(
      ExpectationResultsByType expectationResultByType) {
    if (expectationResultByType.distribution().isEmpty()) {
      return 0;
    }
    return getRoundedPercentage(expectationResultByType);
  }

  public static float getRoundedPercentage(ExpectationResultsByType expectationResultByType) {
    float percentage =
        ((float) getNumberOfAttackChainNodesOnSuccess(expectationResultByType)
                / getTotalNumberOfAttackChainNodes(expectationResultByType))
            * PERCENTAGE_MULTIPLIER;
    return truncatePercentageDecimals(percentage);
  }

  private static float truncatePercentageDecimals(float percentage) {
    return new BigDecimal(percentage)
        .setScale(GLOBAL_SCORE_PERCENTAGE_NUMBER_OF_DECIMALS, RoundingMode.UP)
        .floatValue();
  }

  private static int getNumberOfAttackChainNodesOnSuccess(ExpectationResultsByType expectationResultByType) {
    return expectationResultByType.distribution().getFirst().value();
  }

  private static int getTotalNumberOfAttackChainNodes(ExpectationResultsByType expectationResultByType) {
    return expectationResultByType.distribution().stream()
        .map(ResultDistribution::value)
        .reduce(0, Integer::sum);
  }

  private record FinishedAttackChainRunWithAttackChainNodes(Instant endDate, Set<String> attackChainNodeIds)
      implements Comparable<FinishedAttackChainRunWithAttackChainNodes> {
    @Override
    public int compareTo(FinishedAttackChainRunWithAttackChainNodes attackChainRun) {
      if (this.endDate.isBefore(attackChainRun.endDate)) return 1;
      if (this.endDate.isAfter(attackChainRun.endDate)) return -1;
      return 0;
    }
  }
}
