package io.veriguard.rest.attack_chain_node;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.aop.lock.Lock;
import io.veriguard.aop.lock.LockResourceType;
import io.veriguard.config.VeriguardConfig;
import io.veriguard.config.RabbitMQSslConfiguration;
import io.veriguard.config.RabbitmqConfig;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawDocument;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.database.specification.AttackChainNodeSpecification;
import io.veriguard.database.specification.SpecificationUtils;
import io.veriguard.rest.atomic_testing.form.ExecutionTraceOutput;
import io.veriguard.rest.atomic_testing.form.AttackChainNodeStatusOutput;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.attack_chain_run.exports.ExportOptions;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.helper.queue.BatchQueueService;
import io.veriguard.rest.helper.queue.executor.BatchExecutionTraceExecutor;
import io.veriguard.rest.attack_chain_node.form.*;
import io.veriguard.rest.attack_chain_node.service.BatchingAttackChainNodeStatusService;
import io.veriguard.rest.attack_chain_node.service.ExecutableNodeService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeExecutionService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeExportService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.payload.form.DetectionRemediationOutput;
import io.veriguard.rest.settings.PreviewFeature;
import io.veriguard.service.PreviewFeatureService;
import io.veriguard.service.UserService;
import io.veriguard.service.targets.TargetService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.mapper.PayloadMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@Setter
public class AttackChainNodeApi extends RestBehavior {

  public static final String INJECT_URI = "/api/injects";

  private static final int MAX_NEXT_INJECTS = 6;

  private final ExecutableNodeService executableAttackChainNodeService;
  private final AttackChainRunRepository attackChainRunRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AttackChainNodeService attackChainNodeService;
  private final AttackChainNodeExecutionService attackChainNodeExecutionService;
  private final AttackChainNodeExportService attackChainNodeExportService;
  private final TargetService targetService;
  private final UserRepository userRepository;
  private final PayloadMapper payloadMapper;
  private final UserService userService;
  private final DocumentService documentService;
  private final BatchExecutionTraceExecutor batchExecutionTraceExecutor;
  private final BatchingAttackChainNodeStatusService batchingAttackChainNodeStatusService;

  private final RabbitmqConfig rabbitmqConfig;
  private final VeriguardConfig veriguardConfig;
  private final ObjectMapper objectMapper;
  private final RabbitMQSslConfiguration rabbitMQSslConfiguration;

  private final PreviewFeatureService previewFeatureService;

  // For testing purpose, we add a setter
  @Setter private BatchQueueService<AttackChainNodeExecutionCallback> attackChainNodeTraceQueueService;

  @PostConstruct
  public void init() throws IOException, TimeoutException {
    if (veriguardConfig.getQueueConfig().get("inject-trace") != null) {
      // Initializing the queue for batching the attackChainNode execution trace
      attackChainNodeTraceQueueService =
          new BatchQueueService<>(
              AttackChainNodeExecutionCallback.class,
              batchExecutionTraceExecutor::handleAttackChainNodeExecutionCallbackList,
              rabbitmqConfig,
              objectMapper,
              veriguardConfig.getQueueConfig().get("inject-trace"),
              rabbitMQSslConfiguration);
      // Share the queue with the batching service so it can requeue delayed callbacks
      batchingAttackChainNodeStatusService.setAttackChainNodeTraceQueueService(attackChainNodeTraceQueueService);
    }
  }

  // -- INJECTS --

  @GetMapping(INJECT_URI + "/{injectId}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public AttackChainNode attackChainNode(@PathVariable @NotBlank final String attackChainNodeId) {
    return this.attackChainNodeRepository.findById(attackChainNodeId).orElseThrow(ElementNotFoundException::new);
  }

  @LogExecutionTime
  @PostMapping(INJECT_URI + "/search/export")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  public void attackChainNodesExportFromSearch(
      @RequestBody @Valid AttackChainNodeExportFromSearchRequestInput input, HttpServletResponse response)
      throws IOException {

    // Control and format inputs
    List<AttackChainNode> attackChainNodes =
        getAttackChainNodesAndCheckInputForBulkProcessing(input, Grant.GRANT_TYPE.OBSERVER);

    if (attackChainNodes.isEmpty()) {
      throw new ElementNotFoundException("No injects to export");
    }

    runAttackChainNodeExport(
        attackChainNodes,
        ExportOptions.mask(
            input.getExportOptions().isWithPlayers(),
            input.getExportOptions().isWithTeams(),
            input.getExportOptions().isWithVariableValues()),
        response);
  }

  @PostMapping(INJECT_URI + "/export")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  public void attackChainNodesExport(
      @RequestBody @Valid final AttackChainNodeExportRequestInput attackChainNodeExportRequestInput,
      HttpServletResponse response)
      throws IOException {
    List<String> targetIds = attackChainNodeExportRequestInput.getTargetsIds();
    User currentUser = userService.currentUser();
    List<AttackChainNode> attackChainNodes =
        attackChainNodeRepository.findAll(
            Specification.where(SpecificationUtils.<AttackChainNode>hasIdIn(targetIds))
                .and(
                    SpecificationUtils.hasGrantAccess(
                        currentUser.getId(),
                        currentUser.isAdminOrBypass(),
                        currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT),
                        Grant.GRANT_TYPE.OBSERVER)));
    List<String> foundIds = attackChainNodes.stream().map(AttackChainNode::getId).toList();
    List<String> missedIds =
        new ArrayList<>(targetIds.stream().filter(id -> !foundIds.contains(id)).toList());

    if (!missedIds.isEmpty()) {
      throw new ElementNotFoundException(String.join(", ", missedIds));
    }

    int exportOptionsMask =
        ExportOptions.mask(
            attackChainNodeExportRequestInput.getExportOptions().isWithPlayers(),
            attackChainNodeExportRequestInput.getExportOptions().isWithTeams(),
            attackChainNodeExportRequestInput.getExportOptions().isWithVariableValues());
    runAttackChainNodeExport(attackChainNodes, exportOptionsMask, response);
  }

  @Operation(summary = "Export an inject")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Inject exported successfully"),
        @ApiResponse(responseCode = "404", description = "The inject was not found")
      })
  @PostMapping(INJECT_URI + "/{injectId}/inject_export")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public void attackChainNodesIndividualExport(
      @PathVariable @NotBlank final String attackChainNodeId,
      @RequestBody @Valid
          final AttackChainNodeIndividualExportRequestInput attackChainNodeIndividualExportRequestInput,
      HttpServletResponse response)
      throws IOException {

    AttackChainNode attackChainNode = attackChainNodeRepository.findById(attackChainNodeId).orElseThrow(ElementNotFoundException::new);
    int exportOptionsMask =
        ExportOptions.mask(
            attackChainNodeIndividualExportRequestInput.getExportOptions().isWithPlayers(),
            attackChainNodeIndividualExportRequestInput.getExportOptions().isWithTeams(),
            attackChainNodeIndividualExportRequestInput.getExportOptions().isWithVariableValues());
    runAttackChainNodeExport(List.of(attackChainNode), exportOptionsMask, response);
  }

  private void runAttackChainNodeExport(
      List<AttackChainNode> attackChainNodes, int exportOptionsMask, HttpServletResponse response)
      throws IOException {
    byte[] zippedExport = attackChainNodeExportService.exportAttackChainNodesToZip(attackChainNodes, exportOptionsMask);
    String zipName = attackChainNodeExportService.getZipFileName(exportOptionsMask);

    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
    response.setStatus(HttpServletResponse.SC_OK);
    ServletOutputStream outputStream = response.getOutputStream();
    outputStream.write(zippedExport);
    outputStream.close();
  }

  /**
   * Returns a page of attackChainNode target results based on search parameters
   *
   * @param attackChainNodeId ID of the attackChainNode owning the targets
   * @param targetType Type of the searched targets
   * @param input Search terms specification
   */
  @Operation(summary = "Search inject targets by inject and by target type")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "A page of inject target results is fetched successfully"),
        @ApiResponse(responseCode = "404", description = "The inject ID was not found"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @LogExecutionTime
  @PostMapping(path = INJECT_URI + "/{injectId}/targets/{targetType}/search")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Page<AttackChainNodeTarget> attackChainNodeTargetSearch(
      @PathVariable String attackChainNodeId,
      @PathVariable String targetType,
      @Valid @RequestBody SearchPaginationInput input) {
    TargetType attackChainNodeTargetTypeEnum;

    try {
      attackChainNodeTargetTypeEnum = TargetType.valueOf(targetType);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format("Invalid target type %s", targetType));
    }

    AttackChainNode attackChainNode = attackChainNodeService.attackChainNode(attackChainNodeId);

    return targetService.searchTargets(attackChainNodeTargetTypeEnum, attackChainNode, input);
  }

  /**
   * Returns all possible filter value options for the given target type and attackChainNode
   *
   * @param attackChainNodeId ID of the attackChainNode owning the potential options
   * @param targetType Type of the desired targets as options
   * @param searchText Additional filter on target label
   */
  @Operation(summary = "Get filter values options from possible targets by target type and inject")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Target as option values fetched successfully"),
        @ApiResponse(responseCode = "404", description = "The inject ID was not found"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @LogExecutionTime
  @GetMapping(path = INJECT_URI + "/{injectId}/targets/{targetType}/options")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<FilterUtilsJpa.Option> targetOptions(
      @PathVariable String attackChainNodeId,
      @PathVariable String targetType,
      @RequestParam(required = false) final String searchText) {
    TargetType attackChainNodeTargetTypeEnum;

    try {
      attackChainNodeTargetTypeEnum = TargetType.valueOf(targetType);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format("Invalid target type %s", targetType));
    }

    AttackChainNode attackChainNode = attackChainNodeService.attackChainNode(attackChainNodeId);

    return targetService.getTargetOptions(
        attackChainNodeTargetTypeEnum, attackChainNode, StringUtils.trimToEmpty(searchText));
  }

  /**
   * Returns possible filter value options for the given target ids
   *
   * @param targetType Type of the desired targets as options
   * @param ids IDs of the target options to fetch
   */
  @Operation(summary = "Get filter values options from possible targets by IDs")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Target as option values fetched successfully"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @LogExecutionTime
  @PostMapping(path = INJECT_URI + "/targets/{targetType}/options")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<FilterUtilsJpa.Option> targetOptionsById(
      @PathVariable String targetType, @RequestBody final List<String> ids) {
    TargetType attackChainNodeTargetTypeEnum;

    try {
      attackChainNodeTargetTypeEnum = TargetType.valueOf(targetType);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format("Invalid target type %s", targetType));
    }
    return targetService.getTargetOptionsByIds(attackChainNodeTargetTypeEnum, ids);
  }

  @PostMapping(INJECT_URI + "/execution/reception/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode attackChainNodeExecutionReception(
      @PathVariable String attackChainNodeId, @Valid @RequestBody AttackChainNodeReceptionInput input) {
    AttackChainNode attackChainNode = attackChainNodeRepository.findById(attackChainNodeId).orElseThrow(ElementNotFoundException::new);
    AttackChainNodeStatus attackChainNodeStatus = attackChainNode.getStatus().orElseThrow(ElementNotFoundException::new);
    attackChainNodeStatus.setName(ExecutionStatus.PENDING);
    return attackChainNodeRepository.save(attackChainNode);
  }

  @PostMapping(INJECT_URI + "/execution/callback/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void attackChainNodeExecutionCallback(
      @PathVariable String attackChainNodeId, @Valid @RequestBody AttackChainNodeExecutionInput input)
      throws IOException {
    attackChainNodeExecutionCallback(null, attackChainNodeId, input);
  }

  @PostMapping(INJECT_URI + "/execution/{agentId}/callback/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  @Lock(type = LockResourceType.INJECT, key = "#injectId")
  @Operation(
      summary = "Inject execution callback for implants",
      description =
          "This endpoint is invoked by implants to report the result of an inject execution. "
              + "It is used to update the inject status and execution traces based on the implant's execution result."
              + " If the requested action is 'complete', the inject must be in the 'PENDING' state. otherwise a 409"
              + " is returned. This can sometimes happen if the payload executed by the implant was executed too quickly. ")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Execution callback was successful"),
        @ApiResponse(
            responseCode = "409",
            description =
                "The inject to update was not in a valid state in regards to the requested action. Retry in a few seconds."),
      })
  public void attackChainNodeExecutionCallback(
      @PathVariable
          String agentId, // must allow null because http nodeExecutor used also this method to work.
      @PathVariable String attackChainNodeId,
      @Valid @RequestBody AttackChainNodeExecutionInput input)
      throws IOException {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.LEGACY_INGESTION_EXECUTION_TRACE)
        && attackChainNodeTraceQueueService != null) {
      AttackChainNodeExecutionCallback attackChainNodeExecutionCallback =
          AttackChainNodeExecutionCallback.builder()
              .attackChainNodeExecutionInput(input)
              .agentId(agentId)
              .attackChainNodeId(attackChainNodeId)
              .emissionDate(Instant.now().toEpochMilli())
              .build();

      // Publishing the parameters into a queue for later ingestion
      attackChainNodeTraceQueueService.publish(attackChainNodeExecutionCallback);
    } else {
      attackChainNodeExecutionService.handleAttackChainNodeExecutionCallback(attackChainNodeId, agentId, input);
    }
  }

  @GetMapping(INJECT_URI + "/{injectId}/{agentId}/executable-payload")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @Operation(
      summary = "Get the payload ready to be executed",
      description =
          "This endpoint is invoked by implants to retrieve a payload command that's pre-configured and ready for execution.")
  public Payload getExecutablePayloadAttackChainNode(
      @PathVariable @NotBlank final String attackChainNodeId, @PathVariable @NotBlank final String agentId)
      throws Exception {
    return executableAttackChainNodeService.getExecutablePayloadAndUpdateAttackChainNodeStatus(attackChainNodeId, agentId);
  }

  // -- EXERCISES --

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(INJECT_URI + "/{exerciseId}/{injectId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public AttackChainNode updateAttackChainNode(
      @PathVariable String attackChainRunId,
      @PathVariable String attackChainNodeId,
      @Valid @RequestBody AttackChainNodeInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow(ElementNotFoundException::new);
    AttackChainNode attackChainNode = attackChainNodeService.updateAttackChainNode(attackChainNodeId, input);

    // It should not be possible to add a EE executor on attackChainNode when the attackChainRun is already
    // started.
    if (attackChainRun.getStart().isPresent()) {
      this.attackChainNodeService.throwIfAttackChainNodeNotLaunchable(attackChainNode);
    }

    // If Documents not yet linked directly to the attackChainRun, attached it
    attackChainNode
        .getDocuments()
        .forEach(
            document -> {
              if (!document.getDocument().getAttackChainRuns().contains(attackChainRun)) {
                attackChainRun.getDocuments().add(document.getDocument());
              }
            });
    this.attackChainRunRepository.save(attackChainRun);
    return attackChainNodeRepository.save(attackChainNode);
  }

  @GetMapping(INJECT_URI + "/next")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  public List<AttackChainNode> nextAttackChainNodesToExecute(@RequestParam Optional<Integer> size) {
    return attackChainNodeRepository.findAll(AttackChainNodeSpecification.next()).stream()
        // Keep only attackChainNodes visible by the user
        .filter(attackChainNode -> attackChainNode.getDate().isPresent())
        .filter(
            attackChainNode ->
                attackChainNode
                    .getAttackChainRun()
                    .isUserHasAccess(
                        userRepository
                            .findById(currentUser().getId())
                            .orElseThrow(
                                () -> new ElementNotFoundException("Current user not found"))))
        // Order by near execution
        .sorted(AttackChainNode.executionComparator)
        // Keep only the expected size
        .limit(size.orElse(MAX_NEXT_INJECTS))
        // Collect the result
        .toList();
  }

  @Operation(
      description = "Bulk update of injects",
      tags = {"Injects"})
  @Transactional(rollbackFor = Exception.class)
  @PutMapping(INJECT_URI)
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.INJECT)
  @LogExecutionTime
  public List<AttackChainNode> bulkUpdateAttackChainNode(@RequestBody @Valid final AttackChainNodeBulkUpdateInputs input) {

    // Control and format inputs
    List<AttackChainNode> attackChainNodesToUpdate =
        getAttackChainNodesAndCheckInputForBulkProcessing(input, Grant.GRANT_TYPE.PLANNER);

    // Bulk update
    return this.attackChainNodeService.bulkUpdateAttackChainNode(attackChainNodesToUpdate, input.getUpdateOperations());
  }

  @Operation(
      description = "Bulk delete of injects",
      tags = {"injects-api"})
  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(INJECT_URI)
  @RBAC(actionPerformed = Action.DELETE, resourceType = ResourceType.INJECT)
  @LogExecutionTime
  public List<AttackChainNode> bulkDelete(@RequestBody @Valid final AttackChainNodeBulkProcessingInput input) {

    // Control and format inputs
    List<AttackChainNode> attackChainNodesToDelete =
        getAttackChainNodesAndCheckInputForBulkProcessing(input, Grant.GRANT_TYPE.PLANNER);

    // Bulk delete
    this.attackChainNodeService.deleteAllByIds(attackChainNodesToDelete.stream().map(AttackChainNode::getId).toList());
    return attackChainNodesToDelete;
  }

  // -- OPTION --

  @GetMapping(INJECT_URI + "/findings/options")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<FilterUtilsJpa.Option> optionsByTitleLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return attackChainNodeService.getOptionsByNameLinkedToFindings(
        searchText, sourceId, PageRequest.of(0, 50));
  }

  @PostMapping(INJECT_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.attackChainNodeRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getTitle()))
        .toList();
  }

  /**
   * Retrieve attackChainNodes that match the search input and check that the user is allowed to bulk process
   * them
   *
   * @param input The input for the bulk processing
   * @return The list of attackChainNodes to process
   * @throws BadRequestException If the input is not correctly formatted
   */
  private List<AttackChainNode> getAttackChainNodesAndCheckInputForBulkProcessing(
      AttackChainNodeBulkProcessingInput input, Grant.GRANT_TYPE requested_grant_level) {
    // Control and format inputs
    if ((CollectionUtils.isEmpty(input.getAttackChainNodeIDsToProcess())
            && (input.getSearchPaginationInput() == null))
        || (!CollectionUtils.isEmpty(input.getAttackChainNodeIDsToProcess())
            && (input.getSearchPaginationInput() != null))) {
      throw new BadRequestException(
          "Either inject_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }

    // Retrieve attackChainNodes that match the search input and check that the user is allowed to bulk
    // process them
    return this.attackChainNodeService.getAttackChainNodesAndCheckPermission(input, requested_grant_level);
  }

  // -- Execution Traces
  @Operation(
      description =
          "Get ExecutionTraces from a specific inject and target (asset, agent, team, player)")
  @GetMapping(INJECT_URI + "/execution-traces")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @LogExecutionTime
  public List<ExecutionTraceOutput> getAttackChainNodeTracesFromAttackChainNodeAndTarget(
      @RequestParam String attackChainNodeId,
      @RequestParam String targetId,
      @RequestParam TargetType targetType) {
    return this.attackChainNodeService.getAttackChainNodeTracesOutputFromAttackChainNodeAndTarget(
        attackChainNodeId, targetId, targetType);
  }

  @Operation(description = "Get InjectStatus with global execution traces")
  @GetMapping(INJECT_URI + "/status")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @LogExecutionTime
  public AttackChainNodeStatusOutput getAttackChainNodeStatusWithGlobalExecutionTraces(
      @RequestParam String attackChainNodeId) {
    return this.attackChainNodeService.getAttackChainNodeStatusWithGlobalExecutionTraces(attackChainNodeId);
  }

  @Operation(description = "Get detection remediation by inject based on the payload definition")
  @GetMapping(INJECT_URI + "/detection-remediations/{injectId}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<DetectionRemediationOutput> getPayloadDetectionRemediations(
      @PathVariable String attackChainNodeId) {
    return payloadMapper.toDetectionRemediationOutputs(
        attackChainNodeService.fetchDetectionRemediationsByAttackChainNodeId(attackChainNodeId));
  }

  @Operation(description = "Get documents by inject and payload id")
  @GetMapping(INJECT_URI + "/{injectId}/payload/{payloadId}/documents")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<RawDocument> getPayloadDocumentsByAttackChainNodeIdAndPayloadId(
      @PathVariable String attackChainNodeId, @PathVariable String payloadId) {
    Payload payload = attackChainNodeService.getPayloadByAttackChainNodeId(attackChainNodeId);

    if (!payloadId.equals(payload.getId())) {
      throw new BadRequestException("provided payload id mismatch with provided inject id");
    }

    return documentService.documentsForPayload(payloadId);
  }

  @VisibleForTesting
  public BatchQueueService<AttackChainNodeExecutionCallback> getAttackChainNodeTraceQueueService() {
    return attackChainNodeTraceQueueService;
  }
}
