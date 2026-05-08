package io.veriguard.database.repository;

import static io.veriguard.database.model.DnsResolution.DNS_RESOLUTION_TYPE;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.raw.RawAttackChainNode;
import io.veriguard.database.raw.RawAttackChainNodeIndexing;
import io.veriguard.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository interface for {@link AttackChainNode} entities.
 *
 * <p>This repository provides comprehensive data access operations for attackChainNodes, which
 * represent individual attack simulation steps within attackChainRuns and attackChains. It
 * supports:
 *
 * <ul>
 *   <li>Standard CRUD operations via {@link JpaRepository}
 *   <li>Dynamic filtering via {@link JpaSpecificationExecutor}
 *   <li>Statistical queries via {@link StatisticRepository}
 *   <li>Complex queries for attackChainNode retrieval with relationships
 *   <li>Search engine indexing support
 *   <li>Import/export operations
 *   <li>Team and asset management operations
 * </ul>
 *
 * @see AttackChainNode
 * @see io.veriguard.database.model.AttackChainRun
 * @see io.veriguard.database.model.AttackChain
 */
@Repository
public interface AttackChainNodeRepository
    extends JpaRepository<AttackChainNode, String>,
        JpaSpecificationExecutor<AttackChainNode>,
        StatisticRepository {

  @NotNull
  Optional<AttackChainNode> findById(@NotNull String id);

  @NotNull
  Optional<AttackChainNode> findWithStatusById(@NotNull String id);

  // -- SIMULATION --

  List<AttackChainNode> findByAttackChainRunId(@NotNull String attackChainRunId);

  Optional<AttackChainNode> findByIdAndAttackChainRunId(
      @NotNull String id, @NotNull String attackChainRunId);

  boolean existsByIdAndAttackChainRunId(@NotNull String id, @NotNull String attackChainRunId);

  // -- SCENARIO --

  Optional<AttackChainNode> findByIdAndAttackChainId(
      @NotNull String id, @NotNull String attackChainId);

  boolean existsByIdAndAttackChainId(@NotNull String id, @NotNull String attackChainId);

  Set<AttackChainNode> findByAttackChainId(@NotNull String attackChainId);

  // -- INDEXING --

  @Query(
      value =
          "SELECT f.node_id, f.node_title, f.node_attack_chain_id, f.node_attack_chain_run_id, f.node_created_at, f.node_updated_at, f.node_contract_id, ic.injector_contract_updated_at, ins.tracking_sent_date, "
              + "array_union_agg(ic.injector_contract_platforms) FILTER ( WHERE ic.injector_contract_platforms IS NOT NULL ) as inject_platforms, "
              + "array_agg(icap.attack_pattern_id) FILTER ( WHERE icap.attack_pattern_id IS NOT NULL ) as inject_attack_patterns, "
              + "array_agg(ap.phase_id) FILTER ( WHERE ap.phase_id IS NOT NULL ) as inject_kill_chain_phases, "
              + "array_agg(idp.child_node_id) FILTER ( WHERE idp.child_node_id IS NOT NULL ) as inject_children, "
              + "array_agg(idp.child_node_id) FILTER ( WHERE idp.child_node_id IS NOT NULL ) as attack_pattern_children, "
              + "array_agg(icap_children.attack_pattern_id) FILTER (WHERE icap_children.attack_pattern_id IS NOT NULL) AS attack_patterns_children,"
              + "MAX(ins.status_name) as inject_status_name, "
              + "array_agg(it.tag_id) FILTER ( WHERE it.tag_id IS NOT NULL ) as inject_tags, "
              + "array_agg(ia.asset_id) FILTER ( WHERE ia.asset_id IS NOT NULL ) as inject_assets, "
              + "array_agg(iag.asset_group_id) FILTER ( WHERE iag.asset_group_id IS NOT NULL ) as inject_asset_groups, "
              + "array_agg(ite.team_id) FILTER ( WHERE ite.team_id IS NOT NULL ) || "
              + "array_agg(et.team_id) FILTER ( WHERE et.team_id IS NOT NULL ) || "
              + "array_agg(st.team_id) FILTER ( WHERE st.team_id IS NOT NULL ) as inject_teams " // The deduplication is not done here but in the Set<String> of RawAttackChainNodeIndexing
              + "FROM attack_chain_nodes f "
              + "LEFT JOIN injects_statuses ins ON ins.status_inject = f.node_id "
              + "LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = f.node_contract_id "
              + "LEFT JOIN injectors_contracts_attack_patterns icap ON icap.injector_contract_id = ic.injector_contract_id "
              + "LEFT JOIN attack_patterns_kill_chain_phases ap ON ap.attack_pattern_id = icap.attack_pattern_id "
              + "LEFT JOIN attack_chain_edges idp ON idp.parent_node_id = f.node_id "
              + "LEFT JOIN attack_chain_nodes inject_children ON inject_children.node_id = idp.child_node_id "
              + "LEFT JOIN injectors_contracts ic_children ON ic_children.injector_contract_id = inject_children.node_contract_id "
              + "LEFT JOIN injectors_contracts_attack_patterns icap_children ON icap_children.injector_contract_id = ic_children.injector_contract_id "
              + "LEFT JOIN attack_chain_nodes_tags it ON it.node_id = f.node_id "
              + "LEFT JOIN attack_chain_nodes_assets ia ON ia.node_id = f.node_id "
              + "LEFT JOIN attack_chain_nodes_asset_groups iag ON iag.node_id = f.node_id "
              + "LEFT JOIN attack_chain_nodes_teams ite ON ite.node_id = f.node_id "
              + "LEFT JOIN attack_chain_runs_teams et ON et.run_id = f.node_attack_chain_run_id AND f.node_all_teams "
              + "LEFT JOIN attack_chains_teams st ON st.attack_chain_id = f.node_attack_chain_id AND f.node_all_teams "
              + "WHERE f.node_updated_at > :from "
              + "OR ic.injector_contract_updated_at > :from  "
              + "OR EXISTS ("
              + "    SELECT 1 "
              + "    FROM attack_chain_edges sub_idp "
              + "    WHERE sub_idp.parent_node_id = f.node_id "
              + "      AND sub_idp.edge_updated_at > :from"
              + ")"
              + "OR EXISTS ("
              + "    SELECT 1 "
              + "    FROM injectors_contracts sub_ic "
              + "    WHERE sub_ic.injector_contract_id = inject_children.node_contract_id "
              + "      AND sub_ic.injector_contract_updated_at > :from "
              + ")"
              + "GROUP BY f.node_id, f.node_updated_at, ic.injector_contract_updated_at, ins.tracking_sent_date ORDER BY GREATEST(f.node_updated_at, ic.injector_contract_updated_at) ASC LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawAttackChainNodeIndexing> findForIndexing(@Param("from") Instant from);

  @Query(
      value =
          "select i.* from attack_chain_nodes i where i.node_contract_id = '49229430-b5b5-431f-ba5b-f36f599b0233'"
              + " and i.node_content like :challengeId",
      nativeQuery = true)
  List<AttackChainNode> findAllForChallengeId(@Param("challengeId") String challengeId);

  @Query(
      value =
          "select i from AttackChainNode i "
              + "join i.documents as doc_rel "
              + "join doc_rel.document as doc "
              + "where doc.id = :documentId and i.attackChainRun.id = :attackChainRunId")
  List<AttackChainNode> findAllForAttackChainRunAndDoc(
      @Param("attackChainRunId") String attackChainRunId, @Param("documentId") String documentId);

  @Query(
      value =
          "select i from AttackChainNode i "
              + "join i.documents as doc_rel "
              + "join doc_rel.document as doc "
              + "where doc.id = :documentId and i.attackChain.id = :attackChainId")
  List<AttackChainNode> findAllForAttackChainAndDoc(
      @Param("attackChainId") String attackChainId, @Param("documentId") String documentId);

  @Modifying
  @Query(
      value =
          "insert into attack_chain_nodes (node_id, node_title, node_description, node_country, node_city,"
              + "node_contract_id, node_all_teams, node_enabled, node_attack_chain_run_id, "
              + "node_depends_duration, node_content) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :attackChainRun, :dependsDuration, :content)",
      nativeQuery = true)
  void importSaveForAttackChainRun(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("attackChainRun") String attackChainRunId,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value =
          "insert into attack_chain_nodes (node_id, node_title, node_description, node_country, node_city,"
              + "node_contract_id, node_all_teams, node_enabled, node_attack_chain_id, "
              + "node_depends_duration, node_content) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :attackChain, :dependsDuration, :content)",
      nativeQuery = true)
  void importSaveForAttackChain(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("attackChain") String attackChainId,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value =
          "insert into attack_chain_nodes (node_id, node_title, node_description, node_country, node_city,"
              + "node_contract_id, node_all_teams, node_enabled, "
              + "node_depends_duration, node_content) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :dependsDuration, :content)",
      nativeQuery = true)
  void importSaveStandAlone(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value = "insert into attack_chain_nodes_tags (node_id, tag_id) values (:attackChainNodeId, :tagId)",
      nativeQuery = true)
  void addTag(@Param("attackChainNodeId") String attackChainNodeId, @Param("tagId") String tagId);

  @Modifying
  @Query(
      value = "insert into attack_chain_nodes_teams (node_id, team_id) values (:attackChainNodeId, :teamId)",
      nativeQuery = true)
  void addTeam(@Param("attackChainNodeId") String attackChainNodeId, @Param("teamId") String teamId);

  @Override
  @Query(
      "select count(distinct i) from AttackChainNode i "
          + "join i.attackChainRun as e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and i.createdAt > :creationDate")
  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Override
  @Query("select count(distinct i) from AttackChainNode i where i.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "select icap.attack_pattern_id, count(distinct i) as countInjects from attack_chain_nodes i "
              + "join injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.node_contract_id "
              + "join attack_chain_runs e ON e.run_id = i.node_attack_chain_run_id "
              + "join injects_statuses injectStatus ON injectStatus.status_inject = i.node_id "
              + "where i.node_created_at > :creationDate and i.node_attack_chain_run_id is not null and e.run_start_date is not null and icap.injector_contract_id is not null and injectStatus.status_name = 'SUCCESS'"
              + "group by icap.attack_pattern_id order by countInjects DESC LIMIT 5",
      nativeQuery = true)
  List<Object[]> globalCountGroupByAttackPatternInAttackChainRun(
      @Param("creationDate") Instant creationDate);

  @Query(
      value =
          "select icap.attack_pattern_id, count(distinct i) as countInjects from attack_chain_nodes i "
              + "join injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.node_contract_id "
              + "join attack_chain_runs e on e.run_id = i.node_attack_chain_run_id "
              + "inner join grants ON grants.grant_resource = e.run_id AND grants.grant_resource_type = 'SIMULATION' "
              + "inner join groups ON grants.grant_group = groups.group_id "
              + "inner join users_groups ON groups.group_id = users_groups.group_id "
              + "join injects_statuses injectStatus ON injectStatus.status_inject = i.node_id "
              + "where users_groups.user_id = :userId and i.node_created_at > :creationDate and i.node_attack_chain_run_id is not null and e.run_start_date is not null and icap.injector_contract_id is not null and injectStatus.status_name = 'SUCCESS'"
              + "group by icap.attack_pattern_id order by countInjects DESC LIMIT 5",
      nativeQuery = true)
  List<Object[]> userCountGroupByAttackPatternInAttackChainRun(
      @Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Query(
      value =
          "WITH inject_teams AS ( "
              + "    SELECT node_id, array_agg(team_id) as team_ids "
              + "    FROM attack_chain_nodes_teams "
              + "    WHERE node_id IN (:ids) "
              + "    GROUP BY node_id "
              + "), "
              + "inject_assets AS ( "
              + "    SELECT  "
              + "        i.node_id,  "
              + "        array_agg(DISTINCT a.asset_id) as asset_ids "
              + "    FROM attack_chain_nodes i "
              + "    LEFT JOIN attack_chain_nodes_assets ia ON i.node_id = ia.node_id "
              + "    LEFT JOIN attack_chain_nodes_asset_groups iag ON i.node_id = iag.node_id "
              + "    LEFT JOIN asset_groups_assets aga ON aga.asset_group_id = iag.asset_group_id "
              + "    LEFT JOIN assets a ON a.asset_id = ia.asset_id OR aga.asset_id = a.asset_id "
              + "    WHERE i.node_id IN (:ids) "
              + "    GROUP BY i.node_id "
              + "), "
              + "inject_asset_groups AS ( "
              + "    SELECT node_id, array_agg(asset_group_id) as asset_group_ids "
              + "    FROM attack_chain_nodes_asset_groups "
              + "    WHERE node_id IN (:ids) "
              + "    GROUP BY node_id "
              + "), "
              + "inject_expectations AS ( "
              + "    SELECT node_id, array_agg(node_expectation_id) as expectation_ids "
              + "    FROM attack_chain_node_expectations "
              + "    WHERE node_id IN (:ids) "
              + "    GROUP BY node_id "
              + "), "
              + "inject_communications AS ( "
              + "    SELECT communication_inject as node_id, array_agg(communication_id) as communication_ids "
              + "    FROM communications "
              + "    WHERE communication_inject IN (:ids) "
              + "    GROUP BY communication_inject "
              + "), "
              + "inject_kill_chains AS ( "
              + "    SELECT  "
              + "        i.node_id, "
              + "        array_agg(DISTINCT apkcp.phase_id) as phase_ids "
              + "    FROM attack_chain_nodes i "
              + "    JOIN injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.node_contract_id "
              + "    JOIN attack_patterns_kill_chain_phases apkcp ON apkcp.attack_pattern_id = icap.attack_pattern_id "
              + "    WHERE i.node_id IN (:ids) "
              + "    GROUP BY i.node_id "
              + "), "
              + "inject_platforms AS ( "
              + "    SELECT  "
              + "        i.node_id, "
              + "        array_union_agg(injcon.injector_contract_platforms) as platform_ids "
              + "    FROM attack_chain_nodes i "
              + "    JOIN injectors_contracts injcon ON injcon.injector_contract_id = i.node_contract_id "
              + "    WHERE i.node_id IN (:ids) "
              + "    GROUP BY i.node_id "
              + ") "
              + "SELECT  "
              + "    i.node_id, "
              + "    ins.status_name, "
              + "    i.node_attack_chain_id, "
              + "    COALESCE(it.team_ids, '{}') as inject_teams, "
              + "    COALESCE(ia.asset_ids, '{}') as inject_assets, "
              + "    COALESCE(iag.asset_group_ids, '{}') as inject_asset_groups, "
              + "    COALESCE(ie.expectation_ids, '{}') as inject_expectations, "
              + "    COALESCE(ic.communication_ids, '{}') as inject_communications, "
              + "    COALESCE(ikc.phase_ids, '{}') as inject_kill_chain_phases, "
              + "    COALESCE(ip.platform_ids, '{}') as inject_platforms "
              + "FROM attack_chain_nodes i "
              + "LEFT JOIN injects_statuses ins ON ins.status_inject = i.node_id "
              + "LEFT JOIN inject_teams it ON it.node_id = i.node_id "
              + "LEFT JOIN inject_assets ia ON ia.node_id = i.node_id "
              + "LEFT JOIN inject_asset_groups iag ON iag.node_id = i.node_id "
              + "LEFT JOIN inject_expectations ie ON ie.node_id = i.node_id "
              + "LEFT JOIN inject_communications ic ON ic.node_id = i.node_id "
              + "LEFT JOIN inject_kill_chains ikc ON ikc.node_id = i.node_id "
              + "LEFT JOIN inject_platforms ip ON ip.node_id = i.node_id "
              + "WHERE i.node_id IN (:ids);",
      nativeQuery = true)
  List<RawAttackChainNode> findRawByIds(@Param("ids") List<String> ids);

  @Query(
      value =
          " SELECT attack_chain_nodes.node_id, "
              + "coalesce(array_agg(it.team_id) FILTER ( WHERE it.team_id IS NOT NULL ), '{}') as inject_teams "
              + "FROM attack_chain_nodes "
              + "LEFT JOIN attack_chain_nodes_teams it ON attack_chain_nodes.node_id = it.node_id "
              + "WHERE attack_chain_nodes.node_id IN :ids AND it.team_id = :teamId "
              + "GROUP BY attack_chain_nodes.node_id",
      nativeQuery = true)
  Set<RawAttackChainNode> findRawAttackChainNodeTeams(
      @Param("ids") Collection<String> ids, @Param("teamId") String teamId);

  @Query(
      value =
          "SELECT org.*, "
              + "array_agg(DISTINCT org_tags.tag_id) FILTER (WHERE org_tags.tag_id IS NOT NULL) AS organization_tags, "
              + "array_agg(DISTINCT attack_chain_nodes.node_id) FILTER (WHERE attack_chain_nodes.node_id IS NOT NULL) AS organization_injects, "
              + "coalesce(array_length(array_agg(DISTINCT attack_chain_nodes.node_id) FILTER (WHERE attack_chain_nodes.node_id IS NOT NULL), 1), 0) AS organization_injects_number "
              + "FROM organizations org "
              + "LEFT JOIN organizations_tags org_tags ON org.organization_id = org_tags.organization_id "
              + "LEFT JOIN users ON users.user_organization = org.organization_id "
              + "LEFT JOIN users_teams ON users.user_id = users_teams.user_id "
              + "LEFT JOIN attack_chain_nodes_teams ON attack_chain_nodes_teams.team_id = users_teams.team_id "
              + "LEFT JOIN attack_chain_nodes ON attack_chain_nodes.node_id = attack_chain_nodes_teams.node_id OR attack_chain_nodes.node_all_teams "
              + "GROUP BY org.organization_id",
      nativeQuery = true)
  List<RawAttackChainNode> rawAll();

  // -- TEAM --

  @Modifying
  @Query(
      value =
          "DELETE FROM attack_chain_nodes_teams it "
              + "WHERE it.team_id IN :teamIds "
              + "AND EXISTS (SELECT 1 FROM attack_chain_nodes i WHERE it.node_id = i.node_id AND i.node_attack_chain_run_id = :attackChainRunId)",
      nativeQuery = true)
  @Transactional
  void removeTeamsForAttackChainRun(
      @Param("attackChainRunId") final String attackChainRunId,
      @Param("teamIds") final List<String> teamIds);

  @Modifying
  @Query(
      value =
          "DELETE FROM attack_chain_nodes_teams it "
              + "WHERE it.team_id IN :teamIds "
              + "AND EXISTS (SELECT 1 FROM attack_chain_nodes i WHERE it.node_id = i.node_id AND i.node_attack_chain_id = :attackChainId)",
      nativeQuery = true)
  @Transactional
  void removeTeamsForAttackChain(
      @Param("attackChainId") final String attackChainId,
      @Param("teamIds") final List<String> teamIds);

  @Query(
      value =
          """
    SELECT DISTINCT i.node_id AS id, i.node_title AS label, i.node_created_at
    FROM attack_chain_nodes i
    INNER JOIN findings f ON f.finding_inject_id = i.node_id
    WHERE (:title IS NULL OR LOWER(i.node_title) LIKE LOWER(CONCAT('%', COALESCE(:title, ''), '%')))
      ORDER BY i.node_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllByTitleLinkedToFindings(@Param("title") String title, Pageable pageable);

  @Query(
      value =
          """
    SELECT DISTINCT i.node_id AS id, i.node_title AS label, i.node_created_at
    FROM attack_chain_nodes i
    INNER JOIN findings f ON f.finding_inject_id = i.node_id
    LEFT JOIN findings_assets fa ON fa.finding_id = f.finding_id
    LEFT JOIN attack_chains_runs se ON se.run_id = i.node_attack_chain_run_id
    WHERE (i.node_attack_chain_run_id = :sourceId OR se.attack_chain_id = :sourceId OR fa.asset_id = :sourceId)
      AND (:title IS NULL OR LOWER(i.node_title) LIKE LOWER(CONCAT('%', COALESCE(:title, ''), '%')))
      ORDER BY i.node_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllByTitleLinkedToFindingsWithContext(
      @Param("sourceId") String sourceId, @Param("title") String title, Pageable pageable);

  @Query(
      value = "SELECT i.node_content FROM attack_chain_nodes i WHERE i.node_id IN :attackChainNodeIds",
      nativeQuery = true)
  List<String> findContentsByAttackChainNodeIds(@NotBlank Set<String> attackChainNodeIds);

  /**
   * Check if an AttackChainNode exists by its ID without loading the entity. This is useful for
   * because of the cascade configuration
   *
   * @param id the ID of the AttackChainNode to check
   * @return true if the AttackChainNode exists, false otherwise
   */
  @Query(
      "SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM AttackChainNode i WHERE i.id = :id")
  boolean existsByIdWithoutLoading(@Param("id") String id);

  /**
   * Check if an AttackChainNode exists by its ID, where the AttackChainNode is an atomic testing.
   *
   * @param id ID of the attackChainNode to check
   * @return true if the AttackChainNode exists and is an atomic testing, false otherwise
   */
  boolean existsByIdAndAttackChainIsNullAndAttackChainRunIsNull(String id);

  @Modifying
  @Query(
      value =
          "DELETE FROM attack_chain_nodes i "
              + "USING injectors_contracts ic, payloads p "
              + "WHERE i.node_contract_id = ic.injector_contract_id "
              + "AND ic.injector_contract_payload = p.payload_id "
              + "AND p.payload_type = '"
              + DNS_RESOLUTION_TYPE
              + "' "
              + "AND i.node_attack_chain_id = :attackChainId",
      nativeQuery = true)
  void deleteAllAttackChainNodesWithDnsResolutionContractsByAttackChainId(
      @Param("attackChainId") String attackChainId);

  @Modifying
  @Query(
      value =
          "DELETE FROM attack_chain_nodes i "
              + "USING injectors_contracts ic, injectors_contracts_vulnerabilities icv "
              + "WHERE i.node_contract_id = ic.injector_contract_id "
              + "AND ic.injector_contract_id = icv.injector_contract_id "
              + "AND i.node_attack_chain_id = :attackChainId",
      nativeQuery = true)
  void deleteAllAttackChainNodesWithVulnerableContractsByAttackChainId(
      @Param("attackChainId") String attackChainId);

  @Modifying
  @Query(
      value =
          "DELETE FROM attack_chain_nodes i "
              + "USING injectors_contracts ic, injectors_contracts_attack_patterns icap "
              + "WHERE i.node_contract_id = ic.injector_contract_id "
              + "AND ic.injector_contract_id = icap.injector_contract_id "
              + "AND i.node_attack_chain_id = :attackChainId",
      nativeQuery = true)
  void deleteAllAttackChainNodesWithAttackPatternContractsByAttackChainId(
      @Param("attackChainId") String attackChainId);

  @Modifying
  @Query(
      value =
          "DELETE FROM attack_chain_nodes i WHERE i.node_contract_id = :nodeContract AND i.node_attack_chain_id = :attackChainId",
      nativeQuery = true)
  void deleteAllByAttackChainIdAndNodeContract(String nodeContract, String attackChainId);

  @EntityGraph(attributePaths = {"expectations", "injectorContract"})
  @Query("SELECT i FROM AttackChainNode i WHERE i.id IN :ids")
  List<AttackChainNode> findAllByIdWithExpectations(@Param("ids") List<String> ids);

  @Modifying
  @Query(value = "DELETE FROM attack_chain_nodes WHERE node_id = :id", nativeQuery = true)
  void deleteByIdNative(@Param("id") String id);

  @Modifying
  @Query(value = "DELETE FROM attack_chain_nodes WHERE node_id IN :ids", nativeQuery = true)
  void deleteByAllIdsNative(@Param("ids") List<String> ids);

  @Query("SELECT i FROM AttackChainNode i WHERE i.attackChainRun.id = :simulationId")
  List<AttackChainNode> findAllAttackChainNodeBySimulationId(
      @Param("simulationId") String simulationId);
}
