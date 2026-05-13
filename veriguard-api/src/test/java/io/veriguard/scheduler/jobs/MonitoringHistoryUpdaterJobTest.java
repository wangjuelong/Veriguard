package io.veriguard.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.model.coverage.CoverageRunStatus;
import io.veriguard.database.model.monitoring.MonitoringRunHistory;
import io.veriguard.database.model.monitoring.MonitoringRunStatus;
import io.veriguard.database.repository.CoverageRunRepository;
import io.veriguard.database.repository.MonitoringRunHistoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonitoringHistoryUpdaterJobTest {

  @Mock private MonitoringRunHistoryRepository historyRepository;
  @Mock private CoverageRunRepository coverageRunRepository;
  @Mock private TransactionTemplate transactionTemplate;

  private MonitoringHistoryUpdaterJob job;

  @BeforeEach
  void setUp() {
    job = new MonitoringHistoryUpdaterJob(historyRepository, coverageRunRepository, transactionTemplate);
    doAnswer(
            inv -> {
              @SuppressWarnings("unchecked")
              Consumer<TransactionStatus> cb = (Consumer<TransactionStatus>) inv.getArgument(0);
              cb.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
  }

  @Test
  void backfills_counts_when_coverage_run_completed() {
    MonitoringRunHistory pending = newPending("h1", "run-1", Instant.now().minus(5, ChronoUnit.MINUTES));
    CoverageRun completed = newRun("run-1", CoverageRunStatus.completed, 4, 1, 0, 0);
    when(historyRepository.findAllByStatusAndScheduledAtBefore(any(), any()))
        .thenReturn(List.of(pending));
    when(historyRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(coverageRunRepository.findById("run-1")).thenReturn(Optional.of(completed));

    int updated = job.sweepAndUpdate();

    assertThat(updated).isEqualTo(1);
    ArgumentCaptor<MonitoringRunHistory> captor =
        ArgumentCaptor.forClass(MonitoringRunHistory.class);
    verify(historyRepository, times(1)).save(captor.capture());
    MonitoringRunHistory saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(MonitoringRunStatus.completed);
    assertThat(saved.getHitCount()).isEqualTo(4);
    assertThat(saved.getMissCount()).isEqualTo(1);
    assertThat(saved.getTotalCount()).isEqualTo(5);
  }

  @Test
  void marks_failed_when_coverage_run_failed() {
    MonitoringRunHistory pending = newPending("h2", "run-2", Instant.now().minus(5, ChronoUnit.MINUTES));
    CoverageRun failed = newRun("run-2", CoverageRunStatus.failed, 0, 0, 0, 0);
    failed.setErrorMessage("SOC unreachable");
    when(historyRepository.findAllByStatusAndScheduledAtBefore(any(), any()))
        .thenReturn(List.of(pending));
    when(historyRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(coverageRunRepository.findById("run-2")).thenReturn(Optional.of(failed));

    int updated = job.sweepAndUpdate();

    assertThat(updated).isEqualTo(1);
    ArgumentCaptor<MonitoringRunHistory> captor =
        ArgumentCaptor.forClass(MonitoringRunHistory.class);
    verify(historyRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(MonitoringRunStatus.failed);
    assertThat(captor.getValue().getErrorMessage()).isEqualTo("SOC unreachable");
  }

  @Test
  void skips_running_coverage_run_unless_aged_out() {
    MonitoringRunHistory pending = newPending("h3", "run-3", Instant.now().minus(5, ChronoUnit.MINUTES));
    CoverageRun running = newRun("run-3", CoverageRunStatus.running, 0, 0, 0, 0);
    when(historyRepository.findAllByStatusAndScheduledAtBefore(any(), any()))
        .thenReturn(List.of(pending));
    when(coverageRunRepository.findById("run-3")).thenReturn(Optional.of(running));

    int updated = job.sweepAndUpdate();

    assertThat(updated).isEqualTo(0);
  }

  @Test
  void marks_failed_when_aged_beyond_24h_and_still_running() {
    MonitoringRunHistory aged = newPending("h4", "run-4", Instant.now().minus(25, ChronoUnit.HOURS));
    CoverageRun running = newRun("run-4", CoverageRunStatus.running, 0, 0, 0, 0);
    when(historyRepository.findAllByStatusAndScheduledAtBefore(any(), any()))
        .thenReturn(List.of(aged));
    when(historyRepository.findById(aged.getId())).thenReturn(Optional.of(aged));
    when(coverageRunRepository.findById("run-4")).thenReturn(Optional.of(running));

    int updated = job.sweepAndUpdate();

    assertThat(updated).isEqualTo(1);
    ArgumentCaptor<MonitoringRunHistory> captor =
        ArgumentCaptor.forClass(MonitoringRunHistory.class);
    verify(historyRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(MonitoringRunStatus.failed);
    assertThat(captor.getValue().getErrorMessage()).contains("still running");
  }

  @Test
  void marks_failed_when_linked_coverage_run_was_deleted() {
    MonitoringRunHistory orphan = newPending("h5", "run-5", Instant.now().minus(5, ChronoUnit.MINUTES));
    when(historyRepository.findAllByStatusAndScheduledAtBefore(any(), any()))
        .thenReturn(List.of(orphan));
    when(historyRepository.findById(orphan.getId())).thenReturn(Optional.of(orphan));
    when(coverageRunRepository.findById("run-5")).thenReturn(Optional.empty());

    int updated = job.sweepAndUpdate();

    assertThat(updated).isEqualTo(1);
    ArgumentCaptor<MonitoringRunHistory> captor =
        ArgumentCaptor.forClass(MonitoringRunHistory.class);
    verify(historyRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(MonitoringRunStatus.failed);
    assertThat(captor.getValue().getErrorMessage()).contains("deleted");
  }

  private MonitoringRunHistory newPending(String id, String coverageRunId, Instant scheduledAt) {
    MonitoringRunHistory h = new MonitoringRunHistory();
    h.setId(id);
    h.setJobId("job-x");
    h.setCoverageRunId(coverageRunId);
    h.setScheduledAt(scheduledAt);
    h.setStatus(MonitoringRunStatus.triggered);
    return h;
  }

  private CoverageRun newRun(String id, CoverageRunStatus status, int hit, int miss, int timeout, int oos) {
    CoverageRun r = new CoverageRun();
    r.setId(id);
    r.setBaselineId("b-x");
    r.setStatus(status);
    r.setHitCount(hit);
    r.setMissCount(miss);
    r.setTimeoutCount(timeout);
    r.setOutOfScopeCount(oos);
    r.setTotalCells(hit + miss + timeout + oos);
    if (status.isTerminal()) {
      r.setFinishedAt(Instant.now());
    }
    return r;
  }

  /** 验证 forced fail 的常量约束（防误改）. */
  @Test
  void forced_fail_threshold_constant_is_24h() {
    assertThat(MonitoringHistoryUpdaterJob.FORCED_FAIL_HOURS).isEqualTo(24L);
  }
}
