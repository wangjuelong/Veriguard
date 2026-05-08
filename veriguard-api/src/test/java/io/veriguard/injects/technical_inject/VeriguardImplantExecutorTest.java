package io.veriguard.injects.technical_inject;

import static io.veriguard.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.EXPECTATIONS_VULNERABILITY_COLLECTOR_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.collectors.utils.CollectorsUtils;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.injectors.veriguard.VeriguardImplantContract;
import io.veriguard.injectors.veriguard.model.VeriguardImplantAttackChainNodeContent;
import io.veriguard.integration.Manager;
import io.veriguard.integration.impl.injectors.veriguard.VeriguardNodeExecutorIntegrationFactory;
import io.veriguard.model.attack_chain_node.form.Expectation;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import io.veriguard.utilstest.RabbitMQTestListener;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@Transactional
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class VeriguardImplantExecutorTest extends IntegrationTest {
  @Autowired
  private VeriguardNodeExecutorIntegrationFactory veriguardNodeExecutorIntegrationFactory;

  @Autowired private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;

  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private NodeContractComposer nodeContractComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;

  @Resource protected ObjectMapper mapper;
  @Autowired private NodeExecutorFixture nodeExecutorFixture;

  @BeforeEach
  public void beforeEach() {
    assetGroupComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    attackChainNodeComposer.reset();
    nodeContractComposer.reset();
    collectorComposer.reset();
  }

  private AttackChainNode createTechnicalAttackChainNodeHelper(List<Expectation> expectationList) {
    AttackChainNode attackChainNode = AttackChainNodeFixture.getDefaultAttackChainNode();
    VeriguardImplantAttackChainNodeContent content = new VeriguardImplantAttackChainNodeContent();
    content.setExpectations(expectationList);
    content.setObfuscator("plain-text");
    attackChainNode.setContent(this.mapper.valueToTree(content));

    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
        .withSecurityPlatform(
            securityPlatformComposer.forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "EDR name", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())))
        .persist();

    return attackChainNodeComposer
        .forAttackChainNode(attackChainNode)
        .withAssetGroup(
            assetGroupComposer
                .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("windows asset group"))
                .withAsset(
                    endpointComposer
                        .forEndpoint(EndpointFixture.createEndpoint())
                        .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                        .withAgent(
                            agentComposer.forAgent(AgentFixture.createDefaultAgentService()))))
        .withNodeContract(
            nodeContractComposer
                .forNodeContract(
                    NodeContractFixture.createDefaultNodeContractWithExternalId("external-id"))
                .withNodeExecutor(nodeExecutorFixture.getWellKnownOaevImplantNodeExecutor()))
        .persist()
        .get();
  }

  private static Stream<Arguments> expectationTypeProvider() {
    return Stream.of(
        Arguments.of(
            "detection",
            AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION,
            CollectorsUtils.CROWDSTRIKE),
        Arguments.of(
            "vulnerability",
            AttackChainNodeExpectation.EXPECTATION_TYPE.VULNERABILITY,
            EXPECTATIONS_VULNERABILITY_COLLECTOR_ID));
  }

  @ParameterizedTest(
      name =
          "givenTechnicalInjectWith{0}Expectation_shouldComputeInjectExpectationAndInjectExpectationResult")
  @MethodSource("expectationTypeProvider")
  void givenExpectation_shouldComputeAttackChainNodeExpectationAndNodeExpectationResult(
      String name, AttackChainNodeExpectation.EXPECTATION_TYPE type, String expectedSourceId)
      throws Exception {

    // -- PREPARE --
    Expectation expectation = new Expectation();
    expectation.setName(name);
    expectation.setType(type);
    expectation.setScore(100.0);
    expectation.setExpectationGroup(false);

    AttackChainNode attackChainNode = createTechnicalAttackChainNodeHelper(List.of(expectation));
    Injection injection = mock(Injection.class);
    when(injection.getAttackChainNode()).thenReturn(attackChainNode);
    ExecutableNode executableAttackChainNode =
        new ExecutableNode(
            false,
            false,
            injection,
            List.of(),
            attackChainNode.getAssets(),
            attackChainNode.getAssetGroups(),
            List.of());
    Execution execution = new Execution(executableAttackChainNode.isRuntime());

    // -- EXECUTE --
    Manager manager = new Manager(List.of(veriguardNodeExecutorIntegrationFactory));
    manager.monitorIntegrations();
    io.veriguard.executors.NodeExecutor veriguardImplantExecutor =
        manager.requestNodeExecutorExecutorByType(VeriguardImplantContract.TYPE);
    veriguardImplantExecutor.process(execution, executableAttackChainNode);

    // -- ASSERT --
    // Should have 4 attackChainNode expectations - 1 for asset group - 1 for the endpoint - 1 per
    // agent
    List<AttackChainNodeExpectation> attackChainNodeExpectationList =
        attackChainNodeExpectationRepository.findAllByAttackChainNodeId(attackChainNode.getId());
    assertEquals(4, attackChainNodeExpectationList.size());
    List<AttackChainNodeExpectation> assetGroupExpectations =
        attackChainNodeExpectationList.stream()
            .filter(
                ie -> ie.getAgent() == null && ie.getAsset() == null && ie.getAssetGroup() != null)
            .toList();
    assertEquals(1, assetGroupExpectations.size());
    List<AttackChainNodeExpectation> endpointExpectations =
        attackChainNodeExpectationList.stream()
            .filter(
                ie -> ie.getAgent() == null && ie.getAsset() != null && ie.getAssetGroup() != null)
            .toList();
    assertEquals(1, endpointExpectations.size());
    List<AttackChainNodeExpectation> agentExpectations =
        attackChainNodeExpectationList.stream()
            .filter(
                ie -> ie.getAgent() != null && ie.getAsset() != null && ie.getAssetGroup() != null)
            .toList();
    assertEquals(2, agentExpectations.size());

    // AttackChainNodeExpectation.results.result should be set to null for all existing security
    // platforms at
    // the agent level only.
    assertTrue(assetGroupExpectations.getFirst().getResults().isEmpty());
    assertTrue(endpointExpectations.getFirst().getResults().isEmpty());
    List<NodeExpectationResult> results =
        agentExpectations.stream().flatMap(ie -> ie.getResults().stream()).toList();
    assertTrue(results.stream().allMatch(r -> r.getResult() == null));
    assertTrue(results.stream().allMatch(r -> expectedSourceId.equals(r.getSourceId())));
  }
}
