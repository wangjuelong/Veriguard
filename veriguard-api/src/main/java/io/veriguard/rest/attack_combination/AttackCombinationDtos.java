package io.veriguard.rest.attack_combination;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTO 集合 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D1 REST 输出. */
public final class AttackCombinationDtos {

  private AttackCombinationDtos() {}

  public record BypassDimensionOutput(
      @JsonProperty("bypass_dimension_id") String id,
      @JsonProperty("bypass_dimension_name") String name,
      @JsonProperty("bypass_dimension_category") String category,
      @JsonProperty("bypass_dimension_description") String description,
      @JsonProperty("bypass_dimension_transform_type") String transformType,
      @JsonProperty("bypass_dimension_transform_config") Map<String, Object> transformConfig,
      @JsonProperty("bypass_dimension_created_at") Instant createdAt,
      @JsonProperty("bypass_dimension_updated_at") Instant updatedAt) {}

  public record BypassDimensionPageOutput(
      @JsonProperty("content") List<BypassDimensionOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  public record CombinationSampleOutput(
      @JsonProperty("base_attack_type") String baseAttackType,
      @JsonProperty("bypass_dimension_id") String bypassDimensionId,
      @JsonProperty("bypass_dimension_name") String bypassDimensionName,
      @JsonProperty("preview_payload") String previewPayload) {}

  public record CombinationPreviewOutput(
      @JsonProperty("base_attack_types") List<String> baseAttackTypes,
      @JsonProperty("bypass_dimension_ids") List<String> bypassDimensionIds,
      @JsonProperty("total_combinations") long totalCombinations,
      @JsonProperty("sample_size") int sampleSize,
      @JsonProperty("samples") List<CombinationSampleOutput> samples,
      @JsonProperty("preview_base_payload") String previewBasePayload) {}

  // ============================================================
  // PR D2 — 任务（run）相关 DTO
  // ============================================================

  public record AttackCombinationRunOutput(
      @JsonProperty("attack_combination_run_id") String id,
      @JsonProperty("attack_combination_run_name") String name,
      @JsonProperty("attack_combination_run_base_attack_types") List<String> baseAttackTypes,
      @JsonProperty("attack_combination_run_bypass_dimension_ids") List<String> bypassDimensionIds,
      @JsonProperty("attack_combination_run_asset_ids") List<String> assetIds,
      @JsonProperty("attack_combination_run_status") String status,
      @JsonProperty("attack_combination_run_total_combinations") int totalCombinations,
      @JsonProperty("attack_combination_run_total_results") int totalResults,
      @JsonProperty("attack_combination_run_completed_count") int completedCount,
      @JsonProperty("attack_combination_run_failed_count") int failedCount,
      @JsonProperty("attack_combination_run_rate_limit_per_second") int rateLimitPerSecond,
      @JsonProperty("attack_combination_run_concurrency") int concurrency,
      @JsonProperty("attack_combination_run_max_retries") int maxRetries,
      @JsonProperty("attack_combination_run_timeout_hours") int timeoutHours,
      @JsonProperty("attack_combination_run_progress_percent") double progressPercent,
      @JsonProperty("attack_combination_run_hit_state_counts") Map<String, Long> hitStateCounts,
      @JsonProperty("attack_combination_run_started_at") Instant startedAt,
      @JsonProperty("attack_combination_run_completed_at") Instant completedAt,
      @JsonProperty("attack_combination_run_expires_at") Instant expiresAt,
      @JsonProperty("attack_combination_run_created_at") Instant createdAt,
      @JsonProperty("attack_combination_run_updated_at") Instant updatedAt) {}

  public record AttackCombinationRunPageOutput(
      @JsonProperty("content") List<AttackCombinationRunOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  public record AttackCombinationResultOutput(
      @JsonProperty("attack_combination_result_id") String id,
      @JsonProperty("attack_combination_result_run_id") String runId,
      @JsonProperty("attack_combination_result_combination_id") String combinationId,
      @JsonProperty("attack_combination_result_base_attack_type") String baseAttackType,
      @JsonProperty("attack_combination_result_bypass_dimension_id") String bypassDimensionId,
      @JsonProperty("attack_combination_result_asset_id") String assetId,
      @JsonProperty("attack_combination_result_hit_state") String hitState,
      @JsonProperty("attack_combination_result_retry_count") int retryCount,
      @JsonProperty("attack_combination_result_payload_sample") String payloadSample,
      @JsonProperty("attack_combination_result_error_message") String errorMessage,
      @JsonProperty("attack_combination_result_executed_at") Instant executedAt,
      @JsonProperty("attack_combination_result_created_at") Instant createdAt,
      @JsonProperty("attack_combination_result_updated_at") Instant updatedAt) {}

  public record AttackCombinationResultPageOutput(
      @JsonProperty("content") List<AttackCombinationResultOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  // ============================================================
  // PR D3 — 聚类相关 DTO
  // ============================================================

  public record AttackCombinationClusterOutput(
      @JsonProperty("attack_combination_cluster_id") String id,
      @JsonProperty("attack_combination_cluster_run_id") String runId,
      @JsonProperty("attack_combination_cluster_dim") String clusterDim,
      @JsonProperty("attack_combination_cluster_key") String clusterKey,
      @JsonProperty("attack_combination_cluster_label") String clusterLabel,
      @JsonProperty("attack_combination_cluster_miss_count") int missCount,
      @JsonProperty("attack_combination_cluster_total_in_cluster") int totalInCluster,
      @JsonProperty("attack_combination_cluster_payload_samples") List<String> payloadSamples,
      @JsonProperty("attack_combination_cluster_top_base_attack_types")
          List<Map<String, Object>> topBaseAttackTypes,
      @JsonProperty("attack_combination_cluster_top_bypass_dimensions")
          List<Map<String, Object>> topBypassDimensions,
      @JsonProperty("attack_combination_cluster_computed_at") Instant computedAt,
      @JsonProperty("attack_combination_cluster_created_at") Instant createdAt,
      @JsonProperty("attack_combination_cluster_updated_at") Instant updatedAt,
      // PR D4: 分级输出（severityLevel/label/color 在 cluster 尚未被分级时返回 null）
      @JsonProperty("attack_combination_cluster_severity_score") BigDecimal severityScore,
      @JsonProperty("attack_combination_cluster_severity_level") String severityLevel,
      @JsonProperty("attack_combination_cluster_severity_label") String severityLabel,
      @JsonProperty("attack_combination_cluster_severity_color") String severityColor) {}

  public record AttackCombinationClusterPageOutput(
      @JsonProperty("content") List<AttackCombinationClusterOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  public record AttackCombinationClusterRecomputeOutput(
      @JsonProperty("attack_combination_run_id") String runId,
      @JsonProperty("cluster_recompute_status") String status,
      @JsonProperty("cluster_recompute_job_id") String jobId) {}

  // ============================================================
  // PR D4 — 分级配置 + 重算 DTO
  // ============================================================

  public record SeverityConfigOutput(
      @JsonProperty("severity_config_id") String id,
      @JsonProperty("severity_config_miss_count_weight") BigDecimal missCountWeight,
      @JsonProperty("severity_config_attack_type_weight") BigDecimal attackTypeWeight,
      @JsonProperty("severity_config_asset_sensitivity_weight") BigDecimal assetSensitivityWeight,
      @JsonProperty("severity_config_critical_threshold") BigDecimal criticalThreshold,
      @JsonProperty("severity_config_high_threshold") BigDecimal highThreshold,
      @JsonProperty("severity_config_medium_threshold") BigDecimal mediumThreshold,
      @JsonProperty("severity_config_critical_label") String criticalLabel,
      @JsonProperty("severity_config_high_label") String highLabel,
      @JsonProperty("severity_config_medium_label") String mediumLabel,
      @JsonProperty("severity_config_info_label") String infoLabel,
      @JsonProperty("severity_config_critical_color") String criticalColor,
      @JsonProperty("severity_config_high_color") String highColor,
      @JsonProperty("severity_config_medium_color") String mediumColor,
      @JsonProperty("severity_config_info_color") String infoColor,
      @JsonProperty("severity_config_created_at") Instant createdAt,
      @JsonProperty("severity_config_updated_at") Instant updatedAt) {}

  public record SeverityRecomputeOutput(
      @JsonProperty("attack_combination_run_id") String runId,
      @JsonProperty("severity_recompute_status") String status,
      @JsonProperty("severity_recompute_job_id") String jobId) {}

  // ============================================================
  // PR B5 — 基础攻击类型库 DTO
  // ============================================================

  public record BaseAttackTypeOutput(
      @JsonProperty("base_attack_type_id") String id,
      @JsonProperty("base_attack_type_name") String name,
      @JsonProperty("base_attack_type_category") String category,
      @JsonProperty("base_attack_type_display_label") String displayLabel,
      @JsonProperty("base_attack_type_description") String description,
      @JsonProperty("base_attack_type_severity_score") Integer severityScore,
      @JsonProperty("base_attack_type_default_payload") String defaultPayload,
      @JsonProperty("base_attack_type_attack_pattern_id") String attackPatternId,
      @JsonProperty("base_attack_type_target_layer") String targetLayer,
      @JsonProperty("base_attack_type_created_at") Instant createdAt,
      @JsonProperty("base_attack_type_updated_at") Instant updatedAt) {}

  public record BaseAttackTypePageOutput(
      @JsonProperty("content") List<BaseAttackTypeOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}
}
