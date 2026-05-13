package io.veriguard.combination.executor;

import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真实 pcap 回放执行器 —— IPv6 安全验证系统 §3.6 ★2 PR D5.
 *
 * <p>承接 D2 的执行器路由抽象，针对协议级 / 流量回放类基础攻击派发到协作主机 Agent
 * 的 {@code tcpreplay} 能力（与 §2.3 B-ii PR-D / {@code PcapReplayContract} 同一上行通道）。
 *
 * <p>本 PR 范围内为 <b>骨架</b>：{@link #supports(String)} 覆盖典型 pcap 类基础攻击；
 * {@link #execute(CombinationInstance)} 抛 {@link UnsupportedOperationException} 表示
 * 真实 dispatch 通道尚未接通，由 caller 处理为 failed / retry。
 *
 * <p>注册策略：由 {@code veriguard.combination.pcap-replay.enabled=true} 显式开启
 * （默认关闭），与 HttpInjectExecutor 同模式，便于演示用 Stub 兜底。
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
    // TODO(PR D5+): 接通 PcapReplayDispatchService —— 真实接通后根据 agent 回执映射为
    // hit/miss/timeout（hit=IDS/IPS 阻断或告警、miss=流量被放行未告警、timeout=Agent 未在期内回执）。
    log.info(
        "PcapReplayExecutor dispatch placeholder: run={}, combination={}, base_type={},"
            + " bypass_dim={}, asset={}",
        instance.runId(),
        instance.combinationId(),
        instance.baseAttackType(),
        instance.bypassDimensionId(),
        instance.assetId());
    throw new UnsupportedOperationException(
        "PcapReplayExecutor skeleton: real PcapReplayDispatchService wiring pending (PR D5+);"
            + " base_type="
            + instance.baseAttackType());
  }
}
