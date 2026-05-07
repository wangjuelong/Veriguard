package io.veriguard.rest.inject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.aop.LogExecutionTime;
import io.veriguard.database.model.Agent;
import io.veriguard.database.model.ExecutionStatus;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.repository.AgentRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.queue.BatchQueueService;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionAction;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionCallback;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional
public class BatchingAttackChainNodeStatusService {

  private static final int MAX_RETRIES = 5;
  // Inmemory queue system to add delay to actual re-queuing mechanism
  private final Queue<AttackChainNodeExecutionCallback> callbacksToRequeue = new ConcurrentLinkedQueue<>();

  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AgentRepository agentRepository;
  private final StructuredOutputUtils structuredOutputUtils;
  private final AttackChainNodeExecutionService attackChainNodeExecutionService;

  // Set from AttackChainNodeApi.init() function. I preferred that to creating a dedicated @Bean instance
  // to avoid making too big changes.
  // Also, it can be null when the attackChainNode-trace queue is not configured (e.g. legacy mode, tests).
  @Setter private BatchQueueService<AttackChainNodeExecutionCallback> attackChainNodeTraceQueueService;

  @Resource protected ObjectMapper mapper;

  /**
   * Handle the list of attackChainNode execution callbacks
   *
   * @param attackChainNodeExecutionCallbacks the attackChainNode execution callbacks
   */
  @LogExecutionTime
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public List<AttackChainNodeExecutionCallback> handleAttackChainNodeExecutionCallback(
      List<AttackChainNodeExecutionCallback> attackChainNodeExecutionCallbacks) {

    List<AttackChainNodeExecutionCallback> successfullyProcessedCallbacks = new ArrayList<>();

    // Getting all the attackChainNodes linked to the list of execution traces, all at once
    Map<String, AttackChainNode> mapAttackChainNodesById =
        attackChainNodeRepository
            .findAllByIdWithExpectations(
                attackChainNodeExecutionCallbacks.stream()
                    .map(AttackChainNodeExecutionCallback::getAttackChainNodeId)
                    .toList())
            .stream()
            .collect(Collectors.toMap(AttackChainNode::getId, Function.identity()));

    // Getting all the agents linked to the list of execution traces, all at once
    Map<String, Agent> mapAgentsById =
        StreamSupport.stream(
                agentRepository
                    .findAllById(
                        attackChainNodeExecutionCallbacks.stream()
                            .map(AttackChainNodeExecutionCallback::getAgentId)
                            .toList())
                    .spliterator(),
                false)
            .collect(Collectors.toMap(Agent::getId, Function.identity()));

    // Sorting the attackChainNode execution callbacks to make sure we handle them in chronological order
    Stream<AttackChainNodeExecutionCallback> sortedAttackChainNodeExecutionCallbacks =
        attackChainNodeExecutionCallbacks.stream()
            .sorted(Comparator.comparing(AttackChainNodeExecutionCallback::getEmissionDate));

    // For each of the callback
    sortedAttackChainNodeExecutionCallbacks.forEach(
        callback -> {
          AttackChainNode attackChainNode = null;

          try {
            // Get the attackChainNode or throw if not found
            attackChainNode =
                Optional.ofNullable(mapAttackChainNodesById.get(callback.getAttackChainNodeId()))
                    .orElseThrow(
                        () ->
                            new ElementNotFoundException(
                                "Inject not found: " + callback.getAttackChainNodeId()));
            // issue/3550: added this condition to ensure we only update statuses if the attackChainNode is
            // in a coherent state.
            // This prevents issues where the PENDING status took more time to persist than it took
            // for the agent to send the complete action.
            // FIXME: At the moment, this whole function is only called by execution traces created
            // form our implants.
            // These implants are launched with the async value to true, which force the implant to
            // go from EXECUTING to PENDING, before going to EXECUTED.
            // So if in the future, this function is called to update a synchronous attackChainNode, we will
            // need to find a way to get the async boolean somehow and add it to this condition.
            if (callback
                    .getAttackChainNodeExecutionInput()
                    .getAction()
                    .equals(AttackChainNodeExecutionAction.complete)
                && (attackChainNode.getStatus().isEmpty()
                    || !attackChainNode.getStatus().get().getName().equals(ExecutionStatus.PENDING))) {
              // If we receive a status update with a terminal state status, we must first check
              // that the current status is in the PENDING state
              log.warn(
                  String.format(
                      "Received a complete action for inject %s with status %s, but current status is not PENDING (retry %d/%d)",
                      callback.getAttackChainNodeId(),
                      attackChainNode.getStatus().map(is -> is.getName().toString()).orElse("unknown"),
                      callback.getRetryCount(),
                      MAX_RETRIES));
              if (callback.getRetryCount() < MAX_RETRIES && attackChainNodeTraceQueueService != null) {
                callback.setRetryCount(callback.getRetryCount() + 1);
                // We change the emission date to current timestamp here to be more accurate
                // order has become meaningless in case of re-queueing the message any way
                callback.setEmissionDate(Instant.now().toEpochMilli());
                callbacksToRequeue.add(callback);
              } else {
                if (callback.getRetryCount() < MAX_RETRIES) {
                  log.warn(
                      "Inject trace queue service is not configured, saving trace directly for inject {}",
                      callback.getAttackChainNodeId());
                } else {
                  log.warn("Max retries reached for inject {}", callback.getAttackChainNodeId());
                }
                // Max retry reached, we save the trace anyway, to make sure no information is lost
                // and let the expiration manager logic handle the discrepancies if any exists
                saveExecutionTrace(callback, mapAgentsById, attackChainNode, successfullyProcessedCallbacks);
              }
            } else {
              saveExecutionTrace(callback, mapAgentsById, attackChainNode, successfullyProcessedCallbacks);
            }
          } catch (ElementNotFoundException e) {
            attackChainNodeExecutionService.handleAttackChainNodeExecutionError(attackChainNode, e);
            successfullyProcessedCallbacks.add(callback);
          } catch (Exception e) {
            log.warn(
                "The was a problem processing the element for the inject {} and agent {}",
                callback.getAttackChainNodeId(),
                callback.getAgentId(),
                e);
          }
        });
    return successfullyProcessedCallbacks;
  }

  /**
   * Save the execution trace and compute the attackChainNode status
   *
   * @param callback the execution trace message to handle
   * @param mapAgentsById map of agents linked to the execution traces
   * @param attackChainNode the attackChainNode linked to the given execution trace
   * @param successfullyProcessedCallbacks list of successfully processed execution trace messages
   */
  private void saveExecutionTrace(
      AttackChainNodeExecutionCallback callback,
      Map<String, Agent> mapAgentsById,
      AttackChainNode attackChainNode,
      List<AttackChainNodeExecutionCallback> successfullyProcessedCallbacks) {
    // Get the nullable agent; throw only if ID was supplied and not found
    Agent agent =
        Optional.ofNullable(callback.getAgentId())
            .map(
                id ->
                    Optional.ofNullable(mapAgentsById.get(callback.getAgentId()))
                        .orElseThrow(
                            () ->
                                new ElementNotFoundException(
                                    "Agent not found: " + callback.getAgentId())))
            .orElse(null);

    // Process the execution trace
    if (agent == null) {
      attackChainNodeExecutionService.processAttackChainNodeExecutionWithNodeExecutor(
          attackChainNode, callback.getAttackChainNodeExecutionInput());
    } else {
      attackChainNodeExecutionService.processAttackChainNodeExecutionWithAgent(
          attackChainNode, agent, callback.getAttackChainNodeExecutionInput());
    }
    successfullyProcessedCallbacks.add(callback);
  }

  /**
   * Requeue all callbacks that were received too soon compared to the attackChainNode status This is called
   * from a quartz job
   */
  public void requeueCallbacks() throws IOException {
    if (attackChainNodeTraceQueueService == null) {
      return;
    }
    AttackChainNodeExecutionCallback callback;
    while ((callback = callbacksToRequeue.peek()) != null) {
      try {
        attackChainNodeTraceQueueService.publish(callback);
        callbacksToRequeue.poll();
      } catch (Exception e) {
        log.warn("Unable to requeue inject execution callback, keeping it in memory for retry", e);
        break;
      }
    }
  }
}
