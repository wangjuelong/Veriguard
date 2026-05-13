package io.veriguard.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.monitoring.MonitoringJob;
import io.veriguard.database.model.monitoring.MonitoringRunHistory;
import io.veriguard.database.repository.MonitoringJobRepository;
import io.veriguard.monitoring.MonitoringTriggerService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoundaryMonitoringJobTest {

  @Mock private MonitoringJobRepository jobRepository;
  @Mock private MonitoringTriggerService triggerService;

  private BoundaryMonitoringJob job;

  @BeforeEach
  void setUp() {
    job = new BoundaryMonitoringJob(jobRepository, triggerService);
  }

  @Test
  void fires_jobs_whose_next_cron_time_has_passed() {
    // hourly cron, lastTriggered 2h ago → 应触发
    MonitoringJob hourly = newJob("0 0 * * * ?", Instant.now().minus(2, ChronoUnit.HOURS));
    when(jobRepository.findAllByEnabledTrue()).thenReturn(List.of(hourly));
    when(triggerService.trigger(any(MonitoringJob.class))).thenReturn(new MonitoringRunHistory());

    int fired = job.sweepAndTrigger();

    assertThat(fired).isEqualTo(1);
    verify(triggerService, times(1)).trigger(hourly);
  }

  @Test
  void skips_jobs_whose_cron_has_not_yet_arrived() {
    // hourly cron, lastTriggered 1 min ago → 下一次在 ~59min 之后，不该触发
    MonitoringJob recent = newJob("0 0 * * * ?", Instant.now().minus(1, ChronoUnit.MINUTES));
    when(jobRepository.findAllByEnabledTrue()).thenReturn(List.of(recent));

    int fired = job.sweepAndTrigger();

    assertThat(fired).isEqualTo(0);
    verify(triggerService, never()).trigger(any());
  }

  @Test
  void no_enabled_jobs_returns_zero() {
    when(jobRepository.findAllByEnabledTrue()).thenReturn(List.of());

    int fired = job.sweepAndTrigger();

    assertThat(fired).isEqualTo(0);
    verify(triggerService, never()).trigger(any());
  }

  @Test
  void invalid_cron_skips_job_without_blowing_up_others() {
    MonitoringJob bad = newJob("not-a-cron", Instant.now().minus(2, ChronoUnit.HOURS));
    MonitoringJob good = newJob("0 0 * * * ?", Instant.now().minus(2, ChronoUnit.HOURS));
    when(jobRepository.findAllByEnabledTrue()).thenReturn(List.of(bad, good));
    when(triggerService.trigger(any(MonitoringJob.class))).thenReturn(new MonitoringRunHistory());

    int fired = job.sweepAndTrigger();

    assertThat(fired).isEqualTo(1);
    verify(triggerService, times(1)).trigger(good);
    verify(triggerService, never()).trigger(bad);
  }

  @Test
  void should_fire_uses_created_at_when_last_triggered_is_null() {
    MonitoringJob fresh = new MonitoringJob();
    fresh.setId(UUID.randomUUID().toString());
    fresh.setName("fresh");
    fresh.setBaselineId("b1");
    fresh.setCronExpression("0 0 * * * ?");
    fresh.setEnabled(true);
    fresh.setCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS));
    fresh.setLastTriggeredAt(null);

    boolean shouldFire = BoundaryMonitoringJob.shouldFire(fresh, Instant.now());

    assertThat(shouldFire).isTrue();
  }

  private MonitoringJob newJob(String cron, Instant lastTriggered) {
    MonitoringJob j = new MonitoringJob();
    j.setId(UUID.randomUUID().toString());
    j.setName("job-" + cron);
    j.setBaselineId("baseline-x");
    j.setCronExpression(cron);
    j.setEnabled(true);
    j.setCreatedAt(Instant.now().minus(7, ChronoUnit.DAYS));
    j.setLastTriggeredAt(lastTriggered);
    return j;
  }
}
