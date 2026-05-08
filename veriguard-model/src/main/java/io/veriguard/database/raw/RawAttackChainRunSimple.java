package io.veriguard.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for simplified attackChainRun data.
 *
 * <p>This interface defines a lightweight projection for retrieving essential attackChainRun
 * information without loading full entity relationships. Used for list views and summary displays.
 *
 * @see io.veriguard.database.model.AttackChainRun
 * @see RawSimulation
 */
public interface RawAttackChainRunSimple {

  /**
   * Returns the unique identifier of the attackChainRun.
   *
   * @return the attackChainRun ID
   */
  String getExercise_id();

  /**
   * Returns the current status of the attackChainRun.
   *
   * @return the status (e.g., "SCHEDULED", "RUNNING", "FINISHED")
   */
  String getExercise_status();

  /**
   * Returns the scheduled start date of the attackChainRun.
   *
   * @return the start date
   */
  Instant getExercise_start_date();

  /**
   * Returns the creation timestamp of the attackChainRun.
   *
   * @return the creation timestamp
   */
  Instant getExercise_created_at();

  /**
   * Returns the last update timestamp of the attackChainRun.
   *
   * @return the update timestamp
   */
  Instant getExercise_updated_at();

  /**
   * Returns the end date of the attackChainRun.
   *
   * @return the end date, or {@code null} if not yet finished
   */
  Instant getExercise_end_date();

  /**
   * Returns the display name of the attackChainRun.
   *
   * @return the attackChainRun name
   */
  String getExercise_name();

  /**
   * Returns the category of the attackChainRun.
   *
   * @return the category name
   */
  String getExercise_category();

  /**
   * Returns the subtitle of the attackChainRun.
   *
   * @return the attackChainRun subtitle
   */
  String getExercise_subtitle();

  /**
   * Returns the set of tag IDs associated with this attackChainRun.
   *
   * @return set of tag IDs
   */
  Set<String> getExercise_tags();

  /**
   * Returns the set of attackChainNode IDs in this attackChainRun.
   *
   * @return set of attackChainNode IDs
   */
  Set<String> getInject_ids();
}
