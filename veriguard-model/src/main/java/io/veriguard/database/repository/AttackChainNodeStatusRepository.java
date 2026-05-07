package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainNodeStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackChainNodeStatusRepository
    extends CrudRepository<AttackChainNodeStatus, String>, JpaSpecificationExecutor<AttackChainNodeStatus> {

  @NotNull
  Optional<AttackChainNodeStatus> findById(@NotNull String id);

  @Query(
      value =
          "select c from AttackChainNodeStatus c where c.name = 'PENDING' and c.attackChainNode.nodeContract.nodeExecutor.type = :attackChainNodeType")
  List<AttackChainNodeStatus> pendingForAttackChainNodeType(@Param("injectType") String attackChainNodeType);

  Optional<AttackChainNodeStatus> findByAttackChainNodeId(@NotNull String attackChainNodeId);

  List<AttackChainNodeStatus> findAllByAttackChainNodeIdIn(Collection<String> attackChainNodeIds);

  @Query(
      value =
          "SELECT ins.*, t.*"
              + " FROM injects_statuses ins"
              + " INNER JOIN injects i ON ins.status_inject = i.inject_id"
              + " LEFT JOIN execution_traces t"
              + "  ON t.execution_inject_status_id = ins.status_id"
              + "  AND t.execution_agent_id IS NULL"
              + "  AND cardinality(t.execution_context_identifiers) = 0"
              + " WHERE i.inject_id = :injectId",
      nativeQuery = true)
  Optional<AttackChainNodeStatus> findAttackChainNodeStatusWithGlobalExecutionTraces(String attackChainNodeId);

  @Modifying(clearAutomatically = true)
  @Query("delete from AttackChainNodeStatus i where i.id in :ids")
  void deleteAllByIds(@Param("ids") List<String> ids);
}
