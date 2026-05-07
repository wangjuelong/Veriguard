package io.veriguard.rest.objective;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.helper.DatabaseHelper.resolveRelation;
import static java.time.Instant.now;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainRunRepository;
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
public class AttackChainRunObjectiveApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/attack_chain_runs/";

  private final AttackChainRunRepository attackChainRunRepository;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final UserRepository userRepository;

  // region objectives
  @GetMapping(EXERCISE_URI + "{attackChainRunId}/objectives")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Objective> getMainObjectives(@PathVariable String attackChainRunId) {
    return objectiveRepository.findAll(ObjectiveSpecification.fromAttackChainRun(attackChainRunId));
  }

  @PostMapping(EXERCISE_URI + "{attackChainRunId}/objectives")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Objective createObjective(
      @PathVariable String attackChainRunId, @Valid @RequestBody ObjectiveInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    Objective objective = new Objective();
    objective.setUpdateAttributes(input);
    objective.setAttackChainRun(attackChainRun);
    return objectiveRepository.save(objective);
  }

  @PutMapping(EXERCISE_URI + "{attackChainRunId}/objectives/{objectiveId}")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Objective updateObjective(
      @PathVariable String attackChainRunId,
      @PathVariable String objectiveId,
      @Valid @RequestBody ObjectiveInput input) {
    Objective objective =
        objectiveRepository.findById(objectiveId).orElseThrow(ElementNotFoundException::new);
    objective.setUpdateAttributes(input);
    return objectiveRepository.save(objective);
  }

  @DeleteMapping(EXERCISE_URI + "{attackChainRunId}/objectives/{objectiveId}")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteObjective(
      @PathVariable String attackChainRunId, @PathVariable String objectiveId) {
    objectiveRepository.deleteById(objectiveId);
  }

  // endregion

  // region evaluations
  @GetMapping(EXERCISE_URI + "{attackChainRunId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Evaluation getEvaluation(
      @PathVariable String attackChainRunId, @PathVariable String evaluationId) {
    return evaluationRepository.findById(evaluationId).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping(EXERCISE_URI + "{attackChainRunId}/objectives/{objectiveId}/evaluations")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Evaluation> getEvaluations(
      @PathVariable String attackChainRunId, @PathVariable String objectiveId) {
    return evaluationRepository.findAll(EvaluationSpecification.fromObjective(objectiveId));
  }

  @PostMapping(EXERCISE_URI + "{attackChainRunId}/objectives/{objectiveId}/evaluations")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Evaluation createEvaluation(
      @PathVariable String attackChainRunId,
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
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    attackChainRun.setUpdatedAt(now());
    attackChainRunRepository.save(attackChainRun);
    return result;
  }

  @PutMapping(EXERCISE_URI + "{attackChainRunId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Evaluation updateEvaluation(
      @PathVariable String attackChainRunId,
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
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    attackChainRun.setUpdatedAt(now());
    attackChainRunRepository.save(attackChainRun);
    return result;
  }

  @DeleteMapping(EXERCISE_URI + "{attackChainRunId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteEvaluation(
      @PathVariable String attackChainRunId, @PathVariable String evaluationId) {
    evaluationRepository.deleteById(evaluationId);
  }
  // endregion
}
