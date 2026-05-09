package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AttackChainNodeTeamsInput {

  @JsonProperty("node_teams")
  private List<String> teamIds;

  public List<String> getTeamIds() {
    return teamIds;
  }

  public void setTeamIds(List<String> teamIds) {
    this.teamIds = teamIds;
  }
}
