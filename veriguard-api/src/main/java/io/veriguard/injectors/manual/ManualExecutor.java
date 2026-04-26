package io.veriguard.injectors.manual;

import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTrace;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.execution.ExecutableInject;
import io.veriguard.executors.Injector;
import io.veriguard.executors.InjectorContext;
import io.veriguard.injectors.manual.model.ManualContent;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;

public class ManualExecutor extends Injector {

  private final InjectExpectationService injectExpectationService;

  public ManualExecutor(
      InjectorContext context, final InjectExpectationService injectExpectationService) {
    super(context);
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection)
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

    injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);
    execution.addTrace(
        ExecutionTrace.getNewSuccessTrace(
            "Manual inject execution", ExecutionTraceAction.COMPLETE));
    return new ExecutionProcess(false);
  }
}
