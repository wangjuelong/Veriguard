package io.veriguard.rest.injector;

import static io.veriguard.database.specification.NodeExecutorSpecification.byName;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.utils.AgentUtils.AVAILABLE_ARCHITECTURES;
import static io.veriguard.utils.AgentUtils.AVAILABLE_PLATFORMS;
import static io.veriguard.utils.SecurityUtils.validateJFrogUri;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeStatusService;
import io.veriguard.rest.catalog_connector.dto.ConnectorIds;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.injector.form.NodeExecutorCreateInput;
import io.veriguard.rest.injector.form.NodeExecutorOutput;
import io.veriguard.rest.injector.form.NodeExecutorUpdateInput;
import io.veriguard.rest.injector.response.NodeExecutorRegistration;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.utils.AgentUtils;
import io.veriguard.utils.FilterUtilsJpa;
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
public class NodeExecutorApi extends RestBehavior {

  public static final String INJECT0R_URI = "/api/injectors";

  private final NodeExecutorRepository nodeExecutorRepository;
  private final NodeContractRepository nodeContractRepository;
  private final AttackChainNodeStatusService attackChainNodeStatusService;
  private final NodeExecutorService nodeExecutorService;

  @Value("${info.app.version:unknown}")
  String version;

  @Value(
      "${executor.veriguard-implant.binaries.origin:${executor.veriguard.binaries.origin:local}}")
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
              array = @ArraySchema(schema = @Schema(implementation = NodeExecutorOutput.class))))
  public Iterable<NodeExecutorOutput> nodeExecutors(
      @Parameter(
              name = "includeNext",
              description = "Include injectors pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return nodeExecutorService.nodeExecutorsOutput(includeNext);
  }

  @GetMapping(INJECT0R_URI + "/{nodeExecutorId}/injector_contracts")
  @RBAC(
      resourceId = "#nodeExecutorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR)
  public Collection<JsonNode> nodeExecutorAttackChainNodeTypes(
      @PathVariable String nodeExecutorId) {
    NodeExecutor nodeExecutor =
        nodeExecutorRepository.findById(nodeExecutorId).orElseThrow(ElementNotFoundException::new);
    return fromIterable(nodeContractRepository.findNodeContractsByNodeExecutor(nodeExecutor))
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

  @PutMapping(INJECT0R_URI + "/{nodeExecutorId}")
  @RBAC(
      resourceId = "#nodeExecutorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR)
  public NodeExecutor updateNodeExecutor(
      @PathVariable String nodeExecutorId, @Valid @RequestBody NodeExecutorUpdateInput input) {
    NodeExecutor nodeExecutor =
        nodeExecutorRepository.findById(nodeExecutorId).orElseThrow(ElementNotFoundException::new);
    return nodeExecutorService.updateExistingExternalNodeExecutor(
        nodeExecutor,
        nodeExecutor.getType(),
        input.getName(),
        input.getContracts(),
        input.getCustomContracts(),
        input.getCategory(),
        input.getExecutorCommands(),
        input.getExecutorClearCommands(),
        input.getPayloads());
  }

  @GetMapping(INJECT0R_URI + "/{nodeExecutorId}")
  @RBAC(
      resourceId = "#nodeExecutorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR)
  public NodeExecutor nodeExecutor(@PathVariable String nodeExecutorId) {
    return nodeExecutorRepository
        .findById(nodeExecutorId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping(INJECT0R_URI + "/{nodeExecutorId}/related-ids")
  @RBAC(
      resourceId = "#nodeExecutorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR)
  @Operation(summary = "Retrieve injector related ids")
  public ConnectorIds getNodeExecutorRelatedIds(@PathVariable String nodeExecutorId) {
    return nodeExecutorService.getNodeExecutorRelationsId(nodeExecutorId);
  }

  @PostMapping(
      value = INJECT0R_URI,
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR)
  @Transactional(rollbackOn = Exception.class)
  public NodeExecutorRegistration registerNodeExecutor(
      @Valid @RequestPart("input") NodeExecutorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> file) {
    return nodeExecutorService.registerExternalNodeExecutor(input, file);
  }

  // Public API
  @GetMapping(
      value = "/api/implant/veriguard/{platform}/{architecture}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getVeriguardImplant(
      @PathVariable String platform,
      @PathVariable String architecture,
      @RequestParam(required = false) final String attackChainNodeId,
      @RequestParam(required = false) final String agentId)
      throws IOException {
    platform = Optional.ofNullable(platform).map(String::toLowerCase).orElse("");
    architecture =
        AgentUtils.getCanonicalArchitectureString(
            Optional.ofNullable(architecture).map(String::toLowerCase).orElse(""));
    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      this.attackChainNodeStatusService.setImplantErrorTrace(
          attackChainNodeId,
          agentId,
          "Unable to download the implant. Platform invalid: " + platform);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(architecture)) {
      this.attackChainNodeStatusService.setImplantErrorTrace(
          attackChainNodeId,
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
            this.nodeExecutorRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(INJECT0R_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR)
  public List<FilterUtilsJpa.Option> optionsById(
      @RequestBody final List<String> ids, @RequestParam(required = false) final String sourceId) {
    return fromIterable(this.nodeExecutorRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
