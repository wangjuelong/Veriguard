package io.veriguard.rest.finding;

import static io.veriguard.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.JsonNode;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AssetRepository;
import io.veriguard.database.repository.FindingRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.attack_chain_node.service.ContractOutputContext;
import io.veriguard.rest.attack_chain_node.service.ExecutionProcessingContext;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FindingService {

  private static final String HOST = "host";
  private final AttackChainNodeService attackChainNodeService;

  private final FindingRepository findingRepository;
  private final AssetRepository assetRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;

  // -- CRUD --

  public List<Finding> findings() {
    return fromIterable(this.findingRepository.findAll());
  }

  public Finding finding(@NotNull final String id) {
    return this.findingRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Finding not found with id: " + id));
  }

  public Finding createFinding(@NotNull final Finding finding, @NotBlank final String attackChainNodeId) {
    AttackChainNode attackChainNode = this.attackChainNodeService.attackChainNode(attackChainNodeId);
    finding.setAttackChainNode(attackChainNode);
    return this.findingRepository.save(finding);
  }

  public Finding updateFinding(@NotNull final Finding finding, @NotNull final String attackChainNodeId) {
    if (!finding.getAttackChainNode().getId().equals(attackChainNodeId)) {
      throw new IllegalArgumentException("Inject id cannot be changed: " + attackChainNodeId);
    }
    return this.findingRepository.save(finding);
  }

  public void deleteFinding(@NotNull final String id) {
    if (!this.findingRepository.existsById(id)) {
      throw new EntityNotFoundException("Finding not found with id: " + id);
    }
    this.findingRepository.deleteById(id);
  }

  /**
   * Generates findings based on the provided JSON node and context. It determines whether the
   * execution is agent-based or nodeExecutor-based and processes the findings accordingly.
   *
   * @param executionContext The context of the execution, containing information about whether it's
   *     an agent execution and relevant data for processing.
   * @param contractOutputContext The context of the contract output, providing details about the
   *     expected findings format and metadata.
   * @param structuredOutputNode The JSON node containing the raw data from which findings will be
   *     generated.
   * @param validator A predicate function to validate the format of each finding in the JSON node.
   * @param valueExtractor A function to extract the value for each finding from the JSON node.
   * @param assetExtractor A function to extract associated asset IDs for each finding from the JSON
   *     node (used for nodeExecutor findings).
   * @param userExtractor A function to extract associated user IDs for each finding from the JSON
   *     node (used for nodeExecutor findings).
   * @param teamExtractor A function to extract associated team IDs for each finding from the JSON
   *     node (used for nodeExecutor findings).
   */
  public void generateFindings(
      ExecutionProcessingContext executionContext,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor) {

    if (executionContext.isAgentExecution()) {
      processAgentFindings(
          structuredOutputNode,
          executionContext.attackChainNode(),
          executionContext.agent(),
          contractOutputContext,
          executionContext.valueTargetedAssetsMap(),
          validator,
          valueExtractor);
    } else {
      processNodeExecutorFindings(
          structuredOutputNode,
          executionContext.attackChainNode(),
          contractOutputContext,
          validator,
          valueExtractor,
          assetExtractor,
          userExtractor,
          teamExtractor);
    }
  }

  public void processAgentFindings(
      JsonNode structuredOutputNode,
      AttackChainNode attackChainNode,
      Agent agent,
      ContractOutputContext contractOutputContext,
      Map<String, Endpoint> valueTargetedAssetsMap,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor) {

    if (structuredOutputNode == null || !structuredOutputNode.isArray()) {
      log.debug("Skipping agent findings: structuredOutputNode is null or not an array");
      return;
    }

    log.debug("Processing {} nodes for agent finding", structuredOutputNode.size());
    for (JsonNode jsonNode : structuredOutputNode) {
      if (!validator.test(jsonNode)) {
        log.error("Validation failed for node: {}", jsonNode);
        continue;
      }

      resolveAssetFromStructuredOutput(jsonNode, valueTargetedAssetsMap, agent)
          .ifPresentOrElse(
              asset ->
                  saveAgentFinding(
                      attackChainNode, asset, contractOutputContext, valueExtractor.apply(jsonNode)),
              () -> log.warn("Finding dropped: No asset match for host in {}", jsonNode));
    }
  }

  public void saveAgentFinding(
      AttackChainNode attackChainNode, Asset asset, ContractOutputContext contractOutputContext, String value) {

    findingRepository.saveCompleteFinding(
        contractOutputContext.key(),
        contractOutputContext.type().name(),
        value,
        new String[0],
        attackChainNode.getId(),
        contractOutputContext.name(),
        asset.getId(),
        contractOutputContext.tagIds());
  }

  private Optional<Asset> resolveAssetFromStructuredOutput(
      JsonNode structuredOutput, Map<String, Endpoint> valueTargetedAssetsMap, Agent sourceAgent) {
    if (valueTargetedAssetsMap.isEmpty() || !structuredOutput.has(HOST)) {
      return Optional.of(sourceAgent.getAsset());
    }

    String host = structuredOutput.get(HOST).asText();
    return valueTargetedAssetsMap.keySet().stream()
        .filter(host::contains)
        .findFirst()
        .map(valueTargetedAssetsMap::get);
  }

  public void processNodeExecutorFindings(
      JsonNode structuredOutputNode,
      AttackChainNode attackChainNode,
      ContractOutputContext contractOutputContext,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor) {

    if (structuredOutputNode == null) {
      log.debug("Skipping injector findings: structuredOutputNode is null");
      return;
    }

    List<Finding> findings =
        buildFindings(
            structuredOutputNode,
            contractOutputContext,
            validator,
            valueExtractor,
            assetExtractor,
            userExtractor,
            teamExtractor);

    createFindings(findings, attackChainNode.getId());
  }

  /**
   * Persists a list of findings in the database, associating them with a specific attackChainNode.
   *
   * @param findings The list of findings to be created and persisted.
   * @param attackChainNodeId The identifier of to attackChainNode to which the findings will be associated. Must not
   *     be blank.
   */
  public void createFindings(
      @NotNull final List<Finding> findings, @NotBlank final String attackChainNodeId) {
    AttackChainNode attackChainNode = attackChainNodeService.attackChainNode(attackChainNodeId);
    findings.forEach(finding -> finding.setAttackChainNode(attackChainNode));
    List<Finding> deduplicatedFindings = deduplicateFindings(findings);
    findingRepository.saveAll(deduplicatedFindings);
  }

  /**
   * Deduplicates a list of findings based on the unique constraint keys: value, type, and field.
   * When duplicates are found, their assets, teams and users are merged into the first occurrence.
   *
   * @param findings the raw list of findings, potentially containing duplicates
   * @return a deduplicated list with associations merged
   */
  private List<Finding> deduplicateFindings(@NotNull final List<Finding> findings) {
    Map<String, Finding> seen = new java.util.LinkedHashMap<>();
    for (Finding finding : findings) {
      String key = finding.getValue() + "|" + finding.getType() + "|" + finding.getField();
      Finding existing = seen.get(key);
      if (existing == null) {
        seen.put(key, finding);
      } else {
        log.debug(
            "Duplicate finding detected (value={}, type={}, field={}): merging associations",
            finding.getValue(),
            finding.getType(),
            finding.getField());
        if (finding.getAssets() != null) {
          List<Asset> merged =
              new ArrayList<>(existing.getAssets() != null ? existing.getAssets() : List.of());
          finding
              .getAssets()
              .forEach(
                  a -> {
                    if (!merged.contains(a)) merged.add(a);
                  });
          existing.setAssets(merged);
        }
        if (finding.getTeams() != null) {
          List<Team> merged =
              new ArrayList<>(existing.getTeams() != null ? existing.getTeams() : List.of());
          finding
              .getTeams()
              .forEach(
                  t -> {
                    if (!merged.contains(t)) merged.add(t);
                  });
          existing.setTeams(merged);
        }
        if (finding.getUsers() != null) {
          List<User> merged =
              new ArrayList<>(existing.getUsers() != null ? existing.getUsers() : List.of());
          finding
              .getUsers()
              .forEach(
                  u -> {
                    if (!merged.contains(u)) merged.add(u);
                  });
          existing.setUsers(merged);
        }
      }
    }
    return new ArrayList<>(seen.values());
  }

  public List<Finding> buildFindings(
      JsonNode structuredOutputNode,
      ContractOutputContext contractOutputContext,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor) {

    if (contractOutputContext.isMultiple() && structuredOutputNode.isArray()) {
      List<Finding> findings = new ArrayList<>();
      for (JsonNode node : structuredOutputNode) {
        findings.add(
            buildSingleFinding(
                node,
                contractOutputContext,
                validator,
                valueExtractor,
                assetExtractor,
                userExtractor,
                teamExtractor));
      }
      return findings;
    }

    return List.of(
        buildSingleFinding(
            structuredOutputNode,
            contractOutputContext,
            validator,
            valueExtractor,
            assetExtractor,
            userExtractor,
            teamExtractor));
  }

  private Finding buildSingleFinding(
      JsonNode structuredOutputNode,
      ContractOutputContext contractOutputContext,
      Predicate<JsonNode> validator,
      Function<JsonNode, String> valueExtractor,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor) {

    if (!validator.test(structuredOutputNode)) {
      throw new IllegalArgumentException(
          "Finding not correctly formatted: " + structuredOutputNode);
    }

    Finding finding = FindingUtils.createFinding(contractOutputContext);
    finding.setValue(valueExtractor.apply(structuredOutputNode));
    return linkFinding(structuredOutputNode, finding, assetExtractor, userExtractor, teamExtractor);
  }

  private Finding linkFinding(
      JsonNode structuredOutputNode,
      Finding finding,
      Function<JsonNode, List<String>> assetExtractor,
      Function<JsonNode, List<String>> userExtractor,
      Function<JsonNode, List<String>> teamExtractor) {

    List<String> assetIds = assetExtractor.apply(structuredOutputNode);
    if (!assetIds.isEmpty()) {
      finding.setAssets(fetchEntities(assetIds, assetRepository::findById));
    }

    List<String> teamIds = teamExtractor.apply(structuredOutputNode);
    if (!teamIds.isEmpty()) {
      finding.setTeams(fetchEntities(teamIds, teamRepository::findById));
    }

    List<String> userIds = userExtractor.apply(structuredOutputNode);
    if (!userIds.isEmpty()) {
      finding.setUsers(fetchEntities(userIds, userRepository::findById));
    }

    return finding;
  }

  private <T> List<T> fetchEntities(List<String> ids, Function<String, Optional<T>> finder) {
    return ids.stream().map(finder).filter(Optional::isPresent).map(Optional::get).toList();
  }
}
