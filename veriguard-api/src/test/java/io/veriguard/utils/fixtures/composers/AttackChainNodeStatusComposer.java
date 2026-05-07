package io.veriguard.utils.fixtures.composers;

import io.veriguard.database.model.AttackChainNodeStatus;
import io.veriguard.database.repository.AttackChainNodeStatusRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttackChainNodeStatusComposer extends ComposerBase<AttackChainNodeStatus> {
  @Autowired private AttackChainNodeStatusRepository attackChainNodeStatusRepository;

  public class Composer extends InnerComposerBase<AttackChainNodeStatus> {
    private final AttackChainNodeStatus attackChainNodeStatus;
    private final List<ExecutionTraceComposer.Composer> executionTracesComposer = new ArrayList<>();

    public Composer(AttackChainNodeStatus attackChainNodeStatus) {
      this.attackChainNodeStatus = attackChainNodeStatus;
    }

    public Composer withExecutionTraces(List<ExecutionTraceComposer.Composer> traces) {
      traces.forEach(trace -> withExecutionTrace(trace));
      return this;
    }

    public Composer withExecutionTrace(ExecutionTraceComposer.Composer executionTrace) {
      executionTracesComposer.add(executionTrace);
      executionTrace.get().setAttackChainNodeStatus(this.attackChainNodeStatus);
      this.attackChainNodeStatus.getTraces().add(executionTrace.get());
      return this;
    }

    @Override
    public AttackChainNodeStatusComposer.Composer persist() {
      attackChainNodeStatusRepository.save(attackChainNodeStatus);
      executionTracesComposer.forEach(ExecutionTraceComposer.Composer::persist);
      return this;
    }

    @Override
    public AttackChainNodeStatusComposer.Composer delete() {
      executionTracesComposer.forEach(ExecutionTraceComposer.Composer::delete);
      attackChainNodeStatusRepository.delete(attackChainNodeStatus);
      return this;
    }

    @Override
    public AttackChainNodeStatus get() {
      return this.attackChainNodeStatus;
    }
  }

  public AttackChainNodeStatusComposer.Composer forAttackChainNodeStatus(AttackChainNodeStatus attackChainNodeStatus) {
    generatedItems.add(attackChainNodeStatus);
    return new AttackChainNodeStatusComposer.Composer(attackChainNodeStatus);
  }
}
