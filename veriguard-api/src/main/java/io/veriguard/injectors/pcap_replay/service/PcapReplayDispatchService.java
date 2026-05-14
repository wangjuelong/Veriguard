package io.veriguard.injectors.pcap_replay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Agent;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
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
 * 选择有 {@code pcap_replay} 能力的协作主机 Agent，校验 pcap 回放 inject 内容，并向 Agent 队列派发任务.
 *
 * <p>C1-Platform-2 (Task C.12): 新增 {@link #dispatch(String, Agent, PcapReplayContent)} 方法 — 把任务推到
 * {@link AgentTaskQueueService} 的 mock 队列，并返回一个 {@link CompletableFuture}，待 Agent POST 回执时通过 {@link
 * AgentTaskQueueService#acceptResult} 触发 future 完成 (PcapReplayExecutor / Combination executor 调用并
 * await 结果).
 *
 * <p>tcpreplay 本身仍然由 Agent 侧实施 — 平台只做"投信箱 + 收信箱"模式。
 */
@Service
@Slf4j
public class PcapReplayDispatchService {

  /** Modes that REQUIRE a {@code pcap_replay_rate} value to be present. */
  private static final Set<String> MODES_REQUIRING_RATE = Set.of("MBPS", "PPS", "MULTIPLIER");

  /** All allowed {@code pcap_replay_mode} values (spec §6.2). */
  private static final Set<String> ALLOWED_MODES =
      Set.of("ORIGINAL", "MBPS", "PPS", "MULTIPLIER", "TOPSPEED");

  private final AgentService agentService;
  private final AgentTaskQueueService taskQueueService;
  private final ObjectMapper objectMapper;

  public PcapReplayDispatchService(
      AgentService agentService,
      AgentTaskQueueService taskQueueService,
      ObjectMapper objectMapper) {
    this.agentService = agentService;
    this.taskQueueService = taskQueueService;
    this.objectMapper = objectMapper;
  }

  /**
   * 校验 pcap_replay 内容必填字段 + mode 合法性 + mode/rate 一致性.
   *
   * @throws IllegalArgumentException 字段缺失、mode 不在允许集合，或 MBPS/PPS/MULTIPLIER 模式下缺 rate
   */
  public void validateContent(PcapReplayContent content) {
    if (content.getPcapFileId() == null || content.getPcapFileId().isBlank()) {
      throw new IllegalArgumentException("pcap_file_id is required");
    }
    if (content.getTargetInterface() == null || content.getTargetInterface().isBlank()) {
      throw new IllegalArgumentException("pcap_target_interface (interface) is required");
    }
    String mode = content.getReplayMode();
    if (mode == null || mode.isBlank()) {
      throw new IllegalArgumentException("pcap_replay_mode is required");
    }
    String upperMode = mode.toUpperCase();
    if (!ALLOWED_MODES.contains(upperMode)) {
      throw new IllegalArgumentException(
          "Invalid pcap_replay_mode: " + mode + " (allowed: " + ALLOWED_MODES + ")");
    }
    if (MODES_REQUIRING_RATE.contains(upperMode)
        && (content.getReplayRate() == null || content.getReplayRate() <= 0)) {
      throw new IllegalArgumentException(
          "pcap_replay_rate is required and must be > 0 for mode " + upperMode);
    }
  }

  /**
   * 选一个有 pcap_replay 能力的协作主机 Agent.
   *
   * <p>多个匹配 → 取首个（确定性，便于测试和复现）；后续可加负载策略.
   */
  public Optional<Agent> selectAgent() {
    List<Agent> candidates =
        agentService.selectAgentsForCapability(PcapReplayContract.CAPABILITY_PCAP_REPLAY);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0));
  }

  /**
   * 派发一个 pcap_replay 任务并返回结果 future.
   *
   * @throws CapabilityNotSupportedException Agent 未声明 {@code pcap_replay} 能力
   */
  public CompletableFuture<AgentTaskQueueService.ReceivedResult> dispatch(
      String taskId, Agent agent, PcapReplayContent content) {
    if (taskId == null || agent == null || content == null) {
      throw new IllegalArgumentException("taskId, agent, and content must not be null");
    }
    requireCapability(agent, PcapReplayContract.CAPABILITY_PCAP_REPLAY);

    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(content);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(
          "Failed to serialize PcapReplayContent to JSON for task " + taskId, ex);
    }

    AgentDtos.AgentTask task =
        new AgentDtos.AgentTask(
            taskId,
            PcapReplayContract.CAPABILITY_PCAP_REPLAY,
            PcapReplayContract.TYPE,
            payloadJson,
            List.of());

    log.info(
        "PcapReplayDispatchService.dispatch: task_id={}, agent={}, interface={}, mode={}",
        taskId,
        agent.getId(),
        content.getTargetInterface(),
        content.getReplayMode());
    taskQueueService.enqueue(agent.getId(), task);
    return taskQueueService.awaitResult(taskId);
  }

  /** Generate a fresh dispatch task_id (UUID). */
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
