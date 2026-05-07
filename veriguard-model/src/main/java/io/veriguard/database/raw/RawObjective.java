package io.veriguard.database.raw;

/**
 * Spring Data projection interface for objective data.
 *
 * <p>This interface defines a lightweight projection for retrieving objective identifiers and their
 * attackChainRun associations. Objectives represent goals to be achieved during attackChainRun execution.
 *
 * @see io.veriguard.database.model.Objective
 */
public interface RawObjective {

  /**
   * Returns the unique identifier of the objective.
   *
   * @return the objective ID
   */
  String getObjective_id();

  /**
   * Returns the ID of the attackChainRun this objective belongs to.
   *
   * @return the attackChainRun ID
   */
  String getObjective_attackChainRun();
}
