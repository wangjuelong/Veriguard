package io.veriguard.utils.fixtures;

import static io.veriguard.database.model.Command.COMMAND_TYPE;

import io.veriguard.database.model.ExecutionStatus;
import io.veriguard.database.model.AttackChainNodeStatus;
import io.veriguard.database.model.PayloadCommandBlock;
import io.veriguard.database.model.StatusPayload;
import java.time.Instant;
import java.util.List;

public class AttackChainNodeStatusFixture {

  private static AttackChainNodeStatus createAttackChainNodeStatus(ExecutionStatus status) {
    AttackChainNodeStatus attackChainNodeStatus = new AttackChainNodeStatus();
    attackChainNodeStatus.setTrackingSentDate(Instant.now());
    attackChainNodeStatus.setName(status);
    attackChainNodeStatus.setPayloadOutput(
        new StatusPayload(
            null,
            null,
            COMMAND_TYPE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(new PayloadCommandBlock("cmd", "content", List.of("clean cmd"))),
            "cmd"));
    return attackChainNodeStatus;
  }

  public static AttackChainNodeStatus createPendingAttackChainNodeStatus() {
    return createAttackChainNodeStatus(ExecutionStatus.PENDING);
  }

  public static AttackChainNodeStatus createDraftAttackChainNodeStatus() {
    return createAttackChainNodeStatus(ExecutionStatus.DRAFT);
  }

  public static AttackChainNodeStatus createQueuingAttackChainNodeStatus() {
    return createAttackChainNodeStatus(ExecutionStatus.QUEUING);
  }

  public static AttackChainNodeStatus createSuccessStatus() {
    return createAttackChainNodeStatus(ExecutionStatus.SUCCESS);
  }
}
