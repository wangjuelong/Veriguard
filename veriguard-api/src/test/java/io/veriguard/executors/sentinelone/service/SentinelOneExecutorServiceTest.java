package io.veriguard.executors.sentinelone.service;

import static io.veriguard.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_NAME;
import static io.veriguard.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.database.model.*;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.model.AgentRegisterInput;
import io.veriguard.executors.sentinelone.client.SentinelOneExecutorClient;
import io.veriguard.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.veriguard.executors.sentinelone.model.SentinelOneAgent;
import io.veriguard.service.AgentService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.EndpointService;
import io.veriguard.utils.fixtures.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SentinelOneExecutorServiceTest {

  @Mock private SentinelOneExecutorClient client;
  @Mock private SentinelOneExecutorConfig config;
  @Mock private AssetGroupService assetGroupService;
  @Mock private EndpointService endpointService;
  @Mock private AgentService agentService;
  @Mock private ExecutorService executorService;

  @InjectMocks private SentinelOneExecutorService sentinelOneExecutorService;

  @InjectMocks private SentinelOneExecutorContextService sentinelOneExecutorContextService;

  private SentinelOneAgent sentinelOneAgent;
  private Executor sentinelOneExecutor;

  @BeforeEach
  void setUp() {
    sentinelOneAgent = SentinelOneDeviceFixture.createDefaultSentinelOneAgent();
    sentinelOneExecutor = new Executor();
    sentinelOneExecutor.setName(SENTINELONE_EXECUTOR_NAME);
    sentinelOneExecutor.setType(SENTINELONE_EXECUTOR_TYPE);
  }

  @Test
  void test_run_sentinelone() {
    // Init datas
    when(client.agents()).thenReturn(Set.of(sentinelOneAgent));
    // Run method to test
    sentinelOneExecutorService.run();
    // Asserts
    ArgumentCaptor<String> executorTypeCaptor = ArgumentCaptor.forClass(String.class);
    verify(agentService).getAgentsByExecutorType(executorTypeCaptor.capture());
    assertEquals(sentinelOneExecutor.getType(), executorTypeCaptor.getValue());

    ArgumentCaptor<List<AgentRegisterInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Agent>> agents = ArgumentCaptor.forClass(List.class);
    verify(endpointService).syncAgentsEndpoints(inputsCaptor.capture(), agents.capture());
    assertEquals(1, inputsCaptor.getValue().size());
    assertEquals(0, agents.getValue().size());

    ArgumentCaptor<AssetGroup> assetGroupCaptor = ArgumentCaptor.forClass(AssetGroup.class);
    verify(assetGroupService, times(3))
        .createOrUpdateAssetGroupWithoutDynamicAssets(assetGroupCaptor.capture());
    assertEquals(3, assetGroupCaptor.getAllValues().size());
    assertEquals(
        sentinelOneAgent.getAccountId(),
        assetGroupCaptor.getAllValues().get(0).getExternalReference());
    assertEquals(
        sentinelOneAgent.getGroupId(),
        assetGroupCaptor.getAllValues().get(1).getExternalReference());
    assertEquals(
        sentinelOneAgent.getSiteId(),
        assetGroupCaptor.getAllValues().get(2).getExternalReference());
  }

  @Test
  void test_launchBatchExecutorSubprocess_sentinelone()
      throws JsonProcessingException, InterruptedException {
    // Init datas
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsScriptId()).thenReturn("1234567890");
    Command payloadCommand =
        PayloadFixture.createCommand(
            "cmd",
            "whoami",
            List.of(),
            "whoami",
            Set.of(new Domain(null, "To classify", "#000000", Instant.now(), null)));
    NodeExecutor nodeExecutor = NodeExecutorFixture.createDefaultPayloadNodeExecutor();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "x86_64");
    nodeExecutor.setExecutorCommands(executorCommands);
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.createTechnicalAttackChainNode(
            NodeContractFixture.createPayloadNodeContract(nodeExecutor, payloadCommand),
            "Inject",
            EndpointFixture.createEndpoint());
    attackChainNode.setId("injectId");
    List<Agent> agents =
        List.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "12345"));
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    when(executorService.manageWithoutPlatformAgents(agents, attackChainNodeStatus))
        .thenReturn(agents);
    // Run method to test
    sentinelOneExecutorContextService.launchBatchExecutorSubprocess(
        attackChainNode, new HashSet<>(agents), attackChainNodeStatus);
    // Executor scheduled so we have to wait before the execution
    Thread.sleep(1000);
    // Asserts
    ArgumentCaptor<List<String>> agentIds = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> scriptName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> commandEncoded = ArgumentCaptor.forClass(String.class);
    verify(client)
        .executeScript(agentIds.capture(), scriptName.capture(), commandEncoded.capture());
    assertEquals(1, agentIds.getValue().size());
    assertEquals("1234567890", scriptName.getValue());
    assertEquals(
        "JABhAGcAZQBuAHQASQBEAD0AJgAgACcAQwA6AFwAUAByAG8AZwByAGEAbQAgAEYAaQBsAGUAcwBcAFMAZQBuAHQAaQBuAGUAbABPAG4AZQBcAFMAZQBuAHQAaQBuAGUAbAAgAEEAZwBlAG4AdAAgACoAXABTAGUAbgB0AGkAbgBlAGwAQwB0AGwALgBlAHgAZQAnACAAYQBnAGUAbgB0AF8AaQBkADsAeAA4ADYAXwA2ADQA",
        commandEncoded.getValue());
  }
}
