package io.veriguard.service;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainRunTeamUser;
import io.veriguard.database.model.Team;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.AttackChainRunTeamUserRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttackChainRunTeamUserService {

  private final AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;

  // -- CRUD --

  public AttackChainRunTeamUser createAttackChainRunTeamUser(
      @NotNull AttackChainRun attackChainRun, @NotNull Team team, @NotNull User user) {
    AttackChainRunTeamUser attackChainRunTeamUser = new AttackChainRunTeamUser();
    attackChainRunTeamUser.setAttackChainRun(attackChainRun);
    attackChainRunTeamUser.setTeam(team);
    attackChainRunTeamUser.setUser(user);
    return attackChainRunTeamUserRepository.save(attackChainRunTeamUser);
  }

  public void duplicateTeamUsers(
      @NotNull AttackChainRun target,
      @NotNull List<AttackChainRunTeamUser> sourceTeamUsers,
      @NotNull Map<String, Team> contextualTeams) {
    List<AttackChainRunTeamUser> newTeamUsers =
        sourceTeamUsers.stream()
            .map(
                sourceTeamUser -> {
                  Team resolvedTeam =
                      contextualTeams.getOrDefault(
                          sourceTeamUser.getTeam().getId(), sourceTeamUser.getTeam());
                  AttackChainRunTeamUser attackChainRunTeamUser = new AttackChainRunTeamUser();
                  attackChainRunTeamUser.setAttackChainRun(target);
                  attackChainRunTeamUser.setTeam(resolvedTeam);
                  attackChainRunTeamUser.setUser(sourceTeamUser.getUser());
                  return attackChainRunTeamUser;
                })
            .toList();

    attackChainRunTeamUserRepository.saveAll(newTeamUsers);
  }
}
