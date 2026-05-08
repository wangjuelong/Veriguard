package io.veriguard.database.specification;

import static io.veriguard.database.model.AttackChainRunStatus.FINISHED;
import static io.veriguard.database.model.AttackChainRunStatus.SCHEDULED;

import io.veriguard.database.model.AttackChainRun;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Path;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class AttackChainRunSpecification {

  private AttackChainRunSpecification() {}

  public static Specification<AttackChainRun> recurringInstanceNotStarted() {
    return (root, query, cb) ->
        cb.and(cb.equal(root.get("status"), SCHEDULED), cb.isNotNull(root.get("attackChain")));
  }

  public static Specification<AttackChainRun> findGrantedFor(@NotNull final String userId) {
    return (root, query, cb) -> {
      Path<Object> path = root.join("grants").join("group").join("users").get("id");
      return cb.equal(path, userId);
    };
  }

  public static Specification<AttackChainRun> fromAttackChain(@NotNull final String attackChainId) {
    return (root, query, cb) -> cb.equal(root.get("attackChain").get("id"), attackChainId);
  }

  public static Specification<AttackChainRun> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }

  // -- BASIC PROPERTY --

  public static Specification<AttackChainRun> finished() {
    return (root, query, cb) -> cb.equal(root.get("status"), FINISHED);
  }

  public static Specification<AttackChainRun> closestBefore(@NotNull final Instant instant) {
    return (root, query, cb) -> {
      assert query != null;
      query.orderBy(cb.desc(root.get("end")));
      return cb.lessThan(root.get("end"), instant);
    };
  }
}
