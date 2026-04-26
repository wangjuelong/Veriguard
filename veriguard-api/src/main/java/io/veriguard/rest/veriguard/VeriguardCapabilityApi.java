package io.veriguard.rest.veriguard;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.exception.InputValidationException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class VeriguardCapabilityApi {

  public static final String VERIGUARD_URI = "/api/veriguard";

  private final VeriguardCapabilityService capabilityService;

  @GetMapping(VERIGUARD_URI + "/capability-matrix")
  @Operation(summary = "Get the Veriguard PRD capability matrix")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public VeriguardDtos.CapabilityMatrixOutput capabilityMatrix() {
    return capabilityService.capabilityMatrix();
  }

  @GetMapping(VERIGUARD_URI + "/use-case-catalog")
  @Operation(summary = "Get Veriguard use case catalog metadata")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public VeriguardDtos.AttackCatalogOutput attackCatalog() {
    return capabilityService.attackCatalog();
  }

  @GetMapping(VERIGUARD_URI + "/orchestration-schema")
  @Operation(summary = "Get Veriguard attack orchestration policy schema")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public VeriguardDtos.OrchestrationSchemaOutput orchestrationSchema() {
    return capabilityService.orchestrationSchema();
  }

  @PostMapping(VERIGUARD_URI + "/sandboxes")
  @Operation(summary = "Create a Veriguard sandbox platform")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<VeriguardDtos.SandboxOutput> createSandbox(
      @Valid @RequestBody VeriguardSandboxInput input) throws InputValidationException {
    VeriguardDtos.SandboxOutput sandbox = capabilityService.createSandbox(input);
    return ResponseEntity.created(URI.create(VERIGUARD_URI + "/sandboxes/" + sandbox.id()))
        .body(sandbox);
  }

  @GetMapping(VERIGUARD_URI + "/sandboxes")
  @Operation(summary = "List Veriguard sandbox platforms")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<VeriguardDtos.SandboxOutput> sandboxes() {
    return capabilityService.sandboxes();
  }

  @GetMapping(VERIGUARD_URI + "/sandboxes/{sandboxId}")
  @Operation(summary = "Get a Veriguard sandbox platform")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public VeriguardDtos.SandboxOutput sandbox(@PathVariable @NotBlank String sandboxId) {
    return capabilityService.sandbox(sandboxId);
  }

  @PutMapping(VERIGUARD_URI + "/sandboxes/{sandboxId}")
  @Operation(summary = "Update a Veriguard sandbox platform")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public VeriguardDtos.SandboxOutput updateSandbox(
      @PathVariable @NotBlank String sandboxId, @Valid @RequestBody VeriguardSandboxInput input)
      throws InputValidationException {
    return capabilityService.updateSandbox(sandboxId, input);
  }

  @DeleteMapping(VERIGUARD_URI + "/sandboxes/{sandboxId}")
  @Operation(summary = "Delete a Veriguard sandbox platform")
  @RBAC(actionPerformed = Action.DELETE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<Void> deleteSandbox(@PathVariable @NotBlank String sandboxId) {
    capabilityService.deleteSandbox(sandboxId);
    return ResponseEntity.noContent().build();
  }
}
