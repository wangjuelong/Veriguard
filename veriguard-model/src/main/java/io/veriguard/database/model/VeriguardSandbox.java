package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "veriguard_sandboxes")
@EntityListeners(ModelBaseListener.class)
public class VeriguardSandbox implements Base {

  public enum NetworkPolicy {
    DENY_ALL,
    ALLOWLIST,
    ISOLATED_LAB,
    CUSTOM
  }

  public enum SampleType {
    RANSOMWARE,
    MINER,
    WORM,
    MALICIOUS_DRIVER,
    PRIVILEGE_ESCALATION,
    ACCOUNT_THEFT,
    PROXY_EXECUTION,
    SECURITY_COMPONENT_BYPASS
  }

  public enum Status {
    ACTIVE,
    INACTIVE
  }

  @Id
  @Column(name = "veriguard_sandbox_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("sandbox_id")
  @Schema(type = "string")
  private String id;

  @Column(name = "veriguard_sandbox_name")
  @JsonProperty("sandbox_name")
  @NotBlank
  private String name;

  @Column(name = "veriguard_sandbox_description")
  @JsonProperty("sandbox_description")
  private String description;

  @Column(name = "veriguard_sandbox_network_policy")
  @Enumerated(EnumType.STRING)
  @JsonProperty("sandbox_network_policy")
  @NotNull
  private NetworkPolicy networkPolicy;

  @Type(JsonType.class)
  @Column(name = "veriguard_sandbox_network_rules", columnDefinition = "jsonb")
  @JsonProperty("sandbox_network_rules")
  private List<VeriguardSandboxNetworkRule> networkRules = new ArrayList<>();

  @Column(name = "veriguard_sandbox_auto_restore_enabled")
  @JsonProperty("sandbox_auto_restore_enabled")
  private boolean autoRestoreEnabled;

  @Type(JsonType.class)
  @Column(name = "veriguard_sandbox_supported_sample_types", columnDefinition = "jsonb")
  @JsonProperty("sandbox_supported_sample_types")
  private List<SampleType> supportedSampleTypes = new ArrayList<>();

  @Column(name = "veriguard_sandbox_status")
  @Enumerated(EnumType.STRING)
  @JsonProperty("sandbox_status")
  @NotNull
  private Status status;

  @Column(name = "veriguard_sandbox_created_at")
  @JsonProperty("sandbox_created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "veriguard_sandbox_updated_at")
  @JsonProperty("sandbox_updated_at")
  @UpdateTimestamp
  private Instant updatedAt;

  @JsonIgnore
  @Override
  public ResourceType getResourceType() {
    return ResourceType.PLATFORM_SETTING;
  }
}
