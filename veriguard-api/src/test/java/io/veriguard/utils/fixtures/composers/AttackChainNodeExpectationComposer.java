package io.veriguard.utils.fixtures.composers;

import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttackChainNodeExpectationComposer extends ComposerBase<AttackChainNodeExpectation> {
  @Autowired private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;

  public class Composer extends InnerComposerBase<AttackChainNodeExpectation> {
    private final AttackChainNodeExpectation attackChainNodeExpectation;
    private Optional<AssetGroupComposer.Composer> assetGroupComposer = Optional.empty();
    private Optional<TeamComposer.Composer> teamComposer = Optional.empty();
    private Optional<UserComposer.Composer> userComposer = Optional.empty();
    private Optional<EndpointComposer.Composer> endpointComposer = Optional.empty();
    private Optional<AgentComposer.Composer> agentComposer = Optional.empty();

    public Composer(AttackChainNodeExpectation attackChainNodeExpectation) {
      this.attackChainNodeExpectation = attackChainNodeExpectation;
    }

    public Composer withTeam(TeamComposer.Composer teamComposer) {
      this.teamComposer = Optional.of(teamComposer);
      this.attackChainNodeExpectation.setTeam(teamComposer.get());
      return this;
    }

    public Composer withUser(UserComposer.Composer userComposer) {
      this.userComposer = Optional.of(userComposer);
      this.attackChainNodeExpectation.setUser(userComposer.get());
      return this;
    }

    public Composer withAssetGroup(AssetGroupComposer.Composer assetGroupComposer) {
      this.assetGroupComposer = Optional.of(assetGroupComposer);
      this.attackChainNodeExpectation.setAssetGroup(assetGroupComposer.get());
      return this;
    }

    public Composer withEndpoint(EndpointComposer.Composer endpointComposer) {
      this.endpointComposer = Optional.of(endpointComposer);
      this.attackChainNodeExpectation.setAsset(endpointComposer.get());
      return this;
    }

    public Composer withAgent(AgentComposer.Composer agentComposer) {
      this.agentComposer = Optional.of(agentComposer);
      this.attackChainNodeExpectation.setAgent(agentComposer.get());
      this.attackChainNodeExpectation.setAsset(agentComposer.get().getAsset());
      return this;
    }

    @Override
    public Composer persist() {
      assetGroupComposer.ifPresent(AssetGroupComposer.Composer::persist);
      endpointComposer.ifPresent(EndpointComposer.Composer::persist);
      agentComposer.ifPresent(AgentComposer.Composer::persist);
      teamComposer.ifPresent(TeamComposer.Composer::persist);
      userComposer.ifPresent(UserComposer.Composer::persist);
      attackChainNodeExpectationRepository.save(attackChainNodeExpectation);
      return this;
    }

    @Override
    public InnerComposerBase<AttackChainNodeExpectation> delete() {
      assetGroupComposer.ifPresent(AssetGroupComposer.Composer::delete);
      endpointComposer.ifPresent(EndpointComposer.Composer::delete);
      agentComposer.ifPresent(AgentComposer.Composer::delete);
      teamComposer.ifPresent(TeamComposer.Composer::delete);
      userComposer.ifPresent(UserComposer.Composer::delete);
      attackChainNodeExpectationRepository.delete(attackChainNodeExpectation);
      return this;
    }

    @Override
    public AttackChainNodeExpectation get() {
      return this.attackChainNodeExpectation;
    }
  }

  public Composer forExpectation(AttackChainNodeExpectation attackChainNodeExpectation) {
    generatedItems.add(attackChainNodeExpectation);
    return new Composer(attackChainNodeExpectation);
  }
}
