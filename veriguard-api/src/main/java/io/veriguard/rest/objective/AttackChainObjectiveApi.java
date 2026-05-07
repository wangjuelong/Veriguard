package io.veriguard.rest.objective;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.helper.DatabaseHelper.resolveRelation;
import static java.time.Instant.now;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainRepository;
import io.veriguard.database.repository.EvaluationRepository;
import io.veriguard.database.repository.ObjectiveRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.database.specification.EvaluationSpecification;
import io.veriguard.database.specification.ObjectiveSpecification;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.objective.form.EvaluationInput;
import io.veriguard.rest.objective.form.ObjectiveInput;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AttackChainObjectiveApi extends RestBehavior {

  public static final String SCENARIO_URI = "/api/attack_chains/";

  private final AttackChainRepository attackChainRepository;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final UserRepository userRepository;

  // region objectives
  @GetMapping(SCENARIO_URI + "{scenarioId}/objectives")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Objective> getMainObjectives(@PathVariable String attackChainId) {
    return objectiveRepository.findAll(ObjectiveSpecification.fromAttackChain(attackChainId));
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/objectives")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Objective createObjective(
      @PathVariable String attackChainId, @Valid @RequestBody ObjectiveInput input) {
    AttackChain attackChain =
        attackChainRepository.findById(attackChainId).orElseThrow(ElementNotFoundException::new);
    Objective objective = new Objective();
    objective.setUpdateAttributes(input);
    objective.setAttackChain(attackChain);
    return objectiveRepository.save(objective);
  }

  @PutMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Objective updateObjective(
      @PathVariable String attackChainId,
      @PathVariable String objectiveId,
      @Valid @RequestBody ObjectiveInput input) {
    Objective objective =
        objectiveRepository.findById(objectiveId).orElseThrow(ElementNotFoundException::new);
    objective.setUpdateAttributes(input);
    return objectiveRepository.save(objective);
  }

  @DeleteMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteObjective(
      @PathVariable String attackChainId, @PathVariable String objectiveId) {
    objectiveRepository.deleteById(objectiveId);
  }

  // endregion

  // region evaluations
  @GetMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Evaluation getEvaluation(
      @PathVariable String attackChainId, @PathVariable String evaluationId) {
    return evaluationRepository.findById(evaluationId).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Evaluation> getEvaluations(
      @PathVariable String attackChainId, @PathVariable String objectiveId) {
    return evaluationRepository.findAll(EvaluationSpecification.fromObjective(objectiveId));
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Evaluation createEvaluation(
      @PathVariable String attackChainId,
      @PathVariable String objectiveId,
      @Valid @RequestBody EvaluationInput input) {
    Evaluation evaluation = new Evaluation();
    evaluation.setUpdateAttributes(input);
    Objective objective = resolveRelation(objectiveId, objectiveRepository);
    evaluation.setObjective(objective);
    evaluation.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    Evaluation result = evaluationRepository.save(evaluation);
    objective.setUpdatedAt(now());
    objectiveRepository.save(objective);
    AttackChain attackChain =
        attackChainRepository.findById(attackChainId).orElseThrow(ElementNotFoundException::new);
    attackChain.setUpdatedAt(now());
    attackChainRepository.save(attackChain);
    return result;
  }

  @PutMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Evaluation updateEvaluation(
      @PathVariable String attackChainId,
      @PathVariable String objectiveId,
      @PathVariable String evaluationId,
      @Valid @RequestBody EvaluationInput input) {
    Evaluation evaluation =
        evaluationRepository.findById(evaluationId).orElseThrow(ElementNotFoundException::new);
    evaluation.setUpdateAttributes(input);
    Evaluation result = evaluationRepository.save(evaluation);
    Objective objective =
        objectiveRepository.findById(objectiveId).orElseThrow(ElementNotFoundException::new);
    objective.setUpdatedAt(now());
    objectiveRepository.save(objective);
    AttackChain attackChain =
        attackChainRepository.findById(attackChainId).orElseThrow(ElementNotFoundException::new);
    attackChain.setUpdatedAt(now());
    attackChainRepository.save(attackChain);
    return result;
  }

  @DeleteMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteEvaluation(
      @PathVariable String attackChainId, @PathVariable String evaluationId) {
    evaluationRepository.deleteById(evaluationId);
  }
  // endregion
}
