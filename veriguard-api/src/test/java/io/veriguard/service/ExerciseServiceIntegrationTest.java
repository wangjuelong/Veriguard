package io.veriguard.service;

import static io.veriguard.database.specification.TeamSpecification.fromExercise;
import static io.veriguard.utils.fixtures.ExerciseFixture.getExercise;
import static io.veriguard.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static io.veriguard.utils.fixtures.TeamFixture.getTeam;
import static io.veriguard.utils.fixtures.UserFixture.getUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.veriguard.IntegrationTest;
import io.veriguard.config.cache.LicenseCacheManager;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.ee.Ee;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.rest.exercise.service.ExerciseService;
import io.veriguard.rest.exercise.service.PauseExerciseService;
import io.veriguard.rest.inject.service.InjectDuplicateService;
import io.veriguard.rest.inject.service.InjectService;
import io.veriguard.service.scenario.ScenarioRecurrenceService;
import io.veriguard.telemetry.metric_collectors.ActionMetricCollector;
import io.veriguard.utils.ResultUtils;
import io.veriguard.utils.fixtures.ExerciseFixture;
import io.veriguard.utils.fixtures.InjectorContractFixture;
import io.veriguard.utils.mapper.ExerciseMapper;
import io.veriguard.utils.mapper.InjectExpectationMapper;
import io.veriguard.utils.mapper.InjectMapper;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExerciseServiceIntegrationTest extends IntegrationTest {

  @Mock Ee eeService;
  @Mock InjectDuplicateService injectDuplicateService;
  @Mock VariableService variableService;

  @Autowired private TeamService teamService;
  @Autowired private TagRuleService tagRuleService;
  @Autowired private DocumentService documentService;
  @Autowired private InjectService injectService;
  @Autowired private UserService userService;
  @Autowired private GrantService grantService;
  @Autowired private ExerciseTeamUserService exerciseTeamUserService;

  @Autowired private ExerciseMapper exerciseMapper;
  @Autowired private InjectMapper injectMapper;
  @Autowired private ResultUtils resultUtils;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Autowired private ArticleRepository articleRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TeamRepository teamRepository;

  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @Autowired private LicenseCacheManager licenseCacheManager;
  @Autowired private InjectExpectationMapper injectExpectationMapper;
  @Autowired private ScenarioRecurrenceService scenarioRecurrenceService;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private LessonsService lessonsService;
  @Autowired private FileService fileService;
  @Autowired private PauseExerciseService pauseExerciseService;

  private static String USER_ID;
  private static String TEAM_ID;
  private static String INJECT_ID;

  @InjectMocks private ExerciseService exerciseService;

  @BeforeEach
  void setUp() {
    exerciseService =
        new ExerciseService(
            eeService,
            injectDuplicateService,
            teamService,
            variableService,
            tagRuleService,
            documentService,
            injectService,
            userService,
            grantService,
            exerciseTeamUserService,
            exerciseMapper,
            injectMapper,
            resultUtils,
            actionMetricCollector,
            licenseCacheManager,
            assetRepository,
            assetGroupRepository,
            injectExpectationRepository,
            articleRepository,
            exerciseRepository,
            teamRepository,
            userRepository,
            exerciseTeamUserRepository,
            injectRepository,
            lessonsCategoryRepository,
            injectExpectationMapper,
            scenarioRecurrenceService,
            pauseExerciseService,
            fileService,
            lessonsService);
  }

  @AfterAll
  public void teardown() {
    this.userRepository.deleteById(USER_ID);
    this.teamRepository.deleteById(TEAM_ID);
    this.injectRepository.deleteById(INJECT_ID);
  }

  @DisplayName("Should create new contextual teams while exercise duplication")
  @Test
  @Transactional(rollbackFor = Exception.class)
  void createNewContextualTeamsWhileExerciseDuplication() {
    // -- PREPARE --
    List<Team> exerciseTeams = new ArrayList<>();
    Team contextualTeam = this.teamRepository.save(getTeam(null, "fakeTeamName1", true));
    exerciseTeams.add(contextualTeam);
    Team noContextualTeam = this.teamRepository.save(getTeam(null, "fakeTeamName2", false));
    exerciseTeams.add(noContextualTeam);
    Exercise exercise = this.exerciseRepository.save(getExercise(exerciseTeams));

    // -- EXECUTE --
    Exercise exerciseDuplicated = exerciseService.getDuplicateExercise(exercise.getId());

    // -- ASSERT --
    assertNotEquals(exercise.getId(), exerciseDuplicated.getId());
    assertEquals(2, exerciseDuplicated.getTeams().size());
    exerciseDuplicated
        .getTeams()
        .forEach(
            team -> {
              if (team.getContextual()) {
                assertNotEquals(contextualTeam.getId(), team.getId());
                assertEquals(contextualTeam.getName(), team.getName());
              } else {
                assertEquals(noContextualTeam.getId(), team.getId());
              }
            });
  }

  @DisplayName("Should remove team from exercise")
  @Test
  void testRemoveTeams() {
    // -- PREPARE --
    User user = getUser();
    User userSaved = this.userRepository.saveAndFlush(user);
    USER_ID = userSaved.getId();
    Team team = getTeam(userSaved);
    Team teamSaved = this.teamRepository.saveAndFlush(team);
    TEAM_ID = teamSaved.getId();
    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setTeams(List.of(teamSaved));
    exercise.setFrom(user.getEmail());
    Exercise exerciseSaved = this.exerciseRepository.saveAndFlush(exercise);
    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();
    Inject injectDefaultEmail = getInjectForEmailContract(injectorContract);
    injectDefaultEmail.setExercise(exerciseSaved);
    injectDefaultEmail.setTeams(List.of(teamSaved));
    Inject injectDefaultEmailSaved = this.injectRepository.saveAndFlush(injectDefaultEmail);
    INJECT_ID = injectDefaultEmailSaved.getId();

    // -- EXECUTE --
    this.exerciseService.removeTeams(exerciseSaved.getId(), List.of(teamSaved.getId()));

    // -- ASSERT --
    List<Team> teams = this.teamRepository.findAll(fromExercise(exerciseSaved.getId()));
    assertEquals(0, teams.size());
    Inject injectAssert = this.injectRepository.findById(INJECT_ID).orElseThrow();
    assertEquals(0, injectAssert.getTeams().size());
  }
}
