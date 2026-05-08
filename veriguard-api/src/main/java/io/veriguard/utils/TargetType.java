package io.veriguard.utils;

/**
 * Enumeration representing the different types of targets that can be used for attackChainNode
 * execution.
 *
 * <p>Targets determine who or what receives the attackChainNode during simulation attackChainRuns
 * or atomic testing. Each target type represents a specific category of entities in the Veriguard
 * platform.
 *
 * @see io.veriguard.database.model.AttackChainNode
 * @see io.veriguard.database.model.NodeContract
 */
public enum TargetType {

  /** A single agent entity. */
  AGENT,

  /** Multiple agent entities. */
  AGENTS,

  /** Asset entities (endpoints, systems, or devices). */
  ASSETS,

  /** Groups of assets organized for collective targeting. */
  ASSETS_GROUPS,

  /** Individual players participating in attackChainRuns. */
  PLAYERS,

  /** Teams of players organized for collective participation. */
  TEAMS,

  /** Endpoint devices in the infrastructure. */
  ENDPOINTS
}
