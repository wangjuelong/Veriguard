package io.veriguard.rest.injector_contract;

import static io.veriguard.database.criteria.GenericCriteria.countQuery;
import static io.veriguard.database.model.NodeContract.*;
import static io.veriguard.helper.DatabaseHelper.updateRelation;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static io.veriguard.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.veriguard.utils.JpaUtils.*;
import static io.veriguard.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static io.veriguard.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawNodeExecutorsContracts;
import io.veriguard.database.repository.AttackPatternRepository;
import io.veriguard.database.repository.NodeContractRepository;
import io.veriguard.database.repository.NodeExecutorRepository;
import io.veriguard.database.specification.NodeContractSpecification;
import io.veriguard.injector_contract.Contract;
import io.veriguard.rest.attack_pattern.service.AttackPatternService;
import io.veriguard.rest.domain.DomainService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.injector_contract.form.*;
import io.veriguard.rest.injector_contract.output.NodeContractBaseOutput;
import io.veriguard.rest.injector_contract.output.NodeContractDomainCountOutput;
import io.veriguard.rest.injector_contract.output.NodeContractFullOutput;
import io.veriguard.rest.vulnerability.service.VulnerabilityService;
import io.veriguard.service.UserService;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Service for managing nodeExecutor contracts.
 *
 * <p>Provides CRUD operations, search functionality, and mapping management for nodeExecutor
 * contracts. NodeExecutor contracts define the interface between attackChainNodes and
 * nodeExecutors, specifying input fields, target types, and associated attack patterns.
 *
 * @see io.veriguard.database.model.NodeContract
 * @see io.veriguard.database.model.NodeExecutor
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class NodeContractService {

  @PersistenceContext private EntityManager entityManager;
  @Resource private ObjectMapper mapper;

  private final NodeContractRepository nodeContractRepository;
  private final AttackPatternService attackPatternService;
  private final VulnerabilityService vulnerabilityService;
  private final DomainService domainService;
  private final NodeExecutorRepository nodeExecutorRepository;
  private final UserService userService;
  private final AttackPatternRepository attackPatternRepository;

  /** Configuration flag for enabling email import from XLS files. */
  @Value("${veriguard.xls.import.mail.enable}")
  private boolean mailImportEnabled;

  // -- CRUD --

  /**
   * Retrieves an nodeExecutor contract by ID or external ID.
   *
   * @param id the nodeExecutor contract ID or external ID
   * @return the nodeExecutor contract
   * @throws ElementNotFoundException if not found
   */
  public NodeContract nodeContract(@NotBlank final String id) {
    return nodeContractRepository
        .findByIdOrExternalId(id, id)
        .orElseThrow(() -> new ElementNotFoundException("Injector contract not found"));
  }

  // -- OTHERS --

  @Setter
  @Getter
  private class QuerySetup {
    private TypedQuery<Tuple> query;
    private Long total;
  }

  private QuerySetup setupQuery(
      @Nullable final Specification<NodeContract> specification,
      @Nullable final Specification<NodeContract> specificationCount,
      @NotNull final Pageable pageable,
      boolean include_full_details) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<NodeContract> nodeContractRoot = cq.from(NodeContract.class);
    if (include_full_details) {
      selectForNodeContractFull(cb, cq, nodeContractRoot);
    } else {
      selectForNodeContractBase(cb, cq, nodeContractRoot);
    }

    // Always apply access spec
    Specification<NodeContract> accessSpec =
        NodeContractSpecification.hasAccessToNodeContract(userService.currentUser());

    Specification<NodeContract> combinedSpec =
        (specification == null ? accessSpec : specification.and(accessSpec));

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = combinedSpec.toPredicate(nodeContractRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, nodeContractRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = this.entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- Count Query --
    Specification<NodeContract> combinedSpecCount =
        (specificationCount == null ? accessSpec : specificationCount.and(accessSpec));
    Long total = countQuery(cb, this.entityManager, NodeContract.class, combinedSpecCount);

    QuerySetup qs = new QuerySetup();
    qs.setQuery(query);
    qs.setTotal(total);
    return qs;
  }

  /**
   * Retrieves a page of nodeExecutor contracts with full details.
   *
   * <p>Includes attack patterns, domains, nodeExecutor information, and payload details.
   *
   * @param specification the filter specification
   * @param specificationCount the count specification (may differ for accurate counting)
   * @param pageable the pagination parameters
   * @return a page of full nodeExecutor contract outputs
   */
  public PageImpl<NodeContractFullOutput> getSinglePageFullDetails(
      @Nullable final Specification<NodeContract> specification,
      @Nullable final Specification<NodeContract> specificationCount,
      @NotNull final Pageable pageable) {
    QuerySetup qs = setupQuery(specification, specificationCount, pageable, true);

    // -- EXECUTION --
    List<NodeContractFullOutput> nodeContractFullOutputs = execNodeExecutorFullContract(qs.query);

    return new PageImpl<>(nodeContractFullOutputs, pageable, qs.total);
  }

  /**
   * Retrieves a page of nodeExecutor contracts with base details only.
   *
   * <p>Returns minimal information for list views and dropdowns.
   *
   * @param specification the filter specification
   * @param specificationCount the count specification
   * @param pageable the pagination parameters
   * @return a page of base nodeExecutor contract outputs
   */
  public PageImpl<NodeContractBaseOutput> getSinglePageBaseDetails(
      @Nullable final Specification<NodeContract> specification,
      @Nullable final Specification<NodeContract> specificationCount,
      @NotNull final Pageable pageable) {
    QuerySetup qs = setupQuery(specification, specificationCount, pageable, false);

    // -- EXECUTION --
    List<NodeContractBaseOutput> nodeContractBaseOutputs = execNodeExecutorBaseContract(qs.query);

    return new PageImpl<>(nodeContractBaseOutputs, pageable, qs.total);
  }

  public Iterable<RawNodeExecutorsContracts> getAllRawAttackChainNodeContracts() {
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS)) {
      return nodeContractRepository.getAllRawNodeExecutorsContracts();
    }
    return nodeContractRepository.getAllRawNodeExecutorsContractsWithoutPayloadOrGranted(
        currentUser.getId());
  }

  /**
   * Retrieves a single nodeExecutor contract by ID or external ID.
   *
   * @param nodeContractId the contract ID or external ID
   * @return the nodeExecutor contract
   * @throws ElementNotFoundException if not found
   */
  public NodeContract getSingleNodeContract(String nodeContractId) {
    return nodeContractRepository
        .findByIdOrExternalId(nodeContractId, nodeContractId)
        .orElseThrow(ElementNotFoundException::new);
  }

  /**
   * Creates a new custom nodeExecutor contract.
   *
   * <p>Custom contracts are user-defined and can be modified or deleted. Sets up attack pattern
   * mappings, vulnerabilities, and domain associations.
   *
   * @param input the creation input
   * @return the created nodeExecutor contract
   */
  @Transactional(rollbackOn = Exception.class)
  public NodeContract createNewNodeContract(NodeContractAddInput input) {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setCustom(true);
    nodeContract.setUpdateAttributes(input);
    List<AttackPattern> aps = new ArrayList<>();
    if (!input.getAttackPatternsExternalIds().isEmpty()) {
      aps =
          attackPatternService.getAttackPatternsByExternalIdsThrowIfMissing(
              new HashSet<>(input.getAttackPatternsExternalIds()));
    } else if (!input.getAttackPatternsIds().isEmpty()) {
      aps =
          attackPatternService.findAllByInternalIdsThrowIfMissing(
              new HashSet<>(input.getAttackPatternsIds()));
    }
    nodeContract.setAttackPatterns(aps);
    setVulnerabilitiesFromExternalOrInternalIds(
        input.getVulnerabilityExternalIds(), input.getVulnerabilityIds(), nodeContract);

    nodeContract.setNodeExecutor(
        updateRelation(
            input.getNodeExecutorId(), nodeContract.getNodeExecutor(), nodeExecutorRepository));
    nodeContract.setDomains(
        !nodeContract.getNodeExecutor().isPayloads()
            ? this.domainService.upserts(input.getDomains())
            : new HashSet<>());
    return nodeContractRepository.save(nodeContract);
  }

  public NodeContract createBuiltinNodeContract(
      Contract source, NodeExecutor nodeExecutor, boolean isPayloads) {
    NodeContract target = new NodeContract();
    target.setId(source.getId());
    target.setNodeExecutor(nodeExecutor);

    applyBuiltinContractData(target, source, isPayloads);
    return target;
  }

  public void updateBuiltInNodeContract(NodeContract target, Contract source, boolean isPayloads) {
    applyBuiltinContractData(target, source, isPayloads);
  }

  private void applyBuiltinContractData(NodeContract target, Contract source, boolean isPayloads) {
    target.setManual(source.isManual());
    target.setAtomicTesting(source.isAtomicTesting());
    target.setPlatforms(source.getPlatforms().toArray(new Endpoint.PLATFORM_TYPE[0]));
    target.setNeedsExecutor(source.isNeedsExecutor());

    Map<String, String> labels =
        source.getLabel().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    target.setLabels(labels);

    // Update attack patterns if not overridden
    if (target.getAttackPatterns().isEmpty() && !source.getAttackPatternsExternalIds().isEmpty()) {
      List<AttackPattern> attackPatterns =
          fromIterable(
              attackPatternRepository.findAllByExternalIdInIgnoreCase(
                  source.getAttackPatternsExternalIds()));
      target.setAttackPatterns(attackPatterns);
    } else {
      target.setAttackPatterns(new ArrayList<>());
    }

    try {
      target.setContent(mapper.writeValueAsString(source));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Failed to serialize contract content for: " + target.getId(), e);
    }

    if (!isPayloads) {
      Set<Domain> currentDomains = this.domainService.upsertDomainEntities(target.getDomains());
      Set<Domain> domainsToAdd = this.domainService.upsertDomainEntities(target.getDomains());
      target.setDomains(this.domainService.mergeDomains(currentDomains, domainsToAdd));
    }
    setupImportAvailable(target);
  }

  private void setupImportAvailable(NodeContract nodeContract) {
    // 二开 移除 Email nodeExecutor — no contracts opt into mail import.
  }

  /**
   * Updates an existing nodeExecutor contract.
   *
   * @param nodeContractId the contract ID to update
   * @param input the update input
   * @return the updated nodeExecutor contract
   * @throws ElementNotFoundException if not found
   */
  public NodeContract updateNodeContract(String nodeContractId, NodeContractUpdateInput input) {
    NodeContract nodeContract =
        nodeContractRepository
            .findByIdOrExternalId(nodeContractId, nodeContractId)
            .orElseThrow(ElementNotFoundException::new);
    nodeContract.setUpdateAttributes(input);
    nodeContract.setAttackPatterns(
        attackPatternService.findAllByInternalIdsThrowIfMissing(
            new HashSet<>(input.getAttackPatternsIds())));
    setVulnerabilitiesFromExternalOrInternalIds(
        input.getVulnerabilityExternalIds(), input.getVulnerabilityIds(), nodeContract);
    nodeContract.setDomains(this.domainService.upserts(input.getDomains()));

    nodeContract.setUpdatedAt(Instant.now());
    return nodeContractRepository.save(nodeContract);
  }

  private void setVulnerabilitiesFromExternalOrInternalIds(
      List<String> externalIds, List<String> internalIds, NodeContract nodeContract) {
    Set<Vulnerability> vulns = new HashSet<>();
    if (!externalIds.isEmpty()) {
      vulns =
          vulnerabilityService.findAllByExternalIdsAndAlertIfMissing(new HashSet<>(externalIds));
    } else if (!internalIds.isEmpty()) {
      vulns = vulnerabilityService.findAllByIdsOrThrowIfMissing(new HashSet<>(internalIds));
    }
    nodeContract.setVulnerabilities(vulns);
  }

  /**
   * Updates the attack pattern and vulnerability mappings for a contract.
   *
   * @param nodeContractId the contract ID to update
   * @param input the mapping update input
   * @return the updated nodeExecutor contract
   * @throws ElementNotFoundException if not found
   */
  public NodeContract updateAttackPatternMappings(
      String nodeContractId, NodeContractUpdateMappingInput input) {
    NodeContract nodeContract =
        nodeContractRepository
            .findByIdOrExternalId(nodeContractId, nodeContractId)
            .orElseThrow(ElementNotFoundException::new);
    nodeContract.setAttackPatterns(
        attackPatternService.findAllByInternalIdsThrowIfMissing(
            new HashSet<>(input.getAttackPatternsIds())));
    nodeContract.setVulnerabilities(
        vulnerabilityService.findAllByIdsOrThrowIfMissing(
            new HashSet<>(input.getVulnerabilityIds())));
    nodeContract.setDomains(iterableToSet(domainService.findAllById(input.getDomainIds())));
    nodeContract.setUpdatedAt(Instant.now());
    return nodeContractRepository.save(nodeContract);
  }

  /**
   * Deletes a custom nodeExecutor contract.
   *
   * <p>Only custom contracts (user-created) can be deleted. Built-in contracts cannot be removed.
   *
   * @param nodeContractId the contract ID to delete
   * @throws ElementNotFoundException if not found
   * @throws IllegalArgumentException if the contract is not custom
   */
  public void deleteNodeContract(final String nodeContractId) {
    NodeContract nodeContract =
        this.nodeContractRepository
            .findByIdOrExternalId(nodeContractId, nodeContractId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException("Injector contract not found: " + nodeContractId));
    if (!nodeContract.getCustom()) {
      throw new IllegalArgumentException(
          "This injector contract can't be removed because is not a custom one: " + nodeContractId);
    } else {
      this.nodeContractRepository.deleteById(nodeContract.getId());
    }
  }

  /**
   * Checks if an nodeExecutor contract supports a specific target type.
   *
   * <p>Analyzes the contract's field definitions to determine which target types are supported.
   *
   * @param nodeContract the contract to check
   * @param targetType the target type to verify support for
   * @return true if the contract supports the target type
   */
  public boolean checkTargetSupport(NodeContract nodeContract, TargetType targetType) {
    JsonNode fieldsNode = nodeContract.getConvertedContent().get(CONTRACT_CONTENT_FIELDS);
    Set<TargetType> supportedTargetTypes = new HashSet<>();
    for (JsonNode field : fieldsNode) {
      String type = field.path(CONTRACT_ELEMENT_CONTENT_TYPE).asText();
      switch (type) {
        case CONTRACT_ELEMENT_CONTENT_TYPE_ASSET_GROUP ->
            supportedTargetTypes.add(TargetType.ASSETS_GROUPS);
        case CONTRACT_ELEMENT_CONTENT_TYPE_ASSET -> supportedTargetTypes.add(TargetType.ASSETS);
        case CONTRACT_ELEMENT_CONTENT_TYPE_TEAM -> supportedTargetTypes.add(TargetType.TEAMS);
        default -> {
          // ignore other types: expectations, text, textarea
        }
      }
    }

    return supportedTargetTypes.contains(targetType);
  }

  // -- CRITERIA BUILDER --

  private void selectForNodeContractFull(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<NodeContract> nodeContractRoot) {
    // Joins
    Join<NodeContract, Payload> nodeContractPayloadJoin =
        createLeftJoin(nodeContractRoot, "payload");
    Join<Payload, Collector> payloadCollectorJoin =
        nodeContractPayloadJoin.join("collector", JoinType.LEFT);
    Join<NodeContract, NodeExecutor> nodeContractNodeExecutorJoin =
        createLeftJoin(nodeContractRoot, "injector");
    // Array aggregations
    Expression<String[]> attackPatternIdsExpression =
        createJoinArrayAggOnId(cb, nodeContractRoot, "attackPatterns");

    Expression<String[]> domainsIdsExpression =
        createJoinArrayAggOnId(cb, nodeContractRoot, "domains");

    Expression<String[]> payloadDomainsIdsExpression =
        createJoinArrayAggOnIdForJoin(cb, nodeContractPayloadJoin, "domains");

    // SELECT
    cq.multiselect(
            nodeContractRoot.get("id").alias("injector_contract_id"),
            nodeContractRoot.get("externalId").alias("injector_contract_external_id"),
            nodeContractRoot.get("labels").alias("injector_contract_labels"),
            nodeContractRoot.get("content").alias("injector_contract_content"),
            nodeContractRoot.get("platforms").alias("injector_contract_platforms"),
            nodeContractPayloadJoin.get("type").alias("payload_type"),
            payloadCollectorJoin.get("type").alias("collector_type"),
            nodeContractNodeExecutorJoin.get("type").alias("injector_contract_injector_type"),
            nodeContractNodeExecutorJoin.get("name").alias("injector_contract_injector_name"),
            attackPatternIdsExpression.alias("injector_contract_attack_patterns"),
            payloadDomainsIdsExpression.alias("payload_domains"),
            domainsIdsExpression.alias("injector_contract_domains"),
            nodeContractRoot.get("updatedAt").alias("injector_contract_updated_at"),
            nodeContractPayloadJoin.get("executionArch").alias("payload_execution_arch"))
        .distinct(true);

    // GROUP BY
    cq.groupBy(
        Arrays.asList(
            nodeContractRoot.get("id"),
            nodeContractPayloadJoin.get("id"),
            payloadCollectorJoin.get("id"),
            nodeContractNodeExecutorJoin.get("id")));
  }

  private List<NodeContractFullOutput> execNodeExecutorFullContract(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                new NodeContractFullOutput(
                    tuple.get("injector_contract_id", String.class),
                    tuple.get("injector_contract_external_id", String.class),
                    tuple.get("injector_contract_labels", Map.class),
                    tuple.get("injector_contract_content", String.class),
                    tuple.get("injector_contract_platforms", Endpoint.PLATFORM_TYPE[].class),
                    tuple.get("payload_type", String.class),
                    tuple.get("injector_contract_injector_name", String.class),
                    tuple.get("collector_type", String.class),
                    tuple.get("injector_contract_injector_type", String.class),
                    tuple.get("injector_contract_attack_patterns", String[].class),
                    resolveEffectiveDomains(
                        tuple.get("injector_contract_domains", String[].class),
                        tuple.get("payload_domains", String[].class)),
                    tuple.get("injector_contract_updated_at", Instant.class),
                    tuple.get("payload_execution_arch", Payload.PAYLOAD_EXECUTION_ARCH.class)))
        .toList();
  }

  private List<String> resolveEffectiveDomains(
      String[] nodeExecutorDomains, String[] payloadDomains) {
    String[] effectiveDomains =
        (payloadDomains != null && payloadDomains.length > 0)
            ? payloadDomains
            : nodeExecutorDomains;
    if (effectiveDomains == null) {
      return List.of();
    }
    return Arrays.stream(effectiveDomains).filter(Objects::nonNull).distinct().toList();
  }

  private void selectForNodeContractBase(
      @NotNull final CriteriaBuilder cb,
      @NotNull final CriteriaQuery<Tuple> cq,
      @NotNull final Root<NodeContract> nodeContractRoot) {
    // SELECT
    cq.multiselect(
            nodeContractRoot.get("id").alias("injector_contract_id"),
            nodeContractRoot.get("externalId").alias("injector_contract_external_id"),
            nodeContractRoot.get("updatedAt").alias("injector_contract_updated_at"))
        .distinct(true);
  }

  private List<NodeContractBaseOutput> execNodeExecutorBaseContract(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                new NodeContractBaseOutput(
                    tuple.get("injector_contract_id", String.class),
                    tuple.get("injector_contract_external_id", String.class),
                    tuple.get("injector_contract_updated_at", Instant.class)))
        .toList();
  }

  /**
   * Converts input data to an nodeExecutor contract entity.
   *
   * <p>Used during nodeExecutor registration to create contract entities from input definitions.
   *
   * @param in the contract input data
   * @param nodeExecutor the parent nodeExecutor
   * @return the created nodeExecutor contract (not yet persisted)
   */
  // TODO JRI => REFACTOR TO RELY ON INJECTOR SERVICE
  public NodeContract convertNodeExecutorFromInput(
      NodeContractInput in, NodeExecutor nodeExecutor) {
    NodeContract nodeContract = new NodeContract();
    nodeContract.setId(in.getId());
    nodeContract.setManual(in.isManual());
    nodeContract.setLabels(in.getLabels());
    nodeContract.setNodeExecutor(nodeExecutor);
    nodeContract.setContent(in.getContent());
    nodeContract.setAtomicTesting(in.isAtomicTesting());
    nodeContract.setPlatforms(in.getPlatforms());
    if (!in.getAttackPatternsExternalIds().isEmpty()) {
      List<AttackPattern> attackPatterns =
          fromIterable(
              attackPatternRepository.findAllByExternalIdInIgnoreCase(
                  in.getAttackPatternsExternalIds()));
      nodeContract.setAttackPatterns(attackPatterns);
    } else {
      nodeContract.setAttackPatterns(new ArrayList<>());
    }
    if (!nodeExecutor.isPayloads() && in.getDomains() != null) {
      nodeContract.setDomains(this.domainService.upserts(in.getDomains()));
    }
    return nodeContract;
  }

  /**
   * Computes the count of nodeExecutor contracts grouped by domain.
   *
   * <p>This method applies a specific precedence logic for domain resolution:
   *
   * <ul>
   *   <li>**Priority**: If the contract's payload defines specific domains, these are used for the
   *       count.
   *   <li>**Fallback**: If the payload has no domains (or is null), the contract's direct domains
   *       are used.
   * </ul>
   *
   * <p>It executes two distinct criteria queries and merges the results to generate the final
   * distribution.
   *
   * @param input the search and filtering criteria
   * @return the list of domain counts derived from effective contract associations
   */
  public List<NodeContractDomainCountOutput> getDomainCounts(SearchPaginationInput input) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    Specification<NodeContract> filterSpec = computeFilterGroupJpa(input.getFilterGroup());
    Specification<NodeContract> searchSpec = computeSearchJpa(input.getTextSearch());
    Specification<NodeContract> baseSpec = Specification.where(filterSpec).and(searchSpec);

    CriteriaQuery<NodeContractDomainCountOutput> qPayload =
        cb.createQuery(NodeContractDomainCountOutput.class);
    Root<NodeContract> rootPayload = qPayload.from(NodeContract.class);
    Join<NodeContract, Payload> payloadJoin = rootPayload.join("payload", JoinType.INNER);
    Join<Payload, Domain> payloadDomainsJoin = payloadJoin.join("domains", JoinType.INNER);

    Predicate payloadPredicate = baseSpec.toPredicate(rootPayload, qPayload, cb);
    if (payloadPredicate != null) {
      qPayload.where(payloadPredicate);
    }

    qPayload.multiselect(payloadDomainsJoin.get("id"), cb.countDistinct(rootPayload));
    qPayload.groupBy(payloadDomainsJoin.get("id"));

    List<NodeContractDomainCountOutput> payloadCounts =
        entityManager.createQuery(qPayload).getResultList();

    CriteriaQuery<NodeContractDomainCountOutput> qDirect =
        cb.createQuery(NodeContractDomainCountOutput.class);
    Root<NodeContract> rootDirect = qDirect.from(NodeContract.class);
    Join<NodeContract, Domain> directDomainsJoin = rootDirect.join("domains", JoinType.INNER);
    Join<NodeContract, Payload> pCheckJoin = rootDirect.join("payload", JoinType.LEFT);

    Predicate noPayloadDomains =
        cb.or(cb.isNull(pCheckJoin), cb.isEmpty(pCheckJoin.get("domains")));

    Predicate directUserPredicate = baseSpec.toPredicate(rootDirect, qDirect, cb);
    if (directUserPredicate != null) {
      qDirect.where(cb.and(directUserPredicate, noPayloadDomains));
    } else {
      qDirect.where(noPayloadDomains);
    }

    qDirect.multiselect(directDomainsJoin.get("id"), cb.countDistinct(rootDirect));
    qDirect.groupBy(directDomainsJoin.get("id"));

    List<NodeContractDomainCountOutput> directCounts =
        entityManager.createQuery(qDirect).getResultList();

    Map<String, Long> mergedMap = new HashMap<>();
    Stream.concat(payloadCounts.stream(), directCounts.stream())
        .forEach(output -> mergedMap.merge(output.getDomain(), output.getCount(), Long::sum));

    return mergedMap.entrySet().stream()
        .map(entry -> new NodeContractDomainCountOutput(entry.getKey(), entry.getValue()))
        .toList();
  }
}
