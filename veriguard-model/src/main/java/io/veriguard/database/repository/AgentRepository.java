package io.veriguard.database.repository;

import io.veriguard.database.model.Agent;
import io.veriguard.database.raw.RawAgent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AgentRepository
    extends CrudRepository<Agent, String>, JpaSpecificationExecutor<Agent> {

  @Query(
      value =
          "SELECT a.* FROM agents a left join executors ex on a.agent_executor = ex.executor_id "
              + "where a.agent_asset = :assetId and a.agent_executed_by_user = :user and a.agent_deployment_mode = :deployment "
              + "and a.agent_privilege = :privilege and a.agent_parent is null and a.agent_inject is null and ex.executor_type = :executor",
      nativeQuery = true)
  Optional<Agent> findByAssetExecutorUserDeploymentAndPrivilege(
      @Param("assetId") String assetId,
      @Param("user") String user,
      @Param("deployment") String deployment,
      @Param("privilege") String privilege,
      @Param("executor") String executor);

  @Query(
      value =
          "SELECT a.* FROM agents a left join executors ex on a.agent_executor = ex.executor_id "
              + "where ex.executor_type = :executor",
      nativeQuery = true)
  List<Agent> findByExecutorType(@Param("executor") String executor);

  List<Agent> findByExternalReference(String externalReference);

  /**
   * Returns the agents for Caldera execution
   *
   * @return the list of agents
   */
  @Query(
      value =
          "SELECT a.* FROM agents a WHERE a.agent_parent is not null and a.agent_inject is not null;",
      nativeQuery = true)
  List<Agent> findForExecution();

  // TODO : understand why the generic deleteById from Hibernate doesn't work
  @Modifying
  @Query(value = "DELETE FROM agents agent where agent.agent_id = :agentId;", nativeQuery = true)
  @Transactional
  void deleteByAgentId(String agentId);

  /**
   * 查询声明了指定 capability 的 Agent 列表（B-ii PR-A）.
   *
   * <p>JSONB 数组包含查询：matches 任何 capabilities 包含 {@code capability} 的 Agent. 使用 PostgreSQL JSONB
   * {@code @>} 容器运算符判断 string element 是否存在于 array.
   *
   * @param capabilityJson JSON 数组字符串（如 {@code ["http_attack"]}），由 service 层 构造，避免在 SQL 中拼接用户输入
   */
  @Query(
      value =
          "SELECT a.* FROM agents a "
              + "WHERE a.agent_capabilities @> CAST(:capabilityJson AS jsonb)",
      nativeQuery = true)
  List<Agent> findByCapability(@Param("capabilityJson") String capabilityJson);

  @Query(
      value =
          "SELECT ag.agent_id, "
              + "ag.agent_executed_by_user, "
              + "ex.executor_type "
              + "FROM agents ag "
              + "Left JOIN executors ex ON ag.agent_executor = ex.executor_id "
              + "WHERE ag.agent_id IN :agentIds ;",
      nativeQuery = true)
  Set<RawAgent> rawAgentByIds(Set<String> agentIds);
}
