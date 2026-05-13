package io.veriguard.rest.coverage;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTO 集合 —— PR C3 边界覆盖度子模块 REST 输出. */
public final class CoverageDtos {

  private CoverageDtos() {}

  // ============================================================
  // Policy
  // ============================================================

  public record PolicyOutput(
      @JsonProperty("policy_id") String id,
      @JsonProperty("policy_name") String name,
      @JsonProperty("policy_device_type") String deviceType,
      @JsonProperty("policy_device_id") String deviceId,
      @JsonProperty("policy_external_rule_id") String externalRuleId,
      @JsonProperty("policy_description") String description,
      @JsonProperty("policy_created_at") Instant createdAt,
      @JsonProperty("policy_updated_at") Instant updatedAt) {}

  public record PolicyPageOutput(
      @JsonProperty("content") List<PolicyOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  // ============================================================
  // Coverage Baseline
  // ============================================================

  public record CoverageBaselineOutput(
      @JsonProperty("coverage_baseline_id") String id,
      @JsonProperty("coverage_baseline_name") String name,
      @JsonProperty("coverage_baseline_coverage_type") String coverageType,
      @JsonProperty("coverage_baseline_case_ids") List<String> caseIds,
      @JsonProperty("coverage_baseline_asset_group_id") String assetGroupId,
      @JsonProperty("coverage_baseline_description") String description,
      @JsonProperty("coverage_baseline_soc_query_delay_seconds") int socQueryDelaySeconds,
      @JsonProperty("coverage_baseline_created_at") Instant createdAt,
      @JsonProperty("coverage_baseline_updated_at") Instant updatedAt) {}

  public record CoverageBaselinePageOutput(
      @JsonProperty("content") List<CoverageBaselineOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  // ============================================================
  // Coverage Run
  // ============================================================

  public record CoverageRunOutput(
      @JsonProperty("coverage_run_id") String id,
      @JsonProperty("coverage_run_baseline_id") String baselineId,
      @JsonProperty("coverage_run_status") String status,
      @JsonProperty("coverage_run_total_cells") int totalCells,
      @JsonProperty("coverage_run_hit_count") int hitCount,
      @JsonProperty("coverage_run_miss_count") int missCount,
      @JsonProperty("coverage_run_timeout_count") int timeoutCount,
      @JsonProperty("coverage_run_out_of_scope_count") int outOfScopeCount,
      @JsonProperty("coverage_run_progress_percent") double progressPercent,
      @JsonProperty("coverage_run_hit_state_counts") Map<String, Long> hitStateCounts,
      @JsonProperty("coverage_run_started_at") Instant startedAt,
      @JsonProperty("coverage_run_finished_at") Instant finishedAt,
      @JsonProperty("coverage_run_error_message") String errorMessage,
      @JsonProperty("coverage_run_created_at") Instant createdAt,
      @JsonProperty("coverage_run_updated_at") Instant updatedAt) {}

  public record CoverageRunPageOutput(
      @JsonProperty("content") List<CoverageRunOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  public record CoverageRunCreateOutput(
      @JsonProperty("coverage_run_id") String runId,
      @JsonProperty("coverage_run_status") String status) {}

  // ============================================================
  // Coverage Matrix Cell
  // ============================================================

  public record CoverageCellOutput(
      @JsonProperty("coverage_result_id") String id,
      @JsonProperty("coverage_result_run_id") String runId,
      @JsonProperty("coverage_result_asset_id") String assetId,
      @JsonProperty("coverage_result_policy_id") String policyId,
      @JsonProperty("coverage_result_case_id") String caseId,
      @JsonProperty("coverage_result_hit_state") String hitState,
      @JsonProperty("coverage_result_alert_rule_id") String alertRuleId,
      @JsonProperty("coverage_result_observed_at") Instant observedAt,
      @JsonProperty("coverage_result_error_message") String errorMessage,
      @JsonProperty("coverage_result_created_at") Instant createdAt,
      @JsonProperty("coverage_result_updated_at") Instant updatedAt) {}

  public record CoverageMatrixPageOutput(
      @JsonProperty("content") List<CoverageCellOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  // ============================================================
  // Diff
  // ============================================================

  public record CoverageCellDeltaOutput(
      @JsonProperty("asset_id") String assetId,
      @JsonProperty("policy_id") String policyId,
      @JsonProperty("old_state") String oldState,
      @JsonProperty("new_state") String newState,
      @JsonProperty("change_type") String changeType) {}

  public record CoverageDiffSummaryOutput(
      @JsonProperty("unchanged") int unchanged,
      @JsonProperty("new_hit") int newHit,
      @JsonProperty("new_miss") int newMiss,
      @JsonProperty("dropped_hit") int droppedHit,
      @JsonProperty("dropped_miss") int droppedMiss,
      @JsonProperty("state_changed") int stateChanged) {}

  public record CoverageDiffOutput(
      @JsonProperty("run_id_a") String runIdA,
      @JsonProperty("run_id_b") String runIdB,
      @JsonProperty("deltas") List<CoverageCellDeltaOutput> deltas,
      @JsonProperty("summary") CoverageDiffSummaryOutput summary) {}
}
