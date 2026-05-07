package io.veriguard.service;

import static io.veriguard.utils.fixtures.AttackChainNodeExpectationFixture.createVulnerabilityAttackChainNodeExpectation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionAction;
import io.veriguard.rest.inject.form.AttackChainNodeExecutionInput;
import io.veriguard.rest.inject.form.AttackChainNodeExpectationUpdateInput;
import io.veriguard.rest.inject.service.ExecutionProcessingContext;
import io.veriguard.utils.ExpectationUtils;
import io.veriguard.utils.fixtures.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttackChainNodeExpectationServiceTest {

  static final Long EXPIRATION_TIME_SIX_HOURS = 21600L;

  @Mock private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  @Spy @InjectMocks private AttackChainNodeExpectationService attackChainNodeExpectationService;
  @Spy private ObjectMapper mapper = new ObjectMapper();

  private AttackChainNode attackChainNode;
  private Agent agent;

  @BeforeEach
  void setUp() {
    agent = AgentFixture.createDefaultAgentService();
    attackChainNode = AttackChainNodeFixture.getDefaultAttackChainNode();
    attackChainNode.setExpectations(List.of(createVulnerabilityAttackChainNodeExpectation(attackChainNode, agent)));
  }

  private void mockExpectation(AttackChainNodeExpectation expectation) {
    doReturn(expectation)
        .when(attackChainNodeExpectationService)
        .updateAttackChainNodeExpectation(any(), any(AttackChainNodeExpectationUpdateInput.class));
    when(attackChainNodeExpectationRepository.saveAll(any())).thenReturn(List.of(expectation));
  }

  private ExecutionProcessingContext createContext(AttackChainNodeExecutionInput input) {
    return new ExecutionProcessingContext(attackChainNode, agent, input, Map.of());
  }

  private AttackChainNodeExecutionInput buildDefaultInput(ObjectNode structuredOutput) {
    AttackChainNodeExecutionInput input = new AttackChainNodeExecutionInput();
    input.setMessage("message");
    input.setOutputStructured(structuredOutput != null ? String.valueOf(structuredOutput) : null);
    input.setOutputRaw("outputRaw");
    input.setStatus(ExecutionTraceStatus.SUCCESS.toString());
    input.setDuration(10);
    input.setAction(AttackChainNodeExecutionAction.command_execution);
    return input;
  }

  private void setupAttackChainNodeWithOutputParser(OutputParser outputParser)
      throws JsonProcessingException {
    NodeExecutor nodeExecutor = NodeExecutorFixture.createDefaultNodeExecutor("InjectorName");
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setOutputParsers(outputParser != null ? Set.of(outputParser) : Set.of());
    NodeContract contract =
        NodeContractFixture.createPayloadNodeContract(nodeExecutor, payload);
    attackChainNode.setNodeContract(contract);
  }

  private void setupVulnerabilityExpectation() {
    AttackChainNodeExpectation expectation = createVulnerabilityAttackChainNodeExpectation(attackChainNode, agent);
    attackChainNode.setExpectations(List.of(expectation));
    mockExpectation(expectation);
  }

  private void verifySetResultExpectationVulnerableCalledOnce(
      MockedStatic<ExpectationUtils> mocked) {
    mocked.verify(
        () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), times(1));
  }

  @Test
  @DisplayName("Should return all prevention expectations when none expired")
  void shouldReturnAllPreventionExpectationsWhenNoneExpired() {
    AttackChainNodeExpectation expectation1 =
        AttackChainNodeExpectationFixture.createPreventionAttackChainNodeExpectation(attackChainNode, null);
    AttackChainNodeExpectation expectation2 =
        AttackChainNodeExpectationFixture.createPreventionAttackChainNodeExpectation(attackChainNode, null);
    when(attackChainNodeExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<AttackChainNodeExpectation> result =
        attackChainNodeExpectationService.preventionExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Should return all detection expectations when none expired")
  void shouldReturnAllDetectionExpectationsWhenNoneExpired() {
    AttackChainNodeExpectation expectation1 =
        AttackChainNodeExpectationFixture.createDetectionAttackChainNodeExpectation(attackChainNode, null);
    AttackChainNodeExpectation expectation2 =
        AttackChainNodeExpectationFixture.createDetectionAttackChainNodeExpectation(attackChainNode, null);
    when(attackChainNodeExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<AttackChainNodeExpectation> result =
        attackChainNodeExpectationService.detectionExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Should return all manual expectations when none expired")
  void shouldReturnAllManualExpectationsWhenNoneExpired() {
    AttackChainNodeExpectation expectation1 =
        AttackChainNodeExpectationFixture.createManualAttackChainNodeExpectation(null, attackChainNode);
    AttackChainNodeExpectation expectation2 =
        AttackChainNodeExpectationFixture.createManualAttackChainNodeExpectation(null, attackChainNode);
    when(attackChainNodeExpectationRepository.findAll(any()))
        .thenReturn(List.of(expectation1, expectation2));

    List<AttackChainNodeExpectation> result =
        attackChainNodeExpectationService.manualExpectationsNotExpired(
            EXPIRATION_TIME_SIX_HOURS.intValue() * 2);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(expectation1.getId(), result.getFirst().getId());
  }

  @Test
  @DisplayName("Should set not vulnerable when no output parsers")
  void shouldSetNotVulnerableWhenNoOutputParsers() throws JsonProcessingException {
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupAttackChainNodeWithOutputParser(null);
      setupVulnerabilityExpectation();

      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(new AttackChainNodeExecutionInput()), mapper.createObjectNode());

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should set not vulnerable when structured output is empty")
  void shouldSetNotVulnerableWhenEmptyStructuredOutput() {
    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupVulnerabilityExpectation();

      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should set not vulnerable when structured output has no CVE type")
  void shouldSetNotVulnerableWhenNoCveType() throws JsonProcessingException {
    ObjectNode structuredOutput = mapper.createObjectNode();
    structuredOutput
        .putArray("no-cve-key")
        .addObject()
        .put("id", "no-cve-id")
        .put("host", "savanna28")
        .put("severity", "7.1");

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupAttackChainNodeWithOutputParser(
          OutputParserFixture.getOutputParser(
              Set.of(OutputParserFixture.getContractOutputElementTypeIPv6())));
      setupVulnerabilityExpectation();

      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(structuredOutput)), structuredOutput);

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should set vulnerable when structured output has CVE type and CVE data")
  void shouldSetVulnerableWhenHasCveTypeAndCveData() {
    ObjectNode structuredOutput = mapper.createObjectNode();
    structuredOutput
        .putArray("cve-key")
        .addObject()
        .put("id", "CVE-2025-0234")
        .put("host", "savacano28")
        .put("severity", "7.1");

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupAttackChainNodeWithOutputParser(
          OutputParserFixture.getOutputParser(
              Set.of(OutputParserFixture.getContractOutputElementTypeIPv6())));
      setupVulnerabilityExpectation();

      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(structuredOutput)), structuredOutput);

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("Should set not vulnerable when structured output is an empty array")
  void shouldSetNotVulnerableWhenStructuredOutputIsEmptyArray() {
    // isArray()=true but size()=0 -> not vulnerable
    ArrayNode structuredOutput = mapper.createArrayNode();

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupVulnerabilityExpectation();

      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), structuredOutput);

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should set vulnerable when structured output is a non-empty array")
  void shouldSetVulnerableWhenStructuredOutputIsNonEmptyArray() {
    // isArray()=true and size()>0 -> vulnerable
    ArrayNode structuredOutput = mapper.createArrayNode();
    structuredOutput.addObject().put("id", "CVE-2025-9999");

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      setupVulnerabilityExpectation();

      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), structuredOutput);

      verifySetResultExpectationVulnerableCalledOnce(mocked);
    }
  }

  @Test
  @DisplayName("Should do nothing when no vulnerability expectations match the agent")
  void shouldDoNothingWhenNoVulnerabilityExpectationsForAgent() {
    // Expectation belongs to a different agent -> filtered out -> early return
    Agent otherAgent = AgentFixture.createDefaultAgentService();
    AttackChainNodeExpectation expectationForOtherAgent =
        createVulnerabilityAttackChainNodeExpectation(attackChainNode, otherAgent);
    attackChainNode.setExpectations(List.of(expectationForOtherAgent));

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      // early return: nothing should be called
      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), never());
      verify(attackChainNodeExpectationRepository, never()).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should do nothing when expectations are not of vulnerability type")
  void shouldDoNothingWhenExpectationsAreNotVulnerabilityType() {
    // Only non-VULNERABILITY expectations -> filtered out -> early return
    AttackChainNodeExpectation prevention =
        AttackChainNodeExpectationFixture.createPreventionAttackChainNodeExpectation(attackChainNode, null);
    AttackChainNodeExpectation detection =
        AttackChainNodeExpectationFixture.createDetectionAttackChainNodeExpectation(attackChainNode, null);
    attackChainNode.setExpectations(List.of(prevention, detection));

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), never());
      verify(attackChainNodeExpectationRepository, never()).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should do nothing when expectation has a null agent")
  void shouldDoNothingWhenExpectationHasNullAgent() {
    // exp.getAgent() == null -> filtered out -> early return
    AttackChainNodeExpectation expectationWithNullAgent = createVulnerabilityAttackChainNodeExpectation(attackChainNode, null);
    attackChainNode.setExpectations(List.of(expectationWithNullAgent));

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), never());
      verify(attackChainNodeExpectationRepository, never()).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should do nothing when inject has no expectations")
  void shouldDoNothingWhenAttackChainNodeHasNoExpectations() {
    attackChainNode.setExpectations(List.of());

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      mocked.verify(
          () -> ExpectationUtils.setResultExpectationVulnerable(any(), any(), any()), never());
      verify(attackChainNodeExpectationRepository, never()).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should save all expectations after processing")
  void shouldSaveAllExpectationsAfterProcessing() {
    setupVulnerabilityExpectation();

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      verify(attackChainNodeExpectationRepository, times(1)).saveAll(any());
    }
  }

  @Test
  @DisplayName("Should call update for each vulnerability expectation")
  void shouldCallUpdateForEachVulnerabilityExpectation() {
    // Two vulnerability expectations for the same agent
    AttackChainNodeExpectation exp1 = createVulnerabilityAttackChainNodeExpectation(attackChainNode, agent);
    AttackChainNodeExpectation exp2 = createVulnerabilityAttackChainNodeExpectation(attackChainNode, agent);
    attackChainNode.setExpectations(List.of(exp1, exp2));
    doReturn(exp1)
        .when(attackChainNodeExpectationService)
        .updateAttackChainNodeExpectation(any(), any(AttackChainNodeExpectationUpdateInput.class));
    when(attackChainNodeExpectationRepository.saveAll(any())).thenReturn(List.of(exp1, exp2));

    try (MockedStatic<ExpectationUtils> mocked = Mockito.mockStatic(ExpectationUtils.class)) {
      attackChainNodeExpectationService.matchesVulnerabilityExpectations(
          createContext(buildDefaultInput(null)), mapper.createObjectNode());

      // updateAttackChainNodeExpectation called once per expectation
      verify(attackChainNodeExpectationService, times(2))
          .updateAttackChainNodeExpectation(any(), any(AttackChainNodeExpectationUpdateInput.class));
      verify(attackChainNodeExpectationRepository, times(1)).saveAll(any());
    }
  }
}
