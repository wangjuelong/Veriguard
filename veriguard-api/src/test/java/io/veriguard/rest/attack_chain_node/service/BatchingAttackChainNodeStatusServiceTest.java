package io.veriguard.rest.attack_chain_node.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeStatus;
import io.veriguard.database.model.ExecutionStatus;
import io.veriguard.database.repository.AgentRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionAction;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionCallback;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionInput;
import io.veriguard.rest.helper.queue.BatchQueueService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchingInjectStatusService Tests")
class BatchingAttackChainNodeStatusServiceTest {

  @Mock private AttackChainNodeRepository attackChainNodeRepository;
  @Mock private AgentRepository agentRepository;
  @Mock private StructuredOutputUtils structuredOutputUtils;
  @Mock private AttackChainNodeExecutionService attackChainNodeExecutionService;

  @Mock
  private BatchQueueService<AttackChainNodeExecutionCallback> attackChainNodeTraceQueueService;

  @InjectMocks private BatchingAttackChainNodeStatusService service;

  private static final String INJECT_ID = "inject-1";
  private static final String AGENT_ID = "agent-1";
  private static final int MAX_RETRIES = 5;

  @BeforeEach
  void wireQueueService() {
    // @InjectMocks does not set non-final fields with @Setter; wire it explicitly for the tests
    // that attackChainRun the requeue path. Tests that want to attackChainRun the null-guard branch
    // can
    // override this via service.setAttackChainNodeTraceQueueService(null).
    service.setAttackChainNodeTraceQueueService(attackChainNodeTraceQueueService);
  }

  private AttackChainNode createAttackChainNodeWithPendingStatus(String attackChainNodeId) {
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setId(attackChainNodeId);
    AttackChainNodeStatus status = new AttackChainNodeStatus();
    status.setName(ExecutionStatus.PENDING);
    attackChainNode.setStatus(status);
    return attackChainNode;
  }

  private AttackChainNode createAttackChainNodeWithExecutingStatus(String attackChainNodeId) {
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setId(attackChainNodeId);
    AttackChainNodeStatus status = new AttackChainNodeStatus();
    status.setName(ExecutionStatus.EXECUTING);
    attackChainNode.setStatus(status);
    return attackChainNode;
  }

  private Agent createAgent(String agentId) {
    Agent agent = new Agent();
    agent.setId(agentId);
    return agent;
  }

  private AttackChainNodeExecutionCallback createCallback(
      String attackChainNodeId,
      String agentId,
      AttackChainNodeExecutionAction action,
      long emissionDate) {
    AttackChainNodeExecutionInput input = new AttackChainNodeExecutionInput();
    input.setAction(action);
    input.setMessage("test message");
    input.setStatus("SUCCESS");
    return AttackChainNodeExecutionCallback.builder()
        .attackChainNodeId(attackChainNodeId)
        .agentId(agentId)
        .attackChainNodeExecutionInput(input)
        .emissionDate(emissionDate)
        .build();
  }

  // ========================================================================
  // Chronological ordering
  // ========================================================================
  @Nested
  @DisplayName("Chronological ordering")
  class ChronologicalOrderingTests {

    @Test
    @DisplayName("should process callbacks in chronological order by emission date")
    void shouldProcessInChronologicalOrder() {
      AttackChainNode attackChainNode = createAttackChainNodeWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      // Create callbacks out of order
      AttackChainNodeExecutionCallback late =
          createCallback(
              INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.command_execution, 3000L);
      AttackChainNodeExecutionCallback early =
          createCallback(
              INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.command_execution, 1000L);
      AttackChainNodeExecutionCallback middle =
          createCallback(
              INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.command_execution, 2000L);

      service.handleAttackChainNodeExecutionCallback(List.of(late, early, middle));

      // Verify processAttackChainNodeExecutionWithAgent was called 3 times in chronological order
      InOrder inOrder = inOrder(attackChainNodeExecutionService);
      inOrder
          .verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(
              eq(attackChainNode),
              eq(agent),
              argThat(input -> input == early.getAttackChainNodeExecutionInput()));
      inOrder
          .verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(
              eq(attackChainNode),
              eq(agent),
              argThat(input -> input == middle.getAttackChainNodeExecutionInput()));
      inOrder
          .verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(
              eq(attackChainNode),
              eq(agent),
              argThat(input -> input == late.getAttackChainNodeExecutionInput()));
    }
  }

  // ========================================================================
  // ElementNotFoundException handling
  // ========================================================================
  @Nested
  @DisplayName("ElementNotFoundException handling")
  class ElementNotFoundTests {

    @Test
    @DisplayName("should handle missing inject gracefully and add to success list")
    void shouldHandleMissingAttackChainNode() {
      // Return empty list — no attackChainNode found
      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of());
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      AttackChainNodeExecutionCallback callback =
          createCallback(
              "non-existent-inject",
              AGENT_ID,
              AttackChainNodeExecutionAction.command_execution,
              1000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      // Should be in success list because ElementNotFoundException is caught and handled
      assertEquals(1, result.size());
      verify(attackChainNodeExecutionService)
          .handleAttackChainNodeExecutionError(isNull(), any(Exception.class));
    }

    @Test
    @DisplayName("should handle missing agent gracefully and add to success list")
    void shouldHandleMissingAgent() {
      AttackChainNode attackChainNode = createAttackChainNodeWithPendingStatus(INJECT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      // Return empty — no agent found
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      AttackChainNodeExecutionCallback callback =
          createCallback(
              INJECT_ID,
              "non-existent-agent",
              AttackChainNodeExecutionAction.command_execution,
              1000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      // Missing agent throws ElementNotFoundException → caught → added to success
      assertEquals(1, result.size());
      verify(attackChainNodeExecutionService)
          .handleAttackChainNodeExecutionError(eq(attackChainNode), any(Exception.class));
    }
  }

  // ========================================================================
  // General exception handling
  // ========================================================================
  @Nested
  @DisplayName("General exception handling")
  class GeneralExceptionTests {

    @Test
    @DisplayName("should not add callback to success list when general exception occurs")
    void shouldNotAddToSuccessListOnGeneralException() {
      AttackChainNode attackChainNode = createAttackChainNodeWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      doThrow(new RuntimeException("Unexpected error"))
          .when(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(any(), any(), any());

      AttackChainNodeExecutionCallback callback =
          createCallback(
              INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.command_execution, 1000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      // General exception → NOT added to success list
      assertTrue(result.isEmpty());
    }
  }

  // ========================================================================
  // PENDING state guard
  // ========================================================================
  @Nested
  @DisplayName("PENDING state guard")
  class PendingStateGuardTests {

    @Test
    @DisplayName("should not process complete action for non-PENDING inject and queue it for retry")
    void shouldRejectCompleteActionForNonPendingAttackChainNode() {
      // AttackChainNode is in EXECUTING state, not PENDING
      AttackChainNode attackChainNode = createAttackChainNodeWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      AttackChainNodeExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.complete, 1000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      // The callback is queued for retry → NOT added to the successfully-processed list,
      // and no execution processing happened.
      assertTrue(result.isEmpty());
      verify(attackChainNodeExecutionService, never())
          .processAttackChainNodeExecutionWithAgent(any(), any(), any());
      verify(attackChainNodeExecutionService, never())
          .processAttackChainNodeExecutionWithNodeExecutor(any(), any());
      // retry counter is bumped on the callback instance
      assertEquals(1, callback.getRetryCount());
    }

    @Test
    @DisplayName("should allow complete action for PENDING inject")
    void shouldAllowCompleteActionForPendingAttackChainNode() {
      AttackChainNode attackChainNode = createAttackChainNodeWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      AttackChainNodeExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.complete, 1000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(
              eq(attackChainNode), eq(agent), eq(callback.getAttackChainNodeExecutionInput()));
    }

    @Test
    @DisplayName("should allow non-complete actions regardless of inject status")
    void shouldAllowNonCompleteActionsRegardlessOfStatus() {
      AttackChainNode attackChainNode = createAttackChainNodeWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      // command_execution action should pass regardless of status
      AttackChainNodeExecutionCallback callback =
          createCallback(
              INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.command_execution, 1000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(
              eq(attackChainNode), eq(agent), eq(callback.getAttackChainNodeExecutionInput()));
    }
  }

  // ========================================================================
  // Bulk loading optimization
  // ========================================================================
  @Nested
  @DisplayName("Bulk loading optimization")
  class BulkLoadingTests {

    @Test
    @DisplayName("should load all injects and agents in a single query each")
    void shouldBulkLoadAttackChainNodesAndAgents() {
      AttackChainNode attackChainNode1 = createAttackChainNodeWithPendingStatus("inject-1");
      AttackChainNode attackChainNode2 = createAttackChainNodeWithPendingStatus("inject-2");
      Agent agent1 = createAgent("agent-1");
      Agent agent2 = createAgent("agent-2");

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode1, attackChainNode2));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent1, agent2));

      AttackChainNodeExecutionCallback callback1 =
          createCallback(
              "inject-1", "agent-1", AttackChainNodeExecutionAction.command_execution, 1000L);
      AttackChainNodeExecutionCallback callback2 =
          createCallback(
              "inject-2", "agent-2", AttackChainNodeExecutionAction.command_execution, 2000L);

      service.handleAttackChainNodeExecutionCallback(List.of(callback1, callback2));

      // Verify bulk loads are called exactly once each
      verify(attackChainNodeRepository, times(1)).findAllByIdWithExpectations(anyList());
      verify(agentRepository, times(1)).findAllById(anyList());
    }
  }

  // ========================================================================
  // Successful processing
  // ========================================================================
  @Nested
  @DisplayName("Successful processing")
  class SuccessfulProcessingTests {

    @Test
    @DisplayName("should call processInjectExecutionWithAgent with correct arguments")
    void shouldCallProcessAttackChainNodeExecutionWithCorrectArgs() {
      AttackChainNode attackChainNode = createAttackChainNodeWithPendingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      AttackChainNodeExecutionCallback callback =
          createCallback(
              INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.command_execution, 1000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      assertSame(callback, result.getFirst());
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(
              eq(attackChainNode), eq(agent), eq(callback.getAttackChainNodeExecutionInput()));
    }

    @Test
    @DisplayName("should handle callback with null agentId by passing null agent")
    void shouldHandleNullAgentId() {
      AttackChainNode attackChainNode = createAttackChainNodeWithPendingStatus(INJECT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      AttackChainNodeExecutionCallback callback =
          createCallback(INJECT_ID, null, AttackChainNodeExecutionAction.command_execution, 1000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      assertEquals(1, result.size());
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithNodeExecutor(
              eq(attackChainNode), eq(callback.getAttackChainNodeExecutionInput()));
    }
  }

  // ========================================================================
  // Edge cases
  // ========================================================================
  @Nested
  @DisplayName("Edge cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle empty callback list gracefully")
    void shouldHandleEmptyCallbackList() {
      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of());
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of());

      assertTrue(result.isEmpty());
      verify(attackChainNodeExecutionService, never())
          .processAttackChainNodeExecutionWithAgent(any(), any(), any());
      verify(attackChainNodeExecutionService, never())
          .processAttackChainNodeExecutionWithNodeExecutor(any(), any());
    }

    // ------------------------------------------------------------------
    // Retry / requeue behavior for complete-before-PENDING race condition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("should drain the requeue queue to the external queue service")
    void shouldDrainRequeueQueueOnRequeueCallbacks() throws IOException {
      // First call: attackChainNode is not PENDING → callback gets queued for retry
      AttackChainNode attackChainNode = createAttackChainNodeWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      long originalEmissionDate = 1000L;
      AttackChainNodeExecutionCallback callback =
          createCallback(
              INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.complete, originalEmissionDate);
      long beforeHandle = Instant.now().toEpochMilli();
      service.handleAttackChainNodeExecutionCallback(List.of(callback));

      // Drain
      service.requeueCallbacks();

      ArgumentCaptor<AttackChainNodeExecutionCallback> captor =
          ArgumentCaptor.forClass(AttackChainNodeExecutionCallback.class);
      verify(attackChainNodeTraceQueueService).publish(captor.capture());
      assertSame(callback, captor.getValue());
      assertEquals(1, captor.getValue().getRetryCount());
      // emissionDate must be bumped to "now" on retry so the re-queued callback does not
      // compete for ordering against freshly emitted ones.
      assertNotEquals(originalEmissionDate, captor.getValue().getEmissionDate());
      assertTrue(
          captor.getValue().getEmissionDate() >= beforeHandle,
          "emissionDate should have been refreshed at retry time");
    }

    @Test
    @DisplayName(
        "should process a mixed batch: queue the non-PENDING complete for retry and process the "
            + "other callback normally")
    void shouldHandleMixedBatchOfRetryAndNormalCallbacks() {
      // attackChainNode-1 is in EXECUTING → complete callback must be queued for retry
      AttackChainNode executingAttackChainNode =
          createAttackChainNodeWithExecutingStatus("inject-1");
      // attackChainNode-2 is in PENDING → complete callback must be processed normally
      AttackChainNode pendingAttackChainNode = createAttackChainNodeWithPendingStatus("inject-2");
      Agent agent1 = createAgent("agent-1");
      Agent agent2 = createAgent("agent-2");

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(executingAttackChainNode, pendingAttackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent1, agent2));

      AttackChainNodeExecutionCallback retryCallback =
          createCallback("inject-1", "agent-1", AttackChainNodeExecutionAction.complete, 1000L);
      AttackChainNodeExecutionCallback normalCallback =
          createCallback("inject-2", "agent-2", AttackChainNodeExecutionAction.complete, 2000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(retryCallback, normalCallback));

      // Only the normal callback is in the success list; the retry callback is queued instead.
      assertEquals(1, result.size());
      assertSame(normalCallback, result.getFirst());
      assertEquals(1, retryCallback.getRetryCount());
      assertEquals(0, normalCallback.getRetryCount());

      // Only the pending attackChainNode was processed, not the executing one.
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(
              eq(pendingAttackChainNode),
              eq(agent2),
              eq(normalCallback.getAttackChainNodeExecutionInput()));
      verify(attackChainNodeExecutionService, never())
          .processAttackChainNodeExecutionWithAgent(
              eq(executingAttackChainNode),
              any(),
              eq(retryCallback.getAttackChainNodeExecutionInput()));
    }

    @Test
    @DisplayName(
        "should stop retrying once MAX_RETRIES is reached and still persist the execution trace")
    void shouldPersistExecutionTraceAfterMaxRetries() throws IOException {
      AttackChainNode attackChainNode = createAttackChainNodeWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      AttackChainNodeExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.complete, 1000L);
      // Pretend this callback has already been retried MAX_RETRIES times
      callback.setRetryCount(MAX_RETRIES);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      // Max retries reached → fall through to persist the trace as a last-ditch effort,
      // not re-incremented, not re-queued.
      assertEquals(1, result.size());
      assertSame(callback, result.getFirst());
      assertEquals(MAX_RETRIES, callback.getRetryCount());
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(
              eq(attackChainNode), eq(agent), eq(callback.getAttackChainNodeExecutionInput()));
      service.requeueCallbacks();
      verify(attackChainNodeTraceQueueService, never()).publish(any());
    }

    @Test
    @DisplayName(
        "should persist via injector path when MAX_RETRIES is reached and callback has no agent")
    void shouldPersistViaNodeExecutorPathAfterMaxRetriesWithNullAgent() throws IOException {
      AttackChainNode attackChainNode = createAttackChainNodeWithExecutingStatus(INJECT_ID);

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of());

      AttackChainNodeExecutionCallback callback =
          createCallback(INJECT_ID, null, AttackChainNodeExecutionAction.complete, 1000L);
      // Already exhausted retries
      callback.setRetryCount(MAX_RETRIES);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback));

      // Max retries reached with no agent → fall through to the nodeExecutor branch of
      // saveExecutionTrace and still persist the trace.
      assertEquals(1, result.size());
      assertSame(callback, result.getFirst());
      assertEquals(MAX_RETRIES, callback.getRetryCount());
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithNodeExecutor(
              eq(attackChainNode), eq(callback.getAttackChainNodeExecutionInput()));
      verify(attackChainNodeExecutionService, never())
          .processAttackChainNodeExecutionWithAgent(any(), any(), any());
      service.requeueCallbacks();
      verify(attackChainNodeTraceQueueService, never()).publish(any());
    }

    @Test
    @DisplayName("requeueCallbacks is a safe no-op when queue service is not configured")
    void requeueCallbacksIsSafeWhenQueueServiceIsNull() {
      // Simulate the legacy / unconfigured path: no queue service wired.
      service.setAttackChainNodeTraceQueueService(null);

      AttackChainNode attackChainNode = createAttackChainNodeWithExecutingStatus(INJECT_ID);
      Agent agent = createAgent(AGENT_ID);
      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

      AttackChainNodeExecutionCallback callback =
          createCallback(INJECT_ID, AGENT_ID, AttackChainNodeExecutionAction.complete, 1000L);
      service.handleAttackChainNodeExecutionCallback(List.of(callback));

      // Must not NPE even though the in-memory requeue queue is non-empty.
      assertDoesNotThrow(() -> service.requeueCallbacks());
      verifyNoInteractions(attackChainNodeTraceQueueService);
    }

    @Test
    @DisplayName("should process duplicate inject callbacks with the same inject entity")
    void shouldProcessDuplicateAttackChainNodeCallbacksWithSameAttackChainNodeEntity() {
      AttackChainNode attackChainNode = createAttackChainNodeWithPendingStatus(INJECT_ID);
      Agent agent1 = createAgent("agent-1");
      Agent agent2 = createAgent("agent-2");

      when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
          .thenReturn(List.of(attackChainNode));
      when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent1, agent2));

      AttackChainNodeExecutionCallback callback1 =
          createCallback(
              INJECT_ID, "agent-1", AttackChainNodeExecutionAction.command_execution, 1000L);
      AttackChainNodeExecutionCallback callback2 =
          createCallback(
              INJECT_ID, "agent-2", AttackChainNodeExecutionAction.command_execution, 2000L);

      List<AttackChainNodeExecutionCallback> result =
          service.handleAttackChainNodeExecutionCallback(List.of(callback1, callback2));

      assertEquals(2, result.size());
      // Both callbacks should reference the same AttackChainNode entity from the bulk load
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(same(attackChainNode), eq(agent1), any());
      verify(attackChainNodeExecutionService)
          .processAttackChainNodeExecutionWithAgent(same(attackChainNode), eq(agent2), any());
    }
  }
}
