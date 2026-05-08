package io.veriguard.utils.fixtures;

import io.veriguard.database.raw.RawFinishedAttackChainRunWithAttackChainNodes;
import java.time.Instant;
import java.util.Set;

public class RawFinishedAttackChainRunWithAttackChainNodesFixture {

  private record TestableRawFinishedAttackChainRunWithAttackChainNodes(
      Instant endDate, Set<String> attackChainNodeIds)
      implements RawFinishedAttackChainRunWithAttackChainNodes {

    @Override
    public Instant getExercise_end_date() {
      return endDate;
    }

    @Override
    public Set<String> getInject_ids() {
      return attackChainNodeIds;
    }
  }

  public static RawFinishedAttackChainRunWithAttackChainNodes
      createDefaultRawFinishedAttackChainRunWithAttackChainNodes(
          Instant endDate, Set<String> attackChainNodeIds) {
    return new TestableRawFinishedAttackChainRunWithAttackChainNodes(endDate, attackChainNodeIds);
  }
}
