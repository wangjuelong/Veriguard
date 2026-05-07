package io.veriguard.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for communication data.
 *
 * <p>This interface defines a projection for retrieving communication messages sent during attackChainRun
 * execution. Communications include emails, SMS, and other messages sent to attackChainRun participants.
 *
 * @see io.veriguard.database.model.Communication
 */
public interface RawCommunication {

  /**
   * Returns the unique identifier of the communication.
   *
   * @return the communication ID
   */
  String getCommunication_id();

  /**
   * Returns the external message ID (e.g., email Message-ID header).
   *
   * @return the message ID
   */
  String getCommunication_message_id();

  /**
   * Returns the timestamp when the communication was received.
   *
   * @return the received timestamp, or {@code null} if not yet received
   */
  Instant getCommunication_received_at();

  /**
   * Returns the timestamp when the communication was sent.
   *
   * @return the sent timestamp
   */
  Instant getCommunication_sent_at();

  /**
   * Returns the subject line of the communication.
   *
   * @return the communication subject
   */
  String getCommunication_subject();

  /**
   * Returns the ID of the attackChainNode that triggered this communication.
   *
   * @return the attackChainNode ID
   */
  String getCommunication_attackChainNode();

  /**
   * Returns the set of user IDs who are recipients of this communication.
   *
   * @return set of user IDs
   */
  Set<String> getCommunication_users();

  /**
   * Returns whether the communication has been acknowledged.
   *
   * @return {@code true} if acknowledged, {@code false} otherwise
   */
  boolean getCommunication_ack();

  /**
   * Returns whether this is an animation (facilitator) communication.
   *
   * @return {@code true} if animation communication, {@code false} if player communication
   */
  boolean getCommunication_animation();

  /**
   * Returns the sender address of the communication.
   *
   * @return the "from" address
   */
  String getCommunication_from();

  /**
   * Returns the recipient address of the communication.
   *
   * @return the "to" address
   */
  String getCommunication_to();

  /**
   * Returns the ID of the attackChainRun this communication belongs to.
   *
   * @return the attackChainRun ID
   */
  String getCommunication_attackChainRun();
}
