package io.veriguard.rest.security_validation;

import io.veriguard.database.model.VeriguardSandboxTask;
import io.veriguard.database.repository.VeriguardSandboxRepository;
import io.veriguard.database.repository.VeriguardSandboxTaskRepository;
import io.veriguard.integration.sandbox.SandboxDriverRegistry;
import io.veriguard.integration.sandbox.SandboxIntegrationException;
import io.veriguard.integration.sandbox.dto.SampleSubmissionRequest;
import io.veriguard.integration.sandbox.dto.SandboxTaskStatus;
import io.veriguard.integration.sandbox.dto.SubmissionResult;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 沙箱任务服务 —— C1-Platform-5 (M3).
 *
 * <p>在 {@link io.veriguard.integration.sandbox.SandboxDriver} 之上承担状态机：submit 同步调 driver 并落库，poll
 * 周期性刷新 active 行（QUEUED / RUNNING），refresh 提供按需轮询。终态行 （COMPLETED / FAILED / UNKNOWN）不再被 poll 扫描，原始
 * CAPE 状态写 raw_status 列保留。
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class SandboxTaskService {

  static final Set<VeriguardSandboxTask.Status> ACTIVE_STATUSES =
      EnumSet.of(VeriguardSandboxTask.Status.QUEUED, VeriguardSandboxTask.Status.RUNNING);

  private final VeriguardSandboxTaskRepository taskRepository;
  private final VeriguardSandboxRepository sandboxRepository;
  private final SandboxDriverRegistry driverRegistry;
  private final Clock clock;

  @Autowired
  public SandboxTaskService(
      VeriguardSandboxTaskRepository taskRepository,
      VeriguardSandboxRepository sandboxRepository,
      SandboxDriverRegistry driverRegistry) {
    this(taskRepository, sandboxRepository, driverRegistry, Clock.systemUTC());
  }

  /** Test hook — inject a fixed clock for deterministic timestamps. */
  SandboxTaskService(
      VeriguardSandboxTaskRepository taskRepository,
      VeriguardSandboxRepository sandboxRepository,
      SandboxDriverRegistry driverRegistry,
      Clock clock) {
    this.taskRepository = taskRepository;
    this.sandboxRepository = sandboxRepository;
    this.driverRegistry = driverRegistry;
    this.clock = clock;
  }

  public VeriguardSandboxTask submit(SandboxTaskSubmitInput input) throws InputValidationException {
    if (input == null) {
      throw new InputValidationException(
          "sandbox_task_input_missing", "Submission payload is required.");
    }
    if (input.content() == null || input.content().length == 0) {
      throw new InputValidationException(
          "sandbox_task_sample_empty", "Sample binary must not be empty.");
    }
    if (input.originalFilename() == null || input.originalFilename().isBlank()) {
      throw new InputValidationException(
          "sandbox_task_filename_blank", "Sample filename must not be blank.");
    }
    if (input.timeoutSeconds() != null && input.timeoutSeconds() <= 0) {
      throw new InputValidationException(
          "sandbox_task_timeout_invalid", "Timeout must be positive when provided.");
    }
    if (input.sandboxId() != null
        && !input.sandboxId().isBlank()
        && !sandboxRepository.existsById(input.sandboxId())) {
      throw new InputValidationException(
          "sandbox_task_sandbox_not_found",
          "Referenced sandbox preset does not exist: " + input.sandboxId());
    }

    String sha256 = sha256Hex(input.content());

    SubmissionResult submission =
        driverRegistry
            .driver()
            .submitSample(
                new SampleSubmissionRequest(
                    input.sandboxId(),
                    input.sampleType(),
                    input.originalFilename(),
                    sha256,
                    input.content(),
                    input.targetMachine(),
                    input.timeoutSeconds()));

    VeriguardSandboxTask task = new VeriguardSandboxTask();
    task.setSandboxId(blankToNull(input.sandboxId()));
    task.setSampleSha256(sha256);
    task.setSampleFilename(input.originalFilename());
    task.setSampleSizeBytes((long) input.content().length);
    task.setSampleType(blankToNull(input.sampleType()));
    task.setTargetMachine(blankToNull(input.targetMachine()));
    task.setTimeoutSeconds(input.timeoutSeconds());
    task.setCapeTaskId(submission.capeTaskId());
    task.setStatus(VeriguardSandboxTask.Status.QUEUED);
    task.setSubmittedAt(Instant.now(clock));
    return taskRepository.saveAndFlush(task);
  }

  @Transactional(readOnly = true)
  public List<VeriguardSandboxTask> list() {
    return taskRepository.findAllByOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public VeriguardSandboxTask get(String taskId) {
    return taskRepository.findById(taskId).orElseThrow(ElementNotFoundException::new);
  }

  /** On-demand refresh of a single task; called by REST `/refresh` endpoint. */
  public VeriguardSandboxTask refresh(String taskId) {
    VeriguardSandboxTask task =
        taskRepository.findById(taskId).orElseThrow(ElementNotFoundException::new);
    pollOne(task);
    return taskRepository.saveAndFlush(task);
  }

  /**
   * Called by the Quartz job. Sweeps non-terminal rows, refreshes each, returns count of rows that
   * transitioned to a different status (mainly for log/test asserts).
   */
  public int pollAllActive() {
    List<VeriguardSandboxTask> active =
        taskRepository.findByStatusInOrderByCreatedAtAsc(ACTIVE_STATUSES);
    if (active.isEmpty()) {
      return 0;
    }
    int transitioned = 0;
    for (VeriguardSandboxTask task : active) {
      VeriguardSandboxTask.Status before = task.getStatus();
      try {
        pollOne(task);
      } catch (RuntimeException ex) {
        // pollOne already records the error onto the entity; keep going for the rest of the batch.
        log.warn(
            "SandboxTaskPollingJob: failed to poll task {} (capeTaskId={}): {}",
            task.getId(),
            task.getCapeTaskId(),
            ex.getMessage());
      }
      taskRepository.save(task);
      if (task.getStatus() != before) {
        transitioned++;
      }
    }
    taskRepository.flush();
    return transitioned;
  }

  private void pollOne(VeriguardSandboxTask task) {
    if (task.getCapeTaskId() == null) {
      task.setStatus(VeriguardSandboxTask.Status.FAILED);
      task.setErrorMessage("Missing capeTaskId — cannot poll");
      task.setLastPolledAt(Instant.now(clock));
      return;
    }
    Instant now = Instant.now(clock);
    try {
      SandboxTaskStatus remote = driverRegistry.driver().fetchTaskStatus(task.getCapeTaskId());
      task.setStatus(toEntityStatus(remote.status()));
      task.setRawStatus(remote.rawRemoteStatus());
      task.setErrorMessage(remote.errorMessage());
      task.setLastPolledAt(now);
      if (task.getStatus() == VeriguardSandboxTask.Status.COMPLETED
          || task.getStatus() == VeriguardSandboxTask.Status.FAILED) {
        task.setCompletedAt(now);
      }
    } catch (SandboxIntegrationException ex) {
      task.setStatus(VeriguardSandboxTask.Status.FAILED);
      task.setErrorMessage(ex.getReasonCode() + ": " + ex.getMessage());
      task.setLastPolledAt(now);
      task.setCompletedAt(now);
      throw ex;
    }
  }

  static VeriguardSandboxTask.Status toEntityStatus(SandboxTaskStatus.Status driverStatus) {
    return switch (driverStatus) {
      case QUEUED -> VeriguardSandboxTask.Status.QUEUED;
      case RUNNING -> VeriguardSandboxTask.Status.RUNNING;
      case COMPLETED -> VeriguardSandboxTask.Status.COMPLETED;
      case FAILED -> VeriguardSandboxTask.Status.FAILED;
      case UNKNOWN -> VeriguardSandboxTask.Status.UNKNOWN;
    };
  }

  static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
