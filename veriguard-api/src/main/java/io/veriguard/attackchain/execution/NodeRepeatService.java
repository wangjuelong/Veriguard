package io.veriguard.attackchain.execution;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.ExecutionMode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 节点重复执行调度器接入（PRD §2.4 / spec §3.2）.
 *
 * <p>把 {@link NodeRepeatPlanner} 的纯函数决策接入实际调度循环：
 *
 * <ul>
 *   <li>从节点的 PREVENTION expectations 推算"刚结算这次迭代是否被防御工具拦下"
 *   <li>从节点所属 run/template 拿 {@link ExecutionMode}
 *   <li>调用 {@link NodeRepeatPlanner#plan} 决定 REPEAT / FINALIZE / FINALIZE_BLOCKED
 *   <li>REPEAT 时：推进 {@code currentIteration}、设 {@code triggerNowDate} 为下次起始时刻，并 orphan-remove 当前
 *       status 行 —— 让 {@code AttackChainNodeSpecification.executable()} spec 在下个调度 tick 重新选中
 *       该节点（spec 用 {@code status.name IS NULL} 过滤未执行节点）
 *   <li>FINALIZE / FINALIZE_BLOCKED：保留 status 终态；链路截停由 {@link StopOnBlockHandler} 在 expectation
 *       更新时已完成，本服务只负责"本节点不再重复"
 * </ul>
 *
 * <p>调用方：{@code AttackChainNodeStatusService.updateFinalAttackChainNodeStatus} 在节点 status 进入终态后
 * 调用本服务的 {@link #handleSettled(AttackChainNode)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NodeRepeatService {

  private final NodeRepeatPlanner planner;

  /**
   * 节点结算后调用：决定下一步动作并应用 REPEAT 路径的 side-effects（推进迭代号、清空 status、设下次起始时刻）。
   *
   * <p>必须在已开启的事务内调用 —— REPEAT 路径依赖 {@link AttackChainNode#status} 的 {@code orphanRemoval=true} 在
   * flush 时删除旧 status 行。
   *
   * @param node 刚被结算到终态的节点（未持久化也可，调用方负责后续 save）
   * @return 规划器决议；{@code null} 表示 node 为 null 不予处理
   */
  public NodeRepeatPlanner.Decision handleSettled(AttackChainNode node) {
    if (node == null) {
      return null;
    }
    EXPECTATION_STATUS iterationPrevention = computeIterationPrevention(node);
    ExecutionMode mode = resolveExecutionMode(node);
    NodeRepeatPlanner.Decision decision =
        planner.plan(node, iterationPrevention, mode, Instant.now());

    switch (decision.action()) {
      case REPEAT -> applyRepeat(node, decision);
      case FINALIZE, FINALIZE_BLOCKED ->
          log.debug("Node {} {} ({})", node.getId(), decision.action(), decision.reason());
    }
    return decision;
  }

  /**
   * REPEAT side-effects（包级别可见，便于单测验证）：
   *
   * <ul>
   *   <li>{@code currentIteration} 推进到 {@link NodeRepeatPlanner.Decision#nextIteration()}
   *   <li>{@code triggerNowDate} 设为 {@link NodeRepeatPlanner.Decision#nextStartAt()}（决定调度延迟）
   *   <li>当前 status 行被 orphan-remove（设为 {@code null}），让下次调度 tick 重新拉起
   * </ul>
   */
  void applyRepeat(AttackChainNode node, NodeRepeatPlanner.Decision decision) {
    node.setCurrentIteration(decision.nextIteration());
    if (decision.nextStartAt() != null) {
      node.setTriggerNowDate(decision.nextStartAt());
    }
    // OneToOne mappedBy + orphanRemoval=true → 旧 status 行在事务 flush 时删除
    node.setStatus(null);
    log.info(
        "Node {} REPEAT → iteration {} at {} ({})",
        node.getId(),
        decision.nextIteration(),
        decision.nextStartAt(),
        decision.reason());
  }

  /**
   * 从节点的 PREVENTION 类型 expectations 推算"刚结算这次迭代是否被拦下"：任意一个 PREVENTION expectation 处于 {@link
   * EXPECTATION_STATUS#SUCCESS} 即视为本次迭代被拦下（{@link EXPECTATION_STATUS#SUCCESS} → SUCCESS 信号）；若节点根本没有
   * PREVENTION expectation，返回 {@code null} 让规划器知道维度不可用。
   *
   * <p>限制：当前实现看的是节点上所有 PREVENTION expectations，没有按迭代分桶（节点上没有"本次迭代"的字段）。 这与 spec §3.2
   * 决定"重复执行下任意一次拦下即停"的语义一致 —— 一旦本节点曾经被拦截过，REPEAT 就会 变成 FINALIZE_BLOCKED。后续若需要"按迭代独立判定"，可加
   * expectation 上的迭代号字段重做。
   */
  private EXPECTATION_STATUS computeIterationPrevention(AttackChainNode node) {
    List<AttackChainNodeExpectation> expectations = node.getExpectations();
    if (expectations == null || expectations.isEmpty()) {
      return null;
    }
    boolean anyBlocked = false;
    boolean hasPrevention = false;
    for (AttackChainNodeExpectation e : expectations) {
      if (e.getType() != EXPECTATION_TYPE.PREVENTION) {
        continue;
      }
      hasPrevention = true;
      if (e.getResponse() == EXPECTATION_STATUS.SUCCESS) {
        anyBlocked = true;
        break;
      }
    }
    if (!hasPrevention) {
      return null;
    }
    return anyBlocked ? EXPECTATION_STATUS.SUCCESS : EXPECTATION_STATUS.FAILED;
  }

  /**
   * 取节点所属链路模板的执行模式；template 节点 / atomic-testing 节点（无 run 或 run 无 template）默认按 {@link
   * ExecutionMode#STOP_ON_BLOCK}（保守：被拦就停 repeat）。
   */
  private ExecutionMode resolveExecutionMode(AttackChainNode node) {
    AttackChainRun run = node.getAttackChainRun();
    if (run == null) {
      return ExecutionMode.STOP_ON_BLOCK;
    }
    AttackChain template = run.getAttackChain();
    if (template == null || template.getExecutionMode() == null) {
      return ExecutionMode.STOP_ON_BLOCK;
    }
    return template.getExecutionMode();
  }
}
