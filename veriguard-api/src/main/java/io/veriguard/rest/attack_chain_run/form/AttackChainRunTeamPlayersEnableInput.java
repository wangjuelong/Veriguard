package io.veriguard.rest.attack_chain_run.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AttackChainRunTeamPlayersEnableInput {

  @JsonProperty("exercise_team_players")
  private List<String> playersIds = new ArrayList<>();
}
