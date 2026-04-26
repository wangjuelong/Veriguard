package io.veriguard.rest.injector;

import static io.veriguard.database.specification.InjectorSpecification.byName;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.utils.AgentUtils.AVAILABLE_ARCHITECTURES;
import static io.veriguard.utils.AgentUtils.AVAILABLE_PLATFORMS;
import static io.veriguard.utils.SecurityUtils.validateJFrogUri;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.rest.catalog_connector.dto.ConnectorIds;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.inject.service.InjectStatusService;
import io.veriguard.rest.injector.form.InjectorCreateInput;
import io.veriguard.rest.injector.form.InjectorOutput;
import io.veriguard.rest.injector.form.InjectorUpdateInput;
import io.veriguard.rest.injector.response.InjectorRegistration;
import io.veriguard.service.InjectorService;
import io.veriguard.utils.AgentUtils;
import io.veriguard.utils.FilterUtilsJpa;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InjectorApi extends RestBehavior {

  public static final String INJECT0R_URI = "/api/injectors";

  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final InjectStatusService injectStatusService;
  private final InjectorService injectorService;

  @Value("${info.app.version:unknown}")
  String version;

  @Value("${executor.veriguard-implant.binaries.origin:${executor.veriguard.binaries.origin:local}}")
  private String implantBinaryOrigin;

  @Value(
      "${executor.veriguard-implant.binaries.version:${executor.veriguard.binaries.version:${info.app.version:unknown}}}")
  private String implantBinaryVersion;

  @GetMapping(INJECT0R_URI)
  @Operation(
      summary = "Retrieve injectors",
      description = "Retrieve all injectors and pending injectors if includeNext is true")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array = @ArraySchema(schema = @Schema(implementation = InjectorOutput.class))))
  public Iterable<InjectorOutput> injectors(
      @Parameter(
              name = "includeNext",
              description = "Include injectors pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return injectorService.injectorsOutput(includeNext);
  }

  @GetMapping(INJECT0R_URI + "/{injectorId}/injector_contracts")
  @RBAC(
      resourceId = "#injectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR)
  public Collection<JsonNode> injectorInjectTypes(@PathVariable String injectorId) {
    Injector injector =
        injectorRepository.findById(injectorId).orElseThrow(ElementNotFoundException::new);
    return fromIterable(injectorContractRepository.findInjectorContractsByInjector(injector))
        .stream()
        .map(
            contract -> {
              try {
                return mapper.readTree(contract.getContent());
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  @PutMapping(INJECT0R_URI + "/{injectorId}")
  @RBAC(
      resourceId = "#injectorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR)
  public Injector updateInjector(
      @PathVariable String injectorId, @Valid @RequestBody InjectorUpdateInput input) {
    Injector injector =
        injectorRepository.findById(injectorId).orElseThrow(ElementNotFoundException::new);
    return injectorService.updateExistingExternalInjector(
        injector,
        injector.getType(),
        input.getName(),
        input.getContracts(),
        input.getCustomContracts(),
        input.getCategory(),
        input.getExecutorCommands(),
        input.getExecutorClearCommands(),
        input.getPayloads());
  }

  @GetMapping(INJECT0R_URI + "/{injectorId}")
  @RBAC(
      resourceId = "#injectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR)
  public Injector injector(@PathVariable String injectorId) {
    return injectorRepository.findById(injectorId).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping(INJECT0R_URI + "/{injectorId}/related-ids")
  @RBAC(
      resourceId = "#injectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR)
  @Operation(summary = "Retrieve injector related ids")
  public ConnectorIds getInjectorRelatedIds(@PathVariable String injectorId) {
    return injectorService.getInjectorRelationsId(injectorId);
  }

  @PostMapping(
      value = INJECT0R_URI,
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR)
  @Transactional(rollbackOn = Exception.class)
  public InjectorRegistration registerInjector(
      @Valid @RequestPart("input") InjectorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> file) {
    return injectorService.registerExternalInjector(input, file);
  }

  // Public API
  @GetMapping(
      value = "/api/implant/veriguard/{platform}/{architecture}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getVeriguardImplant(
      @PathVariable String platform,
      @PathVariable String architecture,
      @RequestParam(required = false) final String injectId,
      @RequestParam(required = false) final String agentId)
      throws IOException {
    platform = Optional.ofNullable(platform).map(String::toLowerCase).orElse("");
    architecture =
        AgentUtils.getCanonicalArchitectureString(
            Optional.ofNullable(architecture).map(String::toLowerCase).orElse(""));
    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      this.injectStatusService.setImplantErrorTrace(
          injectId, agentId, "Unable to download the implant. Platform invalid: " + platform);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(architecture)) {
      this.injectStatusService.setImplantErrorTrace(
          injectId,
          agentId,
          "Unable to download the implant. Architecture invalid: " + architecture);
    }

    InputStream in = null;
    String filename = "";
    String resourcePath = "/veriguard-implant/" + platform + "/" + architecture + "/";

    if (implantBinaryOrigin.equals("local")) { // if we want the local binaries
      filename = "veriguard-implant-" + version + (platform.equals("windows") ? ".exe" : "");
      in = getClass().getResourceAsStream("/implants" + resourcePath + filename);
    } else if (implantBinaryOrigin.equals(
        "repository")) { // if we want a specific version from artifactory
      filename =
          "veriguard-implant-" + implantBinaryVersion + (platform.equals("windows") ? ".exe" : "");
      in = new BufferedInputStream(validateJFrogUri(resourcePath, filename).toURL().openStream());
    }

    if (in != null) {
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(IOUtils.toByteArray(in));
    }
    throw new UnsupportedOperationException("Implant " + platform + " executable not supported");
  }

  // -- OPTION --

  @GetMapping(INJECT0R_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return fromIterable(
            this.injectorRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(INJECT0R_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR)
  public List<FilterUtilsJpa.Option> optionsById(
      @RequestBody final List<String> ids, @RequestParam(required = false) final String sourceId) {
    return fromIterable(this.injectorRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
