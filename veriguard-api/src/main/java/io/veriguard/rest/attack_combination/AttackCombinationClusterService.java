package io.veriguard.rest.attack_combination;

import io.veriguard.combination.cluster.ClusterAnalyzer;
import io.veriguard.database.model.combination.AttackCombinationCluster;
import io.veriguard.database.model.combination.AttackCombinationClusterDim;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.database.model.combination.AttackCombinationResult;
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.SeverityConfig;
import io.veriguard.database.model.combination.SeverityLevel;
import io.veriguard.database.repository.AttackCombinationClusterRepository;
import io.veriguard.database.repository.AttackCombinationResultRepository;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.SeverityConfigRepository;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationClusterOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationClusterPageOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationClusterRecomputeOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationResultOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationResultPageOutput;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 攻击组合聚类查询 + 手动重算服务 —— IPv6 安全验证系统 §3.6 ★2 PR D3.
 *
 * <p>聚类列表 / 详情 / 成员 result 下钻为只读路径；recompute 接 ClusterAnalyzer.analyzeAsync.
 */
@Service
public class AttackCombinationClusterService {

  private final AttackCombinationRunRepository runRepository;
  private final AttackCombinationClusterRepository clusterRepository;
  private final AttackCombinationResultRepository resultRepository;
  private final ClusterAnalyzer clusterAnalyzer;
  private final SeverityConfigRepository severityConfigRepository;

  public AttackCombinationClusterService(
      AttackCombinationRunRepository runRepository,
      AttackCombinationClusterRepository clusterRepository,
      AttackCombinationResultRepository resultRepository,
      ClusterAnalyzer clusterAnalyzer,
      SeverityConfigRepository severityConfigRepository) {
    this.runRepository = runRepository;
    this.clusterRepository = clusterRepository;
    this.resultRepository = resultRepository;
    this.clusterAnalyzer = clusterAnalyzer;
    this.severityConfigRepository = severityConfigRepository;
  }

  @Transactional(readOnly = true)
  public AttackCombinationClusterPageOutput list(
      String runId, AttackCombinationClusterDim dim, int page, int size) {
    validatePaging(page, size);
    if (!runRepository.existsById(runId)) {
      throw new IllegalArgumentException("Unknown run id: " + runId);
    }
    Pageable pageable =
        PageRequest.of(
            page, size, Sort.by(Sort.Direction.DESC, "missCount"));
    Page<AttackCombinationCluster> result =
        clusterRepository.findAllByRunIdAndClusterDim(runId, dim, pageable);
    SeverityConfig severityConfig = loadSeverityConfigOrDefault();
    List<AttackCombinationClusterOutput> content =
        result.getContent().stream().map(c -> toOutput(c, severityConfig)).toList();
    return new AttackCombinationClusterPageOutput(
        content,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AttackCombinationClusterOutput detail(String runId, String clusterId) {
    AttackCombinationCluster cluster =
        clusterRepository
            .findById(clusterId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown cluster id: " + clusterId));
    if (!cluster.getRunId().equals(runId)) {
      throw new IllegalArgumentException(
          "Cluster " + clusterId + " does not belong to run " + runId);
    }
    return toOutput(cluster, loadSeverityConfigOrDefault());
  }

  private SeverityConfig loadSeverityConfigOrDefault() {
    return severityConfigRepository.findSingleton().orElseGet(SeverityConfig::new);
  }

  /** 下钻 cluster 成员 result：按 cluster_dim + cluster_key 反向定位 + 可选 hit_state 过滤 + 分页. */
  @Transactional(readOnly = true)
  public AttackCombinationResultPageOutput listClusterMembers(
      String runId,
      String clusterId,
      Optional<AttackCombinationHitState> hitState,
      int page,
      int size) {
    validatePaging(page, size);
    AttackCombinationCluster cluster =
        clusterRepository
            .findById(clusterId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown cluster id: " + clusterId));
    if (!cluster.getRunId().equals(runId)) {
      throw new IllegalArgumentException(
          "Cluster " + clusterId + " does not belong to run " + runId);
    }

    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    // For asset dim → asset_id == cluster_key; for device dim 我们没有反向 result→device 索引，
    // 暂走 asset dim 同样的过滤逻辑（device cluster 也是按 asset 同身份归类的子集，
    // 真要精确按 device_key 反查需要 asset 加 device_key 索引列 → YAGNI 留待后续 PR）.
    // PR D3 baseline：仅支持 asset 维度的成员下钻.
    if (cluster.getClusterDim() != AttackCombinationClusterDim.asset) {
      throw new IllegalArgumentException(
          "Cluster member drill-down is supported only for cluster_dim=asset in PR D3 baseline");
    }
    String assetId = cluster.getClusterKey();

    Page<AttackCombinationResult> resultPage =
        hitState
            .map(
                h ->
                    resultRepository.findAll(
                        (root, q, cb) ->
                            cb.and(
                                cb.equal(root.get("runId"), runId),
                                cb.equal(root.get("assetId"), assetId),
                                cb.equal(root.get("hitState"), h)),
                        pageable))
            .orElseGet(
                () ->
                    resultRepository.findAll(
                        (root, q, cb) ->
                            cb.and(
                                cb.equal(root.get("runId"), runId),
                                cb.equal(root.get("assetId"), assetId)),
                        pageable));
    List<AttackCombinationResultOutput> content =
        resultPage.getContent().stream()
            .map(AttackCombinationClusterService::toResultOutput)
            .toList();
    return new AttackCombinationResultPageOutput(
        content,
        resultPage.getNumber(),
        resultPage.getSize(),
        resultPage.getTotalElements(),
        resultPage.getTotalPages());
  }

  /** 手动触发重算：仅 run.status=completed 时允许. */
  public AttackCombinationClusterRecomputeOutput recompute(String runId) {
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    if (run.getStatus() != AttackCombinationRunStatus.completed) {
      throw new IllegalStateException(
          "Cluster recompute requires completed run, got status: " + run.getStatus());
    }
    String jobId = UUID.randomUUID().toString();
    clusterAnalyzer.analyzeAsync(runId, true);
    return new AttackCombinationClusterRecomputeOutput(runId, "accepted", jobId);
  }

  // ============================================================
  // 内部
  // ============================================================

  private static void validatePaging(int page, int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0, got " + page);
    }
    if (size < 1 || size > 500) {
      throw new IllegalArgumentException("size must be between 1 and 500, got " + size);
    }
  }

  static AttackCombinationClusterOutput toOutput(
      AttackCombinationCluster c, SeverityConfig severityConfig) {
    SeverityLevel level = c.getSeverityLevel();
    String label = labelFor(level, severityConfig);
    String color = colorFor(level, severityConfig);
    return new AttackCombinationClusterOutput(
        c.getId(),
        c.getRunId(),
        c.getClusterDim() == null ? null : c.getClusterDim().name(),
        c.getClusterKey(),
        c.getClusterLabel(),
        c.getMissCount(),
        c.getTotalInCluster(),
        c.getPayloadSamples(),
        c.getTopBaseAttackTypes(),
        c.getTopBypassDimensions(),
        c.getComputedAt(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getSeverityScore(),
        level == null ? null : level.name(),
        label,
        color);
  }

  private static String labelFor(SeverityLevel level, SeverityConfig cfg) {
    if (level == null) {
      return null;
    }
    return switch (level) {
      case critical -> cfg.getCriticalLabel();
      case high -> cfg.getHighLabel();
      case medium -> cfg.getMediumLabel();
      case info -> cfg.getInfoLabel();
    };
  }

  private static String colorFor(SeverityLevel level, SeverityConfig cfg) {
    if (level == null) {
      return null;
    }
    return switch (level) {
      case critical -> cfg.getCriticalColor();
      case high -> cfg.getHighColor();
      case medium -> cfg.getMediumColor();
      case info -> cfg.getInfoColor();
    };
  }

  private static AttackCombinationResultOutput toResultOutput(AttackCombinationResult r) {
    return new AttackCombinationResultOutput(
        r.getId(),
        r.getRunId(),
        r.getCombinationId(),
        r.getBaseAttackType(),
        r.getBypassDimensionId(),
        r.getAssetId(),
        r.getHitState() == null ? null : r.getHitState().name(),
        r.getRetryCount(),
        r.getPayloadSample(),
        r.getErrorMessage(),
        r.getExecutedAt(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
