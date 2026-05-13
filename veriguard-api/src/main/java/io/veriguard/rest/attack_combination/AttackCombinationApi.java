package io.veriguard.rest.attack_combination;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationResultPageOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationRunOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationRunPageOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.BypassDimensionPageOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.CombinationPreviewOutput;
import io.veriguard.rest.attack_combination.AttackCombinationRunService.CreateRunRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST 接口 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D1.
 *
 * <p>提供两个端点：
 * <ul>
 *   <li>GET /api/attack_combination/dimensions —— 维度列表（按 category 过滤 + 分页）</li>
 *   <li>POST /api/attack_combination/templates/preview —— 组合预览（笛卡尔积 size + 样例）</li>
 * </ul>
 *
 * <p>真正的 30 000 组合实例生成下沉到 PR D2.
 */
@RestController
public class AttackCombinationApi {

  public static final String DIMENSIONS_URI = "/api/attack_combination/dimensions";
  public static final String TEMPLATE_PREVIEW_URI = "/api/attack_combination/templates/preview";
  public static final String RUNS_URI = "/api/attack_combination/runs";

  private final AttackCombinationService service;
  private final AttackCombinationRunService runService;

  public AttackCombinationApi(
      AttackCombinationService service, AttackCombinationRunService runService) {
    this.service = service;
    this.runService = runService;
  }

  @GetMapping(DIMENSIONS_URI)
  @Operation(summary = "List bypass dimensions (paged, optional category filter)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public BypassDimensionPageOutput listDimensions(
      @Parameter(description = "Optional category filter (encoding / chunking / casing / ...)")
          @RequestParam(value = "category", required = false)
          BypassDimensionCategory category,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(500) int size) {
    return service.listDimensions(Optional.ofNullable(category), page, size);
  }

  @PostMapping(TEMPLATE_PREVIEW_URI)
  @Operation(
      summary =
          "Preview attack combinations (Cartesian product size + first N transformed payload samples)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public CombinationPreviewOutput previewTemplates(@Valid @RequestBody PreviewRequest request) {
    return service.preview(
        request.baseAttackTypes(), request.bypassDimensionIds(), request.previewBasePayload());
  }

  /** Preview 请求体 —— 直接 wire snake_case. */
  public record PreviewRequest(
      @NotEmpty
          @com.fasterxml.jackson.annotation.JsonProperty("base_attack_types")
          List<String> baseAttackTypes,
      @NotEmpty
          @com.fasterxml.jackson.annotation.JsonProperty("bypass_dimension_ids")
          List<String> bypassDimensionIds,
      @NotNull
          @com.fasterxml.jackson.annotation.JsonProperty("preview_base_payload")
          String previewBasePayload) {}

  // ============================================================
  // PR D2 —— 任务（run）端点
  // ============================================================

  @PostMapping(RUNS_URI)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create attack combination run (auto-starts by default)")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PLATFORM_SETTING)
  public AttackCombinationRunOutput createRun(
      @Valid @RequestBody CreateRunBody body,
      @RequestParam(value = "auto_start", defaultValue = "true") boolean autoStart) {
    try {
      return runService.create(
          new CreateRunRequest(
              body.name(),
              body.baseAttackTypes(),
              body.bypassDimensionIds(),
              body.assetIds(),
              body.rateLimitPerSecond(),
              body.concurrency(),
              body.maxRetries(),
              body.timeoutHours()),
          autoStart);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @GetMapping(RUNS_URI)
  @Operation(summary = "List attack combination runs (optional status filter, paged)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public AttackCombinationRunPageOutput listRuns(
      @Parameter(description = "Optional status filter")
          @RequestParam(value = "status", required = false)
          AttackCombinationRunStatus status,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(500) int size) {
    return runService.list(Optional.ofNullable(status), page, size);
  }

  @GetMapping(RUNS_URI + "/{id}")
  @Operation(summary = "Get attack combination run detail (incl. progress + hit state counts)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public AttackCombinationRunOutput getRun(@PathVariable("id") @NotBlank String id) {
    return runService.detail(id);
  }

  @PostMapping(RUNS_URI + "/{id}/pause")
  @Operation(summary = "Pause a running attack combination run")
  @RBAC(actionPerformed = Action.LAUNCH, resourceType = ResourceType.PLATFORM_SETTING)
  public AttackCombinationRunOutput pauseRun(@PathVariable("id") @NotBlank String id) {
    if (!runService.pause(id)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Run cannot be paused (must be in running state)");
    }
    return runService.detail(id);
  }

  @PostMapping(RUNS_URI + "/{id}/resume")
  @Operation(summary = "Resume a paused attack combination run")
  @RBAC(actionPerformed = Action.LAUNCH, resourceType = ResourceType.PLATFORM_SETTING)
  public AttackCombinationRunOutput resumeRun(@PathVariable("id") @NotBlank String id) {
    if (!runService.resume(id)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Run cannot be resumed (must be in paused state)");
    }
    return runService.detail(id);
  }

  @PostMapping(RUNS_URI + "/{id}/cancel")
  @Operation(summary = "Cancel an attack combination run")
  @RBAC(actionPerformed = Action.LAUNCH, resourceType = ResourceType.PLATFORM_SETTING)
  public AttackCombinationRunOutput cancelRun(@PathVariable("id") @NotBlank String id) {
    if (!runService.cancel(id)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Run cannot be cancelled (already in terminal state)");
    }
    return runService.detail(id);
  }

  @GetMapping(RUNS_URI + "/{id}/results")
  @Operation(summary = "List paged results for a given run (optional hit_state filter)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public AttackCombinationResultPageOutput listRunResults(
      @PathVariable("id") @NotBlank String id,
      @Parameter(description = "Optional hit_state filter")
          @RequestParam(value = "hit_state", required = false)
          AttackCombinationHitState hitState,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "50") @Min(1) @Max(500) int size) {
    return runService.listResults(id, Optional.ofNullable(hitState), page, size);
  }

  /** 创建任务请求体（snake_case wire 字段）. */
  public record CreateRunBody(
      @NotBlank @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
      @NotEmpty
          @com.fasterxml.jackson.annotation.JsonProperty("base_attack_types")
          List<String> baseAttackTypes,
      @NotEmpty
          @com.fasterxml.jackson.annotation.JsonProperty("bypass_dimension_ids")
          List<String> bypassDimensionIds,
      @NotEmpty
          @com.fasterxml.jackson.annotation.JsonProperty("asset_ids")
          List<String> assetIds,
      @com.fasterxml.jackson.annotation.JsonProperty("rate_limit_per_second")
          Integer rateLimitPerSecond,
      @com.fasterxml.jackson.annotation.JsonProperty("concurrency") Integer concurrency,
      @com.fasterxml.jackson.annotation.JsonProperty("max_retries") Integer maxRetries,
      @com.fasterxml.jackson.annotation.JsonProperty("timeout_hours") Integer timeoutHours) {}
}
