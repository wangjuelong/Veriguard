package io.veriguard.rest.attack_chain_run.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.AttackChainRunStatus;

public class AttackChainRunUpdateStatusInput {
  @JsonProperty("attack_chain_run_status")
  private AttackChainRunStatus status;

  public AttackChainRunStatus getStatus() {
    return status;
  }

  public void setStatus(AttackChainRunStatus status) {
    this.status = status;
  }
}
