package io.veriguard.database.repository;

import io.veriguard.database.model.ComcheckStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComcheckStatusRepository
    extends CrudRepository<ComcheckStatus, String>, JpaSpecificationExecutor<ComcheckStatus> {

  @NotNull
  Optional<ComcheckStatus> findById(@NotNull String id);
}
