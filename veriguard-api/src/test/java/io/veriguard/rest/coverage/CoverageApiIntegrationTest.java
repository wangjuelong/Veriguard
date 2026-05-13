package io.veriguard.rest.coverage;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AssetGroup;
import io.veriguard.database.repository.AssetGroupRepository;
import io.veriguard.utils.fixtures.AssetGroupFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class CoverageApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AssetGroupRepository assetGroupRepository;

  private String assetGroupId;

  @BeforeEach
  void seedAssetGroup() {
    AssetGroup g = AssetGroupFixture.createDefaultAssetGroup("c3-test-group");
    assetGroupId = assetGroupRepository.save(g).getId();
  }

  @Test
  void create_and_get_baseline_round_trip() throws Exception {
    String body =
        String.format(
            """
            {
              "name": "Boundary 基线-1",
              "coverage_type": "boundary",
              "case_ids": ["case-A", "case-B"],
              "asset_group_id": "%s",
              "description": "test",
              "soc_query_delay_seconds": 5
            }
            """,
            assetGroupId);

    String location =
        mockMvc
            .perform(
                post(CoverageApi.BASELINES_URI)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.coverage_baseline_name").value("Boundary 基线-1"))
            .andExpect(jsonPath("$.coverage_baseline_coverage_type").value("boundary"))
            .andExpect(jsonPath("$.coverage_baseline_soc_query_delay_seconds").value(5))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Quick smoke check on list filter
    mockMvc
        .perform(get(CoverageApi.BASELINES_URI).param("coverage_type", "boundary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].coverage_baseline_coverage_type").value("boundary"));
  }

  @Test
  void create_baseline_rejects_unknown_asset_group() throws Exception {
    String body =
        """
        {
          "name": "broken",
          "coverage_type": "boundary",
          "case_ids": [],
          "asset_group_id": "non-existent-id"
        }
        """;
    mockMvc
        .perform(
            post(CoverageApi.BASELINES_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_baselines_paged_default() throws Exception {
    mockMvc
        .perform(get(CoverageApi.BASELINES_URI))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void create_policy_and_list_filter_by_device_type() throws Exception {
    String body =
        """
        {
          "name": "WAF-rule-001",
          "device_type": "waf",
          "device_id": "device-A",
          "external_rule_id": "rule-1",
          "description": "test"
        }
        """;
    mockMvc
        .perform(
            post(CoverageApi.POLICIES_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.policy_name").value("WAF-rule-001"))
        .andExpect(jsonPath("$.policy_device_type").value("waf"));

    mockMvc
        .perform(get(CoverageApi.POLICIES_URI).param("device_type", "waf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1));
    mockMvc
        .perform(get(CoverageApi.POLICIES_URI).param("device_type", "ips"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
  }

  @Test
  void delete_baseline_returns_no_content() throws Exception {
    String body =
        String.format(
            """
            {
              "name": "to-delete",
              "coverage_type": "traffic",
              "case_ids": [],
              "asset_group_id": "%s"
            }
            """,
            assetGroupId);
    String created =
        mockMvc
            .perform(
                post(CoverageApi.BASELINES_URI)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = created.replaceAll(".*\"coverage_baseline_id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    mockMvc
        .perform(delete(CoverageApi.BASELINES_URI + "/" + id).with(csrf()))
        .andExpect(status().isNoContent());
  }
}
