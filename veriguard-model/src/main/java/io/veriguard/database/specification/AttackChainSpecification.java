package io.veriguard.database.specification;

import io.veriguard.database.model.AttackChain;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Path;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

public class AttackChainSpecification {

  private AttackChainSpecification() {}

  public static Specification<AttackChain> isRecurring() {
    return (root, query, cb) -> cb.isNotNull(root.get("recurrence"));
  }

  public static Specification<AttackChain> noRecurring() {
    return (root, query, cb) -> cb.isNull(root.get("recurrence"));
  }

  public static Specification<AttackChain> recurrenceStartDateBefore(
      @NotNull final Instant startDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceStart")),
            cb.lessThanOrEqualTo(root.get("recurrenceStart"), startDate));
  }

  public static Specification<AttackChain> recurrenceSartDateAfter(
      @NotNull final Instant startDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceStart")),
            cb.greaterThanOrEqualTo(root.get("recurrenceStart"), startDate));
  }

  public static Specification<AttackChain> recurrenceStopDateAfter(
      @NotNull final Instant stopDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceEnd")),
            cb.greaterThanOrEqualTo(root.get("recurrenceEnd"), stopDate));
  }

  public static Specification<AttackChain> recurrenceStopDateBefore(
      @NotNull final Instant stopDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceEnd")),
            cb.lessThanOrEqualTo(root.get("recurrenceEnd"), stopDate));
  }

  public static Specification<AttackChain> findGrantedFor(String userId) {
    return (root, query, criteriaBuilder) -> {
      Path<Object> path = root.join("grants").join("group").join("users").get("id");
      return criteriaBuilder.equal(path, userId);
    };
  }

  public static Specification<AttackChain> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
