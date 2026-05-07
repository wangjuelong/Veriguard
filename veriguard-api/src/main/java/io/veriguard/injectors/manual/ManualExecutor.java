package io.veriguard.injectors.manual;

import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTrace;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.manual.model.ManualContent;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.service.AttackChainNodeExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;

public class ManualExecutor extends NodeExecutor {

  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public ManualExecutor(
      NodeExecutorContext context, final AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(context);
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableNode injection)
      throws Exception {

    ManualContent content = contentConvert(injection, ManualContent.class);

    List<Expectation> expectations =
        content.getExpectations().stream()
            .flatMap(
                (entry) ->
                    switch (entry.getType()) {
                      case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                      default -> Stream.of();
                    })
            .toList();

    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(injection, expectations);
    execution.addTrace(
        ExecutionTrace.getNewSuccessTrace(
            "Manual inject execution", ExecutionTraceAction.COMPLETE));
    return new ExecutionProcess(false);
  }
}
