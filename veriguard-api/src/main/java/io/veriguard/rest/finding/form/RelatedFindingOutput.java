package io.veriguard.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.rest.attack_chain_run.form.AttackChainRunSimple;
import io.veriguard.rest.attack_chain_node.output.AttackChainNodeSimple;
import io.veriguard.rest.attack_chain.form.AttackChainSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonInclude(NON_NULL)
public class RelatedFindingOutput extends AggregatedFindingOutput {

  @Schema(description = "Inject linked to finding")
  @JsonProperty("finding_inject")
  @NotBlank
  private AttackChainNodeSimple attackChainNode;

  @Schema(description = "Simulation linked to inject")
  @JsonProperty("finding_simulation")
  private AttackChainRunSimple simulation;

  @Schema(description = "Scenario linked to inject")
  @JsonProperty("finding_scenario")
  private AttackChainSimple attackChain;
}
