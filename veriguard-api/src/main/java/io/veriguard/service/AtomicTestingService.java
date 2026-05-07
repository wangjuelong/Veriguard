package io.veriguard.service;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.model.NodeContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.veriguard.database.model.NodeContract.PREDEFINED_EXPECTATIONS;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.database.specification.AttackChainNodeSpecification;
import io.veriguard.database.specification.SpecificationUtils;
import io.veriguard.injector_contract.fields.ContractFieldType;
import io.veriguard.rest.atomic_testing.form.*;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.utils.mapper.AttackChainNodeMapper;
import io.veriguard.utils.mapper.PayloadMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Join;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AtomicTestingService {

  @Resource protected ObjectMapper mapper;
  private final AttackChainNodeMapper attackChainNodeMapper;

  private final AssetGroupRepository assetGroupRepository;
  private final AssetRepository assetRepository;
  private final PayloadMapper payloadMapper;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final NodeContractRepository nodeContractRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;
  private final AssetGroupService assetGroupService;
  private final UserService userService;
  private final AttackChainNodeSearchService attackChainNodeSearchService;
  private final AttackChainNodeService attackChainNodeService;
  private final GrantService grantService;
  private final AttackChainNodeDocumentRepository attackChainNodeDocumentRepository;

  // -- CRUD --

  public AttackChainNodeResultOverviewOutput findById(String attackChainNodeId) {
    Optional<AttackChainNode> attackChainNode =
        attackChainNodeRepository.findWithStatusById(attackChainNodeId);

    if (attackChainNode.isPresent()) {
      List<AssetGroup> computedAssetGroup =
          attackChainNode.get().getAssetGroups().stream()
              .map(assetGroupService::computeDynamicAssets)
              .toList();
      attackChainNode.get().getAssetGroups().clear();
      attackChainNode.get().getAssetGroups().addAll(computedAssetGroup);
    }
    return attackChainNode
        .map(attackChainNodeMapper::toAttackChainNodeResultOverviewOutput)
        .orElseThrow(ElementNotFoundException::new);
  }

  public StatusPayloadOutput findPayloadOutputByAttackChainNodeId(String attackChainNodeId) {
    Optional<AttackChainNode> attackChainNode =
        attackChainNodeRepository.findById(attackChainNodeId);
    return payloadMapper.getStatusPayloadOutputFromAttackChainNode(attackChainNode);
  }

  @Transactional
  public AttackChainNodeResultOverviewOutput createOrUpdate(
      AtomicTestingInput input, String attackChainNodeId) {
    AttackChainNode attackChainNodeToSave = new AttackChainNode();
    if (attackChainNodeId != null) {
      attackChainNodeToSave = attackChainNodeRepository.findById(attackChainNodeId).orElseThrow();
    }

    NodeContract nodeContract =
        nodeContractRepository
            .findById(input.getNodeContract())
            .orElseThrow(ElementNotFoundException::new);
    ObjectNode finalContent = input.getContent();
    // Set expectations
    if (attackChainNodeId == null) {
      finalContent = setExpectations(input, nodeContract, finalContent);
    }
    attackChainNodeToSave.setTitle(input.getTitle());
    attackChainNodeToSave.setContent(finalContent);
    attackChainNodeToSave.setNodeContract(nodeContract);
    attackChainNodeToSave.setAllTeams(input.isAllTeams());
    attackChainNodeToSave.setDescription(input.getDescription());
    attackChainNodeToSave.setDependsDuration(0L);
    attackChainNodeToSave.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    attackChainNodeToSave.setAttackChainRun(null);

    // Set dependencies
    attackChainNodeToSave.setTeams(fromIterable(teamRepository.findAllById(input.getTeams())));
    attackChainNodeToSave.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    attackChainNodeToSave.setAssets(
        fromIterable(this.assetRepository.findAllById(input.getAssets())));
    attackChainNodeToSave.setAssetGroups(
        fromIterable(this.assetGroupRepository.findAllById(input.getAssetGroups())));

    attackChainNodeToSave.getDocuments().clear();

    AttackChainNode finalAttackChainNodeToSave = attackChainNodeToSave;
    input
        .getDocuments()
        .forEach(
            i -> {
              AttackChainNodeDocumentId attackChainNodeDocumentId = new AttackChainNodeDocumentId();
              attackChainNodeDocumentId.setAttackChainNodeId(finalAttackChainNodeToSave.getId());
              attackChainNodeDocumentId.setDocumentId(i.getDocumentId());
              AttackChainNodeDocument attackChainNodeDocument =
                  attackChainNodeDocumentRepository
                      .findById(attackChainNodeDocumentId)
                      .orElse(new AttackChainNodeDocument());
              if (attackChainNodeDocument.getAttackChainNode() == null) {
                attackChainNodeDocument.setCompositeId(attackChainNodeDocumentId);
                attackChainNodeDocument.setAttackChainNode(finalAttackChainNodeToSave);
                attackChainNodeDocument.setDocument(
                    documentRepository.findById(i.getDocumentId()).orElseThrow());
              }
              attackChainNodeDocument.setAttached(i.isAttached());
              finalAttackChainNodeToSave
                  .getDocuments()
                  .add(
                      attackChainNodeId == null
                          ? attackChainNodeDocument
                          : attackChainNodeDocumentRepository.save(attackChainNodeDocument));
            });

    attackChainNodeToSave = attackChainNodeRepository.save(attackChainNodeToSave);
    return attackChainNodeMapper.toAttackChainNodeResultOverviewOutput(attackChainNodeToSave);
  }

  private ObjectNode setExpectations(
      AtomicTestingInput input, NodeContract nodeContract, ObjectNode finalContent) {
    if (input.getContent() == null
        || input.getContent().get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS) == null
        || input.getContent().get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS).isEmpty()) {
      try {
        JsonNode jsonNode = mapper.readTree(nodeContract.getContent());
        List<JsonNode> contractElements =
            StreamSupport.stream(jsonNode.get("fields").spliterator(), false)
                .filter(
                    contractElement ->
                        contractElement
                            .get("type")
                            .asText()
                            .equals(ContractFieldType.Expectation.name().toLowerCase()))
                .toList();
        if (!contractElements.isEmpty()) {
          JsonNode contractElement = contractElements.getFirst();
          if (!contractElement.get(PREDEFINED_EXPECTATIONS).isNull()
              && !contractElement.get(PREDEFINED_EXPECTATIONS).isEmpty()) {
            finalContent = finalContent != null ? finalContent : mapper.createObjectNode();
            ArrayNode predefinedExpectations = mapper.createArrayNode();
            StreamSupport.stream(contractElement.get(PREDEFINED_EXPECTATIONS).spliterator(), false)
                .forEach(
                    predefinedExpectation -> {
                      ObjectNode newExpectation = predefinedExpectation.deepCopy();
                      newExpectation.put("expectation_score", 100);
                      predefinedExpectations.add(newExpectation);
                    });
            // We need the remove in case there are empty expectations because put is deprecated and
            // putifabsent doesn't replace empty expectations
            if (finalContent.has(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS)
                && finalContent.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS).isEmpty()) {
              finalContent.remove(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
            }
            finalContent.putIfAbsent(
                CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, predefinedExpectations);
          }
        }
      } catch (JsonProcessingException e) {
        log.error("Cannot open injector contract", e);
      }
    }
    return finalContent;
  }

  @Transactional
  public AttackChainNodeResultOverviewOutput updateAtomicTestingTags(
      String attackChainNodeId, AtomicTestingUpdateTagsInput input) {

    AttackChainNode attackChainNode =
        attackChainNodeRepository.findById(attackChainNodeId).orElseThrow();
    attackChainNode.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));

    AttackChainNode saved = attackChainNodeRepository.save(attackChainNode);
    return attackChainNodeMapper.toAttackChainNodeResultOverviewOutput(saved);
  }

  public void deleteAtomicTesting(String attackChainNodeId) {
    attackChainNodeService.delete(attackChainNodeId);
  }

  // -- ACTIONS --

  public AttackChainNodeResultOverviewOutput duplicate(String id) {
    return attackChainNodeService.duplicate(id);
  }

  public AttackChainNodeResultOverviewOutput launch(String id) {
    return attackChainNodeService.launch(id);
  }

  @Transactional
  public AttackChainNodeResultOverviewOutput relaunch(String id) {
    // Relaunching an atomic testing is considered as creating a new one.
    // Therefore, any grants created on the current atomic testing will have to be updated with the
    // new ID
    AttackChainNodeResultOverviewOutput relaunched = attackChainNodeService.relaunch(id);
    grantService.updateGrantsForNewResource(
        id, relaunched.getId(), Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING);
    return relaunched;
  }

  // -- PAGINATION --

  /**
   * Search atomic testings with pagination and filtering. Atomic testings are attackChainNodes that
   * are not part of any attackChain or attackChainRun (both fields are null). The search only
   * fetches data according to user permissions via the grant system.
   *
   * @param searchPaginationInput Pagination and filtering parameters
   * @return A paginated list of atomic testing results
   */
  public Page<AttackChainNodeResultOutput> searchAtomicTestingsForCurrentUser(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();

    // Atomic testings are attackChainNodes where attackChain and attackChainRun are null. They are
    // also subject to
    // the grant system.
    User currentUser = userService.currentUser();

    Specification<AttackChainNode> customSpec =
        Specification.where(AttackChainNodeSpecification.isAtomicTesting())
            .and(
                SpecificationUtils.hasGrantAccess(
                    currentUser.getId(),
                    currentUser.isAdminOrBypass(),
                    currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT),
                    Grant.GRANT_TYPE.OBSERVER));

    return buildPaginationCriteriaBuilder(
        (Specification<AttackChainNode> specification,
            Specification<AttackChainNode> specificationCount,
            Pageable pageable) ->
            attackChainNodeSearchService.attackChainNodeResults(
                customSpec.and(specification),
                customSpec.and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        AttackChainNode.class,
        joinMap);
  }
}
