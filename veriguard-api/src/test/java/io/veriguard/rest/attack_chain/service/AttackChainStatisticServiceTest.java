package io.veriguard.rest.attack_chain.service;

import static io.veriguard.expectation.ExpectationType.*;
import static io.veriguard.utils.fixtures.RawFinishedAttackChainRunWithAttackChainNodesFixture.createDefaultRawFinishedAttackChainRunWithAttackChainNodes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import io.veriguard.IntegrationTest;
import io.veriguard.database.raw.RawFinishedAttackChainRunWithAttackChainNodes;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.rest.attack_chain.response.AttackChainStatistic;
import io.veriguard.rest.attack_chain.response.GlobalScoreBySimulationEndDate;
import io.veriguard.utils.ResultUtils;
import io.veriguard.utils.fixtures.ExpectationResultsByTypeFixture;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttackChainStatisticServiceTest extends IntegrationTest {
  @Mock private AttackChainRunRepository attackChainRunRepository;
  @Mock private ResultUtils resultUtils;

  @InjectMocks private AttackChainStatisticService attackChainStatisticService;

  @BeforeEach
  void setUp() {
    attackChainStatisticService =
        new AttackChainStatisticService(attackChainRunRepository, resultUtils);
  }

  @Test
  @DisplayName("Should get scenario statistics")
  void getAttackChainStatistics() {
    String attackChainId = "e6773fee-b901-47af-8050-033b4d387fb6";
    String attackChainNodeId1 = "103da74a-055b-40e2-a934-9605cd3e4191";
    String attackChainNodeId2 = "1838c23d-3bbe-4d8e-ba40-aa8b5fd1614d";
    String attackChainNodeId3 = "0f728b68-ec1f-4a5d-a2e5-53d897c7a7fd";
    String attackChainNodeId4 = "92b0531d-b32f-4a22-9bfd-65c773c30e61";

    Set<String> attackChainRun1AttackChainNodeIds =
        Set.of(attackChainNodeId1, attackChainNodeId2, attackChainNodeId3);
    Set<String> attackChainRun2AttackChainNodeIds = Set.of(attackChainNodeId4);

    Instant attackChainRun1EndDate = Instant.parse("2023-12-12T10:15:30.00Z");
    Instant attackChainRun2EndDate = Instant.parse("2023-12-10T11:15:30.00Z");

    RawFinishedAttackChainRunWithAttackChainNodes rawFinishedAttackChainRunWithAttackChainNodes1 =
        createDefaultRawFinishedAttackChainRunWithAttackChainNodes(
            attackChainRun1EndDate, attackChainRun1AttackChainNodeIds);
    RawFinishedAttackChainRunWithAttackChainNodes rawFinishedAttackChainRunWithAttackChainNodes2 =
        createDefaultRawFinishedAttackChainRunWithAttackChainNodes(
            attackChainRun2EndDate, attackChainRun2AttackChainNodeIds);

    when(attackChainRunRepository
            .rawLatestFinishedAttackChainRunsWithAttackChainNodesByAttackChainId(attackChainId))
        .thenReturn(
            List.of(
                rawFinishedAttackChainRunWithAttackChainNodes1,
                rawFinishedAttackChainRunWithAttackChainNodes2));

    when(resultUtils.computeGlobalExpectationResults(attackChainRun1AttackChainNodeIds))
        .thenReturn(ExpectationResultsByTypeFixture.attackChainRun1GlobalScores);
    when(resultUtils.computeGlobalExpectationResults(attackChainRun2AttackChainNodeIds))
        .thenReturn(ExpectationResultsByTypeFixture.attackChainRun2GlobalScores);

    AttackChainStatistic result = attackChainStatisticService.getStatistics(attackChainId);

    Map<ExpectationType, List<GlobalScoreBySimulationEndDate>> expected =
        Map.of(
            ExpectationType.PREVENTION,
            List.of(
                new GlobalScoreBySimulationEndDate(attackChainRun2EndDate, 0),
                new GlobalScoreBySimulationEndDate(attackChainRun1EndDate, 33.4F)),
            DETECTION,
            List.of(
                new GlobalScoreBySimulationEndDate(attackChainRun2EndDate, 0),
                new GlobalScoreBySimulationEndDate(attackChainRun1EndDate, 100)));
    assertEquals(expected, result.simulationsResultsLatest().globalScoresByExpectationType());
  }
}
