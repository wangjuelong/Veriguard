package io.veriguard.injectors.pcap_replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Execution;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.service.AttackChainNodeExpectationService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PcapReplayExecutorTest {

  @Mock private NodeExecutorContext context;
  @Mock private PcapReplayDispatchService dispatchService;
  @Mock private AttackChainNodeExpectationService attackChainNodeExpectationService;
  @Mock private Execution execution;
  @Mock private ExecutableNode injection;

  private PcapReplayExecutor newSpyExecutor(PcapReplayContent contentStub) throws Exception {
    PcapReplayExecutor real =
        new PcapReplayExecutor(context, dispatchService, attackChainNodeExpectationService);
    PcapReplayExecutor spied = spy(real);
    doReturn(contentStub).when(spied).convertContent(injection);
    return spied;
  }

  private PcapReplayContent validContent() {
    PcapReplayContent c = new PcapReplayContent();
    c.setPcapFileId("pcap-abc");
    c.setTargetInterface("eth0");
    c.setReplayMode("ORIGINAL");
    return c;
  }

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  @Test
  @DisplayName("process: validate 通过 + Agent 找到 → success trace + ExecutionProcess(false)")
  void process_dispatchSuccess() throws Exception {
    PcapReplayContent c = validContent();
    PcapReplayExecutor spied = newSpyExecutor(c);

    doNothing().when(dispatchService).validateContent(c);
    when(dispatchService.selectAgent()).thenReturn(Optional.of(agent("a1")));

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(dispatchService).validateContent(c);
    verify(dispatchService).selectAgent();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: 无可用 Agent → error trace + ExecutionProcess(false)")
  void process_noAgent() throws Exception {
    PcapReplayContent c = validContent();
    PcapReplayExecutor spied = newSpyExecutor(c);

    doNothing().when(dispatchService).validateContent(c);
    when(dispatchService.selectAgent()).thenReturn(Optional.empty());

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（缺 pcap_file_id）→ error trace")
  void process_validateFailure_missingPcap() throws Exception {
    PcapReplayContent c = new PcapReplayContent();
    c.setTargetInterface("eth0");
    c.setReplayMode("ORIGINAL");
    PcapReplayExecutor spied = newSpyExecutor(c);

    doThrow(new IllegalArgumentException("pcap_file_id is required"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（mode 非法 LIGHTSPEED）→ error trace")
  void process_validateFailure_invalidMode() throws Exception {
    PcapReplayContent c = validContent();
    c.setReplayMode("LIGHTSPEED");
    PcapReplayExecutor spied = newSpyExecutor(c);

    doThrow(new IllegalArgumentException("Invalid pcap_replay_mode: LIGHTSPEED"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }
}
