package io.veriguard.utils.fixtures.composers;

import io.veriguard.database.model.SecurityCoverage;
import io.veriguard.database.repository.SecurityCoverageRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SecurityCoverageComposer extends ComposerBase<SecurityCoverage> {
  @Autowired private SecurityCoverageRepository securityCoverageRepository;

  public class Composer extends InnerComposerBase<SecurityCoverage> {
    private final SecurityCoverage securityCoverage;
    private Optional<AttackChainComposer.Composer> attackChainComposer = Optional.empty();

    public Composer(SecurityCoverage securityCoverage) {
      this.securityCoverage = securityCoverage;
    }

    public Composer withAttackChain(AttackChainComposer.Composer attackChainWrapper) {
      attackChainComposer = Optional.of(attackChainWrapper);
      this.securityCoverage.setAttackChain(attackChainWrapper.get());
      return this;
    }

    @Override
    public Composer persist() {
      attackChainComposer.ifPresent(AttackChainComposer.Composer::persist);
      securityCoverageRepository.save(this.securityCoverage);
      return this;
    }

    @Override
    public Composer delete() {
      attackChainComposer.ifPresent(AttackChainComposer.Composer::delete);
      securityCoverageRepository.delete(this.securityCoverage);
      return this;
    }

    @Override
    public SecurityCoverage get() {
      return this.securityCoverage;
    }
  }

  public Composer forSecurityCoverage(SecurityCoverage securityCoverage) {
    generatedItems.add(securityCoverage);
    return new Composer(securityCoverage);
  }
}
