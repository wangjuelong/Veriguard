package io.veriguard.rest.scenario;

import static io.veriguard.database.specification.AttackChainSpecification.byName;
import static io.veriguard.database.specification.TeamSpecification.fromAttackChain;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.util.StringUtils.hasText;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawPaginationAttackChain;
import io.veriguard.database.raw.RawPlayer;
import io.veriguard.database.repository.*;
import io.veriguard.healthcheck.dto.HealthCheck;
import io.veriguard.rest.asset.endpoint.form.EndpointOutput;
import io.veriguard.rest.asset_group.form.AssetGroupOutput;
import io.veriguard.rest.custom_dashboard.CustomDashboardService;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exercise.form.LessonsInput;
import io.veriguard.rest.exercise.form.AttackChainTeamPlayersEnableInput;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.scenario.form.*;
import io.veriguard.rest.scenario.response.AttackChainOutput;
import io.veriguard.rest.team.output.TeamOutput;
import io.veriguard.service.*;
import io.veriguard.service.scenario.AttackChainService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class AttackChainApi extends RestBehavior {

  public static final String SCENARIO_URI = "/api/scenarios";

  private final CustomDashboardService customDashboardService;
  private final TagRepository tagRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final AttackChainRepository attackChainRepository;
  private final AttackChainRunFactory attackChainToAttackChainRunService;
  private final ImportService importService;
  private final AttackChainService attackChainService;
  private final TeamService teamService;
  private final AssetGroupService assetGroupService;
  private final EndpointService endpointService;
  private final DocumentService documentService;
  private final PlatformSettingsService platformSettingsService;

  @PostMapping(SCENARIO_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  public AttackChain createAttackChain(@Valid @RequestBody final AttackChainInput input) {
    if (input == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario input cannot be null");
    }
    AttackChain attackChain = new AttackChain();
    attackChain.setUpdateAttributes(input);
    attackChain.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    if (hasText(input.getCustomDashboard())) {
      attackChain.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      attackChain.setCustomDashboard(
          this.platformSettingsService
              .setting(SettingKeys.DEFAULT_SCENARIO_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    return this.attackChainService.createAttackChain(attackChain);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.SCENARIO)
  public AttackChain duplicateAttackChain(@PathVariable @NotBlank final String attackChainId) {
    return attackChainService.getDuplicateAttackChain(attackChainId);
  }

  @GetMapping(SCENARIO_URI)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<AttackChainSimple> attackChains() {
    return this.attackChainService.attackChains();
  }

  @LogExecutionTime
  @PostMapping(SCENARIO_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public Page<RawPaginationAttackChain> attackChains(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.attackChainService.attackChains(searchPaginationInput);
  }

  @LogExecutionTime
  @PostMapping(SCENARIO_URI + "/search-by-id")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get scenarios by their id",
      description = "Get the scenarios with the specified ids if you have the right to see them")
  public List<AttackChainSimple> attackChainsById(
      @RequestBody final GetAttackChainsInput getAttackChainsInput) {
    return this.attackChainService.attackChains(getAttackChainsInput.getAttackChainIds());
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public AttackChainOutput attackChain(@PathVariable @NotBlank final String attackChainId) {
    return attackChainService.getAttackChainById(attackChainId);
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/healthchecks")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<HealthCheck> streamHealthChecks(@PathVariable @NotBlank final String attackChainId) {
    return attackChainService.runChecks(attackChainId);
  }

  @PutMapping(SCENARIO_URI + "/{scenarioId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public AttackChain updateAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody final UpdateAttackChainInput input) {
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    Set<Tag> currentTagList = attackChain.getTags();
    attackChain.setUpdateAttributes(input);
    attackChain.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    if (hasText(input.getCustomDashboard())) {
      attackChain.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      attackChain.setCustomDashboard(null);
    }
    return this.attackChainService.updateAttackChain(attackChain, currentTagList, input.isApplyTagRule());
  }

  @DeleteMapping(SCENARIO_URI + "/{scenarioId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SCENARIO)
  public void deleteAttackChain(@PathVariable @NotBlank final String attackChainId) {
    this.attackChainService.deleteAttackChain(attackChainId);
  }

  // -- TAGS --

  @PutMapping(SCENARIO_URI + "/{scenarioId}/tags")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public AttackChain updateAttackChainTags(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody final AttackChainUpdateTagsInput input) {
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    Set<Tag> currentTagList = attackChain.getTags();
    attackChain.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.attackChainService.updateAttackChain(attackChain, currentTagList, input.isApplyTagRule());
  }

  // -- EXPORT --

  @GetMapping(SCENARIO_URI + "/{scenarioId}/export")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.SCENARIO)
  public void exportAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @RequestParam(required = false) final boolean isWithTeams,
      @RequestParam(required = false) final boolean isWithPlayers,
      @RequestParam(required = false) final boolean isWithVariableValues,
      HttpServletResponse response)
      throws IOException {
    this.attackChainService.exportAttackChain(
        attackChainId, isWithTeams, isWithPlayers, isWithVariableValues, response);
  }

  // -- IMPORT --

  @PostMapping(SCENARIO_URI + "/import")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SCENARIO)
  public void importAttackChain(@RequestPart("file") @NotNull MultipartFile file) throws Exception {
    this.importService.handleFileImport(file, null, null);
  }

  // -- TEAMS --
  @LogExecutionTime
  @GetMapping(SCENARIO_URI + "/{scenarioId}/teams")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<TeamOutput> attackChainTeams(@PathVariable @NotBlank final String attackChainId) {
    return this.teamService.find(fromAttackChain(attackChainId));
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/remove")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Iterable<TeamOutput> removeAttackChainTeams(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody final AttackChainUpdateTeamsInput input) {
    return this.attackChainService.removeTeams(attackChainId, input.getTeamIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/replace")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public List<TeamOutput> replaceAttackChainTeams(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody final AttackChainUpdateTeamsInput input) {
    return this.attackChainService.replaceTeams(attackChainId, input.getTeamIds());
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/players")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<RawPlayer> getPlayersByAttackChain(@PathVariable String attackChainId) {
    return userRepository.rawPlayersByAttackChainId(attackChainId);
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/enable")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public AttackChain enableAttackChainTeamPlayers(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final AttackChainTeamPlayersEnableInput input) {
    return this.attackChainService.enableAddAttackChainTeamPlayer(
        attackChainId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/disable")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public AttackChain disableAttackChainTeamPlayers(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final AttackChainTeamPlayersEnableInput input) {
    return this.attackChainService.disablePlayers(attackChainId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/add")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public AttackChain addAttackChainTeamPlayers(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final AttackChainTeamPlayersEnableInput input) {
    return this.attackChainService.addAttackChainPlayer(attackChainId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/remove")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public AttackChain removeAttackChainTeamPlayers(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final AttackChainTeamPlayersEnableInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().removeAll(fromIterable(teamUsers));
    teamRepository.save(team);
    return this.attackChainService.disablePlayers(attackChainId, teamId, input.getPlayersIds());
  }

  // -- RECURRENCE --

  @PutMapping(SCENARIO_URI + "/{scenarioId}/recurrence")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  public AttackChain updateAttackChainRecurrence(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody final AttackChainRecurrenceInput input) {
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    if (input.getRecurrenceStart() != null) {
      this.attackChainService.throwIfAttackChainNotLaunchable(attackChain);
    }
    attackChain.setUpdateAttributes(input);
    return this.attackChainService.updateAttackChain(attackChain);
  }

  // -- OPTION --

  @GetMapping(SCENARIO_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.attackChainRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(SCENARIO_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.attackChainRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @GetMapping(SCENARIO_URI + "/category/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> categoryOptionsByName(
      @RequestParam(required = false) final String searchText) {
    return this.attackChainRepository
        .findDistinctCategoriesBySearchTerm(searchText, PageRequest.of(0, 10))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i, i))
        .toList();
  }

  // -- LESSON --
  @PutMapping(SCENARIO_URI + "/{scenarioId}/lessons")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public AttackChain updateAttackChainLessons(
      @PathVariable String attackChainId, @Valid @RequestBody LessonsInput input) {
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    attackChain.setLessonsAnonymized(input.isLessonsAnonymized());
    return attackChainRepository.save(attackChain);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/exercise/running")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  public AttackChainRun createRunningAttackChainRunFromAttackChain(
      @PathVariable @NotBlank final String attackChainId) {
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    this.attackChainService.throwIfAttackChainNotLaunchable(attackChain);
    return attackChainToAttackChainRunService.toAttackChainRun(
        attackChain, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/check-rules")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Returns whether or not the rules apply")
      })
  @Operation(summary = "Check rules", description = "Check if the rules apply to a scenario update")
  public CheckAttackChainRulesOutput checkIfRuleApplies(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody final CheckAttackChainRulesInput input) {
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    return CheckAttackChainRulesOutput.builder()
        .rulesFound(this.attackChainService.checkIfTagRulesApplies(attackChain, input.getNewTags()))
        .build();
  }

  // region asset groups, endpoints, documents and channels
  @GetMapping(SCENARIO_URI + "/{scenarioId}/asset-groups")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary =
          "Get asset groups. Can only be called if the user has access to the given scenario.",
      description = "Get all asset groups used by injects for a given scenario")
  public List<AssetGroup> assetGroups(@PathVariable String attackChainId) {
    return this.assetGroupService.assetGroupsForAttackChain(attackChainId);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/asset-groups/find")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary =
          "Get asset groups by ids. Can only be called if the user has access to the given scenario.",
      description = "Get all asset groups by ids and used by injects for a given scenario")
  public List<AssetGroupOutput> assetGroupsByIds(
      @PathVariable String attackChainId,
      @RequestBody @Valid @NotNull final List<String> assetGroupIds) {
    return this.assetGroupService.assetGroupsByIdsForAttackChain(attackChainId, assetGroupIds);
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/endpoints")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get endpoints. Can only be called if the user has access to the given scenario.",
      description = "Get all endpoints used by injects for a given scenario")
  public List<Endpoint> endpoints(@PathVariable String attackChainId) {
    return this.endpointService.endpointsForAttackChain(attackChainId);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/endpoints/find")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary =
          "Get endpoints by ids. Can only be called if the user has access to the given scenario.",
      description = "Get all endpoints by ids used by injects for a given scenario")
  public List<EndpointOutput> endpointsByIds(
      @PathVariable String attackChainId,
      @RequestBody @Valid @NotNull final List<String> endpointIds) {
    return this.endpointService.endpointsByIdsForAttackChain(attackChainId, endpointIds);
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/documents")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get documents. Can only be called if the user has access to the given scenario.",
      description = "Get all documents used by injects for a given scenario")
  public List<Document> documents(@PathVariable String attackChainId) {
    return this.documentService.documentsForAttackChain(attackChainId);
  }

  // end region
}
