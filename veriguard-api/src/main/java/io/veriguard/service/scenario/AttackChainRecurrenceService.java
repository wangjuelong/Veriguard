package io.veriguard.service.scenario;

import io.veriguard.database.model.AttackChain;
import io.veriguard.service.period.PeriodExpressionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@RequiredArgsConstructor
@Slf4j
@Service
public class AttackChainRecurrenceService {
  private final List<PeriodExpressionHandler> periodExpressionHandlers;

  public Optional<Instant> getNextExecutionTime(@NotNull AttackChain attackChain, Instant currentTime) {
    Optional<PeriodExpressionHandler> handler =
        periodExpressionHandlers.stream()
            .filter(h -> h.canHandleExpression(attackChain.getRecurrence()))
            .findFirst();

    if (handler.isEmpty()) {
      log.warn(
          "Attempted to compute a next occurrence for scenario {} but could not find a period expression handler for recurrence expression '{}'",
          attackChain.getId(),
          attackChain.getRecurrence());
      return Optional.empty();
    }
    return handler
        .get()
        .getNextOccurrence(attackChain.getRecurrenceStart(), currentTime, attackChain.getRecurrence());
  }
}
