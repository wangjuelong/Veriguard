package io.veriguard.helper;

import io.veriguard.database.model.AttackChainNode;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for computing attackChainNode execution statistics.
 *
 * <p>This utility provides aggregated statistics for a collection of attackChainNodes, useful for
 * dashboard displays and progress tracking during attackChainRuns and simulations.
 *
 * @see AttackChainNode
 */
public class AttackChainNodeStatisticsHelper {

  private AttackChainNodeStatisticsHelper() {
    // Utility class - prevent instantiation
  }

  /**
   * Computes aggregated statistics for a list of attackChainNodes.
   *
   * <p>The returned map contains the following statistics:
   *
   * <ul>
   *   <li>{@code total_count} - Total number of attackChainNodes
   *   <li>{@code total_executed} - Number of attackChainNodes that have been executed
   *   <li>{@code total_remaining} - Number of attackChainNodes not yet executed
   *   <li>{@code total_past} - Number of attackChainNodes scheduled in the past
   *   <li>{@code total_future} - Number of attackChainNodes scheduled in the future
   *   <li>{@code total_progress} - Execution progress percentage (0-100)
   * </ul>
   *
   * @param attackChainNodes the list of attackChainNodes to analyze (must not be null)
   * @return a map containing the computed statistics
   */
  public static Map<String, Long> getAttackChainNodeStatistics(
      @NotNull final List<AttackChainNode> attackChainNodes) {
    Map<String, Long> stats = new HashMap<>();
    long total = attackChainNodes.size();
    stats.put("total_count", total);
    long executed =
        attackChainNodes.stream()
            .filter(attackChainNode -> attackChainNode.getStatus().isPresent())
            .count();
    stats.put("total_executed", executed);
    stats.put("total_remaining",
        attackChainNodes.stream().filter(AttackChainNode::isNotExecuted).count());
    stats.put("total_past",
        attackChainNodes.stream().filter(AttackChainNode::isPastAttackChainNode).count());
    stats.put("total_future",
        attackChainNodes.stream().filter(AttackChainNode::isFutureAttackChainNode).count());
    stats.put("total_progress", total > 0 ? (executed * 100 / total) : 0);
    return stats;
  }
}
