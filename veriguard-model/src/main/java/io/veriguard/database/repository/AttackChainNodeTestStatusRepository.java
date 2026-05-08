package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeTestStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

public interface AttackChainNodeTestStatusRepository
    extends CrudRepository<AttackChainNodeTestStatus, String>,
        JpaSpecificationExecutor<AttackChainNodeTestStatus> {

  @NotNull
  Optional<AttackChainNodeTestStatus> findById(@NotNull String id);

  Optional<AttackChainNodeTestStatus> findByAttackChainNode(
      @NotNull AttackChainNode attackChainNode);
}
