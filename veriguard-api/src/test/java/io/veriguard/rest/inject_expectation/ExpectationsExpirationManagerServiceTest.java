package io.veriguard.rest.inject_expectation;

import static io.veriguard.integration.impl.injectors.veriguard.VeriguardNodeExecutorIntegration.VERIGUARD_INJECTOR_ID;
import static io.veriguard.integration.impl.injectors.veriguard.VeriguardNodeExecutorIntegration.VERIGUARD_INJECTOR_NAME;
import static io.veriguard.utils.fixtures.ExpectationFixture.*;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.IntegrationTest;
import io.veriguard.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.model.Expectation;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class ExpectationsExpirationManagerServiceTest extends IntegrationTest {

  private static final String INJECTION_NAME = "AMSI Bypass - AMSI InitFailed";
  private static final String INJECTOR_TYPE = "veriguard_implant";

  public static final long EXPIRATION_TIME_1_s = 1L;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private AttackChainNodeRepository attackChainNodeRepository;
  @Autowired private NodeExecutorRepository nodeExecutorRepository;
  @Autowired private NodeContractRepository nodeContractRepository;
  @Autowired private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  @Autowired private AttackChainNodeExpectationService attackChainNodeExpectationService;
  @Autowired private ExpectationsExpirationManagerService expectationsExpirationManagerService;

  // Saved entities for test setup
  private static NodeExecutor savedNodeExecutor;
  private static NodeContract savedNodeContract;
  private static AssetGroup savedAssetGroup;
  private static Endpoint savedEndpoint;
  private static Agent savedAgent1;
  private static Agent savedAgent2;
  private static AttackChainNode savedAttackChainNode;

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

    // -- Targets --
    savedEndpoint = endpointRepository.save(EndpointFixture.createEndpoint());
    savedAgent1 = agentRepository.save(AgentFixture.createAgent(savedEndpoint, "external01"));
    savedAgent2 = agentRepository.save(AgentFixture.createAgent(savedEndpoint, "external02"));
    savedAssetGroup =
        assetGroupRepository.save(
            AssetGroupFixture.createAssetGroupWithAssets(
                "asset group name", List.of(savedEndpoint)));

    // -- AttackChainNode --
    savedAttackChainNode =
        attackChainNodeRepository.save(
            AttackChainNodeFixture.createTechnicalAttackChainNodeWithAssetGroup(
                savedNodeContract, INJECTION_NAME, savedAssetGroup));
  }

  @AfterAll
  void afterAll() {
    agentRepository.deleteAll();
    attackChainNodeRepository.deleteAll();
    endpointRepository.deleteAll();
  }

  @AfterEach
  void afterEach() {
    attackChainNodeExpectationRepository.deleteAll();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Update injectExpectations with expectationsExpirationManagerService")
  class ComputeExpectationsWithExpectationExpiredManagerService {

    @Test
    @DisplayName("All injectExpectations are expired")
    @WithMockUser(isAdmin = true)
    void allExpectationAreExpired() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableNode executableAttackChainNode = newExecutableNodeWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      detectionExpectations.add(
          createTechnicalDetectionExpectationForAsset(savedEndpoint, null, EXPIRATION_TIME_1_s));
      attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
          executableAttackChainNode, detectionExpectations);

      // -- VERIFY --
      // Agent Expectation
      List<AttackChainNodeExpectation> attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent2.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());
      // Asset
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(
              savedAttackChainNode.getId(), savedEndpoint.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());
      // Asset Group
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(
              savedAttackChainNode.getId(), savedAssetGroup.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      // Agent Expectation
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent2.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
      // Asset
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(
              savedAttackChainNode.getId(), savedEndpoint.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
      // Asset Group
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(
              savedAttackChainNode.getId(), savedAssetGroup.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("One injectExpectations is already filled")
    @WithMockUser(isAdmin = true)
    void OneExpectationIsAlreadyFilled() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableNode executableAttackChainNode = newExecutableNodeWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
          executableAttackChainNode, detectionExpectations);

      // Update one expectation from one agent with source collector-id
      List<AttackChainNodeExpectation> attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());

      AttackChainNodeExpectation ie = attackChainNodeExpectations.getFirst();
      ie.setResults(
          List.of(
              NodeExpectationResult.builder()
                  .sourceId("collector-id")
                  .sourceName("collector-name")
                  .sourceType("collector-type")
                  .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                  .result("result")
                  .sourceAssetId(UUID.randomUUID().toString())
                  .score(50.0)
                  .build()));
      ie.setScore(50.0);

      attackChainNodeExpectationRepository.save(ie);

      // -- VERIFY --
      // Agent Expectation
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());
      assertEquals(50.0, attackChainNodeExpectations.getFirst().getScore());
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent2.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());
      // Asset
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(
              savedAttackChainNode.getId(), savedEndpoint.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());
      // Asset Group
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(
              savedAttackChainNode.getId(), savedAssetGroup.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      // Agent Expectation
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());
      assertEquals(50.0, attackChainNodeExpectations.getFirst().getScore());
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent2.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
      // Asset
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(
              savedAttackChainNode.getId(), savedEndpoint.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
      // Asset Group
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(
              savedAttackChainNode.getId(), savedAssetGroup.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("The agent expectations are already filled")
    @WithMockUser(isAdmin = true)
    void agentExpectationsAreAlreadyFilled() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents

      ExecutableNode executableAttackChainNode = newExecutableNodeWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
          executableAttackChainNode, detectionExpectations);

      // Update agent expectations with source collector-id
      List<AttackChainNodeExpectation> attackChainNodeExpectations =
          List.of(
              attackChainNodeExpectationRepository
                  .findAllByAttackChainNodeAndAgent(
                      savedAttackChainNode.getId(), savedAgent1.getId())
                  .getFirst(),
              attackChainNodeExpectationRepository
                  .findAllByAttackChainNodeAndAgent(
                      savedAttackChainNode.getId(), savedAgent2.getId())
                  .getFirst());

      attackChainNodeExpectations.forEach(
          attackChainNodeExpectation -> {
            attackChainNodeExpectation.setResults(
                List.of(
                    NodeExpectationResult.builder()
                        .sourceId("collector-id")
                        .sourceName("collector-name")
                        .sourceType("collector-type")
                        .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                        .result("result")
                        .sourceAssetId(UUID.randomUUID().toString())
                        .score(100.0)
                        .build()));
            attackChainNodeExpectation.setScore(100.0);
          });

      attackChainNodeExpectationRepository.saveAll(attackChainNodeExpectations);

      // -- VERIFY --
      // Agent Expectation
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());
      assertEquals(100.0, attackChainNodeExpectations.getFirst().getScore());
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent2.getId());
      assertEquals(100.0, attackChainNodeExpectations.getFirst().getScore());
      // Asset
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(
              savedAttackChainNode.getId(), savedEndpoint.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());
      // Asset Group
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(
              savedAttackChainNode.getId(), savedAssetGroup.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      // Agent Expectation
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());
      assertEquals(100.0, attackChainNodeExpectations.getFirst().getScore());
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent2.getId());
      assertEquals(100.0, attackChainNodeExpectations.getFirst().getScore());
      // Asset
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(
              savedAttackChainNode.getId(), savedEndpoint.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
      // Asset Group
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(
              savedAttackChainNode.getId(), savedAssetGroup.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("Asset expectations without agent expectation linked")
    @WithMockUser(isAdmin = true)
    void assetExpectationWithoutAgentExpectationsLinked() {
      // -- PREPARE --
      // Build and save expectations for asset group with one asset and two agents
      ExecutableNode executableAttackChainNode = newExecutableNodeWithTargets();
      List<Expectation> detectionExpectations =
          createDetectionExpectations(
              List.of(savedAgent1, savedAgent2),
              savedEndpoint,
              savedAssetGroup,
              EXPIRATION_TIME_1_s);
      attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
          executableAttackChainNode, detectionExpectations);

      // Delete agent attackChainNode expectations to test behavior of assets without agents
      List<AttackChainNodeExpectation> attackChainNodeExpectations =
          List.of(
              attackChainNodeExpectationRepository
                  .findAllByAttackChainNodeAndAgent(
                      savedAttackChainNode.getId(), savedAgent1.getId())
                  .getFirst(),
              attackChainNodeExpectationRepository
                  .findAllByAttackChainNodeAndAgent(
                      savedAttackChainNode.getId(), savedAgent2.getId())
                  .getFirst());

      List<String> ids =
          attackChainNodeExpectations.stream().map(AttackChainNodeExpectation::getId).toList();

      attackChainNodeExpectationRepository.deleteAllById(ids);

      // -- VERIFY --
      // Asset
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(
              savedAttackChainNode.getId(), savedEndpoint.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());
      // Asset Group
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(
              savedAttackChainNode.getId(), savedAssetGroup.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      // Asset
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAsset(
              savedAttackChainNode.getId(), savedEndpoint.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
      // Asset Group
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAssetGroup(
              savedAttackChainNode.getId(), savedAssetGroup.getId());
      assertEquals(0.0, attackChainNodeExpectations.getFirst().getScore());
    }

    @Test
    @DisplayName("Vulnerability expectation with an agent gets expired")
    @WithMockUser(isAdmin = true)
    void vulnerableExpectationIsExpired() {
      // -- PREPARE --
      // Build and save an expectation for an asset and one agent
      ExecutableNode executableAttackChainNode = newExecutableNodeWithTargets();
      Expectation expectation =
          createTechnicalVulnerabilityExpectationForAgent(
              savedAgent1, savedEndpoint, null, EXPIRATION_TIME_1_s, null);

      attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
          executableAttackChainNode, List.of(expectation));

      // -- VERIFY --
      List<AttackChainNodeExpectation> attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());
      assertEquals(null, attackChainNodeExpectations.getFirst().getScore());

      // -- EXECUTE --
      expectationsExpirationManagerService.computeExpectations();

      // -- ASSERT --
      attackChainNodeExpectations =
          attackChainNodeExpectationRepository.findAllByAttackChainNodeAndAgent(
              savedAttackChainNode.getId(), savedAgent1.getId());
      assertEquals(100.0, attackChainNodeExpectations.getFirst().getScore());
      assertEquals(
          AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
          attackChainNodeExpectations.getFirst().getResponse());
    }
  }

  // -- PRIVATE HELPERS --

  private ExecutableNode newExecutableNodeWithTargets() {
    return new ExecutableNode(
        false,
        true,
        savedAttackChainNode,
        emptyList(),
        List.of(savedEndpoint),
        List.of(savedAssetGroup),
        emptyList());
  }
}
