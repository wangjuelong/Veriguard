package io.veriguard.rest.inject.service;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.Agent;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionAction;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionInput;
import io.veriguard.service.AttackChainNodeExpectationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttackChainNodeExecutionServiceTest {

  private AttackChainNodeExecutionService service;
  private AgentExecutionProcessingHandler agentHandler;
  private NodeExecutorExecutionProcessingHandler nodeExecutorHandler;

  @BeforeEach
  void setUp() {
    agentHandler = mock(AgentExecutionProcessingHandler.class);
    nodeExecutorHandler = mock(NodeExecutorExecutionProcessingHandler.class);
    AttackChainNodeService attackChainNodeService = mock(AttackChainNodeService.class);
    AttackChainNodeStatusService attackChainNodeStatusService = mock(AttackChainNodeStatusService.class);
    AttackChainNodeExpectationService attackChainNodeExpectationService = mock(AttackChainNodeExpectationService.class);
    service =
        new AttackChainNodeExecutionService(
            null,
            attackChainNodeExpectationService,
            null,
            attackChainNodeStatusService,
            attackChainNodeService,
            agentHandler,
            nodeExecutorHandler);
  }

  @Test
  @DisplayName(
      "Should call processContext on handler in processInjectExecution when source is an agent")
  void shouldCallProcessContextOnHandlerInProcessAttackChainNodeExecution() throws Exception {
    AttackChainNode attackChainNode = mock(AttackChainNode.class);
    Agent agent = mock(Agent.class);

    AttackChainNodeExecutionInput input = new AttackChainNodeExecutionInput();
    String logMessage =
        "{\"stdout\":\"[CVE-2025-25241] [http] [critical] http://seen-ip-endpoint/\\n[CVE-2025-25002] [http] [critical] http://seen-ip-endpoint/\\n\"}";
    input.setMessage(logMessage);
    input.setAction(AttackChainNodeExecutionAction.command_execution);
    input.setStatus("SUCCESS");

    when(agentHandler.processContext(any())).thenReturn(Optional.of(mock(ObjectNode.class)));
    AttackChainNodeExecutionService spyService = spy(service);
    spyService.processAttackChainNodeExecutionWithAgent(attackChainNode, agent, input);
    verify(agentHandler).processContext(any());
  }

  @Test
  @DisplayName(
      "Should call processContext on handler in processInjectExecution when source is an injector")
  void shouldCallProcessContextOnNodeExecutorHandlerInProcessAttackChainNodeExecution() throws Exception {
    AttackChainNode attackChainNode = mock(AttackChainNode.class);

    AttackChainNodeExecutionInput input = new AttackChainNodeExecutionInput();
    String logMessage =
        "{\"stdout\":\"[CVE-2025-25241] [http] [critical] http://seen-ip-endpoint/\\n[CVE-2025-25002] [http] [critical] http://seen-ip-endpoint/\\n\"}";
    input.setMessage(logMessage);
    input.setAction(AttackChainNodeExecutionAction.command_execution);
    input.setStatus("SUCCESS");

    when(nodeExecutorHandler.processContext(any())).thenReturn(Optional.of(mock(ObjectNode.class)));
    AttackChainNodeExecutionService spyService = spy(service);
    spyService.processAttackChainNodeExecutionWithNodeExecutor(attackChainNode, input);
    verify(nodeExecutorHandler).processContext(any());
  }
}
