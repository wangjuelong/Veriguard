package io.veriguard.rest.inject.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.database.model.NodeContract;
import io.veriguard.injector_contract.outputs.NodeContractContentOutputElement;
import io.veriguard.output_processor.OutputProcessorFactory;
import io.veriguard.rest.injector_contract.NodeContractContentUtils;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for processing attackChainNode executions triggered by an nodeExecutor (not an agent).
 *
 * <p>This handler generates structured output from the raw execution input and processes additional
 * capabilities such as findings extraction, expectation matching, or asset creation if applicable.
 */
@Slf4j
@Component
public class NodeExecutorExecutionProcessingHandler extends AbstractExecutionProcessingHandler {

  @Resource protected ObjectMapper mapper;
  private final NodeContractContentUtils nodeContractContentUtils;

  public NodeExecutorExecutionProcessingHandler(
      OutputProcessorFactory outputProcessorFactory,
      NodeContractContentUtils nodeContractContentUtils) {
    super(outputProcessorFactory);
    this.nodeContractContentUtils = nodeContractContentUtils;
  }

  /**
   * Processes the execution context, generating structured output and handling additional
   * capabilities such as findings extraction, expectation matching, or asset creation.
   *
   * @param executionContext the execution context to process
   * @return an optional ObjectNode result, if processing produces output
   * @throws JsonProcessingException if JSON serialization fails during processing
   */
  public Optional<ObjectNode> processContext(ExecutionProcessingContext executionContext)
      throws JsonProcessingException {
    if (!executionContext.isSuccess()
        || !ExecutionTraceAction.COMPLETE.equals(executionContext.getAction())) {
      return Optional.empty();
    }

    String outputStructured = executionContext.input().getOutputStructured();
    if (outputStructured == null || outputStructured.isBlank()) {
      log.debug("No structured output provided; skipping injector execution post-processing.");
      return Optional.empty();
    }

    ObjectNode structuredOutput;
    try {
      structuredOutput = mapper.readValue(outputStructured, ObjectNode.class);
    } catch (JsonProcessingException e) {
      log.warn(
          "Failed to parse structured output as JSON; skipping injector execution post-processing.",
          e);
      return Optional.empty();
    }

    if (structuredOutput == null || structuredOutput.isMissingNode()) {
      return Optional.empty();
    }

    NodeContract nodeContract =
        executionContext.attackChainNode().getNodeContract().orElseThrow();

    List<ContractOutputContext> contractOutputContexts =
        getAllContractOutputs(nodeContract).stream().map(ContractOutputContext::from).toList();
    dispatchToProcessors(executionContext, contractOutputContexts, structuredOutput);

    return Optional.of(structuredOutput);
  }

  /**
   * Retrieves all contract output elements from the nodeExecutor contract.
   *
   * @param nodeContract the nodeExecutor contract to inspect
   * @return list of contract output elements
   */
  private List<NodeContractContentOutputElement> getAllContractOutputs(
      NodeContract nodeContract) {
    return nodeContractContentUtils
        .getContractOutputs(nodeContract.getConvertedContent(), mapper)
        .stream()
        .toList();
  }
}
