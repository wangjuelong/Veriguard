package io.veriguard.stability;

import io.veriguard.attackchain.execution.NodeRepeatPlanner;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.Asset;
import io.veriguard.database.model.StabilityTrendSnapshot;
import io.veriguard.database.repository.StabilityTrendSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 稳定性引擎（PR C5 / 招标 §3.3 ★1 + §4.2 ★3）.
 *
 * <p>Hook 点：在 {@code NodeRepeatService.handleSettled} 完成节点重复执行决策后调用本服务的 {@link
 * #onNodeSettled(AttackChainNode, NodeRepeatPlanner.Decision)}.
 *
 * <ul>
 *   <li>{@link NodeRepeatPlanner.Decision.Action#REPEAT} —— 中间迭代，**不入库**（等节点最终结算再聚合）.
 *   <li>{@link NodeRepeatPlanner.Decision.Action#FINALIZE} —— 节点跑满 repeat_count 次，写一行快照.
 *   <li>{@link NodeRepeatPlanner.Decision.Action#FINALIZE_BLOCKED} —— STOP_ON_BLOCK 早停，写一行
 *       totalCount = currentIteration + 1 的快照.
 * </ul>
 *
 * <p>启用条件：节点 {@code repeat_count > 1}（N=1 视为普通验证，不产出 snapshot —— 招标 §3.3 验收要点）.
 *
 * <p>命中语义：PREVENTION expectation = SUCCESS 表示"攻击被防御工具识别 / 拦截"，与
 * {@link io.veriguard.attackchain.execution.NodeRepeatService} 现有"任一拦截即停"语义一致.
 *
 * <p>已知局限：当前 expectation list 跨迭代单调累积（不按迭代分桶），所以 hitCount 直接取"截至此刻 PREVENTION =
 * SUCCESS 的 expectation 数量"，本质上是"节点 N 次中被拦截过的次数上限"。这与招标"命中率"定义一致——只要
 * 任一次被识别 / 拦截即视为该次命中. 后续若需按迭代独立命中，应在 expectation 上加 iteration 列重做.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StabilityCalculator {

  /** 招标 §3.3 验收要点：N=1 视为普通验证，不创建 snapshot. */
  public static final int MIN_REPEAT_FOR_STABILITY = 2;

  /** 招标 §3.3 验收要点：N 上限 100. */
  public static final int MAX_REPEAT = 100;

  private final StabilityTrendSnapshotRepository repository;

  /**
   * 节点结算后写快照（仅在 FINALIZE / FINALIZE_BLOCKED 终态时入库一次）.
   *
   * @param node 已被结算的节点
   * @param decision NodeRepeatPlanner 的决议
   * @return 写入的快照（REPEAT / 未启用稳定性 / null 输入 → {@code Optional.empty()}）
   */
  public Optional<StabilityTrendSnapshot> onNodeSettled(
      AttackChainNode node, NodeRepeatPlanner.Decision decision) {
    if (node == null || decision == null) {
      return Optional.empty();
    }
    if (!isStabilityEnabled(node)) {
      return Optional.empty();
    }
    return switch (decision.action()) {
      case REPEAT -> Optional.empty();
      case FINALIZE, FINALIZE_BLOCKED -> Optional.of(persistSnapshot(node, decision));
    };
  }

  /** 节点是否启用稳定性聚合 —— 仅 repeat_count > 1 时启用. */
  public static boolean isStabilityEnabled(AttackChainNode node) {
    return node != null && node.getRepeatCount() >= MIN_REPEAT_FOR_STABILITY;
  }

  private StabilityTrendSnapshot persistSnapshot(
      AttackChainNode node, NodeRepeatPlanner.Decision decision) {
    int hitCount = countPreventionSuccesses(node.getExpectations());
    int totalCount = computeTotalCount(node, decision);
    if (totalCount <= 0) {
      // 防御性：理论上不会发生（FINALIZE 至少跑过一次），但避免除零 / DB CHECK 报错
      log.warn(
          "Skip stability snapshot for node {} —— totalCount={} <= 0",
          node.getId(),
          totalCount);
      throw new IllegalStateException(
          "Stability snapshot requires totalCount > 0 (node=" + node.getId() + ")");
    }
    // hitCount 不应超过 totalCount —— 单调累积的 expectation list 在极端情况下可能 > totalCount
    // （比如同一次迭代有多个 PREVENTION expectation），此时截断到 totalCount 以满足 DB CHECK.
    int boundedHitCount = Math.min(hitCount, totalCount);
    BigDecimal hitRate =
        BigDecimal.valueOf(boundedHitCount)
            .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP);

    StabilityTrendSnapshot snapshot = new StabilityTrendSnapshot();
    snapshot.setAttackChainRun(node.getAttackChainRun());
    snapshot.setAttackChainNode(node);
    snapshot.setDeviceId(resolveDeviceId(node));
    // baselineId 当前不取值，预留 null 字段
    snapshot.setHitCount(boundedHitCount);
    snapshot.setTotalCount(totalCount);
    snapshot.setHitRate(hitRate);
    snapshot.setCapturedAt(Instant.now());

    StabilityTrendSnapshot saved = repository.save(snapshot);
    log.info(
        "Stability snapshot persisted: node={} hit={}/{} rate={} action={}",
        node.getId(),
        boundedHitCount,
        totalCount,
        hitRate,
        decision.action());
    return saved;
  }

  /**
   * 实际跑过的迭代数：
   *
   * <ul>
   *   <li>FINALIZE —— 自然跑满 repeat_count 次，total = repeat_count.
   *   <li>FINALIZE_BLOCKED —— STOP_ON_BLOCK 早停，total = currentIteration + 1（0-based 转 1-based）.
   * </ul>
   */
  static int computeTotalCount(AttackChainNode node, NodeRepeatPlanner.Decision decision) {
    return switch (decision.action()) {
      case FINALIZE -> Math.max(1, node.getRepeatCount());
      case FINALIZE_BLOCKED -> Math.max(1, node.getCurrentIteration() + 1);
      case REPEAT ->
          // 不应到达 —— onNodeSettled 已在外层 short-circuit
          throw new IllegalArgumentException("REPEAT does not produce a snapshot");
    };
  }

  /** 取 PREVENTION 类型 + SUCCESS 状态的 expectation 计数. */
  static int countPreventionSuccesses(List<AttackChainNodeExpectation> expectations) {
    if (expectations == null || expectations.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (AttackChainNodeExpectation e : expectations) {
      if (e.getType() == EXPECTATION_TYPE.PREVENTION
          && e.getResponse() == EXPECTATION_STATUS.SUCCESS) {
        count++;
      }
    }
    return count;
  }

  /**
   * 节点关联的"目标设备"——多 asset 时取首个；无 asset 时 null. 招标 §3.3 边界设备 / §4.2 流量设备：被测设备即节点的 asset 目标.
   */
  private static String resolveDeviceId(AttackChainNode node) {
    List<Asset> assets = node.getAssets();
    if (assets == null || assets.isEmpty()) {
      return null;
    }
    Asset first = assets.get(0);
    return first == null ? null : first.getId();
  }
}
