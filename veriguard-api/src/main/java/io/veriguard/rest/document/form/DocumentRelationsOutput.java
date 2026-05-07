package io.veriguard.rest.document.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonInclude;
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

  private Set<RelatedEntityOutput> attackChainAttackChainNodes;

  private Set<RelatedEntityOutput> simulationAttackChainNodes;
}
