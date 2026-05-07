package io.veriguard.utils;

/**
 * Enumeration of filter options for attackChainNode queries.
 *
 * <p>Defines the scope of attackChainNodes to include when searching or filtering attackChainNode data.
 *
 * @see io.veriguard.database.model.AttackChainNode
 */
public enum InputFilterOptions {

  /** Include all attackChainNodes regardless of their context. */
  ALL_INJECTS,

  /** Include only attackChainNodes associated with simulations or attackChains. */
  SIMULATION_OR_SCENARIO,

  /** Include only attackChainNodes used in atomic testing (individual technique validation). */
  ATOMIC_TESTING,
}
