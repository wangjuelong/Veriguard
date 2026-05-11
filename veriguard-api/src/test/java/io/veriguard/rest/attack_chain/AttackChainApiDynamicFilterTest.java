package io.veriguard.rest.attack_chain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Filters.FilterGroup;
import io.veriguard.database.model.Filters.FilterMode;
import io.veriguard.rest.attack_chain.form.AttackChainDynamicFilterInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单测覆盖 PUT /api/attack_chains/{id}/dynamic_filter 的 JSON wire format（PRD §2.3 第 4 行）.
 *
 * <p>聚焦 Jackson 反序列化：端点本身只是 findById + setDynamicFilter + save， 不在此重复验证；@Valid Bean Validation 层在
 * Spring MVC 层拦截，不在纯 Jackson 层触发.
 */
class AttackChainApiDynamicFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("空 filter 列表 + mode=and 反序列化正确")
  void deserialize_emptyFilter() throws Exception {
    // Arrange
    String json =
        """
        {"dynamic_filter":{"mode":"and","filters":[]}}
        """;

    // Act
    AttackChainDynamicFilterInput input =
        objectMapper.readValue(json, AttackChainDynamicFilterInput.class);

    // Assert
    FilterGroup fg = input.dynamicFilter();
    assertThat(fg).isNotNull();
    assertThat(fg.getMode()).isEqualTo(FilterMode.and);
    assertThat(fg.getFilters()).isEmpty();
  }

  @Test
  @DisplayName("OR mode + 2 个 filter 反序列化，key/values/operator 正确")
  void deserialize_orModeWithTwoFilters() throws Exception {
    // Arrange
    String json =
        """
        {
          "dynamic_filter": {
            "mode": "or",
            "filters": [
              {"key": "tag", "mode": "or", "operator": "eq", "values": ["red"]},
              {"key": "severity", "mode": "or", "operator": "not_eq", "values": ["low","medium"]}
            ]
          }
        }
        """;

    // Act
    AttackChainDynamicFilterInput input =
        objectMapper.readValue(json, AttackChainDynamicFilterInput.class);

    // Assert
    FilterGroup fg = input.dynamicFilter();
    assertThat(fg.getMode()).isEqualTo(FilterMode.or);
    assertThat(fg.getFilters()).hasSize(2);
    assertThat(fg.getFilters().get(0).getKey()).isEqualTo("tag");
    assertThat(fg.getFilters().get(0).getValues()).containsExactly("red");
    assertThat(fg.getFilters().get(1).getKey()).isEqualTo("severity");
    assertThat(fg.getFilters().get(1).getValues()).containsExactly("low", "medium");
  }

  @Test
  @DisplayName(
      "dynamic_filter=null 时 Jackson 反序列化成功，dynamicFilter() 返回 null（Bean Validation 在 Spring 层拦截）")
  void deserialize_nullDynamicFilter() throws Exception {
    // Arrange
    String json = "{\"dynamic_filter\":null}";

    // Act
    AttackChainDynamicFilterInput input =
        objectMapper.readValue(json, AttackChainDynamicFilterInput.class);

    // Assert — Jackson 层不报错；@NotNull 由 Spring @Valid 拦截
    assertThat(input.dynamicFilter()).isNull();
  }

  @Test
  @DisplayName("FilterGroup 缺少 mode 字段时 Jackson 容错，mode=null；Bean Validation 在 Spring 层拦截")
  void deserialize_missingModeField() throws Exception {
    // Arrange
    String json = "{\"dynamic_filter\":{\"filters\":[]}}";

    // Act
    AttackChainDynamicFilterInput input =
        objectMapper.readValue(json, AttackChainDynamicFilterInput.class);

    // Assert — Jackson 不抛异常；mode 为 null
    assertThat(input.dynamicFilter()).isNotNull();
    assertThat(input.dynamicFilter().getMode()).isNull();
    assertThat(input.dynamicFilter().getFilters()).isEmpty();
  }
}
