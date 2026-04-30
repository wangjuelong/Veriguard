package io.veriguard.scheduler.jobs;

import static io.veriguard.database.model.Comcheck.COMCHECK_STATUS.EXPIRED;
import static io.veriguard.database.specification.ComcheckStatusSpecification.thatNeedExecution;
import static java.time.Instant.now;
import static java.util.stream.Collectors.groupingBy;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.model.Comcheck;
import io.veriguard.database.model.ComcheckStatus;
import io.veriguard.database.repository.ComcheckRepository;
import io.veriguard.database.repository.ComcheckStatusRepository;
import io.veriguard.service.MailingService;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * 二开 simplified comcheck dispatcher. The full Email injector pipeline (with attachments,
 * encryption, IMAP storage, multi-recipient context) is removed in Phase 11.5; this job now sends
 * a single comcheck-link email per recipient using {@link MailingService}.
 */
@Component
@DisallowConcurrentExecution
@Slf4j
@RequiredArgsConstructor
public class ComchecksExecutionJob implements Job {
  @Resource private VeriguardConfig veriguardConfig;
  private final ComcheckRepository comcheckRepository;
  private final ComcheckStatusRepository comcheckStatusRepository;
  private final MailingService mailingService;

  private String renderBody(String template, ComcheckStatus status) {
    String comCheckLink = veriguardConfig.getBaseUrl() + "/comcheck/" + status.getId();
    String anchor = "<a href='" + comCheckLink + "'>" + comCheckLink + "</a>";
    if (template == null) {
      return anchor;
    }
    // Replace common placeholder patterns with the comcheck link.
    return template.replace("${comcheck.url}", anchor).replace("{{ comcheck.url }}", anchor);
  }

  @Override
  @Transactional
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    Instant now = now();
    try {
      // 01. Manage expired comchecks.
      List<Comcheck> toExpired = comcheckRepository.thatMustBeExpired(now);
      comcheckRepository.saveAll(
          toExpired.stream().peek(comcheck -> comcheck.setState(EXPIRED)).toList());
      // 02. Send required statuses
      List<ComcheckStatus> allStatuses = comcheckStatusRepository.findAll(thatNeedExecution());
      Map<Comcheck, List<ComcheckStatus>> byComchecks =
          allStatuses.stream().collect(groupingBy(ComcheckStatus::getComcheck));
      byComchecks
          .entrySet()
          .forEach(
              entry -> {
                Comcheck comCheck = entry.getKey();
                List<ComcheckStatus> comcheckStatuses = entry.getValue();
                List<ComcheckStatus> notified = new ArrayList<>();
                for (ComcheckStatus status : comcheckStatuses) {
                  if (status.getUser() == null
                      || status.getUser().getEmail() == null
                      || status.getUser().getEmail().isBlank()) {
                    continue;
                  }
                  try {
                    String body = renderBody(comCheck.getMessage(), status);
                    mailingService.sendEmail(
                        comCheck.getSubject(), body, List.of(status.getUser()));
                    notified.add(status);
                  } catch (Exception e) {
                    log.warn(
                        "Failed to dispatch comcheck {} to {}: {}",
                        comCheck.getId(),
                        status.getUser().getEmail(),
                        e.getMessage());
                  }
                }
                if (!notified.isEmpty()) {
                  comcheckStatusRepository.saveAll(
                      notified.stream().peek(s -> s.setLastSent(now)).toList());
                }
              });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }
}
