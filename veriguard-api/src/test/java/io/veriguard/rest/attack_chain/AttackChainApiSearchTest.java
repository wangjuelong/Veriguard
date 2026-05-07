package io.veriguard.rest.attack_chain;

import static io.veriguard.config.SessionHelper.currentUser;
import static io.veriguard.database.model.Filters.FilterOperator.contains;
import static io.veriguard.database.model.AttackChain.SEVERITY.critical;
import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;
import static io.veriguard.utils.JsonTestUtils.asJsonString;
import static java.lang.String.valueOf;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.GrantRepository;
import io.veriguard.database.repository.GroupRepository;
import io.veriguard.database.repository.AttackChainRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.rest.attack_chain.form.GetAttackChainsInput;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.mockUser.WithMockUser;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.veriguard.utils.pagination.SortField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
public class AttackChainApiSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private AttackChainRepository attackChainRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GrantRepository grantRepository;
  @Autowired private UserRepository userRepository;

  private static final List<String> SCENARIO_IDS = new ArrayList<>();

  @BeforeAll
  void beforeAll() {
    AttackChain attackChain1 = AttackChainFixture.createDefaultCrisisAttackChain();
    AttackChain attackChain1Saved = this.attackChainRepository.save(attackChain1);
    SCENARIO_IDS.add(attackChain1Saved.getId());

    AttackChain attackChain2 = AttackChainFixture.createDefaultIncidentResponseAttackChain();
    AttackChain attackChain2Saved = this.attackChainRepository.save(attackChain2);
    SCENARIO_IDS.add(attackChain2Saved.getId());
  }

  @AfterAll
  void afterAll() {
    this.attackChainRepository.deleteAllById(SCENARIO_IDS);
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Retrieving scenarios")
  class RetrievingAttackChains {
    // -- PREPARE --

    @Nested
    @DisplayName("Searching page of scenarios")
    class SearchingPageOfAttackChains {

      @Test
      @DisplayName("Retrieving first page of scenarios by textsearch")
      void given_working_search_input_should_return_a_page_of_attackChains() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("Crisis").build();

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Not retrieving first page of scenario by textsearch")
      void given_not_working_search_input_should_return_a_page_of_attackChains() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("wrong").build();

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }

    @Nested
    @DisplayName("Sorting page of scenarios")
    class SortingPageOfAttackChains {

      @Test
      @DisplayName("Sorting page of scenarios by name")
      void given_sorting_input_by_name_should_return_a_page_of_attackChains_sort_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(List.of(SortField.builder().property("scenario_name").build()))
                .build();

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].scenario_name").value("Crisis scenario"))
            .andExpect(jsonPath("$.content.[1].scenario_name").value("Incident response scenario"));
      }

      @Test
      @DisplayName("Sorting page of scenarios by category")
      void given_sorting_input_by_category_should_return_a_page_of_attackChains_sort_by_category()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("scenario_category")
                            .direction("desc")
                            .build()))
                .build();

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].scenario_name").value("Incident response scenario"))
            .andExpect(jsonPath("$.content.[1].scenario_name").value("Crisis scenario"));
      }
    }

    @Nested
    @DisplayName("Filtering page of scenarios")
    class FilteringPageOfAttackChains {

      @Test
      @DisplayName("Filtering page of scenarios by name")
      void given_filter_input_by_name_should_return_a_page_of_attackChains_filter_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator("scenario_name", "Crisis", contains);

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Filtering page of scenarios by category")
      void given_filter_input_by_category_should_return_a_page_of_attackChains_filter_by_category()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "scenario_category", AttackChain.MAIN_FOCUS_INCIDENT_RESPONSE, contains);

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Filtering page of scenarios by severity")
      void given_filter_input_by_severity_should_return_a_page_of_attackChains_filter_by_severity()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "scenario_severity", valueOf(critical), contains);

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Search scenarios by ids as admin")
      void given_list_of_ids_select_attackChains() throws Exception {
        GetAttackChainsInput getAttackChainsInput = new GetAttackChainsInput();
        getAttackChainsInput.setAttackChainIds(SCENARIO_IDS);

        mvc.perform(
                post(SCENARIO_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getAttackChainsInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
      }

      @Test
      @DisplayName("Search scenarios by ids as granted user")
      @WithMockUser(isAdmin = false, withCapabilities = Capability.ACCESS_ASSESSMENT)
      void given_list_of_ids_select_attackChains_with_capabilities() throws Exception {
        GetAttackChainsInput getAttackChainsInput = new GetAttackChainsInput();
        getAttackChainsInput.setAttackChainIds(SCENARIO_IDS);

        mvc.perform(
                post(SCENARIO_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getAttackChainsInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
      }

      @Test
      @DisplayName("Search scenarios by ids as user not granted")
      @WithMockUser(isAdmin = false)
      void given_list_of_ids_select_attackChains_without_capabilities_no_grants() throws Exception {
        GetAttackChainsInput getAttackChainsInput = new GetAttackChainsInput();
        getAttackChainsInput.setAttackChainIds(SCENARIO_IDS);

        mvc.perform(
                post(SCENARIO_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getAttackChainsInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
      }

      @Test
      @DisplayName("Search scenarios by ids as user granted on some scenario")
      @WithMockUser(isAdmin = false)
      void given_list_of_ids_select_attackChains_without_capabilities_with_grants() throws Exception {
        AttackChain attackChainGranted = AttackChainFixture.createDefaultIncidentResponseAttackChain();
        User user = userRepository.findById(currentUser().getId()).get();
        Group group = new Group();
        group.setName("test");
        group = groupRepository.save(group);
        attackChainGranted.getGrants().addAll(group.getGrants());
        attackChainGranted = attackChainRepository.save(attackChainGranted);

        Grant grantObserver = new Grant();
        grantObserver.setResourceId(attackChainGranted.getId());
        grantObserver.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
        grantObserver.setGroup(group);
        grantObserver.setName(Grant.GRANT_TYPE.OBSERVER);
        Grant grantPlanner = new Grant();
        grantPlanner.setResourceId(attackChainGranted.getId());
        grantPlanner.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
        grantPlanner.setGroup(group);
        grantPlanner.setName(Grant.GRANT_TYPE.PLANNER);
        grantRepository.saveAll(List.of(grantObserver, grantPlanner));
        group.setGrants(List.of(grantObserver, grantPlanner));
        group.setUsers(List.of(user));
        groupRepository.save(group);
        SCENARIO_IDS.add(attackChainGranted.getId());

        GetAttackChainsInput getAttackChainsInput = new GetAttackChainsInput();
        List<String> attackChainIds = new ArrayList<>();
        attackChainIds.add(attackChainGranted.getId());
        attackChainIds.addAll(SCENARIO_IDS);
        getAttackChainsInput.setAttackChainIds(attackChainIds);

        mvc.perform(
                post(SCENARIO_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getAttackChainsInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1));

        attackChainRepository.delete(attackChainGranted);
      }
    }
  }
}
