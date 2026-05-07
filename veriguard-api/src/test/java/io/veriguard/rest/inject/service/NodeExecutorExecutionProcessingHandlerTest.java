package io.veriguard.rest.inject.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.database.model.ExecutionTraceStatus;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.NodeContract;
import io.veriguard.injector_contract.outputs.NodeContractContentOutputElement;
import io.veriguard.output_processor.OutputProcessor;
import io.veriguard.output_processor.OutputProcessorFactory;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionAction;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionInput;
import io.veriguard.rest.injector_contract.NodeContractContentUtils;
import io.veriguard.utils.fixtures.AttackChainNodeFixture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeExecutorExecutionProcessingHandlerTest {

  @Mock private OutputProcessorFactory outputProcessorFactory;
  @Mock private NodeContractContentUtils nodeContractContentUtils;
  @Mock private OutputProcessor mockProcessor;

  @InjectMocks private NodeExecutorExecutionProcessingHandler handler;

  private final ObjectMapper mapper = new ObjectMapper();
  private AttackChainNode attackChainNode;
  private NodeContract nodeContract;

  @BeforeEach
  void setUp() {
    this.attackChainNode = AttackChainNodeFixture.getDefaultAttackChainNode();
    this.nodeContract = mock(NodeContract.class);
    attackChainNode.setNodeContract(nodeContract);
    handler.mapper = mapper;
  }

  @Test
  @DisplayName("Should return empty if status is not SUCCESS or action is not COMPLETE")
  void shouldReturnEmptyWhenStatusNotSuccessOrActionNotComplete() throws Exception {
    // Case 1: Status is ERROR
    AttackChainNodeExecutionInput inputError =
        buildInput(ExecutionTraceStatus.ERROR, AttackChainNodeExecutionAction.complete, "{}");
    assertTrue(
        handler
            .processContext(new ExecutionProcessingContext(attackChainNode, null, inputError, Map.of()))
            .isEmpty());

    verifyNoInteractions(outputProcessorFactory);

    // Case 2: Action is NOT complete
    AttackChainNodeExecutionInput inputWrongAction =
        buildInput(ExecutionTraceStatus.SUCCESS, AttackChainNodeExecutionAction.command_execution, "{}");
    assertTrue(
        handler
            .processContext(
                new ExecutionProcessingContext(attackChainNode, null, inputWrongAction, Map.of()))
            .isEmpty());

    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return empty when outputStructured is null")
  void shouldReturnEmptyWhenOutputStructuredIsNull() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx(null);

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isEmpty());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return empty when outputStructured is blank")
  void shouldReturnEmptyWhenOutputStructuredIsBlank() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("   ");

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isEmpty());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return empty and log warning when outputStructured is invalid JSON")
  void shouldReturnEmptyWhenOutputStructuredIsInvalidJson() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("not-valid-json{{{");

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isEmpty());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should return present result and skip processor when key is missing from JSON")
  void shouldSkipProcessorWhenKeyIsMissing() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("{\"unrelated_key\": \"value\"}");

    NodeContractContentOutputElement element = new NodeContractContentOutputElement();
    element.setField("missing_key");
    element.setFindingCompatible(true);

    when(nodeContract.getConvertedContent()).thenReturn(mapper.createObjectNode());
    when(nodeContractContentUtils.getContractOutputs(any(), any()))
        .thenReturn(List.of(element));

    handler.processContext(ctx);

    verify(outputProcessorFactory).getProcessor(any());
    verify(mockProcessor, never()).process(any(), any(), any());
  }

  @Test
  @DisplayName("Should skip dispatch when contract outputs list is empty")
  void shouldSkipDispatchWhenContractOutputsAreEmpty() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("{\"some_key\": \"value\"}");

    when(nodeContract.getConvertedContent()).thenReturn(mapper.createObjectNode());
    when(nodeContractContentUtils.getContractOutputs(any(), any())).thenReturn(List.of());

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isPresent());
    verifyNoInteractions(outputProcessorFactory);
  }

  @Test
  @DisplayName("Should skip processor when no processor is registered for the output type")
  void shouldSkipWhenNoProcessorRegisteredForType() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("{\"cve-field\": [\"CVE-2024-1234\"]}");

    NodeContractContentOutputElement element = new NodeContractContentOutputElement();
    element.setField("cve-field");
    element.setType(ContractOutputType.CVE);
    element.setFindingCompatible(true);

    when(nodeContract.getConvertedContent()).thenReturn(mapper.createObjectNode());
    when(nodeContractContentUtils.getContractOutputs(any(), any()))
        .thenReturn(List.of(element));
    when(outputProcessorFactory.getProcessor(ContractOutputType.CVE)).thenReturn(Optional.empty());

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isPresent());
    verify(mockProcessor, never()).process(any(), any(), any());
  }

  @Test
  @DisplayName(
      "Should dispatch to processor with the correct node value from the structured output")
  void shouldDispatchToProcessorWithCorrectNodeValue() throws Exception {
    ExecutionProcessingContext ctx = createValidCtx("{\"cve-field\": \"CVE-2024-9999\"}");

    NodeContractContentOutputElement element = new NodeContractContentOutputElement();
    element.setField("cve-field");
    element.setType(ContractOutputType.CVE);
    element.setFindingCompatible(true);

    when(nodeContract.getConvertedContent()).thenReturn(mapper.createObjectNode());
    when(nodeContractContentUtils.getContractOutputs(any(), any()))
        .thenReturn(List.of(element));
    when(outputProcessorFactory.getProcessor(ContractOutputType.CVE))
        .thenReturn(Optional.of(mockProcessor));

    Optional<ObjectNode> result = handler.processContext(ctx);

    assertTrue(result.isPresent());
    verify(mockProcessor, times(1))
        .process(
            eq(ctx),
            argThat(c -> "cve-field".equals(c.key()) && ContractOutputType.CVE.equals(c.type())),
            argThat(node -> "CVE-2024-9999".equals(node.asText())));
  }

  private ExecutionProcessingContext createValidCtx(String json) {
    return new ExecutionProcessingContext(
        attackChainNode,
        null,
        buildInput(ExecutionTraceStatus.SUCCESS, AttackChainNodeExecutionAction.complete, json),
        Map.of());
  }

  private AttackChainNodeExecutionInput buildInput(
      ExecutionTraceStatus status, AttackChainNodeExecutionAction action, String jsonContent) {
    AttackChainNodeExecutionInput input = new AttackChainNodeExecutionInput();
    input.setStatus(status.toString());
    input.setAction(action);
    input.setOutputStructured(jsonContent);
    return input;
  }
}
