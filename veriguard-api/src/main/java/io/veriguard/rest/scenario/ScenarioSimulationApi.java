package io.veriguard.rest.scenario;

import static io.veriguard.database.specification.ExerciseSpecification.fromScenario;
import static io.veriguard.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.Base;
import io.veriguard.database.model.Exercise;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.exercise.form.ExerciseSimple;
import io.veriguard.rest.exercise.service.ExerciseService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Join;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioSimulationApi {

  private final ExerciseService exerciseService;

  @LogExecutionTime
  @GetMapping(SCENARIO_URI + "/{scenarioId}/exercises")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<ExerciseSimple> scenarioExercises(
      @PathVariable @NotBlank final String scenarioId) {
    return exerciseService.scenarioExercises(scenarioId);
  }

  @LogExecutionTime
  @PostMapping(SCENARIO_URI + "/{scenarioId}/exercises/search")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<ExerciseSimple> scenarioExercises(
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<Exercise> specification,
            Specification<Exercise> specificationCount,
            Pageable pageable) ->
            this.exerciseService.exercisesWithEmptyGlobalScore(
                fromScenario(scenarioId).and(specification),
                fromScenario(scenarioId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Exercise.class,
        joinMap);
  }

  // -- OPTION --

  @GetMapping(SCENARIO_URI + "/{scenarioId}/simulations/options")
  public List<FilterUtilsJpa.Option> optionsByName(
      @PathVariable @NotBlank final String scenarioId,
      @RequestParam(required = false) final String searchText) {
    return this.exerciseService.findAllAsOptions(fromScenario(scenarioId), searchText);
  }
}
