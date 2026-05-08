package io.veriguard.attackchain.execution;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.NodeState;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 攻击编排节点状态机（PRD §2.4 Phase 3）.
 *
 * <p>纯转换表实现 —— 不操作 DB、不副作用，便于单元测试 + Phase 3.5 接入 scheduler 时的隔离测试。
 *
 * <p>本实现使用 Phase 1 落地的 6 状态枚举 {@link NodeState}（{@code PENDING / SCHEDULED / RUNNING /
 * SETTLED / SKIPPED / FAILED}）。spec §3.1 定义的 7 状态版本（多一个 AWAITING_EXPECTATION）在 Phase 1
 * 实现里被合并到 RUNNING：从 executor dispatch 到 expectation 稳定都视作 RUNNING，等节点状态服务发现
 * expectation 全部 SETTLED 后再切到 {@link NodeState#SETTLED}。
 *
 * <pre>
 * 初始 → PENDING ─→ SCHEDULED ─→ RUNNING ─┬─→ SETTLED        终态
 *                       │            │     │
 *                       │            │     ├─→ RUNNING (repeat)
 *                       │            │     │
 *                       └─→ SKIPPED  └────→ FAILED            终态
 * </pre>
 *
 * <p>RUNNING → RUNNING 的"自循环"对应 spec §3.2 节点重复执行（每次重复仍处 RUNNING，靠
 * {@link AttackChainNode#getCurrentIteration()} 跟踪迭代序号）。
 */
@Component
public class NodeStateMachine {

  private static final Map<NodeState, Set<NodeState>> ALLOWED_TRANSITIONS =
      Map.of(
          // PENDING 初始状态：可被 scheduler 拉起或直接 SKIP/FAIL
          NodeState.PENDING,
              Set.of(NodeState.SCHEDULED, NodeState.SKIPPED, NodeState.FAILED),
          // SCHEDULED：依赖判定后下发执行；条件不满足直接 SKIP
          NodeState.SCHEDULED,
              Set.of(NodeState.RUNNING, NodeState.SKIPPED, NodeState.FAILED),
          // RUNNING：可结算 / 重复（自环）/ 失败
          NodeState.RUNNING,
              Set.of(NodeState.RUNNING, NodeState.SETTLED, NodeState.FAILED),
          // 终态
          NodeState.SETTLED, Set.of(),
          NodeState.SKIPPED, Set.of(),
          NodeState.FAILED, Set.of());

  /**
   * 是否允许从 {@code from} 状态转到 {@code to}。NULL → PENDING 视作合法初始转换。
   */
  public boolean canTransition(NodeState from, NodeState to) {
    if (to == null) {
      return false;
    }
    if (from == null) {
      return to == NodeState.PENDING;
    }
    return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }

  /**
   * 把节点切换到目标状态。非法转换抛 {@link IllegalStateException}。
   */
  public void transition(AttackChainNode node, NodeState to) {
    NodeState from = node.getNodeState();
    if (!canTransition(from, to)) {
      throw new IllegalStateException(
          "Illegal node state transition: " + from + " → " + to + " (node " + node.getId() + ")");
    }
    node.setNodeState(to);
  }

  /** 节点是否处于终态（不可再转出）。 */
  public boolean isTerminal(NodeState state) {
    return state == NodeState.SETTLED || state == NodeState.SKIPPED || state == NodeState.FAILED;
  }
}
