package io.veriguard.rest.inject;

import static io.veriguard.database.specification.InjectSpecification.fromScenario;
import static io.veriguard.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.inject.form.InjectAssistantInput;
import io.veriguard.rest.inject.form.InjectInput;
import io.veriguard.rest.inject.form.InjectUpdateActivationInput;
import io.veriguard.rest.inject.output.InjectOutput;
import io.veriguard.rest.inject.service.InjectAssistantService;
import io.veriguard.rest.inject.service.InjectDuplicateService;
import io.veriguard.rest.inject.service.InjectService;
import io.veriguard.rest.inject.service.ScenarioInjectService;
import io.veriguard.service.*;
import io.veriguard.service.scenario.ScenarioService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.criteria.Join;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioInjectApi extends RestBehavior {

  private final InjectAssistantService injectAssistantService;
  private final InjectSearchService injectSearchService;
  private final InjectRepository injectRepository;
  private final ScenarioService scenarioService;
  private final InjectService injectService;
  private final InjectDuplicateService injectDuplicateService;
  private final ScenarioInjectService scenarioInjectService;

  // -- READ --

  @GetMapping(SCENARIO_URI + "/{scenarioId}/injects/simple")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> scenarioInjectsSimple(
      @PathVariable @NotBlank final String scenarioId) {
    return injectSearchService.injects(fromScenario(scenarioId));
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/simple")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> scenarioInjectsSimple(
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<Inject> specification,
            Specification<Inject> specificationCount,
            Pageable pageable) ->
            this.injectSearchService.injects(
                fromScenario(scenarioId).and(specification),
                fromScenario(scenarioId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Inject.class,
        joinMap);
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/injects")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Inject> scenarioInjects(@PathVariable @NotBlank final String scenarioId) {
    return this.injectRepository.findByScenarioId(scenarioId).stream()
        .sorted(Inject.executionComparator)
        .toList();
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/injects/{injectId}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Inject scenarioInject(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId) {
    return scenarioInjectService.findInjectForScenario(scenarioId, injectId);
  }

  // -- CREATE --

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public Inject createInjectForScenario(
      @PathVariable @NotBlank final String scenarioId, @Valid @RequestBody InjectInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    return this.injectService.createAndSaveInject(null, scenario, input);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/bulk")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public List<Inject> createInjectsForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody List<InjectInput> inputs) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    return this.injectService.createAndSaveInjectList(null, scenario, inputs);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/assistant")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  @Operation(
      summary = "Assistant to generate injects for scenario",
      description = "Generates injects based on the provided attack pattern and targets.")
  public List<Inject> generateInjectsForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody InjectAssistantInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    return injectService.saveAll(
        this.injectAssistantService.generateInjectsForScenario(scenario, input));
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject duplicateInjectForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId) {
    return injectDuplicateService.duplicateInjectForScenarioWithDuplicateWordInTitle(
        scenarioId, injectId);
  }

  // -- UPDATE --

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/injects/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject updateInjectForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody @NotNull InjectInput input) {
    return scenarioInjectService.updateInjectForScenario(scenarioId, injectId, input);
  }

  @PutMapping(SCENARIO_URI + "/{scenarioId}/injects/{injectId}/activation")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject updateInjectActivationForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody InjectUpdateActivationInput input) {
    return scenarioInjectService.updateInjectActivationForScenario(scenarioId, injectId, input);
  }

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(SCENARIO_URI + "/{scenarioId}/injects/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void deleteInjectForScenario(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String injectId) {
    this.scenarioInjectService.deleteInject(scenarioId, injectId);
  }
}
