package io.veriguard.combination.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.Agent;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
import io.veriguard.rest.agent.AgentTaskQueueService;
import io.veriguard.service.exception.CapabilityNotSupportedException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真实 pcap 回放执行器 —— IPv6 安全验证系统 §3.6 ★2 PR D5 / C1-Platform-2 Task C.12.
 *
 * <p>承接 D2 的执行器路由抽象，针对协议级 / 流量回放类基础攻击派发到协作主机 Agent 的 {@code pcap_replay} 能力（与 §2.3 B-ii PR-D /
 * {@code PcapReplayContract} 同一上行通道）。
 *
 * <p>C1-Platform-2 接通流程: 与 {@link HttpInjectExecutor} 同模式 (parse → validate → selectAgent →
 * dispatch.get(timeout) → status 映射 hit/miss/timeout)。
 *
 * <p>注册策略：由 {@code veriguard.combination.pcap-replay.enabled=true} 显式开启（默认关闭），与 HttpInjectExecutor
 * 同模式。
 */
@Component
@ConditionalOnProperty(
    prefix = "veriguard.combination.pcap-replay",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class PcapReplayExecutor implements CombinationExecutor {

  private static final Logger log = LoggerFactory.getLogger(PcapReplayExecutor.class);

  /** 典型 pcap 类基础攻击（协议层 / 流量回放）. */
  static final Set<String> SUPPORTED_BASE_TYPES =
      Set.of(
          "pcap_replay",
          "ipv6_extension_header_abuse",
          "ipv6_fragmentation_evasion",
          "rogue_router_advertisement",
          "neighbor_discovery_spoof",
          "udp_amplification",
          "tcp_session_hijack",
          "dns_tunneling",
          "icmp_redirect");

  /** 单实例 ObjectMapper — 无状态线程安全; 仅做 payload JSON 反序列化. */
  private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper();

  private final PcapReplayDispatchService dispatchService;
  private final Duration awaitTimeout;

  /** Spring 注入构造器 (默认 60s 超时 — pcap 回放比 HTTP 慢一些). */
  public PcapReplayExecutor(
      PcapReplayDispatchService dispatchService,
      @Value("${veriguard.combination.pcap-replay.timeout-seconds:60}") long awaitTimeoutSeconds) {
    this(dispatchService, Duration.ofSeconds(awaitTimeoutSeconds));
  }

  /** Test-facing constructor — explicit timeout. */
  public PcapReplayExecutor(PcapReplayDispatchService dispatchService, Duration awaitTimeout) {
    this.dispatchService = dispatchService;
    this.awaitTimeout = awaitTimeout;
  }

  @Override
  public boolean supports(String baseAttackType) {
    if (baseAttackType == null) {
      return false;
    }
    return SUPPORTED_BASE_TYPES.contains(baseAttackType);
  }

  @Override
  public AttackCombinationHitState execute(CombinationInstance instance) {
    if (instance == null) {
      throw new IllegalArgumentException("instance must not be null");
    }

    // 1) Parse payload as PcapReplayContent.
    PcapReplayContent content;
    try {
      content = PAYLOAD_MAPPER.readValue(instance.transformedPayload(), PcapReplayContent.class);
    } catch (JsonProcessingException ex) {
      log.error(
          "PcapReplayExecutor: failed to parse payload for run={}, combination={}, base_type={}",
          instance.runId(),
          instance.combinationId(),
          instance.baseAttackType(),
          ex);
      throw new IllegalStateException(
          "Invalid pcap_replay payload JSON for combination " + instance.combinationId(), ex);
    }

    // 2) Validate content.
    dispatchService.validateContent(content);

    // 3) Select agent with pcap_replay capability.
    Optional<Agent> agentOpt = dispatchService.selectAgent();
    if (agentOpt.isEmpty()) {
      log.warn(
          "PcapReplayExecutor: no Agent with pcap_replay capability for combination {} —"
              + " marking INCONCLUSIVE (timeout)",
          instance.combinationId());
      return AttackCombinationHitState.timeout;
    }

    // 4) Dispatch + await with timeout.
    String taskId = instance.runId() + ":" + instance.combinationId();
    try {
      AgentTaskQueueService.ReceivedResult received =
          dispatchService
              .dispatch(taskId, agentOpt.get(), content)
              .get(awaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
      return mapStatusToHitState(received.input().status());
    } catch (TimeoutException ex) {
      log.info(
          "PcapReplayExecutor: dispatch timeout after {}ms for combination {} — INCONCLUSIVE",
          awaitTimeout.toMillis(),
          instance.combinationId());
      return AttackCombinationHitState.timeout;
    } catch (CapabilityNotSupportedException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while awaiting dispatch result", ex);
    } catch (ExecutionException ex) {
      throw new RuntimeException(
          "Dispatch future failed for combination " + instance.combinationId(), ex.getCause());
    }
  }

  /**
   * Map agent-reported task status to combination hit_state. Aligns with {@link HttpInjectExecutor}
   * — SUCCESS → hit, FAILED → miss, other / null → timeout.
   */
  private AttackCombinationHitState mapStatusToHitState(String status) {
    if (status == null) {
      return AttackCombinationHitState.timeout;
    }
    return switch (status.toUpperCase()) {
      case "SUCCESS" -> AttackCombinationHitState.hit;
      case "FAILED" -> AttackCombinationHitState.miss;
      default -> AttackCombinationHitState.timeout;
    };
  }
}
