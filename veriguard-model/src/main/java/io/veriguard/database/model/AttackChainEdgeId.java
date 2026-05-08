package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class AttackChainEdgeId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @ManyToOne
  @JsonProperty("inject_parent_id")
  @JoinColumn(referencedColumnName = "node_id", name = "parent_node_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private AttackChainNode attackChainNodeParent;

  @ManyToOne
  @JsonProperty("inject_children_id")
  @JoinColumn(referencedColumnName = "node_id", name = "child_node_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private AttackChainNode attackChainNodeChildren;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttackChainEdgeId that = (AttackChainEdgeId) o;
    return attackChainNodeParent.equals(that.attackChainNodeParent)
        && attackChainNodeChildren.equals(that.attackChainNodeChildren);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attackChainNodeParent, attackChainNodeChildren);
  }
}
