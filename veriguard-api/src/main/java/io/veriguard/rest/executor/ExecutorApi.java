package io.veriguard.rest.executor;

import static io.veriguard.service.EndpointService.SERVICE;
import static io.veriguard.utils.AgentUtils.AVAILABLE_ARCHITECTURES;
import static io.veriguard.utils.AgentUtils.AVAILABLE_PLATFORMS;
import static io.veriguard.utils.SecurityUtils.validateJFrogUri;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.ExecutorRepository;
import io.veriguard.database.repository.TokenRepository;
import io.veriguard.executors.ExecutorService;
import io.veriguard.rest.catalog_connector.dto.ConnectorIds;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.executor.form.ExecutorCreateInput;
import io.veriguard.rest.executor.form.ExecutorOutput;
import io.veriguard.rest.executor.form.ExecutorUpdateInput;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.EndpointService;
import io.veriguard.service.FileService;
import io.veriguard.utils.AgentUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ExecutorApi extends RestBehavior {

  public static final String EXECUTOR_URI = "/api/executors";

  @Value("${info.app.version:unknown}")
  String version;

  @Value("${executor.veriguard-agent.binaries.origin:${executor.veriguard.binaries.origin:local}}")
  private String agentBinaryOrigin;

  @Value(
      "${executor.veriguard-agent.binaries.version:${executor.veriguard.binaries.version:${info.app.version:unknown}}}")
  private String agentBinaryVersion;

  private final ExecutorRepository executorRepository;
  private final EndpointService endpointService;
  private final FileService fileService;
  private final TokenRepository tokenRepository;
  private final ExecutorService executorService;

  @Resource protected ObjectMapper mapper;

  @GetMapping(EXECUTOR_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Retrieve executors",
      description = "Retrieve all executors and pending executors if includeNext is true")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array = @ArraySchema(schema = @Schema(implementation = ExecutorOutput.class))))
  public Iterable<ExecutorOutput> executors(
      @Parameter(
              name = "includeNext",
              description = "Include executors pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return executorService.executorsOutput(includeNext);
  }

  @GetMapping(EXECUTOR_URI + "/{executorId}")
  @RBAC(
      resourceId = "#collectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  public Executor getExecutor(@PathVariable String executorId) {
    return executorService.executor(executorId);
  }

  @GetMapping(EXECUTOR_URI + "/{executorId}/related-ids")
  @RBAC(
      resourceId = "#executorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Retrieve executor related ids")
  public ConnectorIds getExecutorRelatedIds(@PathVariable String executorId) {
    return executorService.getExecutorRelationsId(executorId);
  }

  private Executor updateExecutor(Executor executor, String type, String name, String[] platforms) {
    executor.setUpdatedAt(Instant.now());
    executor.setType(type);
    executor.setName(name);
    executor.setPlatforms(platforms);
    return executorRepository.save(executor);
  }

  @PutMapping(EXECUTOR_URI + "/{executorId}")
  @RBAC(
      resourceId = "#executorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  public Executor updateExecutor(
      @PathVariable String executorId, @Valid @RequestBody ExecutorUpdateInput input) {
    Executor executor =
        executorRepository.findById(executorId).orElseThrow(ElementNotFoundException::new);
    return updateExecutor(
        executor, executor.getType(), executor.getName(), executor.getPlatforms());
  }

  @PostMapping(
      value = EXECUTOR_URI,
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackOn = Exception.class)
  public Executor registerExecutor(
      @Valid @RequestPart("input") ExecutorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> icon,
      @RequestPart("banner") Optional<MultipartFile> banner) {
    try {
      // Upload icon
      if (icon.isPresent() && "image/png".equals(icon.get().getContentType())) {
        fileService.uploadFile(
            FileService.EXECUTORS_IMAGES_ICONS_BASE_PATH + input.getType() + ".png", icon.get());
      }
      // Upload icon
      if (banner.isPresent() && "image/png".equals(banner.get().getContentType())) {
        fileService.uploadFile(
            FileService.EXECUTORS_IMAGES_BANNERS_BASE_PATH + input.getType() + ".png",
            banner.get());
      }
      // We need to support upsert for registration
      Executor executor = executorRepository.findById(input.getId()).orElse(null);
      if (executor == null) {
        Executor executorChecking = executorRepository.findByType(input.getType()).orElse(null);
        if (executorChecking != null) {
          throw new Exception(
              "The executor "
                  + input.getType()
                  + " already exists with a different ID, please delete it or contact your administrator.");
        }
      }
      if (executor != null) {
        return updateExecutor(executor, input.getType(), input.getName(), input.getPlatforms());
      } else {
        // save the nodeExecutor
        Executor newExecutor = new Executor();
        newExecutor.setId(input.getId());
        newExecutor.setName(input.getName());
        newExecutor.setType(input.getType());
        newExecutor.setPlatforms(input.getPlatforms());
        return executorRepository.save(newExecutor);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Public API
  @Operation(
      summary = "Retrieve Veriguard Agent Executable",
      description =
          "Downloads the Veriguard agent executable for a specified platform and architecture.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the executable."),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid platform or architecture specified."),
      })
  @GetMapping(
      value = "/api/agent/executable/veriguard/{platform}/{architecture}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getVeriguardAgentExecutable(
      @Parameter(
              description =
                  "Target platform for the agent installation (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description =
                  "Target architecture for the agent installation (e.g., x86_64, arm64). Case insensitive.",
              required = true)
          @PathVariable
          String architecture)
      throws IOException {
    platform = Optional.ofNullable(platform).map(String::toLowerCase).orElse("");
    architecture =
        Optional.ofNullable(AgentUtils.getCanonicalArchitectureString(architecture))
            .map(String::toLowerCase)
            .orElse("");

    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      throw new IllegalArgumentException("Platform invalid : " + platform);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(architecture)) {
      throw new IllegalArgumentException("Architecture invalid : " + architecture);
    }

    InputStream in = null;
    String resourcePath = "/veriguard-agent/" + platform + "/" + architecture + "/";
    String filename = "";

    if (agentBinaryOrigin.equals("local")) { // if we want the local binaries
      filename = "veriguard-agent-" + version + (platform.equals("windows") ? ".exe" : "");
      in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
    } else if (agentBinaryOrigin.equals(
        "repository")) { // if we want a specific version from artifactory
      filename = "veriguard-agent-" + agentBinaryVersion + (platform.equals("windows") ? ".exe" : "");
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
    throw new UnsupportedOperationException("Agent " + platform + " executable not supported");
  }

  // Public API
  @Operation(
      summary = "Retrieve Veriguard Agent Package",
      description =
          "Downloads the Veriguard agent package for the specified platform and architecture.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved the agent package."),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid platform or architecture specified."),
      })
  @GetMapping(
      value = "/api/agent/package/veriguard/{platform}/{architecture}/{installationMode}",
      produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<byte[]> getVeriguardAgentPackage(
      @Parameter(
              description =
                  "Target platform for the agent package (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description =
                  "Target architecture for the agent package (e.g., x86, x64, arm). Case insensitive.",
              required = true)
          @PathVariable
          String architecture,
      @Parameter(
              description = "Installation Mode: session, user or system service",
              required = true)
          @PathVariable
          String installationMode)
      throws IOException {
    platform = Optional.ofNullable(platform).map(String::toLowerCase).orElse("");
    architecture =
        AgentUtils.getCanonicalArchitectureString(
            Optional.ofNullable(architecture).map(String::toLowerCase).orElse(""));
    installationMode = installationMode.toLowerCase();

    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      throw new IllegalArgumentException("Platform invalid : " + platform);
    }
    if (!AVAILABLE_ARCHITECTURES.contains(architecture)) {
      throw new IllegalArgumentException("Architecture invalid : " + architecture);
    }

    byte[] file = null;
    String filename = null;

    if (platform.equals("windows")) {
      InputStream in = null;
      String resourcePath = "/veriguard-agent/windows/" + architecture + "/";

      filename = "veriguard-agent-installer-";
      if (installationMode != null && !installationMode.equals(SERVICE)) {
        filename = filename.concat(installationMode).concat("-");
      }

      if (agentBinaryOrigin.equals("local")) { // if we want the local binaries
        filename = filename.concat(version).concat(".exe");
        in = getClass().getResourceAsStream("/agents" + resourcePath + filename);
      } else if (agentBinaryOrigin.equals(
          "repository")) { // if we want a specific version from artifactory
        filename = filename.concat(agentBinaryVersion).concat(".exe");
        in = new BufferedInputStream(validateJFrogUri(resourcePath, filename).toURL().openStream());
      }
      if (in == null) {
        throw new UnsupportedOperationException(
            "Agent version " + agentBinaryVersion + " not found");
      }
      file = IOUtils.toByteArray(in);
    }
    // linux & macos - No package needed
    if (file != null) {
      HttpHeaders headers = new HttpHeaders();
      headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
      return ResponseEntity.ok()
          .headers(headers)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(file);
    }
    throw new UnsupportedOperationException("Agent " + platform + " package not supported");
  }

  // Public API
  @Operation(
      summary = "Retrieve Veriguard Agent Installer Command",
      description =
          "Generates the installation command for the Veriguard agent for the specified platform, installation mode and token.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully generated the install command."),
        @ApiResponse(responseCode = "400", description = "Invalid platform specified."),
        @ApiResponse(responseCode = "404", description = "Token not found."),
      })
  @GetMapping(value = "/api/agent/installer/veriguard/{platform}/{installationMode}/{token}")
  @RBAC(skipRBAC = true)
  public @ResponseBody ResponseEntity<String> getVeriguardAgentInstaller(
      @Parameter(
              description =
                  "Target platform for the agent installation (e.g., windows, linux, mac). Case insensitive.",
              required = true)
          @PathVariable
          String platform,
      @Parameter(
              description = "Unique token associated with the agent installation.",
              required = true)
          @PathVariable
          String token,
      @Parameter(
              description = "Installation Mode: session, user or system service",
              required = true)
          @PathVariable
          String installationMode,
      @Parameter(description = "Installation directory") @RequestParam(required = false)
          String installationDir,
      @Parameter(description = "Service name") @RequestParam(required = false) String serviceName)
      throws IOException {
    platform = Optional.ofNullable(platform).map(String::toLowerCase).orElse("");

    if (!AVAILABLE_PLATFORMS.contains(platform)) {
      throw new IllegalArgumentException("Platform invalid : " + platform);
    }
    Optional<Token> resolvedToken = tokenRepository.findByValue(token);
    if (resolvedToken.isEmpty()) {
      throw new UnsupportedOperationException("Invalid token");
    }
    String installCommand =
        this.endpointService.generateInstallCommand(
            platform, token, installationMode, installationDir, serviceName);
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(installCommand);
  }
}
