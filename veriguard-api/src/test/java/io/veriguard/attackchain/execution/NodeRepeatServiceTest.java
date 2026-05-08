package io.veriguard.attackchain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.AttackChainNodeStatus;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.ExecutionMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeRepeatServiceTest {

  @Mock NodeRepeatPlanner planner;

  // ---- handleSettled 路径分发 ----

  @Test
  @DisplayName("REPEAT 决策 → 推进 currentIteration、设 triggerNowDate、清空 status")
  void repeat_applies_side_effects() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainRun run = run(chainWithMode(ExecutionMode.CONTINUE));
    AttackChainNode node = node(run, 0, 3, 30L);
    node.setStatus(stubStatus(node));
    Instant nextStartAt = Instant.parse("2026-05-08T12:00:00Z");
    NodeRepeatPlanner.Decision decision =
        new NodeRepeatPlanner.Decision(
            NodeRepeatPlanner.Decision.Action.REPEAT, 1, nextStartAt, "iter 1/3");
    when(planner.plan(eq(node), any(), eq(ExecutionMode.CONTINUE), any())).thenReturn(decision);

    NodeRepeatPlanner.Decision result = service.handleSettled(node);

    assertThat(result).isEqualTo(decision);
    assertThat(node.getCurrentIteration()).isEqualTo(1);
    assertThat(node.getTriggerNowDate()).isEqualTo(nextStartAt);
    assertThat(node.getStatus()).isEmpty(); // orphan-removed
  }

  @Test
  @DisplayName("FINALIZE 决策 → 不动节点状态")
  void finalize_no_op() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainRun run = run(chainWithMode(ExecutionMode.CONTINUE));
    AttackChainNode node = node(run, 2, 3, 0L);
    AttackChainNodeStatus status = stubStatus(node);
    node.setStatus(status);
    Instant before = node.getTriggerNowDate();
    NodeRepeatPlanner.Decision decision =
        new NodeRepeatPlanner.Decision(
            NodeRepeatPlanner.Decision.Action.FINALIZE, 2, null, "final iter 2/3");
    when(planner.plan(any(), any(), any(), any())).thenReturn(decision);

    NodeRepeatPlanner.Decision result = service.handleSettled(node);

    assertThat(result).isEqualTo(decision);
    assertThat(node.getCurrentIteration()).isEqualTo(2); // 未变
    assertThat(node.getStatus()).contains(status); // 保留
    assertThat(node.getTriggerNowDate()).isEqualTo(before);
  }

  @Test
  @DisplayName("FINALIZE_BLOCKED 决策 → 不动节点状态（链路截停由 StopOnBlockHandler 负责）")
  void finalize_blocked_no_op() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainRun run = run(chainWithMode(ExecutionMode.STOP_ON_BLOCK));
    AttackChainNode node = node(run, 0, 3, 0L);
    AttackChainNodeStatus status = stubStatus(node);
    node.setStatus(status);
    NodeRepeatPlanner.Decision decision =
        new NodeRepeatPlanner.Decision(
            NodeRepeatPlanner.Decision.Action.FINALIZE_BLOCKED,
            0,
            null,
            "stop-on-block early exit");
    when(planner.plan(any(), any(), any(), any())).thenReturn(decision);

    service.handleSettled(node);

    assertThat(node.getCurrentIteration()).isEqualTo(0);
    assertThat(node.getStatus()).contains(status);
  }

  @Test
  @DisplayName("null 节点 → 安全返回 null，不调 planner")
  void null_node_safe() {
    NodeRepeatService service = new NodeRepeatService(planner);
    assertThat(service.handleSettled(null)).isNull();
    verify(planner, never()).plan(any(), any(), any(), any());
  }

  // ---- iterationPrevention 推算 ----

  @Test
  @DisplayName("PREVENTION + SUCCESS expectation → 给 planner 传 SUCCESS")
  void prevention_success_inferred() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainRun run = run(chainWithMode(ExecutionMode.STOP_ON_BLOCK));
    AttackChainNode node = node(run, 0, 3, 0L);
    node.getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    when(planner.plan(any(), any(), any(), any()))
        .thenReturn(
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.FINALIZE_BLOCKED, 0, null, "blocked"));

    service.handleSettled(node);

    ArgumentCaptor<EXPECTATION_STATUS> captor = ArgumentCaptor.forClass(EXPECTATION_STATUS.class);
    verify(planner).plan(eq(node), captor.capture(), eq(ExecutionMode.STOP_ON_BLOCK), any());
    assertThat(captor.getValue()).isEqualTo(EXPECTATION_STATUS.SUCCESS);
  }

  @Test
  @DisplayName("PREVENTION + FAILED expectation → 传 FAILED")
  void prevention_failed_inferred() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainRun run = run(chainWithMode(ExecutionMode.CONTINUE));
    AttackChainNode node = node(run, 0, 3, 0L);
    node.getExpectations().add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.FAILED));
    when(planner.plan(any(), any(), any(), any()))
        .thenReturn(
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.REPEAT,
                1,
                Instant.now().plusSeconds(10),
                "next"));

    service.handleSettled(node);

    ArgumentCaptor<EXPECTATION_STATUS> captor = ArgumentCaptor.forClass(EXPECTATION_STATUS.class);
    verify(planner).plan(eq(node), captor.capture(), any(), any());
    assertThat(captor.getValue()).isEqualTo(EXPECTATION_STATUS.FAILED);
  }

  @Test
  @DisplayName("无 PREVENTION expectation → 传 null（维度不可用）")
  void no_prevention_yields_null() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainRun run = run(chainWithMode(ExecutionMode.CONTINUE));
    AttackChainNode node = node(run, 0, 3, 0L);
    node.getExpectations().add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS));
    when(planner.plan(any(), any(), any(), any()))
        .thenReturn(
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.REPEAT, 1, Instant.now(), "next"));

    service.handleSettled(node);

    ArgumentCaptor<EXPECTATION_STATUS> captor = ArgumentCaptor.forClass(EXPECTATION_STATUS.class);
    verify(planner).plan(eq(node), captor.capture(), any(), any());
    assertThat(captor.getValue()).isNull();
  }

  @Test
  @DisplayName("混合 PREVENTION（一 SUCCESS 一 FAILED）→ SUCCESS 优先（任一拦截即停）")
  void mixed_prevention_success_wins() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainRun run = run(chainWithMode(ExecutionMode.STOP_ON_BLOCK));
    AttackChainNode node = node(run, 0, 3, 0L);
    node.getExpectations().add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.FAILED));
    node.getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    when(planner.plan(any(), any(), any(), any()))
        .thenReturn(
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.FINALIZE_BLOCKED, 0, null, "blocked"));

    service.handleSettled(node);

    ArgumentCaptor<EXPECTATION_STATUS> captor = ArgumentCaptor.forClass(EXPECTATION_STATUS.class);
    verify(planner).plan(any(), captor.capture(), any(), any());
    assertThat(captor.getValue()).isEqualTo(EXPECTATION_STATUS.SUCCESS);
  }

  // ---- ExecutionMode 推算 ----

  @Test
  @DisplayName("链路模板 STOP_ON_BLOCK → 透传给 planner")
  void execution_mode_stop_on_block_passes_through() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainNode node = node(run(chainWithMode(ExecutionMode.STOP_ON_BLOCK)), 0, 3, 0L);
    when(planner.plan(any(), any(), any(), any()))
        .thenReturn(
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.REPEAT, 1, Instant.now(), "next"));

    service.handleSettled(node);

    verify(planner).plan(eq(node), any(), eq(ExecutionMode.STOP_ON_BLOCK), any());
  }

  @Test
  @DisplayName("链路模板 CONTINUE → 透传给 planner")
  void execution_mode_continue_passes_through() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainNode node = node(run(chainWithMode(ExecutionMode.CONTINUE)), 0, 3, 0L);
    when(planner.plan(any(), any(), any(), any()))
        .thenReturn(
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.REPEAT, 1, Instant.now(), "next"));

    service.handleSettled(node);

    verify(planner).plan(eq(node), any(), eq(ExecutionMode.CONTINUE), any());
  }

  @Test
  @DisplayName("template / atomic-testing 节点（无 run）→ 默认 STOP_ON_BLOCK")
  void no_run_defaults_to_stop_on_block() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainNode node = new AttackChainNode();
    node.setId(UUID.randomUUID().toString());
    node.setRepeatCount(3);
    when(planner.plan(any(), any(), any(), any()))
        .thenReturn(
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.FINALIZE, 0, null, "no-op"));

    service.handleSettled(node);

    verify(planner).plan(eq(node), any(), eq(ExecutionMode.STOP_ON_BLOCK), any());
  }

  @Test
  @DisplayName("Run 无 template → 默认 STOP_ON_BLOCK（保守）")
  void run_without_template_defaults_to_stop_on_block() {
    NodeRepeatService service = new NodeRepeatService(planner);
    AttackChainRun run = new AttackChainRun();
    run.setId(UUID.randomUUID().toString());
    AttackChainNode node = node(run, 0, 3, 0L);
    when(planner.plan(any(), any(), any(), any()))
        .thenReturn(
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.FINALIZE, 0, null, "no-op"));

    service.handleSettled(node);

    verify(planner).plan(eq(node), any(), eq(ExecutionMode.STOP_ON_BLOCK), any());
  }

  // ---- helpers ----

  private static AttackChain chainWithMode(ExecutionMode mode) {
    AttackChain c = new AttackChain();
    c.setId(UUID.randomUUID().toString());
    c.setExecutionMode(mode);
    return c;
  }

  private static AttackChainRun run(AttackChain chain) {
    AttackChainRun r = new AttackChainRun();
    r.setId(UUID.randomUUID().toString());
    r.setAttackChain(chain);
    return r;
  }

  private static AttackChainNode node(
      AttackChainRun run, int currentIteration, int repeatCount, long repeatIntervalSeconds) {
    AttackChainNode n = new AttackChainNode();
    n.setId(UUID.randomUUID().toString());
    n.setAttackChainRun(run);
    n.setCurrentIteration(currentIteration);
    n.setRepeatCount(repeatCount);
    n.setRepeatIntervalSeconds(repeatIntervalSeconds);
    n.setExpectations(new ArrayList<>());
    return n;
  }

  private static AttackChainNodeStatus stubStatus(AttackChainNode node) {
    AttackChainNodeStatus s = new AttackChainNodeStatus();
    s.setId(UUID.randomUUID().toString());
    s.setAttackChainNode(node);
    return s;
  }

  private static AttackChainNodeExpectation expectation(
      EXPECTATION_TYPE type, EXPECTATION_STATUS status) {
    AttackChainNodeExpectation e = new AttackChainNodeExpectation();
    e.setType(type);
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
