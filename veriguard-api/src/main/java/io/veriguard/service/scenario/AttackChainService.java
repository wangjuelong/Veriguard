package io.veriguard.service.scenario;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.criteria.GenericCriteria.countQuery;
import static io.veriguard.database.specification.AttackChainSpecification.findGrantedFor;
import static io.veriguard.database.specification.TeamSpecification.fromIds;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.rest.attack_chain.utils.AttackChainUtils.handleCustomFilter;
import static io.veriguard.service.ImportService.EXPORT_ENTRY_ATTACHMENT;
import static io.veriguard.service.ImportService.EXPORT_ENTRY_SCENARIO;
import static io.veriguard.utils.StringUtils.duplicateString;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.veriguard.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.model.*;
import io.veriguard.database.raw.*;
import io.veriguard.database.repository.*;
import io.veriguard.database.specification.AttackChainSpecification;
import io.veriguard.export.Mixins;
import io.veriguard.healthcheck.dto.HealthCheck;
import io.veriguard.healthcheck.utils.HealthCheckUtils;
import io.veriguard.helper.ObjectMapperHelper;
import io.veriguard.rest.attack_chain.export.AttackChainFileExport;
import io.veriguard.rest.attack_chain.form.AttackChainSimple;
import io.veriguard.rest.attack_chain.response.AttackChainOutput;
import io.veriguard.rest.attack_chain.response.AttackChainTeamUserOutput;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeDuplicateService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.attack_chain_run.exports.AttackChainRunFileExport;
import io.veriguard.rest.attack_chain_run.exports.VariableMixin;
import io.veriguard.rest.attack_chain_run.exports.VariableWithValueMixin;
import io.veriguard.rest.attack_chain_run.form.AttackChainRunSimple;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.kill_chain_phase.response.KillChainPhaseOutput;
import io.veriguard.rest.team.output.TeamOutput;
import io.veriguard.service.*;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.mapper.AttackChainMapper;
import io.veriguard.utils.mapper.AttackChainRunMapper;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.hibernate.Hibernate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Service
@Slf4j
@Validated
public class AttackChainService {

  @Value("${veriguard.mail.imap.enabled}")
  private boolean imapEnabled;

  @Value("${veriguard.mail.imap.username}")
  private String imapUsername;

  @Resource private VeriguardConfig veriguardConfig;

  @PersistenceContext private EntityManager entityManager;

  private final AttackChainRepository attackChainRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final DocumentRepository documentRepository;
  private final AttackChainTeamUserRepository attackChainTeamUserRepository;

  private final AttackChainRunMapper attackChainRunMapper;

  private final VariableService variableService;
  private final TeamService teamService;
  private final FileService fileService;
  private final AttackChainNodeDuplicateService attackChainNodeDuplicateService;
  private final TagRuleService tagRuleService;
  private final AttackChainNodeService attackChainNodeService;
  private final UserService userService;

  private final AttackChainNodeRepository attackChainNodeRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;

  private final HealthCheckUtils healthCheckUtils;

  private final AttackChainMapper attackChainMapper;

  @Transactional
  public AttackChain createAttackChain(@NotNull final AttackChain attackChain) {
    computeEmails(attackChain);
    return this.attackChainRepository.save(attackChain);
  }

  public void computeEmails(@NotNull AttackChain attackChain) {
    if (!hasText(attackChain.getFrom())) {
      if (this.imapEnabled) {
        attackChain.setFrom(this.imapUsername);
        attackChain.setReplyTos(new ArrayList<>(Arrays.asList(this.imapUsername)));
      } else {
        attackChain.setFrom(this.veriguardConfig.getDefaultMailer());
        attackChain.setReplyTos(
            new ArrayList<>(Arrays.asList(this.veriguardConfig.getDefaultReplyTo())));
      }
    }
  }

  public List<AttackChainSimple> attackChains() {
    List<RawAttackChainSimple> attackChains;
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      attackChains = fromIterable(this.attackChainRepository.rawAll());
    } else {
      attackChains = this.attackChainRepository.rawAllGranted(currentUser().getId());
    }
    return attackChains.stream().map(AttackChainSimple::fromRawAttackChain).toList();
  }

  public List<AttackChainSimple> attackChains(final List<String> attackChainIds) {
    List<RawAttackChainSimple> attackChains;
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      attackChains = fromIterable(this.attackChainRepository.rawByAttackChainIds(attackChainIds));
    } else {
      attackChains =
          this.attackChainRepository.rawGrantedByAttackChainIds(
              currentUser().getId(), attackChainIds);
    }
    return attackChains.stream().map(AttackChainSimple::fromRawAttackChain).toList();
  }

  public Page<RawPaginationAttackChain> attackChains(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();

    // Compute custom filter
    UnaryOperator<Specification<AttackChain>> deepFilterSpecification =
        handleCustomFilter(searchPaginationInput);

    // Compute find all method
    TriFunction<
            Specification<AttackChain>,
            Specification<AttackChain>,
            Pageable,
            Page<RawPaginationAttackChain>>
        findAll = getFindAllFunction(deepFilterSpecification, joinMap);

    // Compute pagination from find all
    return buildPaginationCriteriaBuilder(
        findAll, searchPaginationInput, AttackChain.class, joinMap);
  }

  private TriFunction<
          Specification<AttackChain>,
          Specification<AttackChain>,
          Pageable,
          Page<RawPaginationAttackChain>>
      getFindAllFunction(
          UnaryOperator<Specification<AttackChain>> deepFilterSpecification,
          Map<String, Join<Base, Base>> joinMap) {
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      return (specification, specificationCount, pageable) ->
          this.findAllWithCriteriaBuilder(
              deepFilterSpecification.apply(specification),
              deepFilterSpecification.apply(specificationCount),
              pageable,
              joinMap);
    } else {
      return (specification, specificationCount, pageable) ->
          this.findAllWithCriteriaBuilder(
              findGrantedFor(currentUser().getId())
                  .and(deepFilterSpecification.apply(specification)),
              findGrantedFor(currentUser().getId())
                  .and(deepFilterSpecification.apply(specificationCount)),
              pageable,
              joinMap);
    }
  }

  private Page<RawPaginationAttackChain> findAllWithCriteriaBuilder(
      Specification<AttackChain> specification,
      Specification<AttackChain> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // -- Create Query --
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    // FROM
    Root<AttackChain> attackChainRoot = cq.from(AttackChain.class);
    // Join on TAG
    Join<Base, Base> attackChainTagsJoin = attackChainRoot.join("tags", JoinType.LEFT);
    joinMap.put("tags", attackChainTagsJoin);
    Expression<String> nullString = cb.nullLiteral(String.class);
    Expression<String[]> arr =
        ((HibernateCriteriaBuilder) cb).arrayAgg(null, attackChainTagsJoin.get("id"));
    Expression<String[]> tagIdsExpression =
        ((HibernateCriteriaBuilder) cb).arrayRemove(arr, nullString);

    // Join on INJECT and INJECTOR CONTRACT
    Join<Base, Base> attackChainNodesJoin = attackChainRoot.join("injects", JoinType.LEFT);
    joinMap.put("injects", attackChainNodesJoin);
    Join<Base, Base> nodeExecutorsContractsJoin =
        attackChainNodesJoin.join("injectorContract", JoinType.LEFT);
    joinMap.put("injects.injectorContract", nodeExecutorsContractsJoin);
    Expression<String[]> platformExpression =
        cb.function("array_union_agg", String[].class, nodeExecutorsContractsJoin.get("platforms"));
    // SELECT
    cq.multiselect(
            attackChainRoot.get("id").alias("scenario_id"),
            attackChainRoot.get("name").alias("scenario_name"),
            attackChainRoot.get("description").alias("scenario_description"),
            attackChainRoot.get("severity").alias("scenario_severity"),
            attackChainRoot.get("category").alias("scenario_category"),
            attackChainRoot.get("recurrence").alias("scenario_recurrence"),
            attackChainRoot.get("updatedAt").alias("scenario_updated_at"),
            tagIdsExpression.alias("scenario_tags"),
            platformExpression.alias("scenario_platforms"))
        .distinct(true);
    // Group By
    cq.groupBy(attackChainRoot.get("id"));

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(attackChainRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, attackChainRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<RawPaginationAttackChain> attackChains =
        query.getResultList().stream()
            .map(
                tuple ->
                    new RawPaginationAttackChain(
                        tuple.get("scenario_id", String.class),
                        tuple.get("scenario_name", String.class),
                        tuple.get("scenario_description", String.class),
                        tuple.get("scenario_severity", AttackChain.SEVERITY.class),
                        tuple.get("scenario_category", String.class),
                        tuple.get("scenario_recurrence", String.class),
                        tuple.get("scenario_updated_at", Instant.class),
                        tuple.get("scenario_tags", String[].class),
                        tuple.get("scenario_platforms", String[].class)))
            .toList();

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, AttackChain.class, specificationCount);

    return new PageImpl<>(attackChains, pageable, total);
  }

  public void throwIfAttackChainNotLaunchable(AttackChain attackChain) {
    attackChain
        .getAttackChainNodes()
        .forEach(attackChainNodeService::throwIfAttackChainNodeNotLaunchable);
  }

  /** AttackChain is recurring AND end date is after now */
  public List<AttackChain> recurringAttackChains(@NotNull final Instant instant) {
    return this.attackChainRepository.findAll(
        AttackChainSpecification.isRecurring()
            .and(AttackChainSpecification.recurrenceStopDateAfter(instant)));
  }

  /** AttackChain is recurring AND start date is before now OR stop date is before now */
  public List<AttackChain> potentialOutdatedRecurringAttackChain(@NotNull final Instant instant) {
    return this.attackChainRepository.findAll(
        AttackChainSpecification.isRecurring()
            .and(
                AttackChainSpecification.recurrenceStartDateBefore(instant)
                    .or(AttackChainSpecification.recurrenceStopDateBefore(instant))));
  }

  public AttackChain attackChain(@NotBlank final String attackChainId) {
    return this.attackChainRepository
        .findById(attackChainId)
        .orElseThrow(() -> new ElementNotFoundException("Scenario not found"));
  }

  public AttackChainOutput getAttackChainById(@NotBlank final String attackChainId) {
    ObjectMapper objectMapper = new ObjectMapper();
    RawAttackChain rawAttackChain = this.attackChainRepository.getAttackChainById(attackChainId);
    if (rawAttackChain == null) {
      throw new ElementNotFoundException("Scenario not found");
    }
    Set<KillChainPhaseOutput> killChainPhases = new HashSet<>();
    if (rawAttackChain.getScenario_kill_chain_phases() != null) {
      try {
        killChainPhases =
            objectMapper.readValue(
                rawAttackChain.getScenario_kill_chain_phases(), new TypeReference<>() {});
      } catch (JsonProcessingException e) {
        log.error("Error reading killChainPhases from scenario id {}", attackChainId, e);
      }
    }
    Set<AttackChainTeamUserOutput> attackChainTeamUsers = new HashSet<>();
    if (rawAttackChain.getScenario_teams_users() != null) {
      try {
        attackChainTeamUsers =
            objectMapper.readValue(
                rawAttackChain.getScenario_teams_users(), new TypeReference<>() {});
      } catch (JsonProcessingException e) {
        log.error("Error reading scenarioTeamUsers from scenario id {}", attackChainId, e);
      }
    }
    return attackChainMapper.toAttackChainOutput(
        rawAttackChain, killChainPhases, attackChainTeamUsers);
  }

  public AttackChain attackChainFromSimulationId(@NotBlank final String simulationId) {
    return this.attackChainRepository
        .findByAttackChainRuns_Id(simulationId)
        .orElseThrow(
            () ->
                new ElementNotFoundException("Scenario not found for simulation: " + simulationId));
  }

  @Transactional(readOnly = true)
  public AttackChainRunSimple latestAttackChainRunByExternalReference(
      @NotBlank final String attackChainExternalReference) {
    Optional<RawAttackChainRunSimple> latestEndedAttackChainRun =
        attackChainRepository.rawAllByExternalReference(attackChainExternalReference).stream()
            .filter(rawAttackChainRun -> rawAttackChainRun.getExercise_end_date() != null)
            .max(Comparator.comparing(RawAttackChainRunSimple::getExercise_end_date));

    return latestEndedAttackChainRun
        .map(attackChainRunMapper::getAttackChainRunSimple)
        .orElseThrow(() -> new ElementNotFoundException("Latest exercise not found"));
  }

  public AttackChain updateAttackChain(@NotNull final AttackChain attackChain) {
    return this.updateAttackChain(attackChain, null, false);
  }

  /**
   * Update the attackChain and each of the attackChainNodes to add default asset groups
   *
   * @param attackChain
   * @param currentTags list of the tags before the update
   * @return
   */
  @Transactional
  public AttackChain updateAttackChain(
      @NotNull final AttackChain attackChain, Set<Tag> currentTags, boolean applyRule) {
    if (applyRule) {
      // Get asset groups from the TagRule of the added tags
      List<AssetGroup> defaultAssetGroupsToAdd =
          tagRuleService.getAssetGroupsFromTagIds(
              attackChain.getTags().stream()
                  .filter(tag -> !currentTags.contains(tag))
                  .map(Tag::getId)
                  .toList());

      // Add the default asset groups to/from the attackChainNodes
      attackChain.getAttackChainNodes().stream()
          .filter(
              attackChainNode ->
                  this.attackChainNodeService.canApplyTargetType(
                      attackChainNode, TargetType.ASSETS_GROUPS))
          .forEach(
              attackChainNode ->
                  attackChainNodeService.applyDefaultAssetGroupsToAttackChainNode(
                      attackChainNode.getId(), defaultAssetGroupsToAdd));
    }
    attackChain.setUpdatedAt(now());
    return this.attackChainRepository.save(attackChain);
  }

  public void updateAttackChains(@NotNull final List<AttackChain> attackChains) {
    attackChains.forEach(attackChain -> attackChain.setUpdatedAt(now()));
    this.attackChainRepository.saveAll(attackChains);
  }

  public void deleteAttackChain(@NotBlank final String attackChainId) {
    this.attackChainRepository.deleteById(attackChainId);
  }

  // -- EXPORT --

  @Transactional
  public void exportAttackChain(
      @NotBlank final String attackChainId,
      final boolean isWithTeams,
      final boolean isWithPlayers,
      final boolean isWithVariableValues,
      HttpServletResponse response)
      throws IOException {
    ObjectMapper objectMapper = ObjectMapperHelper.veriguardJsonMapper();
    AttackChain attackChain = this.attackChain(attackChainId);

    // Start exporting attackChain
    AttackChainFileExport attackChainFileExport = new AttackChainFileExport();
    attackChainFileExport.setVersion(1);
    // Add AttackChain
    attackChainFileExport.setAttackChain(attackChain);
    objectMapper.addMixIn(AttackChain.class, Mixins.AttackChain.class);
    List<Tag> attackChainTags = new ArrayList<>(attackChain.getTags());
    // Add Objectives
    attackChainFileExport.setObjectives(attackChain.getObjectives());
    objectMapper.addMixIn(Objective.class, Mixins.Objective.class);
    // Add Lesson Categories
    attackChainFileExport.setLessonsCategories(attackChain.getLessonsCategories());
    objectMapper.addMixIn(LessonsCategory.class, Mixins.LessonsCategory.class);
    // Add Lessons Questions
    List<LessonsQuestion> lessonsQuestions =
        attackChain.getLessonsCategories().stream()
            .flatMap(category -> category.getQuestions().stream())
            .toList();
    attackChainFileExport.setLessonsQuestions(lessonsQuestions);
    objectMapper.addMixIn(LessonsQuestion.class, Mixins.LessonsQuestion.class);
    // Add Variables
    List<Variable> variables = this.variableService.variablesFromAttackChain(attackChainId);
    attackChainFileExport.setVariables(variables);
    if (isWithVariableValues) {
      objectMapper.addMixIn(Variable.class, VariableWithValueMixin.class);
    } else {
      objectMapper.addMixIn(Variable.class, VariableMixin.class);
    }

    // Add Documents
    List<Document> documentExports = new ArrayList<>();
    documentExports.addAll(attackChain.getDocuments());
    documentExports.addAll(
        attackChain.getAttackChainNodes().stream()
            .flatMap(
                attackChainNode -> {
                  if (attackChainNode.getPayload().isEmpty()) {
                    return Stream.of();
                  }
                  Payload pl = attackChainNode.getPayload().get();
                  return pl.getAttachedDocument().isPresent()
                      ? Stream.of(pl.getAttachedDocument().get())
                      : Stream.of();
                })
            .toList());

    attackChainFileExport.setDocuments(documentExports);
    objectMapper.addMixIn(Document.class, Mixins.Document.class);
    attackChainTags.addAll(
        attackChain.getDocuments().stream().flatMap(doc -> doc.getTags().stream()).toList());
    List<String> documentIds =
        new ArrayList<>(documentExports.stream().map(Document::getId).toList());

    if (isWithTeams) {
      // Add Teams
      attackChainFileExport.setTeams(attackChain.getTeams());
      objectMapper.addMixIn(Team.class, isWithPlayers ? Mixins.Team.class : Mixins.EmptyTeam.class);
      attackChainTags.addAll(
          attackChain.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
    }

    if (isWithPlayers) {
      // Add players
      List<User> players =
          attackChain.getTeams().stream()
              .flatMap(team -> team.getUsers().stream())
              .distinct()
              .toList();
      attackChainFileExport.setUsers(players);
      objectMapper.addMixIn(User.class, Mixins.User.class);
      attackChainTags.addAll(players.stream().flatMap(user -> user.getTags().stream()).toList());
      // organizations
      List<Organization> organizations =
          new ArrayList<>(
              players.stream().map(User::getOrganization).filter(Objects::nonNull).toList());
      organizations.addAll(
          attackChain.getTeams().stream()
              .map(Team::getOrganization)
              .filter(Objects::nonNull)
              .toList());
      attackChainFileExport.setOrganizations(organizations);
      objectMapper.addMixIn(Organization.class, Mixins.Organization.class);
      attackChainTags.addAll(
          organizations.stream().flatMap(org -> org.getTags().stream()).toList());
    } else {
      objectMapper.addMixIn(AttackChainRunFileExport.class, Mixins.AttackChainWithoutPlayers.class);
    }

    // Add AttackChainNodes
    objectMapper.addMixIn(AttackChainNode.class, Mixins.AttackChainNode.class);
    attackChainFileExport.setAttackChainNodes(attackChain.getAttackChainNodes());
    attackChain
        .getAttackChainNodes()
        .forEach(
            attackChainNode -> {
              attackChainTags.addAll(attackChainNode.getTags());
              attackChainNode
                  .getNodeContract()
                  .ifPresent(
                      nodeContract -> {
                        if (nodeContract.getPayload() != null) {
                          attackChainTags.addAll(nodeContract.getPayload().getTags());
                        }
                      });
            });

    // 二开移除 Channel/Article/Challenge — 不再写入 AttackChainFileExport。

    // Tags
    attackChainFileExport.setTags(attackChainTags.stream().distinct().toList());
    objectMapper.addMixIn(Tag.class, Mixins.Tag.class);

    // Add Attackpattern and kill chain phases
    objectMapper.addMixIn(KillChainPhase.class, Mixins.KillChainPhase.class);
    objectMapper.addMixIn(AttackPattern.class, Mixins.AttackPattern.class);
    objectMapper.addMixIn(NodeContract.class, Mixins.NodeContract.class);
    objectMapper.addMixIn(Payload.class, Mixins.Payload.class);

    // load the killchainphases
    attackChain.getAttackChainNodes().stream()
        .map(AttackChainNode::getPayload)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .flatMap(payload -> payload.getAttackPatterns().stream())
        .distinct()
        .toList()
        .stream()
        .forEach(attackPattern -> Hibernate.initialize(attackPattern.getKillChainPhases()));

    // Build the response
    String infos =
        "("
            + (isWithTeams ? "with_teams" : "no_teams")
            + " & "
            + (isWithPlayers ? "with_players" : "no_players")
            + " & "
            + (isWithVariableValues ? "with_variable_values" : "no_variable_values")
            + ")";

    String zipName = (attackChain.getName() + "_" + now().toString()) + "_" + infos + ".zip";
    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
    response.setStatus(HttpServletResponse.SC_OK);
    ZipOutputStream zipExport = new ZipOutputStream(response.getOutputStream());
    ZipEntry zipEntry = new ZipEntry(attackChain.getName() + ".json");
    zipEntry.setComment(EXPORT_ENTRY_SCENARIO);
    zipExport.putNextEntry(zipEntry);
    zipExport.write(
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(attackChainFileExport));
    zipExport.closeEntry();
    // Add the documents
    documentIds.stream()
        .distinct()
        .forEach(
            docId -> {
              Document doc = this.documentRepository.findById(docId).orElseThrow();
              Optional<InputStream> docStream = this.fileService.getFile(doc);
              if (docStream.isPresent()) {
                try {
                  ZipEntry zipDoc = new ZipEntry(doc.getTarget());
                  zipDoc.setComment(EXPORT_ENTRY_ATTACHMENT);
                  byte[] data = docStream.get().readAllBytes();
                  zipExport.putNextEntry(zipDoc);
                  zipExport.write(data);
                  zipExport.closeEntry();
                } catch (IOException e) {
                  log.error(e.getMessage(), e);
                }
              }
            });
    zipExport.finish();
    zipExport.close();
  }

  // -- TEAMS --

  @Transactional(rollbackFor = Exception.class)
  public Iterable<TeamOutput> removeTeams(
      @NotBlank final String attackChainId, @NotNull final List<String> teamIds) {
    // Remove teams from attackChain
    this.attackChainRepository.removeTeams(attackChainId, teamIds);
    // Remove only associations for this attackChain
    this.attackChainTeamUserRepository.deleteByAttackChainIdAndTeamIds(attackChainId, teamIds);
    // Remove all association between attackChainNodes and teams
    this.attackChainNodeRepository.removeTeamsForAttackChain(attackChainId, teamIds);
    // Remove all association between lessons learned and teams
    this.lessonsCategoryRepository.removeTeamsForAttackChain(attackChainId, teamIds);
    return teamService.find(fromIds(teamIds));
  }

  @Transactional(rollbackFor = Exception.class)
  public List<TeamOutput> replaceTeams(
      @NotBlank final String attackChainId, @NotNull final List<String> teamIds) {
    AttackChain attackChain = this.attackChain(attackChainId);
    Set<String> previousTeamIds =
        attackChain.getTeams().stream().map(Team::getId).collect(Collectors.toSet());
    Set<String> targetTeamIds = new LinkedHashSet<>(teamIds);

    Set<String> removedTeamIds = new HashSet<>(previousTeamIds);
    removedTeamIds.removeAll(targetTeamIds);
    if (!removedTeamIds.isEmpty()) {
      List<String> removedTeamIdsList = new ArrayList<>(removedTeamIds);
      this.attackChainTeamUserRepository.deleteByAttackChainIdAndTeamIds(
          attackChainId, removedTeamIdsList);
      this.attackChainNodeRepository.removeTeamsForAttackChain(attackChainId, removedTeamIdsList);
      this.lessonsCategoryRepository.removeTeamsForAttackChain(attackChainId, removedTeamIdsList);
    }

    // Replace teams from a attackChain
    List<Team> teams = fromIterable(this.teamRepository.findAllById(targetTeamIds));
    attackChain.setTeams(teams);
    this.attackChainRepository.save(attackChain);

    List<String> teamIdsAdded =
        targetTeamIds.stream().filter(id -> !previousTeamIds.contains(id)).toList();

    List<Team> teamsAdded = fromIterable(this.teamRepository.findAllById(teamIdsAdded));

    // Enable user
    teamsAdded.forEach(
        team -> {
          List<String> playerIds = team.getUsers().stream().map(User::getId).toList();
          this.enablePlayers(attackChainId, team, playerIds);
        });

    // You must return all the modified teams to ensure the frontend store updates correctly
    List<String> modifiedTeamIds =
        Stream.concat(previousTeamIds.stream(), teams.stream().map(Team::getId))
            .distinct()
            .toList();
    return teamService.find(fromIds(modifiedTeamIds));
  }

  public AttackChain addAttackChainPlayer(
      @NotBlank final String attackChainId,
      @NotBlank final String teamId,
      @NotNull final List<String> playerIds) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(playerIds);
    team.getUsers().addAll(fromIterable(teamUsers));
    Team savedTeam = teamRepository.save(team);
    return this.enablePlayers(attackChainId, savedTeam, playerIds);
  }

  public AttackChain enableAddAttackChainTeamPlayer(
      @NotBlank final String attackChainId,
      @NotBlank final String teamId,
      @NotNull final List<String> playerIds) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    return this.enablePlayers(attackChainId, team, playerIds);
  }

  public AttackChain enablePlayers(
      @NotBlank final String attackChainId,
      @NotBlank final Team team,
      @NotNull final List<String> playerIds) {
    AttackChain attackChain = this.attackChain(attackChainId);
    playerIds.forEach(
        playerId -> {
          boolean alreadyLinked =
              this.attackChainTeamUserRepository.existsByAttackChainIdAndTeamIdAndUserId(
                  attackChainId, team.getId(), playerId);
          if (alreadyLinked) {
            return;
          }
          AttackChainTeamUser attackChainTeamUser = new AttackChainTeamUser();
          attackChainTeamUser.setAttackChain(attackChain);
          attackChainTeamUser.setTeam(team);
          attackChainTeamUser.setUser(this.userRepository.findById(playerId).orElseThrow());
          this.attackChainTeamUserRepository.save(attackChainTeamUser);
        });
    return attackChain;
  }

  public AttackChain disablePlayers(
      @NotBlank final String attackChainId,
      @NotBlank final String teamId,
      @NotNull final List<String> playerIds) {
    playerIds.forEach(
        playerId -> {
          AttackChainTeamUserId attackChainTeamUserId = new AttackChainTeamUserId();
          attackChainTeamUserId.setAttackChainId(attackChainId);
          attackChainTeamUserId.setTeamId(teamId);
          attackChainTeamUserId.setUserId(playerId);
          this.attackChainTeamUserRepository.deleteById(attackChainTeamUserId);
        });
    return this.attackChain(attackChainId);
  }

  @Transactional
  public AttackChain getDuplicateAttackChain(@NotBlank String attackChainId) {
    if (StringUtils.isNotBlank(attackChainId)) {
      AttackChain attackChainOrigin = attackChainRepository.findById(attackChainId).orElseThrow();
      AttackChain attackChain = copyAttackChain(attackChainOrigin);
      AttackChain attackChainDuplicate = attackChainRepository.save(attackChain);
      getListOfDuplicatedAttackChainNodes(attackChainDuplicate, attackChainOrigin);
      getListOfAttackChainTeams(attackChainDuplicate, attackChainOrigin);
      getListOfVariables(attackChainDuplicate, attackChainOrigin);
      getObjectives(attackChainDuplicate, attackChainOrigin);
      getLessonsCategories(attackChainDuplicate, attackChainOrigin);
      return attackChainRepository.save(attackChain);
    }
    throw new ElementNotFoundException();
  }

  public boolean checkIfTagRulesApplies(
      @NotNull final AttackChain attackChain, @NotNull final List<String> newTags) {
    return tagRuleService.checkIfRulesApply(
        attackChain.getTags().stream().map(Tag::getId).toList(), newTags);
  }

  private void getListOfAttackChainTeams(
      @NotNull AttackChain attackChain, @NotNull AttackChain attackChainOrigin) {
    Map<String, Team> contextualTeams = new HashMap<>();
    List<Team> attackChainTeams = new ArrayList<>();
    attackChainOrigin
        .getTeams()
        .forEach(
            attackChainTeam -> {
              if (attackChainTeam.getContextual()) {
                Team team = teamService.copyContextualTeam(attackChainTeam);
                Team teamSaved = this.teamRepository.save(team);
                attackChainTeams.add(teamSaved);
                contextualTeams.put(attackChainTeam.getId(), teamSaved);
              } else {
                attackChainTeams.add(attackChainTeam);
              }
            });
    attackChain.setTeams(new ArrayList<>(attackChainTeams));

    List<AttackChainNode> attackChainAttackChainNodes = attackChain.getAttackChainNodes();
    attackChainAttackChainNodes.forEach(
        attackChainAttackChainNode -> {
          List<Team> teams = new ArrayList<>();
          attackChainAttackChainNode
              .getTeams()
              .forEach(
                  team -> {
                    if (team.getContextual()) {
                      teams.add(contextualTeams.get(team.getId()));
                    } else {
                      teams.add(team);
                    }
                  });
          attackChainAttackChainNode.setTeams(teams);
        });
  }

  private AttackChain copyAttackChain(AttackChain attackChain) {
    AttackChain attackChainDuplicate = new AttackChain();
    attackChainDuplicate.setName(duplicateString(attackChain.getName()));
    attackChainDuplicate.setCategory(attackChain.getCategory());
    attackChainDuplicate.setDescription(attackChain.getDescription());
    attackChainDuplicate.setSeverity(attackChain.getSeverity());
    attackChainDuplicate.setSubtitle(attackChain.getSubtitle());
    attackChainDuplicate.setHeader(attackChain.getHeader());
    attackChainDuplicate.setMainFocus(attackChain.getMainFocus());
    attackChainDuplicate.setFrom(attackChain.getFrom());
    attackChainDuplicate.setExternalUrl(attackChain.getExternalUrl());
    attackChainDuplicate.setTags(new HashSet<>(attackChain.getTags()));
    attackChainDuplicate.setAttackChainNodes(new HashSet<>(attackChain.getAttackChainNodes()));
    attackChainDuplicate.setExternalReference(attackChain.getExternalReference());
    attackChainDuplicate.setTeamUsers(new ArrayList<>(attackChain.getTeamUsers()));
    attackChainDuplicate.setReplyTos(new ArrayList<>(attackChain.getReplyTos()));
    attackChainDuplicate.setLessonsAnonymized(attackChain.isLessonsAnonymized());
    attackChainDuplicate.setDocuments(new ArrayList<>(attackChain.getDocuments()));
    attackChainDuplicate.setGrants(new ArrayList<>(attackChain.getGrants()));
    attackChainDuplicate.setDependencies(
        cleanAttackChainDependencies(attackChain.getDependencies()));
    return attackChainDuplicate;
  }

  private AttackChain.Dependency[] cleanAttackChainDependencies(
      AttackChain.Dependency[] dependencies) {
    if (dependencies == null) {
      return new AttackChain.Dependency[0];
    }

    return Arrays.stream(dependencies)
        .filter(dependency -> !AttackChain.Dependency.STARTERPACK.equals(dependency))
        .toArray(AttackChain.Dependency[]::new);
  }

  private void getListOfDuplicatedAttackChainNodes(
      @NotNull AttackChain attackChain, @NotNull AttackChain attackChainOrigin) {
    Set<AttackChainNode> attackChainNodeListForAttackChain =
        attackChainOrigin.getAttackChainNodes().stream()
            .map(
                attackChainNode ->
                    attackChainNodeDuplicateService.duplicateAttackChainNodeForAttackChain(
                        attackChain, attackChainNode))
            .collect(Collectors.toSet());
    attackChain.setAttackChainNodes(new HashSet<>(attackChainNodeListForAttackChain));
  }

  // 二开移除 Articles/Channels — 复制不再处理 article 字段。

  private void getListOfVariables(AttackChain attackChain, AttackChain attackChainOrigin) {
    List<Variable> variables = variableService.variablesFromAttackChain(attackChainOrigin.getId());
    List<Variable> variableList =
        variables.stream()
            .map(
                variable -> {
                  Variable variable1 = new Variable();
                  variable1.setKey(variable.getKey());
                  variable1.setDescription(variable.getDescription());
                  variable1.setValue(variable.getValue());
                  variable1.setType(variable.getType());
                  variable1.setAttackChain(attackChain);
                  return variable1;
                })
            .toList();
    variableService.createVariables(variableList);
  }

  private void getLessonsCategories(
      AttackChain duplicatedAttackChain, AttackChain originalAttackChain) {
    List<LessonsCategory> duplicatedCategories = new ArrayList<>();
    for (LessonsCategory originalCategory : originalAttackChain.getLessonsCategories()) {
      LessonsCategory duplicatedCategory = new LessonsCategory();
      duplicatedCategory.setName(originalCategory.getName());
      duplicatedCategory.setDescription(originalCategory.getDescription());
      duplicatedCategory.setOrder(originalCategory.getOrder());
      duplicatedCategory.setAttackChain(duplicatedAttackChain);
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
    duplicatedAttackChain.setLessonsCategories(duplicatedCategories);
  }

  private void getObjectives(AttackChain attackChain, AttackChain attackChainOrigin) {
    List<Objective> duplicatedObjectives = new ArrayList<>();
    for (Objective originalObjective : attackChainOrigin.getObjectives()) {
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
      duplicatedObjective.setAttackChain(attackChain);
      duplicatedObjectives.add(duplicatedObjective);
    }
    attackChain.setObjectives(duplicatedObjectives);
  }

  /**
   * Verify all healthcheck for a given attackChain id
   *
   * @param attackChainId to verify
   * @return founded healthcheck list
   */
  @Transactional(readOnly = true)
  public List<HealthCheck> runChecks(String attackChainId) {
    if (attackChainId == null) {
      return null;
    }

    List<HealthCheck> healthChecks = new ArrayList<>();

    AttackChain attackChain = this.attackChain(attackChainId);

    // get the healthcheck for each attackChainNodes, remove duplicate from attackChainNodes
    // HealthCheck results and
    // add them to the result
    List<HealthCheck> attackChainNodesHealthChecksNoDuplicate =
        attackChain.getAttackChainNodes().stream()
            .flatMap(attackChainNode -> attackChainNodeService.runChecks(attackChainNode).stream())
            .collect(
                Collectors.toMap(
                    hc -> hc.getType() + "_" + hc.getDetail(),
                    hc -> hc,
                    (a, b) ->
                        HealthCheck.Status.ERROR.equals(a.getStatus())
                            ? a
                            : HealthCheck.Status.ERROR.equals(b.getStatus()) ? b : a))
            .values()
            .stream()
            .toList();
    healthChecks.addAll(attackChainNodesHealthChecksNoDuplicate);

    healthChecks.addAll(healthCheckUtils.runMissingContentChecks(attackChain));
    healthChecks.addAll(healthCheckUtils.runTeamsChecks(attackChain));

    return healthChecks;
  }
}
