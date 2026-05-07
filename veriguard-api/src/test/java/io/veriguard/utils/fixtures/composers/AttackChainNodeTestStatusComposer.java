package io.veriguard.utils.fixtures.composers;

import io.veriguard.database.model.AttackChainNodeTestStatus;
import io.veriguard.database.repository.AttackChainNodeTestStatusRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttackChainNodeTestStatusComposer extends ComposerBase<AttackChainNodeTestStatus> {

  @Autowired private AttackChainNodeTestStatusRepository attackChainNodeTestStatusRepository;

  public class Composer extends InnerComposerBase<AttackChainNodeTestStatus> {

    private final AttackChainNodeTestStatus AttackChainNodeTestStatus;
    private final List<ExecutionTraceComposer.Composer> executionTracesComposer = new ArrayList<>();

    public Composer(AttackChainNodeTestStatus AttackChainNodeTestStatus) {
      this.AttackChainNodeTestStatus = AttackChainNodeTestStatus;
    }

    public Composer withExecutionTraces(List<ExecutionTraceComposer.Composer> traces) {
      traces.forEach(trace -> withExecutionTrace(trace));
      return this;
    }

    public Composer withExecutionTrace(ExecutionTraceComposer.Composer executionTrace) {
      executionTracesComposer.add(executionTrace);
      executionTrace.get().setAttackChainNodeTestStatus(this.AttackChainNodeTestStatus);
      this.AttackChainNodeTestStatus.getTraces().add(executionTrace.get());
      return this;
    }

    @Override
    public AttackChainNodeTestStatusComposer.Composer persist() {
      attackChainNodeTestStatusRepository.save(AttackChainNodeTestStatus);
      executionTracesComposer.forEach(ExecutionTraceComposer.Composer::persist);
      return this;
    }

    @Override
    public AttackChainNodeTestStatusComposer.Composer delete() {
      executionTracesComposer.forEach(ExecutionTraceComposer.Composer::delete);
      attackChainNodeTestStatusRepository.delete(AttackChainNodeTestStatus);
      return this;
    }

    @Override
    public AttackChainNodeTestStatus get() {
      return this.AttackChainNodeTestStatus;
    }
  }

  public AttackChainNodeTestStatusComposer.Composer forAttackChainNodeTestStatus(AttackChainNodeTestStatus AttackChainNodeTestStatus) {
    generatedItems.add(AttackChainNodeTestStatus);
    return new AttackChainNodeTestStatusComposer.Composer(AttackChainNodeTestStatus);
  }
}
