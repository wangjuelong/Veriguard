package io.veriguard.rest.attack_chain_run;

import static io.veriguard.database.model.SettingKeys.DEFAULT_SIMULATION_DASHBOARD;
import static io.veriguard.database.specification.TeamSpecification.fromAttackChainRun;
import static io.veriguard.rest.attack_chain_run.AttackChainRunApi.EXERCISE_URI;
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
import io.veriguard.rest.attack_chain_run.form.*;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utilstest.RabbitMQTestListener;
import jakarta.transaction.Transactional;
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
public class AttackChainRunApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AttackChainRunComposer attackChainRunComposer;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private AttackChainNodeStatusComposer attackChainNodeStatusComposer;
  @Autowired private ExecutorFixture executorFixture;

  @Autowired private AttackChainRunRepository attackChainRunRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private AttackChainRepository attackChainRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private SettingRepository settingRepository;

  List<AttackChainRunComposer.Composer> attackChainRunWrapperComposers = new ArrayList<>();
  private static final List<String> EXERCISE_IDS = new ArrayList<>();
  private static final List<String> USER_IDS = new ArrayList<>();
  private static final List<String> TEAM_IDS = new ArrayList<>();

  @AfterAll
  void afterAll() {
    attackChainRunWrapperComposers.forEach(AttackChainRunComposer.Composer::delete);
    this.attackChainRunRepository.deleteAllById(EXERCISE_IDS);
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

    AttackChainRunInput attackChainRunInput = new AttackChainRunInput();
    String name = "My scenario";
    attackChainRunInput.setName(name);

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
                    .content(asJsonString(attackChainRunInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.exercise_name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String newAttackChainRunId = JsonPath.read(response, "$.exercise_id");
    AttackChainRun newAttackChainRun =
        this.attackChainRunRepository.findById(newAttackChainRunId).orElseThrow();
    assertEquals(customDashboardSaved.getId(), newAttackChainRun.getCustomDashboard().getId());
  }

  @Nested
  @DisplayName("Retrieving exercise informations")
  class RetrievingAttackChainRuns {
    @Test
    @DisplayName("Retrieving players by exercise")
    @WithMockUser(isAdmin = true)
    void retrievingPlayersByAttackChainRun() throws Exception {
      // -- PREPARE --
      User userTom = userRepository.save(UserFixture.getUser("Tom", "TEST", "tom-test@fake.email"));
      User userBen = userRepository.save(UserFixture.getUser("Ben", "TEST", "ben-test@fake.email"));
      USER_IDS.addAll(Arrays.asList(userTom.getId(), userBen.getId()));
      Team teamA = teamRepository.save(TeamFixture.getTeam(userTom, "TeamA", false));
      Team teamB = teamRepository.save(TeamFixture.getTeam(userBen, "TeamB", false));
      TEAM_IDS.addAll(Arrays.asList(teamA.getId(), teamB.getId()));

      AttackChainRun attackChainRun = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
      attackChainRun.setTeams(Arrays.asList(teamA, teamB));
      AttackChainRun attackChainRunSaved = attackChainRunRepository.save(attackChainRun);
      EXERCISE_IDS.add(attackChainRunSaved.getId());

      AttackChainRunTeamUser attackChainRunTeamUser = new AttackChainRunTeamUser();
      attackChainRunTeamUser.setAttackChainRun(attackChainRunSaved);
      attackChainRunTeamUser.setTeam(teamA);
      attackChainRunTeamUser.setUser(userTom);
      AttackChainRunTeamUser attackChainRunTeamUser2 = new AttackChainRunTeamUser();
      attackChainRunTeamUser2.setAttackChainRun(attackChainRunSaved);
      attackChainRunTeamUser2.setTeam(teamB);
      attackChainRunTeamUser2.setUser(userBen);
      attackChainRunTeamUserRepository.saveAll(
          Arrays.asList(attackChainRunTeamUser, attackChainRunTeamUser2));

      mvc.perform(
              get(EXERCISE_URI + "/" + attackChainRunSaved.getId() + "/players")
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
    void getGlobalScoreForAttackChainRuns() throws Exception {
      AttackChainRun attackChainRun1 = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
      AttackChainRun attackChainRun1Saved = attackChainRunRepository.save(attackChainRun1);
      EXERCISE_IDS.add(attackChainRun1Saved.getId());

      AttackChainRun attackChainRun2 =
          AttackChainRunFixture.createDefaultIncidentResponseAttackChainRun();
      AttackChainRun attackChainRun2Saved = attackChainRunRepository.save(attackChainRun2);
      EXERCISE_IDS.add(attackChainRun2Saved.getId());

      AttackChainRunsGlobalScoresInput input =
          new AttackChainRunsGlobalScoresInput(
              List.of(attackChainRun1Saved.getId(), attackChainRun2Saved.getId()));

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
          JsonPath.read(response, "$.global_scores_by_exercise_ids." + attackChainRun1Saved.getId())
              .toString());
      assertEquals(
          "[]",
          JsonPath.read(response, "$.global_scores_by_exercise_ids." + attackChainRun2Saved.getId())
              .toString());
    }
  }

  @Test
  @DisplayName("Get scenario from exercise id")
  @WithMockUser(isAdmin = true)
  void givenAttackChainRunId_whenGettingAttackChainFromExercise_thenReturnAttackChain()
      throws Exception {
    AttackChain attackChain = AttackChainFixture.createDefaultCrisisAttackChain();
    AttackChain attackChainSaved = attackChainRepository.save(attackChain);

    AttackChainRun attackChainRun = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
    attackChainRun.setAttackChain(attackChainSaved);

    AttackChainRun attackChainRunSaved = attackChainRunRepository.save(attackChainRun);

    String response =
        mvc.perform(
                get(EXERCISE_URI + "/" + attackChainRunSaved.getId() + "/scenario")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(attackChainSaved.getId(), JsonPath.read(response, "$.scenario_id"));
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

    AttackChainRun attackChainRun =
        this.attackChainRunRepository.save(
            AttackChainRunFixture.createDefaultCrisisAttackChainRun());

    CheckAttackChainRunRulesInput input = new CheckAttackChainRunRulesInput();
    input.setNewTags(List.of(tag2.getId()));
    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI + "/" + attackChainRun.getId() + "/check-rules")
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
    CheckAttackChainRunRulesInput input = new CheckAttackChainRunRulesInput();
    input.setNewTags(List.of(tag2.getId()));

    AttackChainRun attackChainRun =
        this.attackChainRunRepository.save(
            AttackChainRunFixture.createDefaultCrisisAttackChainRun());

    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI + "/" + attackChainRun.getId() + "/check-rules")
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

    AttackChainRun attackChainRun = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
    attackChainRun.setTeams(Collections.singletonList(teamA));
    AttackChainRun attackChainRunSaved = attackChainRunRepository.save(attackChainRun);
    EXERCISE_IDS.add(attackChainRunSaved.getId());

    // -- ACT --
    List<String> newTeamIds = Arrays.asList(teamA.getId(), teamB.getId());
    AttackChainRunUpdateTeamsInput input = new AttackChainRunUpdateTeamsInput();
    input.setTeamIds(newTeamIds);

    mvc.perform(
            put(EXERCISE_URI + "/" + attackChainRunSaved.getId() + "/teams/replace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());

    // -- ASSERT --
    List<AttackChainRunTeamUser> links = attackChainRunTeamUserRepository.findAll();

    AttackChainRunTeamUser link = links.getFirst();
    assertEquals(attackChainRunSaved.getId(), link.getAttackChainRun().getId());
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
    void deselectedTeamShouldRemoveLinksOnlyForCurrentAttackChainRun() throws Exception {
      // -- PREPARE --
      User userTom = userRepository.save(UserFixture.getUser("Tom", "RT1", "tom-rt1@fake.email"));
      USER_IDS.add(userTom.getId());

      Team sharedTeam = new Team();
      sharedTeam.setName("SharedTeam-RT1");
      sharedTeam.setUsers(List.of(userTom));
      teamRepository.save(sharedTeam);
      TEAM_IDS.add(sharedTeam.getId());

      // attackChainRunA
      AttackChainRun attackChainRunA = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
      attackChainRunA.setTeams(List.of(sharedTeam));
      AttackChainRun attackChainRunASaved = attackChainRunRepository.save(attackChainRunA);
      EXERCISE_IDS.add(attackChainRunASaved.getId());

      AttackChainRunTeamUser linkA = new AttackChainRunTeamUser();
      linkA.setAttackChainRun(attackChainRunASaved);
      linkA.setTeam(sharedTeam);
      linkA.setUser(userTom);
      attackChainRunTeamUserRepository.save(linkA);

      // attackChainRunB
      AttackChainRun attackChainRunB = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
      attackChainRunB.setTeams(List.of(sharedTeam));
      AttackChainRun attackChainRunBSaved = attackChainRunRepository.save(attackChainRunB);
      EXERCISE_IDS.add(attackChainRunBSaved.getId());

      AttackChainRunTeamUser linkB = new AttackChainRunTeamUser();
      linkB.setAttackChainRun(attackChainRunBSaved);
      linkB.setTeam(sharedTeam);
      linkB.setUser(userTom);
      attackChainRunTeamUserRepository.save(linkB);

      // -- ACT : We remove sharedTeam from attackChainRunA --
      AttackChainRunUpdateTeamsInput input = new AttackChainRunUpdateTeamsInput();
      input.setTeamIds(List.of());

      mvc.perform(
              put(EXERCISE_URI + "/" + attackChainRunASaved.getId() + "/teams/replace")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      // The link of attackChainRunA must have been deleted
      assertTrue(
          attackChainRunTeamUserRepository
              .rawByAttackChainRunIds(List.of(attackChainRunASaved.getId()))
              .isEmpty(),
          "The link exercise-team-user of exerciseA should have been deleted");

      // The link of attackChainRunB must still exist
      var linksB =
          attackChainRunTeamUserRepository.rawByAttackChainRunIds(
              List.of(attackChainRunBSaved.getId()));
      assertEquals(1, linksB.size(), "The link exercise-team-user of exerciseB should be intact");
      assertEquals(sharedTeam.getId(), linksB.getFirst().getTeam_id());

      // attackChainRunA should not have any team anymore
      List<Team> attackChainRunATeams =
          teamRepository.findAll(fromAttackChainRun(attackChainRunASaved.getId()));
      assertTrue(attackChainRunATeams.isEmpty());

      // attackChainRunB should still have sharedTeam in its team list
      List<Team> attackChainRunBTeams =
          teamRepository.findAll(fromAttackChainRun(attackChainRunBSaved.getId()));
      assertEquals(1, attackChainRunBTeams.size());
      assertEquals(sharedTeam.getId(), attackChainRunBTeams.getFirst().getId());
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

      AttackChainRun attackChainRun = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
      attackChainRun.setTeams(List.of(teamToRemove, teamToKeep));
      AttackChainRun attackChainRunSaved = attackChainRunRepository.save(attackChainRun);
      EXERCISE_IDS.add(attackChainRunSaved.getId());

      AttackChainRunUpdateTeamsInput input = new AttackChainRunUpdateTeamsInput();
      input.setTeamIds(List.of(teamToKeep.getId(), teamToAdd.getId()));

      // -- ACT --
      mvc.perform(
              put(EXERCISE_URI + "/" + attackChainRunSaved.getId() + "/teams/replace")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      List<String> teamIds =
          teamRepository.findAll(fromAttackChainRun(attackChainRunSaved.getId())).stream()
              .map(Team::getId)
              .toList();

      assertEquals(2, teamIds.size());
      assertTrue(teamIds.contains(teamToKeep.getId()), "teamToKeep should still be present.");
      assertTrue(teamIds.contains(teamToAdd.getId()), "teamToAdd should be present");
      assertFalse(teamIds.contains(teamToRemove.getId()), "teamToRemove should have been removed");
    }
  }
}
