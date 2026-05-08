package io.veriguard.rest.document.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@JsonInclude(NON_EMPTY)
public class DocumentRelationsOutput {

  private Set<RelatedEntityOutput> simulations;

  private Set<RelatedEntityOutput> securityPlatforms;

  private Set<RelatedEntityOutput> payloads;

  private Set<RelatedEntityOutput> atomicTestings;

  // Preserve wire format inherited from former scenarioInjects field name (Phase 0 invariant: no
  // JSON shape changes).
  @JsonProperty("scenarioInjects")
  private Set<RelatedEntityOutput> chainNodes;

  // Preserve wire format inherited from former simulationInjects field name.
  @JsonProperty("simulationInjects")
  private Set<RelatedEntityOutput> simulationAttackChainNodes;
}
