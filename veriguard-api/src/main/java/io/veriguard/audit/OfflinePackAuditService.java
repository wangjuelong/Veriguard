package io.veriguard.audit;

import io.veriguard.database.model.OfflinePackAuditEntity;
import io.veriguard.database.repository.OfflinePackAuditRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit service for the Veriguard Agent (C1) Mode C offline pack flow (Task C.13).
 *
 * <p>Persists one row per {@code .vpack} export with all the export-time metadata; matching {@code
 * .vresults} import calls update the SAME row (matched by {@code pack_id} UUID) with the
 * import-time fields. Underlying table is {@code offline_pack_audit} (Flyway V20).
 */
@Service
@Slf4j
public class OfflinePackAuditService {

  private final OfflinePackAuditRepository repository;

  public OfflinePackAuditService(OfflinePackAuditRepository repository) {
    this.repository = repository;
  }

  /**
   * Record an export event. Creates a fresh audit row with import fields NULL.
   *
   * @param packId UUID matching {@code .vpack} metadata.pack_id
   * @param agentId target agent_id (FK to agents.agent_id)
   * @param platformId platform identity (e.g. {@code veriguard-prod-001})
   * @param exportedBy admin operator username
   * @param exportedFromIp client IP (may be null in test contexts)
   * @param exportedCiphertextSha256 SHA-256 of the encrypted envelope ciphertext (32 bytes)
   * @param taskCount number of tasks in the pack (must be ≥ 0 and ≤ {@link
   *     OfflinePackAuditEntity#MAX_TASK_COUNT})
   * @return the persisted entity
   * @throws IllegalArgumentException if {@code taskCount} is out of bounds
   */
  @Transactional
  public OfflinePackAuditEntity recordExport(
      UUID packId,
      String agentId,
      String platformId,
      String exportedBy,
      String exportedFromIp,
      byte[] exportedCiphertextSha256,
      int taskCount) {
    if (packId == null) {
      throw new IllegalArgumentException("packId must not be null");
    }
    if (agentId == null || agentId.isBlank()) {
      throw new IllegalArgumentException("agentId must not be blank");
    }
    if (taskCount < 0 || taskCount > OfflinePackAuditEntity.MAX_TASK_COUNT) {
      throw new IllegalArgumentException(
          "taskCount must be in [0, "
              + OfflinePackAuditEntity.MAX_TASK_COUNT
              + "], got "
              + taskCount);
    }
    if (exportedCiphertextSha256 == null || exportedCiphertextSha256.length != 32) {
      throw new IllegalArgumentException(
          "exportedCiphertextSha256 must be exactly 32 bytes (SHA-256 digest)");
    }
    OfflinePackAuditEntity row = new OfflinePackAuditEntity();
    row.setPackId(packId);
    row.setAgentId(agentId);
    row.setPlatformId(platformId);
    row.setIssuedAt(Instant.now());
    row.setExportedBy(exportedBy != null ? exportedBy : "unknown");
    row.setExportedFromIp(exportedFromIp);
    row.setExportedCiphertextSha256(exportedCiphertextSha256.clone());
    row.setTaskCount(taskCount);
    OfflinePackAuditEntity saved = repository.save(row);
    log.info(
        "OfflinePackAudit.recordExport pack_id={}, agent_id={}, task_count={}, exported_by={}",
        packId,
        agentId,
        taskCount,
        exportedBy);
    return saved;
  }

  /**
   * Record an import event. Looks up the existing row by {@code packId} and updates the import
   * fields.
   *
   * @param packId UUID matching the {@code .vresults} metadata.pack_id (must already exist — export
   *     was recorded earlier)
   * @param importedBy admin operator username doing the import
   * @param importedFromIp client IP (may be null in test contexts)
   * @param resultCount number of results in the {@code .vresults} envelope
   * @param rejectedCount number of results rejected on import
   * @return the updated entity, or {@link Optional#empty()} if no row matches {@code packId} (the
   *     export must have happened on a different platform — caller decides whether to surface a
   *     hard error or to log only)
   */
  @Transactional
  public Optional<OfflinePackAuditEntity> recordImport(
      UUID packId, String importedBy, String importedFromIp, int resultCount, int rejectedCount) {
    if (packId == null) {
      throw new IllegalArgumentException("packId must not be null");
    }
    if (resultCount < 0 || rejectedCount < 0) {
      throw new IllegalArgumentException(
          "resultCount/rejectedCount must be ≥ 0 (got result="
              + resultCount
              + ", rejected="
              + rejectedCount
              + ")");
    }
    Optional<OfflinePackAuditEntity> existing = repository.findById(packId);
    if (existing.isEmpty()) {
      log.warn(
          "OfflinePackAudit.recordImport called for unknown pack_id={} (no matching export row);"
              + " skipping audit update",
          packId);
      return Optional.empty();
    }
    OfflinePackAuditEntity row = existing.get();
    row.setImportedAt(Instant.now());
    row.setImportedBy(importedBy != null ? importedBy : "unknown");
    row.setImportedFromIp(importedFromIp);
    row.setResultCount(resultCount);
    row.setRejectedCount(rejectedCount);
    OfflinePackAuditEntity saved = repository.save(row);
    log.info(
        "OfflinePackAudit.recordImport pack_id={}, imported_by={}, result_count={},"
            + " rejected_count={}",
        packId,
        importedBy,
        resultCount,
        rejectedCount);
    return Optional.of(saved);
  }
}
