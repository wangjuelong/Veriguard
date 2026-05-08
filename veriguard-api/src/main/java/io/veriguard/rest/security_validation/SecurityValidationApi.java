package io.veriguard.rest.security_validation;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SecurityValidationApi {

  public static final String CAPABILITIES_URI = "/api/capabilities";
  public static final String ATTACK_USE_CASES_URI = "/api/attack-use-cases";
  public static final String ATTACK_ORCHESTRATION_URI = "/api/attack-orchestration";

  private final SecurityValidationService securityValidationService;

  @GetMapping(CAPABILITIES_URI + "/matrix")
  @Operation(summary = "Get the Veriguard PRD capability matrix")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public SecurityValidationDtos.CapabilityMatrixOutput capabilityMatrix() {
    return securityValidationService.capabilityMatrix();
  }

  @GetMapping(ATTACK_USE_CASES_URI + "/catalog")
  @Operation(summary = "Get Veriguard use case catalog metadata")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public SecurityValidationDtos.AttackCatalogOutput attackCatalog() {
    return securityValidationService.attackCatalog();
  }

  @GetMapping(ATTACK_ORCHESTRATION_URI + "/schema")
  @Operation(summary = "Get Veriguard attack orchestration policy schema")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public SecurityValidationDtos.OrchestrationSchemaOutput orchestrationSchema() {
    return securityValidationService.orchestrationSchema();
  }
}
