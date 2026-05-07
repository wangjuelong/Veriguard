package io.veriguard.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for simplified attackChain data.
 *
 * <p>This interface defines a lightweight projection for retrieving essential attackChain information
 * without loading full entity relationships. Used for list views and summary displays.
 *
 * @see io.veriguard.database.model.AttackChain
 * @see RawAttackChain
 */
public interface RawAttackChainSimple {

  /**
   * Returns the unique identifier of the attackChain.
   *
   * @return the attackChain ID
   */
  String getScenario_id();

  /**
   * Returns the display name of the attackChain.
   *
   * @return the attackChain name
   */
  String getScenario_name();

  /**
   * Returns the cron expression for attackChain recurrence.
   *
   * @return the recurrence cron expression, or {@code null} if not recurring
   */
  String getScenario_recurrence();

  /**
   * Returns the creation timestamp of the attackChain.
   *
   * @return the creation timestamp
   */
  Instant getScenario_created_at();

  /**
   * Returns the timestamp when attackChainNodes were last modified.
   *
   * @return the attackChainNodes update timestamp
   */
  Instant getScenario_injects_updated_at();

  /**
   * Returns the subtitle of the attackChain.
   *
   * @return the attackChain subtitle
   */
  String getScenario_subtitle();

  /**
   * Returns the set of tag IDs associated with this attackChain.
   *
   * @return set of tag IDs
   */
  Set<String> getScenario_tags();

  /**
   * Returns the set of asset IDs targeted by this attackChain.
   *
   * @return set of asset IDs
   */
  Set<String> getScenario_assets();

  /**
   * Returns the set of asset group IDs targeted by this attackChain.
   *
   * @return set of asset group IDs
   */
  Set<String> getScenario_asset_groups();

  /**
   * Returns the set of team IDs participating in this attackChain.
   *
   * @return set of team IDs
   */
  Set<String> getScenario_teams();

  /**
   * Returns the set of attackChainNode IDs in this attackChain.
   *
   * @return set of attackChainNode IDs
   */
  Set<String> getScenario_attackChainNodes();

  /**
   * Returns the set of platforms targeted by this attackChain.
   *
   * @return set of platform types
   */
  Set<String> getScenario_platforms();
}
