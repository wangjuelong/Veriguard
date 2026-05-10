package io.veriguard.utils.fixtures;

import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import java.time.Instant;
import java.util.Set;

public class RawAttackChainNodeExpectationFixture {

  private record TestableRawAttackChainNodeExpectation(
      String attackChainNodeExpectationId,
      String attackChainNodeExpectationName,
      String attackChainNodeTitle,
      String attackChainNodeExpectationDescription,
      String expectationType,
      String expectationResults,
      Double expectationScore,
      Double expectationExpectedScore,
      Long attackChainNodeExpirationTime,
      Boolean expectationGroup,
      Instant createdAt,
      Instant updatedAt,
      String attackChainRunId,
      String attackChainNodeId,
      String userId,
      String teamId,
      String agentId,
      String assetId,
      String assetGroupId,
      Set<String> attackPatternIds,
      String attackChainId,
      Set<String> securityPlatformIds,
      Set<String> domainIds,
      Instant trackingSentDate)
      implements RawAttackChainNodeExpectation {

    @Override
    public String getNode_expectation_id() {
      return attackChainNodeExpectationId;
    }

    @Override
    public String getNode_expectation_name() {
      return attackChainNodeExpectationName;
    }

    @Override
    public String getNode_title() {
      return attackChainNodeExpectationName;
    }

    @Override
    public String getNode_expectation_description() {
      return attackChainNodeExpectationDescription;
    }

    @Override
    public String getNode_expectation_type() {
      return expectationType;
    }

    @Override
    public String getNode_expectation_results() {
      return expectationResults;
    }

    @Override
    public Double getNode_expectation_score() {
      return expectationScore;
    }

    @Override
    public Double getNode_expectation_expected_score() {
      return expectationExpectedScore;
    }

    @Override
    public Long getNode_expiration_time() {
      return attackChainNodeExpirationTime;
    }

    @Override
    public Boolean getNode_expectation_group() {
      return expectationGroup;
    }

    @Override
    public Instant getNode_expectation_created_at() {
      return createdAt;
    }

    @Override
    public Instant getNode_expectation_updated_at() {
      return updatedAt;
    }

    @Override
    public String getAttack_chain_run_id() {
      return attackChainRunId;
    }

    @Override
    public String getNode_id() {
      return attackChainNodeId;
    }

    @Override
    public String getUser_id() {
      return userId;
    }

    @Override
    public String getTeam_id() {
      return teamId;
    }

    @Override
    public String getAgent_id() {
      return "";
    }

    @Override
    public String getAsset_id() {
      return assetId;
    }

    @Override
    public String getAsset_group_id() {
      return assetGroupId;
    }

    @Override
    public Set<String> getAttack_pattern_ids() {
      return attackPatternIds;
    }

    @Override
    public Set<String> getSecurity_platform_ids() {
      return securityPlatformIds;
    }

    @Override
    public Set<String> getDomain_ids() {
      return domainIds;
    }

    @Override
    public String getAttack_chain_id() {
      return attackChainId;
    }

    @Override
    public Instant getTracking_sent_date() {
      return trackingSentDate;
    }
  }

  public static RawAttackChainNodeExpectation createDefaultAttackChainNodeExpectation(
      String expectationType, Double expectationScore, Double expectationExpectedScore) {
    return new TestableRawAttackChainNodeExpectation(
        null,
        null,
        null,
        null,
        expectationType,
        null,
        expectationScore,
        expectationExpectedScore,
        null,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
