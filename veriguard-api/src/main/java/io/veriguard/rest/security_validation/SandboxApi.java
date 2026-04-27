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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SandboxApi {

  public static final String SANDBOXES_URI = "/api/sandboxes";

  private final SandboxService sandboxService;
  private final SandboxScriptExporter scriptExporter;

  public SandboxApi(SandboxService sandboxService, SandboxScriptExporter scriptExporter) {
    this.sandboxService = sandboxService;
    this.scriptExporter = scriptExporter;
  }

  @PostMapping(SANDBOXES_URI)
  @Operation(summary = "Create a sandbox preset")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<SecurityValidationDtos.SandboxOutput> createSandbox(
      @Valid @RequestBody SandboxInput input) throws InputValidationException {
    SecurityValidationDtos.SandboxOutput sandbox = sandboxService.createSandbox(input);
    return ResponseEntity.created(URI.create(SANDBOXES_URI + "/" + sandbox.id())).body(sandbox);
  }

  @GetMapping(SANDBOXES_URI)
  @Operation(summary = "List sandbox presets")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<SecurityValidationDtos.SandboxOutput> sandboxes() {
    return sandboxService.sandboxes();
  }

  @GetMapping(SANDBOXES_URI + "/{sandboxId}")
  @Operation(summary = "Get a sandbox preset")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public SecurityValidationDtos.SandboxOutput sandbox(@PathVariable @NotBlank String sandboxId) {
    return sandboxService.sandbox(sandboxId);
  }

  @PutMapping(SANDBOXES_URI + "/{sandboxId}")
  @Operation(summary = "Update a sandbox preset")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public SecurityValidationDtos.SandboxOutput updateSandbox(
      @PathVariable @NotBlank String sandboxId, @Valid @RequestBody SandboxInput input)
      throws InputValidationException {
    return sandboxService.updateSandbox(sandboxId, input);
  }

  @DeleteMapping(SANDBOXES_URI + "/{sandboxId}")
  @Operation(summary = "Delete a sandbox preset")
  @RBAC(actionPerformed = Action.DELETE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<Void> deleteSandbox(@PathVariable @NotBlank String sandboxId) {
    sandboxService.deleteSandbox(sandboxId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping(
      value = SANDBOXES_URI + "/{sandboxId}/network-rules/exports/iptables",
      produces = "text/plain;charset=UTF-8")
  @Operation(summary = "Export iptables script for a sandbox preset")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<String> exportIptables(@PathVariable @NotBlank String sandboxId) {
    SecurityValidationDtos.SandboxOutput sandbox = sandboxService.sandbox(sandboxId);
    String body = scriptExporter.toIptables(sandbox.name(), sandbox.networkRules());
    String filename = scriptExporter.iptablesFilename(sandbox.name());
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(body);
  }

  @GetMapping(
      value = SANDBOXES_URI + "/{sandboxId}/network-rules/exports/routing-conf",
      produces = "text/plain;charset=UTF-8")
  @Operation(summary = "Export CAPEv2 routing.conf snippet for a sandbox preset")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<String> exportRoutingConf(@PathVariable @NotBlank String sandboxId) {
    SecurityValidationDtos.SandboxOutput sandbox = sandboxService.sandbox(sandboxId);
    String body = scriptExporter.toRoutingConf(sandbox.name(), sandbox.networkRules());
    String filename = scriptExporter.routingConfFilename(sandbox.name());
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(body);
  }
}
