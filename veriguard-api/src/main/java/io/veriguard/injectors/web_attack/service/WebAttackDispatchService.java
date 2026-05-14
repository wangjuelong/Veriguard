package io.veriguard.injectors.web_attack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Agent;
import io.veriguard.injectors.web_attack.WebAttackContract;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.rest.agent.AgentDtos;
import io.veriguard.rest.agent.AgentTaskQueueService;
import io.veriguard.service.AgentService;
import io.veriguard.service.exception.CapabilityNotSupportedException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选择有 {@code http_attack} 能力的协作主机 Agent，校验 Web 攻击包 inject 内容，并向 Agent 队列派发任务.
 *
 * <p>C1-Platform-2 (Task C.9): 新增 {@link #dispatch(String, Agent, WebAttackContent)} 方法 — 把任务推到
 * {@link AgentTaskQueueService} 的 mock 队列，并返回一个 {@link CompletableFuture}，待 Agent POST 回执时通过 {@link
 * AgentTaskQueueService#acceptResult} 触发 future 完成 (HttpInjectExecutor / Combination executor 调用并
 * await 结果).
 *
 * <p>HTTP 流量本身仍然由 Agent 侧实施（{@code wangjuelong/veriguard-agent} 仓 C1-Agent-2 Mode A transport +
 * capabilities 子项目），平台不直接发 HTTP 请求；这里的 dispatch 等价于"投信箱 + 收信箱"模式。
 */
@Service
@Slf4j
public class WebAttackDispatchService {

  private static final Set<String> ALLOWED_METHODS =
      Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

  private final AgentService agentService;
  private final AgentTaskQueueService taskQueueService;
  private final ObjectMapper objectMapper;

  public WebAttackDispatchService(
      AgentService agentService,
      AgentTaskQueueService taskQueueService,
      ObjectMapper objectMapper) {
    this.agentService = agentService;
    this.taskQueueService = taskQueueService;
    this.objectMapper = objectMapper;
  }

  /**
   * 校验 web_attack 内容必填字段 + method 合法性.
   *
   * @throws IllegalArgumentException 字段缺失或 method 不在允许集合
   */
  public void validateContent(WebAttackContent content) {
    if (content.getMethod() == null || content.getMethod().isBlank()) {
      throw new IllegalArgumentException("web_request_method is required");
    }
    if (!ALLOWED_METHODS.contains(content.getMethod().toUpperCase())) {
      throw new IllegalArgumentException(
          "Invalid web_request_method: "
              + content.getMethod()
              + " (allowed: "
              + ALLOWED_METHODS
              + ")");
    }
    if (content.getUrl() == null || content.getUrl().isBlank()) {
      throw new IllegalArgumentException("web_request_url is required");
    }
  }

  /**
   * 选一个有 http_attack 能力的协作主机 Agent.
   *
   * <p>多个匹配 → 取首个（确定性，便于测试和复现）；后续可加负载策略.
   */
  public Optional<Agent> selectAgent() {
    List<Agent> candidates =
        agentService.selectAgentsForCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0));
  }

  /**
   * 向 Agent 队列派发一个 web_attack 任务并返回结果 future.
   *
   * <p>调用流程：
   *
   * <ol>
   *   <li>严格校验 {@code agent.capabilities ∋ "http_attack"}，否则抛 {@link
   *       CapabilityNotSupportedException}
   *   <li>序列化 {@code content} 为 payload JSON
   *   <li>构造 task_id（如果调用方未传一个 — 使用 UUID）
   *   <li>{@link AgentTaskQueueService#enqueue} 写入 mock 队列
   *   <li>{@link AgentTaskQueueService#awaitResult} 注册结果 future
   * </ol>
   *
   * <p>调用方拿到 future 后用 {@code .get(timeout, unit)} 阻塞等待 Agent 回执；超时由调用方决定（{@code
   * HttpInjectExecutor} 默认 30s）。
   *
   * @param taskId 任务 id（如 execution id 或 combination id），用作 result 通道 key
   * @param agent 已通过 {@link #selectAgent} 选定的协作主机 Agent
   * @param content 已 {@link #validateContent} 通过的内容
   * @return future — 完成时携带 Agent 上报的 {@link AgentTaskQueueService.ReceivedResult}
   * @throws CapabilityNotSupportedException 当 Agent 未声明 {@code http_attack} 能力
   * @throws IllegalArgumentException 当 taskId / agent / content 为 null
   */
  public CompletableFuture<AgentTaskQueueService.ReceivedResult> dispatch(
      String taskId, Agent agent, WebAttackContent content) {
    if (taskId == null || agent == null || content == null) {
      throw new IllegalArgumentException("taskId, agent, and content must not be null");
    }
    requireCapability(agent, WebAttackContract.CAPABILITY_HTTP_ATTACK);

    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(content);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(
          "Failed to serialize WebAttackContent to JSON for task " + taskId, ex);
    }

    AgentDtos.AgentTask task =
        new AgentDtos.AgentTask(
            taskId,
            WebAttackContract.CAPABILITY_HTTP_ATTACK,
            WebAttackContract.TYPE,
            payloadJson,
            List.of());

    log.info(
        "WebAttackDispatchService.dispatch: task_id={}, agent={}, method={} {}",
        taskId,
        agent.getId(),
        content.getMethod(),
        content.getUrl());
    taskQueueService.enqueue(agent.getId(), task);
    return taskQueueService.awaitResult(taskId);
  }

  /** Generate a fresh dispatch task_id (UUID). Use when the caller has no natural id. */
  public String newTaskId() {
    return UUID.randomUUID().toString();
  }

  private static void requireCapability(Agent agent, String capability) {
    List<String> caps = agent.getCapabilities();
    if (caps == null || !caps.contains(capability)) {
      throw new CapabilityNotSupportedException(
          capability,
          "Agent "
              + agent.getId()
              + " does not declare capability '"
              + capability
              + "' (has="
              + caps
              + ")");
    }
  }
}
