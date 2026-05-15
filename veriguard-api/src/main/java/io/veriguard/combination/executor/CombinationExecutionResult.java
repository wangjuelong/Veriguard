package io.veriguard.combination.executor;

import io.veriguard.database.model.combination.AttackCombinationHitState;

/**
 * Outcome of one [{@link CombinationExecutor}] dispatch — hit-state classification plus the agent's
 * raw output captured for admin review.
 *
 * <p>The C1-Platform-2 path returned only {@link AttackCombinationHitState}, dropping stdout /
 * stderr / exit_code on the floor. C1-Platform-3 follow-up AB-2 promotes these fields to first
 * class so the scheduler can persist them onto {@code attack_combination_results.{stdout, stderr,
 * exit_code}} (Flyway V22) — admins can then post-mortem each hit / miss without re-running.
 *
 * <p>Non-HTTP executors (Stub / PCAP) that genuinely do not have an agent-shaped output use {@link
 * #of(AttackCombinationHitState)} to wrap their existing hit-state return value; the four detail
 * fields stay null and the scheduler maps that to {@code NULL} columns.
 */
public record CombinationExecutionResult(
    AttackCombinationHitState hitState,
    /** Captured agent stdout, if any. */
    String stdout,
    /** Captured agent stderr, if any. */
    String stderr,
    /** Process exit code or logical equivalent. */
    Integer exitCode,
    /** Operator-readable error message; carried separately from stderr. */
    String errorMessage) {

  /**
   * Wrap a bare {@link AttackCombinationHitState} for executors that do not have agent-shaped
   * output (Stub / PCAP).
   */
  public static CombinationExecutionResult of(AttackCombinationHitState hitState) {
    return new CombinationExecutionResult(hitState, null, null, null, null);
  }
}
