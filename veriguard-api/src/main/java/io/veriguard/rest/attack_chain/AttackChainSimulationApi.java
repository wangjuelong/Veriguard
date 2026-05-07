package io.veriguard.rest.attack_chain;

import static io.veriguard.database.specification.AttackChainRunSpecification.fromAttackChain;
import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Base;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.attack_chain_run.form.AttackChainRunSimple;
import io.veriguard.rest.attack_chain_run.service.AttackChainRunService;
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
public class AttackChainSimulationApi {

  private final AttackChainRunService attackChainRunService;

  @LogExecutionTime
  @GetMapping(SCENARIO_URI + "/{attackChainId}/exercises")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<AttackChainRunSimple> attackChainAttackChainRuns(
      @PathVariable @NotBlank final String attackChainId) {
    return attackChainRunService.attackChainAttackChainRuns(attackChainId);
  }

  @LogExecutionTime
  @PostMapping(SCENARIO_URI + "/{attackChainId}/exercises/search")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<AttackChainRunSimple> attackChainAttackChainRuns(
      @PathVariable @NotBlank final String attackChainId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<AttackChainRun> specification,
            Specification<AttackChainRun> specificationCount,
            Pageable pageable) ->
            this.attackChainRunService.attackChainRunsWithEmptyGlobalScore(
                fromAttackChain(attackChainId).and(specification),
                fromAttackChain(attackChainId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        AttackChainRun.class,
        joinMap);
  }

  // -- OPTION --

  @GetMapping(SCENARIO_URI + "/{attackChainId}/simulations/options")
  public List<FilterUtilsJpa.Option> optionsByName(
      @PathVariable @NotBlank final String attackChainId,
      @RequestParam(required = false) final String searchText) {
    return this.attackChainRunService.findAllAsOptions(fromAttackChain(attackChainId), searchText);
  }
}
