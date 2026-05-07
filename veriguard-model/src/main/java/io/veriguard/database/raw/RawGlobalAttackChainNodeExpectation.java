package io.veriguard.database.raw;

/**
 * Spring Data projection interface for aggregated attackChainNode expectation data.
 *
 * <p>This interface defines a projection for retrieving global/aggregated expectation information
 * across attackChainNodes and attack patterns. Used for dashboard summaries and reporting.
 *
 * @see io.veriguard.database.model.AttackChainNodeExpectation
 */
public interface RawGlobalAttackChainNodeExpectation {

  /**
   * Returns the type of expectation.
   *
   * @return the expectation type (e.g., "DETECTION", "PREVENTION", "MANUAL")
   */
  String getInject_expectation_type();

  /**
   * Returns the actual score achieved for this expectation.
   *
   * @return the achieved score, or {@code null} if not yet evaluated
   */
  Double getInject_expectation_score();

  /**
   * Returns the expected/target score for this expectation.
   *
   * @return the expected score
   */
  Double getInject_expectation_expected_score();

  /**
   * Returns the ID of the attackChainNode this expectation is associated with.
   *
   * @return the attackChainNode ID
   */
  String getInject_id();

  /**
   * Returns the title of the associated attackChainNode.
   *
   * @return the attackChainNode title
   */
  String getInject_title();

  /**
   * Returns the ID of the attack pattern this expectation relates to.
   *
   * @return the attack pattern ID
   */
  String getAttack_pattern_id();
}
