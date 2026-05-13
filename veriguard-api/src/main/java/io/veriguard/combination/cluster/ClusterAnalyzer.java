package io.veriguard.combination.cluster;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.combination.AttackCombinationCluster;
import io.veriguard.database.model.combination.AttackCombinationClusterDim;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.database.model.combination.AttackCombinationResult;
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.repository.AssetRepository;
import io.veriguard.database.repository.AttackCombinationClusterRepository;
import io.veriguard.database.repository.AttackCombinationResultRepository;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.BypassDimensionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 攻击组合聚类分析器 —— IPv6 安全验证系统 §3.6 ★2 PR D3.
 *
 * <p>职责：
 * <ul>
 *   <li>给定 completed 状态的 run id，按 cluster_dim = asset / device 两个维度聚类未防护 result
 *       （hit_state = miss）</li>
 *   <li>每 cluster 保留前 N payload_sample（默认 5）+ top 5 base_attack_types 分布 + top 5
 *       bypass_dimensions 分布</li>
 *   <li>fresh-write：overwrite=true 先 delete by run_id 再 saveAll，幂等</li>
 *   <li>单 cluster 任务最大 5 min；超时则中断，标记任务失败但不污染 run.status</li>
 * </ul>
 *
 * <p>auto trigger：scheduler 在 run.status -&gt; completed 的 TX afterCommit 调 analyzeAsync.
 * manual trigger：REST POST /api/attack_combination/runs/{id}/cluster/recompute.
 */
@Slf4j
@Component
public class ClusterAnalyzer {

  /** result 分页拉取批量 size（避免 OOM）. */
  private static final int RESULT_PAGE_SIZE = 500;

  /** top-N 分布默认保留个数. */
  private static final int TOP_DISTRIBUTION_N = 5;

  /** 单任务超时（毫秒）. */
  private static final long TASK_TIMEOUT_MS = 5L * 60L * 1000L;

  private final AttackCombinationRunRepository runRepository;
  private final AttackCombinationResultRepository resultRepository;
  private final AttackCombinationClusterRepository clusterRepository;
  private final BypassDimensionRepository dimensionRepository;
  private final AssetRepository assetRepository;
  private final DeviceKeyExtractor deviceKeyExtractor;
  private final TransactionTemplate transactionTemplate;
  private final int payloadSamplesPerCluster;

  public ClusterAnalyzer(
      AttackCombinationRunRepository runRepository,
      AttackCombinationResultRepository resultRepository,
      AttackCombinationClusterRepository clusterRepository,
      BypassDimensionRepository dimensionRepository,
      AssetRepository assetRepository,
      DeviceKeyExtractor deviceKeyExtractor,
      TransactionTemplate transactionTemplate,
      @Value("${veriguard.combination.cluster.payload-samples-per-cluster:5}")
          int payloadSamplesPerCluster) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.clusterRepository = clusterRepository;
    this.dimensionRepository = dimensionRepository;
    this.assetRepository = assetRepository;
    this.deviceKeyExtractor = deviceKeyExtractor;
    this.transactionTemplate = transactionTemplate;
    this.payloadSamplesPerCluster = Math.max(1, payloadSamplesPerCluster);
  }

  /**
   * 异步触发聚类（用于 scheduler afterCommit 钩子）.
   *
   * @return CompletableFuture，便于测试 await
   */
  @Async
  public CompletableFuture<Void> analyzeAsync(String runId, boolean overwrite) {
    try {
      analyze(runId, overwrite);
    } catch (Exception e) {
      // 不向 scheduler 线程抛出：聚类失败不影响 run 状态.
      log.error("Cluster analysis failed for run {}: {}", runId, e.getMessage(), e);
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * 同步执行聚类（测试入口 + 手动 recompute fallback 路径）.
   *
   * @throws IllegalStateException 如果 run 不在 completed 状态
   * @throws IllegalArgumentException 如果 run 不存在
   */
  public void analyze(String runId, boolean overwrite) {
    Instant startedAt = Instant.now();
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    if (run.getStatus() != AttackCombinationRunStatus.completed) {
      throw new IllegalStateException(
          "Cluster analysis requires completed run, got status: " + run.getStatus());
    }

    if (overwrite) {
      transactionTemplate.executeWithoutResult(s -> clusterRepository.deleteByRunId(runId));
    }

    // 1. 流式拉所有 miss results（分页 + total_in_cluster 需要全部，单独再拉一次 by asset）
    List<AttackCombinationResult> misses =
        loadResultsByState(runId, AttackCombinationHitState.miss, startedAt);
    if (misses.isEmpty()) {
      log.info("Run {} has no miss results, skipping cluster persistence", runId);
      return;
    }

    // 2. 拉同 run 的所有 result（任何 state）用来统计 total_in_cluster 按 asset 维度
    List<AttackCombinationResult> all = loadAllResults(runId, startedAt);

    // 3. 加载相关 asset + dimension 以便 cluster_label / dim_name
    Set<String> assetIds = new HashSet<>();
    Set<String> dimIds = new HashSet<>();
    for (AttackCombinationResult r : misses) {
      assetIds.add(r.getAssetId());
      dimIds.add(r.getBypassDimensionId());
    }
    Map<String, Asset> assetById = new HashMap<>();
    assetRepository.findAllById(assetIds).forEach(a -> assetById.put(a.getId(), a));
    Map<String, BypassDimension> dimById = new HashMap<>();
    dimensionRepository.findAllById(dimIds).forEach(d -> dimById.put(d.getId(), d));

    // 4. 双 pass 聚类
    List<AttackCombinationCluster> assetClusters =
        buildClusters(
            runId,
            AttackCombinationClusterDim.asset,
            misses,
            all,
            r -> r.getAssetId(),
            (key, sample) -> {
              Asset asset = assetById.get(key);
              return asset == null ? key : asset.getName();
            },
            dimById);

    List<AttackCombinationCluster> deviceClusters =
        buildClusters(
            runId,
            AttackCombinationClusterDim.device,
            misses,
            all,
            r -> {
              Asset asset = assetById.get(r.getAssetId());
              if (asset == null) {
                return "unknown-device-" + r.getAssetId();
              }
              return deviceKeyExtractor.deriveDeviceKey(asset);
            },
            (key, sample) -> key,
            dimById);

    List<AttackCombinationCluster> allClusters = new ArrayList<>();
    allClusters.addAll(assetClusters);
    allClusters.addAll(deviceClusters);

    transactionTemplate.executeWithoutResult(
        s -> clusterRepository.saveAll(allClusters));

    log.info(
        "Cluster analysis for run {} produced {} asset cluster(s) + {} device cluster(s)",
        runId,
        assetClusters.size(),
        deviceClusters.size());
  }

  // ============================================================
  // 内部
  // ============================================================

  /** 按 hit_state 流式分页拉 result. */
  private List<AttackCombinationResult> loadResultsByState(
      String runId, AttackCombinationHitState state, Instant startedAt) {
    List<AttackCombinationResult> out = new ArrayList<>();
    int page = 0;
    while (true) {
      checkTimeout(startedAt);
      org.springframework.data.domain.Page<AttackCombinationResult> p =
          resultRepository.findAllByRunIdAndHitState(
              runId,
              state,
              PageRequest.of(page, RESULT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "executedAt")));
      out.addAll(p.getContent());
      if (!p.hasNext()) {
        break;
      }
      page++;
    }
    return out;
  }

  /** 流式分页拉同 run 全 result（任何状态）. */
  private List<AttackCombinationResult> loadAllResults(String runId, Instant startedAt) {
    List<AttackCombinationResult> out = new ArrayList<>();
    int page = 0;
    while (true) {
      checkTimeout(startedAt);
      org.springframework.data.domain.Page<AttackCombinationResult> p =
          resultRepository.findAllByRunId(
              runId, PageRequest.of(page, RESULT_PAGE_SIZE, Sort.by("createdAt")));
      out.addAll(p.getContent());
      if (!p.hasNext()) {
        break;
      }
      page++;
    }
    return out;
  }

  private void checkTimeout(Instant startedAt) {
    if (Duration.between(startedAt, Instant.now()).toMillis() > TASK_TIMEOUT_MS) {
      throw new ClusterAnalysisTimeoutException(
          "Cluster analysis exceeded TASK_TIMEOUT_MS=" + TASK_TIMEOUT_MS + "ms");
    }
  }

  /** 通用 cluster 构造：按 keyOf 分组，注入 label / 分布. */
  private List<AttackCombinationCluster> buildClusters(
      String runId,
      AttackCombinationClusterDim dim,
      List<AttackCombinationResult> misses,
      List<AttackCombinationResult> all,
      java.util.function.Function<AttackCombinationResult, String> keyOf,
      java.util.function.BiFunction<String, AttackCombinationResult, String> labelOf,
      Map<String, BypassDimension> dimById) {

    // GROUP miss
    Map<String, List<AttackCombinationResult>> missByKey = new LinkedHashMap<>();
    for (AttackCombinationResult r : misses) {
      missByKey.computeIfAbsent(keyOf.apply(r), k -> new ArrayList<>()).add(r);
    }

    // GROUP all (for total_in_cluster)
    Map<String, Integer> totalByKey = new HashMap<>();
    for (AttackCombinationResult r : all) {
      String k = keyOf.apply(r);
      totalByKey.merge(k, 1, Integer::sum);
    }

    List<AttackCombinationCluster> out = new ArrayList<>(missByKey.size());
    Instant now = Instant.now();
    for (Map.Entry<String, List<AttackCombinationResult>> e : missByKey.entrySet()) {
      String key = e.getKey();
      List<AttackCombinationResult> group = e.getValue();

      AttackCombinationCluster cluster = new AttackCombinationCluster();
      cluster.setRunId(runId);
      cluster.setClusterDim(dim);
      cluster.setClusterKey(key);
      cluster.setClusterLabel(labelOf.apply(key, group.get(0)));
      cluster.setMissCount(group.size());
      cluster.setTotalInCluster(totalByKey.getOrDefault(key, group.size()));
      cluster.setPayloadSamples(topPayloadSamples(group, payloadSamplesPerCluster));
      cluster.setTopBaseAttackTypes(topBaseAttackTypes(group));
      cluster.setTopBypassDimensions(topBypassDimensions(group, dimById));
      cluster.setComputedAt(now);
      out.add(cluster);
    }
    return out;
  }

  /** 取前 N 个 payload_sample，按 executedAt desc（misses 已是 desc 序，但防御性再排一次）. */
  private static List<String> topPayloadSamples(List<AttackCombinationResult> group, int n) {
    return group.stream()
        .sorted(
            Comparator.comparing(
                AttackCombinationResult::getExecutedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .map(AttackCombinationResult::getPayloadSample)
        .filter(s -> s != null && !s.isBlank())
        .distinct()
        .limit(n)
        .toList();
  }

  /** 统计 cluster 内 base_attack_type 分布，取 top N. */
  private static List<Map<String, Object>> topBaseAttackTypes(
      List<AttackCombinationResult> group) {
    Map<String, Integer> counts = new HashMap<>();
    for (AttackCombinationResult r : group) {
      counts.merge(r.getBaseAttackType(), 1, Integer::sum);
    }
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(TOP_DISTRIBUTION_N)
        .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue()))
        .toList();
  }

  /** 统计 cluster 内 bypass_dimension 分布，取 top N. */
  private static List<Map<String, Object>> topBypassDimensions(
      List<AttackCombinationResult> group, Map<String, BypassDimension> dimById) {
    Map<String, Integer> counts = new HashMap<>();
    for (AttackCombinationResult r : group) {
      counts.merge(r.getBypassDimensionId(), 1, Integer::sum);
    }
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(TOP_DISTRIBUTION_N)
        .map(
            e -> {
              BypassDimension d = dimById.get(e.getKey());
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("dim_id", e.getKey());
              row.put("name", d == null ? e.getKey() : d.getName());
              row.put("count", e.getValue());
              return row;
            })
        .toList();
  }

  /** 任务执行超过预算时抛此异常，调用方在 analyzeAsync 内吞掉避免污染上层. */
  public static final class ClusterAnalysisTimeoutException extends RuntimeException {
    public ClusterAnalysisTimeoutException(String message) {
      super(message);
    }
  }
}
