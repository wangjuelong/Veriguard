package io.veriguard.database.specification;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.Team;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class TeamSpecification {

  private TeamSpecification() {}

  public static Specification<Team> fromIds(@NotNull final List<String> ids) {
    return (root, query, builder) -> root.get("id").in(ids);
  }

  public static Specification<Team> teamsAccessibleFromOrganizations(List<String> organizationIds) {
    return (root, query, builder) ->
        builder.or(
            builder.isNull(root.get("organization")),
            root.get("organization").get("id").in(organizationIds));
  }

  public static Specification<Team> contextual(final boolean contextual) {
    if (contextual) {
      return (root, query, builder) -> builder.isTrue(root.get("contextual"));
    }
    return (root, query, builder) -> builder.isFalse(root.get("contextual"));
  }

  public static Specification<Team> fromAttackChainRun(@NotBlank final String attackChainRunId) {
    return (root, query, cb) -> {
      Join<Team, AttackChainRun> attackChainRunsJoin = root.join("exercises", JoinType.LEFT);
      return cb.and(
          cb.isNotNull(attackChainRunsJoin.get("id")), cb.equal(attackChainRunsJoin.get("id"), attackChainRunId));
    };
  }

  public static Specification<Team> fromAttackChain(String attackChainId) {
    return (root, query, cb) -> {
      Join<Team, AttackChain> attackChainsJoin = root.join("scenarios", JoinType.LEFT);
      return cb.and(
          cb.isNotNull(attackChainsJoin.get("id")), cb.equal(attackChainsJoin.get("id"), attackChainId));
    };
  }

  public static Specification<Team> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
