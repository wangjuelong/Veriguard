package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.raw.*;
import io.veriguard.utils.Constants;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AttackChainRunRepository
    extends JpaRepository<AttackChainRun, String>,
        StatisticRepository,
        JpaSpecificationExecutor<AttackChainRun> {

  @NotNull
  Optional<AttackChainRun> findById(@NotNull String id);

  @Query(
      value = "select e from AttackChainRun e where e.status = 'SCHEDULED' and e.start <= :start")
  List<AttackChainRun> findAllShouldBeInRunningState(@Param("start") Instant start);

  @Override
  @Query(
      "select count(distinct e) from AttackChainRun e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and e.createdAt > :creationDate")
  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Override
  @Query("select count(distinct e) from AttackChainRun e where e.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "select e.*, se.attack_chain_id from attack_chain_runs e "
              + "left join attack_chain_nodes as inject on e.run_id = inject.node_attack_chain_run_id and inject.node_enabled = 'true' "
              + "left join injects_statuses as status on inject.node_id = status.status_inject and status.status_name not in ('DRAFT', 'PENDING', 'QUEUING', 'EXECUTING')"
              + "left join attack_chains_runs as se on e.run_id = se.run_id "
              + "where e.run_status = 'RUNNING' group by e.run_id, se.attack_chain_id having count(status) = count(inject) "
              + "and count(inject) filter (where inject.node_collect_status = 'COMPLETED' and status.status_name <> 'ERROR') = count(inject) filter (where status.status_name <> 'ERROR');",
      nativeQuery = true)
  List<AttackChainRun> thatMustBeFinished();

  @Query(
      """
    SELECT e FROM AttackChainRun e, AttackChain s
    WHERE e.attackChain.id = s.id AND s.id = :#{#attackChainRun.attackChain.id}
      AND e.launchOrder > :#{#attackChainRun.launchOrder}
      AND e.id != :#{#attackChainRun.id}
    ORDER BY e.launchOrder ASC LIMIT 1
    """)
  Optional<AttackChainRun> following(@Param("attackChainRun") AttackChainRun attackChainRun);

  /**
   * Get the raw version of the attackChainRuns
   *
   * @return the list of attackChainRuns
   */
  @Query(
      value =
          " SELECT ex.run_id AS attack_chain_run_id, "
              + "ex.run_status AS attack_chain_run_status, "
              + "ex.run_start_date AS attack_chain_run_start_date, "
              + "ex.run_updated_at AS attack_chain_run_updated_at, "
              + "ex.run_end_date AS attack_chain_run_end_date, "
              + "ex.run_name AS attack_chain_run_name, "
              + "ex.run_category AS attack_chain_run_category, "
              + "ex.run_subtitle AS attack_chain_run_subtitle, "
              + " array_agg(distinct ie.node_id) FILTER ( WHERE ie.node_id IS NOT NULL ) as node_ids, "
              + " array_agg(distinct et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as attack_chain_run_tags "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chain_node_expectations ie ON ex.run_id = ie.run_id "
              + "LEFT JOIN attack_chain_runs_tags et ON et.run_id = ex.run_id "
              + "GROUP BY ex.run_id ;",
      nativeQuery = true)
  List<RawAttackChainRunSimple> rawAll();

  /**
   * Get the raw version of the attackChainRuns in the list in param
   *
   * @param attackChainRunIds the list of attackChainRun ids
   * @return the list of attackChainRuns
   */
  // Hibernate 6 does not expand a Collection IN parameter inside a native query, so we use
  // PostgreSQL `= ANY(:array)` with a String[] argument.
  @Query(
      value =
          " SELECT ex.run_id AS attack_chain_run_id, "
              + "ex.run_status AS attack_chain_run_status, "
              + "ex.run_start_date AS attack_chain_run_start_date, "
              + "ex.run_updated_at AS attack_chain_run_updated_at, "
              + "ex.run_end_date AS attack_chain_run_end_date, "
              + "ex.run_name AS attack_chain_run_name, "
              + "ex.run_category AS attack_chain_run_category, "
              + "ex.run_subtitle AS attack_chain_run_subtitle, "
              + " array_agg(distinct ie.node_id) FILTER ( WHERE ie.node_id IS NOT NULL ) as node_ids, "
              + " array_agg(distinct et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as attack_chain_run_tags "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chain_node_expectations ie ON ex.run_id = ie.run_id "
              + "LEFT JOIN attack_chain_runs_tags et ON et.run_id = ex.run_id "
              + "WHERE ex.run_id = ANY(:attackChainRunIds) "
              + "GROUP BY ex.run_id ;",
      nativeQuery = true)
  List<RawAttackChainRunSimple> rawByAttackChainRunIds(
      @Param("attackChainRunIds") String[] attackChainRunIds);

  /**
   * Get the raw version of the attackChainRuns a user can see
   *
   * @param userId the id of the user
   * @return the list of attackChainRuns
   */
  @Query(
      value =
          " SELECT ex.run_id AS attack_chain_run_id, "
              + "ex.run_status AS attack_chain_run_status, "
              + "ex.run_start_date AS attack_chain_run_start_date, "
              + "ex.run_updated_at AS attack_chain_run_updated_at, "
              + "ex.run_end_date AS attack_chain_run_end_date, "
              + "ex.run_name AS attack_chain_run_name, "
              + "ex.run_category AS attack_chain_run_category, "
              + "ex.run_subtitle AS attack_chain_run_subtitle, "
              + " array_agg(et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as attack_chain_run_tags, "
              + " array_agg(attack_chain_nodes.node_id) FILTER ( WHERE attack_chain_nodes.node_id IS NOT NULL ) as node_ids "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chain_node_expectations ie ON ex.run_id = ie.run_id "
              + "LEFT JOIN attack_chain_nodes ON ie.node_id = attack_chain_nodes.node_id "
              + "LEFT JOIN attack_chain_runs_tags et ON et.run_id = ex.run_id "
              + "INNER JOIN grants ON grants.grant_resource = ex.run_id AND grants.grant_resource_type = 'SIMULATION' "
              + "INNER JOIN groups ON grants.grant_group = groups.group_id "
              + "INNER JOIN users_groups ON groups.group_id = users_groups.group_id "
              + "WHERE users_groups.user_id = :userId "
              + "GROUP BY ex.run_id ;",
      nativeQuery = true)
  List<RawAttackChainRunSimple> rawAllGranted(@Param("userId") String userId);

  /**
   * Get the raw version of the attackChainRuns a user can see by attackChainRun ids
   *
   * @param userId the id of the user
   * @param attackChainRunIds the list of attackChainRun ids
   * @return the list of attackChainRuns
   */
  // Hibernate 6 does not expand a Collection IN parameter inside a native query, so we use
  // PostgreSQL `= ANY(:array)` with a String[] argument.
  @Query(
      value =
          " SELECT ex.run_id AS attack_chain_run_id, "
              + "ex.run_status AS attack_chain_run_status, "
              + "ex.run_start_date AS attack_chain_run_start_date, "
              + "ex.run_updated_at AS attack_chain_run_updated_at, "
              + "ex.run_end_date AS attack_chain_run_end_date, "
              + "ex.run_name AS attack_chain_run_name, "
              + "ex.run_category AS attack_chain_run_category, "
              + "ex.run_subtitle AS attack_chain_run_subtitle, "
              + " array_agg(et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as attack_chain_run_tags, "
              + " array_agg(attack_chain_nodes.node_id) FILTER ( WHERE attack_chain_nodes.node_id IS NOT NULL ) as node_ids "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chain_node_expectations ie ON ex.run_id = ie.run_id "
              + "LEFT JOIN attack_chain_nodes ON ie.node_id = attack_chain_nodes.node_id "
              + "LEFT JOIN attack_chain_runs_tags et ON et.run_id = ex.run_id "
              + "INNER JOIN grants ON grants.grant_resource = ex.run_id AND grants.grant_resource_type = 'SIMULATION' "
              + "INNER JOIN groups ON grants.grant_group = groups.group_id "
              + "INNER JOIN users_groups ON groups.group_id = users_groups.group_id "
              + "WHERE users_groups.user_id = :userId "
              + "AND ex.run_id = ANY(:attackChainRunIds) "
              + "GROUP BY ex.run_id ;",
      nativeQuery = true)
  List<RawAttackChainRunSimple> rawGrantedByAttackChainRunIds(
      @Param("userId") String userId, @Param("attackChainRunIds") String[] attackChainRunIds);

  /**
   * Get the raw version of the attackChainRuns a user can see
   *
   * @param attackChainRunId the id of the user
   * @return the list of attackChainRuns
   */
  @Query(
      value =
          " SELECT ex.run_id, ex.run_name, ex.run_description, ex.run_status, ex.run_subtitle, "
              + "ex.run_category, ex.run_main_focus, ex.run_severity, ex.run_start_date, "
              + "ex.run_end_date, ex.attack_chain_run_message_header, ex.attack_chain_run_message_footer, ex.attack_chain_run_mail_from, "
              + "ex.run_lessons_anonymized, ex.run_custom_dashboard, ex.run_created_at, ex.run_updated_at, "
              + "se.attack_chain_id, inj.node_attack_chain_id, "
              + " coalesce(array_agg(emrt.attack_chain_run_reply_to) FILTER ( WHERE emrt.attack_chain_run_reply_to IS NOT NULL ), '{}') as attack_chain_run_reply_to, "
              + " coalesce(array_agg(et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ), '{}') as run_tags, "
              + " coalesce(array_agg(ut.user_id) FILTER ( WHERE ut.user_id IS NOT NULL ), '{}') as attack_chain_run_users, "
              + " coalesce(array_agg(la.lessons_answer_id) FILTER ( WHERE la.lessons_answer_id IS NOT NULL ), '{}') as lessons_answers, "
              + " coalesce(array_agg(logs.log_id) FILTER ( WHERE logs.log_id IS NOT NULL ), '{}') as logs, "
              + " coalesce(array_agg(inj.node_id) FILTER ( WHERE inj.node_id IS NOT NULL ), '{}') as node_ids "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chains_runs se ON se.run_id = ex.run_id "
              + "LEFT JOIN exercise_mails_reply_to emrt ON emrt.run_id = ex.run_id "
              + "LEFT JOIN attack_chain_runs_tags et ON et.run_id = ex.run_id "
              + "LEFT JOIN attack_chain_runs_teams ext ON ext.run_id = ex.run_id "
              + "LEFT JOIN users_teams ut ON ext.team_id = ut.team_id "
              + "LEFT JOIN lessons_categories lc ON lc.lessons_category_exercise = ex.run_id "
              + "LEFT JOIN lessons_questions lq ON lq.lessons_question_category = lc.lessons_category_id "
              + "LEFT JOIN lessons_answers la ON la.lessons_answer_question = lq.lessons_question_id "
              + "LEFT JOIN logs ON logs.log_exercise = ex.run_id "
              + "LEFT JOIN attack_chain_nodes inj ON ex.run_id = inj.node_attack_chain_run_id "
              + "WHERE ex.run_id = :attackChainRunId "
              + "GROUP BY ex.run_id, inj.node_attack_chain_id, se.attack_chain_id ;",
      nativeQuery = true)
  RawSimulation rawDetailsById(@Param("attackChainRunId") String attackChainRunId);

  @Query(
      value =
          " SELECT DISTINCT (ie.node_id) "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chain_node_expectations ie ON ex.run_id = ie.run_id "
              + "WHERE ex.run_id = :attackChainRunId AND ie.node_id IS NOT NULL;",
      nativeQuery = true)
  Set<String> findAttackChainNodesByAttackChainRun(
      @Param("attackChainRunId") String attackChainRunId);

  @Query(
      value =
          " SELECT ex.run_id, "
              + "ex.run_status, "
              + "ex.run_start_date, "
              + "ex.run_updated_at, "
              + "ex.run_end_date, "
              + "ex.run_name, "
              + "ex.run_category, "
              + "ex.run_subtitle, "
              + " array_agg(distinct ie.node_id) FILTER ( WHERE ie.node_id IS NOT NULL ) as node_ids, "
              + " array_agg(distinct et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as run_tags "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chains_runs s ON s.run_id = ex.run_id "
              + "LEFT JOIN attack_chain_runs_tags et ON et.run_id = ex.run_id "
              + "LEFT JOIN attack_chain_node_expectations ie ON ex.run_id = ie.run_id "
              + "WHERE s.attack_chain_id IN (:attackChainIds) "
              + "GROUP BY ex.run_id ;",
      nativeQuery = true)
  List<RawAttackChainRunSimple> rawAllByAttackChainIds(
      @Param("attackChainIds") List<String> attackChainIds);

  // -- TEAM --

  @Modifying
  @Query(
      value =
          "DELETE FROM attack_chain_runs_teams et WHERE et.run_id = :attackChainRunId AND et.team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void removeTeams(
      @Param("attackChainRunId") final String attackChainRunId,
      @Param("teamIds") final List<String> teamIds);

  @Query(
      value =
          " SELECT ex.run_id, ex.run_end_date, "
              + " array_agg(distinct ie.node_id) FILTER ( WHERE ie.node_id IS NOT NULL ) as node_ids "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chains_runs s ON s.run_id = ex.run_id "
              + "LEFT JOIN attack_chain_node_expectations ie ON ex.run_id = ie.run_id "
              + "WHERE s.attack_chain_id = :attackChainId "
              + "AND ex.run_status = 'FINISHED' "
              + "AND ex.run_end_date IS NOT NULL "
              + "GROUP BY ex.run_id, ex.run_end_date "
              + "ORDER BY ex.run_end_date DESC "
              + "LIMIT 10 ;",
      nativeQuery = true)
  List<RawFinishedAttackChainRunWithAttackChainNodes>
      rawLatestFinishedAttackChainRunsWithAttackChainNodesByAttackChainId(
          @Param("attackChainId") String attackChainId);

  @Query(
      value =
          """
        SELECT DISTINCT e.run_id AS id, e.run_name AS label, e.run_created_at
        FROM attack_chain_nodes i
        INNER JOIN findings f ON f.finding_inject_id = i.node_id
        INNER JOIN attack_chain_runs e ON i.node_attack_chain_run_id = e.run_id
        WHERE (:name IS NULL OR LOWER(e.run_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')))
        ORDER BY e.run_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllOptionByNameLinkedToFindings(@Param("name") String name, Pageable pageable);

  @Query(
      value =
          """
        SELECT DISTINCT e.run_id AS id, e.run_name AS label, e.run_created_at
        FROM attack_chain_nodes i
        INNER JOIN findings f ON f.finding_inject_id = i.node_id
        LEFT JOIN findings_assets fa ON fa.finding_id = f.finding_id
        LEFT JOIN attack_chain_runs e ON i.node_attack_chain_run_id = e.run_id
        LEFT JOIN attack_chains_runs se ON se.run_id = e.run_id
        WHERE (se.attack_chain_id = :sourceId OR fa.asset_id = :sourceId)
        AND (:name IS NULL OR LOWER(e.run_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')))
        ORDER BY e.run_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllOptionByNameLinkedToFindingsWithContext(
      @Param("sourceId") String sourceId, @Param("name") String name, Pageable pageable);

  // -- INDEXING --

  @Query(
      value =
          "WITH exercise_data AS ("
              + "SELECT ex.run_id, ex.run_name, ex.run_status, ex.run_start_date, ex.run_created_at, MAX(se.attack_chain_id) AS attack_chain_id, " // MAX here is used to get 1 element and not a list because we know that 1 attackChainRun is linked to only 1 attackChain
              + "GREATEST(ex.run_updated_at, max(inj.node_updated_at), max(ic.injector_contract_updated_at)) as attack_chain_run_injects_updated_at, "
              + "array_agg(DISTINCT et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as run_tags, "
              + "array_agg(DISTINCT ete.team_id) FILTER ( WHERE ete.team_id IS NOT NULL ) as attack_chain_run_teams, "
              + "array_agg(DISTINCT ia.asset_id) FILTER ( WHERE ia.asset_id IS NOT NULL ) as attack_chain_run_assets, "
              + "array_agg(DISTINCT iag.asset_group_id) FILTER ( WHERE iag.asset_group_id IS NOT NULL ) as attack_chain_run_asset_groups, "
              + "array_union_agg(ic.injector_contract_platforms) FILTER ( WHERE ic.injector_contract_platforms IS NOT NULL ) as attack_chain_run_platforms "
              + "FROM attack_chain_runs ex "
              + "LEFT JOIN attack_chain_runs_tags et ON et.run_id = ex.run_id "
              + "LEFT JOIN attack_chain_runs_teams ete ON ete.run_id = ex.run_id "
              + "LEFT JOIN attack_chain_nodes inj ON ex.run_id = inj.node_attack_chain_run_id "
              + "LEFT JOIN attack_chain_nodes_assets ia ON ia.node_id = inj.node_id "
              + "LEFT JOIN attack_chain_nodes_asset_groups iag ON iag.node_id = inj.node_id "
              + "LEFT JOIN attack_chains_runs se ON ex.run_id = se.run_id "
              + "LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = inj.node_contract_id "
              + "GROUP BY ex.run_id, ex.run_name, ex.run_created_at, ex.run_updated_at"
              + ") "
              + "SELECT * FROM exercise_data ed "
              + "WHERE ed.attack_chain_run_injects_updated_at > :from "
              + "ORDER BY ed.attack_chain_run_injects_updated_at ASC LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawSimulation> findForIndexing(@Param("from") Instant from);
}
