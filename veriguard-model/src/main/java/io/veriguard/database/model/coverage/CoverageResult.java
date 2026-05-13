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
 * 4 态覆盖单元格 (asset × policy) —— PR C3.
 *
 * <p>对应 Flyway V15 表 {@code coverage_results}.
 *
 * <p>UNIQUE(run_id, asset_id, policy_id) 保证单格幂等；case_id 作为附加上下文（可空，单格可由多个 case 触发）.
 */
@Getter
@Setter
@Entity
@Table(name = "coverage_results")
@EntityListeners(ModelBaseListener.class)
public class CoverageResult implements Base {

  @Id
  @Column(name = "coverage_result_id")
  @JsonProperty("coverage_result_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "coverage_result_run_id")
  @JsonProperty("coverage_result_run_id")
  @NotBlank
  private String runId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "coverage_result_asset_id")
  @JsonProperty("coverage_result_asset_id")
  @NotBlank
  private String assetId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "coverage_result_policy_id")
  @JsonProperty("coverage_result_policy_id")
  @NotBlank
  private String policyId;

  @Column(name = "coverage_result_case_id")
  @JsonProperty("coverage_result_case_id")
  private String caseId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "coverage_result_hit_state")
  @Enumerated(EnumType.STRING)
  @JsonProperty("coverage_result_hit_state")
  @NotNull
  private CoverageHitState hitState;

  @Column(name = "coverage_result_alert_rule_id")
  @JsonProperty("coverage_result_alert_rule_id")
  private String alertRuleId;

  @Column(name = "coverage_result_observed_at")
  @JsonProperty("coverage_result_observed_at")
  private Instant observedAt;

  @Column(name = "coverage_result_error_message")
  @JsonProperty("coverage_result_error_message")
  private String errorMessage;

  @CreationTimestamp
  @Column(name = "coverage_result_created_at")
  @JsonProperty("coverage_result_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "coverage_result_updated_at")
  @JsonProperty("coverage_result_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
