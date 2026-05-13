package io.veriguard.database.model.combination;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 攻击组合任务 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D2.
 *
 * <p>一条 run 行 = 一次「base_attack_types × bypass_dimensions × asset_set」的笛卡尔积执行任务.
 * 限流 / 并发 / 重试 / 超时配置以列形式落表，运行时由 CombinationScheduler 消费.
 *
 * <p>对应 Flyway V12 表 {@code attack_combination_runs}.
 */
@Getter
@Setter
@Entity
@Table(name = "attack_combination_runs")
@EntityListeners(ModelBaseListener.class)
public class AttackCombinationRun implements Base {

  @Id
  @Column(name = "attack_combination_run_id")
  @JsonProperty("attack_combination_run_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "attack_combination_run_name")
  @JsonProperty("attack_combination_run_name")
  @NotBlank
  private String name;

  @Type(JsonType.class)
  @Column(name = "attack_combination_run_base_attack_types", columnDefinition = "jsonb")
  @JsonProperty("attack_combination_run_base_attack_types")
  @NotNull
  private List<String> baseAttackTypes = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "attack_combination_run_bypass_dimension_ids", columnDefinition = "jsonb")
  @JsonProperty("attack_combination_run_bypass_dimension_ids")
  @NotNull
  private List<String> bypassDimensionIds = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "attack_combination_run_asset_ids", columnDefinition = "jsonb")
  @JsonProperty("attack_combination_run_asset_ids")
  @NotNull
  private List<String> assetIds = new ArrayList<>();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_run_status")
  @Enumerated(EnumType.STRING)
  @JsonProperty("attack_combination_run_status")
  @NotNull
  private AttackCombinationRunStatus status = AttackCombinationRunStatus.pending;

  @Column(name = "attack_combination_run_total_combinations")
  @JsonProperty("attack_combination_run_total_combinations")
  private int totalCombinations;

  @Column(name = "attack_combination_run_total_results")
  @JsonProperty("attack_combination_run_total_results")
  private int totalResults;

  @Column(name = "attack_combination_run_completed_count")
  @JsonProperty("attack_combination_run_completed_count")
  private int completedCount;

  @Column(name = "attack_combination_run_failed_count")
  @JsonProperty("attack_combination_run_failed_count")
  private int failedCount;

  @Column(name = "attack_combination_run_rate_limit_per_second")
  @JsonProperty("attack_combination_run_rate_limit_per_second")
  private int rateLimitPerSecond = 100;

  @Column(name = "attack_combination_run_concurrency")
  @JsonProperty("attack_combination_run_concurrency")
  private int concurrency = 16;

  @Column(name = "attack_combination_run_max_retries")
  @JsonProperty("attack_combination_run_max_retries")
  private int maxRetries = 3;

  @Column(name = "attack_combination_run_timeout_hours")
  @JsonProperty("attack_combination_run_timeout_hours")
  private int timeoutHours = 24;

  @Column(name = "attack_combination_run_started_at")
  @JsonProperty("attack_combination_run_started_at")
  private Instant startedAt;

  @Column(name = "attack_combination_run_completed_at")
  @JsonProperty("attack_combination_run_completed_at")
  private Instant completedAt;

  @Column(name = "attack_combination_run_expires_at")
  @JsonProperty("attack_combination_run_expires_at")
  @NotNull
  private Instant expiresAt;

  @CreationTimestamp
  @Column(name = "attack_combination_run_created_at")
  @JsonProperty("attack_combination_run_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "attack_combination_run_updated_at")
  @JsonProperty("attack_combination_run_updated_at")
  private Instant updatedAt;
}
