package io.veriguard.database.repository;

import io.veriguard.database.model.Team;
import io.veriguard.database.raw.RawTeam;
import io.veriguard.utils.Constants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository
    extends JpaRepository<Team, String>, StatisticRepository, JpaSpecificationExecutor<Team> {

  @NotNull
  Optional<Team> findById(@NotNull String id);

  @NotNull
  Optional<Team> findByName(@NotNull final String name);

  @NotNull
  List<Team> findAllByNameIgnoreCase(@NotNull final String name);

  @Query(
      "SELECT team FROM Team team where lower(team.name) = lower(:name) and team.contextual = false")
  List<Team> findByNameIgnoreCaseAndNotContextual(@NotNull final String name);

  @Query(
      "select team from Team team where team.organization is null or team.organization.id in :organizationIds")
  List<Team> teamsAccessibleFromOrganizations(
      @Param("organizationIds") List<String> organizationIds);

  @Override
  @Query(
      "select count(distinct u) from User u "
          + "join u.teams as team "
          + "where u.id = :userId and u.createdAt > :creationDate")
  long userCount(String userId, Instant creationDate);

  @Override
  @Query("select count(distinct t) from Team t where t.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "SELECT team_id, team_name "
              + "FROM teams "
              + "WHERE team_id IN :ids ORDER BY team_name;",
      nativeQuery = true)
  List<RawTeam> rawTeamByIds(@Param("ids") List<String> ids);

  @Query(
      value =
          "SELECT DISTINCT t.team_id AS team_id, t.team_name AS team_name "
              + "FROM teams t "
              + "LEFT JOIN attack_chain_nodes_teams it ON t.team_id = it.team_id "
              + "WHERE t.team_id IN (:ids) OR it.node_id IN (:attackChainNodeIds) ;",
      nativeQuery = true)
  Set<RawTeam> rawByIdsOrAttackChainNodeIds(
      @Param("ids") Set<String> ids, @Param("attackChainNodeIds") Set<String> attackChainNodeIds);

  @Query(
      value =
          "SELECT teams.team_id, teams.team_name, teams.team_description, teams.team_created_at, teams.team_updated_at, teams.team_organization, "
              + "       team_contextual, "
              + "       coalesce(array_agg(DISTINCT teams_tags.tag_id) FILTER ( WHERE teams_tags.tag_id IS NOT NULL ), '{}') as team_tags, "
              + "       coalesce(array_agg(DISTINCT users_teams.user_id) FILTER ( WHERE users_teams.user_id IS NOT NULL ), '{}') as team_users "
              + "FROM teams "
              + "LEFT JOIN teams_tags ON teams_tags.team_id = teams.team_id "
              + "LEFT JOIN users_teams ON users_teams.team_id = teams.team_id "
              + "GROUP BY teams.team_id ;",
      nativeQuery = true)
  List<RawTeam> rawTeams();

  @Query(
      value =
          "SELECT teams.team_id, teams.team_name, teams.team_description, teams.team_created_at, teams.team_updated_at, teams.team_organization, "
              + "       team_contextual, "
              + "       coalesce(array_agg(DISTINCT teams_tags.tag_id) FILTER ( WHERE teams_tags.tag_id IS NOT NULL ), '{}') as team_tags, "
              + "       coalesce(array_agg(DISTINCT users_teams.user_id) FILTER ( WHERE users_teams.user_id IS NOT NULL ), '{}') as team_users, "
              + "       coalesce(array_agg(DISTINCT attack_chain_runs_teams.run_id) FILTER ( WHERE attack_chain_runs_teams.run_id IS NOT NULL ), '{}') as team_exercises, "
              + "       coalesce(array_agg(DISTINCT attack_chains_teams.attack_chain_id) FILTER ( WHERE attack_chains_teams.attack_chain_id IS NOT NULL ), '{}') as team_scenarios, "
              + "       coalesce(array_agg(DISTINCT attack_chain_node_expectations.node_expectation_id) FILTER ( WHERE attack_chain_node_expectations.node_expectation_id IS NOT NULL), '{}') as team_expectations, "
              + "       coalesce(array_agg(DISTINCT attack_chain_nodes.node_id) FILTER ( WHERE attack_chain_nodes.node_id IS NOT NULL), '{}') as team_exercise_injects, "
              + "       coalesce(array_agg(DISTINCT communications.communication_id) FILTER ( WHERE communications.communication_id IS NOT NULL), '{}') as team_communications "
              + "FROM teams "
              + "LEFT JOIN teams_tags ON teams_tags.team_id = teams.team_id "
              + "LEFT JOIN users_teams ON users_teams.team_id = teams.team_id "
              + "LEFT JOIN attack_chain_runs_teams ON attack_chain_runs_teams.team_id = teams.team_id "
              + "LEFT JOIN attack_chains_teams ON attack_chains_teams.team_id = teams.team_id "
              + "LEFT JOIN attack_chain_node_expectations ON attack_chain_node_expectations.team_id = teams.team_id "
              + "LEFT JOIN attack_chain_runs ON attack_chain_runs_teams.run_id = attack_chain_runs.run_id "
              + "LEFT JOIN attack_chain_runs_teams_users ON attack_chain_runs_teams_users.team_id = teams.team_id "
              + "LEFT JOIN attack_chain_nodes ON attack_chain_nodes.node_attack_chain_run_id = attack_chain_runs.run_id "
              + "LEFT JOIN communications ON communications.communication_inject = attack_chain_nodes.node_id "
              + "WHERE teams.team_organization IS NULL OR teams.team_organization IN :organizationIds "
              + "GROUP BY teams.team_id ;",
      nativeQuery = true)
  List<RawTeam> rawTeamsAccessibleFromOrganization(
      @Param("organizationIds") List<String> organizationIds);

  @NotNull
  @EntityGraph(value = "Team.tags", type = EntityGraph.EntityGraphType.LOAD)
  Page<Team> findAll(@NotNull Specification<Team> spec, @NotNull Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT i.node_attack_chain_run_id, t.team_id, t.team_name "
              + "FROM teams t "
              + "INNER JOIN attack_chain_nodes_teams it ON t.team_id = it.team_id "
              + "INNER JOIN attack_chain_nodes i ON it.node_id = i.node_id "
              + "WHERE i.node_attack_chain_run_id in (:attackChainRunIds)",
      nativeQuery = true)
  List<Object[]> teamsByAttackChainRunIds(
      @Param("attackChainRunIds") java.util.Collection<String> attackChainRunIds);

  @Query(
      value =
          "SELECT DISTINCT it.node_id, t.team_id, t.team_name "
              + "FROM teams t "
              + "INNER JOIN attack_chain_nodes_teams it ON t.team_id = it.team_id "
              + "WHERE it.node_id in :attackChainNodeIds",
      nativeQuery = true)
  List<Object[]> teamsByAttackChainNodeIds(Set<String> attackChainNodeIds);

  @Query(
      "SELECT t FROM AttackChainNode i"
          + " JOIN i.teams t"
          + " WHERE ("
          + "   :simulationOrAttackChainId is NULL AND i.attackChainRun.id is NULL AND i.attackChain.id IS NULL"
          + "   OR (i.attackChainRun.id = :simulationOrAttackChainId"
          + "   OR i.attackChain.id = :simulationOrAttackChainId)"
          + " ) AND (:name IS NULL OR lower(t.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<Team> findAllBySimulationOrAttackChainIdAndName(
      String simulationOrAttackChainId, String name);

  @Query(
      value =
          "SELECT DISTINCT t.team_id, t.team_name, t.team_description, t.team_created_at, t.team_updated_at, t.team_organization, t.team_contextual "
              + "FROM teams t "
              + "WHERE EXISTS (SELECT 1 FROM attack_chain_nodes_teams it WHERE it.team_id = t.team_id) "
              + "OR EXISTS (SELECT 1 FROM attack_chain_runs_teams et WHERE et.team_id = t.team_id) "
              + "OR EXISTS (SELECT 1 FROM attack_chains_teams st WHERE st.team_id = t.team_id);",
      nativeQuery = true)
  List<Team> findAllTeamsForAtomicTestingsSimulationsAndAttackChains();

  @Query(
      value =
          "SELECT t.team_id, t.team_name, t.team_updated_at, t.team_created_at "
              + "FROM teams t "
              + "WHERE t.team_updated_at > :from ORDER BY t.team_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawTeam> findForIndexing(@Param("from") Instant from);
}
