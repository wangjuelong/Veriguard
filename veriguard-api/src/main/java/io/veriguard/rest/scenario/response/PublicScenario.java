package io.veriguard.rest.scenario.response;

import io.veriguard.database.model.Scenario;
import io.veriguard.rest.challenge.output.PublicEntity;

public class PublicScenario extends PublicEntity {

  public PublicScenario(Scenario scenario) {
    setId(scenario.getId());
    setName(scenario.getName());
    setDescription(scenario.getDescription());
  }
}
