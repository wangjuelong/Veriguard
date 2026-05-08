package io.veriguard.utils.fixtures;

import static io.veriguard.database.model.AttackChain.SEVERITY.critical;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.Team;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AttackChainFixture {

  public static AttackChain getAttackChain() {
    return getAttackChain(null, null);
  }

  public static AttackChain getAttackChainWithRecurrence(String cronExpression) {
    AttackChain attackChain = getAttackChain(null, null);
    attackChain.setRecurrence(cronExpression);
    return attackChain;
  }

  public static AttackChain getScheduledAttackChain() {
    AttackChain attackChain = getAttackChain(null, null);
    attackChain.setRecurrenceStart(Instant.now().plus(1, ChronoUnit.DAYS));
    return attackChain;
  }

  public static AttackChain getAttackChain(
      List<Team> attackChainTeams, Set<AttackChainNode> chainNodes) {
    AttackChain attackChain = new AttackChain();
    attackChain.setName("Crisis simulation");
    attackChain.setDescription("A crisis simulation for my enterprise");
    attackChain.setSubtitle("A crisis simulation");
    attackChain.setFrom("simulation@mail.fr");
    if (attackChainTeams != null) {
      attackChain.setTeams(attackChainTeams);
    }
    if (chainNodes != null) {
      attackChain.setAttackChainNodes(chainNodes);
    }
    attackChain.setAttackChainRuns(new ArrayList<>());
    return attackChain;
  }

  public static AttackChain createDefaultCrisisAttackChain() {
    AttackChain attackChain = new AttackChain();
    attackChain.setName("Crisis scenario");
    attackChain.setDescription("A crisis scenario for my enterprise");
    attackChain.setSubtitle("A crisis scenario");
    attackChain.setFrom("scenario@mail.fr");
    attackChain.setCategory("crisis-communication");
    attackChain.setAttackChainRuns(new ArrayList<>());
    return attackChain;
  }

  public static AttackChain createDefaultIncidentResponseAttackChain() {
    AttackChain attackChain = new AttackChain();
    attackChain.setName("Incident response scenario");
    attackChain.setDescription("An incident response scenario for my enterprise");
    attackChain.setSubtitle("An incident response scenario");
    attackChain.setFrom("scenario@mail.fr");
    attackChain.setCategory("incident-response");
    attackChain.setSeverity(critical);
    attackChain.setAttackChainRuns(new ArrayList<>());
    return attackChain;
  }
}
