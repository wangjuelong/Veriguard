package io.veriguard.rest.attack_combination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import io.veriguard.database.repository.AttackCombinationClusterRepository;
import io.veriguard.database.repository.AttackCombinationResultRepository;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.BypassDimensionRepository;
import io.veriguard.utils.mockUser.WithMockUser;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** PR D3 集成测试 —— 跑完整 run（全 miss），验证 scheduler afterCommit 触发聚类 + REST 4 端点. */
@DirtiesContext
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties = {
      "veriguard.combination.stub.hit-probability=0.0",
      "veriguard.combination.stub.miss-probability=1.0",
      "veriguard.combination.stub.timeout-probability=0.0"
    })
class AttackCombinationClusterApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private BypassDimensionRepository dimensionRepository;
  @Autowired private AttackCombinationRunRepository runRepository;
  @Autowired private AttackCombinationResultRepository resultRepository;
  @Autowired private AttackCombinationClusterRepository clusterRepository;
  @Autowired private ObjectMapper objectMapper;

  private String dimId1;
  private String dimId2;

  @BeforeEach
  void seedDimensions() {
    clusterRepository.deleteAll();
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
  void completed_run_auto_populates_clusters_and_rest_lists_them() throws Exception {
    String runId = runCompletedSmallRun();

    // After completed, scheduler afterCommit should have triggered cluster analysis.
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> clusterRepository.countByRunId(runId) > 0);

    // 12 result = 2 base × 2 dim × 3 asset, all miss → 3 asset cluster + 3 device cluster = 6
    assertThat(clusterRepository.countByRunId(runId)).isEqualTo(6L);

    // GET clusters?dim=asset → 3 rows
    mockMvc
        .perform(get(AttackCombinationApi.RUNS_URI + "/" + runId + "/clusters").param("dim", "asset"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(3))
        .andExpect(jsonPath("$.content[0].attack_combination_cluster_miss_count").exists())
        .andExpect(jsonPath("$.content[0].attack_combination_cluster_payload_samples").isArray());
  }

  @Test
  void cluster_detail_and_member_drill_down_returns_expected_fields() throws Exception {
    String runId = runCompletedSmallRun();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> clusterRepository.countByRunId(runId) > 0);

    String listResponse =
        mockMvc
            .perform(
                get(AttackCombinationApi.RUNS_URI + "/" + runId + "/clusters").param("dim", "asset"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode tree = objectMapper.readTree(listResponse);
    String clusterId = tree.get("content").get(0).get("attack_combination_cluster_id").asText();

    // Detail
    mockMvc
        .perform(
            get(AttackCombinationApi.RUNS_URI + "/" + runId + "/clusters/" + clusterId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attack_combination_cluster_dim").value("asset"))
        .andExpect(jsonPath("$.attack_combination_cluster_top_base_attack_types").isArray())
        .andExpect(jsonPath("$.attack_combination_cluster_top_bypass_dimensions").isArray());

    // Member drill-down with hit_state=miss
    mockMvc
        .perform(
            get(
                    AttackCombinationApi.RUNS_URI
                        + "/"
                        + runId
                        + "/clusters/"
                        + clusterId
                        + "/results")
                .param("hit_state", "miss"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.total_elements").value(4)); // 2 base × 2 dim per asset
  }

  @Test
  void recompute_on_completed_run_rebuilds_clusters() throws Exception {
    String runId = runCompletedSmallRun();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> clusterRepository.countByRunId(runId) > 0);
    long firstCount = clusterRepository.countByRunId(runId);
    assertThat(firstCount).isEqualTo(6L);

    // Manually wipe to detect recompute writes them back.
    clusterRepository.deleteByRunId(runId);
    assertThat(clusterRepository.countByRunId(runId)).isZero();

    mockMvc
        .perform(
            post(AttackCombinationApi.RUNS_URI + "/" + runId + "/cluster/recompute")
                .with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.cluster_recompute_status").value("accepted"));

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> clusterRepository.countByRunId(runId) == firstCount);
  }

  @Test
  void recompute_on_non_completed_run_returns_409() throws Exception {
    // Create run with auto_start=false → stays pending.
    String body =
        """
        {
          "name": "pending-run",
          "base_attack_types": ["sql_injection"],
          "bypass_dimension_ids": ["%s"],
          "asset_ids": ["asset-X"]
        }
        """
            .formatted(dimId1);

    String resp =
        mockMvc
            .perform(
                post(AttackCombinationApi.RUNS_URI)
                    .with(csrf())
                    .param("auto_start", "false")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = objectMapper.readTree(resp).get("attack_combination_run_id").asText();

    mockMvc
        .perform(
            post(AttackCombinationApi.RUNS_URI + "/" + runId + "/cluster/recompute").with(csrf()))
        .andExpect(status().isConflict());
  }

  // ============================================================
  // Helpers
  // ============================================================

  /** Create + auto-start a 2×2×3 = 12-result run; wait completed; return runId. */
  private String runCompletedSmallRun() throws Exception {
    String body =
        """
        {
          "name": "cluster-it",
          "base_attack_types": ["sql_injection", "xss"],
          "bypass_dimension_ids": ["%s", "%s"],
          "asset_ids": ["asset-A", "asset-B", "asset-C"],
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
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = objectMapper.readTree(response).get("attack_combination_run_id").asText();

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                runRepository
                    .findById(runId)
                    .map(r -> r.getStatus() == AttackCombinationRunStatus.completed)
                    .orElse(false));
    assertThat(resultRepository.countByRunId(runId)).isEqualTo(12L);
    return runId;
  }
}
