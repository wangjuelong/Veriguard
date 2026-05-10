package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackChainNodeExpectationRepository
    extends CrudRepository<AttackChainNodeExpectation, String>,
        JpaSpecificationExecutor<AttackChainNodeExpectation> {

  @NotNull
  Optional<AttackChainNodeExpectation> findById(@NotNull String id);

  @Query(
      value =
          "select i from AttackChainNodeExpectation i where i.attackChainRun.id = :attackChainRunId")
  List<AttackChainNodeExpectation> findAllForAttackChainRun(
      @Param("attackChainRunId") String attackChainRunId);

  @Query(
      value =
          "select i from AttackChainNodeExpectation i where i.attackChainNode.id = :attackChainNodeId")
  List<AttackChainNodeExpectation> findAllByAttackChainNodeId(
      @Param("attackChainNodeId") @NotBlank final String attackChainNodeId);

  @Query(
      value =
          "select i from AttackChainNodeExpectation i where i.attackChainRun.id = :attackChainRunId and i.attackChainNode.id = :attackChainNodeId")
  List<AttackChainNodeExpectation> findAllForAttackChainRunAndAttackChainNode(
      @Param("attackChainRunId") @NotBlank final String attackChainRunId,
      @Param("attackChainNodeId") @NotBlank final String attackChainNodeId);

  // -- BY TARGET TYPE

  @Query(
      value =
          "select i from AttackChainNodeExpectation i "
              + "where i.attackChainNode.id = :attackChainNodeId "
              + "and i.team.id = :teamId "
              + "and i.user.id = :playerId "
              + "ORDER BY i.type, i.createdAt")
  List<AttackChainNodeExpectation> findAllByAttackChainNodeAndTeamAndPlayer(
      @Param("attackChainNodeId") @NotBlank final String attackChainNodeId,
      @Param("teamId") @NotBlank final String teamId,
      @Param("playerId") @NotBlank final String playerId);

  @Query(
      value =
          "select i from AttackChainNodeExpectation i "
              + "where i.attackChainNode.id = :attackChainNodeId "
              + "and i.user.id = :playerId "
              + "ORDER BY i.type, i.createdAt")
  List<AttackChainNodeExpectation> findAllByAttackChainNodeAndPlayer(
      @Param("attackChainNodeId") @NotBlank final String attackChainNodeId,
      @Param("playerId") @NotBlank final String playerId);

  @Query(
      "select ie from AttackChainNodeExpectation ie "
          + "where ie.attackChainNode.id = :attackChainNodeId "
          + "and ie.team.id = :teamId "
          + "and ie.name = :expectationName "
          + "ORDER BY ie.type, ie.createdAt")
  List<AttackChainNodeExpectation> findAllByAttackChainNodeAndTeamAndExpectationName(
      final String attackChainNodeId, final String teamId, final String expectationName);

  @Query(
      "select ie from AttackChainNodeExpectation ie "
          + "where ie.attackChainNode.id = :attackChainNodeId "
          + "and ie.team.id = :teamId "
          + "and ie.name = :expectationName "
          + "and ie.user is not null")
  List<AttackChainNodeExpectation>
      findAllByAttackChainNodeAndTeamAndExpectationNameAndUserIsNotNull(
          final String attackChainNodeId, final String teamId, final String expectationName);

  // -- RETRIEVE EXPECTATIONS FOR TEAM AND NOT FOR PLAYERS
  @Query(
      value =
          "select i from AttackChainNodeExpectation i where i.attackChainNode.id = :attackChainNodeId and i.team.id = :teamId and i.user is null")
  List<AttackChainNodeExpectation> findAllByAttackChainNodeAndTeam(
      @Param("attackChainNodeId") @NotBlank final String attackChainNodeId,
      @Param("teamId") @NotBlank final String teamId);

  // -- INJECT EXPECTATION TECHNICAL --

  @Query(
      value =
          "SELECT i FROM AttackChainNodeExpectation i "
              + "WHERE i.attackChainNode.id = :attackChainNodeId "
              + "AND i.agent.id = :agentId "
              + "ORDER BY i.type, i.createdAt")
  List<AttackChainNodeExpectation> findAllByAttackChainNodeAndAgent(
      @Param("attackChainNodeId") @NotBlank String attackChainNodeId,
      @Param("agentId") @NotBlank String agentId);

  @Query(
      value =
          "SELECT i FROM AttackChainNodeExpectation i "
              + "WHERE i.attackChainNode.id = :attackChainNodeId "
              + "AND i.asset.id = :assetId "
              + "AND i.agent IS NULL "
              + "ORDER BY i.type, i.createdAt")
  List<AttackChainNodeExpectation> findAllByAttackChainNodeAndAsset(
      @Param("attackChainNodeId") @NotBlank String attackChainNodeId,
      @Param("assetId") @NotBlank String assetId);

  @Query(
      value =
          "SELECT i FROM AttackChainNodeExpectation i "
              + "WHERE i.attackChainNode.id = :attackChainNodeId "
              + "AND i.asset.id = :assetId "
              + "AND i.type = :expectationType "
              + "AND i.agent IS NOT NULL "
              + "ORDER BY i.type, i.createdAt")
  List<AttackChainNodeExpectation> findAllWithAgentsByAttackChainNodeAndAsset(
      @Param("attackChainNodeId") @NotBlank String attackChainNodeId,
      @Param("assetId") @NotBlank String assetId,
      @Param("expectationType") @NotBlank
          AttackChainNodeExpectation.EXPECTATION_TYPE expectationType);

  @Query(
      value =
          "SELECT i FROM AttackChainNodeExpectation i "
              + "WHERE i.attackChainNode.id = :attackChainNodeId "
              + "AND i.assetGroup.id = :assetGroupId "
              + "AND i.asset IS NULL "
              + "AND i.agent IS NULL ")
  List<AttackChainNodeExpectation> findAllByAttackChainNodeAndAssetGroup(
      @Param("attackChainNodeId") @NotBlank final String attackChainNodeId,
      @Param("assetGroupId") @NotBlank final String assetGroupId);

  @Query(
      value =
          "SELECT "
              + "i.node_expectation_id AS node_expectation_id, "
              + "i.node_id AS node_id, "
              + "i.run_id AS run_id, "
              + "i.team_id AS team_id, "
              + "i.agent_id AS agent_id, "
              + "i.asset_id AS asset_id, "
              + "i.asset_group_id AS asset_group_id, "
              + "i.node_expectation_type AS node_expectation_type, "
              + "i.user_id AS user_id, "
              + "i.node_expectation_score AS node_expectation_score, "
              + "i.node_expectation_results AS node_expectation_results, "
              + "i.node_expectation_expected_score AS node_expectation_expected_score, "
              + "i.node_expectation_group AS node_expectation_group "
              + "FROM attack_chain_node_expectations i "
              + "WHERE i.node_id IN (:attackChainNodeIds) "
              + "AND i.user_id is null "
              + "AND i.agent_id is null ;",
      nativeQuery = true)
  // We don't include expectations for players, only for the team, neither for agents, if applicable
  List<RawAttackChainNodeExpectation> rawForComputeGlobalByAttackChainNodeIds(
      @Param("attackChainNodeIds") Set<String> attackChainNodeIds);

  @Query(
      value =
          "SELECT "
              + "i.node_expectation_id AS node_expectation_id, "
              + "i.node_id AS node_id, "
              + "i.run_id AS run_id, "
              + "i.team_id AS team_id, "
              + "i.agent_id AS agent_id, "
              + "i.asset_id AS asset_id, "
              + "i.asset_group_id AS asset_group_id, "
              + "i.node_expectation_type AS node_expectation_type, "
              + "i.user_id AS user_id, "
              + "i.node_expectation_score AS node_expectation_score, "
              + "i.node_expectation_expected_score AS node_expectation_expected_score, "
              + "i.node_expectation_group AS node_expectation_group "
              + "FROM attack_chain_node_expectations i "
              + "WHERE i.run_id = ANY(:attackChainRunIds) "
              + "AND i.user_id is null "
              + "AND i.agent_id is null ;",
      nativeQuery = true)
  // We don't include expectations for players, only for the team, if applicable
  // Hibernate 6 does not expand a Collection IN parameter inside a native query (binds the whole
  // collection as a single bytea), so we use PostgreSQL `= ANY(:array)` with a String[] argument.
  List<RawAttackChainNodeExpectation> rawForComputeGlobalByAttackChainRunIds(
      @Param("attackChainRunIds") String[] attackChainRunIds);

  @Query(
      value =
          "select i from AttackChainNodeExpectation i where i.attackChainNode.id in :attackChainNodeIds and i.agent is null and i.user is null")
  List<AttackChainNodeExpectation> findAllForGlobalScoreByAttackChainNodes(
      @Param("attackChainNodeIds") Set<String> attackChainNodeIds);

  @Modifying
  @Query(
      value =
          """
                UPDATE attack_chain_node_expectations
                SET node_expectation_signatures =
                    COALESCE(node_expectation_signatures, '[]'::jsonb) ||
                    jsonb_build_array(jsonb_build_object('type', :sigType, 'value', :sigValue))
                WHERE node_id = :attackChainNodeId AND agent_id = :agentId
                """,
      nativeQuery = true)
  void insertSignature(
      @Param("sigType") String sigType,
      @Param("sigValue") String sigValue,
      @Param("attackChainNodeId") String attackChainNodeId,
      @Param("agentId") String agentId);

  // -- INDEXING --

  // Column aliases match the getter names on RawAttackChainNodeExpectation (Spring Data interface
  // projection): node_* → inject_*, run_id → exercise_id, attack_chain_id → scenario_id.
  @Query(
      value =
          """
    WITH inject_expectation_data AS (
      SELECT
      ie.node_expectation_id AS node_expectation_id,
      ie.node_expectation_name AS node_expectation_name,
      ie.node_expectation_description AS node_expectation_description,
      ie.node_expectation_type AS node_expectation_type,
      ie.node_expectation_results AS node_expectation_results,
      ie.node_expectation_score AS node_expectation_score,
      ie.node_expectation_expected_score AS node_expectation_expected_score,
      ie.node_expectation_expiration_time AS node_expiration_time,
      ie.node_expectation_group AS node_expectation_group,
      ie.node_expectation_created_at AS node_expectation_created_at,
      GREATEST(ie.node_expectation_updated_at, max(i.node_updated_at), max(ic.injector_contract_updated_at)) as node_expectation_updated_at,
      ie.run_id AS attack_chain_run_id,
      ie.node_id AS node_id,
      ie.user_id,
      ie.team_id,
      ie.agent_id,
      ie.asset_id,
      ie.asset_group_id,
      i.node_title as node_title,
      MAX(ins.tracking_sent_date) AS tracking_sent_date,
      array_agg(DISTINCT ap.attack_pattern_id) FILTER ( WHERE ap.attack_pattern_id IS NOT NULL ) AS attack_pattern_ids,
      coalesce(array_agg(DISTINCT p_d.domain_id) FILTER (WHERE p_d.domain_id IS NOT NULL ),array_agg(DISTINCT ic_d.domain_id) FILTER (WHERE ic_d.domain_id IS NOT NULL )) domain_ids,
      MAX(se.attack_chain_id) AS attack_chain_id,
      array_agg(DISTINCT c.collector_security_platform) FILTER ( WHERE c.collector_security_platform IS NOT NULL ) ||
      array_agg(DISTINCT a.asset_id) FILTER ( WHERE a.asset_id IS NOT NULL ) AS security_platform_ids
    FROM attack_chain_node_expectations ie
    LEFT JOIN attack_chain_runs ex ON ex.run_id = ie.run_id
    LEFT JOIN attack_chain_nodes i ON i.node_id = ie.node_id
    LEFT JOIN injects_statuses ins ON ins.status_inject = i.node_id
    LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = i.node_contract_id
    LEFT JOIN injectors_contracts_attack_patterns ic_ap ON ic_ap.injector_contract_id = ic.injector_contract_id
    LEFT JOIN attack_patterns ap ON ap.attack_pattern_id = ic_ap.attack_pattern_id
    LEFT JOIN injectors_contracts_domains ic_d ON ic_d.injector_contract_id = ic.injector_contract_id
    LEFT JOIN payloads_domains p_d ON p_d.payload_id = ic.injector_contract_payload
    LEFT JOIN users u ON u.user_id = ie.user_id
    LEFT JOIN teams t ON t.team_id = ie.team_id
    LEFT JOIN assets asset ON asset.asset_id = ie.asset_id
    LEFT JOIN asset_groups ag ON ag.asset_group_id = ie.asset_group_id
    LEFT JOIN attack_chains_runs se ON se.run_id = ie.run_id
    LEFT JOIN LATERAL jsonb_array_elements(ie.node_expectation_results::jsonb) AS r(elem) ON true
    LEFT JOIN collectors c ON r.elem->>'sourceId' = c.collector_id::text
    LEFT JOIN assets a ON r.elem->>'sourceId' = a.asset_id::text
    GROUP BY
      ie.node_expectation_id,
      ic.injector_contract_id,
      i.node_title
    )
    SELECT * FROM inject_expectation_data ied
    WHERE ied.node_expectation_updated_at > :from AND ied.agent_id IS NULL
    ORDER BY ied.node_expectation_updated_at ASC
    LIMIT 500
    """,
      nativeQuery = true)
  List<RawAttackChainNodeExpectation> findForIndexing(@Param("from") Instant from);
}
