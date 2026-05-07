package io.veriguard.rest.attack_chain;

import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.attack_chain.response.AttackChainStatistic;
import io.veriguard.rest.attack_chain.service.AttackChainStatisticService;
import io.veriguard.rest.helper.RestBehavior;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttackChainStatisticApi extends RestBehavior {

  private final AttackChainStatisticService attackChainStatisticService;

  @GetMapping(SCENARIO_URI + "/{attackChainId}/statistics")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Retrieve scenario statistics")
  public AttackChainStatistic getAttackChainStatistics(
      @PathVariable @NotBlank final String attackChainId) {
    return attackChainStatisticService.getStatistics(attackChainId);
  }
}
