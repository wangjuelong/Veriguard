package io.veriguard.utils.fixtures;

import io.veriguard.database.model.*;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExpectationUpdateInput;
import jakarta.annotation.Nullable;
import java.util.Map;

public class AttackChainNodeExpectationFixture {

  static Long EXPIRATION_TIME_SIX_HOURS = 21600L;
  static Long EXPIRATION_TIME_ONE_HOUR = 3600L;

  static Double EXPECTED_SCORE = 100.0;

  public static AttackChainNodeExpectation createExpectationWithTypeAndStatus(
      AttackChainNodeExpectation.EXPECTATION_TYPE type, AttackChainNodeExpectation.EXPECTATION_STATUS status) {
    AttackChainNodeExpectation expectation = new AttackChainNodeExpectation();
    expectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    expectation.setType(type);
    expectation.setExpectedScore(EXPECTED_SCORE);
    switch (status) {
      case SUCCESS -> expectation.setScore(EXPECTED_SCORE);
      case FAILED -> expectation.setScore(0.0);
      case PENDING -> expectation.setScore(null);
      case PARTIAL -> expectation.setScore(EXPECTED_SCORE / 2);
      default -> throw new IllegalArgumentException("Invalid status: " + status);
    }
    return expectation;
  }

  public static AttackChainNodeExpectation createPreventionAttackChainNodeExpectation(
      AttackChainNode attackChainNode, @Nullable Agent agent) {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setAttackChainNode(attackChainNode);
    attackChainNodeExpectation.setType(AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION);
    attackChainNodeExpectation.setAgent(agent);
    attackChainNodeExpectation.setExpectedScore(EXPECTED_SCORE);
    attackChainNodeExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return attackChainNodeExpectation;
  }

  public static AttackChainNodeExpectation createDetectionAttackChainNodeExpectation(
      AttackChainNode attackChainNode, @Nullable Agent agent) {
    AttackChainNodeExpectation attackChainNodeExpectation = createDefaultDetectionAttackChainNodeExpectation();
    attackChainNodeExpectation.setAttackChainNode(attackChainNode);
    attackChainNodeExpectation.setAgent(agent);
    return attackChainNodeExpectation;
  }

  public static AttackChainNodeExpectation createVulnerabilityAttackChainNodeExpectation(
      AttackChainNode attackChainNode, @Nullable Agent agent) {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setAttackChainNode(attackChainNode);
    attackChainNodeExpectation.setType(AttackChainNodeExpectation.EXPECTATION_TYPE.VULNERABILITY);
    attackChainNodeExpectation.setAgent(agent);
    attackChainNodeExpectation.setExpectedScore(EXPECTED_SCORE);
    attackChainNodeExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return attackChainNodeExpectation;
  }

  public static AttackChainNodeExpectation createManualAttackChainNodeExpectation(Team team, AttackChainNode attackChainNode) {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setAttackChainNode(attackChainNode);
    attackChainNodeExpectation.setType(AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL);
    attackChainNodeExpectation.setTeam(team);
    attackChainNodeExpectation.setExpectedScore(EXPECTED_SCORE);
    attackChainNodeExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    return attackChainNodeExpectation;
  }

  public static AttackChainNodeExpectation createArticleAttackChainNodeExpectation(Team team, AttackChainNode attackChainNode) {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setAttackChainNode(attackChainNode);
    attackChainNodeExpectation.setType(AttackChainNodeExpectation.EXPECTATION_TYPE.ARTICLE);
    attackChainNodeExpectation.setTeam(team);
    attackChainNodeExpectation.setExpectedScore(EXPECTED_SCORE);
    attackChainNodeExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    return attackChainNodeExpectation;
  }

  public static AttackChainNodeExpectation createManualAttackChainNodeExpectationWithAttackChainRun(
      Team team, AttackChainNode attackChainNode, AttackChainRun attackChainRun, String expectationName) {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setAttackChainNode(attackChainNode);
    attackChainNodeExpectation.setType(AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL);
    attackChainNodeExpectation.setTeam(team);
    attackChainNodeExpectation.setExpectedScore(EXPECTED_SCORE);
    attackChainNodeExpectation.setExpirationTime(EXPIRATION_TIME_ONE_HOUR);
    attackChainNodeExpectation.setAttackChainRun(attackChainRun);
    attackChainNodeExpectation.setName(expectationName);
    return attackChainNodeExpectation;
  }

  public static AttackChainNodeExpectation createDefaultDetectionAttackChainNodeExpectation() {
    AttackChainNodeExpectation attackChainNodeExpectation = new AttackChainNodeExpectation();
    attackChainNodeExpectation.setType(AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION);
    attackChainNodeExpectation.setExpectedScore(EXPECTED_SCORE);
    attackChainNodeExpectation.setExpirationTime(EXPIRATION_TIME_SIX_HOURS);
    return attackChainNodeExpectation;
  }

  public static AttackChainNodeExpectationUpdateInput getAttackChainNodeExpectationUpdateInput(
      String collectorId, String result, boolean isSuccess) {
    return AttackChainNodeExpectationUpdateInput.builder()
        .collectorId(collectorId)
        .result(result)
        .isSuccess(isSuccess)
        .metadata(Map.of("alertId", "alertId"))
        .build();
  }
}
