package io.veriguard.rest.attack_combination;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.veriguard.IntegrationTest;
import io.veriguard.database.repository.BaseAttackTypeRepository;
import io.veriguard.datapack.packs.V20260514_Base_attack_types_pack;
import io.veriguard.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests —— PR B5 base_attack_types REST 端点（list / category / target_layer 过滤）。
 *
 * <p>DataPackProcessor 在 test profile 下被禁用（@Profile("!test")），所以测试在 setUp
 * 显式触发 V20260514_Base_attack_types_pack#process() 把 seed JSON 落库。
 */
@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class BaseAttackTypesApiIntegrationTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private BaseAttackTypeRepository repository;
  @Autowired private V20260514_Base_attack_types_pack seedPack;

  @BeforeEach
  void seed() {
    if (repository.count() == 0) {
      seedPack.process();
    }
  }

  @Test
  void list_returns_paged_results_with_seed_data() throws Exception {
    mockMvc
        .perform(get(AttackCombinationApi.BASE_ATTACK_TYPES_URI).param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").exists())
        .andExpect(jsonPath("$.content[0].base_attack_type_name").exists())
        .andExpect(jsonPath("$.content[0].base_attack_type_category").exists())
        .andExpect(jsonPath("$.content[0].base_attack_type_target_layer").exists())
        .andExpect(jsonPath("$.content[0].base_attack_type_severity_score").exists());
  }

  @Test
  void list_with_category_filter_returns_only_that_category() throws Exception {
    mockMvc
        .perform(
            get(AttackCombinationApi.BASE_ATTACK_TYPES_URI)
                .param("category", "web_injection")
                .param("size", "200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].base_attack_type_category").value("web_injection"));
  }

  @Test
  void list_with_target_layer_filter_returns_only_that_layer() throws Exception {
    mockMvc
        .perform(
            get(AttackCombinationApi.BASE_ATTACK_TYPES_URI)
                .param("target_layer", "host")
                .param("size", "200"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].base_attack_type_target_layer").value("host"));
  }

  @Test
  void get_by_name_returns_detail_for_known_seed_entry() throws Exception {
    // sql_injection is seeded as part of web_injection category
    mockMvc
        .perform(get(AttackCombinationApi.BASE_ATTACK_TYPES_URI + "/sql_injection"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.base_attack_type_name").value("sql_injection"))
        .andExpect(jsonPath("$.base_attack_type_category").value("web_injection"))
        .andExpect(jsonPath("$.base_attack_type_severity_score").exists());
  }

  @Test
  void get_by_unknown_name_returns_404() throws Exception {
    mockMvc
        .perform(get(AttackCombinationApi.BASE_ATTACK_TYPES_URI + "/totally_made_up_attack"))
        .andExpect(status().isNotFound());
  }
}
