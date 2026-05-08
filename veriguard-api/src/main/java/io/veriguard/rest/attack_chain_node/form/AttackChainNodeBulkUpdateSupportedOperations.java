package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** Represent the supported operations that can be performed in a bulk update of attackChainNodes */
@Getter
public enum AttackChainNodeBulkUpdateSupportedOperations {
  @JsonProperty("add")
  ADD("ADD"),
  @JsonProperty("remove")
  REMOVE("REMOVE"),
  @JsonProperty("replace")
  REPLACE("REPLACE");

  private final String value;

  AttackChainNodeBulkUpdateSupportedOperations(final String value) {
    this.value = value;
  }
}
