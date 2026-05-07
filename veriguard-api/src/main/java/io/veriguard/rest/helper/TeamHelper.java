package io.veriguard.rest.helper;

import io.veriguard.database.model.*;
import io.veriguard.database.raw.*;
import io.veriguard.database.repository.AttackChainNodeRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TeamHelper {
  public static List<TeamSimple> rawAllTeamToSimplerAllTeam(List<RawTeam> teams) {
    // Then, for all the raw teams, we will create a simpler team object and then send it back to
    // the front
    return teams.stream()
        .map(
            rawTeam -> {
              // We create the simpler team object using the raw one
              TeamSimple teamSimple = new TeamSimple(rawTeam);

              return teamSimple;
            })
        .collect(Collectors.toList());
  }

  private static Set<String> getAttackChainNodeTeamsIds(
      final String teamId,
      Set<String> attackChainNodeIds,
      final AttackChainNodeRepository attackChainNodeRepository) {
    Set<RawAttackChainNode> rawAttackChainNodeTeams =
        attackChainNodeRepository.findRawAttackChainNodeTeams(attackChainNodeIds, teamId);
    return rawAttackChainNodeTeams.stream()
        .map(RawAttackChainNode::getInject_id)
        .collect(Collectors.toSet());
  }
}
