package io.veriguard.executors.crowdstrike.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.veriguard.database.model.Agent;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrowdStrikeAction {

  private List<Agent> agents;
  private String scriptName;
  private String commandEncoded;
}
