package io.veriguard.rest.exercise;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.AttackChainRunExpectationService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class AttackChainRunExpectationApi extends RestBehavior {

  private final AttackChainRunExpectationService attackChainRunExpectationService;

  @LogExecutionTime
  @GetMapping(value = "/api/exercises/{exerciseId}/expectations")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<AttackChainNodeExpectation> attackChainRunAttackChainNodeExpectations(
      @PathVariable @NotBlank final String attackChainRunId) {
    return this.attackChainRunExpectationService.attackChainNodeExpectations(attackChainRunId);
  }
}
