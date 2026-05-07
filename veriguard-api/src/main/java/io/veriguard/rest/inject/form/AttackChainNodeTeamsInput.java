package io.veriguard.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AttackChainNodeTeamsInput {

  @JsonProperty("inject_teams")
  private List<String> teamIds;

  public List<String> getTeamIds() {
    return teamIds;
  }

  public void setTeamIds(List<String> teamIds) {
    this.teamIds = teamIds;
  }
}
