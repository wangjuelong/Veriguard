package io.veriguard.rest.injector_contract;

import static io.veriguard.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.raw.RawNodeExecutorsContracts;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.injector_contract.form.NodeContractAddInput;
import io.veriguard.rest.injector_contract.form.NodeContractUpdateInput;
import io.veriguard.rest.injector_contract.form.NodeContractUpdateMappingInput;
import io.veriguard.rest.injector_contract.input.NodeContractSearchPaginationInput;
import io.veriguard.rest.injector_contract.output.NodeContractBaseOutput;
import io.veriguard.rest.injector_contract.output.NodeContractDomainCountOutput;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class NodeContractApi extends RestBehavior {

  public static final String INJECTOR_CONTRACT_URL = "/api/injector_contracts";

  private final NodeContractService nodeContractService;

  @GetMapping(INJECTOR_CONTRACT_URL)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public Iterable<RawNodeExecutorsContracts> attackChainNodeContracts() {
    return nodeContractService.getAllRawAttackChainNodeContracts();
  }

  /**
   * Searches nodeExecutor contracts with pagination and filtering.
   *
   * <p>Can return either full or base details based on the input flag.
   *
   * @param input the search and pagination parameters
   * @return a page of nodeExecutor contract outputs
   */
  @PostMapping(INJECTOR_CONTRACT_URL + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public Page<? extends NodeContractBaseOutput> nodeContracts(
      @RequestBody @Valid final NodeContractSearchPaginationInput input) {
    if (input.isIncludeFullDetails()) {
      return buildPaginationCriteriaBuilder(
          this.nodeContractService::getSinglePageFullDetails,
          handleArchitectureFilter(input),
          NodeContract.class);
    } else {
      return buildPaginationCriteriaBuilder(
          this.nodeContractService::getSinglePageBaseDetails,
          handleArchitectureFilter(input),
          NodeContract.class);
    }
  }

  @PostMapping(INJECTOR_CONTRACT_URL + "/domain-counts")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public List<NodeContractDomainCountOutput> getDomainCounts(
      @RequestBody @Valid final NodeContractSearchPaginationInput input) {
    SearchPaginationInput filtered = handleArchitectureFilter(input);
    return nodeContractService.getDomainCounts(filtered);
  }

  /**
   * Retrieves a specific nodeExecutor contract by ID.
   *
   * @param nodeContractId the contract ID or external ID
   * @return the nodeExecutor contract
   */
  @GetMapping(INJECTOR_CONTRACT_URL + "/{injectorContractId}")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public NodeContract nodeContract(@PathVariable String nodeContractId) {
    return nodeContractService.getSingleNodeContract(nodeContractId);
  }

  /**
   * Creates a new custom nodeExecutor contract.
   *
   * @param input the creation input with contract details
   * @return the created nodeExecutor contract
   */
  @PostMapping(INJECTOR_CONTRACT_URL)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR_CONTRACT)
  public NodeContract createNodeContract(@Valid @RequestBody NodeContractAddInput input) {
    return nodeContractService.createNewNodeContract(input);
  }

  /**
   * Updates an existing nodeExecutor contract.
   *
   * @param nodeContractId the contract ID to update
   * @param input the update data
   * @return the updated nodeExecutor contract
   */
  @PutMapping(INJECTOR_CONTRACT_URL + "/{injectorContractId}")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public NodeContract updateNodeContract(
      @PathVariable String nodeContractId, @Valid @RequestBody NodeContractUpdateInput input) {
    return nodeContractService.updateNodeContract(nodeContractId, input);
  }

  /**
   * Updates the attack pattern and vulnerability mappings for a contract.
   *
   * @param nodeContractId the contract ID to update
   * @param input the mapping update data
   * @return the updated nodeExecutor contract
   */
  @PutMapping(INJECTOR_CONTRACT_URL + "/{injectorContractId}/mapping")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public NodeContract updateNodeContractMapping(
      @PathVariable String nodeContractId,
      @Valid @RequestBody NodeContractUpdateMappingInput input) {
    return nodeContractService.updateAttackPatternMappings(nodeContractId, input);
  }

  /**
   * Deletes a custom nodeExecutor contract.
   *
   * <p>Only custom (user-created) contracts can be deleted.
   *
   * @param nodeContractId the contract ID to delete
   */
  @DeleteMapping(INJECTOR_CONTRACT_URL + "/{injectorContractId}")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public void deleteNodeContract(@PathVariable String nodeContractId) {
    this.nodeContractService.deleteNodeContract(nodeContractId);
  }
}
