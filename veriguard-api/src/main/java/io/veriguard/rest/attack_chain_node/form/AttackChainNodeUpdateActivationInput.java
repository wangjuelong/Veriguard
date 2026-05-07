package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AttackChainNodeUpdateActivationInput {

  @JsonProperty("inject_enabled")
  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
