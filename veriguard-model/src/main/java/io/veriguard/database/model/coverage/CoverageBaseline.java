package io.veriguard.database.model.coverage;

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
import static java.time.Instant.now;

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
 * 覆盖度基线 —— PR C3 边界覆盖度子模块.
 *
 * <p>一条 baseline = 一份「用例集合 + 资产组」配置；可被多次评估 (run) 复用以观察趋势.
 *
 * <p>对应 Flyway V15 表 {@code coverage_baselines}.
 */
@Getter
@Setter
@Entity
@Table(name = "coverage_baselines")
@EntityListeners(ModelBaseListener.class)
public class CoverageBaseline implements Base {

  @Id
  @Column(name = "coverage_baseline_id")
  @JsonProperty("coverage_baseline_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "coverage_baseline_name")
  @JsonProperty("coverage_baseline_name")
  @NotBlank
  private String name;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "coverage_baseline_coverage_type")
  @Enumerated(EnumType.STRING)
  @JsonProperty("coverage_baseline_coverage_type")
  @NotNull
  private CoverageType coverageType;

  @Type(JsonType.class)
  @Column(name = "coverage_baseline_case_ids", columnDefinition = "jsonb")
  @JsonProperty("coverage_baseline_case_ids")
  @NotNull
  private List<String> caseIds = new ArrayList<>();

  @Column(name = "coverage_baseline_asset_group_id")
  @JsonProperty("coverage_baseline_asset_group_id")
  @NotBlank
  private String assetGroupId;

  @Column(name = "coverage_baseline_description")
  @JsonProperty("coverage_baseline_description")
  private String description;

  @Column(name = "coverage_baseline_soc_query_delay_seconds")
  @JsonProperty("coverage_baseline_soc_query_delay_seconds")
  private int socQueryDelaySeconds = 60;

  @CreationTimestamp
  @Column(name = "coverage_baseline_created_at")
  @JsonProperty("coverage_baseline_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "coverage_baseline_updated_at")
  @JsonProperty("coverage_baseline_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
