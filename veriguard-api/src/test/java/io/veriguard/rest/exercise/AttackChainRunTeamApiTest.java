package io.veriguard.rest.exercise;

import static io.veriguard.rest.exercise.AttackChainRunApi.EXERCISE_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Team;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.utils.fixtures.AttackChainRunFixture;
import io.veriguard.utils.fixtures.PaginationFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.List;
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
public class AttackChainRunTeamApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;

  @Autowired private AttackChainRunRepository attackChainRunRepository;
  @Autowired private TeamRepository teamRepository;

  @DisplayName("Exercise team search")
  @Nested
  @WithMockUser(isAdmin = true)
  class SearchAttackChainRunTeams {
    @Test
    @DisplayName("Returns global and exercise teams when searching teams")
    void givenContextualOnlyFalse_whenSearchingTeams_shouldReturnGlobalAndAttackChainRunTeams()
        throws Exception {
      Team team = new Team();
      String teamName = "Team test";
      team.setName(teamName);

      Team contextualTeam = new Team();
      contextualTeam.setName(teamName + " 2");
      contextualTeam.setContextual(true);
      List<Team> savedTeams = teamRepository.saveAll(List.of(team, contextualTeam));

      AttackChainRun attackChainRun = AttackChainRunFixture.createDefaultAttackChainRun();
      attackChainRun.setTeams(savedTeams.stream().filter(Team::getContextual).toList());
      AttackChainRun attackChainRunCreated = attackChainRunRepository.save(attackChainRun);

      SearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setTextSearch(teamName);

      mvc.perform(
              post(EXERCISE_URI
                      + "/"
                      + attackChainRunCreated.getId()
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
    @DisplayName("Returns only exercise teams")
    void givenContextualOnlyTrue_whenSearchingTeams_shouldReturnOnlyAttackChainRunTeams()
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
      List<Team> attackChainRunTeams = teamRepository.saveAll(List.of(team1, contextualTeam1));

      AttackChainRun attackChainRun = AttackChainRunFixture.createDefaultAttackChainRun();
      attackChainRun.setTeams(attackChainRunTeams);
      AttackChainRun attackChainRunCreated = attackChainRunRepository.save(attackChainRun);

      SearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setTextSearch(teamName);

      mvc.perform(
              post(EXERCISE_URI
                      + "/"
                      + attackChainRunCreated.getId()
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
                      attackChainRunTeams.stream().map(Team::getId).toArray(String[]::new))));
    }
  }
}
