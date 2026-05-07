package io.veriguard.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class UpdateAttackChainInput extends AttackChainInput {
  @JsonProperty("apply_tag_rule")
  private boolean applyTagRule = false;
}
