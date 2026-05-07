package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AttackChainNodeExpectationUpdateInput {
  @NotNull
  @JsonProperty("collector_id")
  private String collectorId;

  @NotNull
  @JsonProperty("result")
  private String result;

  @NotNull
  @JsonProperty("is_success")
  private Boolean isSuccess;

  @JsonProperty("metadata")
  private Map<String, String> metadata;
}
