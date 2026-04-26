package io.veriguard.utils;

/**
 * Enumeration of filter options for inject queries.
 *
 * <p>Defines the scope of injects to include when searching or filtering inject data.
 *
 * @see io.veriguard.database.model.Inject
 */
public enum InputFilterOptions {

  /** Include all injects regardless of their context. */
  ALL_INJECTS,

  /** Include only injects associated with simulations or scenarios. */
  SIMULATION_OR_SCENARIO,

  /** Include only injects used in atomic testing (individual technique validation). */
  ATOMIC_TESTING,
}
