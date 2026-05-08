package io.veriguard.rest.attack_chain_node.service;

import static io.veriguard.utils.ExecutionTraceUtils.convertExecutionAction;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.aop.lock.Lock;
import io.veriguard.aop.lock.LockResourceType;
import io.veriguard.attackchain.execution.NodeRepeatService;
import io.veriguard.database.helper.ExecutionTraceRepositoryHelper;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AgentRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainNodeStatusRepository;
import io.veriguard.integration.ManagerFactory;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionAction;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeExecutionInput;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeUpdateStatusInput;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.utils.AttackChainNodeStatusUtils;
import io.veriguard.utils.AttackChainNodeUtils;
import io.veriguard.utils.ExecutionTraceUtils;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class AttackChainNodeStatusService {

  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AgentRepository agentRepository;
  private final AttackChainNodeService attackChainNodeService;
  private final AttackChainNodeUtils attackChainNodeUtils;
  private final AttackChainNodeStatusRepository attackChainNodeStatusRepository;
  private final ExecutionTraceRepositoryHelper executionTraceRepositoryHelper;

  private final EntityManager entityManager;
  private final ManagerFactory managerFactory;
  private final NodeRepeatService nodeRepeatService;

  public List<AttackChainNodeStatus> findPendingAttackChainNodeStatusByType(
      String attackChainNodeType) {
    return this.attackChainNodeStatusRepository.pendingForAttackChainNodeType(attackChainNodeType);
  }

  public AttackChainNodeStatus findAttackChainNodeStatusByAttackChainNodeId(
      final String attackChainNodeId) {
    if (!hasText(attackChainNodeId)) {
      throw new IllegalArgumentException("InjectId should not be null");
    }
    return this.attackChainNodeStatusRepository
        .findByAttackChainNodeId(attackChainNodeId)
        .orElseThrow(
            () ->
                new ElementNotFoundException("Inject status not found for :" + attackChainNodeId));
  }

  @Transactional(rollbackOn = Exception.class)
  public AttackChainNode updateAttackChainNodeStatus(
      String attackChainNodeId, AttackChainNodeUpdateStatusInput input) {
    AttackChainNode attackChainNode =
        attackChainNodeRepository.findById(attackChainNodeId).orElseThrow();
    // build status
    AttackChainNodeStatus attackChainNodeStatus = new AttackChainNodeStatus();
    attackChainNodeStatus.setAttackChainNode(attackChainNode);
    attackChainNodeStatus.setName(ExecutionStatus.valueOf(input.getStatus()));
    // Save status for attackChainNode
    attackChainNode.setStatus(attackChainNodeStatus);
    return attackChainNodeRepository.save(attackChainNode);
  }

  public void addStartImplantExecutionTraceByAttackChainNode(
      String attackChainNodeId, String agentId, String message, Instant startTime) {
    AttackChainNodeStatus attackChainNodeStatus =
        attackChainNodeStatusRepository
            .findByAttackChainNodeId(attackChainNodeId)
            .orElseThrow(ElementNotFoundException::new);
    Agent agent = agentRepository.findById(agentId).orElseThrow(ElementNotFoundException::new);
    ExecutionTrace trace =
        new ExecutionTrace(
            attackChainNodeStatus,
            ExecutionTraceStatus.INFO,
            null,
            message,
            ExecutionTraceAction.START,
            agent,
            startTime);
    attackChainNodeStatus.addTrace(trace);
    attackChainNodeStatusRepository.save(attackChainNodeStatus);
  }

  public void addJobRetrievalTraces(List<AssetAgentJob> jobs) {
    Map<String, List<AssetAgentJob>> jobsByAttackChainNodeId =
        jobs.stream()
            .filter(j -> j.getAttackChainNode() != null && j.getAgent() != null)
            .collect(Collectors.groupingBy(j -> j.getAttackChainNode().getId()));
    if (jobsByAttackChainNodeId.isEmpty()) {
      return;
    }
    List<AttackChainNodeStatus> statuses =
        attackChainNodeStatusRepository.findAllByAttackChainNodeIdIn(
            jobsByAttackChainNodeId.keySet());
    for (AttackChainNodeStatus status : statuses) {
      for (AssetAgentJob job :
          jobsByAttackChainNodeId.getOrDefault(status.getAttackChainNode().getId(), List.of())) {
        ExecutionTraceUtils.addJobRetrievalTrace(status, job.getAgent());
      }
    }
    attackChainNodeStatusRepository.saveAll(statuses);
  }

  private int getCompleteTrace(AttackChainNode attackChainNode) {
    return attackChainNode
        .getStatus()
        .map(s -> ExecutionTraceUtils.getCompletedAgentIds(s.getTraces()).size())
        .orElse(0);
  }

  public boolean isAllAttackChainNodeAgentsExecuted(AttackChainNode attackChainNode) {
    int totalCompleteTrace = getCompleteTrace(attackChainNode);
    List<Agent> agents = this.attackChainNodeService.getAgentsByAttackChainNode(attackChainNode);
    return agents.size() == totalCompleteTrace;
  }

  public void updateFinalAttackChainNodeStatus(AttackChainNodeStatus attackChainNodeStatus) {
    ExecutionStatus finalStatus =
        AttackChainNodeStatusUtils.computeStatus(
            attackChainNodeStatus.getTraces().stream()
                .filter(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
                .toList());
    attackChainNodeStatus.setTrackingEndDate(Instant.now());
    attackChainNodeStatus.setName(finalStatus);
    attackChainNodeStatus.getAttackChainNode().setUpdatedAt(Instant.now());
    // Phase 4.5：节点结算后让 NodeRepeatPlanner 决定 REPEAT / FINALIZE / FINALIZE_BLOCKED；
    // REPEAT 路径会 orphan-remove 当前 status，让下个调度 tick 重新拉起本节点的下一次迭代。
    nodeRepeatService.handleSettled(attackChainNodeStatus.getAttackChainNode());
  }

  /**
   * Get the execution time from the start trace time and the duration for a specific agent.
   *
   * @param attackChainNodeStatus the AttackChainNodeStatus containing the traces
   * @param agentId the ID of the agent to filter the start trace
   * @param durationInMilis the duration in milliseconds to add to the start trace time
   * @return the calculated execution time as an Instant, or the current time if no start trace is
   *     found
   */
  public Instant getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
      AttackChainNodeStatus attackChainNodeStatus, String agentId, int durationInMilis) {
    return attackChainNodeStatus.getTraces().stream()
        .filter(
            trace ->
                trace.getAction() == ExecutionTraceAction.START
                    && agentId.equals(trace.getAgent().getId()))
        .findFirst()
        .map(startTrace -> startTrace.getTime().plusMillis(durationInMilis))
        .orElse(Instant.now());
  }

  public ExecutionTrace createExecutionTrace(
      AttackChainNodeStatus attackChainNodeStatus,
      AttackChainNodeExecutionInput input,
      Agent agent,
      ObjectNode structuredOutput) {

    // We start by computing the trace date. It should be qual to the START execution trace +
    // input.duration.
    // If the duration is 0 or if there is no START execution trace, we use the current time.
    Instant traceCreationTime;

    boolean noTraces = attackChainNodeStatus.getTraces().isEmpty();
    boolean noDuration = input.getDuration() == 0;

    if (noTraces || noDuration || agent == null) {
      traceCreationTime = Instant.now();
    } else {
      traceCreationTime =
          getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
              attackChainNodeStatus, agent.getId(), input.getDuration());
    }

    ExecutionTraceAction executionAction = convertExecutionAction(input.getAction());
    ExecutionTraceStatus traceStatus = ExecutionTraceStatus.valueOf(input.getStatus());

    ExecutionTrace base =
        new ExecutionTrace(
            attackChainNodeStatus,
            traceStatus,
            null,
            input.getMessage(),
            executionAction,
            agent,
            traceCreationTime);
    return ExecutionTrace.from(base, structuredOutput);
  }

  public void updateAttackChainNodeStatus(
      AttackChainNode attackChainNode,
      Agent agent,
      AttackChainNodeExecutionInput input,
      ObjectNode structuredOutput) {
    AttackChainNodeStatus attackChainNodeStatus =
        attackChainNode.getStatus().orElseThrow(ElementNotFoundException::new);

    // Creating the Execution Trace
    ExecutionTrace executionTrace =
        createExecutionTrace(attackChainNodeStatus, input, agent, structuredOutput);
    // Resolve the placeholder status of the COMPLETE trace
    resolveCompleteTraceStatus(attackChainNodeStatus, executionTrace, agent);
    attackChainNodeStatus.addTrace(executionTrace);
    // Save the trace using a low level call to the database
    String executionTraceId = executionTraceRepositoryHelper.saveExecutionTrace(executionTrace);
    executionTrace.setId(executionTraceId);
    entityManager.merge(attackChainNodeStatus);

    // If the trace is complete
    if (executionTrace.getAction().equals(ExecutionTraceAction.COMPLETE)
        && (agent == null || isAllAttackChainNodeAgentsExecuted(attackChainNode))) {
      // We update the status of the attackChainNode
      updateFinalAttackChainNodeStatus(attackChainNodeStatus);
      executionTraceRepositoryHelper.updateAttackChainNodeUpdateDate(
          attackChainNodeStatus.getAttackChainNode().getId(),
          attackChainNodeStatus.getAttackChainNode().getUpdatedAt());
      executionTraceRepositoryHelper.updateAttackChainNodeStatus(
          attackChainNodeStatus.getId(),
          attackChainNodeStatus.getName().name(),
          attackChainNodeStatus.getTrackingEndDate());
      log.debug("Successfully updated inject final status: {}", attackChainNode.getId());
    }

    log.debug("Successfully updated inject: {}", attackChainNode.getId());
  }

  /**
   * Resolves the status of a COMPLETE trace when the implant sent the default INFO placeholder. The
   * real status is computed from the agent's previous traces (prerequisite, execution, cleanup). If
   * the implant sent an explicit status (not INFO), it is kept as-is.
   */
  protected void resolveCompleteTraceStatus(
      AttackChainNodeStatus attackChainNodeStatus, ExecutionTrace executionTrace, Agent agent) {

    if (agent == null
        || !ExecutionTraceAction.COMPLETE.equals(executionTrace.getAction())
        || !ExecutionTraceStatus.INFO.equals(executionTrace.getStatus())) {
      return;
    }

    ExecutionTraceStatus computedStatus =
        ExecutionTraceUtils.computeAgentTraceStatus(
            attackChainNodeStatus.getTraces().stream()
                .filter(t -> t.getAgent() != null)
                .filter(t -> t.getAgent().getId().equals(agent.getId()))
                .toList());

    if (computedStatus != null) {
      executionTrace.setStatus(computedStatus);
    }
  }

  public AttackChainNodeStatus fromExecution(
      Execution execution, AttackChainNodeStatus attackChainNodeStatus) {
    if (!execution.getTraces().isEmpty()) {
      List<ExecutionTrace> traces =
          execution.getTraces().stream()
              .peek(t -> t.setAttackChainNodeStatus(attackChainNodeStatus))
              .toList();
      attackChainNodeStatus.getTraces().addAll(traces);
    }
    if (execution.isAsync() && ExecutionStatus.EXECUTING.equals(attackChainNodeStatus.getName())) {
      attackChainNodeStatus.setName(ExecutionStatus.PENDING);
    } else {
      updateFinalAttackChainNodeStatus(attackChainNodeStatus);
    }
    return attackChainNodeStatus;
  }

  private AttackChainNodeStatus getOrInitializeAttackChainNodeStatus(
      AttackChainNode attackChainNode) {
    return attackChainNode
        .getStatus()
        .orElseGet(
            () -> {
              AttackChainNodeStatus newStatus = new AttackChainNodeStatus();
              newStatus.setAttackChainNode(attackChainNode);
              newStatus.setTrackingSentDate(Instant.now());
              return newStatus;
            });
  }

  private StatusPayload getPayloadOutput(AttackChainNode attackChainNode) {
    return attackChainNodeUtils.getStatusPayloadFromAttackChainNode(attackChainNode);
  }

  public AttackChainNodeStatus failAttackChainNodeStatus(
      @NotNull String attackChainNodeId, @Nullable String message) {
    AttackChainNode attackChainNode =
        this.attackChainNodeRepository.findById(attackChainNodeId).orElseThrow();
    AttackChainNodeStatus attackChainNodeStatus =
        getOrInitializeAttackChainNodeStatus(attackChainNode);
    if (message != null) {
      attackChainNodeStatus.addErrorTrace(message, ExecutionTraceAction.COMPLETE);
    }
    attackChainNodeStatus.setName(ExecutionStatus.ERROR);
    attackChainNodeStatus.setTrackingEndDate(Instant.now());
    attackChainNodeStatus.setPayloadOutput(getPayloadOutput(attackChainNode));
    return attackChainNodeStatusRepository.save(attackChainNodeStatus);
  }

  @Transactional
  public AttackChainNodeStatus initializeAttackChainNodeStatus(
      @NotNull String attackChainNodeId, @NotNull ExecutionStatus status) {
    AttackChainNode attackChainNode =
        this.attackChainNodeRepository.findById(attackChainNodeId).orElseThrow();
    AttackChainNodeStatus attackChainNodeStatus =
        getOrInitializeAttackChainNodeStatus(attackChainNode);
    attackChainNodeStatus.setName(status);
    attackChainNodeStatus.setTrackingSentDate(Instant.now());
    attackChainNodeStatus.setPayloadOutput(getPayloadOutput(attackChainNode));
    return attackChainNodeStatusRepository.save(attackChainNodeStatus);
  }

  public Iterable<AttackChainNodeStatus> saveAll(
      @NotNull List<AttackChainNodeStatus> attackChainNodeStatuses) {
    return this.attackChainNodeStatusRepository.saveAll(attackChainNodeStatuses);
  }

  public AttackChainNodeStatus save(@NotNull AttackChainNodeStatus attackChainNodeStatus) {
    return this.attackChainNodeStatusRepository.save(attackChainNodeStatus);
  }

  @Lock(type = LockResourceType.INJECT, key = "#attackChainNodeId")
  public void setImplantErrorTrace(String attackChainNodeId, String agentId, String message) {
    if (attackChainNodeId != null
        && !attackChainNodeId.isBlank()
        && agentId != null
        && !agentId.isBlank()) {
      // Create execution traces to inform that the architecture or platform are not compatible with
      // the Veriguard implant
      AttackChainNode attackChainNode =
          attackChainNodeRepository
              .findById(attackChainNodeId)
              .orElseThrow(
                  () -> new ElementNotFoundException("Inject not found: " + attackChainNodeId));
      Agent agent =
          agentRepository
              .findById(agentId)
              .orElseThrow(() -> new ElementNotFoundException("Agent not found: " + agentId));
      AttackChainNodeStatus attackChainNodeStatus =
          attackChainNode
              .getStatus()
              .orElseThrow(() -> new IllegalArgumentException("Status should exist"));
      attackChainNodeStatus.addTrace(
          ExecutionTraceStatus.ERROR, message, ExecutionTraceAction.START, agent);
      attackChainNodeStatusRepository.save(attackChainNodeStatus);
      AttackChainNodeExecutionInput input = new AttackChainNodeExecutionInput();
      input.setMessage("Execution done");
      input.setStatus(ExecutionTraceStatus.INFO.name());
      input.setAction(AttackChainNodeExecutionAction.complete);
      this.updateAttackChainNodeStatus(attackChainNode, agent, input, null);
    }
    throw new IllegalArgumentException(message);
  }

  /**
   * Delete all attackChainNodes statuses for a list of attackChainNodes
   *
   * @param attackChainNodes the list of attackChainNodes
   */
  public void deleteAllAttackChainNodeStatusByAttackChainNodes(
      List<AttackChainNode> attackChainNodes) {
    List<String> attackChainNodeStatusIds =
        attackChainNodes.stream()
            .map(AttackChainNode::getStatus)
            .flatMap(i -> i.map(AttackChainNodeStatus::getId).stream())
            .toList();
    attackChainNodeStatusRepository.deleteAllByIds(attackChainNodeStatusIds);
  }
}
