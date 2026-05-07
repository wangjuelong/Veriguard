package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainNodeStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackChainNodeReportingRepository extends CrudRepository<AttackChainNodeStatus, String> {

  @NotNull
  Optional<AttackChainNodeStatus> findById(@NotNull String id);
}
