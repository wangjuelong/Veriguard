package io.veriguard.rest.atomic_testing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.Collector;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.atomic_testing.form.*;
import io.veriguard.rest.collector.service.CollectorService;
import io.veriguard.rest.exception.UnprocessableContentException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.AtomicTestingService;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.AttackChainNodeImportService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(AtomicTestingApi.ATOMIC_TESTING_URI)
@RequiredArgsConstructor
public class AtomicTestingApi extends RestBehavior {

  public static final String ATOMIC_TESTING_URI = "/api/atomic-testings";

  private final AtomicTestingService atomicTestingService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final CollectorService collectorsService;
  private final AttackChainNodeImportService attackChainNodeImportService;

  @LogExecutionTime
  @PostMapping("/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATOMIC_TESTING)
  @Transactional(readOnly = true)
  public Page<AttackChainNodeResultOutput> findAllAtomicTestings(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return atomicTestingService.searchAtomicTestingsForCurrentUser(searchPaginationInput);
  }

  // some api use attackChainNode as resource type because they are actually used to retrieve
  // attackChainNode data for
  // simulation and AT
  @LogExecutionTime
  @GetMapping("/{attackChainNodeId}")
  @RBAC(resourceId = "#attackChainNodeId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public AttackChainNodeResultOverviewOutput findAtomicTesting(
      @PathVariable String attackChainNodeId) {
    return atomicTestingService.findById(attackChainNodeId);
  }

  @LogExecutionTime
  @GetMapping("/{attackChainNodeId}/payload")
  @RBAC(resourceId = "#attackChainNodeId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public StatusPayloadOutput findAtomicTestingPayload(@PathVariable String attackChainNodeId) {
    return atomicTestingService.findPayloadOutputByAttackChainNodeId(attackChainNodeId);
  }

  @PostMapping()
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ATOMIC_TESTING)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainNodeResultOverviewOutput createAtomicTesting(
      @Valid @RequestBody AtomicTestingInput input) {
    return this.atomicTestingService.createOrUpdate(input, null);
  }

  @PutMapping("/{attackChainNodeId}")
  @RBAC(
      resourceId = "#attackChainNodeId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainNodeResultOverviewOutput updateAtomicTesting(
      @PathVariable @NotBlank final String attackChainNodeId,
      @Valid @RequestBody final AtomicTestingInput input) {
    return atomicTestingService.createOrUpdate(input, attackChainNodeId);
  }

  @DeleteMapping("/{attackChainNodeId}")
  @RBAC(
      resourceId = "#attackChainNodeId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.INJECT)
  public void deleteAtomicTesting(@PathVariable @NotBlank final String attackChainNodeId) {
    atomicTestingService.deleteAtomicTesting(attackChainNodeId);
  }

  @PostMapping("/{atomicTestingId}/duplicate")
  @RBAC(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.ATOMIC_TESTING)
  public AttackChainNodeResultOverviewOutput duplicateAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.duplicate(atomicTestingId);
  }

  @PostMapping("/{atomicTestingId}/launch")
  @RBAC(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  public AttackChainNodeResultOverviewOutput launchAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.launch(atomicTestingId);
  }

  @PostMapping("/{atomicTestingId}/relaunch")
  @RBAC(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  public AttackChainNodeResultOverviewOutput relaunchAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.relaunch(atomicTestingId);
  }

  @GetMapping("/{attackChainNodeId}/target_results/{targetId}/types/{targetType}")
  @RBAC(resourceId = "#attackChainNodeId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<AttackChainNodeExpectation> findTargetResult(
      @PathVariable String attackChainNodeId,
      @PathVariable String targetId,
      @PathVariable String targetType,
      @RequestParam(required = false) String parentTargetId) {
    return attackChainNodeExpectationService
        .findMergedExpectationsByAttackChainNodeAndTargetAndTargetType(
            attackChainNodeId, targetId, parentTargetId, targetType);
  }

  @GetMapping("/{attackChainNodeId}/target_results/{targetId}/asset_with_agents")
  @RBAC(resourceId = "#attackChainNodeId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @Operation(
      summary = "Get the agents injects expectations from an inject, asset and expectation type")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of the agents injects expectations")
      })
  public List<AttackChainNodeExpectationAgentOutput> findTargetResultAssetWithAgents(
      @PathVariable String attackChainNodeId,
      @PathVariable String targetId,
      @RequestParam @NotBlank String expectationType) {
    return attackChainNodeExpectationService
        .findMergedExpectationsWithAgentsByAttackChainNodeAndAsset(
            attackChainNodeId, targetId, expectationType);
  }

  /**
   * Returns expectations for attackChainNode target with results merged across all expectations of
   * the same type
   *
   * @param attackChainNodeId ID of the attackChainNode owning the targets
   * @param targetId ID of the specific target
   * @param targetType Type of the specified target
   */
  @Operation(
      summary =
          "Fetch target expectations with merged results across all occurrences of each expectation type")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Expectation results fetched successfully"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @GetMapping("/{attackChainNodeId}/target_results/{targetId}/types/{targetType}/merged")
  @RBAC(resourceId = "#attackChainNodeId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<AttackChainNodeExpectation> findTargetResultMerged(
      @PathVariable String attackChainNodeId,
      @PathVariable String targetId,
      @PathVariable String targetType) {
    return attackChainNodeExpectationService
        .findMergedExpectationsByAttackChainNodeAndTargetAndTargetType(
            attackChainNodeId, targetId, targetType)
        .stream()
        .sorted(Comparator.comparing(AttackChainNodeExpectation::getType))
        .toList();
  }

  @PutMapping("/{attackChainNodeId}/tags")
  @RBAC(
      resourceId = "#attackChainNodeId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainNodeResultOverviewOutput updateAtomicTestingTags(
      @PathVariable @NotBlank final String attackChainNodeId,
      @Valid @RequestBody final AtomicTestingUpdateTagsInput input) {
    return atomicTestingService.updateAtomicTestingTags(attackChainNodeId, input);
  }

  @GetMapping("/{attackChainNodeId}/collectors")
  @RBAC(resourceId = "#attackChainNodeId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @Operation(summary = "Get the Collectors used in an atomic testing remediation")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Collectors used in an atomic testing remediation")
      })
  public List<Collector> collectorsFromAtomicTesting(@PathVariable String attackChainNodeId) {
    return collectorsService.collectorsForAtomicTesting(attackChainNodeId);
  }

  @PostMapping(
      path = "/import",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.ATOMIC_TESTING)
  public void atomicTestingImport(
      @RequestPart("file") MultipartFile file, HttpServletResponse response) throws Exception {
    if (file == null || file.isEmpty()) {
      throw new UnprocessableContentException("Insufficient input: file is required");
    }

    this.attackChainNodeImportService.importAttackChainNodesForAtomicTestings(file);
  }
}
