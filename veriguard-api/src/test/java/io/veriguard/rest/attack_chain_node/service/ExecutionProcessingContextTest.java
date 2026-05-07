package io.veriguard.rest.attack_chain_node.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionInput;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionProcessingContextTest {

  @Test
  @DisplayName("Should identify agent execution context")
  void shouldIdentifyAgentExecutionContext() {
    ExecutionProcessingContext context =
        new ExecutionProcessingContext(
            mock(AttackChainNode.class), mock(Agent.class), mock(AttackChainNodeExecutionInput.class), Map.of());
    assertTrue(context.isAgentExecution());
    assertFalse(context.isNodeExecutorExecution());
  }

  @Test
  @DisplayName("Should identify injector execution context")
  void shouldIdentifyNodeExecutorExecutionContext() {
    ExecutionProcessingContext context =
        new ExecutionProcessingContext(
            mock(AttackChainNode.class), null, mock(AttackChainNodeExecutionInput.class), Map.of());
    assertFalse(context.isAgentExecution());
    assertTrue(context.isNodeExecutorExecution());
  }
}
