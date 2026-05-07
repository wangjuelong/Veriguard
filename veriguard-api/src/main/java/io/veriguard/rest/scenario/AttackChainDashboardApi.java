package io.veriguard.rest.scenario;

import static io.veriguard.rest.scenario.AttackChainApi.SCENARIO_URI;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.CustomDashboard;
import io.veriguard.database.model.ResourceType;
import io.veriguard.engine.model.EsBase;
import io.veriguard.engine.query.EsAttackPath;
import io.veriguard.engine.query.EsAvgs;
import io.veriguard.engine.query.EsCountInterval;
import io.veriguard.engine.query.EsSeries;
import io.veriguard.rest.custom_dashboard.CustomDashboardService;
import io.veriguard.rest.dashboard.model.WidgetToEntitiesInput;
import io.veriguard.rest.dashboard.model.WidgetToEntitiesOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AttackChainDashboardApi {

  private final CustomDashboardService customDashboardService;

  @Operation(summary = "Find the dashboard linked to a Scenario")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The dashboard"),
        @ApiResponse(responseCode = "404", description = "The Scenario doesn't exist")
      })
  @GetMapping(SCENARIO_URI + "/{scenarioId}/dashboard")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ResponseEntity<CustomDashboard> dashboard(@PathVariable final String attackChainId) {
    return ResponseEntity.ok(
        this.customDashboardService.findCustomDashboardByResourceId(attackChainId));
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/count/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public EsCountInterval dashboardCount(
      @PathVariable final String attackChainId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardCountOnResourceId(attackChainId, widgetId, parameters);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/average/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public EsAvgs dashboardAverage(
      @PathVariable final String attackChainId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardAverageOnResourceId(
        attackChainId, widgetId, parameters);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/series/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<EsSeries> dashboardSeries(
      @PathVariable final String attackChainId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardSeriesOnResourceId(
        attackChainId, widgetId, parameters);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/entities/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<EsBase> dashboardEntities(
      @PathVariable final String attackChainId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.customDashboardService.dashboardEntitiesOnResourceId(
        attackChainId, widgetId, parameters);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/entities-runtime/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      @PathVariable final String attackChainId,
      @PathVariable final String widgetId,
      @Valid @RequestBody WidgetToEntitiesInput input) {
    return this.customDashboardService.widgetToEntitiesRuntimeOnResourceId(
        attackChainId, widgetId, input);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/dashboard/attack-paths/{widgetId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(summary = "Search TagRules")
  public List<EsAttackPath> dashboardAttackPaths(
      @PathVariable final String attackChainId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return this.customDashboardService.dashboardAttackPathsOnResourceId(
        attackChainId, widgetId, parameters);
  }
}
