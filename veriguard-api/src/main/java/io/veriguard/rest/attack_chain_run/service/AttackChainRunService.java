package io.veriguard.rest.attack_chain_run.service;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.criteria.GenericCriteria.countQuery;
import static io.veriguard.database.model.Grant.GRANT_RESOURCE_TYPE.SIMULATION;
import static io.veriguard.database.specification.AttackChainRunSpecification.*;
import static io.veriguard.database.specification.TeamSpecification.fromIds;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.utils.JpaUtils.arrayAggOnId;
import static io.veriguard.utils.StringUtils.duplicateString;
import static io.veriguard.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilderWithNullHandling;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import io.veriguard.database.raw.RawAttackChainRunSimple;
import io.veriguard.database.raw.RawSimulation;
import io.veriguard.database.repository.*;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.rest.atomic_testing.form.TargetSimple;
import io.veriguard.rest.attack_chain.service.AttackChainStatisticService;
import io.veriguard.rest.attack_chain_node.form.NodeExpectationResultsByAttackPattern;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeDuplicateService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.attack_chain_run.form.AttackChainRunSimple;
import io.veriguard.rest.attack_chain_run.form.AttackChainRunsGlobalScoresInput;
import io.veriguard.rest.attack_chain_run.response.AttackChainRunsGlobalScoresOutput;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.team.output.TeamOutput;
import io.veriguard.service.*;
import io.veriguard.service.scenario.AttackChainRecurrenceService;
import io.veriguard.utils.FilterUtilsJpa;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.veriguard.utils.ResultUtils;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.mapper.AttackChainNodeExpectationMapper;
import io.veriguard.utils.mapper.AttackChainNodeMapper;
import io.veriguard.utils.mapper.AttackChainRunMapper;
import io.veriguard.utils.pagination.SortUtilsCriteriaBuilder;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotBlank;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Validated
@Service
@Slf4j
public class AttackChainRunService {

  @PersistenceContext private EntityManager entityManager;

  private final AttackChainNodeDuplicateService attackChainNodeDuplicateService;
  private final TeamService teamService;
  private final VariableService variableService;
  private final TagRuleService tagRuleService;
  private final DocumentService documentService;
  private final AttackChainNodeService attackChainNodeService;
  private final UserService userService;
  private final GrantService grantService;
  private final AttackChainRunTeamUserService attackChainRunTeamUserService;

  private final AttackChainRunMapper attackChainRunMapper;
  private final AttackChainNodeMapper attackChainNodeMapper;
  private final ResultUtils resultUtils;

  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  private final AttackChainRunRepository attackChainRunRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;

  private final AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;

  private final AttackChainRecurrenceService attackChainRecurrenceService;

  private final PauseAttackChainRunService pauseAttackChainRunService;
  private final FileService fileService;
  private final LessonsService lessonsService;

  // region properties
  @Value("${veriguard.mail.imap.enabled}")
  private boolean imapEnabled;

  @Value("${veriguard.mail.imap.username}")
  private String imapUsername;

  @Resource private VeriguardConfig veriguardConfig;

  // endregion

  // -- CRUD --

  // -- CREATION --
  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun createAttackChainRun(@NotNull final AttackChainRun attackChainRun) {
    if (!StringUtils.hasText(attackChainRun.getFrom())) {
      if (imapEnabled) {
        attackChainRun.setFrom(imapUsername);
        attackChainRun.setReplyTos(List.of(imapUsername));
      } else {
        attackChainRun.setFrom(veriguardConfig.getDefaultMailer());
        attackChainRun.setReplyTos(new ArrayList<>(List.of(veriguardConfig.getDefaultReplyTo())));
      }
    }

    return attackChainRunRepository.save(attackChainRun);
  }

  // -- READ --
  public AttackChainRun attackChainRun(@NotBlank final String attackChainRunId) {
    return this.attackChainRunRepository
        .findById(attackChainRunId)
        .orElseThrow(() -> new ElementNotFoundException("Exercise not found"));
  }

  public RawSimulation rawSimulation(@NotBlank final String simulationId) {
    RawSimulation rawSimulation = attackChainRunRepository.rawDetailsById(simulationId);
    if (rawSimulation == null) {
      throw new ElementNotFoundException("Simulation not found");
    }
    return rawSimulation;
  }

  public List<AttackChainRunSimple> attackChainRuns(final List<String> attackChainRunIds) {

    User currentUser = userService.currentUser();
    List<RawAttackChainRunSimple> attackChainRuns =
        currentUser.isAdminOrBypass()
                || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)
            ? attackChainRunRepository.rawByAttackChainRunIds(attackChainRunIds)
            : attackChainRunRepository.rawGrantedByAttackChainRunIds(
                currentUser().getId(), attackChainRunIds);
    return attackChainRunMapper.getAttackChainRunSimples(attackChainRuns);
  }

  // -- UPDATE --
  public AttackChainRun updateAttackChainRun(@NotNull final AttackChainRun attackChainRun) {
    attackChainRun.setUpdatedAt(now());
    return this.attackChainRunRepository.save(attackChainRun);
  }

  // -- DUPLICATION --
  @Transactional
  public AttackChainRun getDuplicateAttackChainRun(@NotBlank String attackChainRunId) {
    AttackChainRun attackChainRunOrigin =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow();
    AttackChainRun attackChainRun = copyExercice(attackChainRunOrigin);
    AttackChainRun attackChainRunDuplicate = attackChainRunRepository.save(attackChainRun);
    duplicateGrants(attackChainRunDuplicate, attackChainRunOrigin);
    getListOfDuplicatedAttackChainNodes(attackChainRunDuplicate, attackChainRunOrigin);
    Map<String, Team> contextualTeams =
        getListOfAttackChainRunTeams(attackChainRunDuplicate, attackChainRunOrigin);
    duplicateTeamUsers(attackChainRunDuplicate, attackChainRunOrigin, contextualTeams);
    getListOfVariables(attackChainRunDuplicate, attackChainRunOrigin);
    getObjectives(attackChainRunDuplicate, attackChainRunOrigin);
    getLessonsCategories(attackChainRunDuplicate, attackChainRunOrigin);
    return attackChainRunRepository.save(attackChainRunDuplicate);
  }

  private AttackChainRun copyExercice(AttackChainRun attackChainRunOrigin) {
    AttackChainRun attackChainRunDuplicate = new AttackChainRun();
    attackChainRunDuplicate.setName(duplicateString(attackChainRunOrigin.getName()));
    attackChainRunDuplicate.setCategory(attackChainRunOrigin.getCategory());
    attackChainRunDuplicate.setDescription(attackChainRunOrigin.getDescription());
    attackChainRunDuplicate.setFrom(attackChainRunOrigin.getFrom());
    attackChainRunDuplicate.setFooter(attackChainRunOrigin.getFooter());
    attackChainRunDuplicate.setAttackChain(attackChainRunOrigin.getAttackChain());
    attackChainRunDuplicate.setHeader(attackChainRunOrigin.getHeader());
    attackChainRunDuplicate.setMainFocus(attackChainRunOrigin.getMainFocus());
    attackChainRunDuplicate.setSeverity(attackChainRunOrigin.getSeverity());
    attackChainRunDuplicate.setSubtitle(attackChainRunOrigin.getSubtitle());
    attackChainRunDuplicate.setLogoDark(attackChainRunOrigin.getLogoDark());
    attackChainRunDuplicate.setLogoLight(attackChainRunOrigin.getLogoLight());
    attackChainRunDuplicate.setTags(new HashSet<>(attackChainRunOrigin.getTags()));
    attackChainRunDuplicate.setReplyTos(new ArrayList<>(attackChainRunOrigin.getReplyTos()));
    attackChainRunDuplicate.setDocuments(new ArrayList<>(attackChainRunOrigin.getDocuments()));
    attackChainRunDuplicate.setLessonsAnonymized(attackChainRunOrigin.isLessonsAnonymized());
    attackChainRunDuplicate.setCustomDashboard(attackChainRunOrigin.getCustomDashboard());
    return attackChainRunDuplicate;
  }

  public List<Document> getAttackChainRunPlayerDocuments(AttackChainRun attackChainRun) {
    return attackChainRun.getAttackChainNodes().stream()
        .flatMap(
            attackChainNode ->
                attackChainNode.getDocuments().stream().map(AttackChainNodeDocument::getDocument))
        .distinct()
        .toList();
  }

  public Optional<AttackChainRun> getFollowingSimulation(AttackChainRun attackChainRun) {
    return attackChainRun.getAttackChain() != null
        ? attackChainRunRepository.following(attackChainRun)
        : Optional.empty();
  }

  public Optional<Instant> getLatestValidityDate(AttackChainRun attackChainRun) {
    Optional<AttackChainRun> follower = this.getFollowingSimulation(attackChainRun);
    if (follower.isPresent()) {
      return follower.get().getStart();
    }

    return attackChainRun.getStart().isPresent() && attackChainRun.getAttackChain() != null
        ? attackChainRecurrenceService.getNextExecutionTime(
            attackChainRun.getAttackChain(), attackChainRun.getStart().get())
        : Optional.empty();
  }

  private Map<String, Team> getListOfAttackChainRunTeams(
      @NotNull AttackChainRun attackChainRun, @NotNull AttackChainRun attackChainRunOrigin) {
    Map<String, Team> contextualTeams = new HashMap<>();
    List<Team> attackChainRunTeams = new ArrayList<>();
    attackChainRunOrigin
        .getTeams()
        .forEach(
            attackChainTeam -> {
              if (attackChainTeam.getContextual()) {
                Team team = teamService.copyContextualTeam(attackChainTeam);
                Team teamSaved = this.teamRepository.save(team);
                attackChainRunTeams.add(teamSaved);
                contextualTeams.put(attackChainTeam.getId(), teamSaved);
              } else {
                attackChainRunTeams.add(attackChainTeam);
              }
            });
    attackChainRun.setTeams(new ArrayList<>(attackChainRunTeams));

    attackChainRun
        .getAttackChainNodes()
        .forEach(
            attackChainNode -> {
              List<Team> teams = new ArrayList<>();
              attackChainNode
                  .getTeams()
                  .forEach(
                      team -> {
                        if (team.getContextual()) {
                          teams.add(contextualTeams.get(team.getId()));
                        } else {
                          teams.add(team);
                        }
                      });
              attackChainNode.setTeams(teams);
            });
    return contextualTeams;
  }

  private void getListOfDuplicatedAttackChainNodes(
      AttackChainRun attackChainRun, AttackChainRun attackChainRunOrigin) {
    List<AttackChainNode> attackChainNodeListForAttackChainRun =
        attackChainRunOrigin.getAttackChainNodes().stream()
            .map(
                attackChainNode ->
                    attackChainNodeDuplicateService.duplicateAttackChainNodeForAttackChainRun(
                        attackChainRun, attackChainNode))
            .toList();
    attackChainRun.setAttackChainNodes(new ArrayList<>(attackChainNodeListForAttackChainRun));
  }

  // 二开移除 Articles/Channels — attackChainRun duplication 不再处理 article 字段。

  private void getListOfVariables(
      AttackChainRun attackChainRun, AttackChainRun attackChainRunOrigin) {
    List<Variable> variables =
        variableService.variablesFromAttackChainRun(attackChainRunOrigin.getId());
    List<Variable> variableList =
        variables.stream()
            .map(
                variable -> {
                  Variable variable1 = new Variable();
                  variable1.setKey(variable.getKey());
                  variable1.setDescription(variable.getDescription());
                  variable1.setValue(variable.getValue());
                  variable1.setType(variable.getType());
                  variable1.setAttackChainRun(attackChainRun);
                  return variable1;
                })
            .toList();
    variableService.createVariables(variableList);
  }

  private void getLessonsCategories(
      AttackChainRun duplicatedAttackChainRun, AttackChainRun originalAttackChainRun) {
    List<LessonsCategory> duplicatedCategories = new ArrayList<>();
    for (LessonsCategory originalCategory : originalAttackChainRun.getLessonsCategories()) {
      LessonsCategory duplicatedCategory = new LessonsCategory();
      duplicatedCategory.setName(originalCategory.getName());
      duplicatedCategory.setDescription(originalCategory.getDescription());
      duplicatedCategory.setOrder(originalCategory.getOrder());
      duplicatedCategory.setAttackChainRun(duplicatedAttackChainRun);
      duplicatedCategory.setTeams(new ArrayList<>(originalCategory.getTeams()));

      List<LessonsQuestion> duplicatedQuestions = new ArrayList<>();
      for (LessonsQuestion originalQuestion : originalCategory.getQuestions()) {
        LessonsQuestion duplicatedQuestion = new LessonsQuestion();
        duplicatedQuestion.setCategory(originalQuestion.getCategory());
        duplicatedQuestion.setContent(originalQuestion.getContent());
        duplicatedQuestion.setExplanation(originalQuestion.getExplanation());
        duplicatedQuestion.setOrder(originalQuestion.getOrder());
        duplicatedQuestion.setCategory(duplicatedCategory);

        List<LessonsAnswer> duplicatedAnswers = new ArrayList<>();
        for (LessonsAnswer originalAnswer : originalQuestion.getAnswers()) {
          LessonsAnswer duplicatedAnswer = new LessonsAnswer();
          duplicatedAnswer.setUser(originalAnswer.getUser());
          duplicatedAnswer.setScore(originalAnswer.getScore());
          duplicatedAnswer.setPositive(originalAnswer.getPositive());
          duplicatedAnswer.setNegative(originalAnswer.getNegative());
          duplicatedAnswer.setQuestion(duplicatedQuestion);
          duplicatedAnswers.add(duplicatedAnswer);
        }
        duplicatedQuestion.setAnswers(duplicatedAnswers);
        duplicatedQuestions.add(duplicatedQuestion);
      }
      duplicatedCategory.setQuestions(duplicatedQuestions);
      duplicatedCategories.add(duplicatedCategory);
    }
    duplicatedAttackChainRun.setLessonsCategories(duplicatedCategories);
  }

  private void getObjectives(
      AttackChainRun duplicatedAttackChainRun, AttackChainRun originalAttackChainRun) {
    List<Objective> duplicatedObjectives = new ArrayList<>();
    for (Objective originalObjective : originalAttackChainRun.getObjectives()) {
      Objective duplicatedObjective = new Objective();
      duplicatedObjective.setTitle(originalObjective.getTitle());
      duplicatedObjective.setDescription(originalObjective.getDescription());
      duplicatedObjective.setPriority(originalObjective.getPriority());
      List<Evaluation> duplicatedEvaluations = new ArrayList<>();
      for (Evaluation originalEvaluation : originalObjective.getEvaluations()) {
        Evaluation duplicatedEvaluation = new Evaluation();
        duplicatedEvaluation.setScore(originalEvaluation.getScore());
        duplicatedEvaluation.setUser(originalEvaluation.getUser());
        duplicatedEvaluation.setObjective(duplicatedObjective);
        duplicatedEvaluations.add(duplicatedEvaluation);
      }
      duplicatedObjective.setEvaluations(duplicatedEvaluations);
      duplicatedObjective.setAttackChainRun(duplicatedAttackChainRun);
      duplicatedObjectives.add(duplicatedObjective);
    }
    duplicatedAttackChainRun.setObjectives(duplicatedObjectives);
  }

  private void duplicateGrants(@NotNull AttackChainRun target, @NotNull AttackChainRun source) {
    List<Grant> duplicatedGrants =
        grantService.duplicateGrants(source.getGrants(), target.getId(), SIMULATION);
    target.setGrants(duplicatedGrants);
  }

  private void duplicateTeamUsers(
      @NotNull AttackChainRun target,
      @NotNull AttackChainRun source,
      @NotNull Map<String, Team> contextualTeams) {
    attackChainRunTeamUserService.duplicateTeamUsers(
        target, source.getTeamUsers(), contextualTeams);
  }

  // -- EXERCISES --
  public List<AttackChainRunSimple> attackChainRuns() {
    // We get the attackChainRuns depending on whether or not we are granted or have the capa
    User currentUser = userService.currentUser();
    List<RawAttackChainRunSimple> attackChainRuns =
        currentUser.isAdminOrBypass()
                || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)
            ? attackChainRunRepository.rawAll()
            : attackChainRunRepository.rawAllGranted(currentUser().getId());
    return attackChainRunMapper.getAttackChainRunSimples(attackChainRuns);
  }

  public Page<AttackChainRunSimple> attackChainRuns(
      Specification<AttackChainRun> specification,
      Specification<AttackChainRun> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilderAndAttackChainRuns result =
        getCriteriaBuilderAndAttackChainRuns(specification, pageable, joinMap);

    setComputedAttributes(result.attackChainRuns());

    return getAttackChainRunSimples(specificationCount, pageable, result);
  }

  public Page<AttackChainRunSimple> attackChainRunsWithEmptyGlobalScore(
      Specification<AttackChainRun> specification,
      Specification<AttackChainRun> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilderAndAttackChainRuns result =
        getCriteriaBuilderAndAttackChainRuns(specification, pageable, joinMap);

    setComputedAttributesWithEmptyGlobalScore(result.attackChainRuns());

    return getAttackChainRunSimples(specificationCount, pageable, result);
  }

  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun changeAttackChainRunStatus(
      AttackChainRunStatus status, String attackChainRunId) {
    AttackChainRun attackChainRun =
        this.attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    // Check if next status is possible
    List<AttackChainRunStatus> nextPossibleStatus = attackChainRun.nextPossibleStatus();
    if (!nextPossibleStatus.contains(status)) {
      throw new UnsupportedOperationException(
          "Exercise can't support moving to status " + status.name());
    }
    boolean isCloseState =
        AttackChainRunStatus.CANCELED.equals(attackChainRun.getStatus())
            || AttackChainRunStatus.FINISHED.equals(attackChainRun.getStatus());

    if (isCloseState && AttackChainRunStatus.SCHEDULED.equals(status)) {
      resetAttackChainRun(attackChainRun);
    }
    // In case of manual start
    if (AttackChainRunStatus.SCHEDULED.equals(attackChainRun.getStatus())
        && AttackChainRunStatus.RUNNING.equals(status)) {
      this.throwIfAttackChainRunNotLaunchable(attackChainRun);
      Instant nextMinute = now().truncatedTo(MINUTES).plus(1, MINUTES);
      attackChainRun.setStart(nextMinute);
    }
    // If attackChainRun move from pause to running state,
    // we log the pause date to be able to recompute attackChainNode dates.
    if (AttackChainRunStatus.PAUSED.equals(attackChainRun.getStatus())
        && AttackChainRunStatus.RUNNING.equals(status)) {
      Instant lastPause =
          attackChainRun.getCurrentPause().orElseThrow(ElementNotFoundException::new);
      attackChainRun.setCurrentPause(null);
      pauseAttackChainRunService.endPauseByAttackChainRun(lastPause, attackChainRun);
    }
    // If pause is asked, just set the pause date.
    if (AttackChainRunStatus.RUNNING.equals(attackChainRun.getStatus())
        && AttackChainRunStatus.PAUSED.equals(status)) {
      attackChainRun.setCurrentPause(Instant.now());
    }
    // Cancelation
    if (AttackChainRunStatus.RUNNING.equals(attackChainRun.getStatus())
        && AttackChainRunStatus.CANCELED.equals(status)) {
      attackChainRun.setEnd(now());
    }
    attackChainRun.setUpdatedAt(now());
    attackChainRun.setStatus(status);
    return saveSimulation(attackChainRun);
  }

  private void resetAttackChainRun(AttackChainRun attackChainRun) {
    // 1. DELETE PAUSES
    pauseAttackChainRunService.deleteAllPauseByAttackChainRunId(attackChainRun.getId());

    // 2. RESET INJECTS (status, communications, findings, expectations, collect status)
    // Fetched separately from attackChainRun.getAttackChainNodes() for performance (avoids Eager
    // loading overhead)
    attackChainNodeService.resetAttackChainNodeByAttackChainRunId(attackChainRun.getId());

    // 3. RESET LESSONS ANSWERS
    lessonsService.resetLessonsAnswer(attackChainRun.getId());

    // 4. SCHEDULE MINIO CLEANUP (after commit to avoid cleanup on rollback)
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              fileService.deleteDirectory(attackChainRun.getId());
            } catch (Exception e) {
              log.error("Failed to delete directory for exercise {}", attackChainRun.getId(), e);
            }
          }
        });

    // 5. RESET EXERCISE DATES
    attackChainRun.setStart(null);
    attackChainRun.setEnd(null);
    attackChainRun.setCurrentPause(null);
  }

  /**
   * Save a simulation
   *
   * @param simulation simulation to save
   * @return the saved simulation
   */
  public AttackChainRun saveSimulation(AttackChainRun simulation) {
    return attackChainRunRepository.save(simulation);
  }

  public void throwIfAttackChainRunNotLaunchable(AttackChainRun attackChainRun) {
    attackChainRun
        .getAttackChainNodes()
        .forEach(attackChainNodeService::throwIfAttackChainNodeNotLaunchable);
  }

  public boolean checkIfTagRulesApplies(
      @NotNull final AttackChainRun attackChainRun, @NotNull final List<String> newTags) {
    return tagRuleService.checkIfRulesApply(
        attackChainRun.getTags().stream().map(Tag::getId).toList(), newTags);
  }

  private PageImpl<AttackChainRunSimple> getAttackChainRunSimples(
      Specification<AttackChainRun> specificationCount,
      Pageable pageable,
      CriteriaBuilderAndAttackChainRuns result) {
    // -- Count Query --
    Long total =
        countQuery(result.cb(), this.entityManager, AttackChainRun.class, specificationCount);

    return new PageImpl<>(result.attackChainRuns(), pageable, total);
  }

  private CriteriaBuilderAndAttackChainRuns getCriteriaBuilderAndAttackChainRuns(
      Specification<AttackChainRun> specification,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<AttackChainRun> attackChainRunRoot = cq.from(AttackChainRun.class);

    // -- Sorting --
    SortUtilsCriteriaBuilder.SortSpecification sortSpecification =
        toSortCriteriaBuilderWithNullHandling(cb, attackChainRunRoot, pageable.getSort());
    cq.orderBy(sortSpecification.orders());

    // -- Select
    List<Selection<?>> selections = getCriteriaBuilderSelections(cb, attackChainRunRoot, joinMap);
    cq.groupBy(Collections.singletonList(attackChainRunRoot.get("id")));
    selections.addAll(sortSpecification.selections());
    cq.multiselect(selections).distinct(true);

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(attackChainRunRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // Type Query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<AttackChainRunSimple> attackChainRuns = execution(query);

    return new CriteriaBuilderAndAttackChainRuns(cb, attackChainRuns);
  }

  public List<FilterUtilsJpa.Option> getOptionsByNameLinkedToFindings(
      String searchText, String simulationOrAttackChainId, Pageable pageable) {
    String trimmedSearchText = org.apache.commons.lang3.StringUtils.trimToNull(searchText);
    String trimmedSimulationOrAttackChainId =
        org.apache.commons.lang3.StringUtils.trimToNull(simulationOrAttackChainId);

    List<Object[]> results;

    if (trimmedSimulationOrAttackChainId == null) {
      results =
          attackChainRunRepository.findAllOptionByNameLinkedToFindings(trimmedSearchText, pageable);
    } else {
      results =
          attackChainRunRepository.findAllOptionByNameLinkedToFindingsWithContext(
              trimmedSimulationOrAttackChainId, trimmedSearchText, pageable);
    }
    return results.stream()
        .map(i -> new FilterUtilsJpa.Option((String) i[0], (String) i[1]))
        .toList();
  }

  public List<NodeExpectationResultsByAttackPattern> extractExpectationResultsByAttackPattern(
      String attackChainRunId) {
    AttackChainRun attackChainRun = attackChainRun(attackChainRunId);
    return resultUtils.computeNodeExpectationResults(attackChainRun.getAttackChainNodes());
  }

  private record CriteriaBuilderAndAttackChainRuns(
      CriteriaBuilder cb, List<AttackChainRunSimple> attackChainRuns) {}

  // -- SELECT --
  private List<Selection<?>> getCriteriaBuilderSelections(
      CriteriaBuilder cb,
      Root<AttackChainRun> attackChainRunRoot,
      Map<String, Join<Base, Base>> joinMap) {
    List<Selection<?>> selections = new ArrayList<>();

    // Array aggregations
    Join<Base, Base> attackChainRunTagsJoin = attackChainRunRoot.join("tags", JoinType.LEFT);
    joinMap.put("tags", attackChainRunTagsJoin);
    Expression<String[]> tagIdsExpression =
        arrayAggOnId((HibernateCriteriaBuilder) cb, attackChainRunTagsJoin);

    Join<Base, Base> attackChainNodesJoin =
        attackChainRunRoot.join("attackChainNodes", JoinType.LEFT);
    joinMap.put("attackChainNodes", attackChainNodesJoin);
    Expression<String[]> attackChainNodeIdsExpression =
        arrayAggOnId((HibernateCriteriaBuilder) cb, attackChainNodesJoin);

    // SELECTIONS
    selections.add(attackChainRunRoot.get("id").alias("exercise_id"));
    selections.add(attackChainRunRoot.get("name").alias("exercise_name"));
    selections.add(attackChainRunRoot.get("status").alias("exercise_status"));
    selections.add(attackChainRunRoot.get("subtitle").alias("exercise_subtitle"));
    selections.add(attackChainRunRoot.get("category").alias("exercise_category"));
    selections.add(attackChainRunRoot.get("start").alias("exercise_start_date"));
    selections.add(attackChainRunRoot.get("end").alias("exercise_end_date"));
    selections.add(attackChainRunRoot.get("updatedAt").alias("exercise_updated_at"));
    selections.add(tagIdsExpression.alias("exercise_tags"));
    selections.add(attackChainNodeIdsExpression.alias("exercise_injects"));

    // GROUP BY
    return selections;
  }

  // -- EXECUTION --
  private List<AttackChainRunSimple> execution(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple -> {
              AttackChainRunSimple attackChainRunSimple = new AttackChainRunSimple();
              attackChainRunSimple.setId(tuple.get("exercise_id", String.class));
              attackChainRunSimple.setName(tuple.get("exercise_name", String.class));
              attackChainRunSimple.setStatus(
                  tuple.get("exercise_status", AttackChainRunStatus.class));
              attackChainRunSimple.setSubtitle(tuple.get("exercise_subtitle", String.class));
              attackChainRunSimple.setCategory(tuple.get("exercise_category", String.class));
              attackChainRunSimple.setStart(tuple.get("exercise_start_date", Instant.class));
              attackChainRunSimple.setUpdatedAt(tuple.get("exercise_updated_at", Instant.class));
              attackChainRunSimple.setTagIds(
                  new HashSet<>(Arrays.asList(tuple.get("exercise_tags", String[].class))));
              attackChainRunSimple.setAttackChainNodeIds(
                  tuple.get("exercise_injects", String[].class));
              return attackChainRunSimple;
            })
        .toList();
  }

  // -- COMPUTED ATTRIBUTES --
  private void setComputedAttributes(List<AttackChainRunSimple> originalAttackChainRuns) {
    List<AttackChainRunSimple> attackChainRuns = getAttackChainRunsWithId(originalAttackChainRuns);
    if (attackChainRuns.isEmpty()) {
      return;
    }

    Set<String> attackChainRunIds = getAttackChainRunIds(attackChainRuns);
    MappingsByAttackChainRunIds mappingsByAttackChainRunIds =
        getResultsByAttackChainRunIds(attackChainRunIds);

    Map<String, List<RawAttackChainNodeExpectation>> expectationsByAttackChainRunIds =
        getExpectationsByAttackChainRunId(attackChainRunIds);

    for (AttackChainRunSimple attackChainRun : attackChainRuns) {
      setGlobalScore(attackChainRun, expectationsByAttackChainRunIds);

      setTargets(attackChainRun, mappingsByAttackChainRunIds);
    }
  }

  private void setComputedAttributesWithEmptyGlobalScore(
      List<AttackChainRunSimple> originalAttackChainRuns) {
    List<AttackChainRunSimple> attackChainRuns = getAttackChainRunsWithId(originalAttackChainRuns);
    if (attackChainRuns.isEmpty()) {
      return;
    }

    MappingsByAttackChainRunIds mappingsByAttackChainRunIds =
        getResultsByAttackChainRunIds(getAttackChainRunIds(attackChainRuns));

    for (AttackChainRunSimple attackChainRun : attackChainRuns) {
      attackChainRun.setExpectationResultByTypes(new ArrayList<>());

      setTargets(attackChainRun, mappingsByAttackChainRunIds);
    }
  }

  private static List<AttackChainRunSimple> getAttackChainRunsWithId(
      List<AttackChainRunSimple> attackChainRuns) {
    return attackChainRuns.stream()
        .filter(attackChainRun -> attackChainRun.getId() != null)
        .toList();
  }

  private static Set<String> getAttackChainRunIds(List<AttackChainRunSimple> attackChainRuns) {
    return attackChainRuns.stream().map(AttackChainRunSimple::getId).collect(Collectors.toSet());
  }

  private MappingsByAttackChainRunIds getResultsByAttackChainRunIds(Set<String> attackChainRunIds) {
    Map<String, List<Object[]>> teamsByAttackChainRunIds =
        getTeamsOrAssetsOrAssetGroupsByAttackChainRunIds(
            teamRepository.teamsByAttackChainRunIds(attackChainRunIds));

    Map<String, List<Object[]>> assetsByAttackChainRunIds =
        getTeamsOrAssetsOrAssetGroupsByAttackChainRunIds(
            assetRepository.assetsByAttackChainRunIds(attackChainRunIds));

    Map<String, List<Object[]>> assetGroupByAttackChainRunIds =
        getTeamsOrAssetsOrAssetGroupsByAttackChainRunIds(
            assetGroupRepository.assetGroupsByAttackChainRunIds(attackChainRunIds));

    return new MappingsByAttackChainRunIds(
        teamsByAttackChainRunIds, assetsByAttackChainRunIds, assetGroupByAttackChainRunIds);
  }

  private Map<String, List<Object[]>> getTeamsOrAssetsOrAssetGroupsByAttackChainRunIds(
      List<Object[]> rawTeamsOrAssetsOrAssetGroups) {
    return ofNullable(rawTeamsOrAssetsOrAssetGroups).orElse(emptyList()).stream()
        .filter(
            rawTeamOrAssetOrAssetGroup ->
                0 < rawTeamOrAssetOrAssetGroup.length && rawTeamOrAssetOrAssetGroup[0] != null)
        .collect(
            Collectors.groupingBy(
                rawTeamOrAssetOrAssetGroup -> (String) rawTeamOrAssetOrAssetGroup[0]));
  }

  private record MappingsByAttackChainRunIds(
      Map<String, List<Object[]>> teamsByAttackChainRunIds,
      Map<String, List<Object[]>> assetsByAttackChainRunIds,
      Map<String, List<Object[]>> assetGroupsByAttackChainRunIds) {}

  private Map<String, List<RawAttackChainNodeExpectation>> getExpectationsByAttackChainRunId(
      Set<String> attackChainRunIds) {
    return ofNullable(
            attackChainNodeExpectationRepository.rawForComputeGlobalByAttackChainRunIds(
                attackChainRunIds))
        .orElse(emptyList())
        .stream()
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(RawAttackChainNodeExpectation::getExercise_id));
  }

  private void setGlobalScore(
      AttackChainRunSimple attackChainRun,
      Map<String, List<RawAttackChainNodeExpectation>> expectationsByAttackChainRunIds) {
    List<RawAttackChainNodeExpectation> expectations =
        expectationsByAttackChainRunIds.getOrDefault(attackChainRun.getId(), emptyList());
    HashSet<String> attackChainNodeIds =
        new HashSet<>(Arrays.asList(attackChainRun.getAttackChainNodeIds()));

    attackChainRun.setExpectationResultByTypes(
        attackChainNodeExpectationMapper.extractExpectationResultByTypesFromRaw(
            attackChainNodeIds, expectations));
  }

  private void setTargets(
      AttackChainRunSimple attackChainRun,
      MappingsByAttackChainRunIds mappingsByAttackChainRunIds) {
    List<TargetSimple> allTargets =
        Stream.of(
                getTargets(
                    attackChainRun,
                    mappingsByAttackChainRunIds.teamsByAttackChainRunIds,
                    TargetType.TEAMS)
                    .stream(),
                getTargets(
                    attackChainRun,
                    mappingsByAttackChainRunIds.assetsByAttackChainRunIds,
                    TargetType.ASSETS)
                    .stream(),
                getTargets(
                    attackChainRun,
                    mappingsByAttackChainRunIds.assetGroupsByAttackChainRunIds,
                    TargetType.ASSETS_GROUPS)
                    .stream())
            .flatMap(Function.identity())
            .toList();
    attackChainRun.getTargets().addAll(allTargets);
  }

  private List<TargetSimple> getTargets(
      AttackChainRunSimple attackChainRun,
      Map<String, List<Object[]>> targetsByAttackChainRunIds,
      TargetType targetType) {
    return attackChainNodeMapper.toTargetSimple(
        targetsByAttackChainRunIds.getOrDefault(attackChainRun.getId(), emptyList()), targetType);
  }

  // -- SCENARIO EXERCISES --
  public Iterable<AttackChainRunSimple> attackChainAttackChainRuns(@NotBlank String attackChainId) {
    List<RawAttackChainRunSimple> attackChainRuns =
        attackChainRunRepository.rawAllByAttackChainIds(List.of(attackChainId));
    return attackChainRunMapper.getAttackChainRunSimples(attackChainRuns);
  }

  // -- GLOBAL RESULTS --
  public List<ExpectationResultsByType> getGlobalResults(@NotBlank String attackChainRunId) {
    return resultUtils.computeGlobalExpectationResults(
        attackChainRunRepository.findAttackChainNodesByAttackChainRun(attackChainRunId));
  }

  public AttackChainRunsGlobalScoresOutput getAttackChainRunsGlobalScores(
      AttackChainRunsGlobalScoresInput input) {
    Map<String, List<ExpectationResultsByType>> globalScoresByAttackChainRunIds =
        input.attackChainRunIds().stream()
            .collect(Collectors.toMap(Function.identity(), this::getGlobalResults));
    return new AttackChainRunsGlobalScoresOutput(globalScoresByAttackChainRunIds);
  }

  // -- TEAMS --
  @Transactional(rollbackFor = Exception.class)
  public Iterable<TeamOutput> removeTeams(
      @NotBlank final String attackChainRunId, @NotNull final List<String> teamIds) {
    // Remove teams from attackChainRun
    this.attackChainRunRepository.removeTeams(attackChainRunId, teamIds);
    // Remove only associations for this attackChainRun
    this.attackChainRunTeamUserRepository.deleteByAttackChainRunIdAndTeamIds(
        attackChainRunId, teamIds);
    // Remove all association between attackChainNodes and teams
    this.attackChainNodeRepository.removeTeamsForAttackChainRun(attackChainRunId, teamIds);
    // Remove all association between lessons learned and teams
    this.lessonsCategoryRepository.removeTeamsForAttackChainRun(attackChainRunId, teamIds);
    return teamService.find(fromIds(teamIds));
  }

  @Transactional(rollbackFor = Exception.class)
  public List<TeamOutput> replaceTeams(
      @NotBlank final String attackChainRunId, @NotNull final List<String> teamIds) {
    AttackChainRun attackChainRun = this.attackChainRun(attackChainRunId);
    Set<String> previousTeamIds =
        attackChainRun.getTeams().stream().map(Team::getId).collect(Collectors.toSet());
    Set<String> targetTeamIds = new LinkedHashSet<>(teamIds);

    Set<String> removedTeamIds = new HashSet<>(previousTeamIds);
    removedTeamIds.removeAll(targetTeamIds);
    if (!removedTeamIds.isEmpty()) {
      List<String> removedTeamIdsList = new ArrayList<>(removedTeamIds);
      this.attackChainRunTeamUserRepository.deleteByAttackChainRunIdAndTeamIds(
          attackChainRunId, removedTeamIdsList);
      this.attackChainNodeRepository.removeTeamsForAttackChainRun(
          attackChainRunId, removedTeamIdsList);
      this.lessonsCategoryRepository.removeTeamsForAttackChainRun(
          attackChainRunId, removedTeamIdsList);
    }

    // Replace teams from attackChainRun
    List<Team> teams = fromIterable(this.teamRepository.findAllById(targetTeamIds));
    attackChainRun.setTeams(teams);
    this.attackChainRunRepository.save(attackChainRun);

    List<String> teamIdsAdded =
        targetTeamIds.stream().filter(id -> !previousTeamIds.contains(id)).toList();

    List<Team> teamsAdded = fromIterable(this.teamRepository.findAllById(teamIdsAdded));

    // Enable user
    teamsAdded.forEach(
        team -> {
          List<String> playerIds = team.getUsers().stream().map(User::getId).toList();
          this.enablePlayers(attackChainRunId, team, playerIds);
        });

    // You must return all the modified teams to ensure the frontend store updates correctly
    List<String> modifiedTeamIds =
        Stream.concat(previousTeamIds.stream(), teams.stream().map(Team::getId))
            .distinct()
            .toList();
    return teamService.find(fromIds(modifiedTeamIds));
  }

  public AttackChainRun enablePlayers(
      @NotBlank final String attackChainRunId,
      @NotNull final Team team,
      @NotNull final List<String> playerIds) {
    AttackChainRun attackChainRun =
        attackChainRunRepository
            .findById(attackChainRunId)
            .orElseThrow(ElementNotFoundException::new);
    playerIds.forEach(
        playerId -> {
          boolean alreadyLinked =
              this.attackChainRunTeamUserRepository.existsByAttackChainRunIdAndTeamIdAndUserId(
                  attackChainRunId, team.getId(), playerId);
          if (alreadyLinked) {
            return;
          }
          AttackChainRunTeamUser attackChainRunTeamUser = new AttackChainRunTeamUser();
          attackChainRunTeamUser.setAttackChainRun(attackChainRun);
          attackChainRunTeamUser.setTeam(team);
          attackChainRunTeamUser.setUser(this.userRepository.findById(playerId).orElseThrow());
          this.attackChainRunTeamUserRepository.save(attackChainRunTeamUser);
        });
    return attackChainRun;
  }

  /**
   * Update the simulation and each of the attackChainNodes to add default asset groups
   *
   * @param attackChainRun
   * @param currentTags list of the tags before the update
   * @return
   */
  @Transactional
  public AttackChainRun updateExercice(
      @NotNull final AttackChainRun attackChainRun,
      @NotNull final Set<Tag> currentTags,
      boolean applyRule) {
    if (applyRule) {
      // Get asset groups from the TagRule of the added tags
      List<AssetGroup> defaultAssetGroupsToAdd =
          tagRuleService.getAssetGroupsFromTagIds(
              attackChainRun.getTags().stream()
                  .filter(tag -> !currentTags.contains(tag))
                  .map(Tag::getId)
                  .toList());

      // Add the default asset groups to the attackChainNodes
      attackChainRun.getAttackChainNodes().stream()
          .filter(
              attackChainNode ->
                  this.attackChainNodeService.canApplyTargetType(
                      attackChainNode, TargetType.ASSETS_GROUPS))
          .forEach(
              attackChainNode ->
                  attackChainNodeService.applyDefaultAssetGroupsToAttackChainNode(
                      attackChainNode.getId(), defaultAssetGroupsToAdd));
    }
    attackChainRun.setUpdatedAt(now());
    return attackChainRunRepository.save(attackChainRun);
  }

  public AttackChainRun previousFinishedSimulation(
      @NotBlank final String attackChainId, @NotNull final Instant instant) {
    return this.attackChainRunRepository
        .findAll(fromAttackChain(attackChainId).and(finished()).and(closestBefore(instant)))
        .stream()
        .findFirst()
        .orElse(null);
  }

  public boolean isFinished(AttackChainRun attackChainRun) {
    return AttackChainRunStatus.FINISHED.equals(attackChainRun.getStatus());
  }

  public boolean isThereAScoreDegradation(
      Map<ExpectationType, ExpectationResultsByType> lastSimulationResultsMap,
      Map<ExpectationType, ExpectationResultsByType> secondLastSimulationResultsMap) {

    for (Map.Entry<ExpectationType, ExpectationResultsByType> entry :
        lastSimulationResultsMap.entrySet()) {
      ExpectationResultsByType lastSimulationResultsByType = entry.getValue();
      ExpectationType type = entry.getKey();

      // we ignore manual expectation
      if (ExpectationType.HUMAN_RESPONSE.equals(type)) {
        break;
      }

      ExpectationResultsByType secondLastSimulationResultsByType =
          secondLastSimulationResultsMap.get(type);

      // we ignore if one of the 2 expectation is still PENDING
      if (AttackChainNodeExpectation.EXPECTATION_STATUS.PENDING.equals(
              lastSimulationResultsByType.avgResult())
          || AttackChainNodeExpectation.EXPECTATION_STATUS.PENDING.equals(
              secondLastSimulationResultsByType.avgResult())) {
        break;
      }

      float lastSimulationScore =
          AttackChainStatisticService.getRoundedPercentage(lastSimulationResultsByType);
      float secondLastSimulationScore =
          AttackChainStatisticService.getRoundedPercentage(secondLastSimulationResultsByType);
      if (lastSimulationScore < secondLastSimulationScore) {
        return true;
      }
    }
    return false;
  }

  // -- OPTION --

  public List<FilterUtilsJpa.Option> findAllAsOptions(
      final Specification<AttackChainRun> specification, final String searchText) {
    return fromIterable(
            attackChainRunRepository.findAll(
                specification.and(byName(searchText)), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
