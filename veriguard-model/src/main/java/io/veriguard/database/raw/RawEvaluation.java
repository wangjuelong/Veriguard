package io.veriguard.database.raw;

/**
 * Spring Data projection interface for evaluation data.
 *
 * <p>This interface defines a projection for retrieving evaluation scores assigned to objectives
 * during attackChainRun assessment.
 *
 * @see io.veriguard.database.model.Evaluation
 */
public interface RawEvaluation {

  /**
   * Returns the unique identifier of the evaluation.
   *
   * @return the evaluation ID
   */
  String getEvaluation_id();

  /**
   * Returns the score assigned in this evaluation.
   *
   * @return the evaluation score
   */
  long getEvaluation_score();

  /**
   * Returns the ID of the objective being evaluated.
   *
   * @return the objective ID
   */
  String getEvaluation_objective();
}
