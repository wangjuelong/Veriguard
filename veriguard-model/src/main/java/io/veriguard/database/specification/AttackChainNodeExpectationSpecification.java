package io.veriguard.database.specification;

import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class AttackChainNodeExpectationSpecification {

  public static Specification<AttackChainNodeExpectation> type(@NotBlank final EXPECTATION_TYPE type) {
    return (root, query, cb) -> cb.equal(root.get("type"), type);
  }

  public static Specification<AttackChainNodeExpectation> assetGroupIsNull() {
    return (root, query, cb) -> cb.isNull(root.get("assetGroup"));
  }

  public static Specification<AttackChainNodeExpectation> fromAssetGroup(
      @Nullable final String assetGroupId) {
    return (root, query, cb) -> cb.equal(root.get("assetGroup").get("id"), assetGroupId);
  }

  public static Specification<AttackChainNodeExpectation> from(@NotBlank final Instant date) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), date);
  }

  public static Specification<AttackChainNodeExpectation> agentNotNull() {
    return (root, query, cb) -> cb.isNotNull(root.get("agent"));
  }

  public static Specification<AttackChainNodeExpectation> assetNotNull() {
    return (root, query, cb) -> cb.isNotNull(root.get("asset"));
  }

  public static Specification<AttackChainNodeExpectation> fromAgents(
      @NotBlank final String attackChainNodeId, @NotEmpty final List<String> agentIds) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("inject").get("id"), attackChainNodeId),
            root.get("agent").get("id").in(agentIds));
  }

  public static Specification<AttackChainNodeExpectation> fromAssets(
      @NotBlank final String attackChainNodeId, @NotEmpty final List<String> assetIds) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("inject").get("id"), attackChainNodeId),
            cb.isNull(root.get("agent")),
            root.get("asset").get("id").in(assetIds));
  }
}
