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
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
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

/** Unit tests for {@link HttpInjectExecutor}. C1-Platform-2 / Task C.10. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpInjectExecutorTest {

  @Mock private WebAttackDispatchService dispatchService;

  /** Shortest possible timeout for tests — keep CI snappy. */
  private static final Duration TEST_TIMEOUT = Duration.ofMillis(500);

  private HttpInjectExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new HttpInjectExecutor(dispatchService, TEST_TIMEOUT);
  }

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  private CombinationInstance sqlInstance() {
    String payloadJson =
        "{\"web_request_method\":\"GET\",\"web_request_url\":\"https://t.example/?q=' OR 1=1 --\"}";
    return new CombinationInstance(
        "run-1",
        "sql_injection:dim-1",
        "sql_injection",
        "dim-1",
        "asset-1",
        payloadJson,
        payloadJson);
  }

  @Test
  void supports_all_eleven_web_attack_base_types() {
    String[] expected = {
      "sql_injection",
      "xss",
      "xxe",
      "ssrf",
      "ssti",
      "command_execution",
      "directory_traversal",
      "csrf",
      "weak_credential",
      "upload_bypass",
      "oversized_upload"
    };
    for (String base : expected) {
      assertThat(executor.supports(base)).as("base_type=%s should be supported", base).isTrue();
    }
    assertThat(HttpInjectExecutor.SUPPORTED_BASE_TYPES).hasSize(expected.length);
  }

  @Test
  void supports_unrelated_or_pcap_base_types_returns_false() {
    assertThat(executor.supports("pcap_replay")).isFalse();
    assertThat(executor.supports("rogue_router_advertisement")).isFalse();
    assertThat(executor.supports("unknown_type")).isFalse();
    assertThat(executor.supports("")).isFalse();
  }

  @Test
  void supports_null_returns_false_not_throws() {
    assertThat(executor.supports(null)).isFalse();
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
        new AgentDtos.ResultInput("SUCCESS", 0, "ok", "", "now", "now", null);
    AgentTaskQueueService.ReceivedResult received =
        new AgentTaskQueueService.ReceivedResult("task", "a1", input, Instant.now());
    when(dispatchService.dispatch(anyString(), any(Agent.class), any(WebAttackContent.class)))
        .thenReturn(CompletableFuture.completedFuture(received));

    AttackCombinationHitState state = executor.execute(sqlInstance());
    assertThat(state).isEqualTo(AttackCombinationHitState.hit);
    verify(dispatchService).validateContent(any(WebAttackContent.class));
  }

  @Test
  @DisplayName("execute: dispatch FAILED → hit_state=miss")
  void execute_dispatchFailedYieldsMiss() {
    Agent a = agent("a1");
    when(dispatchService.selectAgent()).thenReturn(Optional.of(a));

    AgentDtos.ResultInput input =
        new AgentDtos.ResultInput("FAILED", 1, "", "err", "now", "now", "blocked-by-waf");
    AgentTaskQueueService.ReceivedResult received =
        new AgentTaskQueueService.ReceivedResult("task", "a1", input, Instant.now());
    when(dispatchService.dispatch(anyString(), any(Agent.class), any(WebAttackContent.class)))
        .thenReturn(CompletableFuture.completedFuture(received));

    AttackCombinationHitState state = executor.execute(sqlInstance());
    assertThat(state).isEqualTo(AttackCombinationHitState.miss);
  }

  @Test
  @DisplayName("execute: future 超时 → hit_state=timeout")
  void execute_futureTimeoutYieldsTimeout() {
    Agent a = agent("a1");
    when(dispatchService.selectAgent()).thenReturn(Optional.of(a));
    // Returns a future that never completes.
    when(dispatchService.dispatch(anyString(), any(Agent.class), any(WebAttackContent.class)))
        .thenReturn(new CompletableFuture<>());

    AttackCombinationHitState state = executor.execute(sqlInstance());
    assertThat(state).isEqualTo(AttackCombinationHitState.timeout);
  }

  @Test
  @DisplayName("execute: 无 agent → hit_state=timeout (Caller 进一步分类)")
  void execute_noAgentYieldsTimeout() {
    when(dispatchService.selectAgent()).thenReturn(Optional.empty());

    AttackCombinationHitState state = executor.execute(sqlInstance());
    // No agent → can't differentiate hit/miss → INCONCLUSIVE == timeout in enum.
    assertThat(state).isEqualTo(AttackCombinationHitState.timeout);
  }
}
