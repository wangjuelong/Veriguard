package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainNodeExportTarget {
  @JsonProperty("inject_id")
  private String id;
}
