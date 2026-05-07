package io.veriguard.rest.inject;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.specification.AttackChainNodeSpecification.fromSimulation;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.rest.exercise.AttackChainRunApi.EXERCISE_URI;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.execution.ExecutionContext;
import io.veriguard.execution.ExecutionContextService;
import io.veriguard.executors.Executor;
import io.veriguard.rest.atomic_testing.form.AttackChainNodeResultOutput;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.inject.form.*;
import io.veriguard.rest.inject.output.AttackChainNodeOutput;
import io.veriguard.rest.inject.service.AttackChainNodeDuplicateService;
import io.veriguard.rest.inject.service.AttackChainNodeService;
import io.veriguard.rest.inject.service.AttackChainNodeStatusService;
import io.veriguard.rest.inject.service.SimulationAttackChainNodeService;
import io.veriguard.service.AttackChainNodeSearchService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.criteria.Join;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SimulationAttackChainNodeApi extends RestBehavior {

  private final AttackChainNodeSearchService attackChainNodeSearchService;
  private final Executor executor;
  private final NodeContractRepository nodeContractRepository;
  private final AttackChainRunRepository attackChainRunRepository;
  private final UserRepository userRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final ExecutionContextService executionContextService;
  private final AttackChainNodeService attackChainNodeService;
  private final AttackChainNodeDuplicateService attackChainNodeDuplicateService;
  private final AttackChainNodeStatusService attackChainNodeStatusService;
  private final SimulationAttackChainNodeService simulationAttackChainNodeService;

  // -- READ --

  @Operation(summary = "Retrieved injects for an exercise")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Retrieved injects for an exercise",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = AttackChainNodeOutput.class))
            }),
      })
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/simple")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Iterable<AttackChainNodeOutput> attackChainRunAttackChainNodesSimple(
      @PathVariable @NotBlank final String attackChainRunId) {
    return attackChainNodeSearchService.attackChainNodes(fromSimulation(attackChainRunId));
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/simple")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Iterable<AttackChainNodeOutput> attackChainRunAttackChainNodesSimple(
      @PathVariable @NotBlank final String attackChainRunId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<AttackChainNode> specification,
            Specification<AttackChainNode> specificationCount,
            Pageable pageable) ->
            this.attackChainNodeSearchService.attackChainNodes(
                fromSimulation(attackChainRunId).and(specification),
                fromSimulation(attackChainRunId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        AttackChainNode.class,
        joinMap);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<AttackChainNode> attackChainRunAttackChainNodes(@PathVariable @NotBlank final String attackChainRunId) {
    return attackChainNodeRepository.findByAttackChainRunId(attackChainRunId).stream()
        .sorted(AttackChainNode.executionComparator)
        .toList();
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/search")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Page<AttackChainNodeResultOutput> searchAttackChainRunAttackChainNodes(
      @PathVariable final String attackChainRunId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return attackChainNodeSearchService.getPageOfAttackChainNodeResults(attackChainRunId, searchPaginationInput);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/results")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public List<AttackChainNodeResultOutput> attackChainRunAttackChainNodesResults(@PathVariable final String attackChainRunId) {
    return attackChainNodeSearchService.getListOfAttackChainNodeResults(attackChainRunId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public AttackChainNode attackChainRunAttackChainNode(@PathVariable String attackChainRunId, @PathVariable String attackChainNodeId) {
    return simulationAttackChainNodeService.findAttackChainNodeForSimulation(attackChainRunId, attackChainNodeId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Iterable<Team> attackChainRunAttackChainNodeTeams(
      @PathVariable String attackChainRunId, @PathVariable String attackChainNodeId) {
    return simulationAttackChainNodeService.findAttackChainNodeTeamsForSimulation(attackChainRunId, attackChainNodeId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/communications")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Iterable<Communication> attackChainRunAttackChainNodeCommunications(
      @PathVariable String attackChainRunId, @PathVariable String attackChainNodeId) {
    return simulationAttackChainNodeService.findAndAckCommunicationsForSimulation(attackChainRunId, attackChainNodeId);
  }

  // -- CREATE --

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainNode createAttackChainNodeForAttackChainRun(
      @PathVariable String attackChainRunId, @Valid @RequestBody AttackChainNodeInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow(ElementNotFoundException::new);
    return this.attackChainNodeService.createAndSaveAttackChainNode(attackChainRun, null, input);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/bulk")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public List<AttackChainNode> createAttackChainNodesForAttackChainRun(
      @PathVariable String attackChainRunId, @Valid @RequestBody List<AttackChainNodeInput> inputs) {
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow(ElementNotFoundException::new);
    return this.attackChainNodeService.createAndSaveAttackChainNodeList(attackChainRun, null, inputs);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode duplicateAttackChainNodeForAttackChainRun(
      @PathVariable @NotBlank final String attackChainRunId,
      @PathVariable @NotBlank final String attackChainNodeId) {
    return attackChainNodeDuplicateService.duplicateAttackChainNodeForAttackChainRunWithDuplicateWordInTitle(
        attackChainRunId, attackChainNodeId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PostMapping(value = EXERCISE_URI + "/{exerciseId}/inject")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SIMULATION)
  public AttackChainNodeStatus executeAttackChainNode(
      @PathVariable @NotBlank final String attackChainRunId,
      @Valid @RequestPart("input") DirectAttackChainNodeInput input,
      @RequestPart("file") Optional<MultipartFile> file) {
    AttackChainNode attackChainNode =
        input.toAttackChainNode(
            this.nodeContractRepository
                .findById(input.getNodeContract())
                .orElseThrow(() -> new ElementNotFoundException("Injector contract not found")));
    attackChainNode.setUser(
        this.userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    attackChainNode.setAttackChainRun(
        this.attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(() -> new ElementNotFoundException("Exercise not found")));
    attackChainNode.setDependsDuration(0L);
    AttackChainNode savedAttackChainNode = this.attackChainNodeRepository.save(attackChainNode);
    Iterable<User> users = this.userRepository.findAllById(input.getUserIds());
    List<ExecutionContext> userAttackChainNodeContexts =
        fromIterable(users).stream()
            .map(
                user ->
                    this.executionContextService.executionContext(
                        user, savedAttackChainNode, "Direct execution"))
            .collect(Collectors.toList());
    ExecutableNode injection =
        new ExecutableNode(
            true,
            true,
            savedAttackChainNode,
            List.of(),
            savedAttackChainNode.getAssets(),
            savedAttackChainNode.getAssetGroups(),
            userAttackChainNodeContexts);
    file.ifPresent(injection::addDirectAttachment);
    try {
      return executor.directExecute(injection);
    } catch (Exception e) {
      log.warn(e.getMessage(), e);
      return attackChainNodeStatusService.failAttackChainNodeStatus(attackChainNode.getId(), e.getMessage());
    }
  }

  // -- UPDATE --

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/activation")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode updateAttackChainNodeActivationForAttackChainRun(
      @PathVariable String attackChainRunId,
      @PathVariable String attackChainNodeId,
      @Valid @RequestBody AttackChainNodeUpdateActivationInput input) {
    return simulationAttackChainNodeService.updateAttackChainNodeActivationForSimulation(attackChainRunId, attackChainNodeId, input);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/trigger")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode updateAttackChainNodeTrigger(
      @PathVariable String attackChainRunId, @PathVariable String attackChainNodeId) {
    return simulationAttackChainNodeService.triggerAttackChainNodeForSimulation(attackChainRunId, attackChainNodeId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/status")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode setAttackChainNodeStatus(
      @PathVariable String attackChainRunId,
      @PathVariable String attackChainNodeId,
      @Valid @RequestBody AttackChainNodeUpdateStatusInput input) {
    return simulationAttackChainNodeService.setAttackChainNodeStatusForSimulation(attackChainRunId, attackChainNodeId, input);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode updateAttackChainNodeTeams(
      @PathVariable String attackChainRunId,
      @PathVariable String attackChainNodeId,
      @Valid @RequestBody AttackChainNodeTeamsInput input) {
    return simulationAttackChainNodeService.updateAttackChainNodeTeamsForSimulation(attackChainRunId, attackChainNodeId, input);
  }

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void deleteAttackChainNode(@PathVariable String attackChainRunId, @PathVariable String attackChainNodeId) {
    this.simulationAttackChainNodeService.deleteAttackChainNode(attackChainRunId, attackChainNodeId);
  }
}
