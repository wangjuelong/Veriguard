package io.veriguard.database.repository;

import io.veriguard.database.model.InjectDependency;
import io.veriguard.database.model.InjectDependencyId;
import io.veriguard.database.model.Injector;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectDependenciesRepository
    extends CrudRepository<InjectDependency, InjectDependencyId>,
        JpaSpecificationExecutor<Injector> {

  @Query(
      value =
          "SELECT "
              + "inject_parent_id, "
              + "inject_children_id, "
              + "dependency_condition, "
              + "dependency_created_at, "
              + "dependency_updated_at "
              + "FROM injects_dependencies "
              + "WHERE inject_children_id IN :childrens",
      nativeQuery = true)
  List<InjectDependency> findParents(@NotNull List<String> childrens);
}
