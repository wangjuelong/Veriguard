package io.veriguard.rest.team;

import static io.veriguard.database.specification.TeamSpecification.*;
import static io.veriguard.rest.scenario.ScenarioApi.SCENARIO_URI;

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
public class ScenarioTeamApi extends RestBehavior {

  private final TeamService teamService;

  @PostMapping(SCENARIO_URI + "/{scenarioId}/teams/search")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Page<TeamOutput> teams(
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @RequestParam
          @Schema(
              description =
                  "Controls which teams to retrieve - true: Only teams that are part of the scenario")
          final boolean contextualOnly) {
    Specification<Team> teamSpecification;
    if (!contextualOnly) {
      teamSpecification = contextual(false).or(fromScenario(scenarioId));
      // contextual(false) => Teams that exist independently, not created from a specific context
      // (scenario or simulation)
    } else {
      teamSpecification = fromScenario(scenarioId);
    }
    return this.teamService.teamPagination(searchPaginationInput, teamSpecification);
  }
}
