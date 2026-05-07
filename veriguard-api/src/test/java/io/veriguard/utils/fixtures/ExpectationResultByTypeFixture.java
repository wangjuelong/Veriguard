package io.veriguard.utils.fixtures;

import static io.veriguard.expectation.ExpectationType.*;

import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.veriguard.utils.NodeExpectationResultUtils.ResultDistribution;
import java.util.List;

public class ExpectationResultByTypeFixture {
  public static ExpectationResultsByType createDefaultExpectationResultsByType(
      ExpectationType type,
      AttackChainNodeExpectation.EXPECTATION_STATUS avgResult,
      int successCount,
      int pendingCount,
      int partialCount,
      int failureCount) {
    return new ExpectationResultsByType(
        type,
        avgResult,
        List.of(
            new ResultDistribution(SUCCESS_ID, type.successLabel, successCount),
            new ResultDistribution(PENDING_ID, type.pendingLabel, pendingCount),
            new ResultDistribution(PARTIAL_ID, type.partialLabel, partialCount),
            new ResultDistribution(FAILED_ID, type.failureLabel, failureCount)));
  }
}
