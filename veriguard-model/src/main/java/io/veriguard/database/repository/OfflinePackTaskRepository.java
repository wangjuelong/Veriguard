package io.veriguard.database.repository;

import io.veriguard.database.model.OfflinePackTaskEntity;
import io.veriguard.database.model.OfflinePackTaskId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OfflinePackTaskEntity} (Flyway V22).
 *
 * <p>Holds the (pack_id, ordinal) → task_id mapping captured at offline-pack export time and read
 * back at import time to backfill {@code offline_pack_result.task_id}.
 */
@Repository
public interface OfflinePackTaskRepository
    extends JpaRepository<OfflinePackTaskEntity, OfflinePackTaskId> {

  /** Read a pack's drained tasks in original execution order. */
  List<OfflinePackTaskEntity> findByPackIdOrderByOrdinalAsc(UUID packId);
}
