package io.veriguard.database.raw;

import java.time.Instant;

/**
 * Spring Data projection interface for attackChainRun pause data.
 *
 * <p>This interface defines a projection for retrieving pause events during attackChainRun execution.
 * Pauses represent interruptions in attackChainRun flow, typically initiated by facilitators.
 *
 * @see io.veriguard.database.model.Pause
 */
public interface RawPause {

  /**
   * Returns the unique identifier of the pause event.
   *
   * @return the pause ID
   */
  String getPause_id();

  /**
   * Returns the ID of the attackChainRun that was paused.
   *
   * @return the attackChainRun ID
   */
  String getPause_attackChainRun();

  /**
   * Returns the timestamp when the pause was initiated.
   *
   * @return the pause start timestamp
   */
  Instant getPause_date();

  /**
   * Returns the duration of the pause in milliseconds.
   *
   * @return the pause duration in milliseconds
   */
  long getPause_duration();
}
