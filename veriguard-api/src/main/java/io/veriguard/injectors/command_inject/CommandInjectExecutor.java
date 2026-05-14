package io.veriguard.injectors.command_inject;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.database.model.ExecutionTrace.getNewSuccessTrace;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.command_inject.model.CommandInjectContent;
import io.veriguard.injectors.command_inject.service.CommandInjectDispatchService;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.service.AttackChainNodeExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * NodeExecutor 子类，处理 Command inject (Task C.11).
 *
 * <p>process() 流程：
 *
 * <ol>
 *   <li>反序列化 inject_content → {@link CommandInjectContent}
 *   <li>校验内容 (executor / content / timeoutSeconds 范围)
 *   <li>选有 command_inject 能力的协作主机 Agent
 *   <li>记 dispatch trace (success: 含 agent id + executor; error: 无 agent / 校验失败)
 *   <li>保存 ManualExpectation
 *   <li>返回 ExecutionProcess(false) — 不等待 Agent 异步回填 (与 web_attack / pcap_replay 对齐)
 * </ol>
 */
@Slf4j
public class CommandInjectExecutor extends NodeExecutor {

  private final CommandInjectDispatchService dispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public CommandInjectExecutor(
      NodeExecutorContext context,
      CommandInjectDispatchService dispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(context);
    this.dispatchService = dispatchService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  /**
   * Protected hook that wraps {@code contentConvert} so tests can spy and inject a stub content.
   *
   * <p>Production code path: delegates straight to the parent {@code contentConvert}.
   */
  protected CommandInjectContent convertContent(ExecutableNode injection) throws Exception {
    return contentConvert(injection, CommandInjectContent.class);
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableNode injection)
      throws Exception {

    CommandInjectContent content;
    try {
      content = convertContent(injection);
    } catch (Exception e) {
      log.warn("Failed to deserialize CommandInjectContent: {}", e.getMessage());
      execution.addTrace(
          getNewErrorTrace(
              "Invalid command_inject content: " + e.getMessage(), ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }

    // 1) Validate
    try {
      dispatchService.validateContent(content);
    } catch (IllegalArgumentException e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }

    // 2) Pick agent
    Optional<Agent> agentOpt = dispatchService.selectAgent();
    if (agentOpt.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "No agent available with capability '"
                  + CommandInjectContract.CAPABILITY_COMMAND_INJECT
                  + "'; deploy a 协作主机 Agent to execute command injects",
              ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }
    Agent agent = agentOpt.get();

    // 3) Save ManualExpectation entries
    List<Expectation> expectations =
        content.getExpectations().stream()
            .flatMap(
                entry ->
                    switch (entry.getType()) {
                      case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                      default -> Stream.of();
                    })
            .toList();
    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
        injection, expectations);

    // 4) Dispatch trace
    execution.addTrace(
        getNewSuccessTrace(
            "Command inject dispatched to agent "
                + agent.getId()
                + " (executor="
                + content.getExecutor()
                + ")",
            ExecutionTraceAction.COMPLETE));

    return new ExecutionProcess(false);
  }
}
