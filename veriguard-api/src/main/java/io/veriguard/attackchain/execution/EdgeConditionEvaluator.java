package io.veriguard.attackchain.execution;

import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.EdgeCondition;
import org.springframework.stereotype.Component;

/**
 * 边条件求值（PRD §2.4 / spec §3.4）.
 *
 * <p>独立纯函数 —— 无 DB / 无副作用 —— C-ready：未来抽出 {@code ConditionNode} 实体时可直接复用同一颗树。
 *
 * <p>给定父节点的 {@link NodeFinalStatus}（PREVENTION / DETECTION / MANUAL 三个维度的终态），递归求值
 * sealed {@link EdgeCondition} 树（Eq / And / Or），返回该边是否可达（即子节点是否应被调度）。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code condition == null} → 无条件，总是可达</li>
 *   <li>{@code Eq(dim, group)} → 父节点在 dim 维度的状态 ∈ group 描述的集合</li>
 *   <li>{@code And(children)} → 所有 children 都可达</li>
 *   <li>{@code Or(children)} → 任一 child 可达</li>
 * </ul>
 */
@Component
public class EdgeConditionEvaluator {

  /**
   * @return true 表示边条件成立（child 应被调度）；false 表示该路径不走（child 在该 parent 视角下被 SKIP，
   *     是否最终 SKIP 还要看其他 parent 的判定）
   */
  public boolean evaluate(EdgeCondition condition, NodeFinalStatus parentStatus) {
    if (condition == null) {
      return true;
    }
    return switch (condition) {
      case EdgeCondition.Eq eq -> evaluateEq(eq, parentStatus);
      case EdgeCondition.And and -> {
        if (and.children() == null || and.children().isEmpty()) {
          yield true; // 空合取视作恒真
        }
        yield and.children().stream().allMatch(c -> evaluate(c, parentStatus));
      }
      case EdgeCondition.Or or -> {
        if (or.children() == null || or.children().isEmpty()) {
          yield false; // 空析取视作恒假
        }
        yield or.children().stream().anyMatch(c -> evaluate(c, parentStatus));
      }
    };
  }

  private boolean evaluateEq(EdgeCondition.Eq eq, NodeFinalStatus parentStatus) {
    if (parentStatus == null) {
      return false;
    }
    EXPECTATION_STATUS actual = parentStatus.forDimension(eq.dimension());
    return matchesGroup(actual, eq.status());
  }

  private boolean matchesGroup(EXPECTATION_STATUS actual, EdgeCondition.ExpectationStatusGroup group) {
    if (actual == null) {
      // 该维度无 expectation —— 视作 PENDING / 无信息
      return group == EdgeCondition.ExpectationStatusGroup.SETTLED ? false : false;
    }
    return switch (group) {
      // ANY_SUCCESS / ALL_SUCCESS 在节点级单值聚合后语义等价（节点 PREVENTION 总状态 == SUCCESS）
      case ANY_SUCCESS, ALL_SUCCESS -> actual == EXPECTATION_STATUS.SUCCESS;
      // ANY_FAILED / ALL_FAILED 同样在节点级单值聚合后等价
      case ANY_FAILED, ALL_FAILED -> actual == EXPECTATION_STATUS.FAILED;
      // SETTLED：状态稳定（不是 PENDING / UNKNOWN），含 SUCCESS/FAILED/PARTIAL
      case SETTLED ->
          actual == EXPECTATION_STATUS.SUCCESS
              || actual == EXPECTATION_STATUS.FAILED
              || actual == EXPECTATION_STATUS.PARTIAL;
    };
  }
}
