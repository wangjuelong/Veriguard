package io.veriguard.injectors.pcap_replay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.service.AgentService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PcapReplayDispatchServiceTest {

  @Mock private AgentService agentService;

  @InjectMocks private PcapReplayDispatchService dispatchService;

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  private PcapReplayContent validContent() {
    PcapReplayContent c = new PcapReplayContent();
    c.setPcapFileId("pcap-abc");
    c.setTargetInterface("eth0");
    c.setReplayMode("ORIGINAL");
    return c;
  }

  @Test
  @DisplayName("validateContent: 缺 pcap_file_id → 抛 IllegalArgumentException")
  void validate_missingPcapFile() {
    PcapReplayContent c = new PcapReplayContent();
    c.setTargetInterface("eth0");
    c.setReplayMode("ORIGINAL");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pcap_file_id");
  }

  @Test
  @DisplayName("validateContent: 缺 target_interface → 抛 IllegalArgumentException")
  void validate_missingInterface() {
    PcapReplayContent c = new PcapReplayContent();
    c.setPcapFileId("pcap-abc");
    c.setReplayMode("ORIGINAL");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("interface");
  }

  @Test
  @DisplayName("validateContent: mode 非法（如 LIGHTSPEED）→ 抛 IllegalArgumentException")
  void validate_invalidMode() {
    PcapReplayContent c = validContent();
    c.setReplayMode("LIGHTSPEED");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mode");
  }

  @Test
  @DisplayName("validateContent: MBPS 模式但无 rate → 抛 IllegalArgumentException")
  void validate_mbpsMissingRate() {
    PcapReplayContent c = validContent();
    c.setReplayMode("MBPS");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rate");
  }

  @Test
  @DisplayName("selectAgent: 多个 Agent 匹配 → 返回第一个（确定性）")
  void select_multipleAgents() {
    when(agentService.selectAgentsForCapability(PcapReplayContract.CAPABILITY_PCAP_REPLAY))
        .thenReturn(List.of(agent("a1"), agent("a2")));
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("a1");
  }

  @Test
  @DisplayName("selectAgent: 无匹配 Agent → 返回 Optional.empty")
  void select_noAgent() {
    when(agentService.selectAgentsForCapability(PcapReplayContract.CAPABILITY_PCAP_REPLAY))
        .thenReturn(List.of());
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isEmpty();
  }
}
