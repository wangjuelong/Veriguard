package io.veriguard.rest.attack_chain_node.service;

import static io.veriguard.database.model.CollectExecutionStatus.COLLECTING;
import static io.veriguard.database.model.ExecutionStatus.*;
import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.veriguard.database.model.Payload.PAYLOAD_EXECUTION_ARCH.*;
import static io.veriguard.database.specification.AttackChainNodeSpecification.*;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static io.veriguard.service.AttackChainNodeExpectationUtils.extractAssetIdsFromAttackChainNodeExpectationsResults;
import static io.veriguard.utils.AgentUtils.isPrimaryAgent;
import static io.veriguard.utils.AttackChainNodeUtils.extractAttackChainNodeExpectationsFromAttackChainNodes;
import static io.veriguard.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.veriguard.utils.StringUtils.duplicateString;
import static io.veriguard.utils.mapper.AttackChainNodeStatusMapper.toExecutionTracesOutput;
import static io.veriguard.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static java.time.Instant.now;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.database.specification.AttackChainNodeSpecification;
import io.veriguard.database.specification.SpecificationUtils;
import io.veriguard.healthcheck.dto.HealthCheck;
import io.veriguard.healthcheck.enums.ExternalServiceDependency;
import io.veriguard.healthcheck.utils.HealthCheckUtils;
import io.veriguard.injector_contract.ContractTargetedProperty;
import io.veriguard.injector_contract.fields.ContractFieldType;
import io.veriguard.injectors.email.service.ImapService;
import io.veriguard.injectors.email.service.SmtpService;
import io.veriguard.rest.atomic_testing.form.AttackChainNodeResultOverviewOutput;
import io.veriguard.rest.atomic_testing.form.AttackChainNodeStatusOutput;
import io.veriguard.rest.atomic_testing.form.ExecutionTraceOutput;
import io.veriguard.rest.attack_chain_node.form.*;
import io.veriguard.rest.attack_chain_node.output.AgentsAndAssetsAgentless;
import io.veriguard.rest.collector.service.CollectorService;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.injector_contract.NodeContractContentUtils;
import io.veriguard.rest.injector_contract.NodeContractService;
import io.veriguard.rest.security.SecurityExpression;
import io.veriguard.rest.security.SecurityExpressionHandler;
import io.veriguard.rest.tag.TagService;
import io.veriguard.service.*;
import io.veriguard.utils.AttackChainNodeUtils;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.JpaUtils;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.mapper.AttackChainNodeMapper;
import io.veriguard.utils.mapper.AttackChainNodeStatusMapper;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@RequiredArgsConstructor
@Service
@Slf4j
public class AttackChainNodeService {

  private final TeamRepository teamRepository;
  private final ExecutionTraceRepository executionTraceRepository;
  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final CollectorService collectorService;
  private final EndpointService endpointService;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AttackChainNodeDocumentRepository attackChainNodeDocumentRepository;
  private final NodeExecutorService nodeExecutorService;
  private final AttackChainNodeStatusRepository attackChainNodeStatusRepository;
  private final AttackChainNodeMapper attackChainNodeMapper;
  private final MethodSecurityExpressionHandler methodSecurityExpressionHandler;
  private final UserService userService;
  private final NodeContractService nodeContractService;
  private final TagRuleService tagRuleService;
  private final TagService tagService;
  private final DocumentService documentService;
  private final AttackChainNodeStatusMapper attackChainNodeStatusMapper;
  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;
  private final PayloadRepository payloadRepository;
  private final SmtpService smtpService;
  private final ImapService imapService;
  private final HealthCheckUtils healthCheckUtils;
  private final NodeContractContentUtils nodeContractContentUtils;

  @Resource protected ObjectMapper mapper;

  private SecurityExpression getAmbientSecurityExpression() {
    return ((SecurityExpressionHandler) methodSecurityExpressionHandler).getSecurityExpression();
  }

  // -- CRUD --

  public AttackChainNode createAndSaveAttackChainNode(
      @Nullable final AttackChainRun attackChainRun,
      @Nullable final AttackChain attackChain,
      @NotNull final AttackChainNodeInput input) {
    return attackChainNodeRepository.save(buildAttackChainNode(attackChainRun, attackChain, input));
  }

  public List<AttackChainNode> createAndSaveAttackChainNodeList(
      @Nullable final AttackChainRun attackChainRun,
      @Nullable final AttackChain attackChain,
      List<AttackChainNodeInput> attackChainNodeInputs) {

    List<AttackChainNode> attackChainNodes = new ArrayList<>();
    attackChainNodeInputs.forEach(
        attackChainNodeInput ->
            attackChainNodes.add(
                buildAttackChainNode(attackChainRun, attackChain, attackChainNodeInput)));
    return attackChainNodeRepository.saveAll(attackChainNodes);
  }

  private AttackChainNode buildAttackChainNode(
      @Nullable final AttackChainRun attackChainRun,
      @Nullable final AttackChain attackChain,
      @NotNull final AttackChainNodeInput input) {
    if (attackChainRun == null && attackChain == null
        || attackChainRun != null && attackChain != null) {
      throw new IllegalArgumentException("Exactly one of exercise or scenario should be present");
    }

    NodeContract nodeContract = this.nodeContractService.nodeContract(input.getNodeContract());
    // Get common attributes
    AttackChainNode attackChainNode = input.toAttackChainNode(nodeContract);
    attackChainNode.setUser(this.userService.currentUser());
    attackChainNode.setTeams(fromIterable(teamRepository.findAllById(input.getTeams())));
    attackChainNode.setAssets(fromIterable(assetService.assets(input.getAssets())));
    attackChainNode.setTags(tagService.tagSet(input.getTagIds()));
    List<AttackChainNodeDocument> attackChainNodeDocuments =
        input.getDocuments().stream()
            .map(i -> i.toDocument(documentService.document(i.getDocumentId()), attackChainNode))
            .toList();
    attackChainNode.setDocuments(attackChainNodeDocuments);
    // Set dependencies
    if (input.getDependsOn() != null) {
      attackChainNode
          .getDependsOn()
          .addAll(
              input.getDependsOn().stream()
                  .map(
                      attackChainEdgeInput ->
                          attackChainEdgeInput.toAttackChainEdge(
                              attackChainNode,
                              this.attackChainNode(
                                  attackChainEdgeInput
                                      .getRelationship()
                                      .getAttackChainNodeParentId())))
                  .toList());
    }

    Set<Tag> tags = new HashSet<>();
    // EXERCISE
    if (attackChainRun != null) {
      tags = attackChainRun.getTags();
      attackChainNode.setAttackChainRun(attackChainRun);
      // Linked documents directly to the attackChainRun
      attackChainNode
          .getDocuments()
          .forEach(
              document -> {
                if (!document.getDocument().getAttackChainRuns().contains(attackChainRun)) {
                  attackChainRun.getDocuments().add(document.getDocument());
                }
              });
    }
    // SCENARIO
    if (attackChain != null) {
      tags = attackChain.getTags();
      attackChainNode.setAttackChain(attackChain);
      // Linked documents directly to the attackChain
      attackChainNode
          .getDocuments()
          .forEach(
              document -> {
                if (!document.getDocument().getAttackChains().contains(attackChain)) {
                  attackChain.getDocuments().add(document.getDocument());
                }
              });
    }

    // verify if attackChainNode is not manual/sms/emails...
    if (this.canApplyTargetType(attackChainNode, TargetType.ASSETS_GROUPS)) {
      // add default asset groups
      attackChainNode.setAssetGroups(
          this.tagRuleService.applyTagRuleToAttackChainNodeCreation(
              tags.stream().map(Tag::getId).toList(),
              assetGroupService.assetGroups(input.getAssetGroups())));
    }

    // if attackChainNode content is null we add the defaults from the nodeExecutor contract
    // this is the case when creating an attackChainNode from OpenCti
    if (attackChainNode.getContent() == null || attackChainNode.getContent().isEmpty()) {
      attackChainNode.setContent(
          nodeContractContentUtils.getDynamicNodeContractFieldsForAttackChainNode(nodeContract));
    }

    return attackChainNode;
  }

  public AttackChainNode attackChainNode(@NotBlank final String attackChainNodeId) {
    return this.attackChainNodeRepository
        .findById(attackChainNodeId)
        .orElseThrow(
            () -> new ElementNotFoundException("Inject not found with id: " + attackChainNodeId));
  }

  /**
   * Builds an AttackChainNode object based on the provided NodeContract, title, description and
   * enabled
   *
   * @param nodeContract the NodeContract associated with the AttackChainNode
   * @param title the title of the AttackChainNode
   * @param description the description of the AttackChainNode
   * @param enabled indicates whether the AttackChainNode is enabled or not
   * @return the attackChainNode object built
   */
  public AttackChainNode buildAttackChainNode(
      NodeContract nodeContract, String title, String description, Boolean enabled) {
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setTitle(title);
    attackChainNode.setDescription(description);
    attackChainNode.setNodeContract(nodeContract);
    attackChainNode.setDependsDuration(0L);
    attackChainNode.setEnabled(enabled);
    attackChainNode.setContent(
        nodeContractContentUtils.getDynamicNodeContractFieldsForAttackChainNode(nodeContract));
    return attackChainNode;
  }

  /**
   * Builds a technical AttackChainNode object from the provided NodeContract and AttackPattern.
   *
   * @param nodeContract the NodeContract to build the AttackChainNode from
   * @param identifier the AttackPattern or Vulnerability associated with the AttackChainNode
   * @param name the AttackPattern or Vulnerability associated with the AttackChainNode
   * @return the built AttackChainNode object
   */
  public AttackChainNode buildTechnicalAttackChainNode(
      NodeContract nodeContract, String identifier, String name) {
    return buildAttackChainNode(
        nodeContract,
        String.format("[%s] %s - %s", identifier, name, nodeContract.getLabels().get("en")),
        null,
        true);
  }

  @Transactional(rollbackOn = Exception.class)
  public void deleteAllByIds(List<String> attackChainNodeIds) {
    if (!CollectionUtils.isEmpty(attackChainNodeIds)) {
      attackChainNodeRepository.deleteByAllIdsNative(attackChainNodeIds);
    }
  }

  /**
   * Delete all attackChainNodes given as params
   *
   * @param attackChainNodes the attackChainNodes to delete
   */
  @Transactional(rollbackOn = Exception.class)
  public void deleteAll(List<AttackChainNode> attackChainNodes) {
    if (!CollectionUtils.isEmpty(attackChainNodes)) {
      attackChainNodeRepository.deleteAll(attackChainNodes);
    }
  }

  /**
   * Save all attackChainNodes given as params
   *
   * @param attackChainNodes the attackChainNodes to save
   */
  @Transactional(rollbackOn = Exception.class)
  public List<AttackChainNode> saveAll(List<AttackChainNode> attackChainNodes) {
    if (!CollectionUtils.isEmpty(attackChainNodes)) {
      return attackChainNodeRepository.saveAll(attackChainNodes);
    }
    // empty collection
    return attackChainNodes;
  }

  // -- SPECIFIC GETTER --

  public List<AttackChainNode> getExecutedAndNotFinished() {
    return this.attackChainNodeRepository.findAll(
        hasStatus(List.of(SUCCESS, ERROR, MAYBE_PREVENTED, PARTIAL, MAYBE_PARTIAL_PREVENTED))
            .and(hasCollectingStatus(List.of(COLLECTING)))
            .and(fromRunningSimulation()));
  }

  // -- ASSETS --
  public List<AssetToExecute> resolveAllAssetsToExecute(
      @NotNull final AttackChainNode attackChainNode) {
    List<AssetToExecute> assetToExecutes = new ArrayList<>();

    attackChainNode.getAssets().forEach(asset -> assetToExecutes.add(new AssetToExecute(asset)));

    attackChainNode
        .getAssetGroups()
        .forEach(
            assetGroup -> {
              List<Asset> assetsFromGroup =
                  this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());

              assetsFromGroup.forEach(
                  asset -> {
                    AssetToExecute existingAssetToExecute =
                        assetToExecutes.stream()
                            .filter(as -> as.asset().getId().equals(asset.getId()))
                            .findFirst()
                            .orElse(null);

                    if (existingAssetToExecute != null) {
                      existingAssetToExecute.assetGroups().add(assetGroup);
                    } else {
                      AssetToExecute newAssetToExecute =
                          new AssetToExecute(asset, false, new ArrayList<>());
                      newAssetToExecute.assetGroups().add(assetGroup);
                      assetToExecutes.add(newAssetToExecute);
                    }
                  });
            });

    return assetToExecutes;
  }

  public void cleanAttackChainNodesDocAttackChainRun(String attackChainRunId, String documentId) {
    // Delete document from all attackChainRun attackChainNodes
    List<AttackChainNode> attackChainRunAttackChainNodes =
        attackChainNodeRepository.findAllForAttackChainRunAndDoc(attackChainRunId, documentId);
    List<AttackChainNodeDocument> updatedAttackChainNodes =
        attackChainRunAttackChainNodes.stream()
            .flatMap(
                attackChainNode -> {
                  @SuppressWarnings("UnnecessaryLocalVariable")
                  Stream<AttackChainNodeDocument> filterDocuments =
                      attackChainNode.getDocuments().stream()
                          .filter(document -> document.getDocument().getId().equals(documentId));
                  return filterDocuments;
                })
            .toList();
    attackChainNodeDocumentRepository.deleteAll(updatedAttackChainNodes);
  }

  public <T> T convertAttackChainNodeContent(
      @NotNull final AttackChainNode attackChainNode, @NotNull final Class<T> converter)
      throws Exception {
    ObjectNode content = attackChainNode.getContent();
    return this.mapper.treeToValue(content, converter);
  }

  public void cleanAttackChainNodesDocAttackChain(String attackChainId, String documentId) {
    // Delete document from all attackChain attackChainNodes
    List<AttackChainNode> attackChainAttackChainNodes =
        attackChainNodeRepository.findAllForAttackChainAndDoc(attackChainId, documentId);
    List<AttackChainNodeDocument> updatedAttackChainNodes =
        attackChainAttackChainNodes.stream()
            .flatMap(
                attackChainNode -> {
                  @SuppressWarnings("UnnecessaryLocalVariable")
                  Stream<AttackChainNodeDocument> filterDocuments =
                      attackChainNode.getDocuments().stream()
                          .filter(document -> document.getDocument().getId().equals(documentId));
                  return filterDocuments;
                })
            .toList();
    attackChainNodeDocumentRepository.deleteAll(updatedAttackChainNodes);
  }

  @Transactional
  public AttackChainNodeResultOverviewOutput duplicate(String id) {
    AttackChainNode duplicatedAttackChainNode = findAndDuplicateAttackChainNode(id);
    duplicatedAttackChainNode.setTitle(duplicateString(duplicatedAttackChainNode.getTitle()));
    AttackChainNode savedAttackChainNode =
        attackChainNodeRepository.save(duplicatedAttackChainNode);
    return attackChainNodeMapper.toAttackChainNodeResultOverviewOutput(savedAttackChainNode);
  }

  public void throwIfAttackChainNodeNotLaunchable(AttackChainNode attackChainNode) {
    // No license restrictions in community edition
  }

  @Transactional
  public AttackChainNodeResultOverviewOutput launch(String id) {
    AttackChainNode attackChainNode =
        attackChainNodeRepository.findById(id).orElseThrow(ElementNotFoundException::new);
    this.throwIfAttackChainNodeNotLaunchable(attackChainNode);
    attackChainNode.clean();
    attackChainNode.setUpdatedAt(Instant.now());
    AttackChainNode savedAttackChainNode = saveAttackChainNodeAndStatusAsQueuing(attackChainNode);
    return attackChainNodeMapper.toAttackChainNodeResultOverviewOutput(savedAttackChainNode);
  }

  @Transactional
  public AttackChainNodeResultOverviewOutput relaunch(String id) {
    AttackChainNode duplicatedAttackChainNode = findAndDuplicateAttackChainNode(id);
    this.throwIfAttackChainNodeNotLaunchable(duplicatedAttackChainNode);
    AttackChainNode savedAttackChainNode =
        saveAttackChainNodeAndStatusAsQueuing(duplicatedAttackChainNode);
    deleteForRelaunch(id, savedAttackChainNode.getId());
    return attackChainNodeMapper.toAttackChainNodeResultOverviewOutput(savedAttackChainNode);
  }

  @Transactional
  public void delete(String id) {
    attackChainNodeRepository.deleteById(id);
  }

  @Transactional
  public void deleteForRelaunch(String oldId, String newId) {
    attackChainNodeDocumentRepository.updateAttackChainNodeId(newId, oldId);
    attackChainNodeRepository.deleteByIdNative(oldId);
  }

  /**
   * Update an attackChainNode with default asset groups
   *
   * @param attackChainNodeId
   * @param defaultAssetGroupsToAdd
   * @return
   */
  @Transactional
  public AttackChainNode applyDefaultAssetGroupsToAttackChainNode(
      final String attackChainNodeId, final List<AssetGroup> defaultAssetGroupsToAdd) {

    // fetch the attackChainNode
    AttackChainNode attackChainNode =
        this.attackChainNodeRepository
            .findById(attackChainNodeId)
            .orElseThrow(ElementNotFoundException::new);

    // remove/add default asset groups and remove duplicates
    List<AssetGroup> currentAssetGroups = attackChainNode.getAssetGroups();

    Set<String> uniqueAssetGroupIds = new HashSet<>();
    List<AssetGroup> newListOfAssetGroups =
        Stream.concat(currentAssetGroups.stream(), defaultAssetGroupsToAdd.stream())
            .filter(assetGroup -> uniqueAssetGroupIds.add(assetGroup.getId()))
            .collect(Collectors.toList());

    if (new HashSet<>(currentAssetGroups).equals(new HashSet<>(newListOfAssetGroups))) {
      return attackChainNode;
    } else {
      attackChainNode.setAssetGroups(newListOfAssetGroups);
      return this.attackChainNodeRepository.save(attackChainNode);
    }
  }

  public boolean canApplyTargetType(final AttackChainNode attackChainNode, TargetType targetType) {
    Optional<NodeContract> ic = attackChainNode.getNodeContract();
    if (ic.isEmpty()) {
      return false;
    }
    return nodeContractService.checkTargetSupport(ic.get(), targetType);
  }

  public void assignAssetGroup(
      final AttackChainNode attackChainNode, List<AssetGroup> assetGroups) {
    if (this.canApplyTargetType(attackChainNode, TargetType.ASSETS_GROUPS)) {
      attackChainNode.setAssetGroups(assetGroups);
    } else if (this.canApplyTargetType(attackChainNode, TargetType.ASSETS)) {
      attackChainNode.setAssets(
          assetGroupService.assetsFromAssetGroupMap(assetGroups).values().stream()
              .flatMap(endpoints -> endpoints.stream().map(e -> (Asset) e))
              .collect(Collectors.toSet())
              .stream()
              .toList());
    } else {
      log.warn("Injector contract does not support either Asset Groups or Assets.");
    }
  }

  private AttackChainNode findAndDuplicateAttackChainNode(String id) {
    AttackChainNode attackChainNodeOrigin =
        attackChainNodeRepository.findById(id).orElseThrow(ElementNotFoundException::new);
    return AttackChainNodeUtils.duplicateAttackChainNode(attackChainNodeOrigin);
  }

  private AttackChainNode saveAttackChainNodeAndStatusAsQueuing(AttackChainNode attackChainNode) {
    AttackChainNode savedAttackChainNode = attackChainNodeRepository.save(attackChainNode);
    AttackChainNodeStatus attackChainNodeStatus =
        saveAttackChainNodeStatusAsQueuing(savedAttackChainNode);
    savedAttackChainNode.setStatus(attackChainNodeStatus);
    return savedAttackChainNode;
  }

  private AttackChainNodeStatus saveAttackChainNodeStatusAsQueuing(
      AttackChainNode attackChainNode) {
    AttackChainNodeStatus attackChainNodeStatus = new AttackChainNodeStatus();
    attackChainNodeStatus.setAttackChainNode(attackChainNode);
    attackChainNodeStatus.setTrackingSentDate(Instant.now());
    attackChainNodeStatus.setName(ExecutionStatus.QUEUING);
    this.attackChainNodeStatusRepository.save(attackChainNodeStatus);
    return attackChainNodeStatus;
  }

  /**
   * Get the attackChainNode specification for the search pagination input related where the user
   * has grants (through attackChains or simulations)
   *
   * @param input the search input
   * @param requestedGrantLevel the requested grant level to filter the attackChainNodes
   * @return the attackChainNode specification to search in DB
   * @throws BadRequestException if neither of the searchPaginationInput or
   *     attackChainNodeIDsToSearch is provided
   */
  public Specification<AttackChainNode> getAttackChainNodeSpecification(
      final AttackChainNodeBulkProcessingInput input, Grant.GRANT_TYPE requestedGrantLevel) {
    if ((CollectionUtils.isEmpty(input.getAttackChainNodeIDsToProcess())
            && (input.getSearchPaginationInput() == null))
        || (!CollectionUtils.isEmpty(input.getAttackChainNodeIDsToProcess())
            && (input.getSearchPaginationInput() != null))) {
      throw new BadRequestException(
          "Either inject_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    Specification<AttackChainNode> filterSpecifications =
        AttackChainNodeSpecification.fromAttackChainOrSimulation(
            input.getSimulationOrAttackChainId());
    if (input.getSearchPaginationInput() == null) {
      filterSpecifications =
          filterSpecifications.and(
              JpaUtils.computeIn(
                  AttackChainNode.ID_FIELD_NAME, input.getAttackChainNodeIDsToProcess()));
    } else {
      filterSpecifications =
          filterSpecifications.and(
              computeFilterGroupJpa(input.getSearchPaginationInput().getFilterGroup()));
      filterSpecifications =
          filterSpecifications.and(
              computeSearchJpa(input.getSearchPaginationInput().getTextSearch()));
    }
    if (!CollectionUtils.isEmpty(input.getAttackChainNodeIDsToIgnore())) {
      filterSpecifications =
          filterSpecifications.and(
              JpaUtils.computeNotIn(
                  AttackChainNode.ID_FIELD_NAME, input.getAttackChainNodeIDsToIgnore()));
    }
    // Filter out any attackChainNodes not related to resources where the user is granted with the
    // appropriate level
    filterSpecifications =
        filterSpecifications.and(hasGrantAccessForAttackChainNode(requestedGrantLevel));
    return filterSpecifications;
  }

  /**
   * Update attackChainNodes in bulk corresponding to the given criteria with a list of operations
   *
   * @param attackChainNodesToUpdate list of attackChainNodes to update
   * @param operations the operations to perform with fields and values to add, remove or replace
   * @return the list of updated attackChainNodes
   */
  public List<AttackChainNode> bulkUpdateAttackChainNode(
      final List<AttackChainNode> attackChainNodesToUpdate,
      final List<AttackChainNodeBulkUpdateOperation> operations) {
    // We aggregate the different field values in distinct sets in order to avoid retrieving the
    // same data multiple times
    Set<String> teamsIDs = new HashSet<>();
    Set<String> assetsIDs = new HashSet<>();
    Set<String> assetGroupsIDs = new HashSet<>();
    for (var operation : operations) {
      if (CollectionUtils.isEmpty(operation.getValues())) {
        continue;
      }

      switch (operation.getField()) {
        case TEAMS -> teamsIDs.addAll(operation.getValues());
        case ASSETS -> assetsIDs.addAll(operation.getValues());
        case ASSET_GROUPS -> assetGroupsIDs.addAll(operation.getValues());
        default ->
            throw new BadRequestException("Invalid field to update: " + operation.getOperation());
      }
    }

    // We retrieve the data from DB for teams, assets and asset groups in the input values
    Map<String, Team> teamsFromDB =
        this.teamRepository.findAllById(teamsIDs).stream()
            .collect(Collectors.toMap(Team::getId, team -> team));
    Map<String, Asset> assetsFromDB =
        this.assetService.assets(assetsIDs.stream().toList()).stream()
            .collect(Collectors.toMap(Asset::getId, asset -> asset));
    Map<String, AssetGroup> assetGroupsFromDB =
        this.assetGroupService.assetGroups(assetGroupsIDs.stream().toList()).stream()
            .collect(Collectors.toMap(AssetGroup::getId, assetGroup -> assetGroup));

    // we update the attackChainNodes values
    attackChainNodesToUpdate.forEach(
        attackChainNode -> {
          applyUpdateOperation(
              attackChainNode, operations, teamsFromDB, assetsFromDB, assetGroupsFromDB);
          attackChainNode.setUpdatedAt(now());
        });

    // Save updated attackChainNodes and return them
    return this.attackChainNodeRepository.saveAll(attackChainNodesToUpdate);
  }

  /**
   * Get the attackChainNodes to update/delete and check if the user is allowed to update/delete
   * them
   *
   * @param input the attackChainNodes search input.
   * @return the attackChainNodes to update/delete
   * @throws AccessDeniedException if the user is not allowed to update/delete the attackChainNodes
   */
  public List<AttackChainNode> getAttackChainNodesAndCheckPermission(
      AttackChainNodeBulkProcessingInput input, Grant.GRANT_TYPE requested_grant_level) {
    // Control and format inputs
    // Specification building
    Specification<AttackChainNode> filterSpecifications =
        getAttackChainNodeSpecification(input, requested_grant_level);

    // Services calls
    // Bulk select, only on attackChainNodes granted through attackChain or simulation (or without
    // grant for
    // atomic tests)
    return this.attackChainNodeRepository.findAll(filterSpecifications);
  }

  /**
   * Check if the user is allowed to operate on the attackChainNodes based on security challenge
   *
   * @param attackChainNodes the attackChainNodes to check
   * @param authoriseFunction the function to check if the user has the relevant privilege on
   *     attackChainNodes
   * @return List of all authorised AttackChainNodes
   */
  public AttackChainNodeAuthorisationResult authorise(
      List<AttackChainNode> attackChainNodes,
      BiFunction<SecurityExpression, String, Boolean> authoriseFunction) {
    AttackChainNodeAuthorisationResult result = new AttackChainNodeAuthorisationResult();
    for (AttackChainNode attackChainNode : attackChainNodes) {
      if (authoriseFunction.apply(getAmbientSecurityExpression(), attackChainNode.getId())) {
        result.addAuthorised(attackChainNode);
      } else {
        result.addUnauthorised(attackChainNode);
      }
    }
    return result;
  }

  public AttackChainNode updateAttackChainNode(
      @NotBlank final String attackChainNodeId,
      @jakarta.validation.constraints.NotNull AttackChainNodeInput input) {
    AttackChainNode attackChainNode =
        this.attackChainNodeRepository
            .findById(attackChainNodeId)
            .orElseThrow(ElementNotFoundException::new);
    attackChainNode.setUpdateAttributes(input);

    // Set dependencies
    if (input.getDependsOn() != null) {
      input
          .getDependsOn()
          .forEach(
              entry -> {
                Optional<AttackChainEdge> existingDependency =
                    attackChainNode.getDependsOn().stream()
                        .filter(
                            attackChainEdge ->
                                attackChainEdge
                                    .getCompositeId()
                                    .getAttackChainNodeParent()
                                    .getId()
                                    .equals(entry.getRelationship().getAttackChainNodeParentId()))
                        .findFirst();
                if (existingDependency.isPresent()) {
                  existingDependency
                      .get()
                      .getAttackChainEdgeCondition()
                      .setConditions(entry.getConditions().getConditions());
                  existingDependency
                      .get()
                      .getAttackChainEdgeCondition()
                      .setMode(entry.getConditions().getMode());
                } else {
                  AttackChainEdge attackChainEdge = new AttackChainEdge();
                  attackChainEdge.getCompositeId().setAttackChainNodeChildren(attackChainNode);
                  attackChainEdge
                      .getCompositeId()
                      .setAttackChainNodeParent(
                          attackChainNodeRepository
                              .findById(entry.getRelationship().getAttackChainNodeParentId())
                              .orElse(null));
                  attackChainEdge.setAttackChainEdgeCondition(
                      new AttackChainEdgeConditions.AttackChainEdgeCondition());
                  attackChainEdge
                      .getAttackChainEdgeCondition()
                      .setConditions(entry.getConditions().getConditions());
                  attackChainEdge
                      .getAttackChainEdgeCondition()
                      .setMode(entry.getConditions().getMode());
                  attackChainNode.getDependsOn().add(attackChainEdge);
                }
              });
    }

    List<AttackChainEdge> attackChainNodeDepencyToRemove = new ArrayList<>();
    if (attackChainNode.getDependsOn() != null && !attackChainNode.getDependsOn().isEmpty()) {
      if (input.getDependsOn() != null && !input.getDependsOn().isEmpty()) {
        attackChainNode
            .getDependsOn()
            .forEach(
                attackChainEdge -> {
                  if (!input.getDependsOn().stream()
                      .map(
                          (attackChainEdgeInput ->
                              attackChainEdgeInput.getRelationship().getAttackChainNodeParentId()))
                      .toList()
                      .contains(
                          attackChainEdge.getCompositeId().getAttackChainNodeParent().getId())) {
                    attackChainNodeDepencyToRemove.add(attackChainEdge);
                  }
                });
      } else {
        attackChainNodeDepencyToRemove.addAll(attackChainNode.getDependsOn());
      }
      attackChainNode.getDependsOn().removeAll(attackChainNodeDepencyToRemove);
    }

    attackChainNode.setTeams(fromIterable(this.teamRepository.findAllById(input.getTeams())));
    attackChainNode.setAssets(fromIterable(this.assetService.assets(input.getAssets())));
    attackChainNode.setAssetGroups(
        fromIterable(this.assetGroupService.assetGroups(input.getAssetGroups())));
    attackChainNode.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));

    // Set documents
    List<AttackChainNodeDocumentInput> inputDocuments = input.getDocuments();
    List<AttackChainNodeDocument> attackChainNodeDocuments = attackChainNode.getDocuments();

    List<String> askedDocumentIds =
        inputDocuments.stream().map(AttackChainNodeDocumentInput::getDocumentId).toList();
    List<String> currentDocumentIds =
        attackChainNode.getDocuments().stream()
            .map(document -> document.getDocument().getId())
            .toList();
    // To delete
    List<AttackChainNodeDocument> toRemoveDocuments =
        attackChainNodeDocuments.stream()
            .filter(
                attackChainNodeDoc ->
                    !askedDocumentIds.contains(attackChainNodeDoc.getDocument().getId()))
            .toList();
    attackChainNodeDocuments.removeAll(toRemoveDocuments);
    // To add
    inputDocuments.stream()
        .filter(doc -> !currentDocumentIds.contains(doc.getDocumentId()))
        .forEach(
            in -> {
              Optional<Document> doc = this.documentRepository.findById(in.getDocumentId());
              if (doc.isPresent()) {
                AttackChainNodeDocument attackChainNodeDocument = new AttackChainNodeDocument();
                attackChainNodeDocument.setAttackChainNode(attackChainNode);
                Document document = doc.get();
                attackChainNodeDocument.setDocument(document);
                attackChainNodeDocument.setAttached(in.isAttached());
                AttackChainNodeDocument savedAttackChainNodeDoc =
                    this.attackChainNodeDocumentRepository.save(attackChainNodeDocument);
                attackChainNodeDocuments.add(savedAttackChainNodeDoc);
              }
            });
    // Remap the attached boolean
    attackChainNodeDocuments.forEach(
        attackChainNodeDoc -> {
          Optional<AttackChainNodeDocumentInput> inputAttackChainNodeDoc =
              input.getDocuments().stream()
                  .filter(id -> id.getDocumentId().equals(attackChainNodeDoc.getDocument().getId()))
                  .findFirst();
          Boolean attached =
              inputAttackChainNodeDoc.map(AttackChainNodeDocumentInput::isAttached).orElse(false);
          attackChainNodeDoc.setAttached(attached);
        });
    attackChainNode.setDocuments(attackChainNodeDocuments);

    return attackChainNode;
  }

  public AttackChainNode updateAttackChainNodeActivation(
      @NotBlank final String attackChainNodeId,
      @jakarta.validation.constraints.NotNull final AttackChainNodeUpdateActivationInput input) {
    AttackChainNode attackChainNode =
        this.attackChainNodeRepository
            .findById(attackChainNodeId)
            .orElseThrow(ElementNotFoundException::new);
    attackChainNode.setEnabled(input.isEnabled());
    attackChainNode.setUpdatedAt(now());
    return attackChainNodeRepository.save(attackChainNode);
  }

  /**
   * Update the attackChainNode with the given input
   *
   * @param attackChainNodeToUpdate the attackChainNode to update
   * @param operations the operation to perform, with the values to add, remove or replace
   * @param teamsFromDB the teams from the DB, coming from the input values
   * @param assetsFromDB the assets from the DB, coming from the input values
   * @param assetGroupsFromDB the asset groups from the DB, coming from the input values
   */
  private void applyUpdateOperation(
      AttackChainNode attackChainNodeToUpdate,
      List<AttackChainNodeBulkUpdateOperation> operations,
      Map<String, Team> teamsFromDB,
      Map<String, Asset> assetsFromDB,
      Map<String, AssetGroup> assetGroupsFromDB) {
    if (CollectionUtils.isEmpty(operations)) {
      return;
    }

    for (var operation : operations) {
      switch (operation.getField()) {
        case TEAMS ->
            updateAttackChainNodeEntities(
                attackChainNodeToUpdate.getTeams(),
                operation.getValues(),
                teamsFromDB,
                operation.getOperation());
        case ASSETS ->
            updateAttackChainNodeEntities(
                attackChainNodeToUpdate.getAssets(),
                operation.getValues(),
                assetsFromDB,
                operation.getOperation());
        case ASSET_GROUPS ->
            updateAttackChainNodeEntities(
                attackChainNodeToUpdate.getAssetGroups(),
                operation.getValues(),
                assetGroupsFromDB,
                operation.getOperation());
        default ->
            throw new BadRequestException("Invalid field to update: " + operation.getField());
      }
    }
  }

  public void resetAttackChainNodeByAttackChainRunId(String simulationId) {
    List<AttackChainNode> attackChainNodes =
        attackChainNodeRepository.findAllAttackChainNodeBySimulationId(simulationId);
    if (attackChainNodes.isEmpty()) return;
    attackChainNodes.forEach(AttackChainNode::clean);
    attackChainNodeRepository.saveAll(attackChainNodes);
  }

  /**
   * Update the attackChainNode entities
   *
   * @param attackChainNodeEntities the attackChainNode entities to update
   * @param newValuesIDs the IDs of the value to add, remove or replace
   * @param entitiesFromDB the entities from the DB
   * @param operation the operation to apply
   * @param <T> the type of the entities
   */
  private <T> void updateAttackChainNodeEntities(
      List<T> attackChainNodeEntities,
      List<String> newValuesIDs,
      Map<String, T> entitiesFromDB,
      AttackChainNodeBulkUpdateSupportedOperations operation) {
    if (operation == AttackChainNodeBulkUpdateSupportedOperations.REPLACE) {
      attackChainNodeEntities.clear();
    }
    newValuesIDs.forEach(
        id -> {
          T entity = entitiesFromDB.get(id);
          if (entity == null) {
            log.warn("Inject update entity with ID {} not found in the DB", id);
            return;
          }

          switch (operation) {
            case REPLACE, ADD -> {
              if (!attackChainNodeEntities.contains(entity)) {
                attackChainNodeEntities.add(entity);
              }
            }
            case REMOVE -> attackChainNodeEntities.remove(entity);
            default ->
                throw new BadRequestException(
                    "Invalid operation to update inject entities: " + operation);
          }
        });
  }

  public AgentsAndAssetsAgentless getAgentsAndAgentlessAssetsByAttackChainNode(
      AttackChainNode attackChainNode) {
    Set<Agent> agents = new HashSet<>();
    Set<Asset> assetsAgentless = new HashSet<>();

    for (Asset asset : attackChainNode.getAssets()) {
      extractAgentsAndAssetsAgentless(agents, assetsAgentless, asset);
    }

    for (AssetGroup assetGroup : attackChainNode.getAssetGroups()) {
      for (Asset asset : assetGroupService.assetsFromAssetGroup(assetGroup.getId())) {
        extractAgentsAndAssetsAgentless(agents, assetsAgentless, asset);
      }
    }

    return new AgentsAndAssetsAgentless(agents, assetsAgentless);
  }

  private void extractAgentsAndAssetsAgentless(
      Set<Agent> agents, Set<Asset> assetsAgentless, Asset asset) {
    List<Agent> collectedAgents =
        Optional.ofNullable(((Endpoint) Hibernate.unproxy(asset)).getAgents())
            .orElse(Collections.emptyList());
    if (collectedAgents.isEmpty()) {
      assetsAgentless.add(asset);
    } else {
      for (Agent agent : collectedAgents) {
        if (isPrimaryAgent(agent)) {
          agents.add(agent);
        }
      }
    }
  }

  public List<Agent> getAgentsByAttackChainNode(AttackChainNode attackChainNode) {
    List<Agent> agents = new ArrayList<>();
    Set<String> agentIds = new HashSet<>();

    Consumer<Asset> extractAgents =
        asset -> {
          List<Agent> collectedAgents =
              Optional.ofNullable(((Endpoint) Hibernate.unproxy(asset)).getAgents())
                  .orElse(Collections.emptyList());
          for (Agent agent : collectedAgents) {
            if (isPrimaryAgent(agent) && !agentIds.contains(agent.getId())) {
              agents.add(agent);
              agentIds.add(agent.getId());
            }
          }
        };

    new ArrayList<>(attackChainNode.getAssets()).forEach(extractAgents);
    attackChainNode.getAssetGroups().stream()
        .flatMap(assetGroup -> assetGroupService.assetsFromAssetGroup(assetGroup.getId()).stream())
        .forEach(extractAgents);

    return agents;
  }

  public List<FilterUtilsJpa.Option> getOptionsByNameLinkedToFindings(
      String searchText, String sourceId, Pageable pageable) {
    String trimmedSearchText = StringUtils.trimToNull(searchText);
    String trimmedSimulationOrAttackChainId = StringUtils.trimToNull(sourceId);

    List<Object[]> results;

    if (trimmedSimulationOrAttackChainId == null) {
      results =
          attackChainNodeRepository.findAllByTitleLinkedToFindings(trimmedSearchText, pageable);
    } else {
      results =
          attackChainNodeRepository.findAllByTitleLinkedToFindingsWithContext(
              trimmedSimulationOrAttackChainId, trimmedSearchText, pageable);
    }

    return results.stream()
        .map(i -> new FilterUtilsJpa.Option((String) i[0], (String) i[1]))
        .toList();
  }

  public List<ExecutionTraceOutput> getAttackChainNodeTracesOutputFromAttackChainNodeAndTarget(
      final String attackChainNodeId, final String targetId, final TargetType targetType) {
    return toExecutionTracesOutput(
        getAttackChainNodeTracesFromAttackChainNodeAndTarget(
            attackChainNodeId, targetId, targetType));
  }

  public List<ExecutionTrace> getAttackChainNodeTracesFromAttackChainNodeAndTarget(
      final String attackChainNodeId, final String targetId, final TargetType targetType) {
    return switch (targetType) {
      case AGENT ->
          this.executionTraceRepository.findByAttackChainNodeIdAndAgentId(
              attackChainNodeId, targetId);
      case ASSETS ->
          this.executionTraceRepository.findByAttackChainNodeIdAndAssetId(
              attackChainNodeId, targetId);
      case TEAMS ->
          this.executionTraceRepository.findByAttackChainNodeIdAndTeamId(
              attackChainNodeId, targetId);
      case PLAYERS ->
          this.executionTraceRepository.findByAttackChainNodeIdAndPlayerId(
              attackChainNodeId, targetId);
      default -> throw new BadRequestException("Target type " + targetType + " is not supported");
    };
  }

  public AttackChainNodeStatusOutput getAttackChainNodeStatusWithGlobalExecutionTraces(
      String attackChainNodeId) {
    return attackChainNodeStatusMapper.toAttackChainNodeStatusOutput(
        attackChainNodeStatusRepository.findAttackChainNodeStatusWithGlobalExecutionTraces(
            attackChainNodeId));
  }

  /**
   * Function used to get the targeted property field of a targeted asset.
   *
   * @param nodeContractFields NodeContract Fields from where to extract the targeted property
   * @param targetedAssetKey The key of the targeted Asset field
   * @return the object node of targetedProperty field
   */
  private ObjectNode getTargetedPropertyFieldOfTargetedAsset(
      List<ObjectNode> nodeContractFields, String targetedAssetKey) {
    return nodeContractFields.stream()
        .filter(
            f -> f.get("key").asText().startsWith(CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY))
        .filter(
            f -> {
              JsonNode linkedFieldsNode = f.get("linkedFields");
              return linkedFieldsNode != null
                  && linkedFieldsNode.isArray()
                  && StreamSupport.stream(linkedFieldsNode.spliterator(), false)
                      .anyMatch(
                          linkedField -> targetedAssetKey.equals(linkedField.get("key").asText()));
            })
        .findFirst()
        .orElse(null);
  }

  /**
   * Get a map of value (e.g., hostname, seen_ip ) for targeted assets of attackChainNode
   *
   * @param attackChainNode attackChainNode to extract the targeted assets from
   * @return a map where the key is the value of the targeted asset (e.g., hostname, seen_ip) and
   *     the value is the Endpoint object representing the targeted asset
   */
  public Map<String, Endpoint> getValueTargetedAssetMap(AttackChainNode attackChainNode) {
    Map<String, Endpoint> valueTargetedAssetsMap = new HashMap<>();
    NodeContract nodeContract = attackChainNode.getNodeContract().orElseThrow();

    JsonNode nodeContractFieldsNode = nodeContract.getConvertedContent().get("fields");
    if (nodeContractFieldsNode == null || !nodeContractFieldsNode.isArray()) {
      return valueTargetedAssetsMap;
    }

    List<ObjectNode> nodeContractFields =
        StreamSupport.stream(nodeContractFieldsNode.spliterator(), false)
            .map(ObjectNode.class::cast)
            .toList();

    // Get all fields of type TargetedAsset
    List<ObjectNode> targetedAssetFields =
        nodeContractFields.stream()
            .filter(
                node ->
                    node.has("type")
                        && ContractFieldType.TargetedAsset.label.equals(node.get("type").asText()))
            .toList();

    targetedAssetFields.forEach(
        f -> {
          // For each targeted asset field, retrieve the values of the targeted assets based on the
          // targeted property
          String keyField = f.get("key").asText();
          Map<String, Endpoint> valuesAssetsMap =
              this.retrieveValuesOfTargetedAssetFromAttackChainNode(
                  nodeContractFields, attackChainNode.getContent(), keyField);
          valueTargetedAssetsMap.putAll(valuesAssetsMap);
        });

    return valueTargetedAssetsMap;
  }

  /**
   * Function used to retrieve the targetedAsset value from an AttackChainNode.
   *
   * @param nodeContractContentFields NodeContract Content fields from which to retrieve all the
   *     fields set on the attackChainNode
   * @param attackChainNodeContent AttackChainNode content to obtain the value set on an
   *     attackChainNode
   * @param targetedAssetKey The targeted asset key for which we want to retrieve values (can have
   *     many assets set on one targeted asset key)
   * @return a map where the key is the value of the targeted asset (e.g., hostname, seen_ip) and
   *     the value is the Endpoint object representing the targeted asset
   */
  public Map<String, Endpoint> retrieveValuesOfTargetedAssetFromAttackChainNode(
      List<ObjectNode> nodeContractContentFields,
      ObjectNode attackChainNodeContent,
      String targetedAssetKey) {
    Map<String, Endpoint> valueTargetedAssetsMap = new HashMap<>();
    List<String> assetIds =
        mapper.convertValue(
            attackChainNodeContent.get(targetedAssetKey), new TypeReference<List<String>>() {});
    List<Endpoint> endpointList = endpointService.endpoints(assetIds);

    ObjectNode targetedPropertiesField =
        getTargetedPropertyFieldOfTargetedAsset(nodeContractContentFields, targetedAssetKey);

    if (targetedPropertiesField == null) {
      throw new BadRequestException(
          "No targeted property field found for key: " + targetedAssetKey);
    }

    String targetedPropertyKey = targetedPropertiesField.get("key").asText();
    String targetedPropertyValue =
        attackChainNodeContent.has(targetedPropertyKey)
            ? attackChainNodeContent.get(targetedPropertyKey).asText()
            : targetedPropertiesField.get("defaultValue").get(0).asText();

    ContractTargetedProperty contractTargetedProperty =
        ContractTargetedProperty.valueOf(targetedPropertyValue);

    endpointList.forEach(
        endpoint -> {
          String endpointValue = contractTargetedProperty.toEndpointValue.apply(endpoint);
          valueTargetedAssetsMap.put(endpointValue, endpoint);
        });

    return valueTargetedAssetsMap;
  }

  /**
   * Function used to fetch the detection remediations in a attackChainNode based on payload
   * definition.
   *
   * @param attackChainNodeId
   * @return a list of detection remediations
   */
  public List<DetectionRemediation> fetchDetectionRemediationsByAttackChainNodeId(
      String attackChainNodeId) {
    return payloadRepository.fetchDetectionRemediationsByAttackChainNodeId(attackChainNodeId);
  }

  /**
   * Check if a user is granted on an attackChainNode. A user can be granteed on an attackChainNode
   * if: - The attackChainNode is linked to a attackChain or simulation that the user has access to
   * - The attackChainNode is an atomic testing and the user has access to it
   *
   * @param grantType the grant type to check
   * @return a Specification that checks if the user has access to the attackChainNode
   */
  public Specification<AttackChainNode> hasGrantAccessForAttackChainNode(
      Grant.GRANT_TYPE grantType) {

    User currentUser = userService.currentUser();
    boolean hasCapabilityAccessAssessment =
        currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)
            || currentUser.getCapabilities().contains(Capability.BYPASS);

    return (root, query, cb) -> {
      if (currentUser.isAdmin() || hasCapabilityAccessAssessment) {
        return cb.conjunction();
      }

      // Ensure distinct results (joins/subqueries can otherwise produce duplicates).
      query.distinct(true);

      // Use the id expressions for parents (safer than testing the entity path itself)
      Expression<String> attackChainIdPath = root.get("attackChain").get("id");
      Expression<String> attackChainRunIdPath = root.get("attackChainRun").get("id");
      // Check if both are null -> atomic testing case
      Predicate bothParentsNull =
          cb.and(cb.isNull(attackChainIdPath), cb.isNull(attackChainRunIdPath));

      // Get allowed grant types
      List<Grant.GRANT_TYPE> allowedGrantTypes = grantType.andHigher();

      // Create subquery for accessible attackChains
      Subquery<String> accessibleAttackChains =
          SpecificationUtils.accessibleResourcesSubquery(
              query,
              cb,
              currentUser.getId(),
              Grant.GRANT_RESOURCE_TYPE.SCENARIO,
              allowedGrantTypes);
      // Create subquery for accessible simulations
      Subquery<String> accessibleSimulations =
          SpecificationUtils.accessibleResourcesSubquery(
              query,
              cb,
              currentUser.getId(),
              Grant.GRANT_RESOURCE_TYPE.SIMULATION,
              allowedGrantTypes);
      // Create subquery for accessible atomic testings
      Subquery<String> accessibleAtomicTestings =
          SpecificationUtils.accessibleResourcesSubquery(
              query,
              cb,
              currentUser.getId(),
              Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING,
              allowedGrantTypes);

      return cb.or(
          // Case 1: atomic test (no parents, direct grant required)
          cb.and(
              cb.isNull(root.get("attackChain")),
              cb.isNull(root.get("attackChainRun")),
              root.get("id").in(accessibleAtomicTestings)),
          // Case 2: linked to a attackChain, and user has access
          cb.and(
              cb.isNotNull(root.get("attackChain")),
              root.get("attackChain").get("id").in(accessibleAttackChains)),
          // Case 3: linked to a simulation, and user has access
          cb.and(
              cb.isNotNull(root.get("attackChainRun")),
              root.get("attackChainRun").get("id").in(accessibleSimulations)));
    };
  }

  /**
   * Extracts the attackChainNode coverage from the attackChain's attackChainNodes, mapping each
   * attackChainNode to its set of (AttackPattern × Platform × Architecture) combinations.
   *
   * @param attackChain the attackChain containing attackChainNodes
   * @return a map of attackChainNodes to their AttackPattern-platform-architecture combinations
   */
  public Map<AttackChainNode, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>>
      extractCombinationAttackPatternPlatformArchitecture(AttackChain attackChain) {

    return attackChain.getAttackChainNodes().stream()
        .filter(
            attackChainNode ->
                attackChainNode.getPayload().isEmpty()
                    || !(attackChainNode.getPayload().get() instanceof DnsResolution))
        .map(
            attackChainNode ->
                attackChainNode.getNodeContract().map(ic -> Map.entry(attackChainNode, ic)))
        .flatMap(Optional::stream)
        // Only keep attack patterns that specify both platform and architecture.
        // Other cases should be reviewed depending on the nodeExecutor contract source:
        // vulnerability,
        // placeholder, other
        .filter(
            entry ->
                entry.getValue().getArch() != null
                    && entry.getValue().getPlatforms() != null
                    && entry.getValue().getPlatforms().length > 0)
        .map(
            entry -> {
              AttackChainNode attackChainNode = entry.getKey();
              NodeContract ic = entry.getValue();

              // Extract archs
              Set<String> archs =
                  ALL_ARCHITECTURES.equals(ic.getArch())
                      ? Set.of(arm64.name(), x86_64.name())
                      : Set.of(ic.getArch().name());

              // Extract platforms
              Set<Endpoint.PLATFORM_TYPE> platforms =
                  new HashSet<>(Arrays.asList(ic.getPlatforms()));

              // Generate combinations
              Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> combinations =
                  buildCombinationsAttackPatternPlatformsArchitectures(
                      ic.getAttackPatterns(), platforms, archs);

              return Map.entry(attackChainNode, combinations);
            })
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> {
                  Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> merged = new HashSet<>(v1);
                  merged.addAll(v2);
                  return v1;
                }));
  }

  /**
   * Builds the complete set of required combinations of TTPs and platform-architecture pairs.
   *
   * @param attackPatterns list of attack patterns (TTPs)
   * @param platforms set of platforms
   * @param architectures set of architecture
   * @return set of (TTP × Platform × Architecture) combinations
   */
  public static Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>
      buildCombinationsAttackPatternPlatformsArchitectures(
          List<AttackPattern> attackPatterns,
          Set<Endpoint.PLATFORM_TYPE> platforms,
          Set<String> architectures) {

    if (attackPatterns == null || platforms == null || architectures == null) {
      return Collections.emptySet();
    }

    return attackPatterns.stream()
        .flatMap(
            attackPattern -> {
              String id = attackPattern.getId();
              return platforms.stream()
                  .flatMap(
                      platform ->
                          architectures.stream()
                              .map(architecture -> Triple.of(id, platform, architecture)));
            })
        .collect(Collectors.toSet());
  }

  /**
   * Retrieve the payload linked to an attackChainNode
   *
   * @param attackChainNodeId to search payload
   * @return found payload
   * @throws ElementNotFoundException if attackChainNode or payload is not found
   */
  public Payload getPayloadByAttackChainNodeId(String attackChainNodeId) {
    AttackChainNode attackChainNode = attackChainNode(attackChainNodeId);
    return attackChainNode
        .getPayload()
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "payload not found on inject with id : " + attackChainNodeId));
  }

  /**
   * Verify all healthcheck for a given attackChainNode
   *
   * @param attackChainNode to verify
   * @return converted attackChainNode to AttackChainNodeOutput with healthcheck values
   */
  public List<HealthCheck> runChecks(final AttackChainNode attackChainNode) {
    if (attackChainNode == null) {
      return null;
    }

    List<Collector> collectors = this.collectorService.securityPlatformCollectors();
    List<NodeExecutor> nodeExecutors = this.nodeExecutorService.findAll();
    List<HealthCheck> healthChecks = new ArrayList<>();

    healthChecks.addAll(
        healthCheckUtils.runMailServiceChecks(
            attackChainNode,
            ExternalServiceDependency.SMTP,
            smtpService.isServiceAvailable(),
            HealthCheck.Type.SMTP,
            HealthCheck.Status.ERROR));
    healthChecks.addAll(
        healthCheckUtils.runMailServiceChecks(
            attackChainNode,
            ExternalServiceDependency.IMAP,
            imapService.isServiceAvailable(),
            HealthCheck.Type.IMAP,
            HealthCheck.Status.WARNING));
    healthChecks.addAll(
        healthCheckUtils.runExecutorChecks(
            attackChainNode, this.getAgentsAndAgentlessAssetsByAttackChainNode(attackChainNode)));
    healthChecks.addAll(healthCheckUtils.runCollectorChecks(attackChainNode, collectors));
    healthChecks.addAll(healthCheckUtils.runAllNodeExecutorChecks(attackChainNode, nodeExecutors));

    return healthChecks;
  }

  /**
   * Extract all security platform from a list of attackChainNodes
   *
   * @param attackChainNodes to extract security platforms
   * @return distinct security platforms
   */
  public List<SecurityPlatform> extractSecurityPlatforms(List<AttackChainNode> attackChainNodes) {
    Stream<AttackChainNodeExpectation> allAttackChainNodeExpectationsStream =
        extractAttackChainNodeExpectationsFromAttackChainNodes(attackChainNodes);
    Set<String> assetIds =
        extractAssetIdsFromAttackChainNodeExpectationsResults(allAttackChainNodeExpectationsStream);
    return assetService.securityPlatformsByIds(assetIds);
  }
}
