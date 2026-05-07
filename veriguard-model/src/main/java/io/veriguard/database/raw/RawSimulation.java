package io.veriguard.database.raw;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Spring Data projection interface for simulation (attackChainRun) data.
 *
 * <p>This interface defines a comprehensive projection for retrieving attackChainRun information
 * including status, scheduling, messaging configuration, and all associated relationships. A
 * simulation represents an active or completed attackChainRun instance.
 *
 * @see io.veriguard.database.model.AttackChainRun
 */
public interface RawSimulation {

  /**
   * Returns the unique identifier of the attackChainRun.
   *
   * @return the attackChainRun ID
   */
  String getExercise_id();

  /**
   * Returns the display name of the attackChainRun.
   *
   * @return the attackChainRun name
   */
  String getExercise_name();

  /**
   * Returns the description of the attackChainRun.
   *
   * @return the attackChainRun description
   */
  String getExercise_description();

  /**
   * Returns the current status of the attackChainRun.
   *
   * @return the status (e.g., "SCHEDULED", "RUNNING", "PAUSED", "FINISHED", "CANCELED")
   */
  String getExercise_status();

  /**
   * Returns the subtitle of the attackChainRun.
   *
   * @return the attackChainRun subtitle
   */
  String getExercise_subtitle();

  /**
   * Returns the category of the attackChainRun.
   *
   * @return the category name
   */
  String getExercise_category();

  /**
   * Returns the main focus area of the attackChainRun.
   *
   * @return the main focus
   */
  String getExercise_main_focus();

  /**
   * Returns the severity level of the attackChainRun.
   *
   * @return the severity level
   */
  String getExercise_severity();

  /**
   * Returns the scheduled start date of the attackChainRun.
   *
   * @return the start date
   */
  Instant getExercise_start_date();

  /**
   * Returns the end date of the attackChainRun.
   *
   * @return the end date, or {@code null} if not yet finished
   */
  Instant getExercise_end_date();

  /**
   * Returns the header text for attackChainRun messages.
   *
   * @return the message header
   */
  String getExercise_message_header();

  /**
   * Returns the footer text for attackChainRun messages.
   *
   * @return the message footer
   */
  String getExercise_message_footer();

  /**
   * Returns the email address used as sender for attackChainRun communications.
   *
   * @return the "from" email address
   */
  String getExercise_mail_from();

  /**
   * Returns whether lessons learned responses are anonymized.
   *
   * @return {@code true} if lessons are anonymized, {@code false} otherwise
   */
  boolean getExercise_lessons_anonymized();

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
   * Returns the timestamp when attackChainNodes were last modified.
   *
   * @return the attackChainNodes update timestamp
   */
  Instant getExercise_injects_updated_at();

  /**
   * Returns the ID of the attackChain this attackChainRun was created from.
   *
   * @return the attackChain ID, or {@code null} if created independently
   */
  String getScenario_id();

  /**
   * Returns the ID of the custom dashboard for this attackChainRun.
   *
   * @return the custom dashboard ID, or {@code null} if using default
   */
  String getExercise_custom_dashboard();

  /**
   * Returns the set of reply-to email addresses for attackChainRun communications.
   *
   * @return set of reply-to email addresses
   */
  Set<String> getExercise_reply_to();

  /**
   * Returns the set of tag IDs associated with this attackChainRun.
   *
   * @return set of tag IDs
   */
  Set<String> getExercise_tags();

  /**
   * Returns the set of asset IDs targeted by this attackChainRun.
   *
   * @return set of asset IDs
   */
  Set<String> getExercise_assets();

  /**
   * Returns the set of asset group IDs targeted by this attackChainRun.
   *
   * @return set of asset group IDs
   */
  Set<String> getExercise_asset_groups();

  /**
   * Returns the set of team IDs participating in this attackChainRun.
   *
   * @return set of team IDs
   */
  Set<String> getExercise_teams();

  /**
   * Returns the set of user IDs participating in this attackChainRun.
   *
   * @return set of user IDs
   */
  Set<String> getExercise_users();

  /**
   * Returns the set of platforms targeted by this attackChainRun.
   *
   * @return set of platform types
   */
  Set<String> getExercise_platforms();

  /**
   * Returns the set of lessons answer IDs collected from this attackChainRun.
   *
   * @return set of lessons answer IDs
   */
  Set<String> getLessons_answers();

  /**
   * Returns the set of log entry IDs for this attackChainRun.
   *
   * @return set of log IDs
   */
  Set<String> getLogs();

  /**
   * Returns the list of attackChainNode IDs in this attackChainRun.
   *
   * @return list of attackChainNode IDs
   */
  List<String> getInject_ids();
}
