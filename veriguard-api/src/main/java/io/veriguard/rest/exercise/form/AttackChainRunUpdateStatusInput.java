package io.veriguard.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.AttackChainRunStatus;

public class AttackChainRunUpdateStatusInput {
  @JsonProperty("exercise_status")
  private AttackChainRunStatus status;

  public AttackChainRunStatus getStatus() {
    return status;
  }

  public void setStatus(AttackChainRunStatus status) {
    this.status = status;
  }
}
