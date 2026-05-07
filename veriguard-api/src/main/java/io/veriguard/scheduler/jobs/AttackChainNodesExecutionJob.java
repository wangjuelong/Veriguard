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
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.AttackChainEdgesRepository;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.helper.AttackChainNodeHelper;
import io.veriguard.notification.model.NotificationEvent;
import io.veriguard.notification.model.NotificationEventType;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.inject.service.AttackChainNodeService;
import io.veriguard.rest.inject.service.AttackChainNodeStatusService;
import io.veriguard.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.veriguard.service.NotificationEventService;
import io.veriguard.service.SecurityCoverageSendJobService;
import io.veriguard.utils.ExecutionTraceUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.core.env.Environment;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
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
    List<AttackChainRun> attackChainRuns = attackChainRunRepository.findAllShouldBeInRunningState(now());
    if (attackChainRuns.isEmpty()) {
      return;
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
                    != null) // only send notification for attackChainRun associated to a attackChain
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
        attackChainNodeHelper.getAllPendingAttackChainNodesWithThresholdMinutes(this.attackChainNodeExecutionThreshold);

    if (pendingAttackChainNodes.isEmpty()) {
      return;
    }

    for (AttackChainNode attackChainNode : pendingAttackChainNodes) {
      AttackChainNodeStatus status = attackChainNode.getStatus().orElseThrow(ElementNotFoundException::new);
      // Find agents that already have a COMPLETE trace
      Set<String> completedAgentIds = ExecutionTraceUtils.getCompletedAgentIds(status.getTraces());

      // Get all agents expected to execute this attackChainNode
      List<Agent> allAgents = attackChainNodeService.getAgentsByAttackChainNode(attackChainNode);

      // Add a COMPLETE/TIMEOUT trace for each agent that never responded
      for (Agent agent : allAgents) {
        if (!completedAgentIds.contains(agent.getId())) {
          ExecutionTraceUtils.addTimeoutTrace(status, agent, this.attackChainNodeExecutionThreshold);
        }
      }
      attackChainNodeStatusService.updateFinalAttackChainNodeStatus(status);
    }

    attackChainNodeStatusService.saveAll(
        pendingAttackChainNodes.stream()
            .map(attackChainNode -> attackChainNode.getStatus().orElseThrow(ElementNotFoundException::new))
            .collect(Collectors.toList()));
  }

  private void executeAttackChainNode(ExecutableNode executableAttackChainNode) throws Exception {
    // Depending on nodeExecutor type (internal or external) execution must be done differently
    AttackChainNode attackChainNode = executableAttackChainNode.getInjection().getAttackChainNode();
    // We are now checking if we depend on another attackChainNode and if it did not failed
    if (ofNullable(executableAttackChainNode.getAttackChainRunId()).isPresent()) {
      checkErrorMessagesPreExecution(executableAttackChainNode.getAttackChainRunId(), attackChainNode);
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
  protected void checkErrorMessagesPreExecution(String attackChainRunId, AttackChainNode attackChainNode)
      throws ErrorMessagesPreExecutionException {
    List<AttackChainEdge> attackChainEdges =
        attackChainEdgesRepository.findParents(List.of(attackChainNode.getId()));
    if (!attackChainEdges.isEmpty()) {
      List<AttackChainNode> parents =
          attackChainEdges.stream()
              .map(attackChainEdge -> attackChainEdge.getCompositeId().getAttackChainNodeParent())
              .toList();

      Map<String, Boolean> mapCondition =
          getStringBooleanMap(parents, attackChainRunId, attackChainEdges);

      List<String> errorMessages = new ArrayList<>();

      for (AttackChainEdge attackChainEdge : attackChainEdges) {
        List<String> availableKeys =
            new ArrayList<>(
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                            attackChainEdge
                                .getCompositeId()
                                .getAttackChainNodeParent()
                                .getContent()
                                .get("expectations")
                                .elements(),
                            0),
                        false)
                    .map(
                        jsonNode -> {
                          if (jsonNode
                              .get("expectation_type")
                              .asText()
                              .equals(AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL.name())) {
                            return jsonNode.get("expectation_name").asText().toLowerCase();
                          }
                          return jsonNode.get("expectation_type").asText().toLowerCase();
                        })
                    .toList());
        availableKeys.add("execution");

        if (attackChainEdge.getAttackChainEdgeCondition().getConditions().stream()
            .allMatch(condition -> availableKeys.contains(condition.getKey().toLowerCase()))) {
          String expressionToEvaluate = attackChainEdge.getAttackChainEdgeCondition().toString();
          List<String> conditions =
              attackChainEdge.getAttackChainEdgeCondition().getConditions().stream()
                  .map(AttackChainEdgeConditions.Condition::toString)
                  .toList();
          for (String condition : conditions) {
            expressionToEvaluate =
                expressionToEvaluate.replaceAll(
                    condition.split("==")[0].trim(),
                    String.format("#this['%s']", condition.split("==")[0].trim()));
          }

          ExpressionParser parser = new SpelExpressionParser();

          EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
          try {
            Expression exp = parser.parseExpression(expressionToEvaluate);
            boolean canBeExecuted =
                Boolean.TRUE.equals(exp.getValue(context, mapCondition, Boolean.class));
            if (!canBeExecuted) {
              if (errorMessages.isEmpty()) {
                errorMessages.add(
                    "This inject depends on other injects expectations that are not met. The following conditions were not as expected : ");
              }
              errorMessages.addAll(
                  labelFromCondition(
                      attackChainEdge.getCompositeId().getAttackChainNodeParent(),
                      attackChainEdge.getAttackChainEdgeCondition()));
            }

          } catch (EvaluationException | SpelParseException e) {
            log.warn(e.getMessage(), e);
            errorMessages.add(
                "There was an error during the evaluation of the condition of the inject");
          }
        } else {
          log.warn("A key in the conditions didn't match any expectations");
          errorMessages.add("A key in the conditions didn't match any expectations");
        }
      }
      if (!errorMessages.isEmpty()) {
        throw new ErrorMessagesPreExecutionException(errorMessages);
      }
    }
  }

  /**
   * Get a map containing the expectations and if they are met or not
   *
   * @param parents the parents attackChainNodes
   * @param attackChainRunId the id of the attackChainRun
   * @param attackChainEdges the list of dependencies
   * @return a map of expectations and their value
   */
  private @NotNull Map<String, Boolean> getStringBooleanMap(
      List<AttackChainNode> parents, String attackChainRunId, List<AttackChainEdge> attackChainEdges) {
    Map<String, Boolean> mapCondition =
        attackChainEdges.stream()
            .flatMap(
                attackChainEdge ->
                    attackChainEdge.getAttackChainEdgeCondition().getConditions().stream())
            .collect(
                Collectors.toMap(AttackChainEdgeConditions.Condition::getKey, condition -> false));

    parents.forEach(
        parent -> {
          mapCondition.put(
              "Execution",
              parent.getStatus().isPresent()
                  && !ExecutionStatus.ERROR.equals(parent.getStatus().get().getName())
                  && !executionStatusesNotReady.contains(parent.getStatus().get().getName()));

          List<AttackChainNodeExpectation> expectations =
              attackChainNodeExpectationRepository.findAllForAttackChainRunAndAttackChainNode(attackChainRunId, parent.getId());
          expectations.forEach(
              attackChainNodeExpectation -> {
                String name =
                    StringUtils.capitalize(attackChainNodeExpectation.getType().toString().toLowerCase());
                if (attackChainNodeExpectation.getType().equals(AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL)) {
                  name = attackChainNodeExpectation.getName();
                }
                if (AttackChainNodeExpectation.EXPECTATION_TYPE.CHALLENGE.equals(attackChainNodeExpectation.getType())
                    || AttackChainNodeExpectation.EXPECTATION_TYPE.ARTICLE.equals(
                        attackChainNodeExpectation.getType())) {
                  if (attackChainNodeExpectation.getUser() == null && attackChainNodeExpectation.getScore() != null) {
                    mapCondition.put(
                        name, attackChainNodeExpectation.getScore() >= attackChainNodeExpectation.getExpectedScore());
                  }
                } else {
                  mapCondition.put(
                      name, expectationStatusesSuccess.contains(attackChainNodeExpectation.getResponse()));
                }
              });
        });
    return mapCondition;
  }

  private List<String> labelFromCondition(
      AttackChainNode attackChainNodeParent, AttackChainEdgeConditions.AttackChainEdgeCondition condition) {
    List<String> result = new ArrayList<>();
    for (AttackChainEdgeConditions.Condition conditionElement : condition.getConditions()) {
      result.add(
          String.format(
              "Inject '%s' - %s is %s",
              attackChainNodeParent.getTitle(), conditionElement.getKey(), conditionElement.isValue()));
    }
    return result;
  }

  public void updateAttackChainRun(String attackChainRunId) {
    AttackChainRun attackChainRun = attackChainRunRepository.findById(attackChainRunId).orElseThrow();
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

      // We're grouping the attackChainNodes to run by attackChainRuns but also making sure no attackChainNodes
      // run in the same batch as it's parents
      Map<String, List<ExecutableNode>> byAttackChainRuns =
          attackChainNodes.stream()
              .filter(
                  executableAttackChainNode ->
                      // If we got dependencies, we check that the parents are not part of the
                      // current batch of attackChainNodes running. If so, we're filtering them out and
                      // they'll be part of the next batch of launched attackChainNodes. Do note that this is
                      // an edge case as it's not allowed to add a dependency less than a minute
                      // after a parent but can happen if the platform was restarted after some time
                      // out. It'll then start the attackChainNodes that were not started because the
                      // platform was down.
                      executableAttackChainNode.getInjection().getAttackChainNode().getDependsOn() == null
                          || !intersect(
                              attackChainNodes.stream()
                                  .map(execAttackChainNode -> execAttackChainNode.getInjection().getId())
                                  .toList(),
                              executableAttackChainNode.getInjection().getAttackChainNode().getDependsOn().stream()
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
                            AttackChainNode attackChainNode = executableAttackChainNode.getInjection().getAttackChainNode();
                            log.warn(e.getMessage(), e);
                            attackChainNodeStatusService.failAttackChainNodeStatus(attackChainNode.getId(), e.getMessage());
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
            attackChainNode.getExpectations().stream().flatMap(ie -> ie.getResults().stream()).toList();
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
