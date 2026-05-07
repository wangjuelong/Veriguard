package io.veriguard.utils.mapper;

import static io.veriguard.utils.mapper.AgentMapper.toAgentOutput;

import io.veriguard.database.model.*;
import io.veriguard.rest.atomic_testing.form.*;
import io.veriguard.rest.inject.output.AttackChainNodeTestStatusOutput;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttackChainNodeStatusMapper {

  private final AgentMapper agentMapper;

  public AttackChainNodeStatusOutput toAttackChainNodeStatusOutput(Optional<AttackChainNodeStatus> attackChainNodeStatus) {
    return attackChainNodeStatus
        .map(
            status ->
                this.<AttackChainNodeStatusOutput>buildAttackChainNodeStatusOutput(
                    AttackChainNodeStatusOutput.builder().build(), status, status.getTraces()))
        .orElseGet(() -> AttackChainNodeStatusOutput.builder().build());
  }

  public AttackChainNodeTestStatusOutput toAttackChainNodeTestStatusOutput(AttackChainNodeTestStatus attackChainNodeTestStatus) {
    AttackChainNodeTestStatusOutput output = AttackChainNodeTestStatusOutput.builder().build();
    buildAttackChainNodeStatusOutput(output, attackChainNodeTestStatus, attackChainNodeTestStatus.getTraces());

    output.setAttackChainNodeId(attackChainNodeTestStatus.getAttackChainNode().getId());
    output.setAttackChainNodeType(
        attackChainNodeTestStatus
            .getAttackChainNode()
            .getNodeContract()
            .map(NodeContract::getNodeExecutor)
            .map(NodeExecutor::getType)
            .orElse(null));
    output.setAttackChainNodeTitle(attackChainNodeTestStatus.getAttackChainNode().getTitle());

    return output;
  }

  private <T extends AttackChainNodeStatusOutput> T buildAttackChainNodeStatusOutput(
      T output, BaseAttackChainNodeStatus status, List<ExecutionTrace> executionTraces) {
    output.setId(status.getId());
    output.setName(status.getName().name());
    output.setTraces(
        toExecutionTracesOutput(
            executionTraces.stream()
                .filter(trace -> trace.getAgent() == null && trace.getIdentifiers().isEmpty())
                .toList()));
    output.setTrackingSentDate(status.getTrackingSentDate());
    output.setTrackingEndDate(status.getTrackingEndDate());
    return output;
  }

  public AttackChainNodeStatusSimple toAttackChainNodeStatusSimple(Optional<AttackChainNodeStatus> attackChainNodeStatus) {
    return attackChainNodeStatus
        .map(
            status ->
                AttackChainNodeStatusSimple.builder()
                    .id(status.getId())
                    .name(status.getName().name())
                    .trackingSentDate(status.getTrackingSentDate())
                    .trackingEndDate(status.getTrackingEndDate())
                    .build())
        .orElseGet(() -> AttackChainNodeStatusSimple.builder().build());
  }

  public static List<ExecutionTraceOutput> toExecutionTracesOutput(List<ExecutionTrace> traces) {
    return traces.stream()
        .map(
            trace ->
                ExecutionTraceOutput.builder()
                    .status(trace.getStatus())
                    .time(trace.getTime())
                    .message(trace.getMessage())
                    .action(trace.getAction())
                    .agent(trace.getAgent() != null ? toAgentOutput(trace.getAgent()) : null)
                    .build())
        .toList();
  }
}
