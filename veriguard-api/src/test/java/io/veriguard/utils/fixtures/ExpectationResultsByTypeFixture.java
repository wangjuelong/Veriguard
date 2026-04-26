package io.veriguard.utils.fixtures;

import static io.veriguard.expectation.ExpectationType.DETECTION;
import static io.veriguard.expectation.ExpectationType.PREVENTION;
import static io.veriguard.utils.fixtures.ExpectationResultByTypeFixture.createDefaultExpectationResultsByType;

import io.veriguard.database.model.InjectExpectation;
import io.veriguard.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import java.util.List;

public class ExpectationResultsByTypeFixture {

  private static final ExpectationResultsByType exercise1Prevention =
      createDefaultExpectationResultsByType(
          PREVENTION, InjectExpectation.EXPECTATION_STATUS.PARTIAL, 1, 0, 1, 1);
  private static final ExpectationResultsByType exercise1Detection =
      createDefaultExpectationResultsByType(
          DETECTION, InjectExpectation.EXPECTATION_STATUS.SUCCESS, 3, 0, 0, 0);

  private static final ExpectationResultsByType exercise2Prevention =
      createDefaultExpectationResultsByType(
          PREVENTION, InjectExpectation.EXPECTATION_STATUS.FAILED, 0, 0, 0, 1);
  private static final ExpectationResultsByType exercise2Detection =
      createDefaultExpectationResultsByType(
          DETECTION, InjectExpectation.EXPECTATION_STATUS.FAILED, 0, 0, 0, 1);

  public static final List<ExpectationResultsByType> exercise1GlobalScores =
      List.of(exercise1Prevention, exercise1Detection);
  public static final List<ExpectationResultsByType> exercise2GlobalScores =
      List.of(exercise2Prevention, exercise2Detection);
}
