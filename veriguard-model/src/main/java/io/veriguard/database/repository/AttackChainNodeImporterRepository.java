package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainNodeImporter;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackChainNodeImporterRepository extends CrudRepository<AttackChainNodeImporter, UUID> {}
