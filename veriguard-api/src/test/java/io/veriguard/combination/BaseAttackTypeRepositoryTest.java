package io.veriguard.combination;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.combination.BaseAttackType;
import io.veriguard.database.model.combination.BaseAttackTypeCategory;
import io.veriguard.database.model.combination.BaseAttackTypeTargetLayer;
import io.veriguard.database.repository.BaseAttackTypeRepository;
import io.veriguard.datapack.packs.V20260514_Base_attack_types_pack;
import io.veriguard.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成测试 —— PR B5 BaseAttackTypeRepository 验证 seed pack 后表内 ≥ 250 类，
 * 且 category / target_layer 过滤查询返回正确子集。
 *
 * <p>DataPackProcessor 在 test profile 下被禁用（@Profile("!test")），所以测试
 * 在 setUp 显式触发 V20260514_Base_attack_types_pack#process()。
 */
@DirtiesContext
@Transactional
@WithMockUser(isAdmin = true)
class BaseAttackTypeRepositoryTest extends IntegrationTest {

  @Autowired private BaseAttackTypeRepository repository;
  @Autowired private V20260514_Base_attack_types_pack seedPack;

  @BeforeEach
  void seed() {
    if (repository.count() == 0) {
      seedPack.process();
    }
  }

  @Test
  void seed_loaded_at_least_250_base_attack_types() {
    long count = repository.count();
    assertThat(count)
        .as("DataPack seed must load at least 250 base attack types per 招标 §3.6 ★2")
        .isGreaterThanOrEqualTo(250);
  }

  @Test
  void find_by_category_returns_subset() {
    List<BaseAttackType> webInjection =
        repository.findAllByCategory(BaseAttackTypeCategory.web_injection);
    assertThat(webInjection).isNotEmpty();
    assertThat(webInjection)
        .allSatisfy(t -> assertThat(t.getCategory()).isEqualTo(BaseAttackTypeCategory.web_injection));
    // sql_injection is in seed json
    assertThat(webInjection).extracting(BaseAttackType::getName).contains("sql_injection");
  }

  @Test
  void find_by_target_layer_returns_subset() {
    List<BaseAttackType> hostLayer =
        repository.findAllByTargetLayer(BaseAttackTypeTargetLayer.host);
    assertThat(hostLayer).isNotEmpty();
    assertThat(hostLayer)
        .allSatisfy(
            t -> assertThat(t.getTargetLayer()).isEqualTo(BaseAttackTypeTargetLayer.host));
  }
}
