package io.veriguard.database.repository;

import io.veriguard.database.model.InjectImporter;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectImporterRepository extends CrudRepository<InjectImporter, UUID> {}
