package io.veriguard.utils;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.ExecutionTrace;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.database.model.ExecutionTraceStatus;
import io.veriguard.database.model.AttackChainNodeStatus;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionAction;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for execution trace conversions.
 *
 * <p>Provides methods for converting between different execution status and action enumerations
 * used in attackChainNode execution tracking.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.veriguard.database.model.ExecutionTraceStatus
 * @see io.veriguard.database.model.ExecutionTraceAction
 */
public class ExecutionTraceUtils {

  private ExecutionTraceUtils() {}

  // -- CONVERSION --

  /**
   * Converts an attackChainNode execution action to its corresponding trace action.
   *
   * @param action the attackChainNode execution action to convert
   * @return the corresponding execution trace action, defaults to EXECUTION for unmapped actions
   */
  public static ExecutionTraceAction convertExecutionAction(AttackChainNodeExecutionAction action) {
    return switch (action) {
      case prerequisite_check -> ExecutionTraceAction.PREREQUISITE_CHECK;
      case prerequisite_execution -> ExecutionTraceAction.PREREQUISITE_EXECUTION;
      case cleanup_execution -> ExecutionTraceAction.CLEANUP_EXECUTION;
      case complete -> ExecutionTraceAction.COMPLETE;
      default -> ExecutionTraceAction.EXECUTION;
    };
  }

  // -- QUERY --

  /** Returns the IDs of agents that already have a COMPLETE trace. */
  public static Set<String> getCompletedAgentIds(List<ExecutionTrace> traces) {
    return traces.stream()
        .filter(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
        .filter(t -> t.getAgent() != null)
        .map(t -> t.getAgent().getId())
        .collect(Collectors.toSet());
  }

  // -- TRACE BUILDERS --

  /**
   * Adds a COMPLETE/TIMEOUT trace to the given attackChainNode status for an agent that did not respond
   * within the configured threshold.
   */
  public static void addTimeoutTrace(AttackChainNodeStatus status, Agent agent, int thresholdMinutes) {
    status.addTrace(
        ExecutionTraceStatus.TIMEOUT,
        "Agent "
            + agent.getExecutedByUser()
            + " did not respond within the "
            + thresholdMinutes
            + " minutes threshold.",
        ExecutionTraceAction.COMPLETE,
        agent);
  }

  /**
   * Adds a START/INFO trace to the given attackChainNode status indicating that an implant was spawned by
   * the agent.
   */
  public static void addJobRetrievalTrace(AttackChainNodeStatus status, Agent agent) {
    status.addTrace(
        ExecutionTraceStatus.INFO, "Implant spawn by the agent", ExecutionTraceAction.START, agent);
  }

  // -- STATUS COMPUTATION --

  /**
   * Compute the aggregated status from all traces of a single agent. The logic is:
   *
   * <ul>
   *   <li>Non-cleanup errors take priority → return the granular error status
   *   <li>Prerequisite failed → PREREQUISITE_FAILED
   *   <li>If execution succeeded but cleanup failed → SUCCESS_WITH_CLEANUP_FAIL
   *   <li>If all succeeded → SUCCESS
   * </ul>
   */
  public static ExecutionTraceStatus computeAgentTraceStatus(List<ExecutionTrace> agentTraces) {
    boolean hasSuccess = false;
    boolean hasCleanupError = false;
    boolean hasPrerequisiteError = false;
    Set<ExecutionTraceStatus> executionErrors =
        java.util.EnumSet.noneOf(ExecutionTraceStatus.class);

    for (ExecutionTrace trace : agentTraces) {
      ExecutionTraceStatus status = trace.getStatus();
      if (status.isSuccess()) {
        hasSuccess = true;
      } else if (status.isError()) {
        switch (trace.getAction()) {
          case CLEANUP_EXECUTION -> hasCleanupError = true;
          case PREREQUISITE_EXECUTION, PREREQUISITE_CHECK -> hasPrerequisiteError = true;
          default -> executionErrors.add(status);
        }
      }
    }

    // Non-cleanup, non-prerequisite error takes priority
    if (!executionErrors.isEmpty()) {
      return executionErrors.size() == 1
          ? executionErrors.iterator().next()
          : ExecutionTraceStatus.ERROR;
    }
    if (hasPrerequisiteError) {
      return ExecutionTraceStatus.PREREQUISITE_FAILED;
    }
    if (hasSuccess && hasCleanupError) {
      return ExecutionTraceStatus.SUCCESS_WITH_CLEANUP_FAIL;
    }
    if (hasSuccess) {
      return ExecutionTraceStatus.SUCCESS;
    }
    return null;
  }
}
