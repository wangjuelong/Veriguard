package io.veriguard.database.model.coverage;

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
 * 覆盖度评估任务 —— PR C3.
 *
 * <p>一条 run = 一次基线评估；run 完成后 4 态计数固化到 *_count 列以便快速 list/汇总.
 *
 * <p>对应 Flyway V15 表 {@code coverage_runs}.
 */
@Getter
@Setter
@Entity
@Table(name = "coverage_runs")
@EntityListeners(ModelBaseListener.class)
public class CoverageRun implements Base {

  @Id
  @Column(name = "coverage_run_id")
  @JsonProperty("coverage_run_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "coverage_run_baseline_id")
  @JsonProperty("coverage_run_baseline_id")
  @NotBlank
  private String baselineId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "coverage_run_status")
  @Enumerated(EnumType.STRING)
  @JsonProperty("coverage_run_status")
  @NotNull
  private CoverageRunStatus status = CoverageRunStatus.pending;

  @Column(name = "coverage_run_total_cells")
  @JsonProperty("coverage_run_total_cells")
  private int totalCells;

  @Column(name = "coverage_run_hit_count")
  @JsonProperty("coverage_run_hit_count")
  private int hitCount;

  @Column(name = "coverage_run_miss_count")
  @JsonProperty("coverage_run_miss_count")
  private int missCount;

  @Column(name = "coverage_run_timeout_count")
  @JsonProperty("coverage_run_timeout_count")
  private int timeoutCount;

  @Column(name = "coverage_run_out_of_scope_count")
  @JsonProperty("coverage_run_out_of_scope_count")
  private int outOfScopeCount;

  @Column(name = "coverage_run_started_at")
  @JsonProperty("coverage_run_started_at")
  private Instant startedAt;

  @Column(name = "coverage_run_finished_at")
  @JsonProperty("coverage_run_finished_at")
  private Instant finishedAt;

  @Column(name = "coverage_run_error_message")
  @JsonProperty("coverage_run_error_message")
  private String errorMessage;

  @CreationTimestamp
  @Column(name = "coverage_run_created_at")
  @JsonProperty("coverage_run_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "coverage_run_updated_at")
  @JsonProperty("coverage_run_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
