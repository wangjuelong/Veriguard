package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainEdge;
import io.veriguard.database.model.AttackChainEdgeId;
import io.veriguard.database.model.NodeExecutor;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  /**
   * V3 起 edge 主键改为单列 UUID，给 ConditionEdgePopover REST PUT
   * /api/attack_chain_edges/{id}/condition 提供按 UUID 查找的入口。
   */
  Optional<AttackChainEdge> findByEdgeId(@NotNull UUID edgeId);
}
