package io.veriguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.database.repository.AgentRepository;
import java.util.List;
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
class AgentServiceSelectByCapabilityTest {

  @Mock private AgentRepository agentRepository;

  @InjectMocks private AgentService service;

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  @Test
  @DisplayName("null capability → 空列表，不查 repository")
  void nullCapability_returnsEmptyAndSkipsRepo() {
    List<Agent> result = service.selectAgentsForCapability(null);

    assertThat(result).isEmpty();
    verify(agentRepository, never()).findByCapability(any());
  }

  @Test
  @DisplayName("空白字符串 capability → 空列表，不查 repository")
  void blankCapability_returnsEmptyAndSkipsRepo() {
    List<Agent> result = service.selectAgentsForCapability("   ");

    assertThat(result).isEmpty();
    verify(agentRepository, never()).findByCapability(any());
  }

  @Test
  @DisplayName("正常 capability → 构造 JSON 数组字符串调 repository")
  void normalCapability_buildsJsonArrayAndDelegates() {
    Agent a1 = agent("a1");
    when(agentRepository.findByCapability("[\"http_attack\"]")).thenReturn(List.of(a1));

    List<Agent> result = service.selectAgentsForCapability("http_attack");

    assertThat(result).containsExactly(a1);
    verify(agentRepository).findByCapability("[\"http_attack\"]");
  }

  @Test
  @DisplayName("多 Agent 命中 → 返回完整列表")
  void multipleAgentsMatch_returnsAllResults() {
    Agent a1 = agent("a1");
    Agent a2 = agent("a2");
    Agent a3 = agent("a3");
    when(agentRepository.findByCapability(any())).thenReturn(List.of(a1, a2, a3));

    List<Agent> result = service.selectAgentsForCapability("pcap_replay");

    assertThat(result).hasSize(3).containsExactly(a1, a2, a3);
  }

  @Test
  @DisplayName("0 命中 → 空列表（不抛错）")
  void noMatch_returnsEmpty() {
    when(agentRepository.findByCapability(any())).thenReturn(List.of());

    List<Agent> result = service.selectAgentsForCapability("nonexistent");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("含双引号的 capability 名 → JSON 转义安全")
  void capabilityWithQuote_buildsEscapedJsonArray() {
    when(agentRepository.findByCapability(any())).thenReturn(List.of());

    service.selectAgentsForCapability("evil\"name");

    verify(agentRepository).findByCapability("[\"evil\\\"name\"]");
  }

  @Test
  @DisplayName("repository 抛异常 → 异常透传，不静默吞")
  void repositoryThrows_propagates() {
    when(agentRepository.findByCapability(any()))
        .thenThrow(new RuntimeException("DB query failed"));

    assertThrows(RuntimeException.class, () -> service.selectAgentsForCapability("http_attack"));
  }

  @Test
  @DisplayName("正常 capability 名 → 不修改字符串大小写或前后空格")
  void capabilityCasePreserved() {
    when(agentRepository.findByCapability(any())).thenReturn(List.of());

    service.selectAgentsForCapability("HTTP_Attack");

    verify(agentRepository).findByCapability("[\"HTTP_Attack\"]");
  }
}
