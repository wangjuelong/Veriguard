package io.veriguard.database.repository;

import io.veriguard.database.model.OfflinePackAuditEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link OfflinePackAuditEntity} (Task C.13). */
@Repository
public interface OfflinePackAuditRepository extends JpaRepository<OfflinePackAuditEntity, UUID> {}
