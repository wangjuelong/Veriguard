package io.veriguard.rest.attack_chain_node.form;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.AttackChainNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttackChainNodeSettingsInputTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("snake_case JSON deserializes into typed Java fields")
  void deserialize_snakeCaseJson_typedFields() throws Exception {
    String json =
        """
        {
          "node_title": "侦察阶段",
          "node_description": "扫描内网",
          "node_validation_parameter_set_id": "11111111-2222-3333-4444-555555555555",
          "node_repeat_count": 3,
          "node_repeat_interval_seconds": 600
        }
        """;

    AttackChainNodeSettingsInput input =
        objectMapper.readValue(json, AttackChainNodeSettingsInput.class);

    assertThat(input.getTitle()).isEqualTo("侦察阶段");
    assertThat(input.getDescription()).isEqualTo("扫描内网");
    assertThat(input.getValidationParameterSetId())
        .isEqualTo(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    assertThat(input.getRepeatCount()).isEqualTo(3);
    assertThat(input.getRepeatIntervalSeconds()).isEqualTo(600L);
  }

  @Test
  @DisplayName("default values: parameter_set_id null, repeat_count 1, interval 0")
  void default_values() throws Exception {
    AttackChainNodeSettingsInput input =
        objectMapper.readValue("{}", AttackChainNodeSettingsInput.class);

    assertThat(input.getValidationParameterSetId()).isNull();
    assertThat(input.getRepeatCount()).isEqualTo(1);
    assertThat(input.getRepeatIntervalSeconds()).isEqualTo(0L);
  }

  @Test
  @DisplayName("setUpdateAttributes copies title + description + V3 fields onto the AttackChainNode entity")
  void setUpdateAttributes_copiesAllFields() {
    AttackChainNodeSettingsInput input = new AttackChainNodeSettingsInput();
    input.setTitle("侦察阶段");
    input.setDescription("扫描内网");
    UUID parameterSetId = UUID.randomUUID();
    input.setValidationParameterSetId(parameterSetId);
    input.setRepeatCount(5);
    input.setRepeatIntervalSeconds(120L);

    AttackChainNode node = new AttackChainNode();
    node.setUpdateAttributes(input);

    assertThat(node.getTitle()).isEqualTo("侦察阶段");
    assertThat(node.getDescription()).isEqualTo("扫描内网");
    assertThat(node.getValidationParameterSetId()).isEqualTo(parameterSetId);
    assertThat(node.getRepeatCount()).isEqualTo(5);
    assertThat(node.getRepeatIntervalSeconds()).isEqualTo(120L);
  }
}
