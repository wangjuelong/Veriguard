package io.veriguard.database.repository;

import io.veriguard.database.model.Variable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VariableRepository
    extends CrudRepository<Variable, String>, JpaSpecificationExecutor<Variable> {

  @Query(
      value =
          "INSERT INTO variables(variable_id, variable_key, variable_value, variable_description, variable_type, variable_exercise)"
              + "   SELECT gen_random_uuid(), variable_key, variable_value, variable_description, variable_type, :attackChainRunId FROM variables as old"
              + "   WHERE old.variable_scenario = :attackChainId",
      nativeQuery = true)
  @Modifying
  void copyVariableFromAttackChainForSimulation(String attackChainId, String attackChainRunId);
}
