package io.veriguard.rest.exercise;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.InjectExpectation;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.ExerciseExpectationService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ExerciseExpectationApi extends RestBehavior {

  private final ExerciseExpectationService exerciseExpectationService;

  @LogExecutionTime
  @GetMapping(value = "/api/exercises/{exerciseId}/expectations")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<InjectExpectation> exerciseInjectExpectations(
      @PathVariable @NotBlank final String exerciseId) {
    return this.exerciseExpectationService.injectExpectations(exerciseId);
  }
}
