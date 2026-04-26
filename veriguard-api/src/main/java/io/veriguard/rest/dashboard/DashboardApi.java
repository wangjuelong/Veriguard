package io.veriguard.rest.dashboard;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.engine.model.EsBase;
import io.veriguard.engine.model.EsSearch;
import io.veriguard.engine.query.EsAttackPath;
import io.veriguard.engine.query.EsAvgs;
import io.veriguard.engine.query.EsCountInterval;
import io.veriguard.engine.query.EsSeries;
import io.veriguard.rest.dashboard.model.WidgetToEntitiesInput;
import io.veriguard.rest.dashboard.model.WidgetToEntitiesOutput;
import io.veriguard.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DashboardApi extends RestBehavior {

  public static final String DASHBOARD_URI = "/api/dashboards";

  private final DashboardService dashboardService;

  @PostMapping(DASHBOARD_URI + "/count/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsCountInterval count(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.count(widgetId, parameters);
  }

  @PostMapping(DASHBOARD_URI + "/average/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public EsAvgs average(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.average(widgetId, parameters);
  }

  @PostMapping(DASHBOARD_URI + "/series/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsSeries> series(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.series(widgetId, parameters);
  }

  @PostMapping(DASHBOARD_URI + "/entities/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsBase> entities(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return this.dashboardService.entities(widgetId, parameters);
  }

  @PostMapping(DASHBOARD_URI + "/entities-runtime/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public WidgetToEntitiesOutput widgetToEntitiesRuntime(
      @PathVariable final String widgetId, @Valid @RequestBody WidgetToEntitiesInput input) {
    return this.dashboardService.widgetToEntitiesRuntime(widgetId, input);
  }

  @PostMapping(DASHBOARD_URI + "/attack-paths/{widgetId}")
  @RBAC(
      resourceId = "#widgetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public List<EsAttackPath> attackPaths(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return this.dashboardService.attackPaths(widgetId, parameters);
  }

  @GetMapping(DASHBOARD_URI + "/search/{search}")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public List<EsSearch> search(@PathVariable final String search) {
    return this.dashboardService.search(search);
  }
}
