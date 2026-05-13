package io.veriguard.database.model.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.database.model.Base;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import static java.time.Instant.now;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 监控任务执行历史 —— PR C4 招标 §3.2.
 *
 * <p>每行 = 一次 cron 触发的元数据 + 4 态计数（关联 coverage_run 终结后回填）。
 *
 * <p>对应 Flyway V16 表 {@code monitoring_run_history}.
 */
@Getter
@Setter
@Entity
@Table(name = "monitoring_run_history")
@EntityListeners(ModelBaseListener.class)
public class MonitoringRunHistory implements Base {

  @Id
  @Column(name = "monitoring_run_history_id")
  @JsonProperty("monitoring_run_history_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "monitoring_run_history_job_id")
  @JsonProperty("monitoring_run_history_job_id")
  @NotBlank
  private String jobId;

  @Column(name = "monitoring_run_history_coverage_run_id")
  @JsonProperty("monitoring_run_history_coverage_run_id")
  private String coverageRunId;

  @Queryable(sortable = true)
  @Column(name = "monitoring_run_history_scheduled_at")
  @JsonProperty("monitoring_run_history_scheduled_at")
  @NotNull
  private Instant scheduledAt = now();

  @Column(name = "monitoring_run_history_finished_at")
  @JsonProperty("monitoring_run_history_finished_at")
  private Instant finishedAt;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "monitoring_run_history_status")
  @Enumerated(EnumType.STRING)
  @JsonProperty("monitoring_run_history_status")
  @NotNull
  private MonitoringRunStatus status = MonitoringRunStatus.triggered;

  @Column(name = "monitoring_run_history_hit_count")
  @JsonProperty("monitoring_run_history_hit_count")
  private Integer hitCount;

  @Column(name = "monitoring_run_history_miss_count")
  @JsonProperty("monitoring_run_history_miss_count")
  private Integer missCount;

  @Column(name = "monitoring_run_history_timeout_count")
  @JsonProperty("monitoring_run_history_timeout_count")
  private Integer timeoutCount;

  @Column(name = "monitoring_run_history_out_of_scope_count")
  @JsonProperty("monitoring_run_history_out_of_scope_count")
  private Integer outOfScopeCount;

  @Column(name = "monitoring_run_history_total_count")
  @JsonProperty("monitoring_run_history_total_count")
  private Integer totalCount;

  @Column(name = "monitoring_run_history_error_message")
  @JsonProperty("monitoring_run_history_error_message")
  private String errorMessage;

  @CreationTimestamp
  @Column(name = "monitoring_run_history_created_at")
  @JsonProperty("monitoring_run_history_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "monitoring_run_history_updated_at")
  @JsonProperty("monitoring_run_history_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
