package io.veriguard.rest.lessons;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static java.time.Instant.now;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.database.specification.LessonsAnswerSpecification;
import io.veriguard.database.specification.LessonsCategorySpecification;
import io.veriguard.database.specification.LessonsQuestionSpecification;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.lessons.form.*;
import io.veriguard.service.MailingService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AttackChainRunLessonsApi extends RestBehavior {

  public static final String EXERCISE_URL = "/api/attack_chain_runs/";

  private final AttackChainRunRepository attackChainRunRepository;
  private final TeamRepository teamRepository;
  private final LessonsTemplateRepository lessonsTemplateRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final LessonsAnswerRepository lessonsAnswerRepository;
  private final UserRepository userRepository;
  private final MailingService mailingService;

  @GetMapping(EXERCISE_URL + "{exerciseId}/lessons_categories")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<LessonsCategory> attackChainRunLessonsCategories(@PathVariable String attackChainRunId) {
    return lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId));
  }

  @PostMapping(EXERCISE_URL + "{exerciseId}/lessons_apply_template/{lessonsTemplateId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<LessonsCategory> applyAttackChainRunLessonsTemplate(
      @PathVariable String attackChainRunId, @PathVariable String lessonsTemplateId) {
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow(ElementNotFoundException::new);
    LessonsTemplate lessonsTemplate =
        lessonsTemplateRepository
            .findById(lessonsTemplateId)
            .orElseThrow(ElementNotFoundException::new);
    List<LessonsTemplateCategory> lessonsTemplateCategories =
        lessonsTemplate.getCategories().stream().toList();
    for (LessonsTemplateCategory lessonsTemplateCategory : lessonsTemplateCategories) {
      LessonsCategory lessonsCategory = new LessonsCategory();
      lessonsCategory.setAttackChainRun(attackChainRun);
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
    return lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId));
  }

  @PostMapping(EXERCISE_URL + "{exerciseId}/lessons_categories")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory createAttackChainRunLessonsCategory(
      @PathVariable String attackChainRunId, @Valid @RequestBody LessonsCategoryCreateInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow(ElementNotFoundException::new);
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setUpdateAttributes(input);
    lessonsCategory.setAttackChainRun(attackChainRun);
    return lessonsCategoryRepository.save(lessonsCategory);
  }

  @PostMapping(EXERCISE_URL + "{exerciseId}/lessons_answers_reset")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<LessonsCategory> resetAttackChainRunLessonsAnswers(@PathVariable String attackChainRunId) {
    List<LessonsAnswer> lessonsAnswers =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
            .stream()
            .flatMap(
                lessonsCategory ->
                    lessonsQuestionRepository
                        .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                        .stream()
                        .flatMap(
                            lessonsQuestion ->
                                lessonsAnswerRepository
                                    .findAll(
                                        LessonsAnswerSpecification.fromQuestion(
                                            lessonsQuestion.getId()))
                                    .stream()))
            .toList();
    lessonsAnswerRepository.deleteAll(lessonsAnswers);
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
        .stream()
        .toList();
  }

  @PostMapping(EXERCISE_URL + "{exerciseId}/lessons_empty")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<LessonsCategory> emptyAttackChainRunLessons(@PathVariable String attackChainRunId) {
    List<LessonsCategory> lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
            .stream()
            .toList();
    lessonsCategoryRepository.deleteAll(lessonsCategories);
    lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
            .stream()
            .toList();
    return lessonsCategories;
  }

  @PutMapping(EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory updateAttackChainRunLessonsCategory(
      @PathVariable String attackChainRunId,
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

  @DeleteMapping(EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public void deleteAttackChainRunLessonsCategory(
      @PathVariable String attackChainRunId, @PathVariable String lessonsCategoryId) {
    lessonsCategoryRepository.deleteById(lessonsCategoryId);
  }

  @PutMapping(EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/teams")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory updateAttackChainRunLessonsCategoryTeams(
      @PathVariable String attackChainRunId,
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

  @GetMapping(EXERCISE_URL + "{exerciseId}/lessons_questions")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<LessonsQuestion> attackChainRunLessonsQuestions(@PathVariable String attackChainRunId) {
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream())
        .toList();
  }

  @GetMapping(
      EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<LessonsQuestion> attackChainRunLessonsCategoryQuestions(
      @PathVariable String attackChainRunId, @PathVariable String lessonsCategoryId) {
    return lessonsQuestionRepository.findAll(
        LessonsQuestionSpecification.fromCategory(lessonsCategoryId));
  }

  @PostMapping(
      EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public LessonsQuestion createAttackChainRunLessonsQuestion(
      @PathVariable String attackChainRunId,
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
      EXERCISE_URL
          + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public LessonsQuestion updateAttackChainRunLessonsQuestion(
      @PathVariable String attackChainRunId,
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
      EXERCISE_URL
          + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public void deleteAttackChainRunLessonsQuestion(
      @PathVariable String attackChainRunId, @PathVariable String lessonsQuestionId) {
    lessonsQuestionRepository.deleteById(lessonsQuestionId);
  }

  @PostMapping(EXERCISE_URL + "{exerciseId}/lessons_send")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public void sendAttackChainRunLessons(
      @PathVariable String attackChainRunId, @Valid @RequestBody LessonsSendInput input) {
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow(ElementNotFoundException::new);
    List<LessonsCategory> lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
            .stream()
            .toList();
    List<User> users =
        lessonsCategories.stream()
            .flatMap(
                lessonsCategory ->
                    lessonsCategory.getTeams().stream().flatMap(team -> team.getUsers().stream()))
            .distinct()
            .toList();
    mailingService.sendEmail(input.getSubject(), input.getBody(), users);
  }

  @GetMapping(EXERCISE_URL + "{exerciseId}/lessons_answers")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<LessonsAnswer> attackChainRunLessonsAnswers(
      @PathVariable String attackChainRunId, @RequestParam Optional<String> userId) {
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream()
                    .flatMap(
                        lessonsQuestion ->
                            lessonsAnswerRepository
                                .findAll(
                                    LessonsAnswerSpecification.fromQuestion(
                                        lessonsQuestion.getId()))
                                .stream()))
        .toList();
  }

  @GetMapping("/api/player/lessons/exercise/{exerciseId}/lessons_categories")
  @RBAC(skipRBAC = true)
  public List<LessonsCategory> playerLessonsCategories(
      @PathVariable String attackChainRunId, @RequestParam Optional<String> userId) {
    impersonateUser(userRepository, userId); // Protection for ?
    return lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId));
  }

  @GetMapping("/api/player/lessons/exercise/{exerciseId}/lessons_questions")
  @RBAC(skipRBAC = true)
  public List<LessonsQuestion> playerLessonsQuestions(
      @PathVariable String attackChainRunId, @RequestParam Optional<String> userId) {
    impersonateUser(userRepository, userId); // Protection for ?
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream())
        .toList();
  }

  @GetMapping("/api/player/lessons/exercise/{exerciseId}/lessons_answers")
  @RBAC(skipRBAC = true)
  public List<LessonsAnswer> playerLessonsAnswers(
      @PathVariable String attackChainRunId, @RequestParam Optional<String> userId) {
    impersonateUser(userRepository, userId); // Protection for ?
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromAttackChainRun(attackChainRunId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream()
                    .flatMap(
                        lessonsQuestion ->
                            lessonsAnswerRepository
                                .findAll(
                                    LessonsAnswerSpecification.fromQuestion(
                                        lessonsQuestion.getId()))
                                .stream()))
        .toList();
  }

  @PostMapping(
      "/api/player/lessons/exercise/{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}/lessons_answers")
  @RBAC(skipRBAC = true)
  public LessonsAnswer createAttackChainRunLessonsQuestion(
      @PathVariable String attackChainRunId,
      @PathVariable String lessonsQuestionId,
      @Valid @RequestBody LessonsAnswerCreateInput input,
      @RequestParam Optional<String> userId) {
    User user = impersonateUser(userRepository, userId);
    LessonsQuestion lessonsQuestion =
        lessonsQuestionRepository
            .findById(lessonsQuestionId)
            .orElseThrow(ElementNotFoundException::new);

    Optional<LessonsAnswer> optionalAnswer =
        lessonsAnswerRepository.findByUserIdAndQuestionId(user.getId(), lessonsQuestionId);
    LessonsAnswer lessonsAnswer =
        optionalAnswer.orElseGet(
            () -> {
              LessonsAnswer newAnswer = new LessonsAnswer();
              newAnswer.setQuestion(lessonsQuestion);
              newAnswer.setUser(user);
              return newAnswer;
            });
    lessonsAnswer.setScore(input.getScore());
    lessonsAnswer.setPositive(input.getPositive());
    lessonsAnswer.setNegative(input.getNegative());

    return lessonsAnswerRepository.save(lessonsAnswer);
  }
}
