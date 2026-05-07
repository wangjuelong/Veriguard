package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk processing (delete and tests) calls for attackChainNodes */
@Setter
@Getter
public class AttackChainNodeBulkProcessingInput {

  /**
   * The search input, used to select the attackChainNodes to update. Must be provided if attackChainNodeIDsToDelete
   * is not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /** The list of attackChainNodes to process. Must be provided if searchPaginationInput is not provided */
  @JsonProperty("inject_ids_to_process")
  private List<String> attackChainNodeIDsToProcess;

  /** The list of attackChainNodes to ignore from the search input */
  @JsonProperty("inject_ids_to_ignore")
  private List<String> attackChainNodeIDsToIgnore;

  /** The simulation or attackChain ID to which the attackChainNodes belong. */
  @JsonProperty("simulation_or_scenario_id")
  private String simulationOrAttackChainId;
}
