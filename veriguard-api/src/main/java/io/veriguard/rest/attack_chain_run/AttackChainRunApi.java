package io.veriguard.rest.attack_chain_run;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.specification.AttackChainRunSpecification.findGrantedFor;
import static io.veriguard.database.specification.TeamSpecification.fromAttackChainRun;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.helper.StreamHelper.iterableToSet;
import static io.veriguard.rest.attack_chain_run.form.SimulationDetails.fromRawAttackChainRun;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.*;
import io.veriguard.database.repository.*;
import io.veriguard.database.specification.AttackChainRunLogSpecification;
import io.veriguard.database.specification.ComcheckSpecification;
import io.veriguard.rest.asset.endpoint.form.EndpointOutput;
import io.veriguard.rest.asset_group.form.AssetGroupOutput;
import io.veriguard.rest.attack_chain_node.form.NodeExpectationResultsByAttackPattern;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.attack_chain_run.exports.ExportOptions;
import io.veriguard.rest.attack_chain_run.form.*;
import io.veriguard.rest.attack_chain_run.response.AttackChainRunsGlobalScoresOutput;
import io.veriguard.rest.attack_chain_run.service.AttackChainRunService;
import io.veriguard.rest.attack_chain_run.service.ExportService;
import io.veriguard.rest.custom_dashboard.CustomDashboardService;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.exception.InputValidationException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.team.output.TeamOutput;
import io.veriguard.service.*;
import io.veriguard.service.scenario.AttackChainService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Join;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class AttackChainRunApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/attack_chain_runs";

  // region repositories
  private final LogRepository logRepository;
  private final TagRepository tagRepository;
  private final UserRepository userRepository;
  private final DocumentRepository documentRepository;
  private final AttackChainRunRepository attackChainRunRepository;
  private final TeamRepository teamRepository;
  private final AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;
  private final LogRepository attackChainRunLogRepository;
  private final ComcheckRepository comcheckRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;
  private final GrantRepository grantRepository;
  // endregion

  // region services
  private final AssetGroupService assetGroupService;
  private final CustomDashboardService customDashboardService;
  private final EndpointService endpointService;
  private final FileService fileService;
  private final AttackChainNodeService attackChainNodeService;
  private final ImportService importService;
  private final AttackChainRunService attackChainRunService;
  private final TeamService teamService;
  private final ExportService exportService;
  private final DocumentService documentService;
  private final AttackChainService attackChainService;
  private final UserService userService;
  private final PlatformSettingsService platformSettingsService;

  // endregion

  // region logs
  @GetMapping(EXERCISE_URI + "/{exercise}/logs")
  @RBAC(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Log> logs(@PathVariable String attackChainRun) {
    return attackChainRunLogRepository.findAll(
        AttackChainRunLogSpecification.fromAttackChainRun(attackChainRun));
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/logs")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Log createLog(
      @PathVariable String attackChainRunId, @Valid @RequestBody LogCreateInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    Log log = new Log();
    log.setUpdateAttributes(input);
    log.setAttackChainRun(attackChainRun);
    log.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    log.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    return attackChainRunLogRepository.save(log);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/logs/{logId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Log updateLog(
      @PathVariable String attackChainRunId,
      @PathVariable String logId,
      @Valid @RequestBody LogCreateInput input) {
    Log log = logRepository.findById(logId).orElseThrow(ElementNotFoundException::new);
    log.setUpdateAttributes(input);
    log.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return logRepository.save(log);
  }

  @DeleteMapping(EXERCISE_URI + "/{exerciseId}/logs/{logId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void deleteLog(@PathVariable String attackChainRunId, @PathVariable String logId) {
    logRepository.deleteById(logId);
  }

  // endregion

  // region comchecks
  @GetMapping(EXERCISE_URI + "/{exercise}/comchecks")
  @RBAC(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Comcheck> comchecks(@PathVariable String attackChainRun) {
    return comcheckRepository.findAll(ComcheckSpecification.fromAttackChainRun(attackChainRun));
  }

  @GetMapping(EXERCISE_URI + "/{exercise}/comchecks/{comcheck}")
  @RBAC(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Comcheck comcheck(@PathVariable String attackChainRun, @PathVariable String comcheck) {
    Specification<Comcheck> filters =
        ComcheckSpecification.fromAttackChainRun(attackChainRun)
            .and(ComcheckSpecification.id(comcheck));
    return comcheckRepository.findOne(filters).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping(EXERCISE_URI + "/{exercise}/comchecks/{comcheck}/statuses")
  @RBAC(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<ComcheckStatus> comcheckStatuses(
      @PathVariable String attackChainRun, @PathVariable String comcheck) {
    return comcheck(attackChainRun, comcheck).getComcheckStatus();
  }

  // endregion

  // region teams
  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/teams")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<TeamOutput> getAttackChainRunTeams(@PathVariable String attackChainRunId) {
    return this.teamService.find(fromAttackChainRun(attackChainRunId));
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/remove")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Iterable<TeamOutput> removeAttackChainRunTeams(
      @PathVariable String attackChainRunId,
      @Valid @RequestBody AttackChainRunUpdateTeamsInput input) {
    return this.attackChainRunService.removeTeams(attackChainRunId, input.getTeamIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/replace")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Iterable<TeamOutput> replaceAttackChainRunTeams(
      @PathVariable String attackChainRunId,
      @Valid @RequestBody AttackChainRunUpdateTeamsInput input) {
    return this.attackChainRunService.replaceTeams(attackChainRunId, input.getTeamIds());
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/players")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<RawPlayer> getPlayersByAttackChainRun(@PathVariable String attackChainRunId) {
    return userRepository.rawPlayersByAttackChainRunId(attackChainRunId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/enable")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public AttackChainRun enableAttackChainRunTeamPlayers(
      @PathVariable String attackChainRunId,
      @PathVariable String teamId,
      @Valid @RequestBody AttackChainRunTeamPlayersEnableInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    return attackChainRunService.enablePlayers(attackChainRunId, team, input.getPlayersIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/disable")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public AttackChainRun disableAttackChainRunTeamPlayers(
      @PathVariable String attackChainRunId,
      @PathVariable String teamId,
      @Valid @RequestBody AttackChainRunTeamPlayersEnableInput input) {
    input
        .getPlayersIds()
        .forEach(
            playerId -> {
              AttackChainRunTeamUserId attackChainRunTeamUserId = new AttackChainRunTeamUserId();
              attackChainRunTeamUserId.setAttackChainRunId(attackChainRunId);
              attackChainRunTeamUserId.setTeamId(teamId);
              attackChainRunTeamUserId.setUserId(playerId);
              attackChainRunTeamUserRepository.deleteById(attackChainRunTeamUserId);
            });
    return attackChainRunRepository
        .findById(attackChainRunId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/add")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public AttackChainRun addAttackChainRunTeamPlayers(
      @PathVariable String attackChainRunId,
      @PathVariable String teamId,
      @Valid @RequestBody AttackChainRunTeamPlayersEnableInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().addAll(fromIterable(teamUsers));
    teamRepository.save(team);
    return attackChainRunService.enablePlayers(attackChainRunId, team, input.getPlayersIds());
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/remove")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun removeAttackChainRunTeamPlayers(
      @PathVariable String attackChainRunId,
      @PathVariable String teamId,
      @Valid @RequestBody AttackChainRunTeamPlayersEnableInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().removeAll(fromIterable(teamUsers));
    teamRepository.save(team);
    input
        .getPlayersIds()
        .forEach(
            playerId -> {
              AttackChainRunTeamUserId attackChainRunTeamUserId = new AttackChainRunTeamUserId();
              attackChainRunTeamUserId.setAttackChainRunId(attackChainRunId);
              attackChainRunTeamUserId.setTeamId(teamId);
              attackChainRunTeamUserId.setUserId(playerId);
              attackChainRunTeamUserRepository.deleteById(attackChainRunTeamUserId);
            });
    return attackChainRunRepository
        .findById(attackChainRunId)
        .orElseThrow(ElementNotFoundException::new);
  }

  // endregion

  // region attackChainRuns
  @PostMapping(EXERCISE_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SIMULATION)
  public AttackChainRun createAttackChainRun(@Valid @RequestBody CreateAttackChainRunInput input) {
    if (input == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise input cannot be null");
    }
    AttackChainRun attackChainRun = new AttackChainRun();
    attackChainRun.setUpdateAttributes(input);
    attackChainRun.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    if (hasText(input.getCustomDashboard())) {
      attackChainRun.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      attackChainRun.setCustomDashboard(
          this.platformSettingsService
              .setting(SettingKeys.DEFAULT_SIMULATION_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    return this.attackChainRunService.createAttackChainRun(attackChainRun);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun duplicateAttackChainRun(
      @PathVariable @NotBlank final String attackChainRunId) {
    return attackChainRunService.getDuplicateAttackChainRun(attackChainRunId);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun updateAttackChainRunInformation(
      @PathVariable String attackChainRunId, @Valid @RequestBody UpdateAttackChainRunInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    Set<Tag> currentTagList = attackChainRun.getTags();
    attackChainRun.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    attackChainRun.setUpdateAttributes(input);
    if (hasText(input.getCustomDashboard())) {
      attackChainRun.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      attackChainRun.setCustomDashboard(null);
    }
    return attackChainRunService.updateExercice(
        attackChainRun, currentTagList, input.isApplyTagRule());
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/start_date")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  @Deprecated(since = "1.16.0")
  public AttackChainRun deprecatedUpdateAttackChainRunStart(
      @PathVariable String attackChainRunId,
      @Valid @RequestBody AttackChainRunUpdateStartDateInput input)
      throws InputValidationException {
    return this.updateAttackChainRunStart(attackChainRunId, input);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/start-date")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun updateAttackChainRunStart(
      @PathVariable String attackChainRunId,
      @Valid @RequestBody AttackChainRunUpdateStartDateInput input)
      throws InputValidationException {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    if (!attackChainRun.getStatus().equals(AttackChainRunStatus.SCHEDULED)) {
      String message = "Change date is only possible in scheduling state";
      throw new InputValidationException("exercise_start_date", message);
    }
    attackChainRunService.throwIfAttackChainRunNotLaunchable(attackChainRun);
    attackChainRun.setUpdateAttributes(input);
    return attackChainRunRepository.save(attackChainRun);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/tags")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun updateAttackChainRunTags(
      @PathVariable String attackChainRunId,
      @Valid @RequestBody AttackChainRunUpdateTagsInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    Set<Tag> currentTagList = attackChainRun.getTags();
    attackChainRun.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return attackChainRunService.updateExercice(
        attackChainRun, currentTagList, input.isApplyTagRule());
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/logos")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun updateAttackChainRunLogos(
      @PathVariable String attackChainRunId,
      @Valid @RequestBody AttackChainRunUpdateLogoInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    attackChainRun.setLogoDark(documentRepository.findById(input.getLogoDark()).orElse(null));
    attackChainRun.setLogoLight(documentRepository.findById(input.getLogoLight()).orElse(null));
    return attackChainRunRepository.save(attackChainRun);
  }

  // -- OPTION --
  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/findings/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<FilterUtilsJpa.Option> optionsByNameLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String attackChainId) {
    return attackChainRunService.getOptionsByNameLinkedToFindings(
        searchText, attackChainId, PageRequest.of(0, 50));
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.attackChainRunRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/lessons")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun updateAttackChainRunLessons(
      @PathVariable String attackChainRunId, @Valid @RequestBody LessonsInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    attackChainRun.setLessonsAnonymized(input.isLessonsAnonymized());
    return attackChainRunRepository.save(attackChainRun);
  }

  @DeleteMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void deleteAttackChainRun(@PathVariable String attackChainRunId) {
    attackChainRunRepository.deleteById(attackChainRunId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public SimulationDetails attackChainRun(@PathVariable String attackChainRunId) {
    // We get the raw attackChainRun
    RawSimulation rawSimulation = attackChainRunService.rawSimulation(attackChainRunId);
    // We get the attackChainNodes linked to this attackChainRun
    List<RawAttackChainNode> rawAttackChainNodes =
        attackChainNodeRepository.findRawByIds(
            rawSimulation.getInject_ids().stream().distinct().toList());
    // We get the tuple attackChainRun/team/user
    List<RawAttackChainRunTeamUser> listRawAttackChainRunTeamUsers =
        attackChainRunTeamUserRepository.rawByAttackChainRunIds(List.of(attackChainRunId));
    // We get the objectives of this attackChainRun
    List<RawObjective> rawObjectives =
        objectiveRepository.rawByAttackChainRunIds(List.of(attackChainRunId));
    // We make a map of the Evaluations by objective
    Map<String, List<RawEvaluation>> mapEvaluationsByObjective =
        evaluationRepository
            .rawByObjectiveIds(rawObjectives.stream().map(RawObjective::getObjective_id).toList())
            .stream()
            .collect(Collectors.groupingBy(RawEvaluation::getEvaluation_objective));
    // We make a map of grants of users id by type of grant (Planner, Observer)
    Map<String, List<RawGrant>> rawGrants =
        grantRepository.rawByAttackChainRunIds(List.of(attackChainRunId)).stream()
            .collect(Collectors.groupingBy(RawGrant::getGrant_name));
    // We get all the kill chain phases
    List<KillChainPhase> killChainPhase =
        StreamSupport.stream(
                killChainPhaseRepository
                    .findAllById(
                        rawAttackChainNodes.stream()
                            .flatMap(
                                rawAttackChainNode ->
                                    rawAttackChainNode.getInject_kill_chain_phases().stream())
                            .toList())
                    .spliterator(),
                false)
            .collect(Collectors.toList());

    // We create objectives and fill them with evaluations
    List<Objective> objectives =
        rawObjectives.stream()
            .map(
                rawObjective -> {
                  Objective objective = new Objective();
                  if (mapEvaluationsByObjective.get(rawObjective.getObjective_id()) != null) {
                    objective.setEvaluations(
                        mapEvaluationsByObjective.get(rawObjective.getObjective_id()).stream()
                            .map(
                                rawEvaluation -> {
                                  Evaluation evaluation = new Evaluation();
                                  evaluation.setId(rawEvaluation.getEvaluation_id());
                                  evaluation.setScore(rawEvaluation.getEvaluation_score());
                                  return evaluation;
                                })
                            .toList());
                  }
                  return objective;
                })
            .toList();

    List<AttackChainRunTeamUser> listAttackChainRunTeamUsers =
        listRawAttackChainRunTeamUsers.stream()
            .map(AttackChainRunTeamUser::fromRawAttackChainRunTeamUser)
            .toList();

    // We create an AttackChainRunDetails object and populate it
    SimulationDetails detail =
        fromRawAttackChainRun(rawSimulation, listAttackChainRunTeamUsers, objectives);
    detail.setPlatforms(
        rawAttackChainNodes.stream()
            .flatMap(attackChainNode -> attackChainNode.getInject_platforms().stream())
            .distinct()
            .toList());
    detail.setCommunicationsNumber(
        rawAttackChainNodes.stream()
            .mapToLong(rawAttackChainNode -> rawAttackChainNode.getInject_communications().size())
            .sum());
    detail.setKillChainPhases(killChainPhase);
    if (rawGrants.get(Grant.GRANT_TYPE.OBSERVER.name()) != null) {
      detail.setObservers(
          rawGrants.get(Grant.GRANT_TYPE.OBSERVER.name()).stream()
              .map(RawGrant::getUser_id)
              .collect(Collectors.toSet()));
    }
    if (rawGrants.get(Grant.GRANT_TYPE.PLANNER.name()) != null) {
      detail.setPlanners(
          rawGrants.get(Grant.GRANT_TYPE.PLANNER.name()).stream()
              .map(RawGrant::getUser_id)
              .collect(Collectors.toSet()));
    }

    return detail;
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/results")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<ExpectationResultsByType> globalResults(
      @NotBlank @PathVariable String attackChainRunId) {
    return attackChainRunService.getGlobalResults(attackChainRunId);
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/global-scores")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public AttackChainRunsGlobalScoresOutput getAttackChainRunsGlobalScores(
      @Valid @RequestBody AttackChainRunsGlobalScoresInput input) {
    return attackChainRunService.getAttackChainRunsGlobalScores(input);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/results-by-attack-patterns")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<NodeExpectationResultsByAttackPattern> attackChainNodeResults(
      @NotBlank final @PathVariable String attackChainRunId) {
    return attackChainRunService.extractExpectationResultsByAttackPattern(attackChainRunId);
  }

  @DeleteMapping(EXERCISE_URI + "/{exerciseId}/{documentId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun deleteDocument(
      @PathVariable String attackChainRunId, @PathVariable String documentId) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    attackChainRun.setUpdatedAt(now());
    Document doc =
        documentRepository.findById(documentId).orElseThrow(ElementNotFoundException::new);
    Set<AttackChainRun> docAttackChainRuns =
        doc.getAttackChainRuns().stream()
            .filter(ex -> !ex.getId().equals(attackChainRunId))
            .collect(Collectors.toSet());
    if (docAttackChainRuns.isEmpty()) {
      // Document is no longer associate to any attackChainRun, delete it
      documentRepository.delete(doc);
      // All associations with this document will be automatically cleanup.
    } else {
      // Document associated to other attackChainRun, cleanup
      doc.setAttackChainRuns(docAttackChainRuns);
      documentRepository.save(doc);
      // Delete document from all attackChainRun attackChainNodes
      attackChainNodeService.cleanAttackChainNodesDocAttackChainRun(attackChainRunId, documentId);
    }
    return attackChainRunRepository.save(attackChainRun);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/status")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SIMULATION)
  public AttackChainRun changeAttackChainRunStatus(
      @PathVariable String attackChainRunId,
      @Valid @RequestBody AttackChainRunUpdateStatusInput input) {
    AttackChainRunStatus status = input.getStatus();
    return attackChainRunService.changeAttackChainRunStatus(status, attackChainRunId);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<AttackChainRunSimple> attackChainRuns() {
    return attackChainRunService.attackChainRuns();
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/search-by-id")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get simulations by their id",
      description = "Get the simulations with the specified ids if you have the right to see them")
  public List<AttackChainRunSimple> simulationsById(
      @RequestBody final GetAttackChainRunsInput getAttackChainRunsInput) {
    return attackChainRunService.attackChainRuns(getAttackChainRunsInput.getAttackChainRunIds());
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public Page<AttackChainRunSimple> attackChainRuns(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      return buildPaginationCriteriaBuilder(
          (Specification<AttackChainRun> specification,
              Specification<AttackChainRun> specificationCount,
              Pageable pageable) ->
              this.attackChainRunService.attackChainRuns(
                  specification, specificationCount, pageable, joinMap),
          searchPaginationInput,
          AttackChainRun.class,
          joinMap);

    } else {
      return buildPaginationCriteriaBuilder(
          (Specification<AttackChainRun> specification,
              Specification<AttackChainRun> specificationCount,
              Pageable pageable) ->
              this.attackChainRunService.attackChainRuns(
                  findGrantedFor(currentUser().getId()).and(specification),
                  findGrantedFor(currentUser().getId()).and(specificationCount),
                  pageable,
                  joinMap),
          searchPaginationInput,
          AttackChainRun.class,
          joinMap);
    }
  }

  // endregion

  // region communication
  @GetMapping(EXERCISE_URI + "/{exerciseId}/communications")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Communication> attackChainRunCommunications(
      @PathVariable String attackChainRunId) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    List<Communication> communications = new ArrayList<>();
    attackChainRun
        .getAttackChainNodes()
        .forEach(
            attackChainNodeDoc -> communications.addAll(attackChainNodeDoc.getCommunications()));
    return communications;
  }

  @GetMapping("/api/communications/attachment")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  //
  public void downloadAttachment(@RequestParam String file, HttpServletResponse response)
      throws IOException {
    FileContainer fileContainer =
        fileService.getFileContainer(file).orElseThrow(ElementNotFoundException::new);
    response.addHeader(
        HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileContainer.getName());
    response.addHeader(HttpHeaders.CONTENT_TYPE, fileContainer.getContentType());
    response.setStatus(HttpServletResponse.SC_OK);
    fileContainer.getInputStream().transferTo(response.getOutputStream());
  }

  // endregion

  // region import/export
  @GetMapping(EXERCISE_URI + "/{exerciseId}/export")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public void attackChainRunExport(
      @NotBlank @PathVariable final String attackChainRunId,
      @RequestParam(required = false) final boolean isWithTeams,
      @RequestParam(required = false) final boolean isWithPlayers,
      @RequestParam(required = false) final boolean isWithVariableValues,
      HttpServletResponse response)
      throws IOException {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    int exportOptionsMask = ExportOptions.mask(isWithPlayers, isWithTeams, isWithVariableValues);

    byte[] zippedExport =
        exportService.exportAttackChainRunToZip(attackChainRun, exportOptionsMask);
    String zipName = exportService.getZipFileName(attackChainRun, exportOptionsMask);

    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
    response.setStatus(HttpServletResponse.SC_OK);
    ServletOutputStream outputStream = response.getOutputStream();
    outputStream.write(zippedExport);
    outputStream.close();
  }

  @PostMapping(EXERCISE_URI + "/import")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SIMULATION)
  public void attackChainRunImport(@RequestPart("file") MultipartFile file) throws Exception {
    importService.handleFileImport(file, null, null);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/check-rules")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Returns whether or not the rules apply")
      })
  @Operation(
      summary = "Check rules",
      description = "Check if the rules apply to a simulation update")
  public CheckAttackChainRunRulesOutput checkIfRuleApplies(
      @PathVariable @NotBlank final String attackChainRunId,
      @Valid @RequestBody final CheckAttackChainRunRulesInput input) {
    AttackChainRun attackChainRun = this.attackChainRunService.attackChainRun(attackChainRunId);
    return CheckAttackChainRunRulesOutput.builder()
        .rulesFound(
            this.attackChainRunService.checkIfTagRulesApplies(attackChainRun, input.getNewTags()))
        .build();
  }

  // endregion

  // region asset groups, endpoints, documents and channels
  @GetMapping(EXERCISE_URI + "/{exerciseId}/asset-groups")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary =
          "Get asset groups. Can only be called if the user has access to the given simulation.",
      description = "Get all asset groups used by injects for a given simulation")
  public List<AssetGroup> assetGroups(@PathVariable String attackChainRunId) {
    return this.assetGroupService.assetGroupsForSimulation(attackChainRunId);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/asset-groups/find")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary =
          "Get asset groups by ids. Can only be called if the user has access to the given simulation.",
      description = "Get all asset groups by ids used by injects for a given simulation")
  public List<AssetGroupOutput> assetGroupsByIds(
      @PathVariable String attackChainRunId,
      @RequestBody @Valid @NotNull final List<String> assetGroupIds) {
    return this.assetGroupService.assetGroupsByIdsForSimulation(attackChainRunId, assetGroupIds);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/endpoints")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get endpoints. Can only be called if the user has access to the given simulation.",
      description = "Get all endpoints used by injects for a given simulation")
  public List<Endpoint> endpoints(@PathVariable String attackChainRunId) {
    return this.endpointService.endpointsForSimulation(attackChainRunId);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/endpoints/find")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary =
          "Get endpoints by ids. Can only be called if the user has access to the given simulation.",
      description = "Get all endpoints by ids used by injects for a given simulation")
  public List<EndpointOutput> endpointsByIds(
      @PathVariable String attackChainRunId,
      @RequestBody @Valid @NotNull final List<String> endpointIds) {
    return this.endpointService.endpointsByIdsForSimulation(attackChainRunId, endpointIds);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/documents")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get documents. Can only be called if the user has access to the given simulation.",
      description = "Get all documents used by injects for a given simulation")
  public List<Document> documents(@PathVariable String attackChainRunId) {
    return this.documentService.documentsForSimulation(attackChainRunId);
  }

  @GetMapping(EXERCISE_URI + "/{simulationId}/scenario")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Get the Scenario linked to the simulation")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The Scenario related to the given simulation"),
        @ApiResponse(responseCode = "404", description = "Simulation or Scenario not found")
      })
  public AttackChain attackChainFromSimulation(
      @PathVariable @NotBlank @Schema(description = "ID of the simulation")
          final String simulationId) {
    return attackChainService.attackChainFromSimulationId(simulationId);
  }

  // end region
}
