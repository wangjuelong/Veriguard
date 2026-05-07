package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainTeamUser;
import io.veriguard.database.model.AttackChainTeamUserId;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AttackChainTeamUserRepository
    extends JpaRepository<AttackChainTeamUser, AttackChainTeamUserId>,
        CrudRepository<AttackChainTeamUser, AttackChainTeamUserId>,
        JpaSpecificationExecutor<AttackChainTeamUser> {

  @NotNull
  Optional<AttackChainTeamUser> findById(@NotNull final AttackChainTeamUserId id);

  @Modifying
  @Query(
      value = "delete from scenarios_teams_users i where i.team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void deleteTeamFromAllReferences(@Param("teamIds") List<String> teamIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "delete from scenarios_teams_users "
              + "where scenario_id = :scenarioId and team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void deleteByAttackChainIdAndTeamIds(
      @Param("scenarioId") String attackChainId, @Param("teamIds") Collection<String> teamIds);

  boolean existsByAttackChainIdAndTeamIdAndUserId(
      String attackChainId, String teamId, String userId);
}
