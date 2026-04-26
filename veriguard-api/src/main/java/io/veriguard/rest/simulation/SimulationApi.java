package io.veriguard.rest.simulation;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.utils.FilterUtilsJpa.Option;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping(SimulationApi.SIMULATION_URI)
@RestController
@RequiredArgsConstructor
public class SimulationApi extends RestBehavior {

  public static final String SIMULATION_URI = "/api/simulations";

  private final SimulationService simulationService;

  // -- OPTION --

  @GetMapping("/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<Option> optionsByName(@RequestParam(required = false) final String searchText) {
    return this.simulationService.findAllAsOptions(searchText);
  }

  @PostMapping("/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<Option> optionsById(@RequestBody final List<String> ids) {
    return this.simulationService.findAllByIdsAsOptions(ids);
  }
}
