package io.veriguard.rest.attack_chain.utils;

import static io.veriguard.utils.CustomFilterUtils.computeMode;
import static java.util.Optional.ofNullable;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.Filters;
import io.veriguard.database.specification.AttackChainSpecification;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class AttackChainUtils {

  private AttackChainUtils() {}

  private static final String SCENARIO_RECURRENCE_FILTER = "scenario_recurrence";

  /** Manage filters that are not directly managed by the generic mechanics */
  public static UnaryOperator<Specification<AttackChain>> handleCustomFilter(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    // Existence of the filter
    Optional<Filters.Filter> attackChainRecurrenceFilterOpt =
        ofNullable(searchPaginationInput.getFilterGroup())
            .flatMap(f -> f.findByKey(SCENARIO_RECURRENCE_FILTER));

    if (attackChainRecurrenceFilterOpt.isPresent()) {
      // Purge filter
      searchPaginationInput.getFilterGroup().removeByKey(SCENARIO_RECURRENCE_FILTER);
      Specification<AttackChain> customSpecification = null;
      if (attackChainRecurrenceFilterOpt.get().getValues().contains("Scheduled")) {
        customSpecification = AttackChainSpecification.isRecurring();
      } else if (attackChainRecurrenceFilterOpt.get().getValues().contains("Not planned")) {
        customSpecification = AttackChainSpecification.noRecurring();
      }
      if (customSpecification != null) {
        return computeMode(searchPaginationInput, customSpecification);
      }
      return (Specification<AttackChain> specification) -> specification;
    } else {
      return (Specification<AttackChain> specification) -> specification;
    }
  }
}
