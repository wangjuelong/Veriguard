package io.veriguard.rest.exercise;

import static io.veriguard.database.model.SettingKeys.DEFAULT_SIMULATION_DASHBOARD;
import static io.veriguard.database.specification.TeamSpecification.fromExercise;
import static io.veriguard.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.model.Tag;
import io.veriguard.database.repository.*;
import io.veriguard.rest.exercise.form.*;
import io.veriguard.rest.inject.form.InjectInput;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utilstest.RabbitMQTestListener;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestInstance(PER_CLASS)
public class ExerciseApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutorFixture executorFixture;

  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private SettingRepository settingRepository;

  List<ExerciseComposer.Composer> exerciseWrapperComposers = new ArrayList<>();
  private static final List<String> EXERCISE_IDS = new ArrayList<>();
  private static final List<String> USER_IDS = new ArrayList<>();
  private static final List<String> TEAM_IDS = new ArrayList<>();

  @AfterAll
  void afterAll() {
    exerciseWrapperComposers.forEach(ExerciseComposer.Composer::delete);
    this.exerciseRepository.deleteAllById(EXERCISE_IDS);
    this.userRepository.deleteAllById(USER_IDS);
    this.teamRepository.deleteAllById(TEAM_IDS);
    this.tagRuleRepository.deleteAll();
    this.assetGroupRepository.deleteAll();
    this.tagRepository.deleteAll();
  }

  @DisplayName("Create simulation succeed with default dashboard")
  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
  void given_exercise_creation_should_set_default_custom_dashboard() throws Exception {
    // -- PREPARE --
    CustomDashboard defaultDashboard = new CustomDashboard();
    defaultDashboard.setName("Default scenario dashboard");
    CustomDashboard customDashboardSaved = customDashboardRepository.save(defaultDashboard);

    ExerciseInput exerciseInput = new ExerciseInput();
    String name = "My scenario";
    exerciseInput.setName(name);

    settingRepository.save(
        settingRepository
            .findByKey(DEFAULT_SIMULATION_DASHBOARD.key())
            .map(
                s -> {
                  s.setValue(customDashboardSaved.getId());
                  return s;
                })
            .orElseGet(
                () ->
                    new Setting(DEFAULT_SIMULATION_DASHBOARD.key(), customDashboardSaved.getId())));

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI)
                    .content(asJsonString(exerciseInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.exercise_name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String newExerciseId = JsonPath.read(response, "$.exercise_id");
    Exercise newExercise = this.exerciseRepository.findById(newExerciseId).orElseThrow();
    assertEquals(customDashboardSaved.getId(), newExercise.getCustomDashboard().getId());
  }

  @Nested
  @DisplayName("Retrieving exercise informations")
  class RetrievingExercises {
    @Test
    @DisplayName("Retrieving players by exercise")
    @WithMockUser(isAdmin = true)
    void retrievingPlayersByExercise() throws Exception {
      // -- PREPARE --
      User userTom = userRepository.save(UserFixture.getUser("Tom", "TEST", "tom-test@fake.email"));
      User userBen = userRepository.save(UserFixture.getUser("Ben", "TEST", "ben-test@fake.email"));
      USER_IDS.addAll(Arrays.asList(userTom.getId(), userBen.getId()));
      Team teamA = teamRepository.save(TeamFixture.getTeam(userTom, "TeamA", false));
      Team teamB = teamRepository.save(TeamFixture.getTeam(userBen, "TeamB", false));
      TEAM_IDS.addAll(Arrays.asList(teamA.getId(), teamB.getId()));

      Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
      exercise.setTeams(Arrays.asList(teamA, teamB));
      Exercise exerciseSaved = exerciseRepository.save(exercise);
      EXERCISE_IDS.add(exerciseSaved.getId());

      ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
      exerciseTeamUser.setExercise(exerciseSaved);
      exerciseTeamUser.setTeam(teamA);
      exerciseTeamUser.setUser(userTom);
      ExerciseTeamUser exerciseTeamUser2 = new ExerciseTeamUser();
      exerciseTeamUser2.setExercise(exerciseSaved);
      exerciseTeamUser2.setTeam(teamB);
      exerciseTeamUser2.setUser(userBen);
      exerciseTeamUserRepository.saveAll(Arrays.asList(exerciseTeamUser, exerciseTeamUser2));

      mvc.perform(
              get(EXERCISE_URI + "/" + exerciseSaved.getId() + "/players")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(
              jsonPath("$[*].user_id")
                  .value(
                      org.hamcrest.Matchers.containsInAnyOrder(userTom.getId(), userBen.getId())));
    }

    @Test
    @DisplayName("Get global score for exercises")
    @WithMockUser(isAdmin = true)
    void getGlobalScoreForExercises() throws Exception {
      Exercise exercise1 = ExerciseFixture.createDefaultCrisisExercise();
      Exercise exercise1Saved = exerciseRepository.save(exercise1);
      EXERCISE_IDS.add(exercise1Saved.getId());

      Exercise exercise2 = ExerciseFixture.createDefaultIncidentResponseExercise();
      Exercise exercise2Saved = exerciseRepository.save(exercise2);
      EXERCISE_IDS.add(exercise2Saved.getId());

      ExercisesGlobalScoresInput input =
          new ExercisesGlobalScoresInput(List.of(exercise1Saved.getId(), exercise2Saved.getId()));

      String response =
          mvc.perform(
                  post(EXERCISE_URI + "/global-scores")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertEquals(
          "[]",
          JsonPath.read(response, "$.global_scores_by_exercise_ids." + exercise1Saved.getId())
              .toString());
      assertEquals(
          "[]",
          JsonPath.read(response, "$.global_scores_by_exercise_ids." + exercise2Saved.getId())
              .toString());
    }
  }

  @Test
  @DisplayName("Get scenario from exercise id")
  @WithMockUser(isAdmin = true)
  void givenExerciseId_whenGettingScenarioFromExercise_thenReturnScenario() throws Exception {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    Scenario scenarioSaved = scenarioRepository.save(scenario);

    Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
    exercise.setScenario(scenarioSaved);

    Exercise exerciseSaved = exerciseRepository.save(exercise);

    String response =
        mvc.perform(
                get(EXERCISE_URI + "/" + exerciseSaved.getId() + "/scenario")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(scenarioSaved.getId(), JsonPath.read(response, "$.scenario_id"));
  }

  @DisplayName("Check if a rule applies when a rule is found")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void checkIfRuleAppliesTest_WHEN_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    io.veriguard.database.model.Tag tag2 = TagFixture.getTag();
    tag2.setName("tag2");
    tag2 = this.tagRepository.save(tag2);

    AssetGroup assetGroup =
        assetGroupRepository.save(AssetGroupFixture.createDefaultAssetGroup("assetGroup"));
    TagRule tagRule = new TagRule();
    tagRule.setTag(tag2);
    tagRule.setAssetGroups(List.of(assetGroup));
    this.tagRuleRepository.save(tagRule);

    Exercise exercise = this.exerciseRepository.save(ExerciseFixture.createDefaultCrisisExercise());

    CheckExerciseRulesInput input = new CheckExerciseRulesInput();
    input.setNewTags(List.of(tag2.getId()));
    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI + "/" + exercise.getId() + "/check-rules")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(response);
    assertEquals(true, JsonPath.read(response, "$.rules_found"));
  }

  @DisplayName("Check if a rule applies when no rule is found")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void checkIfRuleAppliesTest_WHEN_no_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    Tag tag2 = TagFixture.getTag();
    tag2.setName("tag2");
    tag2 = this.tagRepository.save(tag2);
    CheckExerciseRulesInput input = new CheckExerciseRulesInput();
    input.setNewTags(List.of(tag2.getId()));

    Exercise exercise = this.exerciseRepository.save(ExerciseFixture.createDefaultCrisisExercise());

    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI + "/" + exercise.getId() + "/check-rules")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(response);
    assertEquals(false, JsonPath.read(response, "$.rules_found"));
  }

  @Test
  @Transactional
  @DisplayName("Should enable all users of newly added teams when replacing exercise teams")
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void replacingTeamsShouldEnableNewTeamUsers() throws Exception {
    // -- PREPARE --
    User userTom = userRepository.save(UserFixture.getUser("Tom", "TEST", "tom-test@fake.email"));
    User userBen = userRepository.save(UserFixture.getUser("Ben", "TEST", "ben-test@fake.email"));
    USER_IDS.addAll(Arrays.asList(userTom.getId(), userBen.getId()));

    Team teamA = TeamFixture.getTeam(userTom, "TeamA", false);
    teamA.setUsers(List.of(userTom));
    teamRepository.save(teamA);
    Team teamB = TeamFixture.getTeam(userBen, "TeamB", false);
    teamB.setUsers(List.of(userBen));
    teamRepository.save(teamB);

    TEAM_IDS.addAll(Arrays.asList(teamA.getId(), teamB.getId()));

    Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
    exercise.setTeams(Collections.singletonList(teamA));
    Exercise exerciseSaved = exerciseRepository.save(exercise);
    EXERCISE_IDS.add(exerciseSaved.getId());

    // -- ACT --
    List<String> newTeamIds = Arrays.asList(teamA.getId(), teamB.getId());
    ExerciseUpdateTeamsInput input = new ExerciseUpdateTeamsInput();
    input.setTeamIds(newTeamIds);

    mvc.perform(
            put(EXERCISE_URI + "/" + exerciseSaved.getId() + "/teams/replace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());

    // -- ASSERT --
    List<ExerciseTeamUser> links = exerciseTeamUserRepository.findAll();

    ExerciseTeamUser link = links.getFirst();
    assertEquals(exerciseSaved.getId(), link.getExercise().getId());
    assertEquals(teamB.getId(), link.getTeam().getId());
    assertEquals(userBen.getId(), link.getUser().getId());
  }

  @Nested
  @Transactional
  @DisplayName("replaceTeams - scoped cleanup and no cross-exercise side effects")
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  class ReplaceTeamsIntegration {

    @Test
    @DisplayName(
        "Deselecting a team should remove its exercise-team-user links only for that exercise")
    void deselectedTeamShouldRemoveLinksOnlyForCurrentExercise() throws Exception {
      // -- PREPARE --
      User userTom = userRepository.save(UserFixture.getUser("Tom", "RT1", "tom-rt1@fake.email"));
      USER_IDS.add(userTom.getId());

      Team sharedTeam = new Team();
      sharedTeam.setName("SharedTeam-RT1");
      sharedTeam.setUsers(List.of(userTom));
      teamRepository.save(sharedTeam);
      TEAM_IDS.add(sharedTeam.getId());

      // exerciseA
      Exercise exerciseA = ExerciseFixture.createDefaultCrisisExercise();
      exerciseA.setTeams(List.of(sharedTeam));
      Exercise exerciseASaved = exerciseRepository.save(exerciseA);
      EXERCISE_IDS.add(exerciseASaved.getId());

      ExerciseTeamUser linkA = new ExerciseTeamUser();
      linkA.setExercise(exerciseASaved);
      linkA.setTeam(sharedTeam);
      linkA.setUser(userTom);
      exerciseTeamUserRepository.save(linkA);

      // exerciseB
      Exercise exerciseB = ExerciseFixture.createDefaultCrisisExercise();
      exerciseB.setTeams(List.of(sharedTeam));
      Exercise exerciseBSaved = exerciseRepository.save(exerciseB);
      EXERCISE_IDS.add(exerciseBSaved.getId());

      ExerciseTeamUser linkB = new ExerciseTeamUser();
      linkB.setExercise(exerciseBSaved);
      linkB.setTeam(sharedTeam);
      linkB.setUser(userTom);
      exerciseTeamUserRepository.save(linkB);

      // -- ACT : We remove sharedTeam from exerciseA --
      ExerciseUpdateTeamsInput input = new ExerciseUpdateTeamsInput();
      input.setTeamIds(List.of());

      mvc.perform(
              put(EXERCISE_URI + "/" + exerciseASaved.getId() + "/teams/replace")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      // The link of exerciseA must have been deleted
      assertTrue(
          exerciseTeamUserRepository.rawByExerciseIds(List.of(exerciseASaved.getId())).isEmpty(),
          "The link exercise-team-user of exerciseA should have been deleted");

      // The link of exerciseB must still exist
      var linksB = exerciseTeamUserRepository.rawByExerciseIds(List.of(exerciseBSaved.getId()));
      assertEquals(1, linksB.size(), "The link exercise-team-user of exerciseB should be intact");
      assertEquals(sharedTeam.getId(), linksB.getFirst().getTeam_id());

      // exerciseA should not have any team anymore
      List<Team> exerciseATeams = teamRepository.findAll(fromExercise(exerciseASaved.getId()));
      assertTrue(exerciseATeams.isEmpty());

      // exerciseB should still have sharedTeam in its team list
      List<Team> exerciseBTeams = teamRepository.findAll(fromExercise(exerciseBSaved.getId()));
      assertEquals(1, exerciseBTeams.size());
      assertEquals(sharedTeam.getId(), exerciseBTeams.getFirst().getId());
    }

    @Test
    @DisplayName("Replacing teams should update the exercise team list in database")
    void replacingTeamsShouldPersistNewTeamListInDatabase() throws Exception {
      // -- PREPARE --
      Team teamToRemove = new Team();
      teamToRemove.setName("TeamToRemove-RT3");
      teamRepository.save(teamToRemove);
      TEAM_IDS.add(teamToRemove.getId());

      Team teamToKeep = new Team();
      teamToKeep.setName("TeamToKeep-RT3");
      teamRepository.save(teamToKeep);
      TEAM_IDS.add(teamToKeep.getId());

      Team teamToAdd = new Team();
      teamToAdd.setName("TeamToAdd-RT3");
      teamRepository.save(teamToAdd);
      TEAM_IDS.add(teamToAdd.getId());

      Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
      exercise.setTeams(List.of(teamToRemove, teamToKeep));
      Exercise exerciseSaved = exerciseRepository.save(exercise);
      EXERCISE_IDS.add(exerciseSaved.getId());

      ExerciseUpdateTeamsInput input = new ExerciseUpdateTeamsInput();
      input.setTeamIds(List.of(teamToKeep.getId(), teamToAdd.getId()));

      // -- ACT --
      mvc.perform(
              put(EXERCISE_URI + "/" + exerciseSaved.getId() + "/teams/replace")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      List<String> teamIds =
          teamRepository.findAll(fromExercise(exerciseSaved.getId())).stream()
              .map(Team::getId)
              .toList();

      assertEquals(2, teamIds.size());
      assertTrue(teamIds.contains(teamToKeep.getId()), "teamToKeep should still be present.");
      assertTrue(teamIds.contains(teamToAdd.getId()), "teamToAdd should be present");
      assertFalse(teamIds.contains(teamToRemove.getId()), "teamToRemove should have been removed");
    }
  }
}
