package io.veriguard.rest.attack_chain.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GetAttackChainsInput {
  @JsonProperty("attack_chain_ids")
  private List<String> attackChainIds = new ArrayList<>();
}
