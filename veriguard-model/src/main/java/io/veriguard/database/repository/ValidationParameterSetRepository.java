package io.veriguard.database.repository;

import io.veriguard.database.model.ValidationParameterSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ValidationParameterSetRepository
    extends JpaRepository<ValidationParameterSet, UUID>,
        JpaSpecificationExecutor<ValidationParameterSet> {

  Optional<ValidationParameterSet> findByName(String name);

  boolean existsByName(String name);
}
