package io.veriguard.database.model.combination;

import com.fasterxml.jackson.annotation.JsonProperty;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 攻击组合分级配置（singleton）—— IPv6 安全验证系统 §3.6 ★2 PR D4.
 *
 * <p>单 deploy 至多一行（应用层保证）。每行包含：
 * <ul>
 *   <li>三因子权重：miss_count_weight + attack_type_weight + asset_sensitivity_weight（和 = 1.0）</li>
 *   <li>三档分级阈值：critical / high / medium 的下界（info 兜底）</li>
 *   <li>4 档展示标签（label）和颜色（color，HTML hex）</li>
 * </ul>
 *
 * <p>对应 Flyway V14 表 {@code severity_configs}.
 */
@Getter
@Setter
@Entity
@Table(name = "severity_configs")
@EntityListeners(ModelBaseListener.class)
public class SeverityConfig implements Base {

  /** 应用层使用的固定 singleton id — 任何时刻最多落一行 */
  public static final String SINGLETON_ID = "00000000-0000-0000-0000-000000000001";

  @Id
  @Column(name = "severity_config_id")
  @JsonProperty("severity_config_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  // ---- 三因子权重 ----

  @Column(name = "severity_config_miss_count_weight")
  @JsonProperty("severity_config_miss_count_weight")
  @NotNull
  private BigDecimal missCountWeight = new BigDecimal("0.500");

  @Column(name = "severity_config_attack_type_weight")
  @JsonProperty("severity_config_attack_type_weight")
  @NotNull
  private BigDecimal attackTypeWeight = new BigDecimal("0.300");

  @Column(name = "severity_config_asset_sensitivity_weight")
  @JsonProperty("severity_config_asset_sensitivity_weight")
  @NotNull
  private BigDecimal assetSensitivityWeight = new BigDecimal("0.200");

  // ---- 三档分级阈值（下界） ----

  @Column(name = "severity_config_critical_threshold")
  @JsonProperty("severity_config_critical_threshold")
  @NotNull
  private BigDecimal criticalThreshold = new BigDecimal("70.00");

  @Column(name = "severity_config_high_threshold")
  @JsonProperty("severity_config_high_threshold")
  @NotNull
  private BigDecimal highThreshold = new BigDecimal("40.00");

  @Column(name = "severity_config_medium_threshold")
  @JsonProperty("severity_config_medium_threshold")
  @NotNull
  private BigDecimal mediumThreshold = new BigDecimal("10.00");

  // ---- 4 档标签 ----

  @Column(name = "severity_config_critical_label")
  @JsonProperty("severity_config_critical_label")
  @NotBlank
  private String criticalLabel = "高";

  @Column(name = "severity_config_high_label")
  @JsonProperty("severity_config_high_label")
  @NotBlank
  private String highLabel = "中";

  @Column(name = "severity_config_medium_label")
  @JsonProperty("severity_config_medium_label")
  @NotBlank
  private String mediumLabel = "低";

  @Column(name = "severity_config_info_label")
  @JsonProperty("severity_config_info_label")
  @NotBlank
  private String infoLabel = "信息";

  // ---- 4 档颜色 ----

  @Column(name = "severity_config_critical_color")
  @JsonProperty("severity_config_critical_color")
  @NotBlank
  private String criticalColor = "#dc2626";

  @Column(name = "severity_config_high_color")
  @JsonProperty("severity_config_high_color")
  @NotBlank
  private String highColor = "#f97316";

  @Column(name = "severity_config_medium_color")
  @JsonProperty("severity_config_medium_color")
  @NotBlank
  private String mediumColor = "#eab308";

  @Column(name = "severity_config_info_color")
  @JsonProperty("severity_config_info_color")
  @NotBlank
  private String infoColor = "#3b82f6";

  // ---- audit ----

  @CreationTimestamp
  @Column(name = "severity_config_created_at")
  @JsonProperty("severity_config_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "severity_config_updated_at")
  @JsonProperty("severity_config_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
