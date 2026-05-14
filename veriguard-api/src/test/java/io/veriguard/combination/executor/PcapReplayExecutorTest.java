package io.veriguard.combination.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.Agent;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
import io.veriguard.rest.agent.AgentDtos;
import io.veriguard.rest.agent.AgentTaskQueueService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link PcapReplayExecutor}. C1-Platform-2 / Task C.12. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PcapReplayExecutorTest {

  @Mock private PcapReplayDispatchService dispatchService;

  /** Shortest possible timeout for tests — keep CI snappy. */
  private static final Duration TEST_TIMEOUT = Duration.ofMillis(500);

  private PcapReplayExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new PcapReplayExecutor(dispatchService, TEST_TIMEOUT);
  }

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  private CombinationInstance pcapInstance() {
    String payloadJson =
        "{\"pcap_file_id\":\"pcap-001\","
            + "\"pcap_target_interface\":\"eth0\","
            + "\"pcap_replay_mode\":\"ORIGINAL\"}";
    return new CombinationInstance(
        "run-1", "pcap_replay:dim-7", "pcap_replay", "dim-7", "asset-9", payloadJson, payloadJson);
  }

  @Test
  void supports_known_pcap_base_types() {
    assertThat(executor.supports("pcap_replay")).isTrue();
    assertThat(executor.supports("ipv6_extension_header_abuse")).isTrue();
    assertThat(executor.supports("ipv6_fragmentation_evasion")).isTrue();
    assertThat(executor.supports("rogue_router_advertisement")).isTrue();
    assertThat(executor.supports("neighbor_discovery_spoof")).isTrue();
    assertThat(executor.supports("dns_tunneling")).isTrue();
  }

  @Test
  void supports_web_or_unrelated_base_types_returns_false() {
    assertThat(executor.supports("sql_injection")).isFalse();
    assertThat(executor.supports("xss")).isFalse();
    assertThat(executor.supports(null)).isFalse();
    assertThat(executor.supports("")).isFalse();
  }

  @Test
  void execute_null_instance_throws_illegal_argument() {
    assertThatThrownBy(() -> executor.execute(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("execute: dispatch SUCCESS → hit_state=hit")
  void execute_dispatchSuccessYieldsHit() {
    Agent a = agent("a1");
    when(dispatchService.selectAgent()).thenReturn(Optional.of(a));

    AgentDtos.ResultInput input =
        new AgentDtos.ResultInput("SUCCESS", 0, "replayed-N-packets", "", "now", "now", null);
    AgentTaskQueueService.ReceivedResult received =
        new AgentTaskQueueService.ReceivedResult("task", "a1", input, Instant.now());
    when(dispatchService.dispatch(anyString(), any(Agent.class), any(PcapReplayContent.class)))
        .thenReturn(CompletableFuture.completedFuture(received));

    AttackCombinationHitState state = executor.execute(pcapInstance());
    assertThat(state).isEqualTo(AttackCombinationHitState.hit);
    verify(dispatchService).validateContent(any(PcapReplayContent.class));
  }

  @Test
  @DisplayName("execute: dispatch FAILED → hit_state=miss")
  void execute_dispatchFailedYieldsMiss() {
    Agent a = agent("a1");
    when(dispatchService.selectAgent()).thenReturn(Optional.of(a));

    AgentDtos.ResultInput input =
        new AgentDtos.ResultInput("FAILED", 1, "", "ids-blocked", "now", "now", "ids-blocked");
    AgentTaskQueueService.ReceivedResult received =
        new AgentTaskQueueService.ReceivedResult("task", "a1", input, Instant.now());
    when(dispatchService.dispatch(anyString(), any(Agent.class), any(PcapReplayContent.class)))
        .thenReturn(CompletableFuture.completedFuture(received));

    AttackCombinationHitState state = executor.execute(pcapInstance());
    assertThat(state).isEqualTo(AttackCombinationHitState.miss);
  }

  @Test
  @DisplayName("execute: future 超时 → hit_state=timeout")
  void execute_futureTimeoutYieldsTimeout() {
    Agent a = agent("a1");
    when(dispatchService.selectAgent()).thenReturn(Optional.of(a));
    when(dispatchService.dispatch(anyString(), any(Agent.class), any(PcapReplayContent.class)))
        .thenReturn(new CompletableFuture<>());

    AttackCombinationHitState state = executor.execute(pcapInstance());
    assertThat(state).isEqualTo(AttackCombinationHitState.timeout);
  }

  @Test
  @DisplayName("execute: 无 agent → hit_state=timeout")
  void execute_noAgentYieldsTimeout() {
    when(dispatchService.selectAgent()).thenReturn(Optional.empty());

    AttackCombinationHitState state = executor.execute(pcapInstance());
    assertThat(state).isEqualTo(AttackCombinationHitState.timeout);
  }
}
