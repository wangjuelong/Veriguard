package io.veriguard.rest.team;

import static io.veriguard.database.specification.TeamSpecification.*;
import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;

import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.Team;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.team.output.TeamOutput;
import io.veriguard.service.TeamService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class AttackChainTeamApi extends RestBehavior {

  private final TeamService teamService;

  @PostMapping(SCENARIO_URI + "/{scenarioId}/teams/search")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Page<TeamOutput> teams(
      @PathVariable @NotBlank final String attackChainId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @RequestParam
          @Schema(
              description =
                  "Controls which teams to retrieve - true: Only teams that are part of the scenario")
          final boolean contextualOnly) {
    Specification<Team> teamSpecification;
    if (!contextualOnly) {
      teamSpecification = contextual(false).or(fromAttackChain(attackChainId));
      // contextual(false) => Teams that exist independently, not created from a specific context
      // (attackChain or simulation)
    } else {
      teamSpecification = fromAttackChain(attackChainId);
    }
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification);
  }
}
