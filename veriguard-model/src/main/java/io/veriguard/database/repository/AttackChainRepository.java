package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.raw.RawAttackChain;
import io.veriguard.database.raw.RawAttackChainRunSimple;
import io.veriguard.database.raw.RawAttackChainSimple;
import io.veriguard.utils.Constants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository interface for {@link AttackChain} entities.
 *
 * <p>This repository provides data access operations for attackChains, which are reusable templates
 * for security attackChainRuns. AttackChains define collections of attackChainNodes, team
 * configurations, and recurrence settings. It supports:
 *
 * <ul>
 *   <li>Standard CRUD operations via {@link JpaRepository}
 *   <li>Dynamic filtering via {@link JpaSpecificationExecutor}
 *   <li>Statistical queries via {@link StatisticRepository}
 *   <li>Access-controlled queries respecting user grants
 *   <li>Search engine indexing support
 *   <li>Category management and search
 *   <li>Team assignment operations
 * </ul>
 *
 * @see AttackChain
 * @see io.veriguard.database.model.AttackChainRun
 * @see io.veriguard.database.model.AttackChainNode
 */
@Repository
public interface AttackChainRepository
    extends JpaRepository<AttackChain, String>,
        StatisticRepository,
        JpaSpecificationExecutor<AttackChain> {

  @Query(
      value =
          "WITH scenario_data AS ("
              + "SELECT s.attack_chain_id, s.attack_chain_name, s.attack_chain_recurrence, s.attack_chain_created_at, "
              + "GREATEST(s.attack_chain_updated_at, max(inj.node_updated_at), max(ic.injector_contract_updated_at)) as scenario_injects_updated_at, "
              + "array_agg(DISTINCT st.tag_id) FILTER (WHERE st.tag_id IS NOT NULL) as scenario_tags, "
              + "array_agg(DISTINCT ste.team_id) FILTER (WHERE ste.team_id IS NOT NULL) as scenario_teams, "
              + "array_agg(DISTINCT ia.asset_id) FILTER (WHERE ia.asset_id IS NOT NULL) as scenario_assets, "
              + "array_agg(DISTINCT iag.asset_group_id) FILTER (WHERE iag.asset_group_id IS NOT NULL) as scenario_asset_groups, "
              + "array_union_agg(ic.injector_contract_platforms) FILTER ( WHERE ic.injector_contract_platforms IS NOT NULL ) as scenario_platforms "
              + "FROM attack_chains s "
              + "LEFT JOIN attack_chains_tags st ON st.attack_chain_id = s.attack_chain_id "
              + "LEFT JOIN attack_chains_teams ste ON ste.attack_chain_id = s.attack_chain_id "
              + "LEFT JOIN attack_chain_nodes inj ON s.attack_chain_id = inj.node_attack_chain_id "
              + "LEFT JOIN attack_chain_nodes_assets ia ON ia.node_id = inj.node_id "
              + "LEFT JOIN attack_chain_nodes_asset_groups iag ON iag.node_id = inj.node_id "
              + "LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = inj.node_contract_id "
              + "GROUP BY s.attack_chain_id, s.attack_chain_name, s.attack_chain_created_at, s.attack_chain_updated_at"
              + ") "
              + "SELECT * FROM scenario_data sd "
              + "WHERE sd.scenario_injects_updated_at > :from "
              + "ORDER BY sd.scenario_injects_updated_at ASC LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawAttackChainSimple> findForIndexing(@Param("from") Instant from);

  @Query(
      value =
          "SELECT ex.run_id, "
              + "ex.run_status, "
              + "ex.run_start_date, "
              + "ex.run_created_at, "
              + "ex.run_updated_at, "
              + "ex.run_end_date, "
              + "ex.run_name, "
              + "ex.run_category, "
              + "ex.run_subtitle, "
              + " array_agg(distinct ie.node_id) FILTER ( WHERE ie.node_id IS NOT NULL ) as inject_ids, "
              + " array_agg(distinct et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as run_tags "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chains_runs se ON se.run_id = ex.run_id "
              + "LEFT JOIN attack_chains s ON se.attack_chain_id = s.attack_chain_id "
              + "LEFT JOIN attack_chain_runs_tags et ON et.run_id = ex.run_id "
              + "LEFT JOIN attack_chain_node_expectations ie ON ex.run_id = ie.run_id "
              + "WHERE s.attack_chain_external_reference = :externalReference "
              + "GROUP BY ex.run_id ;",
      nativeQuery = true)
  List<RawAttackChainRunSimple> rawAllByExternalReference(
      @Param("externalReference") String externalReference);

  @Override
  @Query(
      "select count(distinct u) from User u "
          + "join u.teams as team "
          + "join team.attackChains as s "
          + "join s.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and u.createdAt > :creationDate")
  long userCount(String userId, Instant creationDate);

  @Override
  @Query("select count(distinct s) from AttackChain s where s.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "SELECT attack_chain_category, COUNT(*) AS category_count "
              + "FROM attack_chains "
              + "GROUP BY attack_chain_category "
              + "ORDER BY category_count DESC "
              + "LIMIT :limit",
      nativeQuery = true)
  List<Object[]> findTopCategories(@Param("limit") @NotNull final int limit);

  @Query(
      value =
          "SELECT sce.attack_chain_id, sce.attack_chain_name, sce.attack_chain_subtitle, array_agg(sct.tag_id) FILTER (WHERE sct.tag_id IS NOT NULL) as scenario_tags "
              + "FROM attack_chains sce "
              + "LEFT JOIN attack_chains_tags sct ON sct.attack_chain_id = sce.attack_chain_id "
              + "INNER JOIN grants ON grants.grant_resource = sce.attack_chain_id AND grants.grant_resource_type = 'SCENARIO' "
              + "INNER JOIN groups ON grants.grant_group = groups.group_id "
              + "INNER JOIN users_groups ON groups.group_id = users_groups.group_id "
              + "WHERE users_groups.user_id = :userId "
              + "GROUP BY sce.attack_chain_id",
      nativeQuery = true)
  List<RawAttackChainSimple> rawAllGranted(@Param("userId") String userId);

  @Query(
      value =
          "SELECT sce.attack_chain_id, sce.attack_chain_name, sce.attack_chain_subtitle, array_agg(sct.tag_id) FILTER (WHERE sct.tag_id IS NOT NULL) as scenario_tags "
              + "FROM attack_chains sce "
              + "LEFT JOIN attack_chains_tags sct ON sct.attack_chain_id = sce.attack_chain_id "
              + "INNER JOIN grants ON grants.grant_resource = sce.attack_chain_id AND grants.grant_resource_type = 'SCENARIO' "
              + "INNER JOIN groups ON grants.grant_group = groups.group_id "
              + "INNER JOIN users_groups ON groups.group_id = users_groups.group_id "
              + "WHERE users_groups.user_id = :userId "
              + "AND sce.attack_chain_id IN :attackChainIds "
              + "GROUP BY sce.attack_chain_id",
      nativeQuery = true)
  List<RawAttackChainSimple> rawGrantedByAttackChainIds(
      @Param("userId") String userId, @Param("attackChainIds") List<String> attackChainIds);

  @Query(
      value =
          "SELECT sce.attack_chain_id, sce.attack_chain_name, sce.attack_chain_subtitle, array_agg(sct.tag_id) FILTER (WHERE sct.tag_id IS NOT NULL) as scenario_tags "
              + "FROM attack_chains sce "
              + "LEFT JOIN attack_chains_tags sct ON sct.attack_chain_id = sce.attack_chain_id "
              + "GROUP BY sce.attack_chain_id",
      nativeQuery = true)
  List<RawAttackChainSimple> rawAll();

  @Query(
      value =
          "SELECT sce.attack_chain_id, sce.attack_chain_name, sce.attack_chain_subtitle, array_agg(sct.tag_id) FILTER (WHERE sct.tag_id IS NOT NULL) as scenario_tags "
              + "FROM attack_chains sce "
              + "LEFT JOIN attack_chains_tags sct ON sct.attack_chain_id = sce.attack_chain_id "
              + "WHERE sce.attack_chain_id IN :attackChainIds "
              + "GROUP BY sce.attack_chain_id",
      nativeQuery = true)
  List<RawAttackChainSimple> rawByAttackChainIds(@Param("attackChainIds") List<String> attackChainIds);

  @Query(
      value =
          "WITH "
              + "all_users AS ( "
              + "  SELECT st.attack_chain_id, COUNT(DISTINCT ut.user_id) AS scenario_all_users_number "
              + "  FROM attack_chains_teams st "
              + "  JOIN users_teams ut ON ut.team_id = st.team_id "
              + "  WHERE st.attack_chain_id = :attackChainId "
              + "  GROUP BY st.attack_chain_id "
              + "), "
              + "scenario_users AS ( "
              + "  SELECT attack_chain_id, "
              + "         COUNT(DISTINCT user_id) AS scenario_users_number, "
              + "         json_agg(DISTINCT stu.*) FILTER (WHERE stu IS NOT NULL) AS scenario_teams_users "
              + "  FROM attack_chains_teams_users stu "
              + "  WHERE attack_chain_id = :attackChainId "
              + "  GROUP BY attack_chain_id "
              + "), "
              + "attack_chain_runs AS ( "
              + "  SELECT attack_chain_id, "
              + "         array_agg(DISTINCT run_id) FILTER (WHERE run_id IS NOT NULL) AS scenario_exercises "
              + "  FROM attack_chains_runs "
              + "  WHERE attack_chain_id = :attackChainId "
              + "  GROUP BY attack_chain_id "
              + "), "
              + "kill_chain AS ( "
              + "  SELECT i.node_attack_chain_id AS attack_chain_id, "
              + "         json_agg(DISTINCT kcp.*) FILTER (WHERE kcp IS NOT NULL) AS scenario_kill_chain_phases "
              + "  FROM attack_chain_nodes i "
              + "  JOIN injectors_contracts ic ON ic.injector_contract_id = i.node_contract_id "
              + "  JOIN injectors_contracts_attack_patterns icap ON ic.injector_contract_id = icap.injector_contract_id "
              + "  JOIN attack_patterns_kill_chain_phases apkcp ON icap.attack_pattern_id = apkcp.attack_pattern_id "
              + "  JOIN kill_chain_phases kcp ON kcp.phase_id = apkcp.phase_id "
              + "  WHERE i.node_attack_chain_id = :attackChainId "
              + "  GROUP BY i.node_attack_chain_id "
              + "), "
              + "platforms AS ( "
              + "  SELECT i.node_attack_chain_id AS attack_chain_id, "
              + "         array_union_agg(ic.injector_contract_platforms) "
              + "           FILTER (WHERE ic.injector_contract_platforms IS NOT NULL) "
              + "         AS scenario_platforms "
              + "  FROM attack_chain_nodes i "
              + "  JOIN injectors_contracts ic ON ic.injector_contract_id = i.node_contract_id "
              + "  WHERE i.node_attack_chain_id = :attackChainId "
              + "  GROUP BY i.node_attack_chain_id "
              + "), "
              + "tags AS ( "
              + "  SELECT attack_chain_id, "
              + "         array_agg(DISTINCT tag_id) FILTER (WHERE tag_id IS NOT NULL) AS scenario_tags "
              + "  FROM attack_chains_tags "
              + "  WHERE attack_chain_id = :attackChainId "
              + "  GROUP BY attack_chain_id "
              + ") "
              + "SELECT s.attack_chain_id              AS scenario_id, "
              + "       s.attack_chain_name            AS scenario_name, "
              + "       s.attack_chain_description     AS scenario_description, "
              + "       s.attack_chain_subtitle        AS scenario_subtitle, "
              + "       s.attack_chain_created_at      AS scenario_created_at, "
              + "       s.attack_chain_updated_at      AS scenario_updated_at, "
              + "       s.attack_chain_recurrence      AS scenario_recurrence, "
              + "       s.attack_chain_recurrence_start AS scenario_recurrence_start, "
              + "       s.attack_chain_recurrence_end   AS scenario_recurrence_end, "
              + "       s.attack_chain_category        AS scenario_category, "
              + "       s.attack_chain_severity        AS scenario_severity, "
              + "       s.attack_chain_main_focus      AS scenario_main_focus, "
              + "       s.attack_chain_external_reference AS scenario_external_reference, "
              + "       s.attack_chain_external_url    AS scenario_external_url, "
              + "       s.attack_chain_lessons_anonymized AS scenario_lessons_anonymized, "
              + "       s.attack_chain_custom_dashboard AS scenario_custom_dashboard, "
              + "       s.attack_chain_dependencies    AS scenario_dependencies, "
              + "       s.attack_chain_type_affinity   AS scenario_type_affinity, "
              + "       s.execution_mode               AS scenario_execution_mode, "
              + "       s.validation_parameter_set_id  AS scenario_validation_parameter_set_id, "
              + "       s.soc_correlation_rules        AS scenario_soc_correlation_rules, "
              + "       au.scenario_all_users_number, "
              + "       su.scenario_users_number, "
              + "       ex.scenario_exercises, "
              + "       kc.scenario_kill_chain_phases, "
              + "       pf.scenario_platforms, "
              + "       tg.scenario_tags, "
              + "       su.scenario_teams_users "
              + "FROM attack_chains s "
              + "LEFT JOIN all_users au ON au.attack_chain_id = s.attack_chain_id "
              + "LEFT JOIN scenario_users su ON su.attack_chain_id = s.attack_chain_id "
              + "LEFT JOIN attack_chain_runs ex ON ex.attack_chain_id = s.attack_chain_id "
              + "LEFT JOIN kill_chain kc ON kc.attack_chain_id = s.attack_chain_id "
              + "LEFT JOIN platforms pf ON pf.attack_chain_id = s.attack_chain_id "
              + "LEFT JOIN tags tg ON tg.attack_chain_id = s.attack_chain_id "
              + "WHERE s.attack_chain_id = :attackChainId",
      nativeQuery = true)
  RawAttackChain getAttackChainById(@Param("attackChainId") final String attackChainId);

  // -- CATEGORY --

  @Query(
      "SELECT DISTINCT s.category FROM AttackChain s WHERE LOWER(s.category) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
  List<String> findDistinctCategoriesBySearchTerm(
      @Param("searchTerm") final String searchTerm, Pageable pageable);

  // -- PAGINATION --

  @NotNull
  @EntityGraph(value = "Scenario.tags-injects", type = EntityGraph.EntityGraphType.LOAD)
  Page<AttackChain> findAll(@NotNull Specification<AttackChain> spec, @NotNull Pageable pageable);

  // -- TEAM --

  @Modifying
  @Query(
      value =
          "DELETE FROM attack_chains_teams st WHERE st.attack_chain_id = :attackChainId AND st.team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void removeTeams(
      @Param("attackChainId") final String attackChainId,
      @Param("teamIds") final List<String> teamIds);

  Optional<AttackChain> findByAttackChainRuns_Id(String attackChainRunId);
}
