package io.veriguard.service;

import io.veriguard.database.model.AttackPattern;
import io.veriguard.database.model.CustomDashboardParameters;
import io.veriguard.database.raw.RawUserAuth;
import io.veriguard.database.repository.AttackPatternRepository;
import io.veriguard.engine.EngineService;
import io.veriguard.engine.api.ListConfiguration;
import io.veriguard.engine.api.ListRuntime;
import io.veriguard.engine.api.StructuralHistogramRuntime;
import io.veriguard.engine.api.StructuralHistogramWidget;
import io.veriguard.engine.model.attack_chain_node.EsAttackChainNode;
import io.veriguard.engine.query.EsAttackPath;
import io.veriguard.engine.query.EsSeries;
import io.veriguard.engine.query.EsSeriesData;
import io.veriguard.utils.CustomDashboardTimeRange;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsAttackPathService {

  private final AttackPatternRepository attackPatternRepository;

  private final EngineService esService;

  /**
   * Fetches attack paths for a given simulation.
   *
   * @param user the user requesting the data
   * @param runtime the structural histogram runtime containing the simulation filter and the series
   * @return a list of attack paths associated with the simulation
   * @throws ExecutionException
   * @throws InterruptedException
   */
  public List<EsAttackPath> attackPaths(
      RawUserAuth user,
      StructuralHistogramRuntime runtime,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters)
      throws ExecutionException, InterruptedException {

    String simulationId = extractSimulationIdFromSeriesFilter(runtime.getWidget());

    CompletableFuture<List<EsAttackChainNode>> simulationEsAttackChainNodesFuture =
        CompletableFuture.supplyAsync(
            () ->
                fetchSimulationAttackChainNodesFromES(
                    user, simulationId, parameters, definitionParameters));
    CompletableFuture<List<EsSeries>> simulationSeriesFuture =
        CompletableFuture.supplyAsync(() -> esService.multiTermHistogram(user, runtime));

    List<EsAttackChainNode> simulationEsAttackChainNodes = simulationEsAttackChainNodesFuture.get();
    List<EsSeries> simulationSeries = simulationSeriesFuture.get();

    // Fetch attackPattern of simulation
    Set<String> attackPatternIds =
        extractAttackPatternFromEsAttackChainNodes(simulationEsAttackChainNodes);
    Map<String, AttackPattern> attackPatternMap = fetchAttackPatterns(attackPatternIds);

    // Process series results
    Map<String, Long> successRateByAttackPatternIdMap =
        computeSuccessRateSeriesByAttackPatternId(simulationSeries);

    // Build Attack Paths
    return buildAttackPathsFromEsAttackChainNodeList(
        simulationEsAttackChainNodes, attackPatternMap, successRateByAttackPatternIdMap);
  }

  /**
   * Extracts the simulation ID from the series filter of the given structural histogram runtime.
   *
   * @param widget the structural histogram widget containing where the simulation ID is
   * @return the simulation ID extracted
   */
  public String extractSimulationIdFromSeriesFilter(StructuralHistogramWidget widget) {
    return widget.getSeries().getFirst().getFilter().getFilters().stream()
        .filter(f -> "base_simulation_side".equals(f.getKey()))
        .findFirst()
        .map(f -> f.getValues().getFirst())
        .orElseThrow();
  }

  /**
   * Fetches the attackChainNodes associated with a given simulation ID from Elasticsearch.
   *
   * @param user the user requesting the data
   * @param simulationId the ID of the simulation for which attackChainNodes are to be fetched
   * @return a list of EsAttackChainNode objects associated with the simulation
   */
  private List<EsAttackChainNode> fetchSimulationAttackChainNodesFromES(
      RawUserAuth user,
      String simulationId,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters) {
    Map<String, List<String>> filterMap = Map.of("base_simulation_side", List.of(simulationId));
    ListConfiguration config = esService.createListConfiguration("inject", filterMap);
    config.setDateAttribute("inject_created_at");
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);

    return esService
        .entities(user, new ListRuntime(config, parameters, definitionParameters))
        .stream()
        .filter(EsAttackChainNode.class::isInstance)
        .map(EsAttackChainNode.class::cast)
        .toList();
  }

  /**
   * Extracts attack pattern IDs from a list of EsAttackChainNode objects.
   *
   * @param esAttackChainNode the list of EsAttackChainNode objects to extract attack patterns from
   * @return a set of unique attack pattern IDs
   */
  private Set<String> extractAttackPatternFromEsAttackChainNodes(
      List<EsAttackChainNode> esAttackChainNode) {
    return esAttackChainNode.stream()
        .filter(i -> i.getBase_attack_patterns_side() != null)
        .flatMap(i -> i.getBase_attack_patterns_side().stream())
        .collect(Collectors.toSet());
  }

  /**
   * Fetches attack patterns from the database based on a set of attack pattern IDs.
   *
   * @param attackPatternIds the set of attack pattern IDs to fetch
   * @return a map of attack pattern IDs to AttackPattern objects
   */
  private Map<String, AttackPattern> fetchAttackPatterns(Set<String> attackPatternIds) {
    if (attackPatternIds.isEmpty()) {
      return new HashMap<>();
    }

    return StreamSupport.stream(
            attackPatternRepository.findAllById(attackPatternIds).spliterator(), false)
        .collect(Collectors.toMap(AttackPattern::getId, attackPattern -> attackPattern));
  }

  /**
   * Computes the success rate series by attack pattern ID from a list of attackChainNode
   * expectation
   *
   * @param attackChainNodeExpectationSeries the list of EsSeries containing attackChainNode
   *     expectations
   * @return a map where keys are attack pattern IDs and values are their success rates
   */
  private Map<String, Long> computeSuccessRateSeriesByAttackPatternId(
      List<EsSeries> attackChainNodeExpectationSeries) {
    Map<String, Long> successCounts =
        aggregateSeriesData(attackChainNodeExpectationSeries, "SUCCESS");
    Map<String, Long> failedCounts =
        aggregateSeriesData(attackChainNodeExpectationSeries, "FAILED");

    Map<String, Long> successRateMap = new HashMap<>();
    Set<String> allKeys = new HashSet<>(successCounts.keySet());
    allKeys.addAll(failedCounts.keySet());

    for (String key : allKeys) {
      long success = successCounts.getOrDefault(key, 0L);
      long failure = failedCounts.getOrDefault(key, 0L);
      long total = success + failure;

      Long successRate = total > 0 ? (success * 100) / total : null;
      successRateMap.put(key, successRate);
    }

    return successRateMap;
  }

  /**
   * Aggregates data from a list of EsSeries based on a specific filter label. ( Success or Failed )
   *
   * @param series the list of EsSeries to aggregate
   * @param label the label to filter the series data (e.g., "SUCCESS" or "FAILED")
   * @return a map where keys are series data keys(attackPatternId) and values are their aggregated
   *     counts
   */
  private Map<String, Long> aggregateSeriesData(List<EsSeries> series, String label) {
    return series.stream()
        .filter(s -> label.equals(s.getLabel()))
        .flatMap(s -> s.getData().stream())
        .collect(Collectors.toMap(EsSeriesData::getKey, EsSeriesData::getValue, Long::sum));
  }

  /**
   * Builds a list of attack paths from a list of EsAttackChainNode objects, mapping them to their
   * corresponding attack patterns and success rates.
   *
   * @param esAttackChainNodes the list of EsAttackChainNode objects to process
   * @param attackPatterns a map of attack pattern IDs to AttackPattern objects
   * @param successRateMap a map of attack pattern IDs to their success rates
   * @return a list of EsAttackPath objects representing the attack paths
   */
  private List<EsAttackPath> buildAttackPathsFromEsAttackChainNodeList(
      List<EsAttackChainNode> esAttackChainNodes,
      Map<String, AttackPattern> attackPatterns,
      Map<String, Long> successRateMap) {
    Map<String, EsAttackPath> esAttackPathsMap = new HashMap<>();

    for (EsAttackChainNode attackChainNode : esAttackChainNodes) {
      if (attackChainNode.getBase_attack_patterns_side() == null) {
        continue;
      }

      for (String attackId : attackChainNode.getBase_attack_patterns_side()) {
        esAttackPathsMap.compute(
            attackId,
            (key, value) ->
                value == null
                    ? createNewAttackPath(
                        attackPatterns.get(attackId), attackChainNode, successRateMap.get(attackId))
                    : updateAttackPath(value, attackChainNode));
      }
    }

    return esAttackPathsMap.values().stream().toList();
  }

  /**
   * Creates a new EsAttackPath object based on the provided attack pattern and attackChainNode.
   *
   * @param attackPattern the attack pattern to base the attack path on
   * @param attackChainNode the attackChainNode containing the base ID and children attack patterns
   * @param successRate the success rate of the attack pattern
   * @return a new EsAttackPath object, or null if the attack pattern is null
   */
  private EsAttackPath createNewAttackPath(
      AttackPattern attackPattern, EsAttackChainNode attackChainNode, Long successRate) {
    if (attackPattern == null) {
      return null; // Or handle missing attack pattern appropriately
    }

    List<EsAttackPath.KillChainPhaseObject> killChainPhases =
        attackPattern.getKillChainPhases().stream()
            .map(
                killChainPhase ->
                    new EsAttackPath.KillChainPhaseObject(
                        killChainPhase.getId(),
                        killChainPhase.getName(),
                        killChainPhase.getOrder()))
            .toList();

    Set<String> childrenIds =
        attackChainNode.getBase_attack_patterns_children_side() != null
            ? new HashSet<>(attackChainNode.getBase_attack_patterns_children_side())
            : new HashSet<>();

    return new EsAttackPath(
        attackPattern.getId(),
        attackPattern.getName(),
        attackPattern.getExternalId(),
        killChainPhases,
        childrenIds,
        new HashSet<>(List.of(attackChainNode.getBase_id())),
        successRate);
  }

  /**
   * Updates an existing EsAttackPath object by adding the attackChainNode's base ID and children
   *
   * @param attackPath the existing EsAttackPath to update
   * @param attackChainNode the EsAttackChainNode containing the base ID and children attack
   *     patterns
   * @return the updated EsAttackPath object
   */
  private EsAttackPath updateAttackPath(
      EsAttackPath attackPath, EsAttackChainNode attackChainNode) {
    attackPath.getAttackChainNodeIds().add(attackChainNode.getBase_id());
    if (attackPath.getAttackPatternChildrenIds() != null
        && attackChainNode.getBase_attack_patterns_children_side() != null) {
      attackPath
          .getAttackPatternChildrenIds()
          .addAll(attackChainNode.getBase_attack_patterns_children_side());
    }
    return attackPath;
  }
}
