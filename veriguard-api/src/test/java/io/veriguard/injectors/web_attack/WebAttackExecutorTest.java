package io.veriguard.injectors.web_attack;

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
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
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
class WebAttackExecutorTest {

  @Mock private NodeExecutorContext context;
  @Mock private WebAttackDispatchService dispatchService;
  @Mock private AttackChainNodeExpectationService attackChainNodeExpectationService;
  @Mock private Execution execution;
  @Mock private ExecutableNode injection;

  private WebAttackExecutor newSpyExecutor(WebAttackContent contentStub) throws Exception {
    WebAttackExecutor real =
        new WebAttackExecutor(context, dispatchService, attackChainNodeExpectationService);
    WebAttackExecutor spied = spy(real);
    doReturn(contentStub).when(spied).convertContent(injection);
    return spied;
  }

  private WebAttackContent validContent() {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("GET");
    c.setUrl("https://example.com/");
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
    WebAttackContent c = validContent();
    WebAttackExecutor spied = newSpyExecutor(c);

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
    WebAttackContent c = validContent();
    WebAttackExecutor spied = newSpyExecutor(c);

    doNothing().when(dispatchService).validateContent(c);
    when(dispatchService.selectAgent()).thenReturn(Optional.empty());

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（缺 url）→ error trace")
  void process_validateFailure_missingUrl() throws Exception {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("GET");
    WebAttackExecutor spied = newSpyExecutor(c);

    doThrow(new IllegalArgumentException("web_request_url is required"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（method 非法 BREW）→ error trace")
  void process_validateFailure_invalidMethod() throws Exception {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("BREW");
    c.setUrl("https://example.com/");
    WebAttackExecutor spied = newSpyExecutor(c);

    doThrow(new IllegalArgumentException("Invalid web_request_method: BREW"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }
}
