package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainEdge;
import io.veriguard.database.model.AttackChainEdgeId;
import io.veriguard.database.model.NodeExecutor;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackChainEdgesRepository
    extends CrudRepository<AttackChainEdge, AttackChainEdgeId>,
        JpaSpecificationExecutor<NodeExecutor> {

  @Query(
      value =
          "SELECT "
              + "parent_node_id, "
              + "child_node_id, "
              + "edge_condition, "
              + "edge_created_at, "
              + "edge_updated_at "
              + "FROM attack_chain_edges "
              + "WHERE child_node_id IN :childrens",
      nativeQuery = true)
  List<AttackChainEdge> findParents(@NotNull List<String> childrens);
}
