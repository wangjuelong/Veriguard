package io.veriguard.attackchain.execution;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.ExecutionMode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 节点重复执行规划器（PRD §2.4 / spec §3.2）.
 *
 * <p>纯函数 —— 决定"刚结算的这次迭代之后，节点要做什么"：再来一次 / 终结 / 早停。**不操作 DB、不副作用**，便于
 * 调度器在 STOP_ON_BLOCK 钩子之后调用，由调度器把 {@link Decision} 翻译成实际动作。
 *
 * <p>语义（与 spec §3.2 + 问题 6 决议对齐）：
 *
 * <ul>
 *   <li>STOP_ON_BLOCK 模式 + 任一迭代 PREVENTION = SUCCESS → {@link Decision.Action#FINALIZE_BLOCKED}
 *       早停（链路截停由 {@link StopOnBlockHandler} 单独处理；本规划器只决定"本节点不再重复"）
 *   <li>当前迭代 ≥ 配置的总次数 → {@link Decision.Action#FINALIZE} 自然完成
 *   <li>否则 → {@link Decision.Action#REPEAT} 排定下一次（带间隔延迟）
 * </ul>
 */
@Component
public class NodeRepeatPlanner {

  /**
   * 决定刚结算的迭代后下一步动作。
   *
   * @param node 已含 currentIteration / repeatCount / repeatIntervalSeconds 配置
   * @param iterationPrevention 刚结算的这一次迭代的 PREVENTION 维度终态（null 表示节点无 PREVENTION
   *     expectation）
   * @param executionMode 链路模板执行模式（STOP_ON_BLOCK / CONTINUE）
   * @param now 当前时刻；用于算下一次的预定执行时间
   */
  public Decision plan(
      AttackChainNode node,
      EXPECTATION_STATUS iterationPrevention,
      ExecutionMode executionMode,
      Instant now) {
    if (node == null) {
      throw new IllegalArgumentException("node required");
    }
    if (now == null) {
      throw new IllegalArgumentException("now required");
    }

    int total = Math.max(1, node.getRepeatCount());
    int currentIteration = Math.max(0, node.getCurrentIteration());

    // STOP_ON_BLOCK 早停：本次迭代被防御工具拦下了
    if (executionMode == ExecutionMode.STOP_ON_BLOCK
        && iterationPrevention == EXPECTATION_STATUS.SUCCESS) {
      return new Decision(
          Decision.Action.FINALIZE_BLOCKED, currentIteration, null, "stop-on-block early exit");
    }

    // 跑完最后一次
    int nextIteration = currentIteration + 1;
    if (nextIteration >= total) {
      return new Decision(
          Decision.Action.FINALIZE,
          currentIteration,
          null,
          "finalIteration " + currentIteration + "/" + total);
    }

    // 重复一次：算下一次起始时间
    long intervalSec = Math.max(0L, node.getRepeatIntervalSeconds());
    Instant nextStartAt = now.plus(Duration.ofSeconds(intervalSec));
    return new Decision(
        Decision.Action.REPEAT,
        nextIteration,
        nextStartAt,
        "iteration " + nextIteration + "/" + total + " in " + intervalSec + "s");
  }

  /**
   * 规划结果。{@code nextIteration} 在 FINALIZE / FINALIZE_BLOCKED 时是终态迭代号；在 REPEAT 时是即将
   * 开始执行的迭代号（0-based）。{@code nextStartAt} 仅在 REPEAT 时非空。
   */
  public record Decision(Action action, int nextIteration, Instant nextStartAt, String reason) {
    public enum Action {
      /** 节点继续重复，调度器需在 nextStartAt 调度下一次执行 */
      REPEAT,
      /** 节点跑满 repeat_count 次，自然结算到 SETTLED */
      FINALIZE,
      /** STOP_ON_BLOCK 模式下被拦截早停，节点 SETTLED + 链路 stop-on-block 钩子触发 */
      FINALIZE_BLOCKED
    }
  }
}
