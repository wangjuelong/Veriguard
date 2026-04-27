package io.veriguard.rest.security_validation;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.exception.InputValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SecurityValidationApi {

    public static final String CAPABILITIES_URI = "/api/capabilities";
    public static final String ATTACK_USE_CASES_URI = "/api/attack-use-cases";
    public static final String ATTACK_ORCHESTRATION_URI =
        "/api/attack-orchestration";
    public static final String SANDBOXES_URI = "/api/sandboxes";

    private final SecurityValidationService securityValidationService;

    @GetMapping(CAPABILITIES_URI + "/matrix")
    @Operation(summary = "Get the Veriguard PRD capability matrix")
    @RBAC(
        actionPerformed = Action.READ,
        resourceType = ResourceType.PLATFORM_SETTING
    )
    public SecurityValidationDtos.CapabilityMatrixOutput capabilityMatrix() {
        return securityValidationService.capabilityMatrix();
    }

    @GetMapping(ATTACK_USE_CASES_URI + "/catalog")
    @Operation(summary = "Get Veriguard use case catalog metadata")
    @RBAC(
        actionPerformed = Action.READ,
        resourceType = ResourceType.PLATFORM_SETTING
    )
    public SecurityValidationDtos.AttackCatalogOutput attackCatalog() {
        return securityValidationService.attackCatalog();
    }

    @GetMapping(ATTACK_ORCHESTRATION_URI + "/schema")
    @Operation(summary = "Get Veriguard attack orchestration policy schema")
    @RBAC(
        actionPerformed = Action.READ,
        resourceType = ResourceType.PLATFORM_SETTING
    )
    public SecurityValidationDtos.OrchestrationSchemaOutput orchestrationSchema() {
        return securityValidationService.orchestrationSchema();
    }

    @PostMapping(SANDBOXES_URI)
    @Operation(summary = "Create a Veriguard sandbox platform")
    @RBAC(
        actionPerformed = Action.WRITE,
        resourceType = ResourceType.PLATFORM_SETTING
    )
    public ResponseEntity<SecurityValidationDtos.SandboxOutput> createSandbox(
        @Valid @RequestBody SandboxInput input
    ) throws InputValidationException {
        SecurityValidationDtos.SandboxOutput sandbox =
            securityValidationService.createSandbox(input);
        return ResponseEntity.created(
            URI.create(SANDBOXES_URI + "/" + sandbox.id())
        ).body(sandbox);
    }

    @GetMapping(SANDBOXES_URI)
    @Operation(summary = "List Veriguard sandbox platforms")
    @RBAC(
        actionPerformed = Action.READ,
        resourceType = ResourceType.PLATFORM_SETTING
    )
    public List<SecurityValidationDtos.SandboxOutput> sandboxes() {
        return securityValidationService.sandboxes();
    }

    @GetMapping(SANDBOXES_URI + "/{sandboxId}")
    @Operation(summary = "Get a Veriguard sandbox platform")
    @RBAC(
        actionPerformed = Action.READ,
        resourceType = ResourceType.PLATFORM_SETTING
    )
    public SecurityValidationDtos.SandboxOutput sandbox(
        @PathVariable @NotBlank String sandboxId
    ) {
        return securityValidationService.sandbox(sandboxId);
    }

    @PutMapping(SANDBOXES_URI + "/{sandboxId}")
    @Operation(summary = "Update a Veriguard sandbox platform")
    @RBAC(
        actionPerformed = Action.WRITE,
        resourceType = ResourceType.PLATFORM_SETTING
    )
    public SecurityValidationDtos.SandboxOutput updateSandbox(
        @PathVariable @NotBlank String sandboxId,
        @Valid @RequestBody SandboxInput input
    ) throws InputValidationException {
        return securityValidationService.updateSandbox(sandboxId, input);
    }

    @DeleteMapping(SANDBOXES_URI + "/{sandboxId}")
    @Operation(summary = "Delete a Veriguard sandbox platform")
    @RBAC(
        actionPerformed = Action.DELETE,
        resourceType = ResourceType.PLATFORM_SETTING
    )
    public ResponseEntity<Void> deleteSandbox(
        @PathVariable @NotBlank String sandboxId
    ) {
        securityValidationService.deleteSandbox(sandboxId);
        return ResponseEntity.noContent().build();
    }
}
