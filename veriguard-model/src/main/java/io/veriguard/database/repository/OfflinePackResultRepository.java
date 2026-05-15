package io.veriguard.database.repository;

import io.veriguard.database.model.OfflinePackResultEntity;
import io.veriguard.database.model.OfflinePackResultId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OfflinePackResultEntity} (C1-Platform-3).
 *
 * <p>One row per result inside a {@code .vresults} envelope imported via the Mode C 离线工作 flow.
 */
@Repository
public interface OfflinePackResultRepository
    extends JpaRepository<OfflinePackResultEntity, OfflinePackResultId> {

  /**
   * Read a pack's results in agent execution order. Used by the admin UI to render the per-result
   * detail view alongside the {@link io.veriguard.database.model.OfflinePackAuditEntity} summary
   * row.
   */
  List<OfflinePackResultEntity> findByPackIdOrderByOrdinalAsc(UUID packId);
}
