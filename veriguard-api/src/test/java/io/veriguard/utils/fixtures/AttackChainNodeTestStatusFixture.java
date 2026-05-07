package io.veriguard.utils.fixtures;

import io.veriguard.database.model.AttackChainNodeTestStatus;
import io.veriguard.database.model.ExecutionStatus;
import java.time.Instant;

public class AttackChainNodeTestStatusFixture {

  private static AttackChainNodeTestStatus createAttackChainNodeTestStatus(ExecutionStatus status) {
    AttackChainNodeTestStatus attackChainNodeTestStatus = new AttackChainNodeTestStatus();
    attackChainNodeTestStatus.setTrackingSentDate(Instant.now());
    attackChainNodeTestStatus.setName(status);
    return attackChainNodeTestStatus;
  }

  public static AttackChainNodeTestStatus createSuccessAttackChainNodeStatus() {
    return createAttackChainNodeTestStatus(ExecutionStatus.SUCCESS);
  }
}
