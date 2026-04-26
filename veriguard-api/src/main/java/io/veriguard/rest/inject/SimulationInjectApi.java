package io.veriguard.rest.inject;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.specification.InjectSpecification.fromSimulation;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.execution.ExecutableInject;
import io.veriguard.execution.ExecutionContext;
import io.veriguard.execution.ExecutionContextService;
import io.veriguard.executors.Executor;
import io.veriguard.rest.atomic_testing.form.InjectResultOutput;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.inject.form.*;
import io.veriguard.rest.inject.output.InjectOutput;
import io.veriguard.rest.inject.service.InjectDuplicateService;
import io.veriguard.rest.inject.service.InjectService;
import io.veriguard.rest.inject.service.InjectStatusService;
import io.veriguard.rest.inject.service.SimulationInjectService;
import io.veriguard.service.InjectSearchService;
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
public class SimulationInjectApi extends RestBehavior {

  private final InjectSearchService injectSearchService;
  private final Executor executor;
  private final InjectorContractRepository injectorContractRepository;
  private final ExerciseRepository exerciseRepository;
  private final UserRepository userRepository;
  private final InjectRepository injectRepository;
  private final ExecutionContextService executionContextService;
  private final InjectService injectService;
  private final InjectDuplicateService injectDuplicateService;
  private final InjectStatusService injectStatusService;
  private final SimulationInjectService simulationInjectService;

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
                  schema = @Schema(implementation = InjectOutput.class))
            }),
      })
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/simple")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> exerciseInjectsSimple(
      @PathVariable @NotBlank final String exerciseId) {
    return injectSearchService.injects(fromSimulation(exerciseId));
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/simple")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> exerciseInjectsSimple(
      @PathVariable @NotBlank final String exerciseId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<Inject> specification,
            Specification<Inject> specificationCount,
            Pageable pageable) ->
            this.injectSearchService.injects(
                fromSimulation(exerciseId).and(specification),
                fromSimulation(exerciseId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Inject.class,
        joinMap);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Inject> exerciseInjects(@PathVariable @NotBlank final String exerciseId) {
    return injectRepository.findByExerciseId(exerciseId).stream()
        .sorted(Inject.executionComparator)
        .toList();
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/search")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Page<InjectResultOutput> searchExerciseInjects(
      @PathVariable final String exerciseId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return injectSearchService.getPageOfInjectResults(exerciseId, searchPaginationInput);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/results")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public List<InjectResultOutput> exerciseInjectsResults(@PathVariable final String exerciseId) {
    return injectSearchService.getListOfInjectResults(exerciseId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Inject exerciseInject(@PathVariable String exerciseId, @PathVariable String injectId) {
    return simulationInjectService.findInjectForSimulation(exerciseId, injectId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Iterable<Team> exerciseInjectTeams(
      @PathVariable String exerciseId, @PathVariable String injectId) {
    return simulationInjectService.findInjectTeamsForSimulation(exerciseId, injectId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/communications")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Iterable<Communication> exerciseInjectCommunications(
      @PathVariable String exerciseId, @PathVariable String injectId) {
    return simulationInjectService.findAndAckCommunicationsForSimulation(exerciseId, injectId);
  }

  // -- CREATE --

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Inject createInjectForExercise(
      @PathVariable String exerciseId, @Valid @RequestBody InjectInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    return this.injectService.createAndSaveInject(exercise, null, input);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/bulk")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public List<Inject> createInjectsForExercise(
      @PathVariable String exerciseId, @Valid @RequestBody List<InjectInput> inputs) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    return this.injectService.createAndSaveInjectList(exercise, null, inputs);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.INJECT)
  public Inject duplicateInjectForExercise(
      @PathVariable @NotBlank final String exerciseId,
      @PathVariable @NotBlank final String injectId) {
    return injectDuplicateService.duplicateInjectForExerciseWithDuplicateWordInTitle(
        exerciseId, injectId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PostMapping(value = EXERCISE_URI + "/{exerciseId}/inject")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SIMULATION)
  public InjectStatus executeInject(
      @PathVariable @NotBlank final String exerciseId,
      @Valid @RequestPart("input") DirectInjectInput input,
      @RequestPart("file") Optional<MultipartFile> file) {
    Inject inject =
        input.toInject(
            this.injectorContractRepository
                .findById(input.getInjectorContract())
                .orElseThrow(() -> new ElementNotFoundException("Injector contract not found")));
    inject.setUser(
        this.userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    inject.setExercise(
        this.exerciseRepository
            .findById(exerciseId)
            .orElseThrow(() -> new ElementNotFoundException("Exercise not found")));
    inject.setDependsDuration(0L);
    Inject savedInject = this.injectRepository.save(inject);
    Iterable<User> users = this.userRepository.findAllById(input.getUserIds());
    List<ExecutionContext> userInjectContexts =
        fromIterable(users).stream()
            .map(
                user ->
                    this.executionContextService.executionContext(
                        user, savedInject, "Direct execution"))
            .collect(Collectors.toList());
    ExecutableInject injection =
        new ExecutableInject(
            true,
            true,
            savedInject,
            List.of(),
            savedInject.getAssets(),
            savedInject.getAssetGroups(),
            userInjectContexts);
    file.ifPresent(injection::addDirectAttachment);
    try {
      return executor.directExecute(injection);
    } catch (Exception e) {
      log.warn(e.getMessage(), e);
      return injectStatusService.failInjectStatus(inject.getId(), e.getMessage());
    }
  }

  // -- UPDATE --

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/activation")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject updateInjectActivationForExercise(
      @PathVariable String exerciseId,
      @PathVariable String injectId,
      @Valid @RequestBody InjectUpdateActivationInput input) {
    return simulationInjectService.updateInjectActivationForSimulation(exerciseId, injectId, input);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/trigger")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject updateInjectTrigger(
      @PathVariable String exerciseId, @PathVariable String injectId) {
    return simulationInjectService.triggerInjectForSimulation(exerciseId, injectId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/status")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject setInjectStatus(
      @PathVariable String exerciseId,
      @PathVariable String injectId,
      @Valid @RequestBody InjectUpdateStatusInput input) {
    return simulationInjectService.setInjectStatusForSimulation(exerciseId, injectId, input);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject updateInjectTeams(
      @PathVariable String exerciseId,
      @PathVariable String injectId,
      @Valid @RequestBody InjectTeamsInput input) {
    return simulationInjectService.updateInjectTeamsForSimulation(exerciseId, injectId, input);
  }

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void deleteInject(@PathVariable String exerciseId, @PathVariable String injectId) {
    this.simulationInjectService.deleteInject(exerciseId, injectId);
  }
}
