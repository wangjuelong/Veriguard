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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 基础攻击类型 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR B5.
 *
 * <p>满足招标 §3.6 "至少 250 种攻击类型"门槛. 启动时由
 * {@code base_attack_types/250_base_attack_types.json} 通过 DataPack 框架种入；
 * 由 {@code BaseAttackPayloadCatalog} / {@code BaseAttackTypeSeverityCatalog}
 * 在启动期加载到内存缓存供 D2 generator / D4 severity classifier 使用.
 *
 * <p>对应 Flyway V17 表 {@code base_attack_types}.
 */
@Getter
@Setter
@Entity
@Table(name = "base_attack_types")
@EntityListeners(ModelBaseListener.class)
public class BaseAttackType implements Base {

  @Id
  @Column(name = "base_attack_type_id")
  @JsonProperty("base_attack_type_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "base_attack_type_name")
  @JsonProperty("base_attack_type_name")
  @NotBlank
  private String name;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "base_attack_type_category")
  @Enumerated(EnumType.STRING)
  @JsonProperty("base_attack_type_category")
  @NotNull
  private BaseAttackTypeCategory category;

  @Column(name = "base_attack_type_display_label")
  @JsonProperty("base_attack_type_display_label")
  @NotBlank
  private String displayLabel;

  @Column(name = "base_attack_type_description")
  @JsonProperty("base_attack_type_description")
  private String description;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "base_attack_type_severity_score")
  @JsonProperty("base_attack_type_severity_score")
  @Min(0)
  @Max(100)
  @NotNull
  private Integer severityScore;

  @Column(name = "base_attack_type_default_payload")
  @JsonProperty("base_attack_type_default_payload")
  private String defaultPayload;

  @Column(name = "base_attack_type_attack_pattern_id")
  @JsonProperty("base_attack_type_attack_pattern_id")
  private String attackPatternId;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "base_attack_type_target_layer")
  @Enumerated(EnumType.STRING)
  @JsonProperty("base_attack_type_target_layer")
  @NotNull
  private BaseAttackTypeTargetLayer targetLayer;

  @CreationTimestamp
  @Column(name = "base_attack_type_created_at")
  @JsonProperty("base_attack_type_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "base_attack_type_updated_at")
  @JsonProperty("base_attack_type_updated_at")
  private Instant updatedAt;
}
