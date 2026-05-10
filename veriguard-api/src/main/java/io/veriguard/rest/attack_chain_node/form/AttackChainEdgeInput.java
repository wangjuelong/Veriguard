package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.AttackChainEdge;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.EdgeCondition;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainEdgeInput {

  @JsonProperty("dependency_relationship")
  private AttackChainEdgeIdInput relationship;

  /** sealed 递归 {@link EdgeCondition}（Phase 12b-B3.5b：完全替换旧扁平 AttackChainEdgeCondition）。 */
  @JsonProperty("dependency_condition")
  private EdgeCondition conditions;

  public AttackChainEdge toAttackChainEdge(
      @NotNull final AttackChainNode attackChainNode,
      @NotNull final AttackChainNode attackChainNodeParent) {
    AttackChainEdge dependency = new AttackChainEdge();
    dependency.setAttackChainEdgeCondition(this.getConditions());
    dependency.setAttackChainNodeChildren(attackChainNode);
    dependency.setAttackChainNodeParent(attackChainNodeParent);
    return dependency;
  }
}
