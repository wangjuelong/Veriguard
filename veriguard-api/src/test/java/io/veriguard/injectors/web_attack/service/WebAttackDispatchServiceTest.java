package io.veriguard.injectors.web_attack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Agent;
import io.veriguard.injectors.web_attack.WebAttackContract;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
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
class WebAttackDispatchServiceTest {

  @Mock private AgentService agentService;

  private AgentTaskQueueService taskQueue;
  private WebAttackDispatchService dispatchService;

  @BeforeEach
  void setUp() {
    taskQueue = new AgentTaskQueueService();
    dispatchService = new WebAttackDispatchService(agentService, taskQueue, new ObjectMapper());
  }

  private Agent agent(String id, String... capabilities) {
    Agent a = new Agent();
    a.setId(id);
    a.setCapabilities(List.of(capabilities));
    return a;
  }

  private WebAttackContent simpleContent() {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("GET");
    c.setUrl("https://example.com/");
    return c;
  }

  @Test
  @DisplayName("validateContent: 缺 url → 抛 IllegalArgumentException")
  void validate_missingUrl() {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("GET");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url");
  }

  @Test
  @DisplayName("validateContent: 缺 method → 抛 IllegalArgumentException")
  void validate_missingMethod() {
    WebAttackContent c = new WebAttackContent();
    c.setUrl("https://example.com/");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("method");
  }

  @Test
  @DisplayName("validateContent: method 非法（如 BREW）→ 抛 IllegalArgumentException")
  void validate_invalidMethod() {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("BREW");
    c.setUrl("https://example.com/");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("method");
  }

  @Test
  @DisplayName("selectAgent: capability=http_attack 匹配到 1 个 → 返回该 Agent")
  void select_singleAgentMatch() {
    when(agentService.selectAgentsForCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK))
        .thenReturn(List.of(agent("a1", "http_attack")));
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("a1");
  }

  @Test
  @DisplayName("selectAgent: 多个 Agent 匹配 → 返回第一个（确定性）")
  void select_multipleAgents() {
    when(agentService.selectAgentsForCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK))
        .thenReturn(
            List.of(
                agent("a1", "http_attack"),
                agent("a2", "http_attack"),
                agent("a3", "http_attack")));
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("a1");
  }

  @Test
  @DisplayName("selectAgent: 无匹配 Agent → 返回 Optional.empty")
  void select_noAgent() {
    when(agentService.selectAgentsForCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK))
        .thenReturn(List.of());
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isEmpty();
  }

  // ---- Task C.9 dispatch tests ----

  @Test
  @DisplayName("dispatch: 任务进入 agent 队列且可在下次 drainTasks 取出")
  void dispatch_enqueuesTaskForAgent() {
    Agent a = agent("a1", "http_attack");
    String taskId = "task-001";
    dispatchService.dispatch(taskId, a, simpleContent());

    List<AgentDtos.AgentTask> drained = taskQueue.drainTasks("a1");
    assertThat(drained).hasSize(1);
    assertThat(drained.get(0).taskId()).isEqualTo("task-001");
    assertThat(drained.get(0).capability()).isEqualTo("http_attack");
  }

  @Test
  @DisplayName("dispatch: agent POST result → CompletableFuture 完成且携带正确 status")
  void dispatch_futureResolvesOnResultPost() throws Exception {
    Agent a = agent("a1", "http_attack");
    String taskId = "task-002";

    CompletableFuture<AgentTaskQueueService.ReceivedResult> future =
        dispatchService.dispatch(taskId, a, simpleContent());

    // Drain so the agent "saw" the task (not strictly required for future resolution).
    taskQueue.drainTasks("a1");

    AgentDtos.ResultInput result =
        new AgentDtos.ResultInput(
            "SUCCESS", 0, "ok", "", "2026-05-15T00:00:00Z", "2026-05-15T00:00:01Z", null);
    taskQueue.acceptResult(taskId, "a1", result);

    AgentTaskQueueService.ReceivedResult received = future.get(2, TimeUnit.SECONDS);
    assertThat(received.taskId()).isEqualTo("task-002");
    assertThat(received.input().status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("dispatch: 无 result POST → future 超时 (调用方负责 timeout)")
  void dispatch_futureNeverCompletesWithoutResult() {
    Agent a = agent("a1", "http_attack");
    CompletableFuture<AgentTaskQueueService.ReceivedResult> future =
        dispatchService.dispatch("task-003", a, simpleContent());

    assertThatThrownBy(() -> future.get(50, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  @Test
  @DisplayName("dispatch: Agent 缺 http_attack 能力 → 抛 CapabilityNotSupportedException")
  void dispatch_rejectsAgentWithoutCapability() {
    Agent a = agent("a1", "pcap_replay"); // 错的 capability
    assertThatThrownBy(() -> dispatchService.dispatch("task-004", a, simpleContent()))
        .isInstanceOf(CapabilityNotSupportedException.class)
        .hasMessageContaining("http_attack")
        .hasMessageContaining("a1");
  }
}
