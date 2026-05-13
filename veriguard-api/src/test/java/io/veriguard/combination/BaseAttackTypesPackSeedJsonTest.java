package io.veriguard.combination;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.combination.BaseAttackTypeCategory;
import io.veriguard.database.model.combination.BaseAttackTypeTargetLayer;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 校验 base_attack_types seed JSON 结构 + 内容（不依赖 Spring 容器）：
 * - 类型数量 ≥ 250（招标 §3.6 ★2 门槛）
 * - 所有 name 唯一 + 非空
 * - 所有 category 是合法 enum
 * - 所有 target_layer 是合法 enum
 * - severity_score ∈ [0,100]
 * - 9 个 category 至少各有 5 项（避免单类别堆积）
 * - 字段值不含 NUL 字节（PR D1 教训：PG JSONB 不容忍 0x00）
 */
class BaseAttackTypesPackSeedJsonTest {

  private static final String RESOURCE_PATH = "data/base_attack_types/250_base_attack_types.json";
  private static final int MINIMUM_TYPES = 250;
  private static final int MIN_PER_CATEGORY = 5;

  @Test
  void seed_json_satisfies_招标_250_base_attack_type_target() throws Exception {
    ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
    assertThat(resource.exists()).as("seed JSON exists on classpath").isTrue();

    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> root;
    try (InputStream in = resource.getInputStream()) {
      root = mapper.readValue(in, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("base_attack_types");
    assertThat(entries).isNotNull();
    assertThat(entries.size())
        .as("at least %s base attack types per 招标 §3.6 ★2", MINIMUM_TYPES)
        .isGreaterThanOrEqualTo(MINIMUM_TYPES);

    Set<String> names = new HashSet<>();
    Map<BaseAttackTypeCategory, Integer> perCategory = new HashMap<>();

    for (Map<String, Object> entry : entries) {
      Object nameObj = entry.get("name");
      Object catObj = entry.get("category");
      Object labelObj = entry.get("display_label");
      Object layerObj = entry.get("target_layer");
      Object sevObj = entry.get("severity_score");

      assertThat(nameObj).as("name is string").isInstanceOf(String.class);
      assertThat(catObj).as("category is string").isInstanceOf(String.class);
      assertThat(labelObj).as("display_label is string").isInstanceOf(String.class);
      assertThat(layerObj).as("target_layer is string").isInstanceOf(String.class);
      assertThat(sevObj).as("severity_score is number").isInstanceOf(Number.class);

      String name = (String) nameObj;
      assertThat(names.add(name)).as("duplicate name: " + name).isTrue();

      BaseAttackTypeCategory cat = BaseAttackTypeCategory.valueOf((String) catObj);
      assertThat(cat).as("category valid for " + name).isNotNull();
      perCategory.merge(cat, 1, Integer::sum);

      BaseAttackTypeTargetLayer layer = BaseAttackTypeTargetLayer.valueOf((String) layerObj);
      assertThat(layer).as("target_layer valid for " + name).isNotNull();

      int sev = ((Number) sevObj).intValue();
      assertThat(sev)
          .as("severity_score in [0,100] for " + name)
          .isBetween(0, 100);

      // No NUL bytes anywhere (PG JSONB does not tolerate 0x00, see PR D1 fix)
      for (Map.Entry<String, Object> field : entry.entrySet()) {
        if (field.getValue() instanceof String s) {
          assertThat(s.indexOf('\0'))
              .as("NUL byte in " + name + "." + field.getKey())
              .isLessThan(0);
        }
      }
    }
  }

  @Test
  void each_category_has_at_least_min_entries() throws Exception {
    ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> root;
    try (InputStream in = resource.getInputStream()) {
      root = mapper.readValue(in, new TypeReference<Map<String, Object>>() {});
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("base_attack_types");

    Map<BaseAttackTypeCategory, Integer> perCategory = new HashMap<>();
    for (Map<String, Object> entry : entries) {
      BaseAttackTypeCategory cat =
          BaseAttackTypeCategory.valueOf((String) entry.get("category"));
      perCategory.merge(cat, 1, Integer::sum);
    }

    for (BaseAttackTypeCategory cat : BaseAttackTypeCategory.values()) {
      assertThat(perCategory.getOrDefault(cat, 0))
          .as("category %s has at least %s entries", cat, MIN_PER_CATEGORY)
          .isGreaterThanOrEqualTo(MIN_PER_CATEGORY);
    }
  }
}
