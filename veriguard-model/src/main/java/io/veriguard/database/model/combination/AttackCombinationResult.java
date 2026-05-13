package io.veriguard.database.model.combination;

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
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 攻击组合结果 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D2.
 *
 * <p>每行 = 一次 (run × combination × asset) 的执行结果. combination_id 是「虚拟 ID」=
 * base_attack_type + ":" + bypass_dimension_id（不引用 templates 表，因为组合是即时笛卡尔积）.
 *
 * <p>对应 Flyway V12 表 {@code attack_combination_results}.
 */
@Getter
@Setter
@Entity
@Table(name = "attack_combination_results")
@EntityListeners(ModelBaseListener.class)
public class AttackCombinationResult implements Base {

  @Id
  @Column(name = "attack_combination_result_id")
  @JsonProperty("attack_combination_result_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_result_run_id")
  @JsonProperty("attack_combination_result_run_id")
  @NotBlank
  private String runId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_result_combination_id")
  @JsonProperty("attack_combination_result_combination_id")
  @NotBlank
  private String combinationId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_result_base_attack_type")
  @JsonProperty("attack_combination_result_base_attack_type")
  @NotBlank
  private String baseAttackType;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_result_bypass_dimension_id")
  @JsonProperty("attack_combination_result_bypass_dimension_id")
  @NotBlank
  private String bypassDimensionId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_result_asset_id")
  @JsonProperty("attack_combination_result_asset_id")
  @NotBlank
  private String assetId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_result_hit_state")
  @Enumerated(EnumType.STRING)
  @JsonProperty("attack_combination_result_hit_state")
  @NotNull
  private AttackCombinationHitState hitState = AttackCombinationHitState.pending;

  @Column(name = "attack_combination_result_retry_count")
  @JsonProperty("attack_combination_result_retry_count")
  private int retryCount;

  @Column(name = "attack_combination_result_payload_sample")
  @JsonProperty("attack_combination_result_payload_sample")
  private String payloadSample;

  @Column(name = "attack_combination_result_error_message")
  @JsonProperty("attack_combination_result_error_message")
  private String errorMessage;

  @Column(name = "attack_combination_result_executed_at")
  @JsonProperty("attack_combination_result_executed_at")
  private Instant executedAt;

  @CreationTimestamp
  @Column(name = "attack_combination_result_created_at")
  @JsonProperty("attack_combination_result_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "attack_combination_result_updated_at")
  @JsonProperty("attack_combination_result_updated_at")
  private Instant updatedAt;
}
