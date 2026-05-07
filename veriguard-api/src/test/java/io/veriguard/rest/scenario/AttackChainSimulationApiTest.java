package io.veriguard.rest.scenario;

import static io.veriguard.rest.scenario.AttackChainApi.SCENARIO_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Filters;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.Tag;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.AttackChainRepository;
import io.veriguard.database.repository.TagRepository;
import io.veriguard.utils.fixtures.AttackChainRunFixture;
import io.veriguard.utils.fixtures.PaginationFixture;
import io.veriguard.utils.fixtures.AttackChainFixture;
import io.veriguard.utils.fixtures.TagFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.veriguard.utils.pagination.SortField;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
public class AttackChainSimulationApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private AttackChainRepository attackChainRepository;
  @Autowired private AttackChainRunRepository attackChainRunRepository;
  @Autowired private TagRepository tagRepository;

  private static AttackChain attackChain;
  private static AttackChainRun attackChainRun1FromAttackChain;
  private static AttackChainRun attackChainRun2FromAttackChain;
  private static AttackChainRun attackChainRun3NotFromAttackChain;

  @BeforeEach
  void beforeEach() {
    // Create attackChains
    AttackChain defaultAttackChain = AttackChainFixture.createDefaultCrisisAttackChain();
    attackChain = this.attackChainRepository.save(defaultAttackChain);

    // Create attackChainRuns linked to the attackChain
    this.attackChainRunRepository.deleteAll();
    AttackChainRun attackChainRun1 = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
    attackChainRun1.setAttackChain(attackChain);
    attackChainRun1FromAttackChain = this.attackChainRunRepository.save(attackChainRun1);
    AttackChainRun attackChainRun2 = AttackChainRunFixture.createDefaultIncidentResponseAttackChainRun();
    attackChainRun2.setAttackChain(attackChain);
    attackChainRun2FromAttackChain = this.attackChainRunRepository.save(attackChainRun2);

    // Create an attackChainRun not linked to the attackChain
    AttackChainRun attackChainRun3 = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
    attackChainRun3NotFromAttackChain = this.attackChainRunRepository.save(attackChainRun3);
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Retrieving exercises of a scenario")
  class RetrievingAttackChainRunsOfAttackChain {

    @Nested
    @DisplayName("Filtering page of exercises")
    class FilteringPageOfAttackChainRuns {
      @Test
      @DisplayName("Retrieve all exercises of a scenario")
      void given_attackChainId_should_return_all_attackChainRuns_of_attackChain() throws Exception {
        SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();
        List<String> expectedIds =
            List.of(attackChainRun1FromAttackChain.getId(), attackChainRun2FromAttackChain.getId());

        String response =
            mvc.perform(
                    post(SCENARIO_URI + "/" + attackChain.getId() + "/exercises/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(searchPaginationInput))
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.numberOfElements").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThatJson(response)
            .inPath("$.content[*].exercise_id")
            .isArray()
            .containsExactlyInAnyOrderElementsOf(expectedIds);
      }

      @Test
      @DisplayName("Filtering page of exercises by tag")
      void given_attackChainId_and_tag_should_return_filtered_attackChainRuns_of_attackChain()
          throws Exception {
        Tag tagToAdd = TagFixture.getTagWithText("ScenarioSimulationApiTestTag");
        Tag tag = tagRepository.save(tagToAdd);
        Set<Tag> tags = new HashSet<>();
        tags.add(tag);
        attackChainRun2FromAttackChain.setTags(tags);
        attackChainRunRepository.save(attackChainRun2FromAttackChain);
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "exercise_tags", tag.getId(), Filters.FilterOperator.eq);
        mvc.perform(
                post(SCENARIO_URI + "/" + attackChain.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1))
            .andExpect(jsonPath("$.content[0].exercise_id").value(attackChainRun2FromAttackChain.getId()));
        attackChainRun2FromAttackChain.setTags(null);
        attackChainRunRepository.save(attackChainRun2FromAttackChain);
        mvc.perform(
                post(SCENARIO_URI + "/" + attackChain.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }

    @Nested
    @DisplayName("Sorting page of exercises")
    class SortingPageOfAttackChainRuns {
      @Test
      @DisplayName("Sorting page of exercises by updated at date")
      void given_sorting_input_by_updateDate_should_return_a_page_of_attackChainRuns_sort_by_updateDate()
          throws Exception {
        attackChainRun2FromAttackChain.setUpdatedAt(Instant.now());
        attackChainRun1FromAttackChain.setUpdatedAt(Instant.now().minusSeconds(60));
        attackChainRunRepository.saveAll(Arrays.asList(attackChainRun1FromAttackChain, attackChainRun2FromAttackChain));
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("exercise_updated_at")
                            .direction("DESC")
                            .build()))
                .build();
        mvc.perform(
                post(SCENARIO_URI + "/" + attackChain.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2))
            .andExpect(jsonPath("$.content[0].exercise_id").value(attackChainRun2FromAttackChain.getId()))
            .andExpect(jsonPath("$.content[1].exercise_id").value(attackChainRun1FromAttackChain.getId()));
      }

      @Test
      @DisplayName("Sorting page of exercises by last end date otherwise by update date")
      void
          given_sorting_input_by_lastEndDate_should_return_a_page_of_attackChainRuns_sort_by_lastEndDate()
              throws Exception {
        Instant now = Instant.now();
        // end date = yesterday
        attackChainRun2FromAttackChain.setEnd(now.minusSeconds(24 * 60 * 60));
        attackChainRun2FromAttackChain.setUpdatedAt(now.minusSeconds(24 * 60 * 60));

        attackChainRun3NotFromAttackChain.setAttackChain(attackChain);
        attackChainRun3NotFromAttackChain.setEnd(now.minusSeconds(24 * 60 * 60 * 2));
        attackChainRun3NotFromAttackChain.setUpdatedAt(now.minusSeconds(60 * 60));

        attackChainRun1FromAttackChain.setEnd(null);
        attackChainRun1FromAttackChain.setUpdatedAt(now);

        attackChainRunRepository.saveAll(
            Arrays.asList(attackChainRun1FromAttackChain, attackChainRun2FromAttackChain, attackChainRun3NotFromAttackChain));
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("exercise_end_date")
                            .direction("DESC")
                            .nullHandling(Sort.NullHandling.NULLS_LAST)
                            .build(),
                        SortField.builder()
                            .property("exercise_updated_at")
                            .direction("DESC")
                            .build()))
                .build();
        mvc.perform(
                post(SCENARIO_URI + "/" + attackChain.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(3))
            .andExpect(jsonPath("$.content[0].exercise_id").value(attackChainRun2FromAttackChain.getId()))
            .andExpect(jsonPath("$.content[1].exercise_id").value(attackChainRun3NotFromAttackChain.getId()))
            .andExpect(jsonPath("$.content[2].exercise_id").value(attackChainRun1FromAttackChain.getId()));
      }
    }

    @Nested
    @DisplayName("Searching page of exercises of a scenario")
    class SearchingPageOfAttackChainRuns {
      @Test
      @DisplayName("Retrieving first page of exercises by text search")
      void given_working_search_input_should_return_a_page_of_attackChainRuns() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("Crisis").build();
        mvc.perform(
                post(SCENARIO_URI + "/" + attackChain.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1))
            .andExpect(jsonPath("$.content[0].exercise_id").value(attackChainRun1FromAttackChain.getId()));
      }

      @Test
      @DisplayName("Not retrieving first page of exercises by text search")
      void given_notworking_search_input_should_notreturn_a_page_of_attackChainRuns() throws Exception {
        String searchText = "wrong";
        attackChainRun3NotFromAttackChain.setName(searchText);
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch(searchText).build();
        mvc.perform(
                post(SCENARIO_URI + "/" + attackChain.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }
  }
}
