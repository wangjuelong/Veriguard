package io.veriguard.injectors.command_inject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Agent;
import io.veriguard.injectors.command_inject.CommandInjectContract;
import io.veriguard.injectors.command_inject.model.CommandInjectContent;
import io.veriguard.rest.agent.AgentDtos;
import io.veriguard.rest.agent.AgentTaskQueueService;
import io.veriguard.service.AgentService;
import io.veriguard.service.exception.CapabilityNotSupportedException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选择有 {@code command_inject} 能力的协作主机 Agent，校验 Command inject 内容，并向 Agent 队列派发任务 (Task C.11).
 *
 * <p>Wire 形态：payload_type = {@code "Command"} (与 {@link
 * io.veriguard.database.model.PayloadType#COMMAND} 一致, PascalCase JSON / UPPER_SNAKE_CASE Java)。
 *
 * <p>HTTP 流量本身仍然由 Agent 侧实施 — 平台只做"投信箱 + 收信箱"：把 task 推到 mock 队列， 注册 result future，Agent POST result
 * 时通过 {@link AgentTaskQueueService#acceptResult} 触发 future 完成。
 */
@Service
@Slf4j
public class CommandInjectDispatchService {

  /** Wire-form payload type tag (PascalCase per {@code PayloadType.COMMAND.key}). */
  public static final String PAYLOAD_TYPE_COMMAND = "Command";

  private final AgentService agentService;
  private final AgentTaskQueueService taskQueueService;
  private final ObjectMapper objectMapper;

  public CommandInjectDispatchService(
      AgentService agentService,
      AgentTaskQueueService taskQueueService,
      ObjectMapper objectMapper) {
    this.agentService = agentService;
    this.taskQueueService = taskQueueService;
    this.objectMapper = objectMapper;
  }

  /**
   * 校验 command_inject 内容必填字段 + executor 合法性 + timeout 范围.
   *
   * @throws IllegalArgumentException 字段缺失或 executor 不在允许集合 / timeout 越界
   */
  public void validateContent(CommandInjectContent content) {
    if (content.getExecutor() == null || content.getExecutor().isBlank()) {
      throw new IllegalArgumentException("command_executor is required");
    }
    String execLower = content.getExecutor().toLowerCase(Locale.ROOT);
    if (!CommandInjectContract.ALLOWED_EXECUTORS.contains(execLower)) {
      throw new IllegalArgumentException(
          "Invalid command_executor: "
              + content.getExecutor()
              + " (allowed: "
              + CommandInjectContract.ALLOWED_EXECUTORS
              + ")");
    }
    if (content.getContent() == null || content.getContent().isBlank()) {
      throw new IllegalArgumentException("command_content is required");
    }
    if (content.getTimeoutSeconds() <= 0 || content.getTimeoutSeconds() > 3600) {
      throw new IllegalArgumentException(
          "command_timeout_seconds must be in (0, 3600], got " + content.getTimeoutSeconds());
    }
  }

  /**
   * 选一个有 command_inject 能力的协作主机 Agent.
   *
   * <p>多个匹配 → 取首个（确定性，便于测试和复现）；后续可加负载策略.
   */
  public Optional<Agent> selectAgent() {
    List<Agent> candidates =
        agentService.selectAgentsForCapability(CommandInjectContract.CAPABILITY_COMMAND_INJECT);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0));
  }

  /**
   * 派发一个 command_inject 任务并返回结果 future.
   *
   * @throws CapabilityNotSupportedException Agent 未声明 {@code command_inject} 能力
   */
  public CompletableFuture<AgentTaskQueueService.ReceivedResult> dispatch(
      String taskId, Agent agent, CommandInjectContent content) {
    if (taskId == null || agent == null || content == null) {
      throw new IllegalArgumentException("taskId, agent, and content must not be null");
    }
    requireCapability(agent, CommandInjectContract.CAPABILITY_COMMAND_INJECT);

    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(content);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(
          "Failed to serialize CommandInjectContent to JSON for task " + taskId, ex);
    }

    AgentDtos.AgentTask task =
        new AgentDtos.AgentTask(
            taskId,
            CommandInjectContract.CAPABILITY_COMMAND_INJECT,
            CommandInjectContract.TYPE,
            payloadJson,
            List.of());

    log.info(
        "CommandInjectDispatchService.dispatch: task_id={}, agent={}, executor={}",
        taskId,
        agent.getId(),
        content.getExecutor());
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
