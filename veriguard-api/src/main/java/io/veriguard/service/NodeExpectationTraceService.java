package io.veriguard.service;

import static io.veriguard.service.AttackChainNodeExpectationService.COLLECTOR;

import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.Collector;
import io.veriguard.database.model.NodeExpectationTrace;
import io.veriguard.database.model.SecurityPlatform;
import io.veriguard.database.raw.impl.SimpleRawExpectationTrace;
import io.veriguard.database.repository.CollectorRepository;
import io.veriguard.database.repository.NodeExpectationTraceRepository;
import io.veriguard.database.repository.SecurityPlatformRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.inject_expectation_trace.form.NodeExpectationTraceInput;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NodeExpectationTraceService {

  private final NodeExpectationTraceRepository nodeExpectationTraceRepository;
  private final SecurityPlatformRepository securityPlatformRepository;
  private final CollectorRepository collectorRepository;

  public List<NodeExpectationTrace> getNodeExpectationTracesFromCollector(
      @NotNull String attackChainNodeExpectationId, @NotNull String sourceId) {
    return this.nodeExpectationTraceRepository.findByExpectationAndSecurityPlatform(
        attackChainNodeExpectationId, sourceId);
  }

  public long getAlertLinksNumber(
      @NotNull String attackChainNodeExpectationId,
      @NotNull String sourceId,
      String expectationResultSourceType) {
    if (expectationResultSourceType.equalsIgnoreCase(COLLECTOR)) {
      SecurityPlatform securityPlatform =
          securityPlatformRepository
              .findByExternalReference(sourceId)
              .orElseThrow(() -> new ElementNotFoundException("Security platform not found"));
      return this.nodeExpectationTraceRepository.countAlerts(
          attackChainNodeExpectationId, securityPlatform.getId());
    } else {
      return this.nodeExpectationTraceRepository.countAlerts(
          attackChainNodeExpectationId, sourceId);
    }
  }

  @Transactional(rollbackFor = Exception.class)
  public void bulkInsertNodeExpectationTraces(
      @NotNull List<NodeExpectationTraceInput> nodeExpectationTraces) {
    if (nodeExpectationTraces.isEmpty()) {
      return;
    }
    // We start by deduplicating the data, to avoid duplicates in the database
    // Convert the input list to NodeExpectationTrace objects and extract oldest trace's date
    // Start by getting the collector. We can take the first one since they are all the same
    Collector collector =
        collectorRepository
            .findById(nodeExpectationTraces.getFirst().getSourceId())
            .orElseThrow(() -> new ElementNotFoundException("Collector not found"));
    Map<SimpleRawExpectationTrace, NodeExpectationTrace> traces = new HashMap<>();
    nodeExpectationTraces.forEach(
        input -> {
          // Convert input to NodeExpectationTrace
          NodeExpectationTrace trace = new NodeExpectationTrace();
          trace.setUpdateAttributes(input);
          trace.setSecurityPlatform(collector.getSecurityPlatform());
          // We don't need to fetch the actual expectation here, we can just set the id as there is
          // no cascade
          trace.setAttackChainNodeExpectation(new AttackChainNodeExpectation());
          trace.getAttackChainNodeExpectation().setId(input.getAttackChainNodeExpectationId());

          SimpleRawExpectationTrace simpleTrace = SimpleRawExpectationTrace.of(trace);

          traces.computeIfAbsent(simpleTrace, k -> trace);
        });

    // Save the remaining traces
    for (NodeExpectationTrace trace : traces.values()) {
      this.nodeExpectationTraceRepository.insertIfNotExists(
          UUID.randomUUID().toString(),
          trace.getAttackChainNodeExpectation().getId(),
          trace.getSecurityPlatform().getId(),
          trace.getAlertLink(),
          trace.getAlertName(),
          trace.getAlertDate(),
          trace.getCreatedAt(),
          trace.getUpdatedAt());
    }
  }
}
