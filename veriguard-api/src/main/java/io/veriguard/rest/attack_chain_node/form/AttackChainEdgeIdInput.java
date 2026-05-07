package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainEdgeIdInput {

  @JsonProperty("inject_parent_id")
  private String attackChainNodeParentId;

  @JsonProperty("inject_children_id")
  private String attackChainNodeChildrenId;
}
