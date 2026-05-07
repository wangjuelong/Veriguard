package io.veriguard.rest.inject.output;

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

  @JsonProperty("inject_id")
  @NotNull
  private String attackChainNodeId;

  @JsonProperty("inject_title")
  @NotNull
  private String attackChainNodeTitle;

  @JsonProperty("inject_type")
  private String attackChainNodeType;
}
