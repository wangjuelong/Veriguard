package io.veriguard.utils.fixtures;

import static java.time.temporal.ChronoUnit.MINUTES;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainRunStatus;
import io.veriguard.database.model.Team;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AttackChainRunFixture {

  public static final String EXERCISE_NAME = "Exercise test";

  public static AttackChainRun getAttackChainRun() {
    return getAttackChainRun(null);
  }

  public static AttackChainRun getAttackChainRun(List<Team> attackChainRunTeams) {
    AttackChainRun attackChainRun = new AttackChainRun();
    attackChainRun.setName(EXERCISE_NAME);
    if (attackChainRunTeams != null) {
      attackChainRun.setTeams(attackChainRunTeams);
    }
    return attackChainRun;
  }

  public static AttackChainRun createDefaultAttackChainRun() {
    AttackChainRun attackChainRun = createDefaultAttackChainRunWithDefaultName();
    attackChainRun.setDescription("A default test exercise");
    attackChainRun.setSubtitle("Default test exercise");
    attackChainRun.setFrom("default_exercise@mail.fr");
    attackChainRun.setCategory("crisis-communication");
    return attackChainRun;
  }

  public static AttackChainRun createDefaultCrisisAttackChainRun() {
    AttackChainRun attackChainRun = createDefaultAttackChainRunWithName("Crisis exercise");
    attackChainRun.setDescription("A crisis exercise for my enterprise");
    attackChainRun.setSubtitle("A crisis exercise");
    attackChainRun.setFrom("exercise@mail.fr");
    attackChainRun.setCategory("crisis-communication");
    return attackChainRun;
  }

  public static AttackChainRun createDefaultIncidentResponseAttackChainRun() {
    return createDefaultIncidentResponseAttackChainRun(Instant.now());
  }

  public static AttackChainRun createDefaultIncidentResponseAttackChainRun(Instant startTime) {
    AttackChainRun attackChainRun =
        createDefaultAttackChainRunWithName("Incident response exercise");
    attackChainRun.setDescription("An incident response exercise for my enterprise");
    attackChainRun.setSubtitle("An incident response exercise");
    attackChainRun.setFrom("exercise@mail.fr");
    attackChainRun.setCategory("incident-response");
    attackChainRun.setStatus(AttackChainRunStatus.SCHEDULED);
    attackChainRun.setStart(startTime);
    return attackChainRun;
  }

  public static AttackChainRun createDefaultAttackAttackChainRun(Instant startTime) {
    AttackChainRun attackChainRun =
        createDefaultAttackChainRunWithName("Draft incident response exercise");
    attackChainRun.setDescription("An incident response exercise for my enterprise");
    attackChainRun.setSubtitle("An incident response exercise");
    attackChainRun.setFrom("exercise@mail.fr");
    attackChainRun.setCategory("attack-scenario");
    attackChainRun.setMainFocus("incident-response");
    attackChainRun.setStatus(AttackChainRunStatus.SCHEDULED);
    attackChainRun.setStart(startTime);
    return attackChainRun;
  }

  public static AttackChainRun createRunningAttackAttackChainRun() {
    return createRunningAttackAttackChainRun(Instant.now());
  }

  public static AttackChainRun createRunningAttackAttackChainRun(Instant startTime) {
    AttackChainRun attackChainRun =
        createDefaultAttackChainRunWithName("Draft incident response exercise");
    attackChainRun.setDescription("An incident response exercise for my enterprise");
    attackChainRun.setSubtitle("An incident response exercise");
    attackChainRun.setFrom("exercise@mail.fr");
    attackChainRun.setCategory("attack-scenario");
    attackChainRun.setMainFocus("incident-response");
    attackChainRun.setStatus(AttackChainRunStatus.RUNNING);
    attackChainRun.setStart(startTime);
    return attackChainRun;
  }

  public static AttackChainRun createCanceledAttackAttackChainRun(Instant startTime) {
    AttackChainRun attackChainRun =
        createDefaultAttackChainRunWithName("Draft incident response exercise");
    attackChainRun.setDescription("An incident response exercise for my enterprise");
    attackChainRun.setSubtitle("An incident response exercise");
    attackChainRun.setFrom("exercise@mail.fr");
    attackChainRun.setCategory("attack-scenario");
    attackChainRun.setMainFocus("incident-response");
    attackChainRun.setStatus(AttackChainRunStatus.CANCELED);
    attackChainRun.setStart(startTime);
    return attackChainRun;
  }

  public static AttackChainRun createFinishedAttackAttackChainRun() {
    return createFinishedAttackAttackChainRun(Instant.now());
  }

  public static AttackChainRun createFinishedAttackAttackChainRun(Instant startTime) {
    AttackChainRun attackChainRun =
        createDefaultAttackChainRunWithName("Draft incident response exercise");
    attackChainRun.setDescription("An incident response exercise for my enterprise");
    attackChainRun.setSubtitle("An incident response exercise");
    attackChainRun.setFrom("exercise@mail.fr");
    attackChainRun.setCategory("attack-scenario");
    attackChainRun.setMainFocus("incident-response");
    attackChainRun.setStatus(AttackChainRunStatus.FINISHED);
    attackChainRun.setStart(startTime);
    return attackChainRun;
  }

  public static AttackChainRun createPausedAttackAttackChainRun(Instant startTime) {
    AttackChainRun attackChainRun =
        createDefaultAttackChainRunWithName("Draft incident response exercise");
    attackChainRun.setCurrentPause(startTime.truncatedTo(MINUTES).minus(1, MINUTES));
    attackChainRun.setDescription("An incident response exercise for my enterprise");
    attackChainRun.setSubtitle("An incident response exercise");
    attackChainRun.setFrom("exercise@mail.fr");
    attackChainRun.setCategory("attack-scenario");
    attackChainRun.setMainFocus("incident-response");
    attackChainRun.setStatus(AttackChainRunStatus.PAUSED);
    attackChainRun.setStart(startTime);
    return attackChainRun;
  }

  private static AttackChainRun createDefaultAttackChainRunWithDefaultName() {
    return createDefaultAttackChainRunWithName(null);
  }

  private static AttackChainRun createDefaultAttackChainRunWithName(String name) {
    String new_name = name == null ? "exercise-%s".formatted(UUID.randomUUID()) : name;
    AttackChainRun attackChainRun = new AttackChainRun();
    attackChainRun.setName(new_name);
    return attackChainRun;
  }
}
