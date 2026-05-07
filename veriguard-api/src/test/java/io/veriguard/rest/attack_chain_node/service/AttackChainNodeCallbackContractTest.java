package io.veriguard.rest.attack_chain_node.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.AgentRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionAction;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionCallback;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionInput;
import io.veriguard.rest.helper.queue.BatchQueueService;
import io.veriguard.service.AttackChainNodeExpectationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Contract tests that verify the sync path ({@link
 * AttackChainNodeExecutionService#handleAttackChainNodeExecutionCallback}) and the async batch path
 * ({@link BatchingAttackChainNodeStatusService#handleAttackChainNodeExecutionCallback}) apply the
 * same business rules.
 *
 * <p>Each test runs the same assertion against both paths using a functional interface that
 * abstracts the invocation.
 */
@ExtendWith(MockitoExtension.class)
// this is a contract test only where each parameterization run different code paths, lenient mode
// is OK in this case.
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inject Callback Contract Tests")
class AttackChainNodeCallbackContractTest {

  @Mock private AttackChainNodeRepository attackChainNodeRepository;
  @Mock private AgentRepository agentRepository;
  @Mock private AttackChainNodeExpectationService attackChainNodeExpectationService;
  @Mock private AttackChainNodeStatusService attackChainNodeStatusService;
  @Mock private AttackChainNodeService attackChainNodeService;
  @Mock private AgentExecutionProcessingHandler agentExecutionProcessingHandler;
  @Mock private NodeExecutorExecutionProcessingHandler nodeExecutorExecutionProcessingHandler;
  @Mock private StructuredOutputUtils structuredOutputUtils;

  @Mock
  private BatchQueueService<AttackChainNodeExecutionCallback> attackChainNodeTraceQueueService;

  private AttackChainNodeExecutionService attackChainNodeExecutionService;
  private BatchingAttackChainNodeStatusService batchingService;

  @FunctionalInterface
  interface CallbackInvoker {
    void invoke(String attackChainNodeId, String agentId, AttackChainNodeExecutionInput input)
        throws Exception;
  }

  @BeforeEach
  void setUp() throws Exception {
    lenient().when(attackChainNodeService.getValueTargetedAssetMap(any())).thenReturn(Map.of());
    lenient()
        .doReturn(Optional.empty())
        .when(agentExecutionProcessingHandler)
        .processContext(any());
    lenient()
        .doReturn(Optional.empty())
        .when(nodeExecutorExecutionProcessingHandler)
        .processContext(any());

    // Spy: real object with mock dependencies, so we can verify method calls
    attackChainNodeExecutionService =
        spy(
            new AttackChainNodeExecutionService(
                attackChainNodeRepository,
                attackChainNodeExpectationService,
                agentRepository,
                attackChainNodeStatusService,
                attackChainNodeService,
                agentExecutionProcessingHandler,
                nodeExecutorExecutionProcessingHandler));

    // Can't use @InjectMocks: batchingService needs the spy, not a plain mock
    batchingService =
        new BatchingAttackChainNodeStatusService(
            attackChainNodeRepository,
            agentRepository,
            structuredOutputUtils,
            attackChainNodeExecutionService);
    batchingService.setAttackChainNodeTraceQueueService(attackChainNodeTraceQueueService);
  }

  private CallbackInvoker syncInvoker() {
    return (attackChainNodeId, agentId, input) ->
        attackChainNodeExecutionService.handleAttackChainNodeExecutionCallback(
            attackChainNodeId, agentId, input);
  }

  private CallbackInvoker asyncInvoker() {
    return (attackChainNodeId, agentId, input) -> {
      AttackChainNodeExecutionCallback callback =
          AttackChainNodeExecutionCallback.builder()
              .attackChainNodeId(attackChainNodeId)
              .agentId(agentId)
              .attackChainNodeExecutionInput(input)
              .emissionDate(System.currentTimeMillis())
              .build();
      batchingService.handleAttackChainNodeExecutionCallback(List.of(callback));
    };
  }

  static Stream<Arguments> bothPaths() {
    return Stream.of(
        Arguments.of(Named.of("sync", "sync")), Arguments.of(Named.of("async", "async")));
  }

  private CallbackInvoker invokerFor(String path) {
    return "sync".equals(path) ? syncInvoker() : asyncInvoker();
  }

  private AttackChainNode createAttackChainNodeWithStatus(
      String attackChainNodeId, ExecutionStatus executionStatus) {
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setId(attackChainNodeId);
    AttackChainNodeStatus status = new AttackChainNodeStatus();
    status.setName(executionStatus);
    attackChainNode.setStatus(status);
    return attackChainNode;
  }

  private Agent createAgent(String agentId) {
    Agent agent = new Agent();
    agent.setId(agentId);
    return agent;
  }

  private AttackChainNodeExecutionInput createInput(AttackChainNodeExecutionAction action) {
    AttackChainNodeExecutionInput input = new AttackChainNodeExecutionInput();
    input.setAction(action);
    input.setMessage("test");
    input.setStatus("SUCCESS");
    return input;
  }

  // ========================================================================
  // Contract: neither path processes a complete action on a non-PENDING attackChainNode.
  //
  // Note: the two paths diverge on *how* they reject it — the sync path still
  // throws DataIntegrityViolationException, while the async batch path silently
  // queues the callback for later retry (handled by ExecutionTracesBatchRequeueJob).
  // What remains a true contract is that neither path delegates the execution
  // processing in that situation.
  // ========================================================================
  @ParameterizedTest(name = "{0}")
  @MethodSource("bothPaths")
  @DisplayName("should not process complete action for non-PENDING inject")
  void shouldRejectNonPendingCompleteAction(String path) {
    AttackChainNode attackChainNode =
        createAttackChainNodeWithStatus("inject-1", ExecutionStatus.EXECUTING);

    when(attackChainNodeRepository.findById("inject-1")).thenReturn(Optional.of(attackChainNode));
    when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
        .thenReturn(List.of(attackChainNode));
    when(agentRepository.findAllById(anyList())).thenReturn(List.of());

    AttackChainNodeExecutionInput input = createInput(AttackChainNodeExecutionAction.complete);
    CallbackInvoker invoker = invokerFor(path);

    if ("sync".equals(path)) {
      // Sync path still throws DataIntegrityViolationException (not caught internally)
      assertThrows(
          DataIntegrityViolationException.class, () -> invoker.invoke("inject-1", null, input));
    } else {
      // Async batch path does not throw — it queues the callback for retry instead.
      assertDoesNotThrow(() -> invoker.invoke("inject-1", null, input));
    }

    // Neither path should have delegated to execution processing
    verify(attackChainNodeExecutionService, never())
        .processAttackChainNodeExecutionWithAgent(any(), any(), any());
    verify(attackChainNodeExecutionService, never())
        .processAttackChainNodeExecutionWithNodeExecutor(any(), any());
  }

  // ========================================================================
  // Contract: both paths handle missing attackChainNode gracefully
  // ========================================================================
  @ParameterizedTest(name = "{0}")
  @MethodSource("bothPaths")
  @DisplayName("should handle missing inject gracefully")
  void shouldHandleMissingAttackChainNodeGracefully(String path) {
    when(attackChainNodeRepository.findById("missing-inject")).thenReturn(Optional.empty());
    when(attackChainNodeRepository.findAllByIdWithExpectations(anyList())).thenReturn(List.of());
    when(agentRepository.findAllById(anyList())).thenReturn(List.of());

    AttackChainNodeExecutionInput input =
        createInput(AttackChainNodeExecutionAction.command_execution);
    CallbackInvoker invoker = invokerFor(path);

    // Both paths should handle the missing attackChainNode without uncaught exceptions
    assertDoesNotThrow(() -> invoker.invoke("missing-inject", null, input));

    // Both paths should call handleAttackChainNodeExecutionError
    verify(attackChainNodeExecutionService)
        .handleAttackChainNodeExecutionError(isNull(), any(Exception.class));
  }

  // ========================================================================
  // Contract: both paths handle missing agent gracefully
  // ========================================================================
  @ParameterizedTest(name = "{0}")
  @MethodSource("bothPaths")
  @DisplayName("should handle missing agent gracefully")
  void shouldHandleMissingAgentGracefully(String path) {
    AttackChainNode attackChainNode =
        createAttackChainNodeWithStatus("inject-1", ExecutionStatus.PENDING);

    when(attackChainNodeRepository.findById("inject-1")).thenReturn(Optional.of(attackChainNode));
    when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
        .thenReturn(List.of(attackChainNode));
    when(agentRepository.findById("missing-agent")).thenReturn(Optional.empty());
    when(agentRepository.findAllById(anyList())).thenReturn(List.of());

    AttackChainNodeExecutionInput input =
        createInput(AttackChainNodeExecutionAction.command_execution);
    CallbackInvoker invoker = invokerFor(path);

    // Both paths should handle the missing agent without uncaught exceptions
    assertDoesNotThrow(() -> invoker.invoke("inject-1", "missing-agent", input));

    // Both paths should call handleAttackChainNodeExecutionError
    verify(attackChainNodeExecutionService)
        .handleAttackChainNodeExecutionError(eq(attackChainNode), any(Exception.class));
  }

  // ========================================================================
  // Contract: both paths delegate to processAttackChainNodeExecution for valid callbacks
  // ========================================================================
  @ParameterizedTest(name = "{0}")
  @MethodSource("bothPaths")
  @DisplayName("should call processInjectExecution for valid callbacks")
  void shouldDelegateToProcessAttackChainNodeExecution(String path) throws Exception {
    AttackChainNode attackChainNode =
        createAttackChainNodeWithStatus("inject-1", ExecutionStatus.PENDING);
    Agent agent = createAgent("agent-1");

    when(attackChainNodeRepository.findById("inject-1")).thenReturn(Optional.of(attackChainNode));
    when(attackChainNodeRepository.findAllByIdWithExpectations(anyList()))
        .thenReturn(List.of(attackChainNode));
    when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
    when(agentRepository.findAllById(anyList())).thenReturn(List.of(agent));

    AttackChainNodeExecutionInput input =
        createInput(AttackChainNodeExecutionAction.command_execution);
    CallbackInvoker invoker = invokerFor(path);

    invoker.invoke("inject-1", "agent-1", input);

    verify(attackChainNodeExecutionService)
        .processAttackChainNodeExecutionWithAgent(eq(attackChainNode), eq(agent), eq(input));
  }
}
