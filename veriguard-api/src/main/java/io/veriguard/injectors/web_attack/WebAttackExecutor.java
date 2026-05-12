package io.veriguard.injectors.web_attack;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.database.model.ExecutionTrace.getNewSuccessTrace;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
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
 * NodeExecutor 子类，处理 Web 攻击包 inject（B-ii PR-C）.
 *
 * <p>process() 流程：
 *
 * <ol>
 *   <li>反序列化 inject_content → WebAttackContent
 *   <li>校验内容（method 合法 + url 非空）
 *   <li>选有 http_attack 能力的协作主机 Agent
 *   <li>记 dispatch trace（success: 含 agent id + url；error: 无 agent / 校验失败）
 *   <li>保存 ManualExpectation
 *   <li>返回 ExecutionProcess(false) —— 不等待 Agent 异步回填（本 PR 范围外）
 * </ol>
 */
@Slf4j
public class WebAttackExecutor extends NodeExecutor {

  private final WebAttackDispatchService dispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public WebAttackExecutor(
      NodeExecutorContext context,
      WebAttackDispatchService dispatchService,
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
  protected WebAttackContent convertContent(ExecutableNode injection) throws Exception {
    return contentConvert(injection, WebAttackContent.class);
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableNode injection)
      throws Exception {

    WebAttackContent content;
    try {
      content = convertContent(injection);
    } catch (Exception e) {
      log.warn("Failed to deserialize WebAttackContent: {}", e.getMessage());
      execution.addTrace(
          getNewErrorTrace(
              "Invalid web_attack content: " + e.getMessage(), ExecutionTraceAction.COMPLETE));
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
                  + WebAttackContract.CAPABILITY_HTTP_ATTACK
                  + "'; deploy a 协作主机 Agent to execute web attack injects",
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
            "Web attack dispatched to agent "
                + agent.getId()
                + " ("
                + content.getMethod()
                + " "
                + content.getUrl()
                + ")",
            ExecutionTraceAction.COMPLETE));

    return new ExecutionProcess(false);
  }
}
