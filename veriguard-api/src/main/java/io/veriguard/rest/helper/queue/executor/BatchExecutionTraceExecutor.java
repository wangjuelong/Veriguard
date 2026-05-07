package io.veriguard.rest.helper.queue.executor;

import io.veriguard.rest.inject.form.AttackChainNodeExecutionCallback;
import io.veriguard.rest.inject.service.BatchingAttackChainNodeStatusService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchExecutionTraceExecutor {

  private final BatchingAttackChainNodeStatusService batchingAttackChainNodeStatusService;

  public List<AttackChainNodeExecutionCallback> handleAttackChainNodeExecutionCallbackList(
      List<AttackChainNodeExecutionCallback> attackChainNodeExecutionCallbacks) {
    return batchingAttackChainNodeStatusService.handleAttackChainNodeExecutionCallback(attackChainNodeExecutionCallbacks);
  }
}
