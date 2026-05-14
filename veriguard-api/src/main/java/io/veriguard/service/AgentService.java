package io.veriguard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AgentRepository;
import io.veriguard.service.exception.CapabilityNotSupportedException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AgentService {

  @PersistenceContext private EntityManager entityManager;

  private final AgentRepository agentRepository;

  public Optional<Agent> getAgentForAnAsset(
      String assetId,
      String user,
      Agent.DEPLOYMENT_MODE deploymentMode,
      Agent.PRIVILEGE privilege,
      String executor) {
    return agentRepository.findByAssetExecutorUserDeploymentAndPrivilege(
        assetId, user, deploymentMode.name(), privilege.name(), executor);
  }

  public List<Agent> getAgentsForExecution() {
    return agentRepository.findForExecution();
  }

  public List<Agent> getAgentsByExecutorType(String executor) {
    return agentRepository.findByExecutorType(executor);
  }

  public Agent createOrUpdateAgent(@NotNull final Agent agent) {
    return this.agentRepository.save(agent);
  }

  @Transactional
  public List<Agent> saveAllAgents(List<Agent> agents) {
    List<Agent> agentsSaved = new ArrayList<>();
    // Improve perfs for save all
    for (int i = 0; i < agents.size(); i++) {
      agentsSaved.add(agentRepository.save(agents.get(i)));
      // Flush and clear the session every 50 (batch_size property) inserts
      if (i % 50 == 0) {
        entityManager.flush();
        entityManager.clear();
      }
    }
    return agentsSaved;
  }

  public void deleteAgent(@NotBlank final String agentId) {
    this.agentRepository.deleteByAgentId(agentId);
  }

  public List<Agent> findByExternalReference(String externalReference) {
    return agentRepository.findByExternalReference(externalReference);
  }

  public Tuple getAgentMetrics(Iterable<Executor> agentExecutors) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createQuery(Tuple.class);

    Root<Agent> root = cq.from(Agent.class);
    List<Selection<?>> selections = new ArrayList<>();

    selections.add(
        cb.count(cb.selectCase().when(cb.isNull(root.get("parent")), 1)).alias("total_agents"));
    selections.add(countAgentsByField(cb, root, "deploymentMode", "session", "session_agents"));
    selections.add(countAgentsByField(cb, root, "deploymentMode", "service", "service_agents"));
    selections.add(countAgentsByField(cb, root, "privilege", "standard", "user_agents"));
    selections.add(countAgentsByField(cb, root, "privilege", "admin", "admin_agents"));

    // Dynamically add COUNT for each Executor
    for (Executor executor : agentExecutors) {
      selections.add(
          countAgentsByField(cb, root, "executor", executor, "agent_" + executor.getType()));
    }

    cq.multiselect(selections);

    // Execute the query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    return query.getSingleResult();
  }

  private Selection<Long> countAgentsByField(
      CriteriaBuilder cb, Root<Agent> root, String field, Object value, String alias) {
    return cb.count(
            cb.selectCase()
                .when(cb.and(cb.isNull(root.get("parent")), cb.equal(root.get(field), value)), 1))
        .alias(alias);
  }

  private static final ObjectMapper CAPABILITY_JSON_MAPPER = new ObjectMapper();

  /**
   * 查询声明了指定 capability 的 Agent 列表（B-ii PR-A）.
   *
   * <p>null / 空 / 仅空白字符的 capability 返回空列表且不查询数据库（short-circuit）. 否则构造单元素 JSON 数组字符串（如 {@code
   * ["http_attack"]}）传入 {@link AgentRepository#findByCapability(String)}，通过 PostgreSQL JSONB
   * containment 运算符 {@code @>} 匹配数组中包含此元素的 Agent.
   *
   * @param capability capability 标签名（如 command_exec / http_attack / pcap_replay）
   * @return 命中的 Agent 列表（保持 repository 返回顺序）
   */
  public List<Agent> selectAgentsForCapability(String capability) {
    if (capability == null || capability.isBlank()) {
      return List.of();
    }
    try {
      String capabilityJson = CAPABILITY_JSON_MAPPER.writeValueAsString(List.of(capability));
      return agentRepository.findByCapability(capabilityJson);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize capability to JSON: " + capability, e);
    }
  }

  /**
   * 严格校验: 选一个声明了 {@code capability} 的 Agent（Task C.14）.
   *
   * <p>区别于 {@link #selectAgentsForCapability(String)} 返回列表（可能为空）, 本方法 fail-fast: 找不到匹配的 Agent 时抛
   * {@link CapabilityNotSupportedException}, 让 caller 立即得到具体能力的失败原因. 多匹配时取首个（确定性, 与 dispatch 服务对齐,
   * 后续可加负载均衡策略）.
   *
   * @param capability capability 标签（如 {@code http_attack} / {@code command_inject} / {@code
   *     pcap_replay}）
   * @return 命中的第一个 Agent
   * @throws CapabilityNotSupportedException 当没有任何在线 Agent 声明此能力
   * @throws IllegalArgumentException 当 {@code capability} 为 null / 空白
   */
  public Agent selectByCapability(String capability) {
    if (capability == null || capability.isBlank()) {
      throw new IllegalArgumentException("capability must not be blank");
    }
    List<Agent> candidates = selectAgentsForCapability(capability);
    if (candidates.isEmpty()) {
      throw new CapabilityNotSupportedException(
          capability,
          "No Agent currently declares capability '"
              + capability
              + "'; deploy an Agent that exposes this capability before dispatching"
              + " tasks that require it");
    }
    return candidates.get(0);
  }
}
