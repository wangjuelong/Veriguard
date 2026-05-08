package io.veriguard.database.raw;

/**
 * Spring Data projection interface for agent data.
 *
 * <p>This interface defines a lightweight projection for retrieving essential agent information
 * without loading the complete entity. Used primarily for performance-optimized queries where only
 * basic agent identification and execution context is needed.
 *
 * @see io.veriguard.database.model.Agent
 */
public interface RawAgent {

  /**
   * Returns the unique identifier of the agent.
   *
   * @return the agent ID
   */
  String getAgent_id();

  /**
   * Returns the identifier of the user context under which the agent is executed.
   *
   * @return the executed-by user identifier
   */
  String getAgent_executed_by_user();

  /**
   * Returns the type of executor managing this agent.
   *
   * @return the executor type (e.g., "veriguard_caldera_executor", "veriguard_agent")
   */
  String getExecutor_type();
}
