package io.veriguard.rest.collector;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.Collector;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.CollectorRepository;
import io.veriguard.database.repository.SecurityPlatformRepository;
import io.veriguard.rest.catalog_connector.dto.ConnectorIds;
import io.veriguard.rest.collector.form.CollectorCreateInput;
import io.veriguard.rest.collector.form.CollectorOutput;
import io.veriguard.rest.collector.form.CollectorUpdateInput;
import io.veriguard.rest.collector.service.CollectorService;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.FileService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class CollectorApi extends RestBehavior {
  public static final String COLLECTOR_URI = "/api/collectors";
  private final CollectorService collectorService;
  private final CollectorRepository collectorRepository;
  private final SecurityPlatformRepository securityPlatformRepository;

  private final FileService fileService;

  @GetMapping(COLLECTOR_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.COLLECTOR)
  @Operation(
      summary = "Retrieve collectors",
      description = "Retrieve all collectors and pending collectors if includeNext is true")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array = @ArraySchema(schema = @Schema(implementation = CollectorOutput.class))))
  public Iterable<CollectorOutput> collectors(
      @Parameter(
              name = "includeNext",
              description = "Include collectors pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return collectorService.collectorsOutput(includeNext);
  }

  private Collector updateCollector(
      Collector collector,
      String type,
      String name,
      int period,
      Instant lastExecution,
      String securityPlatform) {
    collector.setUpdatedAt(Instant.now());
    collector.setExternal(true);
    collector.setType(type);
    collector.setName(name);
    collector.setPeriod(period);
    collector.setLastExecution(lastExecution);
    if (securityPlatform != null) {
      collector.setSecurityPlatform(
          securityPlatformRepository.findById(securityPlatform).orElseThrow());
    }
    return collectorRepository.save(collector);
  }

  @GetMapping(COLLECTOR_URI + "/{collectorId}")
  @RBAC(
      resourceId = "#collectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.COLLECTOR)
  public Collector getCollector(@PathVariable String collectorId) {
    return collectorService.collector(collectorId);
  }

  @GetMapping(COLLECTOR_URI + "/{collectorId}/related-ids")
  @RBAC(
      resourceId = "#collectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.COLLECTOR)
  @Operation(summary = "Retrieve collector related ids")
  public ConnectorIds getCollectorRelatedIds(@PathVariable String collectorId) {
    return collectorService.getCollectorRelationsId(collectorId);
  }

  @PutMapping(COLLECTOR_URI + "/{collectorId}")
  @RBAC(
      resourceId = "#collectorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.COLLECTOR)
  @Transactional(rollbackOn = Exception.class)
  public Collector updateCollector(
      @PathVariable String collectorId, @Valid @RequestBody CollectorUpdateInput input) {
    Collector collector = collectorService.collector(collectorId);
    return updateCollector(
        collector,
        collector.getType(),
        collector.getName(),
        collector.getPeriod(),
        input.getLastExecution(),
        collector.getSecurityPlatform() != null ? collector.getSecurityPlatform().getId() : null);
  }

  @PostMapping(
      value = COLLECTOR_URI,
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.COLLECTOR)
  @Transactional(rollbackOn = Exception.class)
  public Collector registerCollector(
      @Valid @RequestPart("input") CollectorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> file) {
    try {
      // Upload icon
      if (file.isPresent() && "image/png".equals(file.get().getContentType())) {
        fileService.uploadFile(
            FileService.COLLECTORS_IMAGES_BASE_PATH + input.getType() + ".png", file.get());
      }
      // We need to support upsert for registration
      Collector collector = collectorRepository.findById(input.getId()).orElse(null);
      if (collector == null) {
        Collector collectorChecking = collectorRepository.findByType(input.getType()).orElse(null);
        if (collectorChecking != null) {
          throw new Exception(
              "The collector "
                  + input.getType()
                  + " already exists with a different ID, please delete it or contact your administrator.");
        }
      }
      if (collector != null) {
        return updateCollector(
            collector,
            input.getType(),
            input.getName(),
            input.getPeriod(),
            collector.getLastExecution(),
            input.getSecurityPlatform());
      } else {
        // save the nodeExecutor
        Collector newCollector = new Collector();
        newCollector.setId(input.getId());
        newCollector.setExternal(true);
        newCollector.setName(input.getName());
        newCollector.setType(input.getType());
        newCollector.setPeriod(input.getPeriod());
        if (input.getSecurityPlatform() != null) {
          newCollector.setSecurityPlatform(
              securityPlatformRepository.findById(input.getSecurityPlatform()).orElseThrow());
        }
        return collectorRepository.save(newCollector);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
