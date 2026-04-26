package io.veriguard.rest.helper.queue.executor;

import io.veriguard.rest.inject.form.InjectExecutionCallback;
import io.veriguard.rest.inject.service.BatchingInjectStatusService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchExecutionTraceExecutor {

  private final BatchingInjectStatusService batchingInjectStatusService;

  public List<InjectExecutionCallback> handleInjectExecutionCallbackList(
      List<InjectExecutionCallback> injectExecutionCallbacks) {
    return batchingInjectStatusService.handleInjectExecutionCallback(injectExecutionCallbacks);
  }
}
