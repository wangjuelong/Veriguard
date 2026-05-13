package io.veriguard.combination;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.combination.transform.Base64EncodeTransform;
import io.veriguard.combination.transform.HashCommentInjectTransform;
import io.veriguard.combination.transform.HttpChunkSplitTransform;
import io.veriguard.combination.transform.IdentityTransform;
import io.veriguard.combination.transform.MixedCaseTransform;
import io.veriguard.combination.transform.NoiseSurroundTransform;
import io.veriguard.combination.transform.ParamOrderShuffleTransform;
import io.veriguard.combination.transform.PayloadTransformRegistry;
import io.veriguard.combination.transform.UnicodeFullWidthTransform;
import io.veriguard.combination.transform.UrlEncodeTransform;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 校验 seed JSON 结构 + 内容（不依赖 Spring 容器）：
 * - 维度数量 ≥ 120
 * - 所有 name 唯一
 * - 所有 category 是合法枚举值
 * - 所有 transform_type 在 Registry 注册（可解析）
 * - 每条 entry 必填 name/category/transform_type 三段
 */
class BypassDimensionsSeedJsonTest {

  private static final String RESOURCE_PATH = "data/bypass_dimensions/120_dimensions.json";
  private static final int MINIMUM_DIMENSIONS = 120;

  @Test
  void seed_json_satisfies_招标_120_dimension_target() throws Exception {
    ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
    assertThat(resource.exists()).as("seed JSON exists on classpath").isTrue();

    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> root;
    try (InputStream in = resource.getInputStream()) {
      root = mapper.readValue(in, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("dimensions");
    assertThat(entries).isNotNull();
    assertThat(entries.size()).isGreaterThanOrEqualTo(MINIMUM_DIMENSIONS);

    Set<String> names = new HashSet<>();
    PayloadTransformRegistry registry = newRegistry();

    for (Map<String, Object> entry : entries) {
      Object nameObj = entry.get("name");
      Object catObj = entry.get("category");
      Object typeObj = entry.get("transform_type");
      assertThat(nameObj).isInstanceOf(String.class);
      assertThat(catObj).isInstanceOf(String.class);
      assertThat(typeObj).isInstanceOf(String.class);

      String name = (String) nameObj;
      assertThat(names.add(name)).as("duplicate name: " + name).isTrue();

      assertThat(BypassDimensionCategory.valueOf((String) catObj))
          .as("category valid for " + name)
          .isNotNull();

      assertThat(registry.knownTypes())
          .as("transform_type registered for " + name)
          .contains((String) typeObj);
    }
  }

  /**
   * 用所有现有 9 个 transform 构造 Registry（不走 Spring 容器，纯单元）.
   */
  private static PayloadTransformRegistry newRegistry() {
    return new PayloadTransformRegistry(
        List.of(
            new IdentityTransform(),
            new Base64EncodeTransform(),
            new UrlEncodeTransform(),
            new MixedCaseTransform(),
            new HttpChunkSplitTransform(),
            new NoiseSurroundTransform(),
            new UnicodeFullWidthTransform(),
            new HashCommentInjectTransform(),
            new ParamOrderShuffleTransform()));
  }
}
