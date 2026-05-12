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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 绕过维度 —— IPv6 安全验证系统 §3.6 ★2 攻击组合的基础维度库.
 *
 * <p>每个维度承载一个 payload 变换器（{@code transformType} 字符串路由到具体
 * {@link io.veriguard.combination.transform.PayloadTransform}）与其参数化配置
 * （{@code transformConfig}）. 首批 120 项由
 * {@code bypass_dimensions/120_dimensions.json} 在启动时种入.
 *
 * <p>对应 Flyway V11 表 {@code bypass_dimensions}.
 */
@Getter
@Setter
@Entity
@Table(name = "bypass_dimensions")
@EntityListeners(ModelBaseListener.class)
public class BypassDimension implements Base {

  @Id
  @Column(name = "bypass_dimension_id")
  @JsonProperty("bypass_dimension_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "bypass_dimension_name")
  @JsonProperty("bypass_dimension_name")
  @NotBlank
  private String name;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "bypass_dimension_category")
  @Enumerated(EnumType.STRING)
  @JsonProperty("bypass_dimension_category")
  @NotNull
  private BypassDimensionCategory category;

  @Column(name = "bypass_dimension_description")
  @JsonProperty("bypass_dimension_description")
  private String description;

  @Column(name = "bypass_dimension_transform_type")
  @JsonProperty("bypass_dimension_transform_type")
  @NotBlank
  private String transformType;

  @Type(JsonType.class)
  @Column(name = "bypass_dimension_transform_config", columnDefinition = "jsonb")
  @JsonProperty("bypass_dimension_transform_config")
  @NotNull
  private Map<String, Object> transformConfig = new HashMap<>();

  @CreationTimestamp
  @Column(name = "bypass_dimension_created_at")
  @JsonProperty("bypass_dimension_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "bypass_dimension_updated_at")
  @JsonProperty("bypass_dimension_updated_at")
  private Instant updatedAt;
}
