package io.veriguard.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GetAttackChainsInput {
  @JsonProperty("scenario_ids")
  private List<String> attackChainIds = new ArrayList<>();
}
