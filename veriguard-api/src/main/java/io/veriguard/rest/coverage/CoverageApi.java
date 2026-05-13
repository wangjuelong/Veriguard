package io.veriguard.rest.coverage;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.coverage.CoverageHitState;
import io.veriguard.database.model.coverage.CoverageRunStatus;
import io.veriguard.database.model.coverage.CoverageType;
import io.veriguard.database.model.coverage.PolicyDeviceType;
import io.veriguard.rest.coverage.CoverageDtos.CoverageBaselineOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageBaselinePageOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageDiffOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageMatrixPageOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageRunCreateOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageRunOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageRunPageOutput;
import io.veriguard.rest.coverage.CoverageDtos.PolicyOutput;
import io.veriguard.rest.coverage.CoverageDtos.PolicyPageOutput;
import io.veriguard.rest.coverage.CoverageService.BaselineRequest;
import io.veriguard.rest.coverage.CoverageService.PolicyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST 接口 —— PR C3 边界覆盖度子模块.
 *
 * <p>覆盖招标 §3.1（边界资产覆盖度验证）+ §4.1（流量边界覆盖度验证），由 baseline.coverage_type 区分.
 */
@RestController
public class CoverageApi {

  public static final String BASELINES_URI = "/api/coverage/baselines";
  public static final String RUNS_URI = "/api/coverage/runs";
  public static final String POLICIES_URI = "/api/coverage/policies";

  private final CoverageService service;

  public CoverageApi(CoverageService service) {
    this.service = service;
  }

  // ============================================================
  // Baselines
  // ============================================================

  @PostMapping(BASELINES_URI)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create coverage baseline (boundary §3.1 / traffic §4.1)")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageBaselineOutput createBaseline(@Valid @RequestBody CreateBaselineBody body) {
    try {
      return service.createBaseline(toBaselineRequest(body));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @GetMapping(BASELINES_URI)
  @Operation(summary = "List coverage baselines (optional coverage_type filter, paged)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageBaselinePageOutput listBaselines(
      @Parameter(description = "Optional coverage_type filter: boundary / traffic")
          @RequestParam(value = "coverage_type", required = false)
          CoverageType coverageType,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(500) int size) {
    return service.listBaselines(Optional.ofNullable(coverageType), page, size);
  }

  @GetMapping(BASELINES_URI + "/{id}")
  @Operation(summary = "Get coverage baseline detail")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageBaselineOutput getBaseline(@PathVariable("id") @NotBlank String id) {
    try {
      return service.getBaseline(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PutMapping(BASELINES_URI + "/{id}")
  @Operation(summary = "Update coverage baseline")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageBaselineOutput updateBaseline(
      @PathVariable("id") @NotBlank String id, @Valid @RequestBody CreateBaselineBody body) {
    try {
      return service.updateBaseline(id, toBaselineRequest(body));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @DeleteMapping(BASELINES_URI + "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete coverage baseline")
  @RBAC(actionPerformed = Action.DELETE, resourceType = ResourceType.PLATFORM_SETTING)
  public void deleteBaseline(@PathVariable("id") @NotBlank String id) {
    try {
      service.deleteBaseline(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping(BASELINES_URI + "/{id}/run")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Trigger a new coverage run for the given baseline (async)")
  @RBAC(actionPerformed = Action.LAUNCH, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageRunCreateOutput triggerRun(@PathVariable("id") @NotBlank String id) {
    try {
      return service.triggerRun(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  // ============================================================
  // Runs
  // ============================================================

  @GetMapping(RUNS_URI)
  @Operation(summary = "List coverage runs (filter by baseline_id / status, paged)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageRunPageOutput listRuns(
      @RequestParam(value = "baseline_id", required = false) String baselineId,
      @RequestParam(value = "status", required = false) CoverageRunStatus status,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(500) int size) {
    return service.listRuns(
        Optional.ofNullable(baselineId), Optional.ofNullable(status), page, size);
  }

  @GetMapping(RUNS_URI + "/{id}")
  @Operation(summary = "Get coverage run detail (incl. progress + 4-state counts)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageRunOutput getRun(@PathVariable("id") @NotBlank String id) {
    try {
      return service.getRun(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @GetMapping(RUNS_URI + "/{id}/matrix")
  @Operation(
      summary = "List coverage matrix cells for a run (paged, optional hit_state filter)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageMatrixPageOutput listMatrix(
      @PathVariable("id") @NotBlank String id,
      @RequestParam(value = "hit_state", required = false) CoverageHitState hitState,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "100") @Min(1) @Max(2000) int size) {
    try {
      return service.listMatrix(id, Optional.ofNullable(hitState), page, size);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @GetMapping(RUNS_URI + "/{id}/diff")
  @Operation(summary = "Diff against another run (compare_with=<run_id>)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public CoverageDiffOutput diffRun(
      @PathVariable("id") @NotBlank String id,
      @RequestParam("compare_with") @NotBlank String compareWith) {
    try {
      return service.diffRuns(compareWith, id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  // ============================================================
  // Policies
  // ============================================================

  @PostMapping(POLICIES_URI)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create policy (waf / ips / ids / nta / hids)")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PLATFORM_SETTING)
  public PolicyOutput createPolicy(@Valid @RequestBody CreatePolicyBody body) {
    try {
      return service.createPolicy(
          new PolicyRequest(
              body.name(),
              body.deviceType(),
              body.deviceId(),
              body.externalRuleId(),
              body.description()));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @GetMapping(POLICIES_URI)
  @Operation(summary = "List policies (optional device_type filter, paged)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public PolicyPageOutput listPolicies(
      @RequestParam(value = "device_type", required = false) PolicyDeviceType deviceType,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(500) int size) {
    return service.listPolicies(Optional.ofNullable(deviceType), page, size);
  }

  // ============================================================
  // Body records (snake_case wire)
  // ============================================================

  private static BaselineRequest toBaselineRequest(CreateBaselineBody body) {
    return new BaselineRequest(
        body.name(),
        body.coverageType(),
        body.caseIds(),
        body.assetGroupId(),
        body.description(),
        body.socQueryDelaySeconds());
  }

  public record CreateBaselineBody(
      @JsonProperty("name") String name,
      @JsonProperty("coverage_type") String coverageType,
      @JsonProperty("case_ids") List<String> caseIds,
      @JsonProperty("asset_group_id") String assetGroupId,
      @JsonProperty("description") String description,
      @JsonProperty("soc_query_delay_seconds") Integer socQueryDelaySeconds) {}

  public record CreatePolicyBody(
      @JsonProperty("name") String name,
      @JsonProperty("device_type") String deviceType,
      @JsonProperty("device_id") String deviceId,
      @JsonProperty("external_rule_id") String externalRuleId,
      @JsonProperty("description") String description) {}
}
