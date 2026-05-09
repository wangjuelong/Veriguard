package io.veriguard.database.raw;

import java.time.Instant;

/**
 * Spring Data projection interface for finding data.
 *
 * <p>This interface defines a projection for retrieving findings discovered during attackChainNode
 * execution. Findings represent artifacts, indicators, or other data collected from target systems
 * during attack simulations.
 *
 * @see io.veriguard.database.model.Finding
 */
public interface RawFinding {

  /**
   * Returns the unique identifier of the finding.
   *
   * @return the finding ID
   */
  String getFinding_id();

  /**
   * Returns the value of the finding.
   *
   * @return the finding value (e.g., file hash, IP address, process name)
   */
  String getFinding_value();

  /**
   * Returns the field name that categorizes this finding.
   *
   * @return the field name
   */
  String getFinding_field();

  /**
   * Returns the creation timestamp of the finding.
   *
   * @return the creation timestamp
   */
  Instant getFinding_created_at();

  /**
   * Returns the last update timestamp of the finding.
   *
   * @return the update timestamp
   */
  Instant getFinding_updated_at();

  /**
   * Returns the type of finding.
   *
   * @return the finding type
   */
  String getFinding_type();

  /**
   * Returns the ID of the attackChainNode that produced this finding.
   *
   * @return the attackChainNode ID
   */
  String getFinding_inject_id();

  /**
   * Returns the ID of the attackChainRun this finding belongs to.
   *
   * @return the attackChainRun ID, or {@code null} if from a attackChain
   */
  String getNode_attackChainRun();

  /**
   * Returns the ID of the attackChain this finding belongs to.
   *
   * @return the attackChain ID, or {@code null} if from an attackChainRun
   */
  String getAttack_chain_id();

  /**
   * Returns the ID of the asset where this finding was discovered.
   *
   * @return the asset ID
   */
  String getAsset_id();
}
