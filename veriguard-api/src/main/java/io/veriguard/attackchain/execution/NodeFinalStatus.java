package io.veriguard.attackchain.execution;

import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;

/**
 * 节点终态聚合（spec §3.4）—— PREVENTION / DETECTION / MANUAL 三个维度各自的最终状态。
 *
 * <p>由 scheduler 在节点 SETTLED 时计算并传给 {@link EdgeConditionEvaluator}。null 维度表示该节点没有该
 * 维度的 expectation（视作 PENDING）。
 */
public record NodeFinalStatus(
    EXPECTATION_STATUS prevention, EXPECTATION_STATUS detection, EXPECTATION_STATUS manual) {

  public EXPECTATION_STATUS forDimension(io.veriguard.database.model.EdgeCondition.ExpectationDimension dimension) {
    return switch (dimension) {
      case PREVENTION -> prevention;
      case DETECTION -> detection;
      case MANUAL -> manual;
    };
  }
}
