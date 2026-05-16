package io.veriguard.rest.security_validation;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.VeriguardSandboxTask;
import io.veriguard.rest.exception.InputValidationException;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 沙箱任务 REST —— C1-Platform-5 (M3).
 *
 * <ul>
 *   <li>POST {@value #SANDBOX_TASKS_URI} (multipart) — 提交样本到沙箱，同步落库
 *   <li>GET {@value #SANDBOX_TASKS_URI} — 列出所有任务（按 created_at 倒序）
 *   <li>GET {@value #SANDBOX_TASKS_URI}/{id} — 取单条任务
 *   <li>POST {@value #SANDBOX_TASKS_URI}/{id}/refresh — 按需轮询，立刻返回最新状态
 * </ul>
 */
@RestController
public class SandboxTaskApi {

  public static final String SANDBOX_TASKS_URI = "/api/sandbox-tasks";

  private final SandboxTaskService taskService;

  public SandboxTaskApi(SandboxTaskService taskService) {
    this.taskService = taskService;
  }

  @PostMapping(value = SANDBOX_TASKS_URI, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Submit a sample to the sandbox and create a tracking task")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<VeriguardSandboxTask> submit(
      @RequestParam("sample") MultipartFile sample,
      @RequestParam(value = "sandbox_id", required = false) String sandboxId,
      @RequestParam(value = "sample_type", required = false) String sampleType,
      @RequestParam(value = "target_machine", required = false) String targetMachine,
      @RequestParam(value = "timeout_seconds", required = false) Integer timeoutSeconds)
      throws InputValidationException, IOException {
    if (sample == null || sample.isEmpty()) {
      throw new InputValidationException(
          "sandbox_task_sample_empty", "Sample binary must not be empty.");
    }
    SandboxTaskSubmitInput input =
        new SandboxTaskSubmitInput(
            sandboxId,
            sampleType,
            sample.getOriginalFilename(),
            sample.getBytes(),
            targetMachine,
            timeoutSeconds);
    VeriguardSandboxTask task = taskService.submit(input);
    return ResponseEntity.created(URI.create(SANDBOX_TASKS_URI + "/" + task.getId())).body(task);
  }

  @GetMapping(SANDBOX_TASKS_URI)
  @Operation(summary = "List sandbox tasks (newest first)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<VeriguardSandboxTask> list() {
    return taskService.list();
  }

  @GetMapping(SANDBOX_TASKS_URI + "/{taskId}")
  @Operation(summary = "Get a sandbox task by id")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public VeriguardSandboxTask get(@PathVariable @NotBlank String taskId) {
    return taskService.get(taskId);
  }

  @PostMapping(SANDBOX_TASKS_URI + "/{taskId}/refresh")
  @Operation(summary = "Refresh a sandbox task by polling CAPE once")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public VeriguardSandboxTask refresh(@PathVariable @NotBlank String taskId) {
    return taskService.refresh(taskId);
  }
}
