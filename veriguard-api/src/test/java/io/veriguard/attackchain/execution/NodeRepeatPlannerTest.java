package io.veriguard.attackchain.execution;

import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS.FAILED;
import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS.PARTIAL;
import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.attackchain.execution.NodeRepeatPlanner.Decision;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.ExecutionMode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeRepeatPlannerTest {

  private final NodeRepeatPlanner planner = new NodeRepeatPlanner();
  private final Instant t0 = Instant.parse("2026-05-08T12:00:00Z");

  // ---- REPEAT path ----

  @Test
  @DisplayName("CONTINUE mode + iteration 0 of 3 + FAILED → REPEAT next iteration with interval")
  void continue_repeat_basic() {
    var node = node(3, 30); // 3 次重复，30 秒间隔
    node.setCurrentIteration(0);

    var decision = planner.plan(node, FAILED, ExecutionMode.CONTINUE, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.REPEAT);
    assertThat(decision.nextIteration()).isEqualTo(1);
    assertThat(decision.nextStartAt()).isEqualTo(t0.plusSeconds(30));
  }

  @Test
  @DisplayName("CONTINUE mode + last iteration → FINALIZE")
  void continue_finalize_on_last() {
    var node = node(3, 30);
    node.setCurrentIteration(2); // 0-indexed: iteration 2 is the 3rd / last

    var decision = planner.plan(node, FAILED, ExecutionMode.CONTINUE, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.FINALIZE);
    assertThat(decision.nextStartAt()).isNull();
  }

  @Test
  @DisplayName("STOP_ON_BLOCK + PREVENTION FAILED + iter 0/3 → REPEAT")
  void stop_mode_failed_still_repeats() {
    var node = node(3, 0);
    node.setCurrentIteration(0);

    var decision = planner.plan(node, FAILED, ExecutionMode.STOP_ON_BLOCK, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.REPEAT);
    assertThat(decision.nextIteration()).isEqualTo(1);
  }

  // ---- STOP_ON_BLOCK early exit ----

  @Test
  @DisplayName("STOP_ON_BLOCK + PREVENTION SUCCESS at iter 0 → FINALIZE_BLOCKED early exit")
  void stop_mode_success_early_exit() {
    var node = node(5, 60);
    node.setCurrentIteration(0); // 第 1 次就被拦下

    var decision = planner.plan(node, SUCCESS, ExecutionMode.STOP_ON_BLOCK, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.FINALIZE_BLOCKED);
    assertThat(decision.nextIteration()).isEqualTo(0);
    assertThat(decision.nextStartAt()).isNull();
    assertThat(decision.reason()).contains("stop-on-block");
  }

  @Test
  @DisplayName("STOP_ON_BLOCK + PREVENTION SUCCESS at iter 2 of 5 → FINALIZE_BLOCKED")
  void stop_mode_success_midway_exit() {
    var node = node(5, 60);
    node.setCurrentIteration(2);

    var decision = planner.plan(node, SUCCESS, ExecutionMode.STOP_ON_BLOCK, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.FINALIZE_BLOCKED);
    assertThat(decision.nextIteration()).isEqualTo(2);
  }

  @Test
  @DisplayName("CONTINUE + PREVENTION SUCCESS → still REPEAT (CONTINUE 不早停)")
  void continue_mode_success_does_not_early_exit() {
    var node = node(3, 0);
    node.setCurrentIteration(0);

    var decision = planner.plan(node, SUCCESS, ExecutionMode.CONTINUE, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.REPEAT);
  }

  // ---- Boundary cases ----

  @Test
  @DisplayName("repeatCount=1 → 跑完即 FINALIZE，无 REPEAT")
  void single_iteration_finalizes() {
    var node = node(1, 60);
    node.setCurrentIteration(0);

    var decision = planner.plan(node, FAILED, ExecutionMode.CONTINUE, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.FINALIZE);
  }

  @Test
  @DisplayName("repeatCount=0 当作 1 处理（防御性 max(1, n)）")
  void zero_repeat_count_treated_as_1() {
    var node = node(0, 60);
    node.setCurrentIteration(0);

    var decision = planner.plan(node, FAILED, ExecutionMode.CONTINUE, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.FINALIZE);
  }

  @Test
  @DisplayName("repeatIntervalSeconds=0 → next 时刻 = now（无延迟）")
  void zero_interval_no_delay() {
    var node = node(3, 0);
    node.setCurrentIteration(0);

    var decision = planner.plan(node, FAILED, ExecutionMode.CONTINUE, t0);

    assertThat(decision.nextStartAt()).isEqualTo(t0);
  }

  @Test
  @DisplayName("负 interval 当 0 处理")
  void negative_interval_clamped_to_zero() {
    var node = node(3, -100);
    node.setCurrentIteration(0);

    var decision = planner.plan(node, FAILED, ExecutionMode.CONTINUE, t0);

    assertThat(decision.nextStartAt()).isEqualTo(t0);
  }

  @Test
  @DisplayName("PARTIAL 在 STOP_ON_BLOCK 下不触发早停（只有 SUCCESS 才算被拦）")
  void partial_does_not_trigger_early_exit() {
    var node = node(3, 30);
    node.setCurrentIteration(0);

    var decision = planner.plan(node, PARTIAL, ExecutionMode.STOP_ON_BLOCK, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.REPEAT);
  }

  @Test
  @DisplayName("null prevention（节点无 PREVENTION expectation）→ 按状态 NOT_SUCCESS 处理，正常 REPEAT")
  void null_prevention_continues_repeat() {
    var node = node(3, 30);
    node.setCurrentIteration(0);

    var decision = planner.plan(node, null, ExecutionMode.STOP_ON_BLOCK, t0);

    assertThat(decision.action()).isEqualTo(Decision.Action.REPEAT);
  }

  // ---- Argument validation ----

  @Test
  @DisplayName("null node → IllegalArgumentException")
  void null_node_throws() {
    assertThatThrownBy(() -> planner.plan(null, FAILED, ExecutionMode.CONTINUE, t0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("null now → IllegalArgumentException")
  void null_now_throws() {
    assertThatThrownBy(() -> planner.plan(node(3, 0), FAILED, ExecutionMode.CONTINUE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- helpers ----

  private static AttackChainNode node(int repeatCount, long intervalSeconds) {
    var n = new AttackChainNode();
    n.setId(UUID.randomUUID().toString());
    n.setRepeatCount(repeatCount);
    n.setRepeatIntervalSeconds(intervalSeconds);
    return n;
  }
}
