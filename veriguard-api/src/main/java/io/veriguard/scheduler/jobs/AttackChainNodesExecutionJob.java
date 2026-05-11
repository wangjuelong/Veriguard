package io.veriguard.scheduler.jobs;

import static io.veriguard.database.model.CollectExecutionStatus.COMPLETED;
import static io.veriguard.utils.inject_expectation_result.ExpectationResultBuilder.hasValidResults;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.veriguard.aop.LogExecutionTime;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainEdgesRepository;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.helper.AttackChainNodeHelper;
import io.veriguard.notification.model.NotificationEvent;
import io.veriguard.notification.model.NotificationEventType;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeStatusService;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.veriguard.service.NotificationEventService;
import io.veriguard.service.SecurityCoverageSendJobService;
import io.veriguard.service.attack_chain.AttackChainService;
import io.veriguard.utils.ExecutionTraceUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class AttackChainNodesExecutionJob implements Job {

  public static final String DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES = "10";
  private static final long delayForSimulationCompletedEvent = 3600L;

  private final Environment env;
  private int attackChainNodeExecutionThreshold;

  private final AttackChainNodeHelper attackChainNodeHelper;
  private final AttackChainNodeService attackChainNodeService;
  private final AttackChainRunRepository attackChainRunRepository;
  private final AttackChainEdgesRepository attackChainEdgesRepository;
  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  private final AttackChainNodeStatusService attackChainNodeStatusService;
  private final io.veriguard.executors.Executor executor;
  private final NotificationEventService notificationEventService;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;
  private final io.veriguard.attackchain.execution.LinkVerdictCalculator linkVerdictCalculator;
  private final io.veriguard.attackchain.execution.LinkExpectationService linkExpectationService;
  private final io.veriguard.attackchain.execution.EdgeConditionEvaluator edgeConditionEvaluator;
  private final io.veriguard.database.repository.AttackChainLinkExpectationRepository
      attackChainLinkExpectationRepository;
  // Phase 12c-Biii: 动态节点持久化 + cleanup
  private final AttackChainService attackChainService;
  private final AttackChainNodeRepository attackChainNodeRepository;

  private final List<ExecutionStatus> executionStatusesNotReady =
      List.of(
          ExecutionStatus.QUEUING,
          ExecutionStatus.DRAFT,
          ExecutionStatus.EXECUTING,
          ExecutionStatus.PENDING);

  private final List<AttackChainNodeExpectation.EXPECTATION_STATUS> expectationStatusesSuccess =
      List.of(AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS);

  @Resource protected ObjectMapper mapper;

  @PostConstruct
  private void init() {
    String threshold = env.getProperty("inject.execution.threshold.minutes");
    if (threshold == null || threshold.isBlank()) {
      threshold = DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES;
    }
    this.attackChainNodeExecutionThreshold = Integer.parseInt(threshold);
  }

  public void handleAutoStartAttackChainRuns() {
    List<AttackChainRun> attackChainRuns =
        attackChainRunRepository.findAllShouldBeInRunningState(now());
    if (attackChainRuns.isEmpty()) {
      return;
    }

    // Phase 12c-Biii: 转 RUNNING 前为每个 starting run 派生 + 持久化动态节点.
    // computeDynamicContracts short-circuit 空 filter → 不 save 任何节点.
    // 收集后批量 saveAll，避免 N+1 round-trip（与 attackChainRunRepository.saveAll 同模式）.
    List<AttackChainNode> dynamicNodesToSave = new ArrayList<>();
    for (AttackChainRun attackChainRun : attackChainRuns) {
      AttackChain chain = attackChainRun.getAttackChain();
      if (chain == null) {
        continue;
      }
      List<NodeContract> dynamicContracts = attackChainService.computeDynamicContracts(chain);
      for (NodeContract contract : dynamicContracts) {
        AttackChainNode node = new AttackChainNode();
        node.setId("dynamic-" + contract.getId() + "-" + attackChainRun.getId());
        node.setDynamic(true);
        node.setAttackChain(chain);
        node.setNodeContract(contract);
        node.setDependsDuration(0L);
        node.setRepeatCount(1);
        node.setTitle("动态: " + safeContractLabel(contract));
        dynamicNodesToSave.add(node);
      }
    }
    if (!dynamicNodesToSave.isEmpty()) {
      attackChainNodeRepository.saveAll(dynamicNodesToSave);
    }

    attackChainRunRepository.saveAll(
        attackChainRuns.stream()
            .peek(
                attackChainRun -> {
                  attackChainRun.setStatus(AttackChainRunStatus.RUNNING);
                  attackChainRun.setUpdatedAt(now());
                })
            .toList());
  }

  private String safeContractLabel(NodeContract contract) {
    if (contract.getLabels() != null && contract.getLabels().get("en") != null) {
      return contract.getLabels().get("en");
    }
    return contract.getId();
  }

  public void handleAutoClosingAttackChainRuns() {
    // Change status of finished attackChainRuns.
    List<AttackChainRun> mustBeFinishedSimulations = attackChainRunRepository.thatMustBeFinished();
    List<AttackChainRun> attackChainRunsFinished =
        attackChainRunRepository.saveAll(
            mustBeFinishedSimulations.stream()
                .peek(
                    attackChainRun -> {
                      attackChainRun.setStatus(AttackChainRunStatus.FINISHED);
                      attackChainRun.setEnd(now());
                      attackChainRun.setUpdatedAt(now());
                      // Phase 12c-Biii: cleanup 本 chain 的所有动态节点（is_dynamic=true）.
                      // 手动节点不受影响. 按 chain.id 而非 run.id，因动态节点 FK 关联 chain.
                      if (attackChainRun.getAttackChain() != null) {
                        int deleted =
                            attackChainNodeRepository.deleteByAttackChainIdAndIsDynamicTrue(
                                attackChainRun.getAttackChain().getId());
                        if (deleted > 0) {
                          log.debug(
                              "Cleaned up {} dynamic nodes for chain {} run {}",
                              deleted,
                              attackChainRun.getAttackChain().getId(),
                              attackChainRun.getId());
                        }
                      }
                      // Phase 7: 链路终态前先物化 + 评估链路级 SOC expectation
                      linkExpectationService.instantiateForRun(attackChainRun);
                      linkExpectationService.evaluateForRun(attackChainRun);
                      // Phase 5 + 7: verdict 同时纳入节点级 + 链路级 expectation
                      io.veriguard.attackchain.execution.LinkVerdictCalculator.LinkVerdictResult
                          verdict =
                              linkVerdictCalculator.compute(
                                  attackChainRun,
                                  attackChainLinkExpectationRepository.findByAttackChainRunId(
                                      attackChainRun.getId()));
                      attackChainRun.setVerdictPrevention(verdict.prevention());
                      attackChainRun.setVerdictDetection(verdict.detection());
                      attackChainRun.setVerdictComputedAt(now());
                    })
                .toList());

    // maybe trigger stix coverage background job
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(
        attackChainRunsFinished);

    // send notification
    attackChainRunsFinished.stream()
        .filter(
            ex ->
                ex.getAttackChain()
                    != null) // only send notification for attackChainRun associated to a
        // attackChain
        .forEach(
            ex ->
                notificationEventService.sendNotificationEventWithDelay(
                    NotificationEvent.builder()
                        .eventType(NotificationEventType.SIMULATION_COMPLETED)
                        .resourceType(NotificationRuleResourceType.SCENARIO)
                        .resourceId(ex.getAttackChain().getId())
                        .timestamp(Instant.now())
                        .build(),
                    delayForSimulationCompletedEvent));
  }

  public void handlePendingAttackChainNode() {
    List<AttackChainNode> pendingAttackChainNodes =
        attackChainNodeHelper.getAllPendingAttackChainNodesWithThresholdMinutes(
            this.attackChainNodeExecutionThreshold);

    if (pendingAttackChainNodes.isEmpty()) {
      return;
    }

    for (AttackChainNode attackChainNode : pendingAttackChainNodes) {
      AttackChainNodeStatus status =
          attackChainNode.getStatus().orElseThrow(ElementNotFoundException::new);
      // Find agents that already have a COMPLETE trace
      Set<String> completedAgentIds = ExecutionTraceUtils.getCompletedAgentIds(status.getTraces());

      // Get all agents expected to execute this attackChainNode
      List<Agent> allAgents = attackChainNodeService.getAgentsByAttackChainNode(attackChainNode);

      // Add a COMPLETE/TIMEOUT trace for each agent that never responded
      for (Agent agent : allAgents) {
        if (!completedAgentIds.contains(agent.getId())) {
          ExecutionTraceUtils.addTimeoutTrace(
              status, agent, this.attackChainNodeExecutionThreshold);
        }
      }
      attackChainNodeStatusService.updateFinalAttackChainNodeStatus(status);
    }

    // Phase 4.5：updateFinalAttackChainNodeStatus 可能触发 REPEAT，把 status orphan-remove；
    // 已被移除的节点 getStatus() 为 empty，过滤掉避免 orElseThrow 误报。
    attackChainNodeStatusService.saveAll(
        pendingAttackChainNodes.stream()
            .map(AttackChainNode::getStatus)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(Collectors.toList()));
  }

  private void executeAttackChainNode(ExecutableNode executableAttackChainNode) throws Exception {
    // Depending on nodeExecutor type (internal or external) execution must be done differently
    AttackChainNode attackChainNode = executableAttackChainNode.getInjection().getAttackChainNode();
    // We are now checking if we depend on another attackChainNode and if it did not failed
    if (ofNullable(executableAttackChainNode.getAttackChainRunId()).isPresent()) {
      checkErrorMessagesPreExecution(
          executableAttackChainNode.getAttackChainRunId(), attackChainNode);
    }
    if (!attackChainNode.isReady()) {
      throw new UnsupportedOperationException(
          "The inject is not ready to be executed (missing mandatory fields)");
    }
    log.info("Executing inject {}", attackChainNode.getAttackChainNode().getTitle());
    this.executor.execute(executableAttackChainNode);
  }

  /**
   * Get error messages if pre execution conditions are not met
   *
   * @param attackChainRunId the id of the attackChainRun
   * @param attackChainNode the attackChainNode to check
   */
  @VisibleForTesting
  protected void checkErrorMessagesPreExecution(
      String attackChainRunId, AttackChainNode attackChainNode)
      throws ErrorMessagesPreExecutionException {
    List<AttackChainEdge> attackChainEdges =
        attackChainEdgesRepository.findParents(List.of(attackChainNode.getId()));
    if (attackChainEdges.isEmpty()) {
      return;
    }

    List<String> errorMessages = new ArrayList<>();
    for (AttackChainEdge attackChainEdge : attackChainEdges) {
      AttackChainNode parent = attackChainEdge.getCompositeId().getAttackChainNodeParent();

      // 父节点尚未执行完（QUEUING / DRAFT / EXECUTING / PENDING）→ 视作 PENDING，
      // 任何 typed eq 条件都返回 false（matchesGroup(null, ...) 短路）。
      if (!isParentExecutionSettled(parent)) {
        if (errorMessages.isEmpty()) {
          errorMessages.add(
              "This inject depends on other injects expectations that are not met. The following conditions were not as expected : ");
        }
        errorMessages.addAll(
            labelFromCondition(parent, attackChainEdge.getAttackChainEdgeCondition()));
        continue;
      }

      io.veriguard.attackchain.execution.NodeFinalStatus parentStatus =
          computeParentFinalStatus(parent, attackChainRunId);

      boolean canBeExecuted =
          edgeConditionEvaluator.evaluate(
              attackChainEdge.getAttackChainEdgeCondition(), parentStatus);
      if (!canBeExecuted) {
        if (errorMessages.isEmpty()) {
          errorMessages.add(
              "This inject depends on other injects expectations that are not met. The following conditions were not as expected : ");
        }
        errorMessages.addAll(
            labelFromCondition(parent, attackChainEdge.getAttackChainEdgeCondition()));
      }
    }
    if (!errorMessages.isEmpty()) {
      throw new ErrorMessagesPreExecutionException(errorMessages);
    }
  }

  /** 父节点是否已结束执行（任何非 not-ready 状态都视作 settled，包括 ERROR / FINISHED）. */
  private boolean isParentExecutionSettled(AttackChainNode parent) {
    return parent.getStatus().isPresent()
        && !executionStatusesNotReady.contains(parent.getStatus().get().getName());
  }

  /**
   * 把父节点的多个 expectation 聚合成 {@link io.veriguard.attackchain.execution.NodeFinalStatus} ——
   * 每个维度（PREVENTION / DETECTION / MANUAL）一个 EXPECTATION_STATUS：
   *
   * <ul>
   *   <li>无该维度 expectation → null（视作未结算）
   *   <li>所有 expectation 都 SUCCESS → SUCCESS
   *   <li>所有都 FAILED → FAILED
   *   <li>任一 PENDING / UNKNOWN → PENDING
   *   <li>其余（混合 SUCCESS/FAILED 或含 PARTIAL）→ PARTIAL
   * </ul>
   *
   * <p>评估器的 SETTLED group 会接受 SUCCESS / FAILED / PARTIAL；ANY_SUCCESS / ALL_SUCCESS 只在 SUCCESS 通过；
   * ANY_FAILED / ALL_FAILED 只在 FAILED 通过 —— 与节点级单值聚合后语义一致。
   */
  private io.veriguard.attackchain.execution.NodeFinalStatus computeParentFinalStatus(
      AttackChainNode parent, String attackChainRunId) {
    List<AttackChainNodeExpectation> expectations =
        attackChainNodeExpectationRepository.findAllForAttackChainRunAndAttackChainNode(
            attackChainRunId, parent.getId());
    return new io.veriguard.attackchain.execution.NodeFinalStatus(
        aggregateDimension(expectations, AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION),
        aggregateDimension(expectations, AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION),
        aggregateDimension(expectations, AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL));
  }

  private AttackChainNodeExpectation.EXPECTATION_STATUS aggregateDimension(
      List<AttackChainNodeExpectation> expectations,
      AttackChainNodeExpectation.EXPECTATION_TYPE dimension) {
    List<AttackChainNodeExpectation.EXPECTATION_STATUS> statuses =
        expectations.stream()
            .filter(e -> dimension.equals(e.getType()))
            .map(AttackChainNodeExpectation::getResponse)
            .toList();
    if (statuses.isEmpty()) {
      return null;
    }
    boolean anyPending =
        statuses.stream()
            .anyMatch(
                s ->
                    s == null
                        || s == AttackChainNodeExpectation.EXPECTATION_STATUS.PENDING
                        || s == AttackChainNodeExpectation.EXPECTATION_STATUS.UNKNOWN);
    if (anyPending) {
      return AttackChainNodeExpectation.EXPECTATION_STATUS.PENDING;
    }
    boolean allSuccess =
        statuses.stream().allMatch(s -> s == AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS);
    if (allSuccess) {
      return AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS;
    }
    boolean allFailed =
        statuses.stream().allMatch(s -> s == AttackChainNodeExpectation.EXPECTATION_STATUS.FAILED);
    if (allFailed) {
      return AttackChainNodeExpectation.EXPECTATION_STATUS.FAILED;
    }
    return AttackChainNodeExpectation.EXPECTATION_STATUS.PARTIAL;
  }

  private List<String> labelFromCondition(
      AttackChainNode attackChainNodeParent, EdgeCondition condition) {
    List<String> result = new ArrayList<>();
    if (condition == null) {
      return result;
    }
    appendLabels(condition, attackChainNodeParent.getTitle(), result);
    return result;
  }

  private void appendLabels(EdgeCondition condition, String parentTitle, List<String> sink) {
    switch (condition) {
      case EdgeCondition.Eq eq ->
          sink.add(
              String.format("Inject '%s' - %s is %s", parentTitle, eq.dimension(), eq.status()));
      case EdgeCondition.And and -> {
        if (and.children() != null) {
          and.children().forEach(c -> appendLabels(c, parentTitle, sink));
        }
      }
      case EdgeCondition.Or or -> {
        if (or.children() != null) {
          or.children().forEach(c -> appendLabels(c, parentTitle, sink));
        }
      }
    }
  }

  public void updateAttackChainRun(String attackChainRunId) {
    AttackChainRun attackChainRun =
        attackChainRunRepository.findById(attackChainRunId).orElseThrow();
    attackChainRun.setUpdatedAt(now());
    attackChainRunRepository.save(attackChainRun);
  }

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    try {
      // Handle starting attackChainRuns if needed.
      handleAutoStartAttackChainRuns();
      // Get all attackChainNodes to execute grouped by attackChainRun.
      List<ExecutableNode> attackChainNodes = attackChainNodeHelper.getAttackChainNodesToRun();

      // We're grouping the attackChainNodes to run by attackChainRuns but also making sure no
      // attackChainNodes
      // run in the same batch as it's parents
      Map<String, List<ExecutableNode>> byAttackChainRuns =
          attackChainNodes.stream()
              .filter(
                  executableAttackChainNode ->
                      // If we got dependencies, we check that the parents are not part of the
                      // current batch of attackChainNodes running. If so, we're filtering them out
                      // and
                      // they'll be part of the next batch of launched attackChainNodes. Do note
                      // that this is
                      // an edge case as it's not allowed to add a dependency less than a minute
                      // after a parent but can happen if the platform was restarted after some time
                      // out. It'll then start the attackChainNodes that were not started because
                      // the
                      // platform was down.
                      executableAttackChainNode.getInjection().getAttackChainNode().getDependsOn()
                              == null
                          || !intersect(
                              attackChainNodes.stream()
                                  .map(
                                      execAttackChainNode ->
                                          execAttackChainNode.getInjection().getId())
                                  .toList(),
                              executableAttackChainNode
                                  .getInjection()
                                  .getAttackChainNode()
                                  .getDependsOn()
                                  .stream()
                                  .map(
                                      attackChainEdge ->
                                          attackChainEdge
                                              .getCompositeId()
                                              .getAttackChainNodeParent()
                                              .getAttackChainNode()
                                              .getId())
                                  .toList()))
              .collect(
                  groupingBy(
                      ex ->
                          ex.getInjection().getAttackChainRun() == null
                              ? "atomic"
                              : ex.getInjection().getAttackChainRun().getId()));

      // Execute attackChainNodes in parallel for each attackChainRun.
      byAttackChainRuns.entrySet().parallelStream()
          .forEach(
              (entry) -> {
                // Execute each attackChainNode for the attackChainRun in order.
                entry.getValue().parallelStream()
                    .forEach(
                        executableAttackChainNode -> {
                          try {
                            this.executeAttackChainNode(executableAttackChainNode);
                          } catch (Exception e) {
                            AttackChainNode attackChainNode =
                                executableAttackChainNode.getInjection().getAttackChainNode();
                            log.warn(e.getMessage(), e);
                            attackChainNodeStatusService.failAttackChainNodeStatus(
                                attackChainNode.getId(), e.getMessage());
                          }
                        });
                // Update the attackChainRun
                if (!entry.getKey().equals("atomic")) {
                  updateAttackChainRun(entry.getKey());
                }
              });
      // Change status of finished attackChainRuns.
      handleAttackChainNodeExpectationCollectStatus();
      handleAutoClosingAttackChainRuns();
      handlePendingAttackChainNode();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }

  private void handleAttackChainNodeExpectationCollectStatus() {
    List<AttackChainNode> attackChainNodes = attackChainNodeService.getExecutedAndNotFinished();
    if (attackChainNodes.isEmpty()) {
      return;
    }
    List<AttackChainNode> fulfilled = new ArrayList<>();
    for (AttackChainNode attackChainNode : attackChainNodes) {
      if (attackChainNode.getExpectations().isEmpty()) {
        attackChainNode.setCollectExecutionStatus(COMPLETED);
        fulfilled.add(attackChainNode);
      } else {
        List<NodeExpectationResult> results =
            attackChainNode.getExpectations().stream()
                .flatMap(ie -> ie.getResults().stream())
                .toList();
        if (hasValidResults(results)) {
          attackChainNode.setCollectExecutionStatus(COMPLETED);
          fulfilled.add(attackChainNode);
        }
      }
    }
    attackChainNodeService.saveAll(fulfilled);
  }

  /**
   * Return true if some elements are in the two lists
   *
   * @param firstList the first list to test
   * @param secondList the second list to test
   * @return true if some elements are present in both the lists
   */
  private boolean intersect(List<String> firstList, List<String> secondList) {
    return !firstList.stream()
        .distinct()
        .filter(secondList::contains)
        .collect(Collectors.toSet())
        .isEmpty();
  }
}
