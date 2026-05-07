package io.veriguard.utils.mapper;

import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import static io.veriguard.utils.NodeExpectationResultUtils.getExpectationResultByTypes;
import static java.util.Collections.emptyList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.AttackPattern;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.rest.attack_chain_node.form.NodeExpectationResultsByAttackPattern;
import io.veriguard.utils.NodeExpectationResultUtils;
import io.veriguard.utils.AttackChainNodeUtils;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mapper component for processing and converting attackChainNode expectations.
 *
 * <p>Provides methods for extracting expectation results from various sources including attackChainNode
 * content, raw database queries, and entity objects. Handles the complex logic of building
 * expectation result aggregations by type.
 *
 * @see io.veriguard.database.model.AttackChainNodeExpectation
 * @see io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttackChainNodeExpectationMapper {

  /** JSON property name for expectation type in attackChainNode content. */
  public static final String NODE_EXPECTATION_TYPE = "expectation_type";

  /** Set of all available expectation types for completeness checking. */
  private static final EnumSet<ExpectationType> ALL_EXPECTATION_TYPES =
      EnumSet.allOf(ExpectationType.class);

  private final AttackChainNodeRepository attackChainNodeRepository;
  private final ObjectMapper objectMapper;
  private final AttackChainNodeUtils attackChainNodeUtils;

  /**
   * Build ExpectationResultsByType from attackChainNodeContent
   *
   * @param attackChainNodeContent
   * @param expectations
   * @param scoreExtractor
   * @return List of ExpectationResultsByType
   */
  public <T> List<ExpectationResultsByType> extractExpectationResults(
      ObjectNode attackChainNodeContent,
      List<T> expectations,
      BiFunction<List<AttackChainNodeExpectation.EXPECTATION_TYPE>, List<T>, List<Double>> scoreExtractor) {
    List<ExpectationResultsByType> expectationResultByTypes =
        getExpectationResultByTypes(expectations, scoreExtractor);

    if (!expectationResultByTypes.isEmpty()) {
      return expectationResultByTypes;
    }
    if (attackChainNodeContent == null) {
      return emptyList();
    }

    return buildExpectationResultsFromAttackChainNodeContent(attackChainNodeContent);
  }

  /**
   * Build AttackChainNodeResults based on expectation defined in the content of attackChainNode
   *
   * @param attackChainNodeContent content of attackChainNode where expectations have been defined
   * @return List of AttackChainNodeResultsByType
   */
  private static List<ExpectationResultsByType> buildExpectationResultsFromAttackChainNodeContent(
      ObjectNode attackChainNodeContent) {

    JsonNode contentNode = attackChainNodeContent.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
    if (contentNode == null || !contentNode.isArray()) {
      return emptyList();
    }

    Set<ExpectationType> uniqueTypes = new HashSet<>();
    for (JsonNode expectationNode : contentNode) {
      JsonNode typeNode = expectationNode.get(NODE_EXPECTATION_TYPE);
      if (typeNode != null && typeNode.isTextual()) {
        try {
          ExpectationType type = ExpectationType.of(typeNode.asText().toUpperCase());
          uniqueTypes.add(type);
        } catch (IllegalArgumentException e) {
          log.warn("Expectation Type is not valid", e);
        }
      }
    }

    return buildFallbackResults(uniqueTypes);
  }

  /**
   * Build NodeExpectationResultsByAttackPattern from AttackChainNodeExpectation related to attackPatterns
   *
   * @param attackPattern
   * @param attackChainNodes
   * @return List of NodeExpectationResultsByAttackPattern
   */
  public NodeExpectationResultsByAttackPattern toNodeExpectationResultsByAttackPattern(
      final AttackPattern attackPattern, @NotNull final List<AttackChainNode> attackChainNodes) {

    return NodeExpectationResultsByAttackPattern.builder()
        .results(
            attackChainNodes.stream()
                .map(
                    attackChainNode -> {
                      NodeExpectationResultsByAttackPattern.NodeExpectationResultsByType
                          result =
                              new NodeExpectationResultsByAttackPattern
                                  .NodeExpectationResultsByType();
                      result.setAttackChainNodeId(attackChainNode.getId());
                      result.setAttackChainNodeTitle(attackChainNode.getTitle());
                      result.setResults(
                          extractExpectationResults(
                              attackChainNode.getContent(),
                              attackChainNodeUtils.getPrimaryExpectations(attackChainNode),
                              NodeExpectationResultUtils::getScores));
                      return result;
                    })
                .collect(Collectors.toList()))
        .attackPattern(attackPattern)
        .build();
  }

  /**
   * Extract ExpectationResultsByType from attackChainRuns using data from raw queries
   *
   * @param attackChainNodeIds
   * @param expectations
   * @return List of ExpectationResultsByType
   */
  public List<ExpectationResultsByType> extractExpectationResultByTypesFromRaw(
      Set<String> attackChainNodeIds, List<RawAttackChainNodeExpectation> expectations) {

    if (expectations != null && !expectations.isEmpty()) {
      return getExpectationResultByTypes(
          expectations, NodeExpectationResultUtils::getScoresFromRaw);
    }

    return buildExpectationResultsFromAttackChainNodeContents(attackChainNodeIds);
  }

  /**
   * Extract ExpectationResultsByType from attackChainRuns using data from raw queries
   *
   * @param attackChainNodeIds
   * @param expectations
   * @return List of ExpectationResultsByType
   */
  public List<ExpectationResultsByType> extractExpectationResultByTypes(
      Set<String> attackChainNodeIds, List<AttackChainNodeExpectation> expectations) {

    if (expectations != null && !expectations.isEmpty()) {
      return getExpectationResultByTypes(expectations, NodeExpectationResultUtils::getScores);
    }

    return buildExpectationResultsFromAttackChainNodeContents(attackChainNodeIds);
  }

  /**
   * Build AttackChainNodeResults based on content of attackChainNodes from an attackChainRun
   *
   * @param attackChainNodeIds the attackChainRun id
   * @return List of AttackChainNodeResultsByType
   */
  private List<ExpectationResultsByType> buildExpectationResultsFromAttackChainNodeContents(
      @NotNull Set<String> attackChainNodeIds) {

    // Fetch all attackChainNode contents in order to extract expectations defined in every attackChainNode
    List<String> rawContents = attackChainNodeRepository.findContentsByAttackChainNodeIds(attackChainNodeIds);
    Set<ExpectationType> foundTypes = new HashSet<>();

    for (String contentJson : rawContents) {
      if (contentJson == null || contentJson.isBlank()) continue;

      try {
        JsonNode jsonNode = objectMapper.readTree(contentJson);

        if (jsonNode != null && jsonNode.isObject()) {
          ObjectNode contentNode = (ObjectNode) jsonNode;

          // ExpectationResults from one attackChainNodeContent
          List<ExpectationResultsByType> results =
              buildExpectationResultsFromAttackChainNodeContent(contentNode);

          // Check if all expectation types have already been added, if so stop the loop
          for (ExpectationResultsByType r : results) {
            if (ALL_EXPECTATION_TYPES.contains(r.type())) {
              foundTypes.add(r.type());
              if (foundTypes.size() == ALL_EXPECTATION_TYPES.size()) {
                break;
              }
            }
          }
        }

      } catch (JsonProcessingException e) {
        log.warn("Invalid JSON in inject content", e);
      }
    }

    return buildFallbackResults(foundTypes);
  }

  /**
   * Build final list of ExpectationResults using NodeExpectationResultUtils methods
   *
   * @param foundTypes ExpectationTypes defined in the content of attackChainNode
   * @return List of ExpectationResultsByType
   */
  private static List<ExpectationResultsByType> buildFallbackResults(
      Set<ExpectationType> foundTypes) {
    List<ExpectationResultsByType> fallbackResults = new ArrayList<>();
    for (ExpectationType type : foundTypes) {
      NodeExpectationResultUtils.getExpectationByType(type, emptyList())
          .ifPresent(fallbackResults::add);
    }
    return fallbackResults;
  }
}
