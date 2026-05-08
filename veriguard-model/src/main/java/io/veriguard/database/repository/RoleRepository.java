package io.veriguard.database.repository;

import io.veriguard.database.model.Role;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository
    extends CrudRepository<Role, String>, JpaSpecificationExecutor<Role> {

  @NotNull
  Optional<Role> findById(@NotNull String id);
}
