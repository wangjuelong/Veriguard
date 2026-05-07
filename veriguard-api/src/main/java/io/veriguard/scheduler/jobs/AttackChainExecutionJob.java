package io.veriguard.scheduler.jobs;

import static io.veriguard.database.specification.AttackChainRunSpecification.recurringInstanceNotStarted;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.service.AttackChainRunFactory;
import io.veriguard.service.period.CronService;
import io.veriguard.service.scenario.AttackChainRecurrenceService;
import io.veriguard.service.scenario.AttackChainService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class AttackChainExecutionJob implements Job {

  private final AttackChainService attackChainService;
  private final AttackChainRecurrenceService attackChainRecurrenceService;
  private final AttackChainRunRepository attackChainRunRepository;
  private final AttackChainRunFactory attackChainToAttackChainRunService;
  private final CronService cronService;

  @Override
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    createAttackChainRunsFromAttackChains();
    cleanOutdatedRecurringAttackChain();
  }

  private void createAttackChainRunsFromAttackChains() {
    Instant now = Instant.now();
    // Find each attackChain with cron where now is between start and end date
    List<AttackChain> attackChains = this.attackChainService.recurringAttackChains(now);
    // Filter on valid cron attackChain -> Start date on cron is in 1 minute
    List<AttackChain> validAttackChains =
        attackChains.stream()
            .filter(
                attackChain -> {
                  Optional<Instant> nextOccurrence =
                      attackChainRecurrenceService.getNextExecutionTime(attackChain, now);
                  if (nextOccurrence.isEmpty()) {
                    return false;
                  }
                  Instant startDate = nextOccurrence.get().minus(1, ChronoUnit.MINUTES);
                  ZonedDateTime startDateMinute =
                      startDate.atZone(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MINUTES);
                  ZonedDateTime nowMinute =
                      now.atZone(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MINUTES);
                  return startDateMinute.equals(nowMinute);
                })
            .toList();
    // Check if a simulation link to this attackChain already exists
    // Retrieve simulations not started, link to a attackChain
    List<String> alreadyExistIds =
        this.attackChainRunRepository.findAll(recurringInstanceNotStarted()).stream()
            .map(AttackChainRun::getAttackChain)
            .map(AttackChain::getId)
            .toList();
    // Filter attackChains with this results
    validAttackChains.stream()
        .filter(attackChain -> !alreadyExistIds.contains(attackChain.getId()))
        // Create simulation with start date provided by cron
        .forEach(
            attackChain ->
                this.attackChainToAttackChainRunService.toAttackChainRun(
                    attackChain,
                    attackChainRecurrenceService.getNextExecutionTime(attackChain, now).orElse(now),
                    false));
  }

  private void cleanOutdatedRecurringAttackChain() {
    // Find each attackChain with cron is outdated:
    List<AttackChain> attackChains =
        this.attackChainService.potentialOutdatedRecurringAttackChain(Instant.now());
    List<AttackChain> validAttackChains =
        attackChains.stream().filter(this::isAttackChainOutdated).toList();

    // Remove recurring setup
    validAttackChains.forEach(
        s -> {
          s.setRecurrenceStart(null);
          s.setRecurrenceEnd(null);
          s.setRecurrence(null);
        });
    // Save it
    if (!validAttackChains.isEmpty()) this.attackChainService.updateAttackChains(validAttackChains);
  }

  private boolean isAttackChainOutdated(@NotNull final AttackChain attackChain) {
    if (attackChain.getRecurrenceEnd() == null) {
      return false;
    }
    // End date is passed
    if (attackChain.getRecurrenceEnd().isBefore(Instant.now())) {
      return true;
    }

    // There are no next execution -> example: end date is tomorrow at 1AM and execution cron is at
    // 6AM and it's 6PM
    Instant nextExecution =
        attackChainRecurrenceService
            .getNextExecutionTime(attackChain, Instant.now())
            .orElse(Instant.now());
    return nextExecution.isAfter(attackChain.getRecurrenceEnd());
  }
}
