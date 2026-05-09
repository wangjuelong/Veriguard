package io.veriguard.attackchain.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.database.model.AttackChainLinkExpectation;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.LinkExpectationStatus;
import io.veriguard.database.model.LinkVerdict;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LinkVerdictCalculatorTest {

  private final LinkVerdictCalculator calculator = new LinkVerdictCalculator();

  // ---- VerdictStats.toVerdict() 计数法判定 ----

  @Test
  @DisplayName("VerdictStats: 0 分母 → N_A")
  void stats_zero_denominator_yields_na() {
    assertThat(new LinkVerdictCalculator.VerdictStats(0, 0).toVerdict()).isEqualTo(LinkVerdict.N_A);
  }

  @Test
  @DisplayName("VerdictStats: 0 分子 → FULL_BREACH")
  void stats_zero_numerator_yields_full_breach() {
    assertThat(new LinkVerdictCalculator.VerdictStats(5, 0).toVerdict())
        .isEqualTo(LinkVerdict.FULL_BREACH);
  }

  @Test
  @DisplayName("VerdictStats: 分子=分母 → FULL_BLOCKED")
  void stats_numerator_equals_denominator_yields_full_blocked() {
    assertThat(new LinkVerdictCalculator.VerdictStats(3, 3).toVerdict())
        .isEqualTo(LinkVerdict.FULL_BLOCKED);
  }

  @Test
  @DisplayName("VerdictStats: 部分命中 → PARTIAL")
  void stats_partial_yields_partial() {
    assertThat(new LinkVerdictCalculator.VerdictStats(5, 2).toVerdict())
        .isEqualTo(LinkVerdict.PARTIAL);
  }

  // ---- compute(AttackChainRun) 完整链路场景 ----

  @Test
  @DisplayName("空 run → N_A / N_A")
  void compute_null_run_returns_na() {
    var result = calculator.compute(null);
    assertThat(result.prevention()).isEqualTo(LinkVerdict.N_A);
    assertThat(result.detection()).isEqualTo(LinkVerdict.N_A);
  }

  @Test
  @DisplayName("无节点 run → N_A / N_A")
  void compute_run_without_nodes_returns_na() {
    var run = run();
    run.setAttackChainNodes(new ArrayList<>());
    var result = calculator.compute(run);
    assertThat(result.prevention()).isEqualTo(LinkVerdict.N_A);
    assertThat(result.detection()).isEqualTo(LinkVerdict.N_A);
  }

  @Test
  @DisplayName("全 PREVENTION SUCCESS / 全 DETECTION SUCCESS → FULL_BLOCKED / FULL_BLOCKED")
  void compute_all_blocked_and_detected() {
    var run = runWithNodes(2);
    run.getAttackChainNodes()
        .forEach(
            n -> {
              n.getExpectations()
                  .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
              n.getExpectations()
                  .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS));
            });

    var result = calculator.compute(run);

    assertThat(result.prevention()).isEqualTo(LinkVerdict.FULL_BLOCKED);
    assertThat(result.detection()).isEqualTo(LinkVerdict.FULL_BLOCKED);
  }

  @Test
  @DisplayName("全 PREVENTION FAILED / 全 DETECTION FAILED → FULL_BREACH / FULL_BREACH")
  void compute_all_breached_and_undetected() {
    var run = runWithNodes(2);
    run.getAttackChainNodes()
        .forEach(
            n -> {
              n.getExpectations()
                  .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.FAILED));
              n.getExpectations()
                  .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.FAILED));
            });

    var result = calculator.compute(run);

    assertThat(result.prevention()).isEqualTo(LinkVerdict.FULL_BREACH);
    assertThat(result.detection()).isEqualTo(LinkVerdict.FULL_BREACH);
  }

  @Test
  @DisplayName("混合 PREVENTION（1 SUCCESS + 1 FAILED）→ PARTIAL")
  void compute_mixed_prevention_partial() {
    var run = runWithNodes(2);
    run.getAttackChainNodes()
        .get(0)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    run.getAttackChainNodes()
        .get(1)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.FAILED));

    var result = calculator.compute(run);

    assertThat(result.prevention()).isEqualTo(LinkVerdict.PARTIAL);
    assertThat(result.detection()).isEqualTo(LinkVerdict.N_A);
  }

  @Test
  @DisplayName("仅 DETECTION expectation → PREVENTION=N_A，DETECTION 按计数")
  void compute_detection_only_partial_for_prevention_na() {
    var run = runWithNodes(1);
    run.getAttackChainNodes()
        .get(0)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS));

    var result = calculator.compute(run);

    assertThat(result.prevention()).isEqualTo(LinkVerdict.N_A);
    assertThat(result.detection()).isEqualTo(LinkVerdict.FULL_BLOCKED);
  }

  @Test
  @DisplayName("PREVENTION FULL_BLOCKED + DETECTION PARTIAL")
  void compute_independent_dimensions() {
    var run = runWithNodes(2);
    run.getAttackChainNodes()
        .forEach(
            n -> {
              n.getExpectations()
                  .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
            });
    run.getAttackChainNodes()
        .get(0)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS));
    run.getAttackChainNodes()
        .get(1)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.FAILED));

    var result = calculator.compute(run);

    assertThat(result.prevention()).isEqualTo(LinkVerdict.FULL_BLOCKED);
    assertThat(result.detection()).isEqualTo(LinkVerdict.PARTIAL);
  }

  @Test
  @DisplayName("PARTIAL / PENDING / UNKNOWN expectation status 不算分子（防守方未明确胜）")
  void compute_partial_status_does_not_count_as_blocked() {
    var run = runWithNodes(1);
    run.getAttackChainNodes()
        .get(0)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.PARTIAL));
    run.getAttackChainNodes()
        .get(0)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.PENDING));
    run.getAttackChainNodes()
        .get(0)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.UNKNOWN));

    var result = calculator.compute(run);

    // 3 expectation, 0 SUCCESS → FULL_BREACH（对计数法这些非 SUCCESS 状态都算"未拦下"）
    assertThat(result.prevention()).isEqualTo(LinkVerdict.FULL_BREACH);
  }

  @Test
  @DisplayName("节点 expectations 为 null → 跳过该节点")
  void compute_null_expectations_node_skipped() {
    var run = runWithNodes(2);
    run.getAttackChainNodes().get(0).setExpectations(null);
    run.getAttackChainNodes()
        .get(1)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));

    var result = calculator.compute(run);

    assertThat(result.prevention()).isEqualTo(LinkVerdict.FULL_BLOCKED);
  }

  // ---- Phase 7：链路级 expectations 纳入 DETECTION 维度 ----

  @Test
  @DisplayName("链路级 expectation 全 SUCCESS + 无节点 DETECTION → DETECTION=FULL_BLOCKED")
  void link_expectations_all_success() {
    var run = runWithNodes(0);
    var links =
        List.of(linkExp(LinkExpectationStatus.SUCCESS), linkExp(LinkExpectationStatus.SUCCESS));

    var result = calculator.compute(run, links);

    assertThat(result.detection()).isEqualTo(LinkVerdict.FULL_BLOCKED);
    assertThat(result.prevention()).isEqualTo(LinkVerdict.N_A);
  }

  @Test
  @DisplayName("链路级 expectation 全 FAILED → DETECTION=FULL_BREACH")
  void link_expectations_all_failed() {
    var run = runWithNodes(0);
    var links =
        List.of(linkExp(LinkExpectationStatus.FAILED), linkExp(LinkExpectationStatus.FAILED));

    var result = calculator.compute(run, links);

    assertThat(result.detection()).isEqualTo(LinkVerdict.FULL_BREACH);
  }

  @Test
  @DisplayName("链路级 expectation 混合（SUCCESS + UNKNOWN）→ DETECTION=PARTIAL")
  void link_expectations_partial() {
    var run = runWithNodes(0);
    var links =
        List.of(linkExp(LinkExpectationStatus.SUCCESS), linkExp(LinkExpectationStatus.UNKNOWN));

    var result = calculator.compute(run, links);

    assertThat(result.detection()).isEqualTo(LinkVerdict.PARTIAL);
  }

  @Test
  @DisplayName("节点级 + 链路级 DETECTION 合并：节点全 SUCCESS + 链路 1 FAILED → PARTIAL")
  void link_and_node_detection_merge() {
    var run = runWithNodes(2);
    run.getAttackChainNodes()
        .forEach(
            n ->
                n.getExpectations()
                    .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS)));
    var links = List.of(linkExp(LinkExpectationStatus.FAILED));

    var result = calculator.compute(run, links);

    // 节点级 2 SUCCESS / 2，链路级 0 / 1 → 总 2 / 3 → PARTIAL
    assertThat(result.detection()).isEqualTo(LinkVerdict.PARTIAL);
  }

  @Test
  @DisplayName("compute(run) 老签名：不传 link expectations 等价于空列表（Phase 5 backward compat）")
  void backward_compat_no_link_param() {
    var run = runWithNodes(1);
    run.getAttackChainNodes()
        .get(0)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS));

    assertThat(calculator.compute(run)).isEqualTo(calculator.compute(run, List.of()));
  }

  @Test
  @DisplayName("null link expectations 列表 → 退化到只看节点级")
  void null_link_expectations_safe() {
    var run = runWithNodes(1);
    run.getAttackChainNodes()
        .get(0)
        .getExpectations()
        .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS));

    var result = calculator.compute(run, null);

    assertThat(result.detection()).isEqualTo(LinkVerdict.FULL_BLOCKED);
  }

  // ---- helpers ----

  private static AttackChainLinkExpectation linkExp(LinkExpectationStatus status) {
    AttackChainLinkExpectation e = new AttackChainLinkExpectation();
    e.setId(java.util.UUID.randomUUID());
    e.setStatus(status);
    return e;
  }

  private static AttackChainRun run() {
    var r = new AttackChainRun();
    r.setId(UUID.randomUUID().toString());
    return r;
  }

  private static AttackChainRun runWithNodes(int count) {
    var r = run();
    List<AttackChainNode> nodes = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      AttackChainNode n = new AttackChainNode();
      n.setId(UUID.randomUUID().toString());
      n.setAttackChainRun(r);
      n.setExpectations(new ArrayList<>());
      nodes.add(n);
    }
    r.setAttackChainNodes(nodes);
    return r;
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
