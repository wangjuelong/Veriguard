package io.veriguard.database.model.combination;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import io.veriguard.database.model.Base;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * 攻击组合模板 —— IPv6 安全验证系统 §3.6 ★2 攻击组合.
 *
 * <p>每条记录表示一个 {@code (base_attack_type, bypass_dimension)} 组合单元.
 * {@code base_attack_type} 是 §3.5 用例库 {@code attack_category} 枚举字符串
 * （PR D1 不在 DB 层加外键，避免循环依赖；Java/服务层校验有效性）.
 *
 * <p>组合数量目标：基础类型 250 × 绕过维度 120 ≈ 30 000 组合单元.
 *
 * <p>对应 Flyway V11 表 {@code attack_combination_templates}.
 */
@Getter
@Setter
@Entity
@Table(name = "attack_combination_templates")
@EntityListeners(ModelBaseListener.class)
public class AttackCombinationTemplate implements Base {

  @Id
  @Column(name = "attack_combination_template_id")
  @JsonProperty("attack_combination_template_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "attack_combination_template_name")
  @JsonProperty("attack_combination_template_name")
  @NotBlank
  private String name;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "attack_combination_template_base_attack_type")
  @JsonProperty("attack_combination_template_base_attack_type")
  @NotBlank
  private String baseAttackType;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "attack_combination_template_bypass_dimension_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("attack_combination_template_bypass_dimension")
  @Schema(type = "string")
  @NotNull
  private BypassDimension bypassDimension;

  @Column(name = "attack_combination_template_combined_payload_template")
  @JsonProperty("attack_combination_template_combined_payload_template")
  private String combinedPayloadTemplate;

  @Column(name = "attack_combination_template_description")
  @JsonProperty("attack_combination_template_description")
  private String description;

  @CreationTimestamp
  @Column(name = "attack_combination_template_created_at")
  @JsonProperty("attack_combination_template_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "attack_combination_template_updated_at")
  @JsonProperty("attack_combination_template_updated_at")
  private Instant updatedAt;
}
