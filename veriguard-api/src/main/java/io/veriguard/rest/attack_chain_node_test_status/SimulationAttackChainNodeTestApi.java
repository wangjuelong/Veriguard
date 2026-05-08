package io.veriguard.rest.attack_chain_node_test_status;

import static io.veriguard.database.specification.AttackChainNodeSpecification.testable;
import static io.veriguard.rest.attack_chain_run.AttackChainRunApi.EXERCISE_URI;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.Grant;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeBulkProcessingInput;
import io.veriguard.rest.attack_chain_node.output.AttackChainNodeTestStatusOutput;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.AttackChainNodeTestStatusService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SimulationAttackChainNodeTestApi extends RestBehavior {

  private final AttackChainNodeTestStatusService attackChainNodeTestStatusService;
  private final AttackChainNodeService attackChainNodeService;

  /**
   * @deprecated since 1.16.0, forRemoval = true
   * @see #findAttackChainRunPageAttackChainNodeTests
   */
  @PostMapping("/api/exercise/{simulationId}/injects/test")
  @RBAC(
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION,
      resourceId = "#simulationId")
  public Page<AttackChainNodeTestStatusOutput> findAllAttackChainRunAttackChainNodeTests(
      @PathVariable @NotBlank String simulationId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return attackChainNodeTestStatusService.findAllAttackChainNodeTestsByAttackChainRunId(
        simulationId, searchPaginationInput);
  }

  @PostMapping(EXERCISE_URI + "/{simulationId}/injects/test/search")
  @RBAC(
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION,
      resourceId = "#simulationId")
  public Page<AttackChainNodeTestStatusOutput> findAttackChainRunPageAttackChainNodeTests(
      @PathVariable @NotBlank String simulationId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return attackChainNodeTestStatusService.findAllAttackChainNodeTestsByAttackChainRunId(
        simulationId, searchPaginationInput);
  }

  @Transactional(rollbackFor = Exception.class)
  @GetMapping(EXERCISE_URI + "/{simulationId}/injects/{attackChainNodeId}/test")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public AttackChainNodeTestStatusOutput testAttackChainNode(
      @PathVariable @NotBlank String simulationId, @PathVariable @NotBlank String attackChainNodeId)
      throws Exception {
    return attackChainNodeTestStatusService.testAttackChainNode(attackChainNodeId);
  }

  @Transactional(rollbackFor = Exception.class)
  @GetMapping(EXERCISE_URI + "/injects/test/{testId}")
  @RBAC(
      actionPerformed = Action.SEARCH,
      resourceType =
          ResourceType.SIMULATION) // fixme : should use action search on resourceType simulation
  public AttackChainNodeTestStatusOutput findAttackChainNodeTestStatus(
      @PathVariable @NotBlank String testId) {
    return attackChainNodeTestStatusService.findAttackChainNodeTestStatusById(testId);
  }

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(EXERCISE_URI + "/{simulationId}/injects/test/{testId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteAttackChainNodeTest(
      @PathVariable @NotBlank String simulationId, @PathVariable String testId) {
    attackChainNodeTestStatusService.deleteAttackChainNodeTest(testId);
  }

  @Operation(
      description = "Bulk tests of injects",
      tags = {"Injects", "Tests"})
  @Transactional(rollbackFor = Exception.class)
  @PostMapping(EXERCISE_URI + "/{simulationId}/injects/test")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @LogExecutionTime
  public List<AttackChainNodeTestStatusOutput> bulkTestAttackChainNode(
      @PathVariable @NotBlank String simulationId,
      @RequestBody @Valid final AttackChainNodeBulkProcessingInput input) {

    // Control and format inputs
    if (!simulationId.equals(input.getSimulationOrAttackChainId())) {
      throw new BadRequestException(
          "Provided simulation ID does not match the input simulation ID");
    }
    if (CollectionUtils.isEmpty(input.getAttackChainNodeIDsToProcess())
        && input.getSearchPaginationInput() == null) {
      throw new BadRequestException(
          "Either search_pagination_input or inject_ids_to_process must be provided");
    }

    // Specification building
    Specification<AttackChainNode> filterSpecifications =
        this.attackChainNodeService
            .getAttackChainNodeSpecification(input, Grant.GRANT_TYPE.PLANNER)
            .and(testable());

    // Services calls
    // Bulk test
    return attackChainNodeTestStatusService.bulkTestAttackChainNodes(filterSpecifications);
  }
}
