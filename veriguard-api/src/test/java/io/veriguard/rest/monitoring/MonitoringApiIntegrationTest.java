package io.veriguard.rest.monitoring;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AssetGroup;
import io.veriguard.database.model.coverage.CoverageBaseline;
import io.veriguard.database.model.coverage.CoverageType;
import io.veriguard.database.repository.AssetGroupRepository;
import io.veriguard.database.repository.CoverageBaselineRepository;
import io.veriguard.utils.fixtures.AssetGroupFixture;
import io.veriguard.utils.mockUser.WithMockUser;
import java.util.UUID;
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
class MonitoringApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private CoverageBaselineRepository baselineRepository;

  private String baselineId;

  @BeforeEach
  void seedBaseline() {
    AssetGroup g = AssetGroupFixture.createDefaultAssetGroup("c4-test-group");
    String assetGroupId = assetGroupRepository.save(g).getId();

    CoverageBaseline b = new CoverageBaseline();
    b.setId(UUID.randomUUID().toString());
    b.setName("c4-baseline");
    b.setCoverageType(CoverageType.boundary);
    b.setAssetGroupId(assetGroupId);
    baselineId = baselineRepository.save(b).getId();
  }

  @Test
  void create_job_round_trip_and_list() throws Exception {
    String body =
        String.format(
            """
            {
              "name": "hourly-monitor",
              "baseline_id": "%s",
              "cron_expression": "0 0 * * * ?",
              "enabled": true,
              "description": "test hourly"
            }
            """,
            baselineId);

    mockMvc
        .perform(
            post(MonitoringApi.JOBS_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.monitoring_job_name").value("hourly-monitor"))
        .andExpect(jsonPath("$.monitoring_job_baseline_id").value(baselineId))
        .andExpect(jsonPath("$.monitoring_job_cron_expression").value("0 0 * * * ?"))
        .andExpect(jsonPath("$.monitoring_job_enabled").value(true))
        .andExpect(jsonPath("$.monitoring_job_next_fire_at").exists());

    mockMvc
        .perform(get(MonitoringApi.JOBS_URI))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1));

    mockMvc
        .perform(get(MonitoringApi.JOBS_URI).param("enabled", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1));

    mockMvc
        .perform(get(MonitoringApi.JOBS_URI).param("enabled", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
  }

  @Test
  void create_job_rejects_sub_hour_cron() throws Exception {
    String body =
        String.format(
            """
            {
              "name": "too-fast",
              "baseline_id": "%s",
              "cron_expression": "0 0/5 * * * ?"
            }
            """,
            baselineId);

    mockMvc
        .perform(
            post(MonitoringApi.JOBS_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_job_rejects_unknown_baseline() throws Exception {
    String body =
        """
        {
          "name": "missing-baseline",
          "baseline_id": "non-existent",
          "cron_expression": "0 0 * * * ?"
        }
        """;
    mockMvc
        .perform(
            post(MonitoringApi.JOBS_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void enable_disable_round_trip() throws Exception {
    String jobId = createJob("toggle-job", baselineId, "0 0 * * * ?", true);

    mockMvc
        .perform(post(MonitoringApi.JOBS_URI + "/" + jobId + "/disable").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monitoring_job_enabled").value(false));

    mockMvc
        .perform(post(MonitoringApi.JOBS_URI + "/" + jobId + "/enable").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monitoring_job_enabled").value(true));
  }

  @Test
  void delete_job_returns_no_content() throws Exception {
    String jobId = createJob("to-delete", baselineId, "0 0 * * * ?", true);

    mockMvc
        .perform(delete(MonitoringApi.JOBS_URI + "/" + jobId).with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(MonitoringApi.JOBS_URI + "/" + jobId))
        .andExpect(status().isNotFound());
  }

  @Test
  void trend_endpoint_requires_from_and_to() throws Exception {
    String jobId = createJob("trend-job", baselineId, "0 0 * * * ?", true);
    // Missing 'from' → Spring 报 400（@RequestParam required=true 默认）
    mockMvc
        .perform(get(MonitoringApi.JOBS_URI + "/" + jobId + "/trend"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void history_endpoint_returns_empty_for_new_job() throws Exception {
    String jobId = createJob("history-job", baselineId, "0 0 * * * ?", true);
    mockMvc
        .perform(get(MonitoringApi.JOBS_URI + "/" + jobId + "/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
  }

  // Helper: 创建 job 并返回 id
  private String createJob(String name, String baselineId, String cron, boolean enabled)
      throws Exception {
    String body =
        String.format(
            """
            {
              "name": "%s",
              "baseline_id": "%s",
              "cron_expression": "%s",
              "enabled": %s
            }
            """,
            name, baselineId, cron, enabled);
    String json =
        mockMvc
            .perform(
                post(MonitoringApi.JOBS_URI)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.replaceAll(".*\"monitoring_job_id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }
}
