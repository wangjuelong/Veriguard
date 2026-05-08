package io.veriguard.rest.inject_expectation_trace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.Collector;
import io.veriguard.database.model.NodeExpectationTrace;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.CollectorRepository;
import io.veriguard.database.repository.NodeExpectationTraceRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.rest.inject_expectation_trace.form.NodeExpectationTraceBulkInsertInput;
import io.veriguard.rest.inject_expectation_trace.form.NodeExpectationTraceInput;
import io.veriguard.service.NodeExpectationTraceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping(NodeExpectationTraceApi.INJECT_EXPECTATION_TRACES_URI)
@Slf4j
public class NodeExpectationTraceApi extends RestBehavior {

  public static final String INJECT_EXPECTATION_TRACES_URI = "/api/inject-expectations-traces";

  private final NodeExpectationTraceService nodeExpectationTraceService;
  private final NodeExpectationTraceRepository nodeExpectationTraceRepository;
  private final CollectorRepository collectorRepository;

  /**
   * @deprecated since 1.16.0, forRemoval = true
   * @see #bulkInsertNodeExpectationTraceForCollector(NodeExpectationTraceBulkInsertInput)
   */
  @Deprecated(since = "1.16.0", forRemoval = true)
  @Operation(
      summary =
          "Create inject expectation trace for collector. Deprecated since 1.16.0. Replaced by "
              + INJECT_EXPECTATION_TRACES_URI
              + "/bulk")
  @PostMapping()
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public NodeExpectationTrace createNodeExpectationTraceForCollector(
      @Valid @RequestBody NodeExpectationTraceInput input) {

    NodeExpectationTraceBulkInsertInput bulkInput = new NodeExpectationTraceBulkInsertInput();
    bulkInput.setExpectationTraces(List.of(input));

    this.bulkInsertNodeExpectationTraceForCollector(bulkInput);

    Collector collector =
        collectorRepository
            .findById(input.getSourceId())
            .orElseThrow(() -> new ElementNotFoundException("Collector not found"));

    return this.nodeExpectationTraceRepository
        .findByAlertLinkAndAlertNameAndSecurityPlatformAndAttackChainNodeExpectation(
            input.getAlertLink(),
            input.getAlertName(),
            collector.getSecurityPlatform().getId(),
            input.getAttackChainNodeExpectationId());
  }

  /**
   * Bulk insert attackChainNode expectation traces for a collector.
   *
   * @param inputs the list of attackChainNode expectation trace inputs to be inserted
   */
  @Operation(summary = "Bulk insert inject expectation traces")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Inject expectation traces inserted successfully")
      })
  @LogExecutionTime
  @PostMapping("/bulk")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public void bulkInsertNodeExpectationTraceForCollector(
      @Valid @RequestBody @NotNull NodeExpectationTraceBulkInsertInput inputs) {
    if (inputs.getExpectationTraces().isEmpty()) {
      return;
    }
    this.nodeExpectationTraceService.bulkInsertNodeExpectationTraces(inputs.getExpectationTraces());
  }

  @Operation(summary = "Get inject expectation traces from collector")
  @GetMapping()
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<NodeExpectationTrace> getNodeExpectationTracesFromCollector(
      @RequestParam String attackChainNodeExpectationId, @RequestParam String sourceId) {
    try {
      Collector collector =
          collectorRepository
              .findById(sourceId)
              .orElseThrow(() -> new ElementNotFoundException("Collector not found"));
      return this.nodeExpectationTraceService.getNodeExpectationTracesFromCollector(
          attackChainNodeExpectationId, collector.getSecurityPlatform().getId());
    } catch (ElementNotFoundException e) {
      return Collections.emptyList();
    }
  }

  @Operation(summary = "Get inject expectation traces' count")
  @GetMapping("/count")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public long getAlertLinksNumber(
      @RequestParam String attackChainNodeExpectationId,
      @RequestParam String sourceId,
      @RequestParam String expectationResultSourceType) {
    return this.nodeExpectationTraceService.getAlertLinksNumber(
        attackChainNodeExpectationId, sourceId, expectationResultSourceType);
  }
}
