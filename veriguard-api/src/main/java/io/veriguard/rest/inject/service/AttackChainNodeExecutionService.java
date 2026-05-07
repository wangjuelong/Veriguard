package io.veriguard.rest.inject.service;

import static io.veriguard.utils.ExecutionTraceUtils.convertExecutionAction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AgentRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionAction;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionInput;
import io.veriguard.service.AttackChainNodeExpectationService;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class AttackChainNodeExecutionService {

  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final AgentRepository agentRepository;
  private final AttackChainNodeStatusService attackChainNodeStatusService;
  private final AttackChainNodeService attackChainNodeService;

  private final AgentExecutionProcessingHandler agentExecutionProcessingHandler;
  private final NodeExecutorExecutionProcessingHandler nodeExecutorExecutionProcessingHandler;

  @Resource protected ObjectMapper mapper;

  @Transactional
  public void handleAttackChainNodeExecutionCallback(
      String attackChainNodeId, String agentId, AttackChainNodeExecutionInput input) {
    AttackChainNode attackChainNode = null;

    try {
      attackChainNode = loadAttackChainNodeOrThrow(attackChainNodeId);
      // issue/3550: added this condition to ensure we only update statuses if the attackChainNode is in a
      // coherent state.
      // This prevents issues where the PENDING status took more time to persist than it took for
      // the agent to send the complete action.
      // FIXME: At the moment, this whole function is called by our implant and nodeExecutors. These
      // implant are
      // launched with the async value to true, which force the implant to go from EXECUTING to
      // PENDING, before going to EXECUTED.
      // So if in the future, this function is called to update a synchronous attackChainNode, we will need
      // to find a way to get the async boolean somehow and add it to this condition.
      if (AttackChainNodeExecutionAction.complete.equals(input.getAction())
          && (attackChainNode.getStatus().isEmpty()
              || !ExecutionStatus.PENDING.equals(attackChainNode.getStatus().get().getName()))) {
        // If we receive a status update with a terminal state status, we must first check that the
        // current status is in the PENDING state
        log.warn(
            String.format(
                "Received a complete action for inject %s with status %s, but current status is not PENDING",
                attackChainNodeId, attackChainNode.getStatus().map(is -> is.getName().toString()).orElse("unknown")));
        throw new DataIntegrityViolationException(
            "Cannot complete inject that is not in PENDING state");
      }
      Agent agent = loadAgentIfPresent(agentId);
      if (agent == null) {
        processAttackChainNodeExecutionWithNodeExecutor(attackChainNode, input);
      } else {
        processAttackChainNodeExecutionWithAgent(attackChainNode, agent, input);
      }
    } catch (ElementNotFoundException e) {
      handleAttackChainNodeExecutionError(attackChainNode, e);
    }
  }

  public void processAttackChainNodeExecutionWithAgent(
      AttackChainNode attackChainNode, Agent agent, AttackChainNodeExecutionInput input) {
    processAttackChainNodeExecution(attackChainNode, agent, input, agentExecutionProcessingHandler);
  }

  public void processAttackChainNodeExecutionWithNodeExecutor(AttackChainNode attackChainNode, AttackChainNodeExecutionInput input) {
    processAttackChainNodeExecution(attackChainNode, null, input, nodeExecutorExecutionProcessingHandler);
  }

  /**
   * Processes the execution of an attackChainNode by resolving the appropriate handler based on the
   * execution source (nodeExecutor or agent), extracting findings, matching expectations and updating
   * the attackChainNode status.
   *
   * @param attackChainNode the attackChainNode being executed
   * @param agent the agent executing to attackChainNode, or {@code null} if triggered by an nodeExecutor
   * @param input the execution input containing action, status, and output data
   * @throws RuntimeException if the output structured cannot be parsed
   */
  private void processAttackChainNodeExecution(
      AttackChainNode attackChainNode,
      @Nullable Agent agent,
      AttackChainNodeExecutionInput input,
      AbstractExecutionProcessingHandler handler) {
    try {
      Map<String, Endpoint> valueTargetedAssetsMap = attackChainNodeService.getValueTargetedAssetMap(attackChainNode);
      // Build the context encapsulating all execution data and conditions (success, action, source)
      ExecutionProcessingContext executionContext =
          new ExecutionProcessingContext(attackChainNode, agent, input, valueTargetedAssetsMap);
      // Delegate to the appropriate handler (nodeExecutor or agent) to process output execution
      ObjectNode resolvedStructured = handler.processContext(executionContext).orElse(null);

      attackChainNodeStatusService.updateAttackChainNodeStatus(attackChainNode, agent, input, resolvedStructured);
      addEndDateAttackChainNodeExpectationTimeSignatureIfNeeded(attackChainNode, agent, input);

    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to process inject execution for inject", e);
    }
  }

  /**
   * Adds an end date signature to attackChainNode expectations if the action is COMPLETE.
   *
   * @param attackChainNode the attackChainNode for which to add the end date signature
   * @param agent the agent for which to add the end date signature
   * @param input the input containing the action and duration
   */
  @VisibleForTesting
  public void addEndDateAttackChainNodeExpectationTimeSignatureIfNeeded(
      AttackChainNode attackChainNode, Agent agent, AttackChainNodeExecutionInput input) {
    if (agent != null
        && ExecutionTraceAction.COMPLETE.equals(convertExecutionAction(input.getAction()))) {
      AttackChainNodeStatus attackChainNodeStatus = attackChainNode.getStatus().orElseThrow();
      Instant endDate =
          attackChainNodeStatusService.getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
              attackChainNodeStatus, agent.getId(), input.getDuration());
      attackChainNodeExpectationService.addEndDateSignatureToAttackChainNodeExpectationsByAgent(
          attackChainNode.getId(), agent.getId(), endDate);
    }
  }

  private Agent loadAgentIfPresent(String agentId) {
    return (agentId == null)
        ? null
        : agentRepository
            .findById(agentId)
            .orElseThrow(() -> new ElementNotFoundException("Agent not found: " + agentId));
  }

  private AttackChainNode loadAttackChainNodeOrThrow(String attackChainNodeId) {
    return attackChainNodeRepository
        .findById(attackChainNodeId)
        .orElseThrow(() -> new ElementNotFoundException("Inject not found: " + attackChainNodeId));
  }

  public void handleAttackChainNodeExecutionError(AttackChainNode attackChainNode, Exception e) {
    log.error(e.getMessage(), e);
    if (attackChainNode != null) {
      attackChainNode
          .getStatus()
          .ifPresent(
              status -> {
                ExecutionTrace trace =
                    new ExecutionTrace(
                        status,
                        ExecutionTraceStatus.ERROR,
                        null,
                        e.getMessage(),
                        ExecutionTraceAction.COMPLETE,
                        null,
                        Instant.now());
                status.addTrace(trace);
              });
      attackChainNodeRepository.save(attackChainNode);
    }
  }
}
