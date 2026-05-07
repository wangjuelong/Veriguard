package io.veriguard.injects.atomic_testing;

import static io.veriguard.expectation.ExpectationType.DETECTION;
import static io.veriguard.expectation.ExpectationType.HUMAN_RESPONSE;
import static io.veriguard.expectation.ExpectationType.PREVENTION;
import static io.veriguard.expectation.ExpectationType.VULNERABILITY;
import static io.veriguard.utils.fixtures.ExpectationResultByTypeFixture.createDefaultExpectationResultsByType;
import static io.veriguard.utils.fixtures.RawAttackChainNodeExpectationFixture.createDefaultAttackChainNodeExpectation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.raw.RawAttackChainNodeExpectation;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.veriguard.utils.AttackChainNodeUtils;
import io.veriguard.utils.ResultUtils;
import io.veriguard.utils.mapper.AttackChainNodeExpectationMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResultUtilsTest extends IntegrationTest {

  @Mock private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  @Mock private AttackChainNodeRepository attackChainNodeRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private AttackChainNodeUtils attackChainNodeUtils;

  private AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;
  private ResultUtils resultUtils;

  @BeforeEach
  void before() {
    attackChainNodeExpectationMapper =
        new AttackChainNodeExpectationMapper(attackChainNodeRepository, objectMapper, attackChainNodeUtils);
    resultUtils = new ResultUtils(attackChainNodeExpectationRepository, attackChainNodeExpectationMapper);
  }

  @Test
  @DisplayName("Should get calculated global scores for injects")
  void getAttackChainRunsGlobalScores() {
    String attackChainNodeId1 = "103da74a-055b-40e2-a934-9605cd3e4191";
    String attackChainNodeId2 = "1838c23d-3bbe-4d8e-ba40-aa8b5fd1614d";

    Set<String> attackChainNodeIds = Set.of(attackChainNodeId1, attackChainNodeId2);

    List<RawAttackChainNodeExpectation> expectations =
        List.of(
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION.toString(), 100.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION.toString(), 100.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.VULNERABILITY.toString(), 100.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL.toString(), 0.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION.toString(), 50.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION.toString(), 100.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.VULNERABILITY.toString(), 100.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL.toString(), 0.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.PREVENTION.toString(), 0.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.DETECTION.toString(), 100.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.VULNERABILITY.toString(), 100.0, 100.0),
            createDefaultAttackChainNodeExpectation(
                AttackChainNodeExpectation.EXPECTATION_TYPE.MANUAL.toString(), 0.0, 100.0));
    when(attackChainNodeExpectationRepository.rawForComputeGlobalByAttackChainNodeIds(attackChainNodeIds))
        .thenReturn(expectations);

    var result = resultUtils.computeGlobalExpectationResults(attackChainNodeIds);

    ExpectationResultsByType expectedPreventionResult =
        createDefaultExpectationResultsByType(
            PREVENTION, AttackChainNodeExpectation.EXPECTATION_STATUS.PARTIAL, 1, 0, 1, 1);
    ExpectationResultsByType expectedDetectionResult =
        createDefaultExpectationResultsByType(
            DETECTION, AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS, 3, 0, 0, 0);
    ExpectationResultsByType expectedVulnerabilityResult =
        createDefaultExpectationResultsByType(
            VULNERABILITY, AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS, 3, 0, 0, 0);
    ExpectationResultsByType expectedHumanResponseResult =
        createDefaultExpectationResultsByType(
            HUMAN_RESPONSE, AttackChainNodeExpectation.EXPECTATION_STATUS.FAILED, 0, 0, 0, 3);

    List<ExpectationResultsByType> expectedPreventionResult1 =
        List.of(
            expectedPreventionResult,
            expectedDetectionResult,
            expectedVulnerabilityResult,
            expectedHumanResponseResult);

    assertEquals(expectedPreventionResult1, result);
  }
}
