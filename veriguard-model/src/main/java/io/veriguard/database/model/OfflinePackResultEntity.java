package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * JPA entity mirroring the {@code offline_pack_result} table (Flyway V21 / C1-Platform-3).
 *
 * <p>One row per task result inside a {@code .vresults} envelope imported via the Mode C 离线工作 flow.
 * {@link OfflinePackAuditEntity} is the per-pack summary row; this entity is the per-result detail.
 * Use composite key {@code (pack_id, ordinal)} so a single pack's result list can be read back in
 * agent-side execution order (the wire schema is a JSON array, correlated by index).
 *
 * <p>All result fields ({@code status / exit_code / stdout / stderr / started_at / finished_at /
 * error_message}) are stored verbatim from the agent's {@code TaskResult} struct — no aggregation,
 * no truncation. {@code task_id} is nullable because C1-Platform-3 does not yet maintain a
 * persistent {@code (pack_id, ordinal) → task_id} mapping; once that lands (future PR), this column
 * gets backfilled on import.
 */
@Getter
@Setter
@Entity
@Table(name = "offline_pack_result")
@IdClass(OfflinePackResultId.class)
public class OfflinePackResultEntity {

  @Id
  @Column(name = "pack_id")
  @JsonProperty("pack_id")
  private UUID packId;

  @Id
  @Column(name = "ordinal")
  @JsonProperty("ordinal")
  private int ordinal;

  @Column(name = "task_id")
  @JsonProperty("task_id")
  private String taskId;

  @Column(name = "status", nullable = false)
  @JsonProperty("status")
  private String status;

  @Column(name = "exit_code", nullable = false)
  @JsonProperty("exit_code")
  private int exitCode;

  @Column(name = "stdout")
  @JsonProperty("stdout")
  private String stdout;

  @Column(name = "stderr")
  @JsonProperty("stderr")
  private String stderr;

  @Column(name = "started_at")
  @JsonProperty("started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  @JsonProperty("finished_at")
  private Instant finishedAt;

  @Column(name = "error_message")
  @JsonProperty("error_message")
  private String errorMessage;

  @Column(name = "agent_id", nullable = false)
  @JsonProperty("agent_id")
  private String agentId;

  @Column(name = "imported_at", nullable = false)
  @JsonProperty("imported_at")
  private Instant importedAt;
}
