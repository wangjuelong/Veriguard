package io.veriguard.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainEdgeInput {

  @JsonProperty("dependency_relationship")
  private AttackChainEdgeIdInput relationship;

  @JsonProperty("dependency_condition")
  private AttackChainEdgeConditions.AttackChainEdgeCondition conditions;

  public AttackChainEdge toAttackChainEdge(
      @NotNull final AttackChainNode attackChainNode, @NotNull final AttackChainNode attackChainNodeParent) {
    AttackChainEdge dependency = new AttackChainEdge();
    dependency.setAttackChainEdgeCondition(this.getConditions());
    dependency.setCompositeId(new AttackChainEdgeId());
    dependency.getCompositeId().setAttackChainNodeChildren(attackChainNode);
    dependency.getCompositeId().setAttackChainNodeParent(attackChainNodeParent);
    return dependency;
  }
}
