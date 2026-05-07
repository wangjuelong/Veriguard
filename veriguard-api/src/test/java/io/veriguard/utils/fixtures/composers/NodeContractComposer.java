package io.veriguard.utils.fixtures.composers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.AttackPattern;
import io.veriguard.database.model.NodeExecutor;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.model.Vulnerability;
import io.veriguard.database.repository.NodeContractRepository;
import io.veriguard.database.repository.NodeExecutorRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NodeContractComposer extends ComposerBase<NodeContract> {

  @Autowired private NodeContractRepository nodeContractRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private NodeExecutorRepository nodeExecutorRepository;
  @Autowired private ObjectMapper objectMapper;

  public class Composer extends InnerComposerBase<NodeContract> {
    private final List<String> WELL_KNOWN_CONTRACT_IDS = List.of();

    private final NodeContract nodeContract;
    private final List<AttackPatternComposer.Composer> attackPatternComposer = new ArrayList<>();
    private final List<VulnerabilityComposer.Composer> vulnerabilityComposer = new ArrayList<>();
    private Optional<PayloadComposer.Composer> payloadComposer = Optional.empty();

    public Composer(NodeContract nodeContract) {
      this.nodeContract = nodeContract;
    }

    public Composer withPayload(PayloadComposer.Composer payloadComposer) {
      this.payloadComposer = Optional.of(payloadComposer);
      this.nodeContract.setPayload(payloadComposer.get());
      return this;
    }

    public Composer withAttackPattern(AttackPatternComposer.Composer attackPatternComposer) {
      this.attackPatternComposer.add(attackPatternComposer);
      List<AttackPattern> tempAttackPattern = this.nodeContract.getAttackPatterns();
      tempAttackPattern.add(attackPatternComposer.get());
      this.nodeContract.setAttackPatterns(tempAttackPattern);
      return this;
    }

    public Composer withVulnerability(VulnerabilityComposer.Composer cveComposer) {
      this.vulnerabilityComposer.add(cveComposer);
      Set<Vulnerability> tempVulnerability = this.nodeContract.getVulnerabilities();
      tempVulnerability.add(cveComposer.get());
      this.nodeContract.setVulnerabilities(tempVulnerability);
      return this;
    }

    public Composer withNodeExecutor(NodeExecutor nodeExecutor) {
      this.nodeContract.setNodeExecutor(nodeExecutor);
      return this;
    }

    public ObjectNode getAttackChainNodeContent() {
      return objectMapper.createObjectNode();
    }

    @Override
    public Composer persist() {
      payloadComposer.ifPresent(PayloadComposer.Composer::persist);
      attackPatternComposer.forEach(AttackPatternComposer.Composer::persist);
      vulnerabilityComposer.forEach(VulnerabilityComposer.Composer::persist);
      if (!WELL_KNOWN_CONTRACT_IDS.contains(nodeContract.getId())) {
        entityManager.persist(nodeContract.getNodeExecutor());
        nodeExecutorRepository.save(nodeContract.getNodeExecutor());
        entityManager.persist(nodeContract);
        nodeContractRepository.save(nodeContract);
      }
      return this;
    }

    @Override
    public Composer delete() {
      payloadComposer.ifPresent(PayloadComposer.Composer::delete);
      attackPatternComposer.forEach(AttackPatternComposer.Composer::delete);
      vulnerabilityComposer.forEach(VulnerabilityComposer.Composer::delete);
      if (!WELL_KNOWN_CONTRACT_IDS.contains(nodeContract.getId())) {
        nodeContractRepository.delete(nodeContract);
      }
      return this;
    }

    @Override
    public NodeContract get() {
      return this.nodeContract;
    }
  }

  public Composer forNodeContract(NodeContract nodeContract) {
    this.generatedItems.add(nodeContract);
    return new Composer(nodeContract);
  }
}
