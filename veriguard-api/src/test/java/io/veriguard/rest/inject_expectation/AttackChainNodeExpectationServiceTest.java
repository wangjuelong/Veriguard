package io.veriguard.rest.inject_expectation;

import static io.veriguard.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;
import static io.veriguard.integration.impl.injectors.veriguard.VeriguardNodeExecutorIntegration.VERIGUARD_INJECTOR_ID;
import static io.veriguard.integration.impl.injectors.veriguard.VeriguardNodeExecutorIntegration.VERIGUARD_INJECTOR_NAME;
import static io.veriguard.utils.fixtures.ExpectationFixture.createDetectionExpectations;
import static io.veriguard.utils.fixtures.ExpectationFixture.createPreventionExpectations;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.model.Expectation;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class AttackChainNodeExpectationServiceTest extends IntegrationTest {

  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";
  private static final String INJECTOR_TYPE = "veriguard_implant";

  // Saved entities for test setup
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private AttackChainNodeExpectationComposer attackChainNodeExpectationComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private EntityManager entityManager;

  @Autowired private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  @Autowired private NodeContractRepository nodeContractRepository;
  @Autowired private NodeExecutorRepository nodeExecutorRepository;
  @Autowired private AttackChainNodeRepository attackChainNodeRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private CollectorComposer collectorComposer;

  @Autowired private AttackChainNodeExpectationService attackChainNodeExpectationService;

  private static NodeExecutor savedNodeExecutor;
  private static NodeContract savedNodeContract;
  private static Asset savedAsset;

  @BeforeAll
  void beforeAll() throws JsonProcessingException {
    NodeContract nodeContract =
        NodeContractFixture.createNodeContract(Map.of("en", INJECTION_NAME));
    savedNodeExecutor =
        nodeExecutorRepository.save(
            NodeExecutorFixture.createNodeExecutor(
                VERIGUARD_INJECTOR_ID, VERIGUARD_INJECTOR_NAME, INJECTOR_TYPE));
    nodeContract.setNodeExecutor(savedNodeExecutor);

    savedNodeContract = nodeContractRepository.save(nodeContract);
    savedAsset = assetRepository.save(AssetFixture.createDefaultAsset("asset name"));
    collectorComposer.forCollector(CollectorFixture.createDefaultCollector("FAKE")).persist();
  }

  @AfterAll
  void afterAll() {
    assetRepository.deleteAll();
  }

  @AfterEach
  void afterEach() {
    attackChainNodeExpectationRepository.deleteAll();
    attackChainNodeRepository.deleteAll();
    assetGroupRepository.deleteAll();
    agentRepository.deleteAll();
  }

  private AttackChainNode saveAttackChainNode(NodeContract nodeContract) {
    AttackChainNode attackChainNode =
        AttackChainNodeFixture.createTechnicalAttackChainNode(nodeContract, INJECTION_NAME, savedAsset);
    return attackChainNodeRepository.save(attackChainNode);
  }

  private ExecutableNode createExecutableNode(
      AttackChainNode savedAttackChainNode, List<AssetGroup> assetGroups) {
    return new ExecutableNode(
        false, true, savedAttackChainNode, emptyList(), List.of(savedAsset), assetGroups, emptyList());
  }

  private Agent createAgent(String external01) {
    Agent agent = AgentFixture.createAgent(savedAsset, external01);
    return this.agentRepository.save(agent);
  }

  private AssetGroup createAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createAssetGroupWithAssets(name, List.of(savedAsset));
    return assetGroupRepository.save(assetGroup);
  }

  @Test
  @DisplayName(
      "Expectations type prevention and detection should be created for agent linked to asset")
  void expectationsForAssetLinkedToAgent() {
    // -- PREPARE --
    Agent savedAgent = createAgent("external01");
    AttackChainNode savedAttackChainNode = saveAttackChainNode(savedNodeContract);
    ExecutableNode executableAttackChainNode = createExecutableNode(savedAttackChainNode, emptyList());
    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            List.of(savedAgent), savedAsset, null, DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> preventionExpectations =
        createPreventionExpectations(
            List.of(savedAgent), savedAsset, null, DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> expectations =
        java.util.stream.Stream.concat(
                detectionExpectations.stream(), preventionExpectations.stream())
            .toList();

    // -- EXECUTE --
    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(executableAttackChainNode, expectations);

    // -- ASSERT --
    assertEquals(4, attackChainNodeExpectationRepository.findAll().spliterator().getExactSizeIfKnown());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAsset(savedAttackChainNode.getId(), savedAsset.getId())
            .size());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAgent(savedAttackChainNode.getId(), savedAgent.getId())
            .size());
  }

  @Test
  @DisplayName(
      "Expectations should be created for agent linked to asset who is part of an asset group")
  void expectationsForAssetGroupLinkedToAgent() {
    // -- PREPARE --
    Agent savedAgent = createAgent("external01");
    AssetGroup savedAssetGroup = createAssetGroup("asset group name");
    AttackChainNode savedAttackChainNode = saveAttackChainNode(savedNodeContract);
    ExecutableNode executableAttackChainNode =
        createExecutableNode(savedAttackChainNode, List.of(savedAssetGroup));
    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            List.of(savedAgent),
            savedAsset,
            savedAssetGroup,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> preventionExpectations =
        createPreventionExpectations(
            List.of(savedAgent),
            savedAsset,
            savedAssetGroup,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> expectations =
        java.util.stream.Stream.concat(
                detectionExpectations.stream(), preventionExpectations.stream())
            .toList();

    // -- EXECUTE --
    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(executableAttackChainNode, expectations);

    // -- ASSERT --
    assertEquals(6, attackChainNodeExpectationRepository.findAll().spliterator().getExactSizeIfKnown());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAsset(savedAttackChainNode.getId(), savedAsset.getId())
            .size());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAssetGroup(savedAttackChainNode.getId(), savedAssetGroup.getId())
            .size());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAgent(savedAttackChainNode.getId(), savedAgent.getId())
            .size());
  }

  @Test
  @DisplayName("Expectations should be created for asset with multiple agents")
  void expectationsForAssetWithMultipleAgents() {
    // -- PREPARE --
    Agent savedAgent = createAgent("external01");
    Agent savedAgent1 = createAgent("external02");
    AttackChainNode savedAttackChainNode = saveAttackChainNode(savedNodeContract);
    ExecutableNode executableAttackChainNode = createExecutableNode(savedAttackChainNode, emptyList());
    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            List.of(savedAgent, savedAgent1),
            savedAsset,
            null,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> preventionExpectations =
        createPreventionExpectations(
            List.of(savedAgent, savedAgent1),
            savedAsset,
            null,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> expectations =
        java.util.stream.Stream.concat(
                detectionExpectations.stream(), preventionExpectations.stream())
            .toList();

    // -- EXECUTE --
    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(executableAttackChainNode, expectations);

    // -- ASSERT --
    assertEquals(6, attackChainNodeExpectationRepository.findAll().spliterator().getExactSizeIfKnown());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAsset(savedAttackChainNode.getId(), savedAsset.getId())
            .size());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAgent(savedAttackChainNode.getId(), savedAgent.getId())
            .size());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAgent(savedAttackChainNode.getId(), savedAgent1.getId())
            .size());
  }

  @Test
  @DisplayName("Expectations should be created for asset group with multiple agents")
  void expectationsForAssetGroupWithMultipleAgents() {
    // -- PREPARE --
    Agent savedAgent = createAgent("external01");
    Agent savedAgent1 = createAgent("external02");
    AssetGroup savedAssetGroup = createAssetGroup("assetGroup name");
    AttackChainNode savedAttackChainNode = saveAttackChainNode(savedNodeContract);
    ExecutableNode executableAttackChainNode =
        createExecutableNode(savedAttackChainNode, List.of(savedAssetGroup));

    List<Expectation> detectionExpectations =
        createDetectionExpectations(
            List.of(savedAgent, savedAgent1),
            savedAsset,
            savedAssetGroup,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> preventionExpectations =
        createPreventionExpectations(
            List.of(savedAgent, savedAgent1),
            savedAsset,
            savedAssetGroup,
            DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME);
    List<Expectation> expectations =
        java.util.stream.Stream.concat(
                detectionExpectations.stream(), preventionExpectations.stream())
            .toList();

    // -- EXECUTE --
    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(executableAttackChainNode, expectations);

    // -- ASSERT --
    assertEquals(8, attackChainNodeExpectationRepository.findAll().spliterator().getExactSizeIfKnown());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAsset(savedAttackChainNode.getId(), savedAsset.getId())
            .size());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAssetGroup(savedAttackChainNode.getId(), savedAssetGroup.getId())
            .size());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAgent(savedAttackChainNode.getId(), savedAgent.getId())
            .size());
    assertEquals(
        2,
        attackChainNodeExpectationRepository
            .findAllByAttackChainNodeAndAgent(savedAttackChainNode.getId(), savedAgent1.getId())
            .size());
  }
}
