package io.veriguard.utils.fixtures;

import io.veriguard.database.model.ExecutionStatus;
import io.veriguard.database.model.InjectTestStatus;
import java.time.Instant;

public class InjectTestStatusFixture {

  private static InjectTestStatus createInjectTestStatus(ExecutionStatus status) {
    InjectTestStatus injectTestStatus = new InjectTestStatus();
    injectTestStatus.setTrackingSentDate(Instant.now());
    injectTestStatus.setName(status);
    return injectTestStatus;
  }

  public static InjectTestStatus createSuccessInjectStatus() {
    return createInjectTestStatus(ExecutionStatus.SUCCESS);
  }
}
