package io.veriguard.rest.attack_chain;

import static io.veriguard.database.model.SettingKeys.DEFAULT_SCENARIO_DASHBOARD;
import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
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
import io.veriguard.rest.attack_chain.form.AttackChainInput;
import io.veriguard.rest.attack_chain.form.AttackChainUpdateTeamsInput;
import io.veriguard.rest.attack_chain.form.CheckAttackChainRulesInput;
import io.veriguard.service.AssetGroupService;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import io.veriguard.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
@Transactional
public class AttackChainApiTest extends IntegrationTest {

  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private AttackChainNodeStatusComposer attackChainNodeStatusComposer;
  @Autowired private AttackChainComposer attackChainComposer;
  @Autowired private ExecutorFixture executorFixture;

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AttackChainRepository attackChainRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private AttackChainTeamUserRepository attackChainTeamUserRepository;
  @Autowired private SettingRepository settingRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private AssetGroupService assetGroupService;

  @AfterEach
  void afterEach() {
    agentComposer.reset();
    endpointComposer.reset();
    attackChainNodeComposer.reset();
    attackChainNodeStatusComposer.reset();
    attackChainComposer.reset();
  }

  @DisplayName("Create scenario succeed")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void createAttackChainTest() throws Exception {
    // -- PREPARE --
    AttackChainInput attackChainInput = new AttackChainInput();

    // -- EXECUTE & ASSERT --
    this.mvc
        .perform(
            post(SCENARIO_URI)
                .content(asJsonString(attackChainInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());

    // -- PREPARE --
    String name = "My scenario";
    attackChainInput.setName(name);
    String from = "no-reply@veriguard.io";
    attackChainInput.setFrom(from);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI)
                    .content(asJsonString(attackChainInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.scenario_name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    String attackChainId = JsonPath.read(response, "$.scenario_id");
    assertFalse(attackChainId.isEmpty());
  }

  @DisplayName("Create scenario succeed with default dashboard")
  @Test
  @WithMockUser(isAdmin = true)
  void given_scenario_creation_should_set_default_custom_dashboard() throws Exception {
    // -- PREPARE --
    CustomDashboard defaultDashboard = new CustomDashboard();
    defaultDashboard.setName("Default scenario dashboard");
    CustomDashboard customDashboardSaved = customDashboardRepository.save(defaultDashboard);

    AttackChainInput attackChainInput = new AttackChainInput();
    String name = "My scenario";
    attackChainInput.setName(name);
    String from = "no-reply@veriguard.io";
    attackChainInput.setFrom(from);

    settingRepository.save(
        settingRepository
            .findByKey(DEFAULT_SCENARIO_DASHBOARD.key())
            .map(
                s -> {
                  s.setValue(customDashboardSaved.getId());
                  return s;
                })
            .orElseGet(
                () -> new Setting(DEFAULT_SCENARIO_DASHBOARD.key(), customDashboardSaved.getId())));

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI)
                    .content(asJsonString(attackChainInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.scenario_name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String newAttackChainId = JsonPath.read(response, "$.scenario_id");
    AttackChain newAttackChain =
        this.attackChainRepository.findById(newAttackChainId).orElseThrow();
    assertEquals(customDashboardSaved.getId(), newAttackChain.getCustomDashboard().getId());
  }

  @DisplayName("Retrieve scenarios")
  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void retrieveAttackChainsTest() throws Exception {
    // -- PREPARE --
    attackChainComposer
        .forAttackChain(AttackChainFixture.createDefaultCrisisAttackChain())
        .persist()
        .get();

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(get(SCENARIO_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Retrieve scenario")
  @Test
  @Order(3)
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void retrieveAttackChainTest() throws Exception {
    // -- PREPARE --
    AttackChain testAttackChain =
        attackChainComposer
            .forAttackChain(AttackChainFixture.createDefaultCrisisAttackChain())
            .persist()
            .get();

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + testAttackChain.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Requesting non existing scenario by ID fails gracefully")
  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void failsafeNonExistAttackChainId() throws Exception {
    // -- EXECUTE --
    this.mvc
        .perform(
            get(SCENARIO_URI + "/DOESNOTEXIST").accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @DisplayName("Update scenario")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void updateAttackChainTest() throws Exception {
    // -- PREPARE --
    AttackChain testAttackChain =
        attackChainComposer
            .forAttackChain(AttackChainFixture.createDefaultCrisisAttackChain())
            .persist()
            .get();

    AttackChainInput attackChainInput = new AttackChainInput();
    String subtitle = "A subtitle";
    attackChainInput.setName(testAttackChain.getName());
    attackChainInput.setFrom(testAttackChain.getFrom());
    attackChainInput.setSubtitle(subtitle);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/" + testAttackChain.getId())
                    .content(asJsonString(attackChainInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(subtitle, JsonPath.read(response, "$.scenario_subtitle"));
  }

  @DisplayName("Delete scenario")
  @Test
  @WithMockUser(withCapabilities = {Capability.DELETE_ASSESSMENT})
  void deleteAttackChainTest() throws Exception {
    // -- PREPARE --
    AttackChain testAttackChain =
        attackChainComposer
            .forAttackChain(AttackChainFixture.createDefaultCrisisAttackChain())
            .persist()
            .get();

    // -- EXECUTE 1 ASSERT --
    this.mvc
        .perform(delete(SCENARIO_URI + "/" + testAttackChain.getId()).with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  @DisplayName("Check if a rule applies when a rule is found")
  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void checkIfRuleAppliesTest_WHEN_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    Tag tag2 = TagFixture.getTag();
    tag2.setName("tag2");
    tag2 = this.tagRepository.save(tag2);

    AssetGroup assetGroup =
        assetGroupRepository.save(AssetGroupFixture.createDefaultAssetGroup("assetGroup"));
    TagRule tagRule = new TagRule();
    tagRule.setTag(tag2);
    tagRule.setAssetGroups(List.of(assetGroup));
    this.tagRuleRepository.save(tagRule);

    AttackChain attackChain = this.attackChainRepository.save(AttackChainFixture.getAttackChain());

    CheckAttackChainRulesInput input = new CheckAttackChainRulesInput();
    input.setNewTags(List.of(tag2.getId()));
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/" + attackChain.getId() + "/check-rules")
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
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void checkIfRuleAppliesTest_WHEN_no_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    Tag tag2 = TagFixture.getTag();
    tag2.setName("tag2");
    tag2 = this.tagRepository.save(tag2);
    CheckAttackChainRulesInput input = new CheckAttackChainRulesInput();
    input.setNewTags(List.of(tag2.getId()));

    AttackChain attackChain = this.attackChainRepository.save(AttackChainFixture.getAttackChain());

    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/" + attackChain.getId() + "/check-rules")
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
  @DisplayName("Should enable all users of newly added teams when replacing scenario teams")
  @WithMockUser(isAdmin = true)
  void replacingTeamsShouldEnableNewTeamUsers() throws Exception {
    // -- PREPARE --
    User userTom = userRepository.save(UserFixture.getUser("Tom", "TEST", "tom-test@fake.email"));
    User userBen = userRepository.save(UserFixture.getUser("Ben", "TEST", "ben-test@fake.email"));

    Team teamA = TeamFixture.getTeam(userTom, "TeamA", false);
    teamA.setUsers(List.of(userTom));
    teamRepository.save(teamA);
    Team teamB = TeamFixture.getTeam(userBen, "TeamB", false);
    teamB.setUsers(List.of(userBen));
    teamRepository.save(teamB);

    AttackChain attackChain = AttackChainFixture.createDefaultCrisisAttackChain();
    attackChain.setTeams(Collections.singletonList(teamA));
    AttackChain attackChainSaved = attackChainRepository.save(attackChain);

    // -- ACT --
    List<String> newTeamIds = Arrays.asList(teamA.getId(), teamB.getId());
    AttackChainUpdateTeamsInput input = new AttackChainUpdateTeamsInput();
    input.setTeamIds(newTeamIds);

    mvc.perform(
            put(SCENARIO_URI + "/" + attackChainSaved.getId() + "/teams/replace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());

    // -- ASSERT --
    List<AttackChainTeamUser> links = attackChainTeamUserRepository.findAll();

    AttackChainTeamUser link = links.getFirst();
    assertEquals(attackChainSaved.getId(), link.getAttackChain().getId());
    assertEquals(teamB.getId(), link.getTeam().getId());
    assertEquals(userBen.getId(), link.getUser().getId());
  }
}
