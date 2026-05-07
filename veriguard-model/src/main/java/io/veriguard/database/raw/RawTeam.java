package io.veriguard.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for team data with relationships.
 *
 * <p>This interface defines a projection for retrieving team information including metadata,
 * organizational context, and associations with users, attackChainRuns, attackChains, and
 * attackChainNodes.
 *
 * @see io.veriguard.database.model.Team
 */
public interface RawTeam {

  /**
   * Returns the unique identifier of the team.
   *
   * @return the team ID
   */
  String getTeam_id();

  /**
   * Returns the display name of the team.
   *
   * @return the team name
   */
  String getTeam_name();

  /**
   * Returns the description of the team.
   *
   * @return the team description, or {@code null} if not set
   */
  String getTeam_description();

  /**
   * Returns the creation timestamp of the team.
   *
   * @return the creation timestamp
   */
  Instant getTeam_created_at();

  /**
   * Returns the last update timestamp of the team.
   *
   * @return the update timestamp
   */
  Instant getTeam_updated_at();

  /**
   * Returns the ID of the organization this team belongs to.
   *
   * @return the organization ID, or {@code null} if not associated with an organization
   */
  String getTeam_organization();

  /**
   * Returns whether this is a contextual team.
   *
   * <p>Contextual teams are created specifically for a particular attackChainRun or attackChain and
   * are not reusable across different simulations.
   *
   * @return {@code true} if the team is contextual, {@code false} if it's reusable
   */
  boolean getTeam_contextual();

  /**
   * Returns the set of tag IDs associated with this team.
   *
   * @return set of tag IDs
   */
  Set<String> getTeam_tags();

  /**
   * Returns the set of user IDs who are members of this team.
   *
   * @return set of user IDs
   */
  Set<String> getTeam_users();

  /**
   * Returns the set of attackChainRun IDs this team participates in.
   *
   * @return set of attackChainRun IDs
   */
  Set<String> getTeam_attackChainRuns();

  /**
   * Returns the set of attackChain IDs this team is configured for.
   *
   * @return set of attackChain IDs
   */
  Set<String> getTeam_attackChains();

  /**
   * Returns the set of expectation IDs assigned to this team.
   *
   * @return set of expectation IDs
   */
  Set<String> getTeam_expectations();

  /**
   * Returns the set of attackChainNode IDs targeting this team in attackChainRuns.
   *
   * @return set of attackChainNode IDs
   */
  Set<String> getTeam_exercise_attackChainNodes();

  /**
   * Returns the set of communication IDs sent to this team.
   *
   * @return set of communication IDs
   */
  Set<String> getTeam_communications();
}
