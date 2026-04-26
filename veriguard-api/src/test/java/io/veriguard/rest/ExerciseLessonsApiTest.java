package io.veriguard.rest;

import static io.veriguard.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static io.veriguard.utils.fixtures.ExerciseFixture.getExercise;
import static io.veriguard.utils.fixtures.ExerciseLessonsCategoryFixture.getLessonsCategory;
import static io.veriguard.utils.fixtures.TeamFixture.getTeam;
import static io.veriguard.utils.fixtures.UserFixture.getUser;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.Exercise;
import io.veriguard.database.model.LessonsCategory;
import io.veriguard.database.model.Team;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.ExerciseRepository;
import io.veriguard.database.repository.LessonsCategoryRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.integration.Manager;
import io.veriguard.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.veriguard.rest.exercise.service.ExerciseService;
import io.veriguard.rest.lessons.form.LessonsSendInput;
import io.veriguard.service.MailingService;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExerciseLessonsApiTest extends IntegrationTest {

  static Exercise EXERCISE;
  static LessonsCategory LESSONCATEGORY;
  static Team TEAM;
  static User USER;

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseService exerciseService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @SpyBean private MailingService mailingService;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;

  @BeforeAll
  void beforeAll() throws Exception {
    new Manager(List.of(emailInjectorIntegrationFactory)).monitorIntegrations();
    LESSONCATEGORY = getLessonCategory();
  }

  @AfterAll
  void afterAll() {
    this.exerciseRepository.delete(EXERCISE);
    this.lessonsCategoryRepository.delete(LESSONCATEGORY);
    this.teamRepository.delete(TEAM);
    this.userRepository.delete(USER);
  }

  private LessonsCategory getLessonCategory() {
    USER = this.userRepository.save(getUser());
    TEAM = teamRepository.save(getTeam(USER, "My team", false));
    EXERCISE = this.exerciseService.createExercise(getExercise(List.of(TEAM)));
    return this.lessonsCategoryRepository.save(getLessonsCategory(EXERCISE, List.of(TEAM)));
  }

  @DisplayName("Send surveys for exercise lessons")
  @Test
  @WithMockUser(isAdmin = true)
  void sendExerciseLessonsTest() throws Exception {

    // -- PREPARE --
    String lessonSubject = "Subject";
    String lessonBody = "This is a lesson";
    LessonsSendInput lessonsSendInput = new LessonsSendInput();
    lessonsSendInput.setSubject(lessonSubject);
    lessonsSendInput.setBody(lessonBody);
    User user = userRepository.findById(LESSONCATEGORY.getUsers().getFirst()).orElseThrow();

    // -- EXECUTE --
    mvc.perform(
            post(EXERCISE_URI + "/" + EXERCISE.getId() + "/lessons_send")
                .content(asJsonString(lessonsSendInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    // -- ASSERT --
    verify(mailingService)
        .sendEmail(
            lessonSubject,
            lessonBody,
            List.of(user),
            exerciseRepository.findById(EXERCISE.getId()));
  }
}
