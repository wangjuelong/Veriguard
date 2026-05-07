package io.veriguard.rest.attack_chain_node;

import static io.veriguard.database.model.ExecutionTraceAction.EXECUTION;
import static io.veriguard.utils.mapper.AttackChainNodeStatusMapper.toExecutionTracesOutput;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import io.veriguard.api.inject_result.dto.AttackChainNodeResultPayloadExecutionOutput;
import io.veriguard.api.inject_result.dto.AttackChainNodeResultPayloadExecutionOutput.AttackChainNodeResultPayloadExecutionOutputBuilder;
import io.veriguard.database.model.Agent;
import io.veriguard.database.model.ExecutionTrace;
import io.veriguard.database.model.AttackChainNodeStatus;
import io.veriguard.database.model.StatusPayload;
import io.veriguard.rest.atomic_testing.form.ExecutionTraceOutput;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeStatusService;
import io.veriguard.utils.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class AttackChainNodeExecutionResultService {

  private final AttackChainNodeService attackChainNodeService;
  private final AttackChainNodeStatusService attackChainNodeStatusService;

  public AttackChainNodeResultPayloadExecutionOutput attackChainNodeExecutionResultPayload(
      @NotBlank final String attackChainNodeId,
      @NotBlank final String targetId,
      @NotNull final TargetType targetType) {
    AttackChainNodeStatus attackChainNodeStatus = this.attackChainNodeStatusService.findAttackChainNodeStatusByAttackChainNodeId(attackChainNodeId);
    AttackChainNodeResultPayloadExecutionOutputBuilder output =
        AttackChainNodeResultPayloadExecutionOutput.builder()
            .payloadCommandBlocks(
                Optional.of(attackChainNodeStatus)
                    .map(AttackChainNodeStatus::getPayloadOutput)
                    .map(StatusPayload::getPayloadCommandBlocks)
                    .orElse(new ArrayList<>()));

    // group traces by agent
    List<ExecutionTrace> traces =
        attackChainNodeService.getAttackChainNodeTracesFromAttackChainNodeAndTarget(attackChainNodeId, targetId, targetType);

    Set<String> agentIds =
        traces.stream()
            .map(ExecutionTrace::getAgent)
            .filter(Objects::nonNull)
            .map(Agent::getId)
            .collect(toSet());

    Map<String, List<ExecutionTraceOutput>> executionByAgent =
        toExecutionTracesOutput(
                traces.stream().filter(t -> EXECUTION.equals(t.getAction())).toList())
            .stream()
            .collect(groupingBy(t -> t.getAgent().getId()));

    Map<String, List<ExecutionTraceOutput>> result = new LinkedHashMap<>();

    agentIds.forEach(
        agentId ->
            result.put(
                agentId, new ArrayList<>(executionByAgent.getOrDefault(agentId, List.of()))));

    output.traces(result);
    return output.build();
  }
}
