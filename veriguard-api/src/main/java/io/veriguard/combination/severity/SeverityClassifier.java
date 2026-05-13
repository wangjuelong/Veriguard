package io.veriguard.combination.severity;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.combination.AttackCombinationCluster;
import io.veriguard.database.model.combination.AttackCombinationClusterDim;
import io.veriguard.database.model.combination.SeverityConfig;
import io.veriguard.database.model.combination.SeverityLevel;
import io.veriguard.database.repository.AssetRepository;
import io.veriguard.database.repository.AttackCombinationClusterRepository;
import io.veriguard.database.repository.SeverityConfigRepository;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 攻击组合 cluster 严重度分级器 —— IPv6 安全验证系统 §3.6 ★2 PR D4.
 *
 * <p>评分公式（每 cluster）：
 *
 * <pre>
 *   severity_score = miss_count_weight       × miss_count_score (0-100, = min(miss/10, 100))
 *                  + attack_type_weight      × attack_type_severity_avg (top_base_attack_types 平均)
 *                  + asset_sensitivity_weight × asset_sensitivity_score (cluster_dim=asset 时反查 asset.tags)
 * </pre>
 *
 * <p>分级按 SeverityConfig 阈值映射：
 * <ul>
 *   <li>score &gt; critical_threshold → critical</li>
 *   <li>score &gt; high_threshold     → high</li>
 *   <li>score &gt; medium_threshold   → medium</li>
 *   <li>otherwise                     → info</li>
 * </ul>
 *
 * <p>启动时校验 default 权重和 = 1.0 ± 0.001（fail-fast，避免无效配置上线）。
 */
@Slf4j
@Component
public class SeverityClassifier {

  /** 权重总和容差 */
  private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.001");

  /** miss_count 归一化分母：miss=10 → 100 分（饱和） */
  private static final double MISS_COUNT_SATURATION = 10.0;

  private final SeverityConfigRepository configRepository;
  private final AttackCombinationClusterRepository clusterRepository;
  private final AssetRepository assetRepository;
  private final BaseAttackTypeSeverityCatalog typeCatalog;
  private final AssetSensitivityScorer assetScorer;
  private final TransactionTemplate transactionTemplate;

  public SeverityClassifier(
      SeverityConfigRepository configRepository,
      AttackCombinationClusterRepository clusterRepository,
      AssetRepository assetRepository,
      BaseAttackTypeSeverityCatalog typeCatalog,
      AssetSensitivityScorer assetScorer,
      TransactionTemplate transactionTemplate) {
    this.configRepository = configRepository;
    this.clusterRepository = clusterRepository;
    this.assetRepository = assetRepository;
    this.typeCatalog = typeCatalog;
    this.assetScorer = assetScorer;
    this.transactionTemplate = transactionTemplate;
  }

  /** 校验 default 配置的权重和（fail-fast）。任何启动时不可恢复的不一致直接抛。 */
  @PostConstruct
  void validateDefaultWeights() {
    SeverityConfig defaults = new SeverityConfig();
    BigDecimal sum =
        defaults
            .getMissCountWeight()
            .add(defaults.getAttackTypeWeight())
            .add(defaults.getAssetSensitivityWeight());
    if (sum.subtract(BigDecimal.ONE).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
      throw new IllegalStateException(
          "SeverityConfig default weights must sum to 1.0 (±"
              + WEIGHT_TOLERANCE
              + "), got: "
              + sum);
    }
  }

  /**
   * 异步触发分级（供 ClusterAnalyzer.analyze() 末尾链调 / 手动 recompute）。
   *
   * <p>失败不抛到调用线程，仅 log；cluster 行 severity_* 列保持 null。
   */
  @Async
  public CompletableFuture<Void> classifyAsync(String runId, boolean overwrite) {
    try {
      classify(runId, overwrite);
    } catch (Exception e) {
      log.error("Severity classification failed for run {}: {}", runId, e.getMessage(), e);
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * 同步执行分级（测试入口 + 手动 recompute fallback）。
   *
   * @param overwrite 当前未启用 — 设计上每次都全量回填（cluster 是 fresh-write 的，不会重复算）。
   *     保留参数为对齐 ClusterAnalyzer 签名 + 未来增量化预留。
   */
  public void classify(String runId, boolean overwrite) {
    SeverityConfig config = loadConfigOrDefault();
    validateWeights(config);

    List<AttackCombinationCluster> assetClusters =
        clusterRepository.findAllByRunIdAndClusterDim(runId, AttackCombinationClusterDim.asset);
    List<AttackCombinationCluster> deviceClusters =
        clusterRepository.findAllByRunIdAndClusterDim(runId, AttackCombinationClusterDim.device);

    List<AttackCombinationCluster> all = new ArrayList<>();
    all.addAll(assetClusters);
    all.addAll(deviceClusters);

    if (all.isEmpty()) {
      log.info("Severity classify for run {} has no clusters; skip.", runId);
      return;
    }

    // 资产敏感度只在 cluster_dim=asset 时反查；预加载一次 asset。
    Set<String> assetIds = new HashSet<>();
    for (AttackCombinationCluster c : assetClusters) {
      if (c.getClusterKey() != null) {
        assetIds.add(c.getClusterKey());
      }
    }
    Map<String, Asset> assetById = new HashMap<>();
    if (!assetIds.isEmpty()) {
      assetRepository.findAllById(assetIds).forEach(a -> assetById.put(a.getId(), a));
    }

    for (AttackCombinationCluster cluster : all) {
      int missScore = computeMissCountScore(cluster.getMissCount());
      int typeScore = computeAttackTypeScore(cluster.getTopBaseAttackTypes());
      int assetScore =
          cluster.getClusterDim() == AttackCombinationClusterDim.asset
              ? assetScorer.scoreFor(assetById.get(cluster.getClusterKey()))
              : AssetSensitivityScorer.DEFAULT_SENSITIVITY;

      BigDecimal score = weightedSum(config, missScore, typeScore, assetScore);
      cluster.setSeverityScore(score);
      cluster.setSeverityLevel(levelFor(score, config));
    }

    transactionTemplate.executeWithoutResult(s -> clusterRepository.saveAll(all));
    log.info(
        "Severity classify for run {} updated {} cluster(s) ({} asset + {} device)",
        runId,
        all.size(),
        assetClusters.size(),
        deviceClusters.size());
  }

  // ============================================================
  // 内部
  // ============================================================

  private SeverityConfig loadConfigOrDefault() {
    return configRepository.findSingleton().orElseGet(SeverityConfig::new);
  }

  private static void validateWeights(SeverityConfig config) {
    BigDecimal sum =
        config
            .getMissCountWeight()
            .add(config.getAttackTypeWeight())
            .add(config.getAssetSensitivityWeight());
    if (sum.subtract(BigDecimal.ONE).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
      throw new IllegalStateException(
          "SeverityConfig weights must sum to 1.0 (±" + WEIGHT_TOLERANCE + "), got: " + sum);
    }
  }

  /** miss_count_score = min(miss/10, 100)，归一到 0-100。 */
  static int computeMissCountScore(int missCount) {
    if (missCount <= 0) {
      return 0;
    }
    double v = (double) missCount / MISS_COUNT_SATURATION * 100.0;
    if (v >= 100.0) {
      return 100;
    }
    return (int) Math.round(v);
  }

  /**
   * top_base_attack_types 内每项查表求平均；为空则返回 0（贡献 0 分）。
   * topBaseAttackTypes 元素结构：{"name": "sql_injection", "count": 12}。
   */
  int computeAttackTypeScore(List<Map<String, Object>> topBaseAttackTypes) {
    if (topBaseAttackTypes == null || topBaseAttackTypes.isEmpty()) {
      return 0;
    }
    int total = 0;
    int n = 0;
    for (Map<String, Object> entry : topBaseAttackTypes) {
      Object nameObj = entry.get("name");
      if (!(nameObj instanceof String name) || name.isBlank()) {
        continue;
      }
      total += typeCatalog.severityFor(name);
      n++;
    }
    if (n == 0) {
      return 0;
    }
    return total / n;
  }

  /** 三项加权求和，输出 0-100 范围的 BigDecimal（保留 2 位小数）. */
  static BigDecimal weightedSum(
      SeverityConfig config, int missScore, int typeScore, int assetScore) {
    BigDecimal missTerm =
        config.getMissCountWeight().multiply(BigDecimal.valueOf(missScore));
    BigDecimal typeTerm =
        config.getAttackTypeWeight().multiply(BigDecimal.valueOf(typeScore));
    BigDecimal assetTerm =
        config.getAssetSensitivityWeight().multiply(BigDecimal.valueOf(assetScore));
    BigDecimal raw = missTerm.add(typeTerm).add(assetTerm);
    // clamp to [0, 100]
    if (raw.compareTo(BigDecimal.ZERO) < 0) {
      raw = BigDecimal.ZERO;
    } else if (raw.compareTo(BigDecimal.valueOf(100)) > 0) {
      raw = BigDecimal.valueOf(100);
    }
    return raw.setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * 阈值映射：
   *
   * <pre>
   *   score &gt; critical_threshold → critical
   *   score &gt; high_threshold     → high
   *   score &gt; medium_threshold   → medium
   *   otherwise                     → info
   * </pre>
   *
   * 严格 &gt;（边界值落到较低档），与表层 default 70/40/10 + 等于阈值归低档的常见做法一致.
   */
  static SeverityLevel levelFor(BigDecimal score, SeverityConfig config) {
    if (score.compareTo(config.getCriticalThreshold()) > 0) {
      return SeverityLevel.critical;
    }
    if (score.compareTo(config.getHighThreshold()) > 0) {
      return SeverityLevel.high;
    }
    if (score.compareTo(config.getMediumThreshold()) > 0) {
      return SeverityLevel.medium;
    }
    return SeverityLevel.info;
  }

  /** 用于测试 inspect。 */
  static List<SeverityLevel> levels() {
    return Collections.unmodifiableList(List.of(SeverityLevel.values()));
  }
}
