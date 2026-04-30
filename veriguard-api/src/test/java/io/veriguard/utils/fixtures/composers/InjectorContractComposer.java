package io.veriguard.utils.fixtures.composers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.AttackPattern;
import io.veriguard.database.model.Injector;
import io.veriguard.database.model.InjectorContract;
import io.veriguard.database.model.Vulnerability;
import io.veriguard.database.repository.InjectorContractRepository;
import io.veriguard.database.repository.InjectorRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectorContractComposer extends ComposerBase<InjectorContract> {

  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private ObjectMapper objectMapper;

  public class Composer extends InnerComposerBase<InjectorContract> {
    private final List<String> WELL_KNOWN_CONTRACT_IDS = List.of();

    private final InjectorContract injectorContract;
    private final List<AttackPatternComposer.Composer> attackPatternComposer = new ArrayList<>();
    private final List<VulnerabilityComposer.Composer> vulnerabilityComposer = new ArrayList<>();
    private Optional<PayloadComposer.Composer> payloadComposer = Optional.empty();

    public Composer(InjectorContract injectorContract) {
      this.injectorContract = injectorContract;
    }

    public Composer withPayload(PayloadComposer.Composer payloadComposer) {
      this.payloadComposer = Optional.of(payloadComposer);
      this.injectorContract.setPayload(payloadComposer.get());
      return this;
    }

    public Composer withAttackPattern(AttackPatternComposer.Composer attackPatternComposer) {
      this.attackPatternComposer.add(attackPatternComposer);
      List<AttackPattern> tempAttackPattern = this.injectorContract.getAttackPatterns();
      tempAttackPattern.add(attackPatternComposer.get());
      this.injectorContract.setAttackPatterns(tempAttackPattern);
      return this;
    }

    public Composer withVulnerability(VulnerabilityComposer.Composer cveComposer) {
      this.vulnerabilityComposer.add(cveComposer);
      Set<Vulnerability> tempVulnerability = this.injectorContract.getVulnerabilities();
      tempVulnerability.add(cveComposer.get());
      this.injectorContract.setVulnerabilities(tempVulnerability);
      return this;
    }

    public Composer withInjector(Injector injector) {
      this.injectorContract.setInjector(injector);
      return this;
    }

    public ObjectNode getInjectContent() {
      return objectMapper.createObjectNode();
    }

    @Override
    public Composer persist() {
      payloadComposer.ifPresent(PayloadComposer.Composer::persist);
      attackPatternComposer.forEach(AttackPatternComposer.Composer::persist);
      vulnerabilityComposer.forEach(VulnerabilityComposer.Composer::persist);
      if (!WELL_KNOWN_CONTRACT_IDS.contains(injectorContract.getId())) {
        entityManager.persist(injectorContract.getInjector());
        injectorRepository.save(injectorContract.getInjector());
        entityManager.persist(injectorContract);
        injectorContractRepository.save(injectorContract);
      }
      return this;
    }

    @Override
    public Composer delete() {
      payloadComposer.ifPresent(PayloadComposer.Composer::delete);
      attackPatternComposer.forEach(AttackPatternComposer.Composer::delete);
      vulnerabilityComposer.forEach(VulnerabilityComposer.Composer::delete);
      if (!WELL_KNOWN_CONTRACT_IDS.contains(injectorContract.getId())) {
        injectorContractRepository.delete(injectorContract);
      }
      return this;
    }

    @Override
    public InjectorContract get() {
      return this.injectorContract;
    }
  }

  public Composer forInjectorContract(InjectorContract injectorContract) {
    this.generatedItems.add(injectorContract);
    return new Composer(injectorContract);
  }
}
