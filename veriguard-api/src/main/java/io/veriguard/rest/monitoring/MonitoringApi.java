package io.veriguard.rest.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.monitoring.MonitoringRunStatus;
import io.veriguard.monitoring.InvalidCronException;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringHistoryPageOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringJobOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringJobPageOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringTrendOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringTriggerOutput;
import io.veriguard.rest.monitoring.MonitoringService.JobRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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
 * REST 接口 —— PR C4 边界策略常态化监控.
 *
 * <p>覆盖招标 §3.2「对安全设备策略的有效性进行常态化监控，按天 / 按小时建立监控视图，输出趋势分析」.
 */
@RestController
public class MonitoringApi {

  public static final String JOBS_URI = "/api/monitoring/jobs";

  private final MonitoringService service;

  public MonitoringApi(MonitoringService service) {
    this.service = service;
  }

  // ============================================================
  // Jobs CRUD
  // ============================================================

  @PostMapping(JOBS_URI)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create monitoring job (cron + baseline_id)")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringJobOutput createJob(@Valid @RequestBody CreateJobBody body) {
    try {
      return service.createJob(toRequest(body));
    } catch (InvalidCronException | IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @GetMapping(JOBS_URI)
  @Operation(summary = "List monitoring jobs (optional enabled filter, paged)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringJobPageOutput listJobs(
      @Parameter(description = "Optional enabled filter") @RequestParam(value = "enabled", required = false)
          Boolean enabled,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(500) int size) {
    return service.listJobs(Optional.ofNullable(enabled), page, size);
  }

  @GetMapping(JOBS_URI + "/{id}")
  @Operation(summary = "Get monitoring job detail (incl. next_fire_at)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringJobOutput getJob(@PathVariable("id") @NotBlank String id) {
    try {
      return service.getJob(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PutMapping(JOBS_URI + "/{id}")
  @Operation(summary = "Update monitoring job")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringJobOutput updateJob(
      @PathVariable("id") @NotBlank String id, @Valid @RequestBody CreateJobBody body) {
    try {
      return service.updateJob(id, toRequest(body));
    } catch (InvalidCronException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IllegalArgumentException e) {
      // unknown id → 404；其它（baseline 不存在等）→ 400
      if (e.getMessage() != null && e.getMessage().startsWith("Unknown monitoring_job_id")) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @DeleteMapping(JOBS_URI + "/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete monitoring job")
  @RBAC(actionPerformed = Action.DELETE, resourceType = ResourceType.PLATFORM_SETTING)
  public void deleteJob(@PathVariable("id") @NotBlank String id) {
    try {
      service.deleteJob(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping(JOBS_URI + "/{id}/enable")
  @Operation(summary = "Enable monitoring job")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringJobOutput enableJob(@PathVariable("id") @NotBlank String id) {
    try {
      return service.setEnabled(id, true);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping(JOBS_URI + "/{id}/disable")
  @Operation(summary = "Disable monitoring job")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringJobOutput disableJob(@PathVariable("id") @NotBlank String id) {
    try {
      return service.setEnabled(id, false);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping(JOBS_URI + "/{id}/trigger-now")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Trigger monitoring job immediately (out of cron)")
  @RBAC(actionPerformed = Action.LAUNCH, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringTriggerOutput triggerNow(@PathVariable("id") @NotBlank String id) {
    try {
      return service.triggerNow(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  // ============================================================
  // History + Trend
  // ============================================================

  @GetMapping(JOBS_URI + "/{id}/history")
  @Operation(summary = "List monitoring history rows for a job (paged, optional time + status)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringHistoryPageOutput listHistory(
      @PathVariable("id") @NotBlank String id,
      @RequestParam(value = "from", required = false) Instant from,
      @RequestParam(value = "to", required = false) Instant to,
      @RequestParam(value = "status", required = false) MonitoringRunStatus status,
      @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(500) int size) {
    try {
      return service.listHistory(
          id,
          Optional.ofNullable(from),
          Optional.ofNullable(to),
          Optional.ofNullable(status),
          page,
          size);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @GetMapping(JOBS_URI + "/{id}/trend")
  @Operation(summary = "Trend aggregation by hour / day (招标 §3.2 趋势分析)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public MonitoringTrendOutput trend(
      @PathVariable("id") @NotBlank String id,
      @RequestParam(value = "aggregation", defaultValue = "day") String aggregation,
      @RequestParam("from") Instant from,
      @RequestParam("to") Instant to) {
    try {
      return service.trend(id, aggregation, from, to);
    } catch (IllegalArgumentException e) {
      // unknown id → 404；其它（aggregation/from/to 不合法）→ 400
      if (e.getMessage() != null && e.getMessage().startsWith("Unknown monitoring_job_id")) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  // ============================================================
  // Body records
  // ============================================================

  private static JobRequest toRequest(CreateJobBody body) {
    return new JobRequest(
        body.name(), body.baselineId(), body.cronExpression(), body.enabled(), body.description());
  }

  public record CreateJobBody(
      @JsonProperty("name") String name,
      @JsonProperty("baseline_id") String baselineId,
      @JsonProperty("cron_expression") String cronExpression,
      @JsonProperty("enabled") Boolean enabled,
      @JsonProperty("description") String description) {}
}
