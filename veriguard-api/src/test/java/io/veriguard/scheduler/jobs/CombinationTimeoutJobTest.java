package io.veriguard.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import java.time.Instant;
import java.util.List;
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
class CombinationTimeoutJobTest {

  @Mock private AttackCombinationRunRepository runRepository;
  @Mock private TransactionTemplate transactionTemplate;

  private CombinationTimeoutJob job;

  @BeforeEach
  void setUp() {
    job = new CombinationTimeoutJob(runRepository, transactionTemplate);
    // Make TransactionTemplate.executeWithoutResult invoke the callback synchronously
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
  void marks_expired_runs_as_failed() {
    AttackCombinationRun expiredRunning = newRun(AttackCombinationRunStatus.running, true);
    AttackCombinationRun expiredPaused = newRun(AttackCombinationRunStatus.paused, true);
    when(runRepository.findAllByStatusInAndExpiresAtBefore(any(), any()))
        .thenReturn(List.of(expiredRunning, expiredPaused));

    int count = job.runTimeoutSweep();

    assertThat(count).isEqualTo(2);
    ArgumentCaptor<AttackCombinationRun> captor = ArgumentCaptor.forClass(AttackCombinationRun.class);
    verify(runRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .allMatch(r -> r.getStatus() == AttackCombinationRunStatus.failed);
    assertThat(captor.getAllValues()).allMatch(r -> r.getCompletedAt() != null);
  }

  @Test
  void no_expired_runs_no_save() {
    when(runRepository.findAllByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of());

    int count = job.runTimeoutSweep();

    assertThat(count).isEqualTo(0);
    verify(runRepository, never()).save(any());
  }

  private AttackCombinationRun newRun(AttackCombinationRunStatus status, boolean expired) {
    AttackCombinationRun r = new AttackCombinationRun();
    r.setId(UUID.randomUUID().toString());
    r.setName("run-" + status);
    r.setStatus(status);
    r.setExpiresAt(expired ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(3600));
    return r;
  }
}
