package io.veriguard.rest.attack_combination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import io.veriguard.database.repository.AttackCombinationResultRepository;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.BypassDimensionRepository;
import io.veriguard.utils.mockUser.WithMockUser;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@DirtiesContext
@WithMockUser(isAdmin = true)
class AttackCombinationRunApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private BypassDimensionRepository dimensionRepository;
  @Autowired private AttackCombinationRunRepository runRepository;
  @Autowired private AttackCombinationResultRepository resultRepository;
  @Autowired private ObjectMapper objectMapper;

  private String dimId1;
  private String dimId2;

  @BeforeEach
  void seedDimensions() {
    resultRepository.deleteAll();
    runRepository.deleteAll();
    dimensionRepository.deleteAll();
    dimId1 = saveDim("encoding.base64.standard", BypassDimensionCategory.encoding, "identity", Map.of());
    dimId2 = saveDim("noise.space.single", BypassDimensionCategory.noise, "identity", Map.of());
  }

  private String saveDim(
      String name,
      BypassDimensionCategory category,
      String transformType,
      Map<String, Object> config) {
    BypassDimension d = new BypassDimension();
    d.setName(name);
    d.setCategory(category);
    d.setTransformType(transformType);
    d.setTransformConfig(new HashMap<>(config));
    return dimensionRepository.save(d).getId();
  }

  @Test
  void create_run_runs_to_completion_and_results_persisted() throws Exception {
    String body =
        """
        {
          "name": "test-run",
          "base_attack_types": ["sql_injection", "xss"],
          "bypass_dimension_ids": ["%s", "%s"],
          "asset_ids": ["asset-A", "asset-B"],
          "rate_limit_per_second": 100,
          "concurrency": 4,
          "max_retries": 0,
          "timeout_hours": 1
        }
        """
            .formatted(dimId1, dimId2);

    String response =
        mockMvc
            .perform(
                post(AttackCombinationApi.RUNS_URI)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.attack_combination_run_id").exists())
            .andExpect(jsonPath("$.attack_combination_run_total_combinations").value(4))
            .andExpect(jsonPath("$.attack_combination_run_total_results").value(8))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String runId =
        objectMapper.readTree(response).get("attack_combination_run_id").asText();

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                runRepository
                    .findById(runId)
                    .map(r -> r.getStatus().isTerminal())
                    .orElse(false));

    assertThat(runRepository.findById(runId).orElseThrow().getStatus())
        .isEqualTo(AttackCombinationRunStatus.completed);
    assertThat(resultRepository.countByRunId(runId)).isEqualTo(8);

    // Verify GET detail exposes progress + hit_state_counts
    mockMvc
        .perform(get(AttackCombinationApi.RUNS_URI + "/" + runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attack_combination_run_status").value("completed"))
        .andExpect(jsonPath("$.attack_combination_run_progress_percent").value(1.0))
        .andExpect(jsonPath("$.attack_combination_run_hit_state_counts").exists())
        .andExpect(jsonPath("$.attack_combination_run_completed_count").value(8));
  }

  @Test
  void create_run_with_too_many_combos_returns_400() throws Exception {
    // 21 known base types × 2 dims × 100 assets > 300_000? No — 4200. We need to overshoot.
    // 21 × 2 × 100 = 4200; we need TOTAL_RESULTS > 300_000.
    // Construct request with too-large asset_ids to hit MAX_ASSETS_PER_RUN guard first.
    StringBuilder assetIds = new StringBuilder("[");
    for (int i = 0; i < 101; i++) {
      if (i > 0) assetIds.append(",");
      assetIds.append("\"asset-").append(i).append("\"");
    }
    assetIds.append("]");

    String body =
        """
        {
          "name": "oversized",
          "base_attack_types": ["sql_injection"],
          "bypass_dimension_ids": ["%s"],
          "asset_ids": %s
        }
        """
            .formatted(dimId1, assetIds);

    mockMvc
        .perform(
            post(AttackCombinationApi.RUNS_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_run_with_unknown_base_type_returns_400() throws Exception {
    String body =
        """
        {
          "name": "bad-type",
          "base_attack_types": ["unknown_attack_xyz"],
          "bypass_dimension_ids": ["%s"],
          "asset_ids": ["a1"]
        }
        """
            .formatted(dimId1);

    mockMvc
        .perform(
            post(AttackCombinationApi.RUNS_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void list_runs_returns_paged_results() throws Exception {
    // Create a run first
    createOneRun();

    mockMvc
        .perform(get(AttackCombinationApi.RUNS_URI))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].attack_combination_run_id").exists());
  }

  @Test
  void cancel_pending_run_returns_cancelled() throws Exception {
    // Create with auto_start=false so it stays pending
    String body =
        """
        {
          "name": "cancel-target",
          "base_attack_types": ["sql_injection"],
          "bypass_dimension_ids": ["%s"],
          "asset_ids": ["a1"]
        }
        """
            .formatted(dimId1);

    String response =
        mockMvc
            .perform(
                post(AttackCombinationApi.RUNS_URI + "?auto_start=false")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId =
        objectMapper.readTree(response).get("attack_combination_run_id").asText();

    mockMvc
        .perform(post(AttackCombinationApi.RUNS_URI + "/" + runId + "/cancel").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attack_combination_run_status").value("cancelled"));
  }

  @Test
  void pause_unstarted_run_returns_409() throws Exception {
    String body =
        """
        {
          "name": "no-pause",
          "base_attack_types": ["sql_injection"],
          "bypass_dimension_ids": ["%s"],
          "asset_ids": ["a1"]
        }
        """
            .formatted(dimId1);

    String response =
        mockMvc
            .perform(
                post(AttackCombinationApi.RUNS_URI + "?auto_start=false")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId =
        objectMapper.readTree(response).get("attack_combination_run_id").asText();

    mockMvc
        .perform(post(AttackCombinationApi.RUNS_URI + "/" + runId + "/pause").with(csrf()))
        .andExpect(status().isConflict());
  }

  private String createOneRun() throws Exception {
    String body =
        """
        {
          "name": "list-target",
          "base_attack_types": ["sql_injection"],
          "bypass_dimension_ids": ["%s"],
          "asset_ids": ["a1"]
        }
        """
            .formatted(dimId1);

    String response =
        mockMvc
            .perform(
                post(AttackCombinationApi.RUNS_URI)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = objectMapper.readTree(response).get("attack_combination_run_id").asText();
    // Wait for completion to keep test deterministic
    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                runRepository
                    .findById(runId)
                    .map(r -> r.getStatus().isTerminal())
                    .orElse(false));
    return runId;
  }
}
