package io.veriguard.utils;

import static java.util.Collections.emptyList;

import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.rest.attack_chain_node.form.NodeExpectationResultsByAttackPattern;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.veriguard.utils.mapper.AttackChainNodeExpectationMapper;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Component for computing and aggregating expectation results across attackChainRuns and
 * attackChains.
 *
 * <p>Provides methods to calculate global expectation results, filter by security platform, and
 * group results by attack patterns. This component is essential for generating reports and
 * dashboards that show the overall effectiveness of security controls.
 *
 * @see ExpectationResultsByType
 * @see AttackChainNodeExpectationMapper
 */
@RequiredArgsConstructor
@Component
public class ResultUtils {

  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  private final AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;

  /**
   * Computes global expectation results across all specified attackChainNodes.
   *
   * <p>Aggregates expectation results by type (Prevention, Detection, Vulnerability, Human
   * Response) for the given set of attackChainNode IDs using optimized raw database queries.
   *
   * @param attackChainNodeIds the set of attackChainNode IDs to compute results for
   * @return a list of aggregated results by expectation type, or empty list if no attackChainNodes
   *     provided
   */
  public List<ExpectationResultsByType> computeGlobalExpectationResults(
      Set<String> attackChainNodeIds) {

    if (attackChainNodeIds == null || attackChainNodeIds.isEmpty()) {
      return emptyList();
    }

    List<RawAttackChainNodeExpectation> expectations =
        attackChainNodeExpectationRepository.rawForComputeGlobalByAttackChainNodeIds(
            attackChainNodeIds);

    return attackChainNodeExpectationMapper.extractExpectationResultByTypesFromRaw(
        attackChainNodeIds, expectations);
  }

  /**
   * Computes global expectation results filtered by a specific security platform.
   *
   * <p>Similar to {@link #computeGlobalExpectationResults(Set)} but filters expectation results to
   * only include those from the specified security platform. Results are cloned to avoid modifying
   * the original expectations, and scores are recalculated based on platform-specific results.
   *
   * @param attackChainNodeIds the set of attackChainNode IDs to compute results for
   * @param securityPlatform the security platform to filter results by
   * @return a list of aggregated results filtered to the specified platform
   */
  public List<ExpectationResultsByType> computeGlobalExpectationResultsForPlatform(
      Set<String> attackChainNodeIds, SecurityPlatform securityPlatform) {

    if (attackChainNodeIds == null || attackChainNodeIds.isEmpty()) {
      return emptyList();
    }

    List<AttackChainNodeExpectation> expectations =
        attackChainNodeExpectationRepository
            .findAllForGlobalScoreByAttackChainNodes(attackChainNodeIds)
            .stream()
            .map(AttackChainNodeExpectation::clone)
            .toList();
    expectations.forEach(
        exp -> {
          exp.setResults(
              exp.getResults().stream()
                  .filter(r -> r.getSourceId().equals(securityPlatform.getId()))
                  .toList());

          exp.setScore(
              exp.getResults().stream()
                  .max(Comparator.comparing(NodeExpectationResult::getScore))
                  .map(NodeExpectationResult::getScore)
                  .orElse(null));
        });

    return attackChainNodeExpectationMapper.extractExpectationResultByTypes(
        attackChainNodeIds, expectations);
  }

  /**
   * Computes attackChainNode expectation results grouped by attack pattern.
   *
   * <p>Organizes the expectations from the provided attackChainNodes by their associated attack
   * patterns. Each attackChainNode may be linked to multiple attack patterns through its
   * nodeExecutor contract, resulting in grouped results suitable for MITRE ATT&CK matrix
   * visualization.
   *
   * @param attackChainNodes the list of attackChainNodes to process (must not be null)
   * @return a list of expectation results grouped by attack pattern
   */
  public List<NodeExpectationResultsByAttackPattern> computeNodeExpectationResults(
      @NotNull final List<AttackChainNode> attackChainNodes) {

    Map<AttackPattern, List<AttackChainNode>> groupedByAttackPattern =
        attackChainNodes.stream()
            .flatMap(
                attackChainNode ->
                    attackChainNode
                        .getNodeContract()
                        .map(
                            contract ->
                                contract.getAttackPatterns().stream()
                                    .map(
                                        attackPattern -> Map.entry(attackPattern, attackChainNode)))
                        .orElseGet(Stream::empty))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    return groupedByAttackPattern.entrySet().stream()
        .map(
            entry ->
                attackChainNodeExpectationMapper.toNodeExpectationResultsByAttackPattern(
                    entry.getKey(), entry.getValue()))
        .toList();
  }
}
