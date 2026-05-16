package io.veriguard.rest.security_validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.VeriguardSandboxTask;
import io.veriguard.database.repository.VeriguardSandboxRepository;
import io.veriguard.database.repository.VeriguardSandboxTaskRepository;
import io.veriguard.integration.sandbox.SandboxDriver;
import io.veriguard.integration.sandbox.SandboxDriverRegistry;
import io.veriguard.integration.sandbox.SandboxIntegrationException;
import io.veriguard.integration.sandbox.SandboxIntegrationException.ReasonCode;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SandboxTaskServiceTest {

  @Mock private VeriguardSandboxTaskRepository taskRepository;
  @Mock private VeriguardSandboxRepository sandboxRepository;
  @Mock private SandboxDriverRegistry driverRegistry;
  @Mock private SandboxDriver driver;

  private SandboxTaskService service;
  private final Instant fixedInstant = Instant.parse("2026-05-16T08:30:00Z");

  @BeforeEach
  void setUp() {
    // lenient: validation-rejection tests + pure helper tests never call the driver, so strict
    // mode would flag this otherwise-shared stubbing as Unnecessary.
    lenient().when(driverRegistry.driver()).thenReturn(driver);
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    service = new SandboxTaskService(taskRepository, sandboxRepository, driverRegistry, fixedClock);
  }

  @Test
  void submit_happy_path_hashes_content_and_persists_queued_row() throws Exception {
    byte[] sample = "hello world".getBytes();
    when(driver.submitSample(any())).thenReturn(new SubmissionResult(4242L));
    when(taskRepository.saveAndFlush(any(VeriguardSandboxTask.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SandboxTaskSubmitInput input =
        new SandboxTaskSubmitInput(null, "RANSOMWARE", "sample.exe", sample, "win10x64", 120);
    VeriguardSandboxTask saved = service.submit(input);

    ArgumentCaptor<SampleSubmissionRequest> driverArg =
        ArgumentCaptor.forClass(SampleSubmissionRequest.class);
    verify(driver).submitSample(driverArg.capture());
    assertThat(driverArg.getValue().sampleSha256())
        .isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    assertThat(driverArg.getValue().content()).isEqualTo(sample);
    assertThat(driverArg.getValue().originalFilename()).isEqualTo("sample.exe");
    assertThat(driverArg.getValue().targetMachineName()).isEqualTo("win10x64");
    assertThat(driverArg.getValue().timeoutSeconds()).isEqualTo(120);

    assertThat(saved.getStatus()).isEqualTo(VeriguardSandboxTask.Status.QUEUED);
    assertThat(saved.getCapeTaskId()).isEqualTo(4242L);
    assertThat(saved.getSampleSha256())
        .isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    assertThat(saved.getSampleFilename()).isEqualTo("sample.exe");
    assertThat(saved.getSampleSizeBytes()).isEqualTo((long) sample.length);
    assertThat(saved.getSampleType()).isEqualTo("RANSOMWARE");
    assertThat(saved.getTargetMachine()).isEqualTo("win10x64");
    assertThat(saved.getTimeoutSeconds()).isEqualTo(120);
    assertThat(saved.getSubmittedAt()).isEqualTo(fixedInstant);
  }

  @Test
  void submit_empty_sample_rejected_without_driver_call() {
    SandboxTaskSubmitInput input =
        new SandboxTaskSubmitInput(null, null, "f.bin", new byte[0], null, null);
    assertThatThrownBy(() -> service.submit(input))
        .isInstanceOfSatisfying(
            InputValidationException.class,
            ex -> assertThat(ex.getField()).isEqualTo("sandbox_task_sample_empty"));
    verify(driver, never()).submitSample(any());
    verify(taskRepository, never()).saveAndFlush(any());
  }

  @Test
  void submit_blank_filename_rejected() {
    SandboxTaskSubmitInput input =
        new SandboxTaskSubmitInput(null, null, " ", new byte[] {1, 2}, null, null);
    assertThatThrownBy(() -> service.submit(input))
        .isInstanceOfSatisfying(
            InputValidationException.class,
            ex -> assertThat(ex.getField()).isEqualTo("sandbox_task_filename_blank"));
  }

  @Test
  void submit_non_positive_timeout_rejected() {
    SandboxTaskSubmitInput input =
        new SandboxTaskSubmitInput(null, null, "x", new byte[] {1}, null, 0);
    assertThatThrownBy(() -> service.submit(input))
        .isInstanceOfSatisfying(
            InputValidationException.class,
            ex -> assertThat(ex.getField()).isEqualTo("sandbox_task_timeout_invalid"));
  }

  @Test
  void submit_unknown_sandbox_id_rejected() {
    when(sandboxRepository.existsById("missing")).thenReturn(false);
    SandboxTaskSubmitInput input =
        new SandboxTaskSubmitInput("missing", null, "x", new byte[] {1}, null, null);
    assertThatThrownBy(() -> service.submit(input))
        .isInstanceOfSatisfying(
            InputValidationException.class,
            ex -> assertThat(ex.getField()).isEqualTo("sandbox_task_sandbox_not_found"));
    verify(driver, never()).submitSample(any());
  }

  @Test
  void submit_existing_sandbox_id_passes_through() throws Exception {
    when(sandboxRepository.existsById("preset-1")).thenReturn(true);
    when(driver.submitSample(any())).thenReturn(new SubmissionResult(7L));
    when(taskRepository.saveAndFlush(any(VeriguardSandboxTask.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    SandboxTaskSubmitInput input =
        new SandboxTaskSubmitInput("preset-1", null, "x", new byte[] {1}, null, null);
    VeriguardSandboxTask saved = service.submit(input);
    assertThat(saved.getSandboxId()).isEqualTo("preset-1");
  }

  @Test
  void submit_driver_failure_propagates_and_persists_nothing() {
    when(driver.submitSample(any()))
        .thenThrow(new SandboxIntegrationException(ReasonCode.CONNECTION_FAILED, "CAPE down"));
    SandboxTaskSubmitInput input =
        new SandboxTaskSubmitInput(null, null, "x", new byte[] {1}, null, null);
    assertThatThrownBy(() -> service.submit(input)).isInstanceOf(SandboxIntegrationException.class);
    verify(taskRepository, never()).saveAndFlush(any());
  }

  @Test
  void refresh_unknown_task_throws_element_not_found() {
    when(taskRepository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.refresh("missing"))
        .isInstanceOf(ElementNotFoundException.class);
  }

  @Test
  void refresh_running_task_to_completed_records_completed_at() {
    VeriguardSandboxTask task = newTask(VeriguardSandboxTask.Status.RUNNING, 99L);
    when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
    when(taskRepository.saveAndFlush(task)).thenReturn(task);
    when(driver.fetchTaskStatus(99L))
        .thenReturn(new SandboxTaskStatus(SandboxTaskStatus.Status.COMPLETED, "reported", null));

    VeriguardSandboxTask refreshed = service.refresh("t1");
    assertThat(refreshed.getStatus()).isEqualTo(VeriguardSandboxTask.Status.COMPLETED);
    assertThat(refreshed.getRawStatus()).isEqualTo("reported");
    assertThat(refreshed.getCompletedAt()).isEqualTo(fixedInstant);
    assertThat(refreshed.getLastPolledAt()).isEqualTo(fixedInstant);
  }

  @Test
  void pollAllActive_empty_set_skips_driver() {
    when(taskRepository.findByStatusInOrderByCreatedAtAsc(SandboxTaskService.ACTIVE_STATUSES))
        .thenReturn(List.of());
    int n = service.pollAllActive();
    assertThat(n).isZero();
    verify(driver, never()).fetchTaskStatus(any(Long.class));
  }

  @Test
  void pollAllActive_transitions_each_active_row() {
    VeriguardSandboxTask t1 = newTask(VeriguardSandboxTask.Status.QUEUED, 1L);
    t1.setId("t1");
    VeriguardSandboxTask t2 = newTask(VeriguardSandboxTask.Status.RUNNING, 2L);
    t2.setId("t2");
    when(taskRepository.findByStatusInOrderByCreatedAtAsc(SandboxTaskService.ACTIVE_STATUSES))
        .thenReturn(List.of(t1, t2));
    when(driver.fetchTaskStatus(1L))
        .thenReturn(new SandboxTaskStatus(SandboxTaskStatus.Status.RUNNING, "running", null));
    when(driver.fetchTaskStatus(2L))
        .thenReturn(new SandboxTaskStatus(SandboxTaskStatus.Status.COMPLETED, "reported", null));

    int transitioned = service.pollAllActive();
    assertThat(transitioned).isEqualTo(2);
    assertThat(t1.getStatus()).isEqualTo(VeriguardSandboxTask.Status.RUNNING);
    assertThat(t2.getStatus()).isEqualTo(VeriguardSandboxTask.Status.COMPLETED);
    assertThat(t2.getCompletedAt()).isEqualTo(fixedInstant);
    verify(taskRepository, times(2)).save(any(VeriguardSandboxTask.class));
  }

  @Test
  void pollAllActive_driver_failure_marks_row_failed_and_continues_batch() {
    VeriguardSandboxTask t1 = newTask(VeriguardSandboxTask.Status.RUNNING, 1L);
    t1.setId("t1");
    VeriguardSandboxTask t2 = newTask(VeriguardSandboxTask.Status.RUNNING, 2L);
    t2.setId("t2");
    when(taskRepository.findByStatusInOrderByCreatedAtAsc(SandboxTaskService.ACTIVE_STATUSES))
        .thenReturn(List.of(t1, t2));
    when(driver.fetchTaskStatus(1L))
        .thenThrow(new SandboxIntegrationException(ReasonCode.TIMEOUT, "slow"));
    when(driver.fetchTaskStatus(2L))
        .thenReturn(new SandboxTaskStatus(SandboxTaskStatus.Status.RUNNING, "running", null));

    int transitioned = service.pollAllActive();
    assertThat(transitioned).isEqualTo(1);
    assertThat(t1.getStatus()).isEqualTo(VeriguardSandboxTask.Status.FAILED);
    assertThat(t1.getErrorMessage()).contains("TIMEOUT").contains("slow");
    assertThat(t2.getStatus()).isEqualTo(VeriguardSandboxTask.Status.RUNNING);
  }

  @Test
  void pollOne_missing_cape_task_id_marks_failed_without_driver_call() {
    VeriguardSandboxTask task = newTask(VeriguardSandboxTask.Status.QUEUED, null);
    task.setId("orphan");
    when(taskRepository.findById("orphan")).thenReturn(Optional.of(task));
    when(taskRepository.saveAndFlush(task)).thenReturn(task);

    VeriguardSandboxTask refreshed = service.refresh("orphan");
    assertThat(refreshed.getStatus()).isEqualTo(VeriguardSandboxTask.Status.FAILED);
    assertThat(refreshed.getErrorMessage()).contains("Missing capeTaskId");
    verify(driver, never()).fetchTaskStatus(any(Long.class));
  }

  @Test
  void status_mapping_covers_all_driver_states() {
    assertThat(SandboxTaskService.toEntityStatus(SandboxTaskStatus.Status.QUEUED))
        .isEqualTo(VeriguardSandboxTask.Status.QUEUED);
    assertThat(SandboxTaskService.toEntityStatus(SandboxTaskStatus.Status.RUNNING))
        .isEqualTo(VeriguardSandboxTask.Status.RUNNING);
    assertThat(SandboxTaskService.toEntityStatus(SandboxTaskStatus.Status.COMPLETED))
        .isEqualTo(VeriguardSandboxTask.Status.COMPLETED);
    assertThat(SandboxTaskService.toEntityStatus(SandboxTaskStatus.Status.FAILED))
        .isEqualTo(VeriguardSandboxTask.Status.FAILED);
    assertThat(SandboxTaskService.toEntityStatus(SandboxTaskStatus.Status.UNKNOWN))
        .isEqualTo(VeriguardSandboxTask.Status.UNKNOWN);
  }

  @Test
  void sha256_hex_known_vector() {
    assertThat(SandboxTaskService.sha256Hex("hello world".getBytes()))
        .isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    assertThat(SandboxTaskService.sha256Hex(new byte[0]))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }

  private VeriguardSandboxTask newTask(VeriguardSandboxTask.Status status, Long capeTaskId) {
    VeriguardSandboxTask t = new VeriguardSandboxTask();
    t.setStatus(status);
    t.setCapeTaskId(capeTaskId);
    t.setSampleSha256("0".repeat(64));
    t.setSampleFilename("x");
    t.setSampleSizeBytes(1L);
    return t;
  }
}
