package io.veriguard.api.payload;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.Payload;
import io.veriguard.database.model.ResourceType;
import io.veriguard.jsonapi.JsonApiDocument;
import io.veriguard.jsonapi.ResourceObject;
import io.veriguard.jsonapi.ZipJsonApi;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.payload.PayloadApi;
import io.veriguard.rest.payload.service.PayloadService;
import io.veriguard.service.ImportService;
import io.veriguard.service.ZipJsonService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping(PayloadApi.PAYLOAD_URI)
@RequiredArgsConstructor
public class PayloadApiImporter extends RestBehavior {

  private final ZipJsonApi<Payload> zipJsonApi;
  private final ImportService importService;
  private final PayloadService payloadService;

  @Operation(
      description =
          "Imports a payload from a JSON:API document. The name will be suffixed with '(import)' by default.")
  @PostMapping(
      value = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<JsonApiDocument<ResourceObject>> importJson(
      @RequestPart("file") @NotNull MultipartFile file) throws Exception {
    try {
      ZipJsonService.ImportOutput<Payload> response =
          zipJsonApi.handleImport(file, "payload_name", null, this::sanitize);
      payloadService.updateNodeContractsForPayload(response.persistedData());
      return ResponseEntity.ok(response.jsonApiDocument());
    } catch (Exception ex) {
      log.warn("Fallback to old import due to {}", ex.getMessage(), ex);
      // Fall back to the legacy importer
      importService.handleFileImport(file, null, null);
      return ResponseEntity.ok().build();
    }
  }

  /**
   * Removes detection remediations whose collector does not exist in the target database. During
   * import, the collector is resolved by its business key (type). If not found, the collector field
   * is null and the remediation must be dropped to avoid constraint violations.
   */
  private Payload sanitize(Payload payload) {
    payload
        .getDetectionRemediations()
        .removeIf(
            dr -> {
              if (dr.getCollector() == null) {
                log.warn("Skipping detection remediation — collector not found in target database");
                return true;
              }
              return false;
            });
    return payload;
  }
}
