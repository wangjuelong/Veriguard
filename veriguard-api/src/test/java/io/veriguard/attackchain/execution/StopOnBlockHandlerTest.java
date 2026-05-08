package io.veriguard.attackchain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainRunStatus;
import io.veriguard.database.model.ExecutionMode;
import io.veriguard.database.model.NodeState;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainRunRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StopOnBlockHandlerTest {

  @Mock AttackChainNodeRepository nodeRepository;
  @Mock AttackChainRunRepository runRepository;
  @InjectMocks StopOnBlockHandler handler;

  // ---- handlePotentialBlock 入口 ----

  @Test
  @DisplayName("PREVENTION + SUCCESS + STOP_ON_BLOCK → 触发截停")
  void prevention_success_stop_on_block_triggers() {
    var template = chainWithMode(ExecutionMode.STOP_ON_BLOCK);
    var run = run(AttackChainRunStatus.RUNNING, template);
    var trigger = node("trigger", run, NodeState.RUNNING);
    var pending = node("pending", run, NodeState.PENDING);
    var scheduled = node("scheduled", run, NodeState.SCHEDULED);
    var alreadyRunning = node("running", run, NodeState.RUNNING);
    var alreadySettled = node("settled", run, NodeState.SETTLED);

    when(nodeRepository.findByAttackChainRunId(run.getId()))
        .thenReturn(List.of(trigger, pending, scheduled, alreadyRunning, alreadySettled));

    boolean fired = handler.handlePotentialBlock(trigger, expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));

    assertThat(fired).isTrue();
    assertThat(run.getStatus()).isEqualTo(AttackChainRunStatus.STOPPED_ON_BLOCK);
    assertThat(pending.getNodeState()).isEqualTo(NodeState.SKIPPED);
    assertThat(scheduled.getNodeState()).isEqualTo(NodeState.SKIPPED);
    // 已 RUNNING / SETTLED 不动
    assertThat(alreadyRunning.getNodeState()).isEqualTo(NodeState.RUNNING);
    assertThat(alreadySettled.getNodeState()).isEqualTo(NodeState.SETTLED);
    verify(runRepository).save(run);
    verify(nodeRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("PREVENTION + FAILED → 不触发（攻击没被拦下）")
  void prevention_failed_does_not_trigger() {
    var template = chainWithMode(ExecutionMode.STOP_ON_BLOCK);
    var run = run(AttackChainRunStatus.RUNNING, template);
    var node = node("n1", run, NodeState.RUNNING);

    boolean fired = handler.handlePotentialBlock(node, expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.FAILED));

    assertThat(fired).isFalse();
    assertThat(run.getStatus()).isEqualTo(AttackChainRunStatus.RUNNING);
    verify(runRepository, never()).save(run);
  }

  @Test
  @DisplayName("DETECTION + SUCCESS → 不触发（detect 不等于 block）")
  void detection_success_does_not_trigger() {
    var template = chainWithMode(ExecutionMode.STOP_ON_BLOCK);
    var run = run(AttackChainRunStatus.RUNNING, template);
    var node = node("n1", run, NodeState.RUNNING);

    boolean fired = handler.handlePotentialBlock(node, expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS));

    assertThat(fired).isFalse();
    verify(runRepository, never()).save(run);
  }

  @Test
  @DisplayName("CONTINUE 模式 → 即使被拦也不停")
  void continue_mode_does_not_trigger() {
    var template = chainWithMode(ExecutionMode.CONTINUE);
    var run = run(AttackChainRunStatus.RUNNING, template);
    var node = node("n1", run, NodeState.RUNNING);

    boolean fired = handler.handlePotentialBlock(node, expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));

    assertThat(fired).isFalse();
    assertThat(run.getStatus()).isEqualTo(AttackChainRunStatus.RUNNING);
  }

  @Test
  @DisplayName("Run 已经 STOPPED_ON_BLOCK → 幂等，不重复处理")
  void already_stopped_idempotent() {
    var template = chainWithMode(ExecutionMode.STOP_ON_BLOCK);
    var run = run(AttackChainRunStatus.STOPPED_ON_BLOCK, template);
    var node = node("n1", run, NodeState.RUNNING);

    boolean fired = handler.handlePotentialBlock(node, expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));

    assertThat(fired).isFalse();
    verify(runRepository, never()).save(run);
  }

  @Test
  @DisplayName("Run 已 FINISHED 或 CANCELED → 不触发")
  void terminal_run_no_op() {
    var template = chainWithMode(ExecutionMode.STOP_ON_BLOCK);
    for (AttackChainRunStatus terminal :
        new AttackChainRunStatus[] {
          AttackChainRunStatus.FINISHED, AttackChainRunStatus.CANCELED
        }) {
      var run = run(terminal, template);
      var node = node("n", run, NodeState.RUNNING);

      assertThat(
              handler.handlePotentialBlock(
                  node, expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS)))
          .isFalse();
    }
  }

  @Test
  @DisplayName("template 节点（无 run）→ 不触发")
  void template_node_no_run() {
    var node = new AttackChainNode();
    node.setId(UUID.randomUUID().toString());
    // attackChainRun 保持 null
    boolean fired = handler.handlePotentialBlock(node, expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    assertThat(fired).isFalse();
  }

  @Test
  @DisplayName("null expectation / null node → 安全返回 false")
  void null_inputs_safe() {
    assertThat(handler.handlePotentialBlock(null, null)).isFalse();
    var node = node("n", run(AttackChainRunStatus.RUNNING, chainWithMode(ExecutionMode.STOP_ON_BLOCK)), NodeState.RUNNING);
    assertThat(handler.handlePotentialBlock(node, null)).isFalse();
  }

  // ---- handleNodeBlocked 直接入口 ----

  @Test
  @DisplayName("handleNodeBlocked: 直接调用 + STOP_ON_BLOCK → 触发")
  void handleNodeBlocked_direct() {
    var template = chainWithMode(ExecutionMode.STOP_ON_BLOCK);
    var run = run(AttackChainRunStatus.RUNNING, template);
    var node = node("n", run, NodeState.RUNNING);
    when(nodeRepository.findByAttackChainRunId(run.getId())).thenReturn(List.of(node));

    boolean fired = handler.handleNodeBlocked(node);

    assertThat(fired).isTrue();
    assertThat(run.getStatus()).isEqualTo(AttackChainRunStatus.STOPPED_ON_BLOCK);
  }

  @Test
  @DisplayName("handleNodeBlocked: null 节点 → false")
  void handleNodeBlocked_null() {
    assertThat(handler.handleNodeBlocked(null)).isFalse();
  }

  // ---- helpers ----

  private static AttackChain chainWithMode(ExecutionMode mode) {
    var c = new AttackChain();
    c.setId(UUID.randomUUID().toString());
    c.setExecutionMode(mode);
    return c;
  }

  private static AttackChainRun run(AttackChainRunStatus status, AttackChain template) {
    var r = new AttackChainRun();
    r.setId(UUID.randomUUID().toString());
    r.setStatus(status);
    r.setAttackChain(template);
    return r;
  }

  private static AttackChainNode node(String label, AttackChainRun run, NodeState state) {
    var n = new AttackChainNode();
    n.setId(UUID.randomUUID().toString());
    n.setTitle(label);
    n.setAttackChainRun(run);
    n.setNodeState(state);
    return n;
  }

  private static AttackChainNodeExpectation expectation(EXPECTATION_TYPE type, EXPECTATION_STATUS status) {
    var e = new AttackChainNodeExpectation();
    e.setType(type);
    // getResponse() reads score vs expectedScore — set them directly to produce desired status
    switch (status) {
      case SUCCESS -> {
        e.setExpectedScore(100.0);
        e.setScore(100.0);
      }
      case FAILED -> {
        e.setExpectedScore(100.0);
        e.setScore(0.0);
      }
      case PARTIAL -> {
        e.setExpectedScore(100.0);
        e.setScore(50.0);
      }
      case PENDING -> {
        e.setExpectedScore(100.0);
        e.setScore(null);
      }
      case UNKNOWN -> {
        e.setExpectedScore(null);
        e.setScore(null);
      }
    }
    return e;
  }
}
