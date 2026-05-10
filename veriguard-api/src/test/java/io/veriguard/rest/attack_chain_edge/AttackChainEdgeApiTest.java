package io.veriguard.rest.attack_chain_edge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.EdgeCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单测覆盖 PUT /api/attack_chain_edges/{id}/condition 的 JSON wire format。完整端到端 (mvc) 测试
 * 走 IntegrationTest 太重；这里聚焦输入反序列化 —— 端点逻辑本身只是 findByEdgeId + setCondition + save，
 * Phase 3 EdgeConditionEvaluatorTest 已覆盖 sealed 类型自身的求值，不重复验证。
 */
class AttackChainEdgeApiTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("flat eq condition deserializes into EdgeCondition.Eq via JsonTypeInfo discriminator")
  void deserialize_flatEq() throws Exception {
    String json =
        """
        {
          "dependency_condition": {
            "type": "eq",
            "dimension": "PREVENTION",
            "status": "ALL_SUCCESS"
          }
        }
        """;
    AttackChainEdgeApi.EdgeConditionUpdateInput input =
        objectMapper.readValue(json, AttackChainEdgeApi.EdgeConditionUpdateInput.class);

    assertThat(input.condition()).isInstanceOf(EdgeCondition.Eq.class);
    EdgeCondition.Eq eq = (EdgeCondition.Eq) input.condition();
    assertThat(eq.dimension()).isEqualTo(EdgeCondition.ExpectationDimension.PREVENTION);
    assertThat(eq.status()).isEqualTo(EdgeCondition.ExpectationStatusGroup.ALL_SUCCESS);
  }

  @Test
  @DisplayName("nested and/or tree deserializes recursively")
  void deserialize_nestedTree() throws Exception {
    String json =
        """
        {
          "dependency_condition": {
            "type": "and",
            "children": [
              { "type": "eq", "dimension": "PREVENTION", "status": "ALL_SUCCESS" },
              {
                "type": "or",
                "children": [
                  { "type": "eq", "dimension": "DETECTION", "status": "ANY_SUCCESS" },
                  { "type": "eq", "dimension": "MANUAL", "status": "SETTLED" }
                ]
              }
            ]
          }
        }
        """;
    AttackChainEdgeApi.EdgeConditionUpdateInput input =
        objectMapper.readValue(json, AttackChainEdgeApi.EdgeConditionUpdateInput.class);

    assertThat(input.condition()).isInstanceOf(EdgeCondition.And.class);
    EdgeCondition.And and = (EdgeCondition.And) input.condition();
    assertThat(and.children()).hasSize(2);
    assertThat(and.children().get(0)).isInstanceOf(EdgeCondition.Eq.class);
    assertThat(and.children().get(1)).isInstanceOf(EdgeCondition.Or.class);

    EdgeCondition.Or innerOr = (EdgeCondition.Or) and.children().get(1);
    assertThat(innerOr.children()).hasSize(2);
    assertThat(innerOr.children())
        .allMatch(c -> c instanceof EdgeCondition.Eq);
  }

  @Test
  @DisplayName("null condition body is preserved (allowed: deletes the edge condition)")
  void deserialize_nullCondition() throws Exception {
    String json = "{ \"dependency_condition\": null }";
    AttackChainEdgeApi.EdgeConditionUpdateInput input =
        objectMapper.readValue(json, AttackChainEdgeApi.EdgeConditionUpdateInput.class);

    assertThat(input.condition()).isNull();
  }
}
