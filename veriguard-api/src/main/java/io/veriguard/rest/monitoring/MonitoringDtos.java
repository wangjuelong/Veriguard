package io.veriguard.rest.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 监控子模块 wire DTOs —— PR C4 招标 §3.2.
 *
 * <p>所有字段保持 snake_case + 与 entity 同源前缀（与 PR C3/C5 一致）.
 */
public final class MonitoringDtos {

  private MonitoringDtos() {}

  // ===================== Job =====================

  public record MonitoringJobOutput(
      @JsonProperty("monitoring_job_id") String id,
      @JsonProperty("monitoring_job_name") String name,
      @JsonProperty("monitoring_job_baseline_id") String baselineId,
      @JsonProperty("monitoring_job_cron_expression") String cronExpression,
      @JsonProperty("monitoring_job_enabled") boolean enabled,
      @JsonProperty("monitoring_job_description") String description,
      @JsonProperty("monitoring_job_last_triggered_at") Instant lastTriggeredAt,
      @JsonProperty("monitoring_job_next_fire_at") Instant nextFireAt,
      @JsonProperty("monitoring_job_created_at") Instant createdAt,
      @JsonProperty("monitoring_job_updated_at") Instant updatedAt) {}

  public record MonitoringJobPageOutput(
      @JsonProperty("content") List<MonitoringJobOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  // ===================== History =====================

  public record MonitoringRunHistoryOutput(
      @JsonProperty("monitoring_run_history_id") String id,
      @JsonProperty("monitoring_run_history_job_id") String jobId,
      @JsonProperty("monitoring_run_history_coverage_run_id") String coverageRunId,
      @JsonProperty("monitoring_run_history_scheduled_at") Instant scheduledAt,
      @JsonProperty("monitoring_run_history_finished_at") Instant finishedAt,
      @JsonProperty("monitoring_run_history_status") String status,
      @JsonProperty("monitoring_run_history_hit_count") Integer hitCount,
      @JsonProperty("monitoring_run_history_miss_count") Integer missCount,
      @JsonProperty("monitoring_run_history_timeout_count") Integer timeoutCount,
      @JsonProperty("monitoring_run_history_out_of_scope_count") Integer outOfScopeCount,
      @JsonProperty("monitoring_run_history_total_count") Integer totalCount,
      @JsonProperty("monitoring_run_history_hit_rate") BigDecimal hitRate,
      @JsonProperty("monitoring_run_history_error_message") String errorMessage,
      @JsonProperty("monitoring_run_history_created_at") Instant createdAt) {}

  public record MonitoringHistoryPageOutput(
      @JsonProperty("content") List<MonitoringRunHistoryOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  public record MonitoringTriggerOutput(
      @JsonProperty("monitoring_run_history_id") String historyId,
      @JsonProperty("monitoring_run_history_status") String status,
      @JsonProperty("monitoring_run_history_coverage_run_id") String coverageRunId) {}

  // ===================== Trend =====================

  /** 一个聚合桶（按 hour 或 day），avgHitRate = sum(hit) / sum(total). */
  public record MonitoringTrendBucket(
      @JsonProperty("bucket") String bucket,
      @JsonProperty("avg_hit_rate") BigDecimal avgHitRate,
      @JsonProperty("hit_sum") long hitSum,
      @JsonProperty("miss_sum") long missSum,
      @JsonProperty("timeout_sum") long timeoutSum,
      @JsonProperty("out_of_scope_sum") long outOfScopeSum,
      @JsonProperty("total_sum") long totalSum,
      @JsonProperty("run_count") long runCount) {}

  public record MonitoringTrendOutput(
      @JsonProperty("monitoring_job_id") String jobId,
      @JsonProperty("aggregation") String aggregation,
      @JsonProperty("buckets") List<MonitoringTrendBucket> buckets) {}

  /** 内部使用：给 LocalDate / Instant key 一个稳定字符串. */
  public static String dayKey(LocalDate day) {
    return day.toString();
  }
}
