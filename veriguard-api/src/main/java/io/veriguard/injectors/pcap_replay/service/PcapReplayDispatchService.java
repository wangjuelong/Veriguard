package io.veriguard.injectors.pcap_replay.service;

import io.veriguard.database.model.Agent;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.service.AgentService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选择有 {@code pcap_replay} 能力的协作主机 Agent，并校验 pcap 回放 inject 内容.
 *
 * <p>本 PR 不发起真实 tcpreplay；agent 客户端独立项目落地后由 agent 侧完成 tcpreplay 执行 + 结果回填.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PcapReplayDispatchService {

  /** Modes that REQUIRE a {@code pcap_replay_rate} value to be present. */
  private static final Set<String> MODES_REQUIRING_RATE = Set.of("MBPS", "PPS", "MULTIPLIER");

  /** All allowed {@code pcap_replay_mode} values (spec §6.2). */
  private static final Set<String> ALLOWED_MODES =
      Set.of("ORIGINAL", "MBPS", "PPS", "MULTIPLIER", "TOPSPEED");

  private final AgentService agentService;

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
}
