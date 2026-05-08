package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * 边条件表达式（spec §3.4）.
 *
 * <p>嵌入在 {@code attack_chain_edges.edge_condition} JSONB 列。支持 {@code eq} 原子条件 + {@code and / or} 组合。
 *
 * <p>未来若需要将条件抽出成独立 {@code ConditionNode} 实体（C-ready），原子条件已经按对象表示，可直接当字段映射。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EdgeCondition.Eq.class, name = "eq"),
  @JsonSubTypes.Type(value = EdgeCondition.And.class, name = "and"),
  @JsonSubTypes.Type(value = EdgeCondition.Or.class, name = "or")
})
public sealed interface EdgeCondition permits EdgeCondition.Eq, EdgeCondition.And, EdgeCondition.Or {

  /**
   * 维度 —— PREVENTION / DETECTION / MANUAL.
   *
   * <p>对应 {@link AttackChainNodeExpectation.EXPECTATION_TYPE} 的子集（除去 TEXT/DOCUMENT/ARTICLE/CHALLENGE/VULNERABILITY，那些为 Phase 11.5 已 drop 的演练遗留）。
   */
  enum ExpectationDimension {
    PREVENTION,
    DETECTION,
    MANUAL
  }

  /** 状态聚合分类 —— 单节点 N 个 expectation 的集合状态。 */
  enum ExpectationStatusGroup {
    ANY_SUCCESS,
    ANY_FAILED,
    ALL_SUCCESS,
    ALL_FAILED,
    SETTLED
  }

  /** 原子条件：某节点在某维度上达到某状态聚合。 */
  record Eq(ExpectationDimension dimension, ExpectationStatusGroup status)
      implements EdgeCondition {}

  /** 全部子条件成立。 */
  record And(List<EdgeCondition> children) implements EdgeCondition {}

  /** 任一子条件成立。 */
  record Or(List<EdgeCondition> children) implements EdgeCondition {}
}
