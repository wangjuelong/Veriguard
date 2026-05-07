package io.veriguard.executors.execution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.ExecutionTraceRepository;
import io.veriguard.execution.ExecutionExecutorException;
import io.veriguard.execution.ExecutionExecutorService;
import io.veriguard.executors.ExecutorContextService;
import io.veriguard.executors.utils.ExecutorUtils;
import io.veriguard.rest.attack_chain_node.output.AgentsAndAssetsAgentless;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.domain.enums.PresetDomain;
import io.veriguard.rest.exception.AgentException;
import io.veriguard.utils.fixtures.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ExecutionExecutorServiceTest {

  @Mock private AttackChainNodeService attackChainNodeService;
  @Mock private ExecutionTraceRepository executionTraceRepository;
  @Mock private ExecutorContextService executorContextService;

  @InjectMocks private ExecutionExecutorService executorService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(executorService, "executorUtils", new ExecutorUtils());
  }

  @Test
  void test_launchExecutorContext_noAssetException() throws Exception {

    // Init datas
    Command payloadCommand =
        PayloadFixture.createCommand(
            "cmd", "whoami", List.of(), "whoami", new HashSet<>(Set.of(PresetDomain.TOCLASSIFY)));
    NodeExecutor nodeExecutor = NodeExecutorFixture.createDefaultPayloadNodeExecutor();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "x86_64");
    nodeExecutor.setExecutorCommands(executorCommands);
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("0123456789");
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.createTechnicalAttackChainNode(
            NodeContractFixture.createPayloadNodeContract(nodeExecutor, payloadCommand),
            "Inject",
            endpoint);
    attackChainNode.setStatus(AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus());
    when(attackChainNodeService.getAgentsAndAgentlessAssetsByAttackChainNode(attackChainNode))
        .thenReturn(
            new AgentsAndAssetsAgentless(new HashSet<>(), new HashSet<>(List.of(endpoint))));
    // Run method to test
    assertThrows(
        ExecutionExecutorException.class,
        () -> {
          executorService.launchExecutorContext(attackChainNode);
        });
  }

  @Test
  void test_saveAgentlessAssetsTraces_withAgents() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("0123456789");
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    // Run method to test
    executorService.saveAgentlessAssetsTraces(
        new HashSet<>(Set.of(endpoint)), attackChainNodeStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository).saveAll(executionTrace.capture());
    assertEquals(
        ExecutionTraceStatus.ASSET_AGENTLESS, executionTrace.getValue().getFirst().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getFirst().getAction());
    assertEquals(
        "Asset " + endpoint.getName() + " has no agent, unable to launch the inject",
        executionTrace.getValue().getFirst().getMessage());
  }

  @Test
  void test_saveAgentlessAssetsTraces_withoutAgents() {
    // Init datas
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    // Run method to test
    executorService.saveAgentlessAssetsTraces(new HashSet<>(), attackChainNodeStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository, never()).saveAll(executionTrace.capture());
  }

  @Test
  void test_saveInactiveAgentsTraces_withAgents() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    agent.setLastSeen(Instant.now().minus(5, ChronoUnit.DAYS));
    endpoint.setAgents(List.of(agent));
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    // Run method to test
    executorService.saveInactiveAgentsTraces(new HashSet<>(Set.of(agent)), attackChainNodeStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository).saveAll(executionTrace.capture());
    assertEquals(
        ExecutionTraceStatus.AGENT_INACTIVE, executionTrace.getValue().getFirst().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getFirst().getAction());
    assertEquals(
        "Agent "
            + agent.getExecutedByUser()
            + " is inactive for the asset "
            + agent.getAsset().getName(),
        executionTrace.getValue().getFirst().getMessage());
  }

  @Test
  void test_saveInactiveAgentsTraces_withoutAgents() {
    // Init datas
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    // Run method to test
    executorService.saveInactiveAgentsTraces(new HashSet<>(), attackChainNodeStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository, never()).saveAll(executionTrace.capture());
  }

  @Test
  void test_saveWithoutExecutorAgentsTraces_withAgents() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    agent.setExecutor(null);
    endpoint.setAgents(List.of(agent));
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    // Run method to test
    executorService.saveWithoutExecutorAgentsTraces(
        new HashSet<>(Set.of(agent)), attackChainNodeStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository).saveAll(executionTrace.capture());
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace.getValue().getFirst().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getFirst().getAction());
    assertEquals(
        "Cannot find the executor for the agent "
            + agent.getExecutedByUser()
            + " from the asset "
            + agent.getAsset().getName(),
        executionTrace.getValue().getFirst().getMessage());
  }

  @Test
  void test_saveWithoutExecutorAgentsTraces_withoutAgents() {
    // Init datas
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    // Run method to test
    executorService.saveWithoutExecutorAgentsTraces(new HashSet<>(), attackChainNodeStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository, never()).saveAll(executionTrace.capture());
  }

  @Test
  void test_saveCrowdstrikeSentineloneAgentsErrorTraces() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    endpoint.setAgents(List.of(agent));
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    // Run method to test
    executorService.saveAgentsErrorTraces(
        new Exception("EXCEPTION !!"), new HashSet<>(Set.of(agent)), attackChainNodeStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository).saveAll(executionTrace.capture());
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace.getValue().getFirst().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getFirst().getAction());
    assertEquals("EXCEPTION !!", executionTrace.getValue().getFirst().getMessage());
  }

  @Test
  void saveAgentErrorTrace() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    endpoint.setAgents(List.of(agent));
    AttackChainNodeStatus attackChainNodeStatus =
        AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
    // Run method to test
    executorService.saveAgentErrorTrace(
        new AgentException("EXCEPTION !!", agent), attackChainNodeStatus);
    // Asserts
    ArgumentCaptor<ExecutionTrace> executionTrace = ArgumentCaptor.forClass(ExecutionTrace.class);
    verify(executionTraceRepository).save(executionTrace.capture());
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace.getValue().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getAction());
    assertEquals("EXCEPTION !!", executionTrace.getValue().getMessage());
  }
}
