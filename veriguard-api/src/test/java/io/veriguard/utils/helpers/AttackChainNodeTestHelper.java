package io.veriguard.utils.helpers;

import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AttackChainNodeTestHelper {

  private final AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  private final PayloadRepository payloadRepository;
  private final NodeContractRepository nodeContractRepository;
  private final AgentRepository agentRepository;
  private final EndpointRepository endpointRepository;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final FindingRepository findingRepository;
  private final AssetRepository assetRepository;
  private final DomainRepository domainRepository;
  private final NodeExecutorRepository nodeExecutorRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AttackChainNode getPendingAttackChainNodeWithAssets(
      AttackChainNodeComposer attackChainNodeComposer,
      NodeContractComposer nodeContractComposer,
      EndpointComposer endpointComposer,
      AgentComposer agentComposer,
      AttackChainNodeStatusComposer attackChainNodeStatusComposer) {
    return attackChainNodeComposer
        .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
        .withNodeContract(
            nodeContractComposer
                .forNodeContract(NodeContractFixture.createDefaultNodeContract())
                .withNodeExecutor(NodeExecutorFixture.createDefaultPayloadNodeExecutor()))
        .withEndpoint(
            endpointComposer
                .forEndpoint(EndpointFixture.createEndpoint())
                .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
        .withAttackChainNodeStatus(
            attackChainNodeStatusComposer.forAttackChainNodeStatus(
                AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus()))
        .persist()
        .get();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AttackChainNodeExpectation forceSaveAttackChainNodeExpectation(
      AttackChainNodeExpectation expectation) {
    return attackChainNodeExpectationRepository.save(expectation);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Domain forceSaveDomain(Domain domain) {
    return domainRepository.save(domain);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Payload forceSavePayload(Payload payload) {
    return payloadRepository.save(payload);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NodeContract forceSaveNodeContract(NodeContract nodeContract) {
    return nodeContractRepository.save(nodeContract);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AttackChainNode forceSaveAttackChainNode(AttackChainNode attackChainNode) {
    return attackChainNodeRepository.save(attackChainNode);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Agent forceSaveAgent(Agent agent) {
    return agentRepository.save(agent);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NodeExecutor forceSaveNodeExecutor(NodeExecutor nodeExecutor) {
    return nodeExecutorRepository.save(nodeExecutor);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Endpoint forceSaveEndpoint(Endpoint endpoint) {
    return endpointRepository.save(endpoint);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Finding forceSaveFinding(Finding finding) {
    return findingRepository.save(finding);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Asset forceSaveAsset(Asset asset) {
    return assetRepository.save(asset);
  }

  /**
   * Queries findings for a given attackChainNode ID in a new independent transaction, so that
   * findings committed by async processing threads are visible even when called from within an
   * outer {@code @Transactional} test method.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Finding> findFindingsByAttackChainNodeId(String attackChainNodeId) {
    return findingRepository.findAllByAttackChainNodeId(attackChainNodeId);
  }

  /**
   * Returns true if the attackChainNode has at least one execution trace, confirming async
   * processing completed. Runs in a new independent transaction so results committed by async
   * threads are visible from within an outer {@code @Transactional} test.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean hasAttackChainNodeStatusTrace(String attackChainNodeId) {
    return attackChainNodeRepository
        .findById(attackChainNodeId)
        .flatMap(AttackChainNode::getStatus)
        .filter(s -> !s.getTraces().isEmpty())
        .isPresent();
  }
}
