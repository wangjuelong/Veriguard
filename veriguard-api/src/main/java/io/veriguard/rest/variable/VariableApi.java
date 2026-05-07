package io.veriguard.rest.variable;

import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;
import static io.veriguard.rest.attack_chain_run.AttackChainRunApi.EXERCISE_URI;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.variable.form.VariableInput;
import io.veriguard.service.VariableService;
import io.veriguard.service.scenario.AttackChainService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class VariableApi extends RestBehavior {

  public static final String VARIABLE_URI = "/api/variables";

  private final VariableService variableService;
  private final AttackChainService attackChainService;
  private final AttackChainRunRepository attackChainRunRepository;

  // -- EXERCISES --

  @PostMapping(EXERCISE_URI + "/{attackChainRunId}/variables")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Variable createVariableForAttackChainRun(
      @PathVariable @NotBlank final String attackChainRunId,
      @Valid @RequestBody final VariableInput input) {
    Variable variable = new Variable();
    variable.setUpdateAttributes(input);
    AttackChainRun attackChainRun =
        this.attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    variable.setAttackChainRun(attackChainRun);
    return this.variableService.createVariable(variable);
  }

  @GetMapping(EXERCISE_URI + "/{attackChainRunId}/variables")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Variable> variablesFromAttackChainRun(
      @PathVariable @NotBlank final String attackChainRunId) {
    return this.variableService.variablesFromAttackChainRun(attackChainRunId);
  }

  @PutMapping(EXERCISE_URI + "/{attackChainRunId}/variables/{variableId}")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Variable updateVariableForAttackChainRun(
      @PathVariable @NotBlank final String attackChainRunId,
      @PathVariable @NotBlank final String variableId,
      @Valid @RequestBody final VariableInput input) {
    Variable variable = this.variableService.variable(variableId);
    assert attackChainRunId.equals(variable.getAttackChainRun().getId());
    variable.setUpdateAttributes(input);
    return this.variableService.updateVariable(variable);
  }

  @DeleteMapping(EXERCISE_URI + "/{attackChainRunId}/variables/{variableId}")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteVariableForAttackChainRun(
      @PathVariable @NotBlank final String attackChainRunId,
      @PathVariable @NotBlank final String variableId) {
    Variable variable = this.variableService.variable(variableId);
    assert attackChainRunId.equals(variable.getAttackChainRun().getId());
    this.variableService.deleteVariable(variableId);
  }

  // -- SCENARIOS --

  @PostMapping(SCENARIO_URI + "/{attackChainId}/variables")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Variable createVariableForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody final VariableInput input) {
    Variable variable = new Variable();
    variable.setUpdateAttributes(input);
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    variable.setAttackChain(attackChain);
    return this.variableService.createVariable(variable);
  }

  @GetMapping(SCENARIO_URI + "/{attackChainId}/variables")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Variable> variablesFromAttackChain(
      @PathVariable @NotBlank final String attackChainId) {
    return this.variableService.variablesFromAttackChain(attackChainId);
  }

  @PutMapping(SCENARIO_URI + "/{attackChainId}/variables/{variableId}")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Variable updateVariableForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String variableId,
      @Valid @RequestBody final VariableInput input) {
    Variable variable = this.variableService.variable(variableId);
    assert attackChainId.equals(variable.getAttackChain().getId());
    variable.setUpdateAttributes(input);
    return this.variableService.updateVariable(variable);
  }

  @DeleteMapping(SCENARIO_URI + "/{attackChainId}/variables/{variableId}")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteVariableForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String variableId) {
    Variable variable = this.variableService.variable(variableId);
    assert attackChainId.equals(variable.getAttackChain().getId());
    this.variableService.deleteVariable(variableId);
  }
}
