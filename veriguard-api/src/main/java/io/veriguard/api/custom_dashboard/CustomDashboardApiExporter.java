package io.veriguard.api.custom_dashboard;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.CustomDashboard;
import io.veriguard.database.model.ResourceType;
import io.veriguard.jsonapi.ZipJsonApi;
import io.veriguard.rest.custom_dashboard.CustomDashboardApi;
import io.veriguard.rest.custom_dashboard.CustomDashboardService;
import io.veriguard.rest.helper.RestBehavior;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CustomDashboardApi.CUSTOM_DASHBOARDS_URI)
@RequiredArgsConstructor
public class CustomDashboardApiExporter extends RestBehavior {

  private final CustomDashboardService customDashboardService;
  private final ZipJsonApi<CustomDashboard> zipJsonApi;

  @Operation(
      description =
          "Exports a custom dashboard in JSON:API format, optionally including related entities.")
  @GetMapping(value = "/{customDashboardId}/export", produces = "application/zip")
  @Transactional(readOnly = true)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<byte[]> export(@PathVariable @NotBlank final String customDashboardId)
      throws IOException {
    CustomDashboard customDashboard = customDashboardService.customDashboard(customDashboardId);
    return zipJsonApi.handleExport(customDashboard);
  }
}
