package io.veriguard.utils.fixtures;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainRunTeamUser;
import io.veriguard.database.model.Team;
import io.veriguard.database.model.User;

public class AttackChainRunTeamUserFixture {

  public static AttackChainRunTeamUser createAttackChainRunTeamUser(AttackChainRun attackChainRun, Team team, User user) {
    AttackChainRunTeamUser attackChainRunTeamUser = new AttackChainRunTeamUser();
    attackChainRunTeamUser.setAttackChainRun(attackChainRun);
    attackChainRunTeamUser.setTeam(team);
    attackChainRunTeamUser.setUser(user);
    return attackChainRunTeamUser;
  }
}
