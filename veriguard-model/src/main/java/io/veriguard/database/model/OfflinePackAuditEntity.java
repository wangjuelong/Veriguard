package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * JPA entity mirroring the {@code offline_pack_audit} table (Flyway V20 / Task C.13).
 *
 * <p>Audit trail row for each {@code .vpack} export and matching {@code .vresults} import in the
 * Veriguard Agent (C1) Mode C 离线工作 flow. Export rows are created at {@code recordExport} time
 * with the import fields NULL; import rows update the SAME pack row (matched by UUID PK) with the
 * import fields when the operator returns a {@code .vresults} file.
 *
 * <p>FK to {@code agents.agent_id} is enforced at the SQL level (V20); the entity stores the agent
 * id as a VARCHAR(255) string to mirror {@link Agent#getId()}.
 *
 * <p>Per spec §3.5.5 the {@code task_count} CHECK constraint caps at 1000 — service-side
 * enforcement reads this same limit.
 */
@Getter
@Setter
@Entity
@Table(name = "offline_pack_audit")
public class OfflinePackAuditEntity {

  /** Spec §3.5.5 hard cap on per-pack task count (mirrored CHECK in V20). */
  public static final int MAX_TASK_COUNT = 1000;

  @Id
  @Column(name = "pack_id")
  @JsonProperty("pack_id")
  private UUID packId;

  @Column(name = "agent_id", nullable = false)
  @JsonProperty("agent_id")
  private String agentId;

  @Column(name = "platform_id", nullable = false)
  @JsonProperty("platform_id")
  private String platformId;

  @Column(name = "issued_at", nullable = false)
  @JsonProperty("issued_at")
  private Instant issuedAt;

  @Column(name = "exported_by", nullable = false)
  @JsonProperty("exported_by")
  private String exportedBy;

  @Column(name = "exported_from_ip", columnDefinition = "inet")
  @JsonProperty("exported_from_ip")
  private String exportedFromIp;

  @Column(name = "exported_ciphertext_sha256", nullable = false)
  @JsonProperty("exported_ciphertext_sha256")
  private byte[] exportedCiphertextSha256;

  @Column(name = "task_count", nullable = false)
  @JsonProperty("task_count")
  private int taskCount;

  // -- import-time fields (NULL until matching .vresults imported) --

  @Column(name = "imported_at")
  @JsonProperty("imported_at")
  private Instant importedAt;

  @Column(name = "imported_by")
  @JsonProperty("imported_by")
  private String importedBy;

  @Column(name = "imported_from_ip", columnDefinition = "inet")
  @JsonProperty("imported_from_ip")
  private String importedFromIp;

  @Column(name = "result_count")
  @JsonProperty("result_count")
  private Integer resultCount;

  @Column(name = "rejected_count")
  @JsonProperty("rejected_count")
  private Integer rejectedCount;
}
