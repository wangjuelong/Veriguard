package io.veriguard.attackchain.execution;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainRunStatus;
import io.veriguard.database.model.ExecutionMode;
import io.veriguard.database.model.NodeState;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainRunRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stop-on-block 钩子（PRD §2.4 拦截后停止 / spec §3.3）.
 *
 * <p>当某个 PREVENTION 类型的 expectation 达到 SUCCESS 时（防御工具拦下了攻击），如果链路模板的执行模式
 * 是 {@link ExecutionMode#STOP_ON_BLOCK}，立即截停整条 run：
 *
 * <ul>
 *   <li>把 {@link AttackChainRun#status} 置为 {@link AttackChainRunStatus#STOPPED_ON_BLOCK}
 *   <li>把所有 {@link NodeState#PENDING} / {@link NodeState#SCHEDULED} 节点置为 {@link NodeState#SKIPPED}
 *   <li>已 {@link NodeState#RUNNING} 的节点放任自然结束（不强制 abort，避免半执行的攻击残留）
 * </ul>
 *
 * <p>幂等：如果 run 已经是 STOPPED_ON_BLOCK / FINISHED / CANCELED，跳过；多次触发安全。
 *
 * <p>调用方：通常由 {@code AttackChainNodeExpectationService.updateAttackChainNodeExpectation} 在
 * PREVENTION expectation 完成更新后触发；测试中可单独调用 {@link #handlePotentialBlock(AttackChainNode,
 * AttackChainNodeExpectation)}。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StopOnBlockHandler {

  private final AttackChainNodeRepository nodeRepository;
  private final AttackChainRunRepository runRepository;

  /**
   * 检查并应用 stop-on-block。**当 PREVENTION expectation 进入 SUCCESS 时调用。**
   *
   * @param node 该 expectation 所属节点（必须挂在 run 上，不是 template / atomic-testing）
   * @param expectation 刚结算的 expectation；只有 PREVENTION + SUCCESS 才会触发停止
   * @return true 表示触发了 stop-on-block；false 表示未触发（不满足条件 / run 已结束等）
   */
  @Transactional
  public boolean handlePotentialBlock(
      AttackChainNode node, AttackChainNodeExpectation expectation) {
    // 仅 PREVENTION 维度 + SUCCESS 才考虑停止
    if (expectation == null
        || expectation.getType() != AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION) {
      return false;
    }
    if (expectation.getResponse() != AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS) {
      return false;
    }
    return handleNodeBlocked(node);
  }

  /**
   * 不分维度的入口：调用方已经判定节点被拦截，直接执行截停逻辑。便于 Phase 4+ verdict 计算后批量调用。
   */
  @Transactional
  public boolean handleNodeBlocked(AttackChainNode node) {
    if (node == null) {
      return false;
    }
    AttackChainRun run = node.getAttackChainRun();
    if (run == null) {
      // template 节点 / atomic-testing 节点不参与编排截停
      return false;
    }
    AttackChain template = run.getAttackChain();
    if (template == null) {
      log.warn("AttackChainRun {} has no attached template; skipping stop-on-block", run.getId());
      return false;
    }
    if (template.getExecutionMode() != ExecutionMode.STOP_ON_BLOCK) {
      return false;
    }
    if (isRunTerminal(run.getStatus())) {
      // 幂等：已截停 / 已结束 / 已取消的 run 不重复处理
      return false;
    }

    log.info(
        "STOP_ON_BLOCK triggered: node {} blocked → halting run {}", node.getId(), run.getId());

    // 1. 标记 run 截停
    run.setStatus(AttackChainRunStatus.STOPPED_ON_BLOCK);
    runRepository.save(run);

    // 2. 取消所有 PENDING / SCHEDULED 节点
    List<AttackChainNode> runNodes = nodeRepository.findByAttackChainRunId(run.getId());
    int skippedCount = 0;
    for (AttackChainNode sibling : runNodes) {
      NodeState state = sibling.getNodeState();
      if (state == NodeState.PENDING || state == NodeState.SCHEDULED) {
        sibling.setNodeState(NodeState.SKIPPED);
        skippedCount++;
      }
    }
    if (skippedCount > 0) {
      nodeRepository.saveAll(runNodes);
    }
    log.info(
        "STOP_ON_BLOCK applied: run {} status=STOPPED_ON_BLOCK, {} downstream nodes skipped",
        run.getId(),
        skippedCount);
    return true;
  }

  private static boolean isRunTerminal(AttackChainRunStatus status) {
    return status == AttackChainRunStatus.STOPPED_ON_BLOCK
        || status == AttackChainRunStatus.FINISHED
        || status == AttackChainRunStatus.CANCELED;
  }
}
