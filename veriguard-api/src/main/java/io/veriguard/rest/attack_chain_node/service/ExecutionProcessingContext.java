package io.veriguard.rest.attack_chain_node.service;

import static io.veriguard.utils.ExecutionTraceUtils.convertExecutionAction;

import io.veriguard.database.model.*;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionInput;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * Context object for processing an attackChainNode execution.
 *
 * <p>Holds references to the attackChainNode, agent, input, and targeted assets map. Provides
 * utility methods to determine execution status and type.
 */
public record ExecutionProcessingContext(
    AttackChainNode attackChainNode,
    @Nullable Agent agent,
    AttackChainNodeExecutionInput input,
    Map<String, Endpoint> valueTargetedAssetsMap) {

  /** Returns true if the execution status is SUCCESS. */
  public boolean isSuccess() {
    return ExecutionTraceStatus.SUCCESS.toString().equals(input.getStatus());
  }

  /** Returns true if the execution is for an nodeExecutor (not agent). */
  public boolean isNodeExecutorExecution() {
    return !isAgentExecution();
  }

  /** Returns true if the execution is for an agent. */
  public boolean isAgentExecution() {
    return agent != null;
  }

  /** Returns the execution action for this context. */
  public ExecutionTraceAction getAction() {
    return convertExecutionAction(input.getAction());
  }
}
