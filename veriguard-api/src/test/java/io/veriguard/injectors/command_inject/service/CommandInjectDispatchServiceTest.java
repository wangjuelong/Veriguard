package io.veriguard.injectors.command_inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Agent;
import io.veriguard.injectors.command_inject.CommandInjectContract;
import io.veriguard.injectors.command_inject.model.CommandInjectContent;
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

/** Unit tests for {@link CommandInjectDispatchService}. Task C.11. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandInjectDispatchServiceTest {

  @Mock private AgentService agentService;

  private AgentTaskQueueService taskQueue;
  private CommandInjectDispatchService dispatchService;

  @BeforeEach
  void setUp() {
    taskQueue = new AgentTaskQueueService();
    dispatchService = new CommandInjectDispatchService(agentService, taskQueue, new ObjectMapper());
  }

  private Agent agent(String id, String... capabilities) {
    Agent a = new Agent();
    a.setId(id);
    a.setCapabilities(List.of(capabilities));
    return a;
  }

  private CommandInjectContent validContent() {
    CommandInjectContent c = new CommandInjectContent();
    c.setExecutor("bash");
    c.setContent("id");
    c.setTimeoutSeconds(30);
    return c;
  }

  @Test
  @DisplayName("validateContent: 缺 executor → 抛")
  void validate_missingExecutor() {
    CommandInjectContent c = new CommandInjectContent();
    c.setContent("id");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command_executor");
  }

  @Test
  @DisplayName("validateContent: executor=fish (非法) → 抛")
  void validate_invalidExecutor() {
    CommandInjectContent c = new CommandInjectContent();
    c.setExecutor("fish");
    c.setContent("id");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command_executor")
        .hasMessageContaining("fish");
  }

  @Test
  @DisplayName("validateContent: 缺 content → 抛")
  void validate_missingContent() {
    CommandInjectContent c = new CommandInjectContent();
    c.setExecutor("bash");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command_content");
  }

  @Test
  @DisplayName("validateContent: timeout=0 或越界 → 抛")
  void validate_timeoutOutOfBounds() {
    CommandInjectContent c = new CommandInjectContent();
    c.setExecutor("bash");
    c.setContent("id");
    c.setTimeoutSeconds(0);
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeout");

    c.setTimeoutSeconds(3601);
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeout");
  }

  @Test
  @DisplayName("selectAgent: 单 agent 匹配")
  void select_singleAgentMatch() {
    when(agentService.selectAgentsForCapability(CommandInjectContract.CAPABILITY_COMMAND_INJECT))
        .thenReturn(List.of(agent("a1", "command_inject")));
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("a1");
  }

  @Test
  @DisplayName("selectAgent: 无 agent → empty")
  void select_noAgent() {
    when(agentService.selectAgentsForCapability(CommandInjectContract.CAPABILITY_COMMAND_INJECT))
        .thenReturn(List.of());
    assertThat(dispatchService.selectAgent()).isEmpty();
  }

  @Test
  @DisplayName("dispatch: task 进队列, future 注册成功")
  void dispatch_enqueuesTask() {
    Agent a = agent("a1", "command_inject");
    dispatchService.dispatch("task-001", a, validContent());

    List<AgentDtos.AgentTask> drained = taskQueue.drainTasks("a1");
    assertThat(drained).hasSize(1);
    assertThat(drained.get(0).taskId()).isEqualTo("task-001");
    assertThat(drained.get(0).capability()).isEqualTo("command_inject");
  }

  @Test
  @DisplayName("dispatch: agent POST result → future 完成")
  void dispatch_futureResolvesOnResultPost() throws Exception {
    Agent a = agent("a1", "command_inject");
    CompletableFuture<AgentTaskQueueService.ReceivedResult> future =
        dispatchService.dispatch("task-002", a, validContent());

    AgentDtos.ResultInput result =
        new AgentDtos.ResultInput(
            "SUCCESS", 0, "uid=0(root)", "", "2026-05-15T00:00:00Z", "2026-05-15T00:00:01Z", null);
    taskQueue.acceptResult("task-002", "a1", result);

    AgentTaskQueueService.ReceivedResult received = future.get(2, TimeUnit.SECONDS);
    assertThat(received.input().status()).isEqualTo("SUCCESS");
    assertThat(received.input().stdout()).isEqualTo("uid=0(root)");
  }

  @Test
  @DisplayName("dispatch: 无 result → future 超时")
  void dispatch_futureTimeoutWithoutResult() {
    Agent a = agent("a1", "command_inject");
    CompletableFuture<AgentTaskQueueService.ReceivedResult> future =
        dispatchService.dispatch("task-003", a, validContent());

    assertThatThrownBy(() -> future.get(50, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  @DisplayName("dispatch: agent 缺 command_inject 能力 → 抛")
  void dispatch_rejectsAgentWithoutCapability() {
    Agent a = agent("a1", "http_attack"); // 错的 capability
    assertThatThrownBy(() -> dispatchService.dispatch("task-004", a, validContent()))
        .isInstanceOf(CapabilityNotSupportedException.class)
        .hasMessageContaining("command_inject")
        .hasMessageContaining("a1");
  }
}
