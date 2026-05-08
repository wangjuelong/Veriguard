package io.veriguard.database.raw;

import io.veriguard.helper.UserHelper;
import java.util.List;

/**
 * Spring Data projection interface for player (attackChainRun participant) data.
 *
 * <p>This interface defines a lightweight projection for retrieving player information during
 * attackChainRun execution. Players are users who participate in attackChainRuns as targets or
 * respondents.
 *
 * @see io.veriguard.database.model.User
 * @see RawUser
 */
public interface RawPlayer {

  /**
   * Computes and returns the Gravatar URL for this player based on their email address.
   *
   * @return the Gravatar URL
   */
  default String getUser_gravatar() {
    return UserHelper.getGravatar(getUser_email());
  }

  /**
   * Returns the unique identifier of the player.
   *
   * @return the user ID
   */
  String getUser_id();

  /**
   * Returns the player's first name.
   *
   * @return the first name
   */
  String getUser_firstname();

  /**
   * Returns the player's last name.
   *
   * @return the last name
   */
  String getUser_lastname();

  /**
   * Returns the player's email address.
   *
   * @return the email address
   */
  String getUser_email();

  /**
   * Returns the ID of the player's organization.
   *
   * @return the organization ID
   */
  String getUser_organization();

  /**
   * Returns the list of tag IDs associated with this player.
   *
   * @return list of tag IDs
   */
  List<String> getUser_tags();
}
