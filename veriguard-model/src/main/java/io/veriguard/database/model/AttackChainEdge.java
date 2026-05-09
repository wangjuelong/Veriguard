package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 攻击链边（spec §2.2.3）.
 *
 * <p>V3 后从复合主键 (parent_id, child_id) 改为单列 UUID {@code edge_id} + UNIQUE (parent_node_id, child_node_id)，
 * 为后续 ConditionNode 关系拆分腾出空间。
 */
@Setter
@Getter
@Entity
@Table(name = "attack_chain_edges")
public class AttackChainEdge {

  @Id
  @Column(name = "edge_id")
  @JsonProperty("edge_id")
  private UUID edgeId;

  @ManyToOne
  @JoinColumn(name = "parent_node_id", referencedColumnName = "node_id")
  @JsonProperty("node_parent_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private AttackChainNode attackChainNodeParent;

  @ManyToOne
  @JoinColumn(name = "child_node_id", referencedColumnName = "node_id")
  @JsonProperty("node_children_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private AttackChainNode attackChainNodeChildren;

  // -- 兼容旧 JSON wire format 的 condition 字段 --
  @Column(name = "edge_condition")
  @JsonProperty("dependency_condition")
  @Type(JsonType.class)
  private AttackChainEdgeConditions.AttackChainEdgeCondition attackChainEdgeCondition;

  @CreationTimestamp
  @Column(name = "edge_created_at")
  @JsonProperty("dependency_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "edge_updated_at")
  @JsonProperty("dependency_updated_at")
  private Instant updateDate;

  @PrePersist
  void ensureEdgeId() {
    if (edgeId == null) {
      edgeId = UUID.randomUUID();
    }
  }

  /**
   * 兼容旧代码：暴露伪 compositeId 给 {@link AttackChainNode#dependsOn} 等 mappedBy 关系。新建议直接用
   * {@link #getAttackChainNodeParent()} / {@link #getAttackChainNodeChildren()}。
   */
  @JsonProperty("dependency_relationship")
  @Transient
  public AttackChainEdgeId getCompositeId() {
    AttackChainEdgeId id = new AttackChainEdgeId();
    id.setAttackChainNodeParent(attackChainNodeParent);
    id.setAttackChainNodeChildren(attackChainNodeChildren);
    return id;
  }

  /** Setter 兼容：{@code edge.setCompositeId(id)} 实际上更新 parent/child 字段。 */
  public void setCompositeId(AttackChainEdgeId id) {
    if (id == null) {
      this.attackChainNodeParent = null;
      this.attackChainNodeChildren = null;
      return;
    }
    this.attackChainNodeParent = id.getAttackChainNodeParent();
    this.attackChainNodeChildren = id.getAttackChainNodeChildren();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttackChainEdge that = (AttackChainEdge) o;
    if (edgeId != null && that.edgeId != null) return edgeId.equals(that.edgeId);
    return Objects.equals(attackChainNodeParent, that.attackChainNodeParent)
        && Objects.equals(attackChainNodeChildren, that.attackChainNodeChildren);
  }

  @Override
  public int hashCode() {
    return Objects.hash(edgeId, attackChainNodeParent, attackChainNodeChildren);
  }
}
