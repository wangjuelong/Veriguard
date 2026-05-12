package io.veriguard.injectors.web_attack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.injectors.web_attack.WebAttackContract;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
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
class WebAttackDispatchServiceTest {

  @Mock private AgentService agentService;

  @InjectMocks private WebAttackDispatchService dispatchService;

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
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
        .thenReturn(List.of(agent("a1")));
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("a1");
  }

  @Test
  @DisplayName("selectAgent: 多个 Agent 匹配 → 返回第一个（确定性）")
  void select_multipleAgents() {
    when(agentService.selectAgentsForCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK))
        .thenReturn(List.of(agent("a1"), agent("a2"), agent("a3")));
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
}
