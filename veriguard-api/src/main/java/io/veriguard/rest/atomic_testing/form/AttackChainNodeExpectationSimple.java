package io.veriguard.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class AttackChainNodeExpectationSimple {

  @JsonProperty("node_expectation_id")
  @NotBlank
  private String id;

  @JsonProperty("node_expectation_name")
  private String name;
}
