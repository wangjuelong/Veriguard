package io.veriguard.rest.attack_chain_run.service;

import static io.veriguard.utils.NodeExpectationResultUtils.getResultDetail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.expectation.ExpectationType;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeDuplicateService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.attack_chain_run.form.AttackChainRunsGlobalScoresInput;
import io.veriguard.rest.document.DocumentService;
import io.veriguard.service.*;
import io.veriguard.service.attack_chain.AttackChainRecurrenceService;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import io.veriguard.utils.ResultUtils;
import io.veriguard.utils.TargetType;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.AttackChainComposer;
import io.veriguard.utils.fixtures.composers.AttackChainRunComposer;
import io.veriguard.utils.mapper.AttackChainNodeExpectationMapper;
import io.veriguard.utils.mapper.AttackChainNodeMapper;
import io.veriguard.utils.mapper.AttackChainRunMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
@ExtendWith(MockitoExtension.class)
class AttackChainRunServiceTest extends IntegrationTest {

  @Mock private AttackChainNodeDuplicateService attackChainNodeDuplicateService;
  @Mock private TeamService teamService;
  @Mock private VariableService variableService;
  @Mock private TagRuleService tagRuleService;
  @Mock private DocumentService documentService;
  @Mock private AttackChainNodeService attackChainNodeService;
  @Mock private UserService userService;

  @Mock private AttackChainRunMapper attackChainRunMapper;
  @Mock private AttackChainNodeMapper attackChainNodeMapper;
  @Mock private ResultUtils resultUtils;

  @Mock private AssetRepository assetRepository;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  @Mock private AttackChainRunRepository attackChainRunRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;
  @Mock private AttackChainNodeRepository attackChainNodeRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;
  @Mock private GrantService grantService;
  @Mock private AttackChainRunTeamUserService attackChainRunTeamUserService;

  @Autowired private AttackChainComposer attackChainComposer;
  @Autowired private AttackChainRunComposer attackChainRunComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private AttackChainRunService actualAttackChainRunService;

  @Autowired private LessonsService lessonsService;
  @Autowired private FileService fileService;
  @Autowired private PauseAttackChainRunService pauseAttackChainRunService;
  @Autowired private AttackChainRecurrenceService attackChainRecurrenceService;

  @Mock private AttackChainNodeExpectationMapper attackChainNodeExpectationMapper;

  @InjectMocks private AttackChainRunService mockedAttackChainRunService;

  @BeforeEach
  void setUp() {
    mockedAttackChainRunService =
        new AttackChainRunService(
            attackChainNodeDuplicateService,
            teamService,
            variableService,
            tagRuleService,
            documentService,
            attackChainNodeService,
            userService,
            grantService,
            attackChainRunTeamUserService,
            attackChainRunMapper,
            attackChainNodeMapper,
            resultUtils,
            assetRepository,
            assetGroupRepository,
            attackChainNodeExpectationRepository,
            attackChainRunRepository,
            teamRepository,
            userRepository,
            attackChainRunTeamUserRepository,
            attackChainNodeRepository,
            lessonsCategoryRepository,
            attackChainNodeExpectationMapper,
            attackChainRecurrenceService,
            pauseAttackChainRunService,
            fileService,
            lessonsService);

    attackChainComposer.reset();
    attackChainRunComposer.reset();
  }

  @Test
  @DisplayName("Should get exercises global scores")
  void getAttackChainRunsGlobalScores() {
    String attackChainRunId1 = "3e95b1ea-8957-4452-b0f7-edf4003eaa98";
    String attackChainRunId2 = "c740797e-e34c-4066-a16c-a8baad9058f9";

    String attackChainNodeId1 = "103da74a-055b-40e2-a934-9605cd3e4191";
    String attackChainNodeId2 = "1838c23d-3bbe-4d8e-ba40-aa8b5fd1614d";
    String attackChainNodeId3 = "0f728b68-ec1f-4a5d-a2e5-53d897c7a7fd";
    String attackChainNodeId4 = "bf05a17a-af6b-4238-9c3e-296db7f07d00";

    Set<String> attackChainRun1AttackChainNodeIds =
        Set.of(attackChainNodeId1, attackChainNodeId2, attackChainNodeId3);
    Set<String> attackChainRun2AttackChainNodeIds = Set.of(attackChainNodeId4);

    when(attackChainRunRepository.findAttackChainNodesByAttackChainRun(attackChainRunId1))
        .thenReturn(attackChainRun1AttackChainNodeIds);
    when(attackChainRunRepository.findAttackChainNodesByAttackChainRun(attackChainRunId2))
        .thenReturn(attackChainRun2AttackChainNodeIds);

    when(resultUtils.computeGlobalExpectationResults(attackChainRun1AttackChainNodeIds))
        .thenReturn(ExpectationResultsByTypeFixture.attackChainRun1GlobalScores);
    when(resultUtils.computeGlobalExpectationResults(attackChainRun2AttackChainNodeIds))
        .thenReturn(ExpectationResultsByTypeFixture.attackChainRun2GlobalScores);

    var results =
        mockedAttackChainRunService.getAttackChainRunsGlobalScores(
            new AttackChainRunsGlobalScoresInput(List.of(attackChainRunId1, attackChainRunId2)));

    assertEquals(
        results.globalScoresByAttackChainRunIds(),
        Map.of(
            attackChainRunId1, ExpectationResultsByTypeFixture.attackChainRun1GlobalScores,
            attackChainRunId2, ExpectationResultsByTypeFixture.attackChainRun2GlobalScores));
  }

  @Test
  public void testUpdateExercise_WITH_apply_rule_true() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    AttackChainNode attackChainNode1 = new AttackChainNode();
    attackChainNode1.setId("1");
    AttackChainNode attackChainNode2 = new AttackChainNode();
    attackChainNode1.setId("2");
    AttackChainRun attackChainRun = AttackChainRunFixture.getAttackChainRun(null);
    attackChainRun.setAttackChainNodes(List.of(attackChainNode1, attackChainNode2));
    attackChainRun.setTags(new HashSet<>(Set.of(tag1, tag2)));
    Set<Tag> currentTags = new HashSet<>(Set.of(tag2, tag3));
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(attackChainRunRepository.save(attackChainRun)).thenReturn(attackChainRun);
    when(attackChainNodeService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS)))
        .thenReturn(true);

    mockedAttackChainRunService.updateExercice(attackChainRun, currentTags, true);

    attackChainRun
        .getAttackChainNodes()
        .forEach(
            attackChainNode ->
                verify(attackChainNodeService)
                    .applyDefaultAssetGroupsToAttackChainNode(
                        attackChainNode.getId(), assetGroupsToAdd));
    verify(attackChainRunRepository).save(attackChainRun);
  }

  @Test
  public void testUpdateExercise_WITH_apply_rule_true_and_manual_attackChainNode() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    AttackChainNode attackChainNode1 = new AttackChainNode();
    attackChainNode1.setId("1");
    AttackChainNode attackChainNode2 = new AttackChainNode();
    attackChainNode1.setId("2");
    AttackChainRun attackChainRun = AttackChainRunFixture.getAttackChainRun(null);
    attackChainRun.setAttackChainNodes(List.of(attackChainNode1, attackChainNode2));
    attackChainRun.setTags(new HashSet<>(Set.of(tag1, tag2)));
    Set<Tag> currentTags = new HashSet<>(Set.of(tag2, tag3));
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(attackChainRunRepository.save(attackChainRun)).thenReturn(attackChainRun);
    when(attackChainNodeService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS)))
        .thenReturn(false);

    mockedAttackChainRunService.updateExercice(attackChainRun, currentTags, true);

    verify(attackChainNodeService, never()).applyDefaultAssetGroupsToAttackChainNode(any(), any());
    verify(attackChainRunRepository).save(attackChainRun);
  }

  @Test
  public void testUpdateExercise_WITH_apply_rule_false() {
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    AttackChainNode attackChainNode1 = new AttackChainNode();
    attackChainNode1.setId("1");
    AttackChainNode attackChainNode2 = new AttackChainNode();
    attackChainNode1.setId("2");
    AttackChainRun attackChainRun = AttackChainRunFixture.getAttackChainRun(null);
    attackChainRun.setAttackChainNodes(List.of(attackChainNode1, attackChainNode2));
    attackChainRun.setTags(new HashSet<>(Set.of(tag1, tag2)));
    Set<Tag> currentTags = new HashSet<>(Set.of(tag2, tag3));

    when(attackChainRunRepository.save(attackChainRun)).thenReturn(attackChainRun);

    mockedAttackChainRunService.updateExercice(attackChainRun, currentTags, false);

    verify(attackChainNodeService, never()).applyDefaultAssetGroupsToAttackChainNode(any(), any());
  }

  @Test
  public void test_isThereAScoreDegradation_with_same_results() {
    List<Double> scores = List.of(1.0, 1.0, 0.0, 0.5);

    Map<ExpectationType, ExpectationResultsByType> resultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));

    assertFalse(mockedAttackChainRunService.isThereAScoreDegradation(resultsMap, resultsMap));
  }

  @Test
  public void test_isThereAScoreDegradation_with_lower_result() {
    List<Double> scores = List.of(1.0, 1.0, 0.0, 0.5, 1.0);
    List<Double> lowerScores = List.of(1.0, 1.0, 0.0, 0.5, 0.0);

    Map<ExpectationType, ExpectationResultsByType> lastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));
    assertTrue(
        mockedAttackChainRunService.isThereAScoreDegradation(lastResultsMap, secondLastResultsMap));
  }

  @Test
  public void test_isThereAScoreDegradation_WITH_manual_expectation() {
    List<Double> scores = List.of(1.0, 1.0, 0.0, 0.5, 1.0);
    List<Double> lowerScores = List.of(1.0, 1.0, 0.0, 0.5, 0.0);
    Map<ExpectationType, ExpectationResultsByType> lastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.HUMAN_RESPONSE,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.HUMAN_RESPONSE,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));
    assertFalse(
        mockedAttackChainRunService.isThereAScoreDegradation(lastResultsMap, secondLastResultsMap));
  }

  @Test
  public void test_isThereAScoreDegradation_WITH_expectation_pending() {
    List<Double> scores = List.of(1.0, 1.0, 0.0, 0.5, 1.0);
    List<Double> lowerScores = List.of(1.0, 1.0, 0.0, 0.5, 0.0);
    Map<ExpectationType, ExpectationResultsByType> lastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.PENDING,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));
  }

  @Nested
  @DisplayName("Tests for latest validity date")
  public class LatestValidityDate {
    @Nested
    @DisplayName("With recurring scenario")
    public class WithRecurringAttackChain {
      private AttackChainComposer.Composer attackChainWrapper;

      @BeforeEach
      public void setup() {
        attackChainWrapper =
            attackChainComposer.forAttackChain(
                AttackChainFixture.getAttackChainWithRecurrence("56 43 10 * * *"));
      }

      @Nested
      @DisplayName("With successor Simulation")
      public class WithSuccessorSimulation {
        private AttackChainRunComposer.Composer successorSimulationWrapper;
        private final Instant successorSimulationStartTime = Instant.parse("2022-04-24T01:02:03Z");

        @BeforeEach
        public void setup() {
          AttackChainRun successorFixture = AttackChainRunFixture.createDefaultAttackChainRun();
          successorFixture.setStart(successorSimulationStartTime);
          successorSimulationWrapper = attackChainRunComposer.forAttackChainRun(successorFixture);
        }

        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          AttackChainRunComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            AttackChainRun consideredFixture = AttackChainRunFixture.createDefaultAttackChainRun();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper =
                attackChainRunComposer.forAttackChainRun(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until successor start date")
          public void isValidUntilSuccessorStartDate() {
            attackChainWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            attackChainComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            attackChainRunComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualAttackChainRunService.getLatestValidityDate(
                    consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(successorSimulationStartTime);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          AttackChainRunComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            AttackChainRun consideredFixture = AttackChainRunFixture.createDefaultAttackChainRun();
            consideredSimulationWrapper =
                attackChainRunComposer.forAttackChainRun(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            attackChainWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            attackChainComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            attackChainRunComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualAttackChainRunService.getLatestValidityDate(
                    consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }

      @Nested
      @DisplayName("Without successor Simulation")
      public class WithoutSuccessorSimulation {
        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          AttackChainRunComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            AttackChainRun consideredFixture = AttackChainRunFixture.createDefaultAttackChainRun();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper =
                attackChainRunComposer.forAttackChainRun(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until next scenario trigger")
          public void isValidUntilNextAttackChainTrigger() {
            attackChainWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            attackChainComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            attackChainRunComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Instant expected = Instant.parse("2022-04-23T10:43:56Z");

            Optional<Instant> latestValidity =
                actualAttackChainRunService.getLatestValidityDate(
                    consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(expected);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          AttackChainRunComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            AttackChainRun consideredFixture = AttackChainRunFixture.createDefaultAttackChainRun();
            consideredSimulationWrapper =
                attackChainRunComposer.forAttackChainRun(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            attackChainWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            attackChainComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            attackChainRunComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualAttackChainRunService.getLatestValidityDate(
                    consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }
    }

    @Nested
    @DisplayName("With non-recurring scenario")
    public class WithNonRecurringAttackChain {
      private AttackChainComposer.Composer attackChainWrapper;

      @BeforeEach
      public void setup() {
        attackChainWrapper =
            attackChainComposer.forAttackChain(AttackChainFixture.getAttackChain());
      }

      @Nested
      @DisplayName("With successor Simulation")
      public class WithSuccessorSimulation {
        private AttackChainRunComposer.Composer successorSimulationWrapper;
        private final Instant successorSimulationStartTime = Instant.parse("2022-04-24T01:02:03Z");

        @BeforeEach
        public void setup() {
          AttackChainRun successorFixture = AttackChainRunFixture.createDefaultAttackChainRun();
          successorFixture.setStart(successorSimulationStartTime);
          successorSimulationWrapper = attackChainRunComposer.forAttackChainRun(successorFixture);
        }

        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          AttackChainRunComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            AttackChainRun consideredFixture = AttackChainRunFixture.createDefaultAttackChainRun();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper =
                attackChainRunComposer.forAttackChainRun(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until successor start date")
          public void isValidUntilSuccessorStartDate() {
            attackChainWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            attackChainComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            attackChainRunComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualAttackChainRunService.getLatestValidityDate(
                    consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(successorSimulationStartTime);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          AttackChainRunComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            AttackChainRun consideredFixture = AttackChainRunFixture.createDefaultAttackChainRun();
            consideredSimulationWrapper =
                attackChainRunComposer.forAttackChainRun(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            attackChainWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            attackChainComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            attackChainRunComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualAttackChainRunService.getLatestValidityDate(
                    consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }

      @Nested
      @DisplayName("Without successor Simulation")
      public class WithoutSuccessorSimulation {
        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          AttackChainRunComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            AttackChainRun consideredFixture = AttackChainRunFixture.createDefaultAttackChainRun();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper =
                attackChainRunComposer.forAttackChainRun(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilNextAttackChainTrigger() {
            attackChainWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            attackChainComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            attackChainRunComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualAttackChainRunService.getLatestValidityDate(
                    consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          AttackChainRunComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            AttackChainRun consideredFixture = AttackChainRunFixture.createDefaultAttackChainRun();
            consideredSimulationWrapper =
                attackChainRunComposer.forAttackChainRun(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            attackChainWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            attackChainComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            attackChainRunComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualAttackChainRunService.getLatestValidityDate(
                    consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }
    }
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }
}
