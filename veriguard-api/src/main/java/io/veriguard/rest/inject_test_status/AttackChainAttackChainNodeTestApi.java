package io.veriguard.rest.inject_test_status;

import static io.veriguard.database.specification.AttackChainNodeSpecification.testable;
import static io.veriguard.rest.scenario.AttackChainApi.SCENARIO_URI;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.Grant;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.exception.BadRequestException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.inject.form.AttackChainNodeBulkProcessingInput;
import io.veriguard.rest.inject.output.AttackChainNodeTestStatusOutput;
import io.veriguard.rest.inject.service.AttackChainNodeService;
import io.veriguard.service.AttackChainNodeTestStatusService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
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
public class AttackChainAttackChainNodeTestApi extends RestBehavior {

  private final AttackChainNodeTestStatusService attackChainNodeTestStatusService;
  private final AttackChainNodeService attackChainNodeService;

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/test/search")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Page<AttackChainNodeTestStatusOutput> findAllAttackChainAttackChainNodeTests(
      @PathVariable @NotBlank String attackChainId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return attackChainNodeTestStatusService.findAllAttackChainNodeTestsByAttackChainId(
        attackChainId, searchPaginationInput);
  }

  @Transactional(rollbackFor = Exception.class)
  @GetMapping(SCENARIO_URI + "/injects/test/{testId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SCENARIO)
  public AttackChainNodeTestStatusOutput findAttackChainNodeTestStatus(@PathVariable @NotBlank String testId) {
    return attackChainNodeTestStatusService.findAttackChainNodeTestStatusById(testId);
  }

  @Transactional(rollbackFor = Exception.class)
  @GetMapping(SCENARIO_URI + "/{scenarioId}/injects/{injectId}/test")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  public AttackChainNodeTestStatusOutput testAttackChainNode(
      @PathVariable @NotBlank final String attackChainId, @PathVariable @NotBlank String attackChainNodeId)
      throws Exception {
    return attackChainNodeTestStatusService.testAttackChainNode(attackChainNodeId);
  }

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(SCENARIO_URI + "/{scenarioId}/injects/test/{testId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteAttackChainNodeTest(
      @PathVariable @NotBlank final String attackChainId, @PathVariable String testId) {
    attackChainNodeTestStatusService.deleteAttackChainNodeTest(testId);
  }

  @Operation(
      description = "Bulk tests of injects",
      tags = {"Injects", "Tests"})
  @Transactional(rollbackFor = Exception.class)
  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/test")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @LogExecutionTime
  public List<AttackChainNodeTestStatusOutput> bulkTestAttackChainNode(
      @PathVariable @NotBlank final String attackChainId,
      @RequestBody @Valid final AttackChainNodeBulkProcessingInput input) {

    // Control and format inputs
    if (!attackChainId.equals(input.getSimulationOrAttackChainId())) {
      throw new BadRequestException("Provided scenario ID does not match the input scenario ID");
    }
    if (CollectionUtils.isEmpty(input.getAttackChainNodeIDsToProcess())
        && input.getSearchPaginationInput() == null) {
      throw new BadRequestException(
          "Either search_pagination_input or inject_ids_to_process must be provided");
    }

    // Specification building
    Specification<AttackChainNode> filterSpecifications =
        this.attackChainNodeService.getAttackChainNodeSpecification(input, Grant.GRANT_TYPE.PLANNER).and(testable());

    // Services calls
    // Bulk test
    return attackChainNodeTestStatusService.bulkTestAttackChainNodes(filterSpecifications);
  }
}
