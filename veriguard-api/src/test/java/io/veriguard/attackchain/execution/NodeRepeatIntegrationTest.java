package io.veriguard.attackchain.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeStatus;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainNodeStatusRepository;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeStatusService;
import io.veriguard.utils.fixtures.AttackChainNodeFixture;
import io.veriguard.utils.fixtures.AttackChainNodeStatusFixture;
import io.veriguard.utils.fixtures.AttackChainRunFixture;
import io.veriguard.utils.fixtures.ExecutionTraceFixture;
import io.veriguard.utils.fixtures.composers.AttackChainNodeComposer;
import io.veriguard.utils.fixtures.composers.AttackChainNodeStatusComposer;
import io.veriguard.utils.fixtures.composers.AttackChainRunComposer;
import io.veriguard.utils.fixtures.composers.ExecutionTraceComposer;
import io.veriguard.utilstest.RabbitMQTestListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 4.5 集成测试：repeatCount=3 + CONTINUE 模式 + 没有 PREVENTION expectation —— 通过连续 3 次 {@link
 * AttackChainNodeStatusService#updateFinalAttackChainNodeStatus} 调用，验证：
 *
 * <ul>
 *   <li>iter 0/1 settle 后 status orphan-removed、currentIteration 推进
 *   <li>iter 2 settle 后 planner FINALIZE：status 保留 + currentIteration 不动
 * </ul>
 */
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
class NodeRepeatIntegrationTest extends IntegrationTest {

  @Autowired private AttackChainRunComposer attackChainRunComposer;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private AttackChainNodeStatusComposer attackChainNodeStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;

  @Autowired private AttackChainNodeRepository attackChainNodeRepository;
  @Autowired private AttackChainNodeStatusRepository attackChainNodeStatusRepository;
  @Autowired private AttackChainNodeStatusService attackChainNodeStatusService;

  @Test
  @DisplayName("repeatCount=3 + 无 PREVENTION → 3 次 settle 后 currentIteration=2 + status 终态")
  void repeatCount_3_iterates_then_finalizes() {
    // -- ARRANGE --
    // 不挂 AttackChain 模板：本场景用 run-only 节点（atomic-style），NodeRepeatService 在 chain==null 时
    // 默认 STOP_ON_BLOCK，但因没有 PREVENTION expectation，规划器走 REPEAT 直到耗尽 repeatCount。
    // 这样既覆盖到了 REPEAT/FINALIZE 路径，又避免引入额外 AttackChain 行影响其他测试 findAll() 顺序。
    AttackChainRun run = AttackChainRunFixture.createRunningAttackAttackChainRun();

    AttackChainNode node = AttackChainNodeFixture.getDefaultAttackChainNode();
    node.setRepeatCount(3);
    node.setRepeatIntervalSeconds(0L);

    AttackChainNodeStatus initialStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();

    attackChainRunComposer
        .forAttackChainRun(run)
        .withAttackChainNode(
            attackChainNodeComposer
                .forAttackChainNode(node)
                .withAttackChainNodeStatus(
                    attackChainNodeStatusComposer
                        .forAttackChainNodeStatus(initialStatus)
                        .withExecutionTrace(
                            executionTraceComposer.forExecutionTrace(
                                ExecutionTraceFixture.createDefaultExecutionTraceComplete()))))
        .persist();
    entityManager.flush();
    entityManager.clear();

    String nodeId = node.getId();

    // -- ACT iteration 0 --
    AttackChainNodeStatus status0 =
        attackChainNodeStatusRepository.findByAttackChainNodeId(nodeId).orElseThrow();
    attackChainNodeStatusService.updateFinalAttackChainNodeStatus(status0);
    entityManager.flush();
    entityManager.clear();

    AttackChainNode reloaded = attackChainNodeRepository.findById(nodeId).orElseThrow();
    assertThat(reloaded.getCurrentIteration())
        .as("iter 0 settle → REPEAT advances currentIteration to 1")
        .isEqualTo(1);
    assertThat(reloaded.getStatus()).as("iter 0 settle → status orphan-removed").isEmpty();

    // -- ACT iteration 1 (fresh status) --
    persistFreshStatus(reloaded);
    AttackChainNodeStatus status1Loaded =
        attackChainNodeStatusRepository.findByAttackChainNodeId(nodeId).orElseThrow();
    attackChainNodeStatusService.updateFinalAttackChainNodeStatus(status1Loaded);
    entityManager.flush();
    entityManager.clear();

    reloaded = attackChainNodeRepository.findById(nodeId).orElseThrow();
    assertThat(reloaded.getCurrentIteration())
        .as("iter 1 settle → REPEAT advances currentIteration to 2")
        .isEqualTo(2);
    assertThat(reloaded.getStatus()).as("iter 1 settle → status orphan-removed").isEmpty();

    // -- ACT iteration 2 (final) --
    persistFreshStatus(reloaded);
    AttackChainNodeStatus status2Loaded =
        attackChainNodeStatusRepository.findByAttackChainNodeId(nodeId).orElseThrow();
    attackChainNodeStatusService.updateFinalAttackChainNodeStatus(status2Loaded);
    entityManager.flush();
    entityManager.clear();

    reloaded = attackChainNodeRepository.findById(nodeId).orElseThrow();
    assertThat(reloaded.getCurrentIteration())
        .as("iter 2 settle → FINALIZE keeps currentIteration at 2")
        .isEqualTo(2);
    assertThat(reloaded.getStatus())
        .as("iter 2 settle → FINALIZE preserves status (no orphan-remove)")
        .isPresent();
  }

  private void persistFreshStatus(AttackChainNode reloaded) {
    AttackChainNodeStatus status =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    status.setAttackChainNode(reloaded);
    var trace = ExecutionTraceFixture.createDefaultExecutionTraceComplete();
    trace.setAttackChainNodeStatus(status);
    status.getTraces().add(trace);
    reloaded.setStatus(status);
    attackChainNodeRepository.save(reloaded);
    entityManager.flush();
    entityManager.clear();
  }
}
