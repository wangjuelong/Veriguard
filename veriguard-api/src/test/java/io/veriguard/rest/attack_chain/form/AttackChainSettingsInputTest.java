package io.veriguard.rest.attack_chain.form;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.ExecutionMode;
import io.veriguard.database.model.SocCorrelationRuleRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttackChainSettingsInputTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("snake_case JSON deserializes into typed Java fields")
  void deserialize_snakeCaseJson_typedFields() throws Exception {
    String json =
        """
        {
          "attack_chain_execution_mode": "CONTINUE",
          "attack_chain_validation_parameter_set_id": "11111111-2222-3333-4444-555555555555",
          "attack_chain_soc_correlation_rules": [
            {
              "connector_id": "elastic",
              "rule_id": "rule-1",
              "display_name": "Suspicious Login",
              "match_window_seconds": 3600
            }
          ]
        }
        """;

    AttackChainSettingsInput input = objectMapper.readValue(json, AttackChainSettingsInput.class);

    assertThat(input.getExecutionMode()).isEqualTo(ExecutionMode.CONTINUE);
    assertThat(input.getValidationParameterSetId())
        .isEqualTo(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    assertThat(input.getSocCorrelationRules()).hasSize(1);
    SocCorrelationRuleRef ref = input.getSocCorrelationRules().get(0);
    assertThat(ref.connectorId()).isEqualTo("elastic");
    assertThat(ref.ruleId()).isEqualTo("rule-1");
    assertThat(ref.displayName()).isEqualTo("Suspicious Login");
    assertThat(ref.matchWindowSeconds()).isEqualTo(3600);
  }

  @Test
  @DisplayName("default executionMode is STOP_ON_BLOCK when JSON omits the field")
  void default_executionMode_stopOnBlock() throws Exception {
    String json = "{}";

    AttackChainSettingsInput input = objectMapper.readValue(json, AttackChainSettingsInput.class);

    assertThat(input.getExecutionMode()).isEqualTo(ExecutionMode.STOP_ON_BLOCK);
    assertThat(input.getValidationParameterSetId()).isNull();
    assertThat(input.getSocCorrelationRules()).isEmpty();
  }

  @Test
  @DisplayName("setUpdateAttributes copies executionMode + parameter set + SOC rules onto AttackChain entity")
  void setUpdateAttributes_copiesAllFields() {
    AttackChainSettingsInput input = new AttackChainSettingsInput();
    input.setExecutionMode(ExecutionMode.CONTINUE);
    UUID parameterSetId = UUID.randomUUID();
    input.setValidationParameterSetId(parameterSetId);
    input.setSocCorrelationRules(
        List.of(new SocCorrelationRuleRef("elastic", "rule-a", "Rule A", 7200)));

    AttackChain attackChain = new AttackChain();
    attackChain.setUpdateAttributes(input);

    assertThat(attackChain.getExecutionMode()).isEqualTo(ExecutionMode.CONTINUE);
    assertThat(attackChain.getValidationParameterSetId()).isEqualTo(parameterSetId);
    assertThat(attackChain.getSocCorrelationRules()).hasSize(1);
    assertThat(attackChain.getSocCorrelationRules().get(0).connectorId()).isEqualTo("elastic");
  }

  @Test
  @DisplayName("clearing parameterSetId via null is preserved by BeanUtils copy")
  void setUpdateAttributes_clearsParameterSetId() {
    AttackChain attackChain = new AttackChain();
    attackChain.setValidationParameterSetId(UUID.randomUUID());

    AttackChainSettingsInput input = new AttackChainSettingsInput();
    input.setExecutionMode(ExecutionMode.STOP_ON_BLOCK);
    input.setValidationParameterSetId(null);

    attackChain.setUpdateAttributes(input);

    assertThat(attackChain.getValidationParameterSetId()).isNull();
  }
}
