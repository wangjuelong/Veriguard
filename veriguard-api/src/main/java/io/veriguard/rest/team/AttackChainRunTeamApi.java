package io.veriguard.rest.team;

import static io.veriguard.database.specification.TeamSpecification.contextual;
import static io.veriguard.database.specification.TeamSpecification.fromAttackChainRun;
import static io.veriguard.rest.exercise.AttackChainRunApi.EXERCISE_URI;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.Team;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.team.output.TeamOutput;
import io.veriguard.service.TeamService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class AttackChainRunTeamApi extends RestBehavior {

  private final TeamService teamService;

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/{exerciseId}/teams/search")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Page<TeamOutput> searchTeams(
      @PathVariable @NotBlank final String attackChainRunId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @RequestParam
          @Schema(
              description =
                  "Controls which teams to retrieve - true: Only teams that are part of the simulation")
          final boolean contextualOnly) {
    Specification<Team> teamSpecification;
    if (!contextualOnly) {
      teamSpecification = contextual(false).or(fromAttackChainRun(attackChainRunId));
      // contextual(false) => Teams that exist independently, not created from a specific context
      // (attackChain or simulation)
    } else {
      teamSpecification = fromAttackChainRun(attackChainRunId);
    }
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification);
  }
}
