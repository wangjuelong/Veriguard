package io.veriguard.executors.sentinelone.service;

import static io.veriguard.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.veriguard.executors.sentinelone.model.SentinelOneAction;
import io.veriguard.service.AgentService;
import io.veriguard.utils.fixtures.AgentFixture;
import io.veriguard.utils.fixtures.EndpointFixture;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SentinelOneGarbageCollectorServiceTest {

  @Mock private AgentService agentService;
  @Mock private SentinelOneExecutorContextService sentinelOneExecutorContextService;
  @Mock SentinelOneExecutorConfig config;

  @InjectMocks private SentinelOneGarbageCollectorService sentinelOneGarbageCollectorService;

  @Test
  void test_run_garbageCollector_withSentinelOneAgents() {
    // Init datas
    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setAsset(EndpointFixture.createEndpoint());
    when(agentService.getAgentsByExecutorType(SENTINELONE_EXECUTOR_TYPE))
        .thenReturn(List.of(agent));
    when(config.getWindowsScriptId()).thenReturn("test script");
    // Run method to test
    sentinelOneGarbageCollectorService.run();
    // Asserts
    ArgumentCaptor<List<SentinelOneAction>> actionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(sentinelOneExecutorContextService).executeActions(actionsCaptor.capture());
    assertEquals(1, actionsCaptor.getValue().size());
    SentinelOneAction sentinelOneAction = actionsCaptor.getValue().get(0);
    assertEquals("test script", sentinelOneAction.getScriptId());
    assertEquals(
        "RwBlAHQALQBDAGgAaQBsAGQASQB0AGUAbQAgAC0AUABhAHQAaAAgACIAQwA6AFwAUAByAG8AZwByAGEAbQAgAEYAaQBsAGUAcwAgACgAeAA4ADYAKQBcAEYAaQBsAGkAZwByAGEAbgBcAE8AQQBFAFYAIABBAGcAZQBuAHQAXABwAGEAeQBsAG8AYQBkAHMAIgAsACIAQwA6AFwAUAByAG8AZwByAGEAbQAgAEYAaQBsAGUAcwAgACgAeAA4ADYAKQBcAEYAaQBsAGkAZwByAGEAbgBcAE8AQQBFAFYAIABBAGcAZQBuAHQAXAByAHUAbgB0AGkAbQBlAHMAIgAgAC0ARABpAHIAZQBjAHQAbwByAHkAIAAtAFIAZQBjAHUAcgBzAGUAIAB8ACAAVwBoAGUAcgBlAC0ATwBiAGoAZQBjAHQAIAB7ACQAXwAuAEMAcgBlAGEAdABpAG8AbgBUAGkAbQBlACAALQBsAHQAIAAoAEcAZQB0AC0ARABhAHQAZQApAC4AQQBkAGQASABvAHUAcgBzACgALQAyADQAKQB9ACAAfAAgAFIAZQBtAG8AdgBlAC0ASQB0AGUAbQAgAC0AUgBlAGMAdQByAHMAZQAgAC0ARgBvAHIAYwBlAA==",
        sentinelOneAction.getCommandEncoded());
    assertEquals(List.of(agent), sentinelOneAction.getAgents());
  }
}
