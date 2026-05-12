package io.veriguard.injectors.pcap_replay;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.database.model.ExecutionTrace.getNewSuccessTrace;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
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
 * NodeExecutor 子类，处理 pcap 回放 inject（B-ii PR-D）.
 *
 * <p>process() 流程：
 *
 * <ol>
 *   <li>反序列化 inject_content → PcapReplayContent
 *   <li>校验内容（pcap_file_id + interface + mode + mode/rate 一致性）
 *   <li>选有 pcap_replay 能力的协作主机 Agent
 *   <li>记 dispatch trace（success: 含 agent id + interface + mode；error: 无 agent / 校验失败）
 *   <li>保存 ManualExpectation
 *   <li>返回 ExecutionProcess(false) —— 不等待 Agent 异步回填（本 PR 范围外）
 * </ol>
 */
@Slf4j
public class PcapReplayExecutor extends NodeExecutor {

  private final PcapReplayDispatchService dispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public PcapReplayExecutor(
      NodeExecutorContext context,
      PcapReplayDispatchService dispatchService,
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
  protected PcapReplayContent convertContent(ExecutableNode injection) throws Exception {
    return contentConvert(injection, PcapReplayContent.class);
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableNode injection)
      throws Exception {

    PcapReplayContent content;
    try {
      content = convertContent(injection);
    } catch (Exception e) {
      log.warn("Failed to deserialize PcapReplayContent: {}", e.getMessage());
      execution.addTrace(
          getNewErrorTrace(
              "Invalid pcap_replay content: " + e.getMessage(), ExecutionTraceAction.COMPLETE));
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
                  + PcapReplayContract.CAPABILITY_PCAP_REPLAY
                  + "'; deploy a 协作主机 Agent with tcpreplay to execute pcap replay injects",
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
            "pcap replay dispatched to agent "
                + agent.getId()
                + " (interface="
                + content.getTargetInterface()
                + ", mode="
                + content.getReplayMode()
                + ", pcap="
                + content.getPcapFileId()
                + ")",
            ExecutionTraceAction.COMPLETE));

    return new ExecutionProcess(false);
  }
}
