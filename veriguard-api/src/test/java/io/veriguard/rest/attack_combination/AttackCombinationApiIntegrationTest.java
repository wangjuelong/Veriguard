package io.veriguard.rest.attack_combination;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import io.veriguard.database.repository.BypassDimensionRepository;
import io.veriguard.utils.mockUser.WithMockUser;
import java.util.HashMap;
import java.util.Map;
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
class AttackCombinationApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private BypassDimensionRepository repository;

  private String dimEncodingId;
  private String dimNoiseId;

  @BeforeEach
  void seedSampleDimensions() {
    repository.deleteAll();
    dimEncodingId = saveDim("encoding.base64.standard", BypassDimensionCategory.encoding, "base64_encode", Map.of());
    dimNoiseId =
        saveDim(
            "noise.space.single",
            BypassDimensionCategory.noise,
            "noise_surround",
            Map.of("prefix", " ", "suffix", " "));
  }

  private String saveDim(
      String name, BypassDimensionCategory category, String transformType, Map<String, Object> config) {
    BypassDimension d = new BypassDimension();
    d.setName(name);
    d.setCategory(category);
    d.setTransformType(transformType);
    d.setTransformConfig(new HashMap<>(config));
    return repository.save(d).getId();
  }

  @Test
  void list_dimensions_returns_paged_results() throws Exception {
    mockMvc
        .perform(get(AttackCombinationApi.DIMENSIONS_URI))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(2))
        .andExpect(jsonPath("$.content[0].bypass_dimension_name").exists())
        .andExpect(jsonPath("$.content[0].bypass_dimension_category").exists());
  }

  @Test
  void list_dimensions_with_category_filter_returns_subset() throws Exception {
    mockMvc
        .perform(get(AttackCombinationApi.DIMENSIONS_URI).param("category", "encoding"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].bypass_dimension_category").value("encoding"));
  }

  @Test
  void preview_returns_cartesian_size_and_samples() throws Exception {
    String body =
        """
        {
          "base_attack_types": ["sql_injection", "xss"],
          "bypass_dimension_ids": ["%s", "%s"],
          "preview_base_payload": "OR 1=1"
        }
        """
            .formatted(dimEncodingId, dimNoiseId);

    mockMvc
        .perform(
            post(AttackCombinationApi.TEMPLATE_PREVIEW_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_combinations").value(4))
        .andExpect(jsonPath("$.sample_size").value(4))
        .andExpect(jsonPath("$.samples", org.hamcrest.Matchers.hasSize(4)))
        .andExpect(jsonPath("$.samples[0].preview_payload").exists())
        .andExpect(jsonPath("$.preview_base_payload").value("OR 1=1"));
  }

  @Test
  void preview_with_empty_base_types_returns_400() throws Exception {
    // Bean validation @NotEmpty on base_attack_types triggers MethodArgumentNotValidException → 400
    String body =
        """
        {
          "base_attack_types": [],
          "bypass_dimension_ids": ["%s"],
          "preview_base_payload": "X"
        }
        """
            .formatted(dimEncodingId);

    mockMvc
        .perform(
            post(AttackCombinationApi.TEMPLATE_PREVIEW_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }
}
