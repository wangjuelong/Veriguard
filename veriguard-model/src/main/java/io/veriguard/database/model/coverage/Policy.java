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
 * 安全设备策略 —— PR C3 边界覆盖度子模块.
 *
 * <p>对应 Flyway V15 表 {@code policies}.
 */
@Getter
@Setter
@Entity
@Table(name = "policies")
@EntityListeners(ModelBaseListener.class)
public class Policy implements Base {

  @Id
  @Column(name = "policy_id")
  @JsonProperty("policy_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "policy_name")
  @JsonProperty("policy_name")
  @NotBlank
  private String name;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "policy_device_type")
  @Enumerated(EnumType.STRING)
  @JsonProperty("policy_device_type")
  @NotNull
  private PolicyDeviceType deviceType;

  @Column(name = "policy_device_id")
  @JsonProperty("policy_device_id")
  private String deviceId;

  @Column(name = "policy_external_rule_id")
  @JsonProperty("policy_external_rule_id")
  private String externalRuleId;

  @Column(name = "policy_description")
  @JsonProperty("policy_description")
  private String description;

  @CreationTimestamp
  @Column(name = "policy_created_at")
  @JsonProperty("policy_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Column(name = "policy_updated_at")
  @JsonProperty("policy_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
