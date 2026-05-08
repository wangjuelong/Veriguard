package io.veriguard.utils.fixtures;

import static io.veriguard.expectation.ExpectationType.DETECTION;
import static io.veriguard.expectation.ExpectationType.PREVENTION;
import static io.veriguard.utils.fixtures.ExpectationResultByTypeFixture.createDefaultExpectationResultsByType;

import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import java.util.List;

public class ExpectationResultsByTypeFixture {

  private static final ExpectationResultsByType attackChainRun1Prevention =
      createDefaultExpectationResultsByType(
          PREVENTION, AttackChainNodeExpectation.EXPECTATION_STATUS.PARTIAL, 1, 0, 1, 1);
  private static final ExpectationResultsByType attackChainRun1Detection =
      createDefaultExpectationResultsByType(
          DETECTION, AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS, 3, 0, 0, 0);

  private static final ExpectationResultsByType attackChainRun2Prevention =
      createDefaultExpectationResultsByType(
          PREVENTION, AttackChainNodeExpectation.EXPECTATION_STATUS.FAILED, 0, 0, 0, 1);
  private static final ExpectationResultsByType attackChainRun2Detection =
      createDefaultExpectationResultsByType(
          DETECTION, AttackChainNodeExpectation.EXPECTATION_STATUS.FAILED, 0, 0, 0, 1);

  public static final List<ExpectationResultsByType> attackChainRun1GlobalScores =
      List.of(attackChainRun1Prevention, attackChainRun1Detection);
  public static final List<ExpectationResultsByType> attackChainRun2GlobalScores =
      List.of(attackChainRun2Prevention, attackChainRun2Detection);
}
