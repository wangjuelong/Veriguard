package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * 沙箱任务行 —— C1-Platform-5 (M3) 在 {@code io.veriguard.integration.sandbox.SandboxDriver}（M2）
 * 之上的持久化队列。
 *
 * <p>每行表示一次"样本提交到 CAPEv2 → 状态轮询 → 终态归档"的完整任务生命周期。row 写入时 已携带 capeTaskId（synchronous-submit
 * 模型），Quartz {@code SandboxTaskPollingJob} 周期扫 status ∈ {QUEUED, RUNNING} 的行刷新状态。
 *
 * <p>样本二进制不入库（参见 V23 migration 注释）。
 */
@Getter
@Setter
@Entity
@Table(name = "veriguard_sandbox_tasks")
@EntityListeners(ModelBaseListener.class)
public class VeriguardSandboxTask implements Base {

  public enum Status {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    UNKNOWN
  }

  @Id
  @Column(name = "sandbox_task_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("sandbox_task_id")
  @Schema(type = "string")
  private String id;

  /**
   * Optional FK to a {@link VeriguardSandbox} preset; null when caller targets a machine directly.
   */
  @Column(name = "sandbox_task_sandbox_id")
  @JsonProperty("sandbox_task_sandbox_id")
  private String sandboxId;

  @Column(name = "sandbox_task_sample_sha256")
  @JsonProperty("sandbox_task_sample_sha256")
  @NotBlank
  private String sampleSha256;

  @Column(name = "sandbox_task_sample_filename")
  @JsonProperty("sandbox_task_sample_filename")
  @NotBlank
  private String sampleFilename;

  @Column(name = "sandbox_task_sample_size_bytes")
  @JsonProperty("sandbox_task_sample_size_bytes")
  @NotNull
  private Long sampleSizeBytes;

  @Column(name = "sandbox_task_sample_type")
  @JsonProperty("sandbox_task_sample_type")
  private String sampleType;

  @Column(name = "sandbox_task_target_machine")
  @JsonProperty("sandbox_task_target_machine")
  private String targetMachine;

  @Column(name = "sandbox_task_timeout_seconds")
  @JsonProperty("sandbox_task_timeout_seconds")
  private Integer timeoutSeconds;

  @Column(name = "sandbox_task_cape_task_id")
  @JsonProperty("sandbox_task_cape_task_id")
  private Long capeTaskId;

  @Column(name = "sandbox_task_status")
  @Enumerated(EnumType.STRING)
  @JsonProperty("sandbox_task_status")
  @NotNull
  private Status status;

  @Column(name = "sandbox_task_raw_status")
  @JsonProperty("sandbox_task_raw_status")
  private String rawStatus;

  @Column(name = "sandbox_task_error_message")
  @JsonProperty("sandbox_task_error_message")
  private String errorMessage;

  @Column(name = "sandbox_task_submitted_at")
  @JsonProperty("sandbox_task_submitted_at")
  private Instant submittedAt;

  @Column(name = "sandbox_task_last_polled_at")
  @JsonProperty("sandbox_task_last_polled_at")
  private Instant lastPolledAt;

  @Column(name = "sandbox_task_completed_at")
  @JsonProperty("sandbox_task_completed_at")
  private Instant completedAt;

  @Column(name = "sandbox_task_created_at")
  @JsonProperty("sandbox_task_created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "sandbox_task_updated_at")
  @JsonProperty("sandbox_task_updated_at")
  @UpdateTimestamp
  private Instant updatedAt;

  @JsonIgnore
  @Override
  public ResourceType getResourceType() {
    return ResourceType.PLATFORM_SETTING;
  }
}
