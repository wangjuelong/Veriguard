package io.veriguard.rest.attack_chain_node.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.rest.atomic_testing.form.AttackChainNodeStatusOutput;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@SuperBuilder
public class AttackChainNodeTestStatusOutput extends AttackChainNodeStatusOutput {

  @JsonProperty("node_id")
  @NotNull
  private String attackChainNodeId;

  @JsonProperty("node_title")
  @NotNull
  private String attackChainNodeTitle;

  @JsonProperty("node_type")
  private String attackChainNodeType;
}
