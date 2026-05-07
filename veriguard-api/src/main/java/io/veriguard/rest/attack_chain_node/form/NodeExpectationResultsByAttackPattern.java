package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.veriguard.database.model.AttackPattern;
import io.veriguard.helper.MonoIdSerializer;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeExpectationResultsByAttackPattern {

  @JsonProperty("inject_expectation_results")
  private List<NodeExpectationResultsByType> results;

  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_attack_pattern")
  @Schema(type = "string")
  private AttackPattern attackPattern;

  @Builder
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class NodeExpectationResultsByType {
    @JsonProperty("inject_id")
    private String attackChainNodeId;

    @JsonProperty("inject_title")
    private String attackChainNodeTitle;

    @JsonProperty("results")
    private List<ExpectationResultsByType> results;
  }
}
