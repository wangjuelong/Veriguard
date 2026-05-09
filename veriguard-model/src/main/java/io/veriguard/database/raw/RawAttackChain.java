package io.veriguard.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for attackChain data.
 *
 * <p>This interface defines a comprehensive projection for retrieving attackChain information
 * including configuration, scheduling, messaging settings, and associated relationships such as
 * attackChainRuns, tags, and platforms.
 *
 * @see io.veriguard.database.model.AttackChain
 */
public interface RawAttackChain {

  /**
   * Returns the unique identifier of the attackChain.
   *
   * @return the attackChain ID
   */
  String getAttack_chain_id();

  /**
   * Returns the display name of the attackChain.
   *
   * @return the attackChain name
   */
  String getAttack_chain_name();

  /**
   * Returns the category classification of the attackChain.
   *
   * @return the category name, or {@code null} if not categorized
   */
  String getAttack_chain_category();

  /**
   * Returns the creation timestamp of the attackChain.
   *
   * @return the creation timestamp
   */
  Instant getAttack_chain_created_at();

  /**
   * Returns the last update timestamp of the attackChain.
   *
   * @return the update timestamp
   */
  Instant getAttack_chain_updated_at();

  /**
   * Returns the ID of the custom dashboard associated with this attackChain.
   *
   * @return the custom dashboard ID, or {@code null} if using default dashboard
   */
  String getAttack_chain_custom_dashboard();

  /**
   * Returns the detailed description of the attackChain.
   *
   * @return the attackChain description
   */
  String getAttack_chain_description();

  /**
   * Returns the external URL reference for this attackChain.
   *
   * @return the external URL, or {@code null} if not set
   */
  String getAttack_chain_external_url();

  /**
   * Returns whether lessons learned responses should be anonymized.
   *
   * @return {@code true} if lessons are anonymized, {@code false} otherwise
   */
  boolean getAttack_chain_lessons_anonymized();

  /**
   * Returns the email address used as the sender for attackChain communications.
   *
   * @return the "from" email address
   */
  String getAttack_chain_mail_from();

  /**
   * Returns the main focus area of the attackChain.
   *
   * @return the main focus (e.g., "incident-response", "endpoint-protection")
   */
  String getAttack_chain_main_focus();

  /**
   * Returns the footer text for attackChain messages.
   *
   * @return the message footer
   */
  String getAttack_chain_message_footer();

  /**
   * Returns the header text for attackChain messages.
   *
   * @return the message header
   */
  String getAttack_chain_message_header();

  /**
   * Returns the cron expression for attackChain recurrence scheduling.
   *
   * @return the recurrence cron expression, or {@code null} if not recurring
   */
  String getAttack_chain_recurrence();

  /**
   * Returns the start timestamp for recurrence scheduling.
   *
   * @return the recurrence start timestamp, or {@code null} if not set
   */
  Instant getAttack_chain_recurrence_start();

  /**
   * Returns the end timestamp for recurrence scheduling.
   *
   * @return the recurrence end timestamp, or {@code null} if open-ended
   */
  Instant getAttack_chain_recurrence_end();

  /**
   * Returns the subtitle of the attackChain.
   *
   * @return the attackChain subtitle
   */
  String getAttack_chain_subtitle();

  /**
   * Returns the set of dependency identifiers for this attackChain.
   *
   * @return set of dependency identifiers (e.g., "STARTERPACK")
   */
  Set<String> getAttack_chain_dependencies();

  /**
   * Returns the severity level of the attackChain.
   *
   * @return the severity level (e.g., "low", "medium", "high", "critical")
   */
  String getAttack_chain_severity();

  String getAttack_chain_type_affinity();

  /**
   * Returns the set of attackChainRun IDs created from this attackChain.
   *
   * @return set of attackChainRun IDs
   */
  Set<String> getAttack_chain_attackChainRuns();

  /**
   * Returns the kill chain phases as a serialized string.
   *
   * @return the kill chain phases data
   */
  String getAttack_chain_kill_chain_phases();

  /**
   * Returns the set of platforms targeted by this attackChain.
   *
   * @return set of platform types (e.g., "Linux", "Windows", "macOS")
   */
  Set<String> getAttack_chain_platforms();

  /**
   * Returns the set of tag IDs associated with this attackChain.
   *
   * @return set of tag IDs
   */
  Set<String> getAttack_chain_tags();

  /**
   * Returns the team-user associations as a serialized string.
   *
   * @return the team users data
   */
  String getAttack_chain_teams_users();

  /**
   * Returns the count of users specifically assigned to this attackChain.
   *
   * @return the count of directly assigned users
   */
  Long getAttack_chain_users_number();

  /**
   * Returns the total count of users across all teams in this attackChain.
   *
   * @return the total user count
   */
  Long getAttack_chain_all_users_number();
}
