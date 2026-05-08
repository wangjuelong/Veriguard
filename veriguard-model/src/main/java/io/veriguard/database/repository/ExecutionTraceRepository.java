package io.veriguard.database.repository;

import io.veriguard.database.model.ExecutionTrace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionTraceRepository
    extends CrudRepository<ExecutionTrace, String>, JpaSpecificationExecutor<ExecutionTrace> {

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN attack_chain_nodes i ON ins.status_inject = i.node_id "
              + "INNER JOIN Agents a ON t.execution_agent_id = a.agent_id "
              + "WHERE i.node_id = :attackChainNodeId AND t.execution_agent_id = :targetId",
      nativeQuery = true)
  List<ExecutionTrace> findByAttackChainNodeIdAndAgentId(
      @Param("attackChainNodeId") String attackChainNodeId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN attack_chain_nodes i ON ins.status_inject = i.node_id "
              + "LEFT JOIN Agents a ON t.execution_agent_id = a.agent_id "
              + "WHERE i.node_id = :attackChainNodeId AND (a.agent_asset = :targetId OR :targetId = ANY(t.execution_context_identifiers))",
      nativeQuery = true)
  List<ExecutionTrace> findByAttackChainNodeIdAndAssetId(
      @Param("attackChainNodeId") String attackChainNodeId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN attack_chain_nodes i ON ins.status_inject = i.node_id "
              + "INNER JOIN users_teams ut ON ut.user_id = ANY(t.execution_context_identifiers) "
              + "WHERE i.node_id = :attackChainNodeId AND ut.team_id = :targetId",
      nativeQuery = true)
  List<ExecutionTrace> findByAttackChainNodeIdAndTeamId(
      @Param("attackChainNodeId") String attackChainNodeId, @Param("targetId") String targetId);

  @Query(
      value =
          "SELECT t.* FROM execution_traces t "
              + "INNER JOIN injects_statuses ins ON t.execution_inject_status_id = ins.status_id "
              + "INNER JOIN attack_chain_nodes i ON ins.status_inject = i.node_id "
              + "WHERE i.node_id = :attackChainNodeId AND :targetId = ANY(t.execution_context_identifiers)",
      nativeQuery = true)
  List<ExecutionTrace> findByAttackChainNodeIdAndPlayerId(
      @Param("attackChainNodeId") String attackChainNodeId, @Param("targetId") String targetId);
}
