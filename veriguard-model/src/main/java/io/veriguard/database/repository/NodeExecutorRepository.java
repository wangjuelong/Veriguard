package io.veriguard.database.repository;

import io.veriguard.database.model.NodeExecutor;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NodeExecutorRepository
    extends CrudRepository<NodeExecutor, String>, JpaSpecificationExecutor<NodeExecutor> {

  @NotNull
  Optional<NodeExecutor> findById(@NotNull String id);

  @NotNull
  Optional<NodeExecutor> findByType(@NotNull String type);

  List<NodeExecutor> findAllByPayloads(@NotNull Boolean payloads);
}
