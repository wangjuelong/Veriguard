package io.veriguard.executors.crowdstrike.service;

import static io.veriguard.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.veriguard.executors.crowdstrike.model.CrowdStrikeAction;
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
public class CrowdstrikeGarbageCollectorServiceTest {

  @Mock private AgentService agentService;
  @Mock private CrowdStrikeExecutorContextService crowdStrikeExecutorContextService;
  @Mock private CrowdStrikeExecutorConfig config;

  @InjectMocks private CrowdStrikeGarbageCollectorService crowdStrikeGarbageCollectorService;

  @Test
  void test_run_garbageCollector_withCrowdstrikeAgents() {
    // Init datas
    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setAsset(EndpointFixture.createEndpoint());
    when(agentService.getAgentsByExecutorType(CROWDSTRIKE_EXECUTOR_TYPE))
        .thenReturn(List.of(agent));
    when(config.getWindowsScriptName()).thenReturn("test script");
    // Run method to test
    crowdStrikeGarbageCollectorService.run();
    // Asserts
    ArgumentCaptor<List<CrowdStrikeAction>> actionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(crowdStrikeExecutorContextService).executeActions(actionsCaptor.capture());
    assertEquals(1, actionsCaptor.getValue().size());
    CrowdStrikeAction crowdStrikeAction = actionsCaptor.getValue().get(0);
    assertEquals("test script", crowdStrikeAction.getScriptName());
    assertEquals(
        "RwBlAHQALQBDAGgAaQBsAGQASQB0AGUAbQAgAC0AUABhAHQAaAAgACIAQwA6AFwAUAByAG8AZwByAGEAbQAgAEYAaQBsAGUAcwAgACgAeAA4ADYAKQBcAEYAaQBsAGkAZwByAGEAbgBcAE8AQQBFAFYAIABBAGcAZQBuAHQAXABwAGEAeQBsAG8AYQBkAHMAIgAsACIAQwA6AFwAUAByAG8AZwByAGEAbQAgAEYAaQBsAGUAcwAgACgAeAA4ADYAKQBcAEYAaQBsAGkAZwByAGEAbgBcAE8AQQBFAFYAIABBAGcAZQBuAHQAXAByAHUAbgB0AGkAbQBlAHMAIgAgAC0ARABpAHIAZQBjAHQAbwByAHkAIAAtAFIAZQBjAHUAcgBzAGUAIAB8ACAAVwBoAGUAcgBlAC0ATwBiAGoAZQBjAHQAIAB7ACQAXwAuAEMAcgBlAGEAdABpAG8AbgBUAGkAbQBlACAALQBsAHQAIAAoAEcAZQB0AC0ARABhAHQAZQApAC4AQQBkAGQASABvAHUAcgBzACgALQAyADQAKQB9ACAAfAAgAFIAZQBtAG8AdgBlAC0ASQB0AGUAbQAgAC0AUgBlAGMAdQByAHMAZQAgAC0ARgBvAHIAYwBlAA==",
        crowdStrikeAction.getCommandEncoded());
    assertEquals(List.of(agent), crowdStrikeAction.getAgents());
  }
}
