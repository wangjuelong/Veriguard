package io.veriguard.rest.expectation;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.ResourceType;
import io.veriguard.model.inject.form.Expectation;
import io.veriguard.rest.attack_chain_run.form.ExpectationUpdateInput;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExpectationBulkUpdateInput;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExpectationUpdateInput;
import io.veriguard.service.ExpectationService;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ExpectationApi extends RestBehavior {

  public static final String EXPECTATIONS_URI = "/api/expectations";
  public static final String INJECTS_EXPECTATIONS_URI = "/api/attack_chain_nodes/expectations";

  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final ExpectationService expectationService;

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(EXPECTATIONS_URI + "/{expectationId}")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public AttackChainNodeExpectation updateAttackChainNodeExpectation(
      @PathVariable @NotBlank final String expectationId,
      @Valid @RequestBody final ExpectationUpdateInput input) {
    return attackChainNodeExpectationService.updateAttackChainNodeExpectation(expectationId, input);
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(EXPECTATIONS_URI + "/{expectationId}/{sourceId}/delete")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public AttackChainNodeExpectation deleteNodeExpectationResult(
      @PathVariable @NotBlank final String expectationId,
      @PathVariable @NotBlank final String sourceId) {
    return attackChainNodeExpectationService.deleteNodeExpectationResult(expectationId, sourceId);
  }

  @Operation(
      summary = "Get Inject Expectations",
      description =
          "Retrieves inject expectations of agents installed on an asset. If an expiration time is provided, it will return all expectations not expired within this timeframe independently of their results. Otherwise, it will return all expectations without any result.")
  @GetMapping(INJECTS_EXPECTATIONS_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<AttackChainNodeExpectation> getAttackChainNodeExpectationsNotFilledAndNotExpired(
      @RequestParam(required = false, name = "expiration_time") final Integer expirationTime) {
    if (expirationTime == null) {
      return Stream.of(
              attackChainNodeExpectationService.manualExpectationsNotFill(),
              attackChainNodeExpectationService.preventionExpectationsNotFill(),
              attackChainNodeExpectationService.detectionExpectationsNotFill())
          .flatMap(List::stream)
          .toList();
    }

    return Stream.of(
            attackChainNodeExpectationService.manualExpectationsNotFillAndNotExpired(expirationTime),
            attackChainNodeExpectationService.preventionExpectationsNotFillAndNotExpired(expirationTime),
            attackChainNodeExpectationService.detectionExpectationsNotFillAndNotExpired(expirationTime))
        .flatMap(List::stream)
        .toList();
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source",
      description =
          "Retrieves inject expectations that have not seen any result yet of agents installed on an asset for a given source ID.")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/{sourceId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<AttackChainNodeExpectation> getAttackChainNodeExpectationsNotFilledForSource(
      @PathVariable String sourceId) {
    return Stream.concat(
            attackChainNodeExpectationService.manualExpectationsNotFill(sourceId).stream(),
            Stream.concat(
                attackChainNodeExpectationService.preventionExpectationsNotFill(sourceId).stream(),
                attackChainNodeExpectationService.detectionExpectationsNotFill(sourceId).stream()))
        .toList();
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID.")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/assets/{sourceId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<AttackChainNodeExpectation> getAttackChainNodeExpectationsAssetsNotFilledAndNotExpiredForSource(
      @PathVariable String sourceId,
      @RequestParam(required = false, name = "expiration_time") final Integer expirationTime) {
    if (expirationTime == null) {
      return Stream.concat(
              attackChainNodeExpectationService.preventionExpectationsNotFill(sourceId).stream(),
              attackChainNodeExpectationService.detectionExpectationsNotFill(sourceId).stream())
          .toList();
    }
    return Stream.concat(
            attackChainNodeExpectationService
                .preventionExpectationsNotFilledAndNotExpired(expirationTime, sourceId)
                .stream(),
            attackChainNodeExpectationService
                .detectionExpectationsNotFilledAndNotExpired(expirationTime, sourceId)
                .stream())
        .toList();
  }

  @GetMapping(INJECTS_EXPECTATIONS_URI + "/prevention")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<AttackChainNodeExpectation> getAttackChainNodePreventionExpectationsNotFilled() {
    return attackChainNodeExpectationService.preventionExpectationsNotFill().stream().toList();
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source and type Prevention",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID and type Prevention.")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/prevention/{sourceId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<AttackChainNodeExpectation> getAttackChainNodePreventionExpectationsNotFilledForSource(
      @PathVariable String sourceId) {
    return attackChainNodeExpectationService.preventionExpectationsNotFill(sourceId).stream().toList();
  }

  @GetMapping(INJECTS_EXPECTATIONS_URI + "/detection")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<AttackChainNodeExpectation> getAttackChainNodeDetectionExpectationsNotFilled() {
    return attackChainNodeExpectationService.detectionExpectationsNotFill().stream().toList();
  }

  @Operation(
      summary = "Get Inject Expectations for a Specific Source and type Detection",
      description =
          "Retrieves inject expectations of agents installed on an asset for a given source ID and type detection.")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/detection/{sourceId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<AttackChainNodeExpectation> getAttackChainNodeDetectionExpectationsNotFilledForSource(
      @PathVariable String sourceId) {
    return attackChainNodeExpectationService.detectionExpectationsNotFill(sourceId).stream().toList();
  }

  @Operation(
      summary = "Update Inject Expectation",
      description = "Update Inject expectation from an external source, e.g., EDR collector.")
  @PutMapping(INJECTS_EXPECTATIONS_URI + "/{expectationId}")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public AttackChainNodeExpectation updateAttackChainNodeExpectation(
      @PathVariable @NotBlank final String expectationId,
      @Valid @RequestBody @NotNull AttackChainNodeExpectationUpdateInput input) {
    return attackChainNodeExpectationService.updateAttackChainNodeExpectation(expectationId, input);
  }

  @Operation(
      summary = "Bulk Update Inject Expectation",
      description = "Bulk Update Inject expectation from an external source, e.g., EDR collector.")
  @PutMapping(INJECTS_EXPECTATIONS_URI + "/bulk")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public void updateAttackChainNodeExpectation(
      @Valid @RequestBody @NotNull AttackChainNodeExpectationBulkUpdateInput inputs) {
    attackChainNodeExpectationService.bulkUpdateAttackChainNodeExpectation(inputs.getInputs());
  }

  @Operation(summary = "Get available expectations for an inject by injector contract id")
  @GetMapping(INJECTS_EXPECTATIONS_URI + "/available")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<Expectation> getAvailableExpectationsForAttackChainNode(
      @RequestParam @NotBlank String nodeContractId) {
    return expectationService.getAvailableExpectationsForAttackChainNode(nodeContractId);
  }
}
