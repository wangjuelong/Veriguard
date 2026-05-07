package io.veriguard.service;

import static io.veriguard.database.model.Grant.GRANT_RESOURCE_TYPE.SIMULATION;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.rest.custom_dashboard.CustomDashboardService;
import io.veriguard.utils.CopyObjectListUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttackChainRunFactory {

  private final AttackChainRunRepository attackChainRunRepository;
  private final TeamRepository teamRepository;
  private final AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;
  private final ObjectiveRepository objectiveRepository;
  private final DocumentRepository documentRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AttackChainNodeDocumentRepository attackChainNodeDocumentRepository;
  private final VariableService variableService;
  private final TeamService teamService;
  private final GrantService grantService;
  private final PlatformSettingsService platformSettingsService;
  private final CustomDashboardService customDashboardService;

  @Transactional(rollbackFor = Exception.class)
  public AttackChainRun toAttackChainRun(
      @NotBlank final AttackChain attackChain,
      @Nullable final Instant start,
      final boolean isRunning) {
    AttackChainRun attackChainRun = new AttackChainRun();
    attackChainRun.setAttackChain(attackChain);
    attackChainRun.setName(attackChain.getName());
    attackChainRun.setDescription(attackChain.getDescription());
    attackChainRun.setSubtitle(attackChain.getSubtitle());
    attackChainRun.setCategory(attackChain.getCategory());
    attackChainRun.setMainFocus(attackChain.getMainFocus());
    attackChainRun.setSeverity(attackChain.getSeverity());
    attackChainRun.setHeader(attackChain.getHeader());
    attackChainRun.setFooter(attackChain.getFooter());
    attackChainRun.setFrom(attackChain.getFrom());
    attackChainRun.addReplyTos(attackChain.getReplyTos());
    attackChainRun.setStart(start);
    attackChainRun.setSecurityCoverage(attackChain.getSecurityCoverage());
    if (isRunning) {
      attackChainRun.setStatus(AttackChainRunStatus.RUNNING);
    }

    // Tags
    attackChainRun.setTags(CopyObjectListUtils.copy(attackChain.getTags(), Tag.class));

    // Custom Dashboard
    attackChainRun.setCustomDashboard(
        this.platformSettingsService
            .setting(SettingKeys.DEFAULT_SIMULATION_DASHBOARD.key())
            .map(Setting::getValue)
            .filter(v -> !v.isEmpty())
            .map(this.customDashboardService::customDashboard)
            .orElse(null));

    AttackChainRun attackChainRunSaved = this.attackChainRunRepository.save(attackChainRun);

    // Grants
    List<Grant> attackChainRunGrants =
        grantService.duplicateGrants(
            attackChain.getGrants(), attackChainRunSaved.getId(), SIMULATION);
    attackChainRunSaved.setGrants(attackChainRunGrants);

    // Teams
    Map<String, Team> contextualTeams = new HashMap<>();
    attackChain
        .getTeams()
        .forEach(
            attackChainTeam -> {
              if (attackChainTeam.getContextual()) {
                Team team = teamService.copyContextualTeam(attackChainTeam);
                team.setAttackChainRuns(
                    new ArrayList<>() {
                      {
                        add(attackChainRunSaved);
                      }
                    });
                Team teamSaved = this.teamRepository.save(team);
                contextualTeams.put(attackChainTeam.getId(), teamSaved);
              } else {
                List<AttackChainRun> attackChainRuns = attackChainTeam.getAttackChainRuns();
                attackChainRuns.add(attackChainRun);
                attackChainTeam.setAttackChainRuns(attackChainRuns);
                this.teamRepository.save(attackChainTeam);
              }
            });

    // TeamUsers
    List<AttackChainRunTeamUser> attackChainRunTeamUsers =
        attackChain.getTeamUsers().stream()
            .map(
                attackChainTeamUser -> {
                  AttackChainRunTeamUser attackChainRunTeamUser = new AttackChainRunTeamUser();
                  attackChainRunTeamUser.setAttackChainRun(attackChainRunSaved);
                  attackChainRunTeamUser.setUser(attackChainTeamUser.getUser());
                  attackChainRunTeamUser.setTeam(
                      computeTeam(attackChainTeamUser.getTeam(), contextualTeams));
                  return attackChainRunTeamUser;
                })
            .toList();
    this.attackChainRunTeamUserRepository.saveAll(attackChainRunTeamUsers);

    // Objectives
    List<Objective> attackChainObjectives = attackChain.getObjectives();
    List<Objective> attackChainRunObjectives =
        attackChainObjectives.stream()
            .map(
                attackChainObjective -> {
                  Objective attackChainRunObjective = new Objective();
                  attackChainRunObjective.setAttackChainRun(attackChainRunSaved);
                  attackChainRunObjective.setTitle(attackChainObjective.getTitle());
                  attackChainRunObjective.setDescription(attackChainObjective.getDescription());
                  attackChainRunObjective.setPriority(attackChainObjective.getPriority());
                  return attackChainRunObjective;
                })
            .toList();
    this.objectiveRepository.saveAll(attackChainRunObjectives);

    // Documents
    List<Document> attackChainDocuments =
        addAttackChainRunToDocuments(attackChain.getDocuments(), attackChainRunSaved);
    this.documentRepository.saveAll(attackChainDocuments);

    // 二开移除 Articles / Channels — no attackChainRun-level article copy.

    // Lessons
    attackChainRun.setLessonsAnonymized(attackChain.isLessonsAnonymized());

    // Lessons categories
    List<LessonsCategory> attackChainLessonCategories = attackChain.getLessonsCategories();
    attackChainLessonCategories.forEach(
        attackChainLessonCategory -> {
          LessonsCategory attackChainRunLessonCategory = new LessonsCategory();
          attackChainRunLessonCategory.setAttackChainRun(attackChainRunSaved);
          attackChainRunLessonCategory.setName(attackChainLessonCategory.getName());
          attackChainRunLessonCategory.setDescription(attackChainLessonCategory.getDescription());
          attackChainRunLessonCategory.setOrder(attackChainLessonCategory.getOrder());

          // Teams
          List<Team> teams = new ArrayList<>();
          attackChainLessonCategory
              .getTeams()
              .forEach(team -> teams.add(computeTeam(team, contextualTeams)));
          attackChainRunLessonCategory.setTeams(teams);

          LessonsCategory attackChainRunLessonCategorySaved =
              this.lessonsCategoryRepository.save(attackChainRunLessonCategory);

          // Lessons questions
          List<LessonsQuestion> attackChainRunLessonsQuestions =
              attackChainLessonCategory.getQuestions().stream()
                  .map(
                      attackChainLessonsQuestion -> {
                        LessonsQuestion attackChainRunLessonsQuestion = new LessonsQuestion();
                        attackChainRunLessonsQuestion.setContent(
                            attackChainLessonsQuestion.getContent());
                        attackChainRunLessonsQuestion.setExplanation(
                            attackChainLessonsQuestion.getExplanation());
                        attackChainRunLessonsQuestion.setOrder(
                            attackChainLessonsQuestion.getOrder());
                        attackChainRunLessonsQuestion.setCategory(
                            attackChainRunLessonCategorySaved);
                        return attackChainRunLessonsQuestion;
                      })
                  .toList();
          this.lessonsQuestionRepository.saveAll(attackChainRunLessonsQuestions);
        });

    // AttackChainNodes
    List<AttackChainNode> attackChainAttackChainNodes = attackChain.getAttackChainNodes();
    Map<String, AttackChainNode> mapAttackChainRunAttackChainNodesByAttackChainAttackChainNode =
        new HashMap<>();
    attackChainAttackChainNodes.forEach(
        attackChainAttackChainNode -> {
          AttackChainNode attackChainRunAttackChainNode = new AttackChainNode();
          attackChainRunAttackChainNode.setTitle(attackChainAttackChainNode.getTitle());
          attackChainRunAttackChainNode.setDescription(attackChainAttackChainNode.getDescription());
          attackChainRunAttackChainNode.setNodeContract(
              attackChainAttackChainNode.getNodeContract().orElse(null));
          attackChainRunAttackChainNode.setCountry(attackChainAttackChainNode.getCountry());
          attackChainRunAttackChainNode.setCity(attackChainAttackChainNode.getCity());
          attackChainRunAttackChainNode.setEnabled(attackChainAttackChainNode.isEnabled());
          attackChainRunAttackChainNode.setAllTeams(attackChainAttackChainNode.isAllTeams());
          attackChainRunAttackChainNode.setAttackChainRun(attackChainRunSaved);
          attackChainRunAttackChainNode.setDependsDuration(
              attackChainAttackChainNode.getDependsDuration());
          attackChainRunAttackChainNode.setUser(attackChainAttackChainNode.getUser());
          attackChainRunAttackChainNode.setStatus(
              attackChainAttackChainNode.getStatus().orElse(null));
          attackChainRunAttackChainNode.setTags(
              CopyObjectListUtils.copy(attackChainAttackChainNode.getTags(), Tag.class));
          attackChainRunAttackChainNode.setContent(attackChainAttackChainNode.getContent());

          // 二开移除 Channel nodeExecutor — no contract-specific content remapping.

          // Teams
          List<Team> teams = new ArrayList<>();
          attackChainAttackChainNode
              .getTeams()
              .forEach(team -> teams.add(computeTeam(team, contextualTeams)));
          attackChainRunAttackChainNode.setTeams(teams);

          // Assets & Asset Groups
          attackChainRunAttackChainNode.setAssets(
              CopyObjectListUtils.copy(attackChainAttackChainNode.getAssets(), Asset.class));
          attackChainRunAttackChainNode.setAssetGroups(
              CopyObjectListUtils.copy(
                  attackChainAttackChainNode.getAssetGroups(), AssetGroup.class));
          AttackChainNode attackChainNodeSaved =
              this.attackChainNodeRepository.save(attackChainRunAttackChainNode);

          mapAttackChainRunAttackChainNodesByAttackChainAttackChainNode.put(
              attackChainAttackChainNode.getId(), attackChainNodeSaved);

          // Documents
          List<AttackChainNodeDocument> attackChainRunAttackChainNodeDocuments = new ArrayList<>();
          attackChainAttackChainNode
              .getDocuments()
              .forEach(
                  attackChainNodeDocument -> {
                    AttackChainNodeDocument attackChainRunAttackChainNodeDocument =
                        new AttackChainNodeDocument();
                    attackChainRunAttackChainNodeDocument.setAttackChainNode(attackChainNodeSaved);
                    attackChainRunAttackChainNodeDocument.setDocument(
                        attackChainNodeDocument.getDocument());
                    attackChainRunAttackChainNodeDocument.setAttached(
                        attackChainNodeDocument.isAttached());
                    attackChainRunAttackChainNodeDocuments.add(
                        attackChainRunAttackChainNodeDocument);
                  });
          this.attackChainNodeDocumentRepository.saveAll(attackChainRunAttackChainNodeDocuments);
        });

    // Second pass to add the correct links
    attackChainAttackChainNodes.forEach(
        attackChainAttackChainNode -> {
          if (attackChainAttackChainNode.getDependsOn() != null) {
            AttackChainNode attackChainNodeToUpdate =
                mapAttackChainRunAttackChainNodesByAttackChainAttackChainNode.get(
                    attackChainAttackChainNode.getId());
            attackChainNodeToUpdate.getDependsOn().clear();
            attackChainNodeToUpdate
                .getDependsOn()
                .addAll(
                    attackChainAttackChainNode.getDependsOn().stream()
                        .map(
                            (attackChainEdge -> {
                              AttackChainEdge dep = new AttackChainEdge();
                              dep.setCompositeId(attackChainEdge.getCompositeId());
                              dep.setAttackChainEdgeCondition(
                                  attackChainEdge.getAttackChainEdgeCondition());
                              dep.getCompositeId()
                                  .setAttackChainNodeParent(
                                      mapAttackChainRunAttackChainNodesByAttackChainAttackChainNode
                                          .get(
                                              dep.getCompositeId()
                                                  .getAttackChainNodeParent()
                                                  .getId()));
                              dep.getCompositeId()
                                  .setAttackChainNodeChildren(attackChainNodeToUpdate);
                              return dep;
                            }))
                        .toList());
            this.attackChainNodeRepository.save(attackChainNodeToUpdate);
          }
        });

    // Variables
    this.variableService.copyVariableFromAttackChainForSimulation(
        attackChain.getId(), attackChainRunSaved.getId());

    return attackChainRunSaved;
  }

  private List<Document> addAttackChainRunToDocuments(
      @NotNull final List<Document> origDocuments, @NotNull final AttackChainRun attackChainRun) {
    List<Document> destDocuments = new ArrayList<>();
    origDocuments.forEach(
        origDocument -> {
          try {
            Document destDocument = new Document();
            BeanUtils.copyProperties(destDocument, origDocument);
            Set<AttackChainRun> attackChainRuns = destDocument.getAttackChainRuns();
            attackChainRuns.add(attackChainRun);
            destDocument.setAttackChainRuns(attackChainRuns);
            destDocuments.add(destDocument);
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        });
    return destDocuments;
  }

  private Team computeTeam(
      @NotNull final Team origTeam, @NotNull final Map<String, Team> contextualTeams) {
    if (origTeam.getContextual()) {
      return contextualTeams.get(origTeam.getId());
    } else {
      return origTeam;
    }
  }
}
