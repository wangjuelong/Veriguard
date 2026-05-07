package io.veriguard.rest.attack_chain;

import static io.veriguard.database.specification.TeamSpecification.fromAttackChain;
import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static io.veriguard.utils.fixtures.AttackChainFixture.getAttackChain;
import static io.veriguard.utils.fixtures.TeamFixture.TEAM_NAME;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainTeamUser;
import io.veriguard.database.model.Team;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.AttackChainRepository;
import io.veriguard.database.repository.AttackChainTeamUserRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.attack_chain_run.form.AttackChainRunTeamPlayersEnableInput;
import io.veriguard.rest.attack_chain_run.form.AttackChainTeamPlayersEnableInput;
import io.veriguard.rest.attack_chain.form.AttackChainUpdateTeamsInput;
import io.veriguard.utils.fixtures.PaginationFixture;
import io.veriguard.utils.fixtures.UserFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class AttackChainTeamApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private AttackChainRepository attackChainRepository;
  @Autowired private AttackChainTeamUserRepository attackChainTeamUserRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;

  @DisplayName("Given a valid scenario and team input, should add team to scenario successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAttackChainAndTeamInput_should_replaceTeamToAttackChainSuccessfully() throws Exception {
    // -- PREPARE --
    AttackChain attackChain = getAttackChain();
    Team teamToRemove = new Team();
    teamToRemove.setName("teamToRemove");
    Team teamToRemoveSaved = this.teamRepository.save(teamToRemove);
    attackChain.setTeams(List.of(teamToRemoveSaved));
    AttackChain attackChainCreated = this.attackChainRepository.save(attackChain);

    Team teamToAdd = new Team();
    teamToAdd.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(teamToAdd);
    AttackChainUpdateTeamsInput input = new AttackChainUpdateTeamsInput();
    input.setTeamIds(List.of(teamCreated.getId()));

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/" + attackChainCreated.getId() + "/teams/replace")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    ObjectMapper objectMapper = new ObjectMapper();
    List<Map<String, Object>> responseList =
        objectMapper.readValue(response, new TypeReference<List<Map<String, Object>>>() {});
    assertEquals(2, responseList.size());
    Map<String, Map<String, Object>> responseMap =
        responseList.stream()
            .collect(
                Collectors.toMap(
                    obj -> (String) obj.get("team_name"),
                    obj -> obj,
                    (existing, duplicate) -> {
                      throw new IllegalStateException("Duplicate key found: " + existing);
                    }));

    assertEquals(List.of(), responseMap.get("teamToRemove").get("team_scenarios"));
    assertEquals(
        List.of(attackChainCreated.getId()), responseMap.get(TEAM_NAME).get("team_scenarios"));

    Optional<AttackChain> attackChainSaved = this.attackChainRepository.findById(attackChainCreated.getId());
    assertTrue(attackChainSaved.isPresent());
    assertEquals(1, (long) attackChainSaved.get().getTeams().size());
    assertEquals(teamCreated.getId(), attackChainSaved.get().getTeams().getFirst().getId());
  }

  @DisplayName("Given a valid scenario with teams, should retrieve teams successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAttackChainWithTeams_should_retrieveTeamsSuccessfully() throws Exception {
    // -- PREPARE --
    Team team = new Team();
    team.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(team);

    AttackChain attackChain = getAttackChain();
    attackChain.setTeams(List.of(teamCreated));
    AttackChain attackChainCreated = this.attackChainRepository.save(attackChain);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + attackChainCreated.getId() + "/teams")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(teamCreated.getId(), JsonPath.read(response, "$[0].team_id"));
  }

  @DisplayName("Given a valid scenario and team, should add player to team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAttackChainAndTeam_should_addPlayerToTeamSuccessfully() throws Exception {
    // -- PREPARE --
    Team team = new Team();
    team.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(team);

    AttackChain attackChain = getAttackChain();
    attackChain.setTeams(List.of(teamCreated));
    AttackChain attackChainCreated = this.attackChainRepository.save(attackChain);

    User user = UserFixture.getUser();
    User userCreated = this.userRepository.save(user);

    AttackChainTeamPlayersEnableInput input = new AttackChainTeamPlayersEnableInput();
    input.setPlayersIds(List.of(userCreated.getId()));

    // -- EXECUTE --
    this.mvc
        .perform(
            put(SCENARIO_URI
                    + "/"
                    + attackChainCreated.getId()
                    + "/teams/"
                    + teamCreated.getId()
                    + "/players/add")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // -- ASSERT --
    List<AttackChainTeamUser> attackChainTeamUsers =
        fromIterable(this.attackChainTeamUserRepository.findAll());
    assertTrue(!attackChainTeamUsers.isEmpty());
    assertTrue(
        attackChainTeamUsers.stream().anyMatch(s -> userCreated.getId().equals(s.getUser().getId())));
  }

  @DisplayName(
      "Given a valid scenario and team with a player, should remove player from team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAttackChainAndTeamWithPlayer_should_removePlayerFromTeamSuccessfully()
      throws Exception {
    // -- PREPARE --
    Team team = new Team();
    team.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(team);

    AttackChain attackChain = getAttackChain();
    attackChain.setTeams(List.of(teamCreated));
    AttackChain attackChainCreated = this.attackChainRepository.save(attackChain);

    User user = UserFixture.getUser();
    User userCreated = this.userRepository.save(user);

    AttackChainTeamUser attackChainTeamUser = new AttackChainTeamUser();
    attackChainTeamUser.setAttackChain(attackChainCreated);
    attackChainTeamUser.setTeam(teamCreated);
    attackChainTeamUser.setUser(userCreated);
    this.attackChainTeamUserRepository.save(attackChainTeamUser);

    AttackChainRunTeamPlayersEnableInput input = new AttackChainRunTeamPlayersEnableInput();
    input.setPlayersIds(List.of(userCreated.getId()));

    // -- EXECUTE --
    this.mvc
        .perform(
            put(SCENARIO_URI
                    + "/"
                    + attackChainCreated.getId()
                    + "/teams/"
                    + teamCreated.getId()
                    + "/players/remove")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // -- ASSERT --
    List<AttackChainTeamUser> attackChainTeamUsers =
        fromIterable(this.attackChainTeamUserRepository.findAll());
    assertTrue(!attackChainTeamUsers.isEmpty());
    assertFalse(attackChainTeamUsers.stream().anyMatch(s -> s.getAttackChain() == null));
  }

  @DisplayName("Given a valid scenario with a team, should remove team from scenario successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAttackChainWithTeam_should_removeTeamFromAttackChainSuccessfully() throws Exception {
    // -- PREPARE --
    Team team = new Team();
    team.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(team);

    AttackChain attackChain = getAttackChain();
    attackChain.setTeams(List.of(teamCreated));
    AttackChain attackChainCreated = this.attackChainRepository.save(attackChain);

    AttackChainUpdateTeamsInput input = new AttackChainUpdateTeamsInput();
    input.setTeamIds(List.of(teamCreated.getId()));

    // -- EXECUTE --
    this.mvc
        .perform(
            put(SCENARIO_URI + "/" + attackChainCreated.getId() + "/teams/remove")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // -- ASSERT --
    List<Team> teams = this.teamRepository.findAll(fromAttackChain(attackChainCreated.getId()));
    assertTrue(teams.isEmpty());
  }

  @DisplayName("Scenario team search")
  @Nested
  @WithMockUser(isAdmin = true)
  class SearchAttackChainTeams {
    @Test
    @DisplayName("Returns global and scenario teams when searching teams")
    void givenContextualOnlyFalse_whenSearchingTeams_shouldReturnGlobalAndAttackChainTeams()
        throws Exception {
      Team team = new Team();
      String teamName = "Team test";
      team.setName(teamName);

      Team contextualTeam = new Team();
      contextualTeam.setName(teamName + " 2");
      contextualTeam.setContextual(true);
      List<Team> savedTeams = teamRepository.saveAll(List.of(team, contextualTeam));

      AttackChain attackChain = getAttackChain();
      attackChain.setTeams(savedTeams.stream().filter(Team::getContextual).toList());
      AttackChain attackChainCreated = attackChainRepository.save(attackChain);

      SearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setTextSearch(teamName);

      mvc.perform(
              post(SCENARIO_URI
                      + "/"
                      + attackChainCreated.getId()
                      + "/teams/search?contextualOnly=false")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.content.size()").value(2))
          .andExpect(
              jsonPath(
                  "$.content[*].team_id",
                  containsInAnyOrder(savedTeams.stream().map(Team::getId).toArray(String[]::new))));
    }

    @Test
    @DisplayName("Returns only scenario teams")
    void givenContextualOnlyTrue_whenSearchingTeams_shouldReturnOnlyAttackChainTeams()
        throws Exception {
      Team team = new Team();
      String teamName = "Team test";
      team.setName(teamName);

      Team team1 = new Team();
      team1.setName(teamName + "1");

      Team contextualTeam = new Team();
      contextualTeam.setName(teamName + "3");
      contextualTeam.setContextual(true);

      Team contextualTeam1 = new Team();
      contextualTeam1.setName(teamName + "4");
      contextualTeam1.setContextual(true);

      teamRepository.saveAll(List.of(team, contextualTeam));
      List<Team> attackChainTeams = teamRepository.saveAll(List.of(team1, contextualTeam1));

      AttackChain attackChain = getAttackChain();
      attackChain.setTeams(attackChainTeams);
      AttackChain attackChainCreated = attackChainRepository.save(attackChain);

      SearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setTextSearch(teamName);

      mvc.perform(
              post(SCENARIO_URI
                      + "/"
                      + attackChainCreated.getId()
                      + "/teams/search?contextualOnly=true")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.content.size()").value(2))
          .andExpect(
              jsonPath(
                  "$.content[*].team_id",
                  containsInAnyOrder(
                      attackChainTeams.stream().map(Team::getId).toArray(String[]::new))));
    }
  }

  @DisplayName("replaceTeams - scoped cleanup and no cross-scenario side effects")
  @Nested
  @WithMockUser(isAdmin = true)
  class ReplaceTeamsIntegration {

    @Test
    @DisplayName(
        "Deselecting a team should remove its scenario-team-user links only for that scenario")
    void deselectedTeamShouldRemoveLinksOnlyForCurrentAttackChain() throws Exception {
      // -- PREPARE --
      User userTom = userRepository.save(UserFixture.getUser("Tom", "RT1", "tom-rt1sc@fake.email"));

      Team sharedTeam = new Team();
      sharedTeam.setName("SharedTeam-SC-RT1");
      sharedTeam.setUsers(List.of(userTom));
      teamRepository.save(sharedTeam);

      // attackChainA
      AttackChain attackChainA = getAttackChain();
      attackChainA.setTeams(List.of(sharedTeam));
      AttackChain attackChainASaved = attackChainRepository.save(attackChainA);

      AttackChainTeamUser linkA = new AttackChainTeamUser();
      linkA.setAttackChain(attackChainASaved);
      linkA.setTeam(sharedTeam);
      linkA.setUser(userTom);
      attackChainTeamUserRepository.save(linkA);

      // attackChainB
      AttackChain attackChainB = getAttackChain();
      attackChainB.setTeams(List.of(sharedTeam));
      AttackChain attackChainBSaved = attackChainRepository.save(attackChainB);

      AttackChainTeamUser linkB = new AttackChainTeamUser();
      linkB.setAttackChain(attackChainBSaved);
      linkB.setTeam(sharedTeam);
      linkB.setUser(userTom);
      attackChainTeamUserRepository.save(linkB);

      // -- ACT : We remove sharedTeam from attackChainA --
      AttackChainUpdateTeamsInput input = new AttackChainUpdateTeamsInput();
      input.setTeamIds(List.of());

      mvc.perform(
              put(SCENARIO_URI + "/" + attackChainASaved.getId() + "/teams/replace")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      List<AttackChainTeamUser> linksA =
          fromIterable(attackChainTeamUserRepository.findAll()).stream()
              .filter(l -> l.getAttackChain().getId().equals(attackChainASaved.getId()))
              .toList();
      assertTrue(
          linksA.isEmpty(),
          "The scenario-team-user links of scenarioA must be deleted when the team is removed from scenarioA");

      List<AttackChainTeamUser> linksB =
          fromIterable(attackChainTeamUserRepository.findAll()).stream()
              .filter(l -> l.getAttackChain().getId().equals(attackChainBSaved.getId()))
              .toList();
      assertEquals(
          1,
          linksB.size(),
          "The scenario-team-user links of scenarioB must not be deleted when the team is removed from scenarioA");

      AttackChain attackChainAReloaded =
          attackChainRepository.findById(attackChainASaved.getId()).orElseThrow();
      assertTrue(attackChainAReloaded.getTeams().isEmpty());

      AttackChain attackChainBReloaded =
          attackChainRepository.findById(attackChainBSaved.getId()).orElseThrow();
      assertEquals(1, attackChainBReloaded.getTeams().size());
    }

    @Test
    @DisplayName("Calling replaceTeams twice with same payload must not cause duplicate key error")
    void callingReplaceTeamsTwiceShouldNotCauseDuplicateKey() throws Exception {
      // -- PREPARE --
      User userTom = userRepository.save(UserFixture.getUser("Tom", "RT2", "tom-rt2sc@fake.email"));

      Team team = new Team();
      team.setName("Team-SC-RT2");
      team.setUsers(List.of(userTom));
      teamRepository.save(team);

      AttackChain attackChain = getAttackChain();
      attackChain.setTeams(List.of());
      AttackChain attackChainSaved = attackChainRepository.save(attackChain);

      AttackChainUpdateTeamsInput input = new AttackChainUpdateTeamsInput();
      input.setTeamIds(List.of(team.getId()));

      // -- ACT: two identical calls to replaceTeams with the same team list --
      mvc.perform(
              put(SCENARIO_URI + "/" + attackChainSaved.getId() + "/teams/replace")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      mvc.perform(
              put(SCENARIO_URI + "/" + attackChainSaved.getId() + "/teams/replace")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      List<AttackChainTeamUser> links =
          fromIterable(attackChainTeamUserRepository.findAll()).stream()
              .filter(l -> l.getAttackChain().getId().equals(attackChainSaved.getId()))
              .toList();
      assertEquals(
          1, links.size(), "Only one scenario-team-user link should exist for the team and user");
      assertEquals(team.getId(), links.getFirst().getTeam().getId());
      assertEquals(userTom.getId(), links.getFirst().getUser().getId());
    }

    @Test
    @DisplayName("Replacing teams should update the scenario team list in database")
    void replacingTeamsShouldPersistNewTeamListInDatabase() throws Exception {
      // -- PREPARE --
      Team teamToRemove = new Team();
      teamToRemove.setName("TeamToRemove-SC-RT3");
      teamRepository.save(teamToRemove);

      Team teamToKeep = new Team();
      teamToKeep.setName("TeamToKeep-SC-RT3");
      teamRepository.save(teamToKeep);

      Team teamToAdd = new Team();
      teamToAdd.setName("TeamToAdd-SC-RT3");
      teamRepository.save(teamToAdd);

      AttackChain attackChain = getAttackChain();
      attackChain.setTeams(List.of(teamToRemove, teamToKeep));
      AttackChain attackChainSaved = attackChainRepository.save(attackChain);

      AttackChainUpdateTeamsInput input = new AttackChainUpdateTeamsInput();
      input.setTeamIds(List.of(teamToKeep.getId(), teamToAdd.getId()));

      // -- ACT --
      mvc.perform(
              put(SCENARIO_URI + "/" + attackChainSaved.getId() + "/teams/replace")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      AttackChain reloaded = attackChainRepository.findById(attackChainSaved.getId()).orElseThrow();
      List<String> teamIds = reloaded.getTeams().stream().map(Team::getId).toList();

      assertEquals(2, teamIds.size());
      assertTrue(teamIds.contains(teamToKeep.getId()), "teamToKeep should still be present");
      assertTrue(teamIds.contains(teamToAdd.getId()), "teamToAdd should be present");
      assertFalse(
          teamIds.contains(teamToRemove.getId()),
          "teamToRemove should have been removed from the scenario");
    }
  }
}
