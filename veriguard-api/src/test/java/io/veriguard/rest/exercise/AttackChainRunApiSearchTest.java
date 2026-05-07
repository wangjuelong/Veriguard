package io.veriguard.rest.exercise;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.model.AttackChainRunStatus.SCHEDULED;
import static io.veriguard.database.model.Filters.FilterOperator.contains;
import static io.veriguard.rest.exercise.AttackChainRunApi.EXERCISE_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static java.lang.String.valueOf;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.GrantRepository;
import io.veriguard.database.repository.GroupRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.exercise.form.GetAttackChainRunsInput;
import io.veriguard.utils.fixtures.AttackChainRunFixture;
import io.veriguard.utils.fixtures.PaginationFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.veriguard.utils.pagination.SortField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
public class AttackChainRunApiSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private AttackChainRunRepository attackChainRunRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GrantRepository grantRepository;
  @Autowired private UserRepository userRepository;

  private static final List<String> EXERCISE_IDS = new ArrayList<>();

  @BeforeEach
  void beforeAll() {
    AttackChainRun attackChainRun1 = AttackChainRunFixture.createDefaultCrisisAttackChainRun();
    AttackChainRun attackChainRun1Saved = this.attackChainRunRepository.save(attackChainRun1);
    EXERCISE_IDS.add(attackChainRun1Saved.getId());

    AttackChainRun attackChainRun2 = AttackChainRunFixture.createDefaultIncidentResponseAttackChainRun();
    AttackChainRun attackChainRun2Saved = this.attackChainRunRepository.save(attackChainRun2);
    EXERCISE_IDS.add(attackChainRun2Saved.getId());
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Retrieving exercises")
  class RetrievingAttackChainRuns {
    // -- PREPARE --

    @Nested
    @DisplayName("Searching page of exercises")
    class SearchingPageOfAttackChainRuns {

      @Test
      @DisplayName("Retrieving first page of exercises by text search")
      void given_working_search_input_should_return_a_page_of_attackChainRuns() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("Crisis").build();

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Not retrieving first page of exercises by text search")
      void given_not_working_search_input_should_return_a_page_of_attackChainRuns() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("wrong").build();

        mvc.perform(
                post(EXERCISE_URI + "/search")
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
      @DisplayName("Sorting page of exercises by name")
      void given_sorting_input_by_name_should_return_a_page_of_attackChainRuns_sort_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(List.of(SortField.builder().property("exercise_name").build()))
                .build();

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].exercise_name").value("Crisis exercise"))
            .andExpect(jsonPath("$.content.[1].exercise_name").value("Incident response exercise"));
      }

      @Test
      @DisplayName("Sorting page of exercises by start date")
      void given_sorting_input_by_start_date_should_return_a_page_of_attackChainRuns_sort_by_start_date()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("exercise_start_date")
                            .direction("desc")
                            .build()))
                .build();

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[1].exercise_name").value("Incident response exercise"))
            .andExpect(jsonPath("$.content.[0].exercise_name").value("Crisis exercise"));
      }
    }

    @Nested
    @DisplayName("Filtering page of exercises")
    class FilteringPageOfAttackChainRuns {

      @Test
      @DisplayName("Filtering page of exercises by name")
      void given_filter_input_by_name_should_return_a_page_of_attackChainRuns_filter_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator("exercise_name", "Crisis", contains);

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Filtering page of exercises by status")
      void given_filter_input_by_status_should_return_a_page_of_attackChainRuns_filter_by_status()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "exercise_status", valueOf(SCHEDULED), contains);

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2));
      }

      @Test
      @DisplayName("Search exercises by ids as admin")
      void given_list_of_ids_select_attackChainRuns() throws Exception {
        GetAttackChainRunsInput getAttackChainRunsInput = new GetAttackChainRunsInput();
        getAttackChainRunsInput.setAttackChainRunIds(EXERCISE_IDS);

        mvc.perform(
                post(EXERCISE_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getAttackChainRunsInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
      }

      @Test
      @DisplayName("Search exercises by ids as user granted")
      @WithMockUser(isAdmin = false, withCapabilities = Capability.ACCESS_ASSESSMENT)
      void given_list_of_ids_select_attackChainRuns_with_capabilities() throws Exception {
        GetAttackChainRunsInput getAttackChainRunsInput = new GetAttackChainRunsInput();
        getAttackChainRunsInput.setAttackChainRunIds(EXERCISE_IDS);

        mvc.perform(
                post(EXERCISE_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getAttackChainRunsInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
      }

      @Test
      @DisplayName("Search exercises by ids as user non granted")
      @WithMockUser(isAdmin = false)
      void given_list_of_ids_select_attackChainRuns_without_capabilities_no_grants() throws Exception {
        GetAttackChainRunsInput getAttackChainRunsInput = new GetAttackChainRunsInput();
        getAttackChainRunsInput.setAttackChainRunIds(EXERCISE_IDS);

        mvc.perform(
                post(EXERCISE_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getAttackChainRunsInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
      }

      @Test
      @DisplayName("Search exercises by ids as user granted on some exercises")
      @WithMockUser(isAdmin = false)
      void given_list_of_ids_select_attackChainRuns_without_capabilities_with_grants() throws Exception {
        AttackChainRun attackChainRunGranted = AttackChainRunFixture.createDefaultAttackChainRun();
        User user = userRepository.findById(currentUser().getId()).get();
        Group group = new Group();
        group.setName("test");
        group = groupRepository.save(group);
        attackChainRunGranted.getGrants().addAll(group.getGrants());
        attackChainRunGranted = attackChainRunRepository.save(attackChainRunGranted);

        Grant grantObserver = new Grant();
        grantObserver.setResourceId(attackChainRunGranted.getId());
        grantObserver.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
        grantObserver.setGroup(group);
        grantObserver.setName(Grant.GRANT_TYPE.OBSERVER);
        Grant grantPlanner = new Grant();
        grantPlanner.setResourceId(attackChainRunGranted.getId());
        grantPlanner.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
        grantPlanner.setGroup(group);
        grantPlanner.setName(Grant.GRANT_TYPE.PLANNER);
        grantRepository.saveAll(List.of(grantObserver, grantPlanner));
        group.setGrants(new ArrayList<>(List.of(grantObserver, grantPlanner)));
        group.setUsers(new ArrayList<>(List.of(user)));
        groupRepository.save(group);
        EXERCISE_IDS.add(attackChainRunGranted.getId());

        GetAttackChainRunsInput getAttackChainRunsInput = new GetAttackChainRunsInput();
        List<String> attackChainRunIds = new ArrayList<>();
        attackChainRunIds.add(attackChainRunGranted.getId());
        attackChainRunIds.addAll(EXERCISE_IDS);
        getAttackChainRunsInput.setAttackChainRunIds(attackChainRunIds);

        mvc.perform(
                post(EXERCISE_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getAttackChainRunsInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1));

        attackChainRunRepository.delete(attackChainRunGranted);
      }
    }
  }
}
