package io.veriguard.injectors.pcap_replay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PcapReplayDispatchServiceTest {

  @Mock private AgentService agentService;

  private AgentTaskQueueService taskQueue;
  private PcapReplayDispatchService dispatchService;

  @BeforeEach
  void setUp() {
    taskQueue = new AgentTaskQueueService();
    dispatchService = new PcapReplayDispatchService(agentService, taskQueue, new ObjectMapper());
  }

  private Agent agent(String id, String... capabilities) {
    Agent a = new Agent();
    a.setId(id);
    a.setCapabilities(List.of(capabilities));
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
        .thenReturn(List.of(agent("a1", "pcap_replay"), agent("a2", "pcap_replay")));
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

  // ---- Task C.12 dispatch tests ----

  @Test
  @DisplayName("dispatch: 任务进入 agent 队列且可在下次 drainTasks 取出")
  void dispatch_enqueuesTaskForAgent() {
    Agent a = agent("a1", "pcap_replay");
    dispatchService.dispatch("task-001", a, validContent());

    List<AgentDtos.AgentTask> drained = taskQueue.drainTasks("a1");
    assertThat(drained).hasSize(1);
    assertThat(drained.get(0).taskId()).isEqualTo("task-001");
    assertThat(drained.get(0).capability()).isEqualTo("pcap_replay");
  }

  @Test
  @DisplayName("dispatch: agent POST result → CompletableFuture 完成")
  void dispatch_futureResolvesOnResultPost() throws Exception {
    Agent a = agent("a1", "pcap_replay");
    CompletableFuture<AgentTaskQueueService.ReceivedResult> future =
        dispatchService.dispatch("task-002", a, validContent());

    AgentDtos.ResultInput result =
        new AgentDtos.ResultInput(
            "SUCCESS", 0, "replayed", "", "2026-05-15T00:00:00Z", "2026-05-15T00:00:01Z", null);
    taskQueue.acceptResult("task-002", "a1", result);

    AgentTaskQueueService.ReceivedResult received = future.get(2, TimeUnit.SECONDS);
    assertThat(received.taskId()).isEqualTo("task-002");
    assertThat(received.input().status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("dispatch: 无 result → future 超时")
  void dispatch_futureTimeoutWithoutResult() {
    Agent a = agent("a1", "pcap_replay");
    CompletableFuture<AgentTaskQueueService.ReceivedResult> future =
        dispatchService.dispatch("task-003", a, validContent());

    assertThatThrownBy(() -> future.get(50, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  @DisplayName("dispatch: Agent 缺 pcap_replay 能力 → 抛 CapabilityNotSupportedException")
  void dispatch_rejectsAgentWithoutCapability() {
    Agent a = agent("a1", "http_attack"); // wrong capability
    assertThatThrownBy(() -> dispatchService.dispatch("task-004", a, validContent()))
        .isInstanceOf(CapabilityNotSupportedException.class)
        .hasMessageContaining("pcap_replay")
        .hasMessageContaining("a1");
  }
}
