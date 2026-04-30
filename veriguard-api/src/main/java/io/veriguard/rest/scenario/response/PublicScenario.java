package io.veriguard.rest.scenario.response;

import io.veriguard.database.model.Scenario;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicScenario {

  private String id;
  private String name;
  private String description;

  public PublicScenario(Scenario scenario) {
    this.id = scenario.getId();
    this.name = scenario.getName();
    this.description = scenario.getDescription();
  }
}
