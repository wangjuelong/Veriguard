package io.veriguard.stability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.attackchain.execution.NodeRepeatPlanner;
import io.veriguard.database.model.Asset;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.StabilityTrendSnapshot;
import io.veriguard.database.repository.StabilityTrendSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * StabilityCalculator 单元测试（PR C5 / 招标 §3.3 + §4.2）.
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>N=1 → 不写 snapshot
 *   <li>N=10 全命中 → hit_rate = 1.0
 *   <li>N=10 半命中 → hit_rate = 0.5
 *   <li>N=10 全 miss → hit_rate = 0.0
 *   <li>FINALIZE_BLOCKED 早停 → totalCount = currentIteration + 1
 *   <li>REPEAT 决策 → 不写 snapshot
 *   <li>null node / null decision → 安全空返回
 *   <li>device_id 取首个 asset
 *   <li>hitCount > totalCount 截断保护
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StabilityCalculatorTest {

  @Mock StabilityTrendSnapshotRepository repository;

  // ----- 启用判断 -----

  @Test
  @DisplayName("N=1 (repeatCount=1) → 不写 snapshot（普通验证）")
  void n1_skips_snapshot() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, /* repeatCount */ 1, /* currentIteration */ 0);
    NodeRepeatPlanner.Decision finalize_ =
        new NodeRepeatPlanner.Decision(
            NodeRepeatPlanner.Decision.Action.FINALIZE, 0, null, "single");

    Optional<StabilityTrendSnapshot> result = calc.onNodeSettled(node, finalize_);

    assertThat(result).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("repeatCount=2 启用稳定性 → 写 snapshot")
  void n2_enables_stability() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 2, 1);
    NodeRepeatPlanner.Decision dec =
        new NodeRepeatPlanner.Decision(NodeRepeatPlanner.Decision.Action.FINALIZE, 1, null, "");
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<StabilityTrendSnapshot> result = calc.onNodeSettled(node, dec);

    assertThat(result).isPresent();
    verify(repository).save(any());
  }

  // ----- hit_rate 计算 -----

  @Test
  @DisplayName("N=10 全命中（10 个 PREVENTION SUCCESS）→ hit_rate = 1.0000")
  void all_hits_rate_one() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 10, 9);
    for (int i = 0; i < 10; i++) {
      node.getExpectations()
          .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    }
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    NodeRepeatPlanner.Decision dec =
        new NodeRepeatPlanner.Decision(
            NodeRepeatPlanner.Decision.Action.FINALIZE, 9, null, "final");

    calc.onNodeSettled(node, dec);

    ArgumentCaptor<StabilityTrendSnapshot> captor =
        ArgumentCaptor.forClass(StabilityTrendSnapshot.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getHitCount()).isEqualTo(10);
    assertThat(captor.getValue().getTotalCount()).isEqualTo(10);
    assertThat(captor.getValue().getHitRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
  }

  @Test
  @DisplayName("N=10 半命中（5 SUCCESS + 5 FAILED）→ hit_rate = 0.5000")
  void half_hits_rate_half() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 10, 9);
    for (int i = 0; i < 5; i++) {
      node.getExpectations()
          .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
      node.getExpectations()
          .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.FAILED));
    }
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    NodeRepeatPlanner.Decision dec =
        new NodeRepeatPlanner.Decision(NodeRepeatPlanner.Decision.Action.FINALIZE, 9, null, "");

    calc.onNodeSettled(node, dec);

    ArgumentCaptor<StabilityTrendSnapshot> captor =
        ArgumentCaptor.forClass(StabilityTrendSnapshot.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getHitCount()).isEqualTo(5);
    assertThat(captor.getValue().getTotalCount()).isEqualTo(10);
    assertThat(captor.getValue().getHitRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
  }

  @Test
  @DisplayName("N=10 全 miss（10 PREVENTION FAILED）→ hit_rate = 0.0000")
  void zero_hits_rate_zero() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 10, 9);
    for (int i = 0; i < 10; i++) {
      node.getExpectations()
          .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.FAILED));
    }
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    NodeRepeatPlanner.Decision dec =
        new NodeRepeatPlanner.Decision(NodeRepeatPlanner.Decision.Action.FINALIZE, 9, null, "");

    calc.onNodeSettled(node, dec);

    ArgumentCaptor<StabilityTrendSnapshot> captor =
        ArgumentCaptor.forClass(StabilityTrendSnapshot.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getHitCount()).isEqualTo(0);
    assertThat(captor.getValue().getHitRate()).isEqualByComparingTo(new BigDecimal("0.0000"));
  }

  @Test
  @DisplayName("DETECTION expectation 不计入 hitCount（只统计 PREVENTION）")
  void detection_not_counted() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 5, 4);
    for (int i = 0; i < 5; i++) {
      node.getExpectations()
          .add(expectation(EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS));
    }
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    calc.onNodeSettled(
        node, new NodeRepeatPlanner.Decision(NodeRepeatPlanner.Decision.Action.FINALIZE, 4, null, ""));

    ArgumentCaptor<StabilityTrendSnapshot> captor =
        ArgumentCaptor.forClass(StabilityTrendSnapshot.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getHitCount()).isEqualTo(0);
  }

  // ----- FINALIZE_BLOCKED 路径 -----

  @Test
  @DisplayName("FINALIZE_BLOCKED 早停 → totalCount = currentIteration + 1（不是 repeatCount）")
  void finalize_blocked_uses_current_iteration_plus_one() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    // 配置 N=10，但在第 3 次（currentIteration=2）被早停
    AttackChainNode node = node(null, 10, 2);
    node.getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    calc.onNodeSettled(
        node,
        new NodeRepeatPlanner.Decision(
            NodeRepeatPlanner.Decision.Action.FINALIZE_BLOCKED, 2, null, "blocked"));

    ArgumentCaptor<StabilityTrendSnapshot> captor =
        ArgumentCaptor.forClass(StabilityTrendSnapshot.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getTotalCount()).isEqualTo(3); // 2 + 1
    assertThat(captor.getValue().getHitCount()).isEqualTo(1);
    // hit_rate = 1/3 = 0.3333
    assertThat(captor.getValue().getHitRate()).isEqualByComparingTo(new BigDecimal("0.3333"));
  }

  // ----- REPEAT 中间路径 -----

  @Test
  @DisplayName("REPEAT 决策 → 不写 snapshot（仅终态写）")
  void repeat_no_snapshot() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 10, 3);

    Optional<StabilityTrendSnapshot> result =
        calc.onNodeSettled(
            node,
            new NodeRepeatPlanner.Decision(
                NodeRepeatPlanner.Decision.Action.REPEAT, 4, Instant.now(), "next"));

    assertThat(result).isEmpty();
    verify(repository, never()).save(any());
  }

  // ----- 输入安全 -----

  @Test
  @DisplayName("null node → 空返回，不调 repository")
  void null_node_safe() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    assertThat(
            calc.onNodeSettled(
                null,
                new NodeRepeatPlanner.Decision(
                    NodeRepeatPlanner.Decision.Action.FINALIZE, 0, null, "")))
        .isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("null decision → 空返回")
  void null_decision_safe() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 10, 0);
    assertThat(calc.onNodeSettled(node, null)).isEmpty();
    verify(repository, never()).save(any());
  }

  // ----- device_id 解析 -----

  @Test
  @DisplayName("节点关联 Asset → snapshot.deviceId = 首个 asset.id")
  void device_id_from_first_asset() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 5, 4);
    Asset asset = asset("asset-firewall-01");
    node.setAssets(List.of(asset));
    node.getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    calc.onNodeSettled(
        node, new NodeRepeatPlanner.Decision(NodeRepeatPlanner.Decision.Action.FINALIZE, 4, null, ""));

    ArgumentCaptor<StabilityTrendSnapshot> captor =
        ArgumentCaptor.forClass(StabilityTrendSnapshot.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDeviceId()).isEqualTo("asset-firewall-01");
  }

  @Test
  @DisplayName("节点无 asset → snapshot.deviceId = null")
  void device_id_null_when_no_asset() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 5, 4);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    calc.onNodeSettled(
        node, new NodeRepeatPlanner.Decision(NodeRepeatPlanner.Decision.Action.FINALIZE, 4, null, ""));

    ArgumentCaptor<StabilityTrendSnapshot> captor =
        ArgumentCaptor.forClass(StabilityTrendSnapshot.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getDeviceId()).isNull();
  }

  // ----- hitCount 截断 -----

  @Test
  @DisplayName("hitCount > totalCount → 截断到 totalCount（满足 DB CHECK）")
  void hit_count_truncated_to_total() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 3, 2);
    // 3 次迭代，每次产 2 个 PREVENTION SUCCESS expectation → 累计 6 个 > totalCount=3
    for (int i = 0; i < 6; i++) {
      node.getExpectations()
          .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    }
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    calc.onNodeSettled(
        node, new NodeRepeatPlanner.Decision(NodeRepeatPlanner.Decision.Action.FINALIZE, 2, null, ""));

    ArgumentCaptor<StabilityTrendSnapshot> captor =
        ArgumentCaptor.forClass(StabilityTrendSnapshot.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getHitCount()).isEqualTo(3);
    assertThat(captor.getValue().getTotalCount()).isEqualTo(3);
    assertThat(captor.getValue().getHitRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
  }

  // ----- isStabilityEnabled 边界 -----

  @Test
  @DisplayName("isStabilityEnabled: null / N<2 → false；N≥2 → true")
  void enabled_predicate_boundaries() {
    assertThat(StabilityCalculator.isStabilityEnabled(null)).isFalse();
    assertThat(StabilityCalculator.isStabilityEnabled(node(null, 1, 0))).isFalse();
    assertThat(StabilityCalculator.isStabilityEnabled(node(null, 2, 0))).isTrue();
    assertThat(StabilityCalculator.isStabilityEnabled(node(null, 100, 0))).isTrue();
  }

  // ----- 多次 finalize 不重复（同一节点多次调用） -----

  @Test
  @DisplayName("多次 FINALIZE 各写一行（调用方负责幂等）")
  void multiple_finalize_each_save() {
    StabilityCalculator calc = new StabilityCalculator(repository);
    AttackChainNode node = node(null, 5, 4);
    node.getExpectations()
        .add(expectation(EXPECTATION_TYPE.PREVENTION, EXPECTATION_STATUS.SUCCESS));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    NodeRepeatPlanner.Decision dec =
        new NodeRepeatPlanner.Decision(NodeRepeatPlanner.Decision.Action.FINALIZE, 4, null, "");
    calc.onNodeSettled(node, dec);
    calc.onNodeSettled(node, dec);

    verify(repository, times(2)).save(any());
  }

  // ----- helpers -----

  private static AttackChainNode node(AttackChainRun run, int repeatCount, int currentIteration) {
    AttackChainNode n = new AttackChainNode();
    n.setId(UUID.randomUUID().toString());
    n.setAttackChainRun(run);
    n.setRepeatCount(repeatCount);
    n.setCurrentIteration(currentIteration);
    n.setExpectations(new ArrayList<>());
    n.setAssets(new ArrayList<>());
    return n;
  }

  private static Asset asset(String id) {
    Asset a = new Endpoint(); // Endpoint 是 Asset 的具体子类（@DiscriminatorColumn）
    a.setId(id);
    a.setName("test-" + id);
    return a;
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
