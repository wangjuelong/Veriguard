package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * 验证参数集（spec §2.2.4 / PRD §2.4 全局 + 节点级验证参数 ） —— 命名 + 复用：链路或节点引用本实体的 id
 * 来共享同一组 expectation 默认值（PREVENTION / DETECTION 期望分 + 超时 + SOC 规则集）。 系统预设 3 条
 * (严格 / 宽松 / 快速演练) 由 V3 migration 种子写入并以 is_template=true 标记，**不可编辑、不可删除**。
 */
@Setter
@Getter
@Entity
@Table(name = "validation_parameter_sets")
public class ValidationParameterSet {

  @Id
  @Column(name = "parameter_set_id")
  @JsonProperty("parameter_set_id")
  private UUID id;

  @Column(name = "name", unique = true, nullable = false)
  @JsonProperty("parameter_set_name")
  private String name;

  @Column(name = "description")
  @JsonProperty("parameter_set_description")
  private String description;

  @Column(name = "is_template", nullable = false)
  @JsonProperty("parameter_set_is_template")
  private boolean template = false;

  @Type(JsonBinaryType.class)
  @Column(name = "default_targets", columnDefinition = "jsonb")
  @JsonProperty("parameter_set_default_targets")
  private List<TargetRef> defaultTargets = new ArrayList<>();

  @Column(name = "prevention_expected_score", nullable = false)
  @JsonProperty("parameter_set_prevention_expected_score")
  private int preventionExpectedScore = 100;

  @Column(name = "prevention_expiration_seconds", nullable = false)
  @JsonProperty("parameter_set_prevention_expiration_seconds")
  private int preventionExpirationSeconds = 1800;

  @Column(name = "detection_expected_score", nullable = false)
  @JsonProperty("parameter_set_detection_expected_score")
  private int detectionExpectedScore = 100;

  @Column(name = "detection_expiration_seconds", nullable = false)
  @JsonProperty("parameter_set_detection_expiration_seconds")
  private int detectionExpirationSeconds = 1800;

  @Type(JsonBinaryType.class)
  @Column(name = "soc_correlation_rules", columnDefinition = "jsonb")
  @JsonProperty("parameter_set_soc_correlation_rules")
  private List<SocCorrelationRuleRef> socCorrelationRules = new ArrayList<>();

  @ManyToMany
  @JoinTable(
      name = "validation_parameter_set_tags",
      joinColumns = @JoinColumn(name = "parameter_set_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonProperty("parameter_set_tags")
  private Set<Tag> tags = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  @JsonProperty("parameter_set_created_at")
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @JsonProperty("parameter_set_updated_at")
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
