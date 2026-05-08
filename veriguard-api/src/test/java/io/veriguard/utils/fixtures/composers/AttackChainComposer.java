package io.veriguard.utils.fixtures.composers;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainRepository;
import io.veriguard.service.attack_chain.AttackChainService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttackChainComposer extends ComposerBase<AttackChain> {
  @Autowired private AttackChainRepository attackChainRepository;
  @Autowired private AttackChainService attackChainService;

  public class Composer extends InnerComposerBase<AttackChain> {
    private final AttackChain attackChain;
    private final List<AttackChainNodeComposer.Composer> attackChainNodeComposers =
        new ArrayList<>();
    private final List<AttackChainRunComposer.Composer> simulationComposers = new ArrayList<>();
    private final List<TagComposer.Composer> tagComposers = new ArrayList<>();
    private final List<VariableComposer.Composer> variableComposers = new ArrayList<>();

    public Composer(AttackChain attackChain) {
      this.attackChain = attackChain;
    }

    public Composer withAttackChainNodes(
        List<AttackChainNodeComposer.Composer> attackChainNodeComposers) {
      attackChainNodeComposers.forEach(this::withAttackChainNode);
      return this;
    }

    public AttackChainComposer.Composer withTag(TagComposer.Composer tagComposer) {
      this.tagComposers.add(tagComposer);
      Set<Tag> tempTags = this.attackChain.getTags();
      tempTags.add(tagComposer.get());
      this.attackChain.setTags(tempTags);
      return this;
    }

    public Composer withAttackChainNode(AttackChainNodeComposer.Composer attackChainNodeComposer) {
      attackChainNodeComposers.add(attackChainNodeComposer);
      Set<AttackChainNode> tempAttackChainNodes =
          new HashSet<>(this.attackChain.getAttackChainNodes());
      attackChainNodeComposer.get().setAttackChain(attackChain);
      tempAttackChainNodes.add(attackChainNodeComposer.get());
      this.attackChain.setAttackChainNodes(tempAttackChainNodes);
      return this;
    }

    public Composer withSimulation(AttackChainRunComposer.Composer simulationComposer) {
      simulationComposers.add(simulationComposer);
      List<AttackChainRun> simulations = this.attackChain.getAttackChainRuns();
      simulations.add(simulationComposer.get());
      this.attackChain.setAttackChainRuns(simulations);
      return this;
    }

    public Composer withVariable(VariableComposer.Composer variableComposer) {
      variableComposers.add(variableComposer);
      List<Variable> tempVariables = this.attackChain.getVariables();
      tempVariables.add(variableComposer.get());
      variableComposer.get().setAttackChain(attackChain);
      attackChain.setVariables(tempVariables);
      return this;
    }

    @Override
    public Composer persist() {
      simulationComposers.forEach(AttackChainRunComposer.Composer::persist);
      attackChainRepository.save(attackChain);
      tagComposers.forEach(TagComposer.Composer::persist);
      attackChainNodeComposers.forEach(AttackChainNodeComposer.Composer::persist);
      variableComposers.forEach(VariableComposer.Composer::persist);
      attackChainService.createAttackChain(attackChain);
      return this;
    }

    @Override
    public Composer delete() {
      attackChainNodeComposers.forEach(AttackChainNodeComposer.Composer::delete);
      tagComposers.forEach(TagComposer.Composer::delete);
      simulationComposers.forEach(AttackChainRunComposer.Composer::delete);
      variableComposers.forEach(VariableComposer.Composer::delete);
      attackChainRepository.delete(attackChain);
      return this;
    }

    @Override
    public AttackChain get() {
      return this.attackChain;
    }
  }

  public Composer forAttackChain(AttackChain attackChain) {
    this.generatedItems.add(attackChain);
    return new Composer(attackChain);
  }
}
