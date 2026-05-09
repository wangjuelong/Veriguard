package io.veriguard.service;

import static io.veriguard.database.criteria.GenericCriteria.countQuery;
import static io.veriguard.utils.JpaUtils.createJoinArrayAggOnId;
import static io.veriguard.utils.JpaUtils.createJoinArrayAggOnIdForJoin;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.veriguard.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import io.veriguard.database.repository.AssetGroupRepository;
import io.veriguard.database.repository.AssetRepository;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.rest.atomic_testing.form.AttackChainNodeResultOutput;
import io.veriguard.rest.atomic_testing.form.AttackChainNodeStatusSimple;
import io.veriguard.rest.atomic_testing.form.NodeContractSimple;
import io.veriguard.rest.atomic_testing.form.TargetSimple;
import io.veriguard.rest.attack_chain_node.output.AttackChainNodeOutput;
import io.veriguard.rest.payload.output.PayloadSimple;
import io.veriguard.utils.NodeExpectationResultUtils;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.mapper.AttackChainNodeExpectationMapper;
import io.veriguard.utils.mapper.AttackChainNodeMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class AttackChainNodeSearchService {

  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  private final TeamRepository teamRepository;
  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;

  private final AttackChainNodeMapper attackChainNodeMapper;
  private final AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;

  @PersistenceContext private EntityManager entityManager;

  // -- LIST INJECTOUTPUT --

  public List<AttackChainNodeOutput> attackChainNodes(
      Specification<AttackChainNode> specification) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AttackChainNode> attackChainNodeRoot = cq.from(AttackChainNode.class);
    selectForAttackChainNode(cb, cq, attackChainNodeRoot, new HashMap<>());

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(attackChainNodeRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    cq.orderBy(cb.asc(attackChainNodeRoot.get("dependsDuration")));

    // Type Query
    TypedQuery<Tuple> query = this.entityManager.createQuery(cq);

    // -- EXECUTION --
    return execAttackChainNode(query);
  }

  // -- PAGE INJECT OUTPUT --
  public Page<AttackChainNodeOutput> attackChainNodes(
      Specification<AttackChainNode> specification,
      Specification<AttackChainNode> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AttackChainNode> attackChainNodeRoot = cq.from(AttackChainNode.class);
    selectForAttackChainNode(cb, cq, attackChainNodeRoot, joinMap);

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(attackChainNodeRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, attackChainNodeRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = this.entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<AttackChainNodeOutput> attackChainNodes = execAttackChainNode(query);

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, AttackChainNode.class, specificationCount);

    return new PageImpl<>(attackChainNodes, pageable, total);
  }

  private void selectForAttackChainNode(
      CriteriaBuilder cb,
      CriteriaQuery<Tuple> cq,
      Root<AttackChainNode> attackChainNodeRoot,
      Map<String, Join<Base, Base>> joinMap) {
    // Joins
    Join<Base, Base> attackChainNodeAttackChainRunJoin =
        attackChainNodeRoot.join("attackChainRun", JoinType.LEFT);
    joinMap.put("attackChainRun", attackChainNodeAttackChainRunJoin);

    Join<Base, Base> attackChainNodeAttackChainJoin =
        attackChainNodeRoot.join("attackChain", JoinType.LEFT);
    joinMap.put("attackChain", attackChainNodeAttackChainJoin);

    Join<Base, Base> nodeContractJoin = attackChainNodeRoot.join("nodeContract", JoinType.LEFT);
    joinMap.put("nodeContract", nodeContractJoin);

    Join<Base, Base> payloadJoin = nodeContractJoin.join("payload", JoinType.LEFT);
    joinMap.put("payload", payloadJoin);

    Join<Base, Base> nodeExecutorJoin = nodeContractJoin.join("nodeExecutor", JoinType.LEFT);
    joinMap.put("nodeExecutor", nodeExecutorJoin);

    Join<Base, Base> attackChainEdge = attackChainNodeRoot.join("dependsOn", JoinType.LEFT);
    joinMap.put("dependsOn", attackChainEdge);

    // Array aggregations
    Expression<String[]> tagIdsExpression = createJoinArrayAggOnId(cb, attackChainNodeRoot, "tags");
    Expression<String[]> teamIdsExpression =
        createJoinArrayAggOnId(cb, attackChainNodeRoot, "teams");
    Expression<String[]> assetIdsExpression =
        createJoinArrayAggOnId(cb, attackChainNodeRoot, "assets");
    Expression<String[]> assetGroupIdsExpression =
        createJoinArrayAggOnId(cb, attackChainNodeRoot, "assetGroups");
    Expression<String[]> domainsPayloadIdExpression =
        createJoinArrayAggOnIdForJoin(cb, payloadJoin, "domains");
    Expression<String[]> domainsContractIdExpression =
        createJoinArrayAggOnIdForJoin(cb, nodeContractJoin, "domains");

    // SELECT
    cq.multiselect(
            attackChainNodeRoot.get("id").alias("inject_id"),
            attackChainNodeRoot.get("title").alias("node_title"),
            attackChainNodeRoot.get("enabled").alias("inject_enabled"),
            attackChainNodeRoot.get("content").alias("inject_content"),
            attackChainNodeRoot.get("allTeams").alias("inject_all_teams"),
            attackChainNodeAttackChainRunJoin.get("id").alias("inject_exercise"),
            attackChainNodeAttackChainJoin.get("id").alias("inject_scenario"),
            attackChainNodeRoot.get("dependsDuration").alias("inject_depends_duration"),
            nodeContractJoin.alias("inject_injector_contract"),
            tagIdsExpression.alias("inject_tags"),
            teamIdsExpression.alias("inject_teams"),
            assetIdsExpression.alias("inject_assets"),
            assetGroupIdsExpression.alias("inject_asset_groups"),
            nodeExecutorJoin.get("type").alias("inject_type"),
            domainsPayloadIdExpression.alias("payload_domains"),
            domainsContractIdExpression.alias("injector_contract_domains"),
            attackChainEdge.alias("inject_depends_on"))
        .distinct(true);

    // GROUP BY
    cq.groupBy(
        Arrays.asList(
            attackChainNodeRoot.get("id"),
            attackChainNodeAttackChainRunJoin.get("id"),
            attackChainNodeAttackChainJoin.get("id"),
            nodeContractJoin.get("id"),
            nodeExecutorJoin.get("id"),
            attackChainEdge.get("id")));
  }

  private List<AttackChainNodeOutput> execAttackChainNode(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                attackChainNodeMapper.toAttackChainNodeOutput(
                    tuple.get("inject_id", String.class),
                    tuple.get("node_title", String.class),
                    tuple.get("inject_enabled", Boolean.class),
                    tuple.get("inject_content", ObjectNode.class),
                    tuple.get("inject_all_teams", Boolean.class),
                    tuple.get("inject_exercise", String.class),
                    tuple.get("inject_scenario", String.class),
                    tuple.get("inject_depends_duration", Long.class),
                    tuple.get("inject_injector_contract", NodeContract.class),
                    tuple.get("inject_tags", String[].class),
                    tuple.get("inject_teams", String[].class),
                    tuple.get("inject_assets", String[].class),
                    tuple.get("inject_asset_groups", String[].class),
                    tuple.get("inject_type", String.class),
                    tuple.get("inject_depends_on", AttackChainEdge.class)))
        .toList();
  }

  // -- PAGE INJECT SEARCH --
  public Page<AttackChainNodeResultOutput> getPageOfAttackChainNodeResults(
      String attackChainRunId, @Valid SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();

    Specification<AttackChainNode> customSpec =
        Specification.where(
            (root, query, cb) -> {
              Predicate predicate = cb.conjunction();
              predicate =
                  cb.and(
                      predicate, cb.equal(root.get("attackChainRun").get("id"), attackChainRunId));
              return predicate;
            });

    return buildPaginationCriteriaBuilder(
        (Specification<AttackChainNode> specification,
            Specification<AttackChainNode> specificationCount,
            Pageable pageable) ->
            attackChainNodeResults(
                customSpec.and(specification),
                customSpec.and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        AttackChainNode.class,
        joinMap);
  }

  public Page<AttackChainNodeResultOutput> attackChainNodeResults(
      Specification<AttackChainNode> specification,
      Specification<AttackChainNode> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {

    // Prepare query and execute
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    List<AttackChainNodeResultOutput> attackChainNodes =
        executeAttackChainNodeQuery(cb, specification, pageable, joinMap);

    // Fetch related data for attackChainNodes
    setComputedAttribute(attackChainNodes);

    long totalCount = countQuery(cb, entityManager, AttackChainNode.class, specificationCount);
    return new PageImpl<>(attackChainNodes, pageable, totalCount);
  }

  // -- LIST INJECTRESUTLOUTPUT --
  public List<AttackChainNodeResultOutput> getListOfAttackChainNodeResults(
      String attackChainRunId) {
    // Create specification for filtering by attackChainRunId
    Specification<AttackChainNode> specification =
        Specification.where(
            (root, query, cb) -> cb.equal(root.get("attackChainRun").get("id"), attackChainRunId));

    // Prepare query and execute
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    Sort sort = Sort.by(Sort.Order.desc("updatedAt"));
    Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE, sort);
    List<AttackChainNodeResultOutput> attackChainNodes =
        executeAttackChainNodeQuery(cb, specification, pageable, new HashMap<>());

    setComputedAttribute(attackChainNodes);
    return attackChainNodes;
  }

  // -- UTILS --
  private List<AttackChainNodeResultOutput> executeAttackChainNodeQuery(
      CriteriaBuilder cb,
      Specification<AttackChainNode> specification,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AttackChainNode> attackChainNodeRoot = cq.from(AttackChainNode.class);

    // Select the attackChainNodes with possible joins
    selectForAttackChainNodes(cb, cq, attackChainNodeRoot, joinMap);

    // Apply filters if any
    if (specification != null) {
      Predicate predicate = specification.toPredicate(attackChainNodeRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // Apply sorting based on Pageable
    List<Order> orders = toSortCriteriaBuilder(cb, attackChainNodeRoot, pageable.getSort());
    cq.orderBy(orders);

    // Execute the query with pagination
    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    return execAttackChainNodes(query);
  }

  private void setComputedAttribute(List<AttackChainNodeResultOutput> attackChainNodes) {
    // Fetch related data for attackChainNodes
    Set<String> attackChainNodeIds =
        attackChainNodes.stream()
            .map(AttackChainNodeResultOutput::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    if (!attackChainNodeIds.isEmpty()) {
      Map<String, List<Object[]>> teamMap = fetchRelatedTargets(attackChainNodeIds, "teams");
      Map<String, List<Object[]>> assetMap = fetchRelatedTargets(attackChainNodeIds, "assets");
      Map<String, List<Object[]>> assetGroupMap =
          fetchRelatedTargets(attackChainNodeIds, "assetGroups");
      Map<String, List<RawAttackChainNodeExpectation>> expectationMap =
          fetchExpectations(attackChainNodeIds);

      // Map results to AttackChainNodeResultOutput and set targets
      mapResultsToAttackChainNodes(
          attackChainNodes, teamMap, assetMap, assetGroupMap, expectationMap);
    }
  }

  public Map<String, List<Object[]>> fetchRelatedTargets(
      Set<String> attackChainNodeIds, String targetType) {
    if (attackChainNodeIds == null || attackChainNodeIds.isEmpty()) {
      return new HashMap<>();
    }

    Optional<List<Object[]>> data;
    switch (targetType) {
      case "teams":
        data = ofNullable(teamRepository.teamsByAttackChainNodeIds(attackChainNodeIds));
        break;
      case "assets":
        data = ofNullable(assetRepository.assetsByAttackChainNodeIds(attackChainNodeIds));
        break;
      case "assetGroups":
        data = ofNullable(assetGroupRepository.assetGroupsByAttackChainNodeIds(attackChainNodeIds));
        break;
      default:
        throw new IllegalArgumentException("Unknown data type: " + targetType);
    }
    if (data.isEmpty()) {
      return new HashMap<>();
    }

    return data.orElse(emptyList()).stream()
        .filter(Objects::nonNull)
        .filter(row -> 0 < row.length && row[0] != null) // [0]: id
        .collect(Collectors.groupingBy(row -> (String) row[0]));
  }

  private Map<String, List<RawAttackChainNodeExpectation>> fetchExpectations(
      Set<String> attackChainNodeIds) {
    if (attackChainNodeIds == null || attackChainNodeIds.isEmpty()) {
      return new HashMap<>();
    }

    return ofNullable(
            attackChainNodeExpectationRepository.rawForComputeGlobalByAttackChainNodeIds(
                attackChainNodeIds))
        .orElse(emptyList())
        .stream()
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(RawAttackChainNodeExpectation::getNode_id));
  }

  private void mapResultsToAttackChainNodes(
      List<AttackChainNodeResultOutput> attackChainNodes,
      Map<String, List<Object[]>> teamMap,
      Map<String, List<Object[]>> assetMap,
      Map<String, List<Object[]>> assetGroupMap,
      Map<String, List<RawAttackChainNodeExpectation>> expectationMap) {

    for (AttackChainNodeResultOutput attackChainNode : attackChainNodes) {
      if (attackChainNode.getId() != null) {
        // Set global score (expectations)
        attackChainNode.setExpectationResultByTypes(
            attackChainNodeExpectationMapper.extractExpectationResults(
                attackChainNode.getContent(),
                expectationMap.getOrDefault(attackChainNode.getId(), emptyList()),
                NodeExpectationResultUtils::getScoresFromRaw));

        // Set targets (teams, assets, asset groups)
        List<TargetSimple> allTargets =
            Stream.concat(
                    attackChainNodeMapper
                        .toTargetSimple(
                            teamMap.getOrDefault(attackChainNode.getId(), emptyList()),
                            TargetType.TEAMS)
                        .stream(),
                    Stream.concat(
                        attackChainNodeMapper
                            .toTargetSimple(
                                assetMap.getOrDefault(attackChainNode.getId(), emptyList()),
                                TargetType.ASSETS)
                            .stream(),
                        attackChainNodeMapper
                            .toTargetSimple(
                                assetGroupMap.getOrDefault(attackChainNode.getId(), emptyList()),
                                TargetType.ASSETS_GROUPS)
                            .stream()))
                .toList();

        attackChainNode.getTargets().addAll(allTargets);
      }
    }
  }

  private void selectForAttackChainNodes(
      CriteriaBuilder cb,
      CriteriaQuery<Tuple> cq,
      Root<AttackChainNode> attackChainNodeRoot,
      Map<String, Join<Base, Base>> joinMap) {
    // Joins
    Join<Base, Base> nodeContractJoin = attackChainNodeRoot.join("nodeContract", JoinType.LEFT);
    joinMap.put("nodeContract", nodeContractJoin);

    Join<Base, Base> nodeExecutorJoin = nodeContractJoin.join("nodeExecutor", JoinType.LEFT);
    joinMap.put("nodeExecutor", nodeExecutorJoin);

    Join<Base, Base> payloadJoin = nodeContractJoin.join("payload", JoinType.LEFT);
    joinMap.put("payload", payloadJoin);

    Join<Base, Base> collectorJoin = payloadJoin.join("collector", JoinType.LEFT);
    joinMap.put("collector", collectorJoin);

    Join<Base, Base> statusJoin = attackChainNodeRoot.join("status", JoinType.LEFT);
    joinMap.put("status", statusJoin);

    // Array aggregations
    Expression<String[]> teamIdsExpression =
        createJoinArrayAggOnId(cb, attackChainNodeRoot, "teams");
    Expression<String[]> assetIdsExpression =
        createJoinArrayAggOnId(cb, attackChainNodeRoot, "assets");
    Expression<String[]> assetGroupIdsExpression =
        createJoinArrayAggOnId(cb, attackChainNodeRoot, "assetGroups");
    Expression<String[]> domainsPayloadIdExpression =
        createJoinArrayAggOnIdForJoin(cb, payloadJoin, "domains");
    Expression<String[]> domainsContractIdExpression =
        createJoinArrayAggOnIdForJoin(cb, nodeContractJoin, "domains");

    // SELECT
    cq.multiselect(
            attackChainNodeRoot.get("id").alias("inject_id"),
            attackChainNodeRoot.get("title").alias("node_title"),
            attackChainNodeRoot.get("updatedAt").alias("inject_updated_at"),
            attackChainNodeRoot.get("content").alias("inject_content"),
            nodeExecutorJoin.get("type").alias("inject_type"),
            nodeContractJoin.get("id").alias("injector_contract_id"),
            nodeContractJoin.get("content").alias("injector_contract_content"),
            nodeContractJoin.get("convertedContent").alias("convertedContent"),
            nodeContractJoin.get("platforms").alias("injector_contract_platforms"),
            nodeContractJoin.get("labels").alias("injector_contract_labels"),
            payloadJoin.get("id").alias("payload_id"),
            payloadJoin.get("type").alias("payload_type"),
            collectorJoin.get("type").alias("payload_collector_type"),
            statusJoin.get("id").alias("status_id"),
            statusJoin.get("name").alias("status_name"),
            statusJoin.get("trackingSentDate").alias("status_tracking_sent_date"),
            teamIdsExpression.alias("inject_teams"),
            assetIdsExpression.alias("inject_assets"),
            domainsPayloadIdExpression.alias("payload_domains"),
            domainsContractIdExpression.alias("injector_contract_domains"),
            assetGroupIdsExpression.alias("inject_asset_groups"))
        .distinct(true);

    // GROUP BY
    cq.groupBy(
        Arrays.asList(
            attackChainNodeRoot.get("id"),
            nodeContractJoin.get("id"),
            nodeExecutorJoin.get("id"),
            payloadJoin.get("id"),
            collectorJoin.get("id"),
            statusJoin.get("id")));
  }

  private List<AttackChainNodeResultOutput> execAttackChainNodes(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple -> {
              AttackChainNodeStatusSimple attackChainNodeStatus = null;
              ExecutionStatus status = tuple.get("status_name", ExecutionStatus.class);
              if (status != null) {
                attackChainNodeStatus =
                    AttackChainNodeStatusSimple.builder()
                        .id(tuple.get("status_id", String.class))
                        .name(status.name())
                        .trackingSentDate(tuple.get("status_tracking_sent_date", Instant.class))
                        .build();
              } else {
                attackChainNodeStatus = AttackChainNodeStatusSimple.builder().build();
              }

              PayloadSimple payloadSimple = null;
              String payloadId = tuple.get("payload_id", String.class);
              if (payloadId != null) {
                payloadSimple =
                    PayloadSimple.builder()
                        .id(payloadId)
                        .type(tuple.get("payload_type", String.class))
                        .collectorType(tuple.get("payload_collector_type", String.class))
                        .domains(tuple.get("payload_domains", String[].class))
                        .build();
              }

              NodeContractSimple nodeContractSimple = null;
              String nodeContractId = tuple.get("injector_contract_id", String.class);
              if (nodeContractId != null) {
                nodeContractSimple =
                    NodeContractSimple.builder()
                        .id(nodeContractId)
                        .content(tuple.get("injector_contract_content", String.class))
                        .convertedContent(tuple.get("convertedContent", ObjectNode.class))
                        .platforms(
                            tuple.get(
                                "injector_contract_platforms", Endpoint.PLATFORM_TYPE[].class))
                        .payload(payloadSimple)
                        .domains(tuple.get("injector_contract_domains", String[].class))
                        .labels(tuple.get("injector_contract_labels", Map.class))
                        .build();
              }

              AttackChainNodeResultOutput attackChainNodeResultOutput =
                  new AttackChainNodeResultOutput();
              attackChainNodeResultOutput.setId(tuple.get("inject_id", String.class));
              attackChainNodeResultOutput.setTitle(tuple.get("node_title", String.class));
              attackChainNodeResultOutput.setContent(tuple.get("inject_content", ObjectNode.class));
              attackChainNodeResultOutput.setUpdatedAt(
                  tuple.get("inject_updated_at", Instant.class));
              attackChainNodeResultOutput.setAttackChainNodeType(
                  tuple.get("inject_type", String.class));
              attackChainNodeResultOutput.setNodeContract(nodeContractSimple);
              attackChainNodeResultOutput.setStatus(attackChainNodeStatus);
              attackChainNodeResultOutput.setTeamIds(tuple.get("inject_teams", String[].class));
              attackChainNodeResultOutput.setAssetIds(tuple.get("inject_assets", String[].class));
              attackChainNodeResultOutput.setAssetGroupIds(
                  tuple.get("inject_asset_groups", String[].class));

              return attackChainNodeResultOutput;
            })
        .toList();
  }
}
