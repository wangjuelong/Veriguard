package io.veriguard.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AttackChainUpdateTeamsInput {

  @JsonProperty("scenario_teams")
  private List<String> teamIds = new ArrayList<>();
}
