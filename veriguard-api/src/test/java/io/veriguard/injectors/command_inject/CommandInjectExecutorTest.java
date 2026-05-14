package io.veriguard.injectors.command_inject;

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
import io.veriguard.injectors.command_inject.model.CommandInjectContent;
import io.veriguard.injectors.command_inject.service.CommandInjectDispatchService;
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

/** Unit tests for {@link CommandInjectExecutor}. Task C.11. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandInjectExecutorTest {

  @Mock private NodeExecutorContext context;
  @Mock private CommandInjectDispatchService dispatchService;
  @Mock private AttackChainNodeExpectationService attackChainNodeExpectationService;
  @Mock private Execution execution;
  @Mock private ExecutableNode injection;

  private CommandInjectExecutor newSpyExecutor(CommandInjectContent contentStub) throws Exception {
    CommandInjectExecutor real =
        new CommandInjectExecutor(context, dispatchService, attackChainNodeExpectationService);
    CommandInjectExecutor spied = spy(real);
    doReturn(contentStub).when(spied).convertContent(injection);
    return spied;
  }

  private CommandInjectContent validContent() {
    CommandInjectContent c = new CommandInjectContent();
    c.setExecutor("bash");
    c.setContent("id");
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
    CommandInjectContent c = validContent();
    CommandInjectExecutor spied = newSpyExecutor(c);

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
    CommandInjectContent c = validContent();
    CommandInjectExecutor spied = newSpyExecutor(c);

    doNothing().when(dispatchService).validateContent(c);
    when(dispatchService.selectAgent()).thenReturn(Optional.empty());

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（缺 content）→ error trace")
  void process_validateFailure_missingContent() throws Exception {
    CommandInjectContent c = new CommandInjectContent();
    c.setExecutor("bash");
    CommandInjectExecutor spied = newSpyExecutor(c);

    doThrow(new IllegalArgumentException("command_content is required"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（executor=fish）→ error trace")
  void process_validateFailure_invalidExecutor() throws Exception {
    CommandInjectContent c = new CommandInjectContent();
    c.setExecutor("fish");
    c.setContent("id");
    CommandInjectExecutor spied = newSpyExecutor(c);

    doThrow(new IllegalArgumentException("Invalid command_executor: fish"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }
}
