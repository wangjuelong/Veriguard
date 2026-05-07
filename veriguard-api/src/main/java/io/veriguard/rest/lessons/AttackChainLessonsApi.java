package io.veriguard.rest.lessons;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static java.time.Instant.now;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.database.specification.LessonsCategorySpecification;
import io.veriguard.database.specification.LessonsQuestionSpecification;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.lessons.form.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AttackChainLessonsApi extends RestBehavior {

  public static final String SCENARIO_URI = "/api/attack_chains/";

  private final AttackChainRepository attackChainRepository;
  private final TeamRepository teamRepository;
  private final LessonsTemplateRepository lessonsTemplateRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final LessonsAnswerRepository lessonsAnswerRepository;
  private final UserRepository userRepository;

  @GetMapping(SCENARIO_URI + "{scenarioId}/lessons_categories")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<LessonsCategory> attackChainLessonsCategories(@PathVariable String attackChainId) {
    return lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromAttackChain(attackChainId));
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/lessons_apply_template/{lessonsTemplateId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<LessonsCategory> applyAttackChainLessonsTemplate(
      @PathVariable String attackChainId, @PathVariable String lessonsTemplateId) {
    AttackChain attackChain =
        attackChainRepository.findById(attackChainId).orElseThrow(ElementNotFoundException::new);
    LessonsTemplate lessonsTemplate =
        lessonsTemplateRepository
            .findById(lessonsTemplateId)
            .orElseThrow(ElementNotFoundException::new);
    List<LessonsTemplateCategory> lessonsTemplateCategories =
        lessonsTemplate.getCategories().stream().toList();
    for (LessonsTemplateCategory lessonsTemplateCategory : lessonsTemplateCategories) {
      LessonsCategory lessonsCategory = new LessonsCategory();
      lessonsCategory.setAttackChain(attackChain);
      lessonsCategory.setName(lessonsTemplateCategory.getName());
      lessonsCategory.setDescription(lessonsTemplateCategory.getDescription());
      lessonsCategory.setOrder(lessonsTemplateCategory.getOrder());
      lessonsCategoryRepository.save(lessonsCategory);
      List<LessonsQuestion> lessonsQuestions =
          lessonsTemplateCategory.getQuestions().stream()
              .map(
                  lessonsTemplateQuestion -> {
                    LessonsQuestion lessonsQuestion = new LessonsQuestion();
                    lessonsQuestion.setCategory(lessonsCategory);
                    lessonsQuestion.setContent(lessonsTemplateQuestion.getContent());
                    lessonsQuestion.setExplanation(lessonsTemplateQuestion.getExplanation());
                    lessonsQuestion.setOrder(lessonsTemplateQuestion.getOrder());
                    return lessonsQuestion;
                  })
              .toList();
      lessonsQuestionRepository.saveAll(lessonsQuestions);
    }
    return lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromAttackChain(attackChainId));
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/lessons_categories")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory createAttackChainLessonsCategory(
      @PathVariable String attackChainId, @Valid @RequestBody LessonsCategoryCreateInput input) {
    AttackChain attackChain =
        attackChainRepository.findById(attackChainId).orElseThrow(ElementNotFoundException::new);
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setUpdateAttributes(input);
    lessonsCategory.setAttackChain(attackChain);
    return lessonsCategoryRepository.save(lessonsCategory);
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/lessons_empty")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<LessonsCategory> emptyAttackChainLessons(@PathVariable String attackChainId) {
    List<LessonsCategory> lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromAttackChain(attackChainId))
            .stream()
            .toList();
    lessonsCategoryRepository.deleteAll(lessonsCategories);
    lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromAttackChain(attackChainId))
            .stream()
            .toList();
    return lessonsCategories;
  }

  @PutMapping(SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory updateAttackChainLessonsCategory(
      @PathVariable String attackChainId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsCategoryUpdateInput input) {
    LessonsCategory lessonsTemplateCategory =
        lessonsCategoryRepository
            .findById(lessonsCategoryId)
            .orElseThrow(ElementNotFoundException::new);
    lessonsTemplateCategory.setUpdateAttributes(input);
    lessonsTemplateCategory.setUpdated(now());
    return lessonsCategoryRepository.save(lessonsTemplateCategory);
  }

  @DeleteMapping(SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public void deleteAttackChainLessonsCategory(
      @PathVariable String attackChainId, @PathVariable String lessonsCategoryId) {
    lessonsCategoryRepository.deleteById(lessonsCategoryId);
  }

  @PutMapping(SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}/teams")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory updateAttackChainLessonsCategoryTeams(
      @PathVariable String attackChainId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsCategoryTeamsInput input) {
    LessonsCategory lessonsCategory =
        lessonsCategoryRepository
            .findById(lessonsCategoryId)
            .orElseThrow(ElementNotFoundException::new);
    Iterable<Team> lessonsCategoryTeams = teamRepository.findAllById(input.getTeamIds());
    lessonsCategory.setTeams(fromIterable(lessonsCategoryTeams));
    return lessonsCategoryRepository.save(lessonsCategory);
  }

  @GetMapping(SCENARIO_URI + "{scenarioId}/lessons_questions")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<LessonsQuestion> attackChainLessonsQuestions(@PathVariable String attackChainId) {
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromAttackChain(attackChainId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream())
        .toList();
  }

  @GetMapping(
      SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}/lessons_questions")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<LessonsQuestion> attackChainLessonsCategoryQuestions(
      @PathVariable String attackChainId, @PathVariable String lessonsCategoryId) {
    return lessonsQuestionRepository.findAll(
        LessonsQuestionSpecification.fromCategory(lessonsCategoryId));
  }

  @PostMapping(
      SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}/lessons_questions")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public LessonsQuestion createAttackChainLessonsQuestion(
      @PathVariable String attackChainId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsQuestionCreateInput input) {
    LessonsCategory lessonsCategory =
        lessonsCategoryRepository
            .findById(lessonsCategoryId)
            .orElseThrow(ElementNotFoundException::new);
    LessonsQuestion lessonsQuestion = new LessonsQuestion();
    lessonsQuestion.setUpdateAttributes(input);
    lessonsQuestion.setCategory(lessonsCategory);
    return lessonsQuestionRepository.save(lessonsQuestion);
  }

  @PutMapping(
      SCENARIO_URI
          + "{scenarioId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public LessonsQuestion updateAttackChainLessonsQuestion(
      @PathVariable String attackChainId,
      @PathVariable String lessonsQuestionId,
      @Valid @RequestBody LessonsQuestionUpdateInput input) {
    LessonsQuestion lessonsQuestion =
        lessonsQuestionRepository
            .findById(lessonsQuestionId)
            .orElseThrow(ElementNotFoundException::new);
    lessonsQuestion.setUpdateAttributes(input);
    lessonsQuestion.setUpdated(now());
    return lessonsQuestionRepository.save(lessonsQuestion);
  }

  @DeleteMapping(
      SCENARIO_URI
          + "{scenarioId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public void deleteAttackChainLessonsQuestion(
      @PathVariable String attackChainId, @PathVariable String lessonsQuestionId) {
    lessonsQuestionRepository.deleteById(lessonsQuestionId);
  }
}
