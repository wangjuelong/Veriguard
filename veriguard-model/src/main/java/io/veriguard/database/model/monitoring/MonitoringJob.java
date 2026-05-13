package io.veriguard.database.model.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.database.model.Base;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * 边界策略监控任务 —— PR C4 招标 §3.2 常态化监控.
 *
 * <p>一条 monitoring_job = 一份「baseline + cron + enabled」配置；Quartz 周期触发器按 cron
 * 调用 {@code CoverageRunner.runAsync(baselineId)}，每次触发记一条
 * {@link MonitoringRunHistory}。
 *
 * <p>对应 Flyway V16 表 {@code monitoring_jobs}.
 */
@Getter
@Setter
@Entity
@Table(name = "monitoring_jobs")
@EntityListeners(ModelBaseListener.class)
public class MonitoringJob implements Base {

  @Id
  @Column(name = "monitoring_job_id")
  @JsonProperty("monitoring_job_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "monitoring_job_name")
  @JsonProperty("monitoring_job_name")
  @NotBlank
  private String name;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "monitoring_job_baseline_id")
  @JsonProperty("monitoring_job_baseline_id")
  @NotBlank
  private String baselineId;

  @Column(name = "monitoring_job_cron_expression")
  @JsonProperty("monitoring_job_cron_expression")
  @NotBlank
  private String cronExpression;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "monitoring_job_enabled")
  @JsonProperty("monitoring_job_enabled")
  private boolean enabled = true;

  @Column(name = "monitoring_job_description")
  @JsonProperty("monitoring_job_description")
  private String description;

  @Column(name = "monitoring_job_last_triggered_at")
  @JsonProperty("monitoring_job_last_triggered_at")
  private Instant lastTriggeredAt;

  @CreationTimestamp
  @Column(name = "monitoring_job_created_at")
  @JsonProperty("monitoring_job_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "monitoring_job_updated_at")
  @JsonProperty("monitoring_job_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
