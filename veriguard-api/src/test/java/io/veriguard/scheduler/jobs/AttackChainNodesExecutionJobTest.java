package io.veriguard.scheduler.jobs;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.AttackChainEdgesRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.SecurityCoverageSendJobRepository;
import io.veriguard.rest.attack_chain_run.service.AttackChainRunService;
import io.veriguard.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.veriguard.utils.fixtures.*;
import io.veriguard.utils.fixtures.composers.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttackChainNodesExecutionJobTest extends IntegrationTest {

  @Autowired private AttackChainNodesExecutionJob job;

  @Autowired private AttackChainRunService attackChainRunService;
  @Autowired private AttackChainNodeRepository attackChainNodeRepository;

  @Autowired private AttackChainRunComposer attackChainRunComposer;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private AttackChainNodeStatusComposer attackChainNodeStatusComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private SecurityCoverageSendJobRepository securityCoverageSendJobRepository;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;

  @Mock private AttackChainEdgesRepository attackChainEdgesRepository;

  @BeforeAll
  public void init() {
    MockitoAnnotations.openMocks(this);
  }

  @DisplayName("Not start children injects at the same time as parent injects")
  @Test
  void given_cron_in_one_minute_should_not_start_children_attackChainNodes()
      throws JobExecutionException {
    // -- PREPARE --
    AttackChainRun attackChainRun = AttackChainRunFixture.getAttackChainRun();
    attackChainRun.setStart(Instant.now().minus(1, ChronoUnit.MINUTES));
    AttackChainRun attackChainRunSaved =
        this.attackChainRunService.createAttackChainRun(attackChainRun);
    AttackChainNode attackChainNodeParent =
        attackChainNodeComposer
            .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
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
    AttackChainNode attackChainNodeChildren =
        attackChainNodeComposer
            .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
            .withEndpoint(
                endpointComposer
                    .forEndpoint(EndpointFixture.createEndpoint())
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
            .withAttackChainNodeStatus(
                attackChainNodeStatusComposer.forAttackChainNodeStatus(
                    AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus()))
            .withDependsOn(attackChainNodeParent)
            .persist()
            .get();
    entityManager.flush();

    attackChainNodeParent.setAttackChainRun(attackChainRunSaved);
    attackChainNodeChildren.setAttackChainRun(attackChainRunSaved);
    attackChainNodeParent.setStatus(null);
    attackChainNodeChildren.setStatus(null);
    attackChainRunSaved.setAttackChainNodes(
        new ArrayList<>(List.of(attackChainNodeParent, attackChainNodeChildren)));
    String attackChainRunId = attackChainRunSaved.getId();

    attackChainNodeRepository.saveAll(
        new ArrayList<>(List.of(attackChainNodeParent, attackChainNodeChildren)));
    entityManager.flush();
    // -- EXECUTE --
    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    List<AttackChainNode> attackChainNodesSaved =
        attackChainNodeRepository.findByAttackChainRunId(attackChainRunId);
    Optional<AttackChainNode> savedAttackChainNodeParent =
        attackChainNodesSaved.stream()
            .filter(
                attackChainNode -> attackChainNode.getId().equals(attackChainNodeParent.getId()))
            .findFirst();
    Optional<AttackChainNode> savedAttackChainNodeChildren =
        attackChainNodesSaved.stream()
            .filter(
                attackChainNode -> attackChainNode.getId().equals(attackChainNodeChildren.getId()))
            .findFirst();
    // Checking that only the parent attackChainNode has a status
    assertTrue(savedAttackChainNodeParent.isPresent());
    assertTrue(savedAttackChainNodeChildren.isPresent());

    assertTrue(savedAttackChainNodeParent.get().getStatus().isPresent());
    assertTrue(savedAttackChainNodeChildren.get().getStatus().isEmpty());

    assertNotNull(savedAttackChainNodeParent.get().getStatus().get().getName());
  }

  @Test
  @DisplayName("When auto closing of stix-created simulation, trigger stix coverage job")
  public void whenAutoClosingStixCreatedSimulation_TriggerStixCoverageJob()
      throws JobExecutionException {
    AttackChainRunComposer.Composer attackChainRunWrapper =
        attackChainRunComposer
            .forAttackChainRun(AttackChainRunFixture.createDefaultAttackChainRun())
            .withSecurityCoverage(
                securityCoverageComposer.forSecurityCoverage(
                    SecurityCoverageFixture.createDefaultSecurityCoverage()))
            .withAttackChainNode(
                attackChainNodeComposer
                    .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
                    .withAttackChainNodeStatus(
                        attackChainNodeStatusComposer.forAttackChainNodeStatus(
                            AttackChainNodeStatusFixture.createSuccessStatus())))
            .withAttackChainNode(
                attackChainNodeComposer
                    .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
                    .withAttackChainNodeStatus(
                        attackChainNodeStatusComposer.forAttackChainNodeStatus(
                            AttackChainNodeStatusFixture.createSuccessStatus())));

    attackChainNodeComposer.generatedItems.forEach(
        i -> i.setCollectExecutionStatus(CollectExecutionStatus.COMPLETED));
    attackChainRunWrapper.get().setStatus(AttackChainRunStatus.RUNNING);
    attackChainRunWrapper.persist();
    entityManager.flush();

    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // assert
    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobRepository.findBySimulation(attackChainRunWrapper.get());
    assertThat(job).isNotEmpty();
  }

  @Test
  @DisplayName(
      "When auto closing of NON stix-created simulation, DOES NOT trigger stix coverage job")
  public void whenAutoClosingNONStixCreatedSimulation_DoesNotTriggerStixCoverageJob()
      throws JobExecutionException {
    AttackChainRunComposer.Composer attackChainRunWrapper =
        attackChainRunComposer
            .forAttackChainRun(AttackChainRunFixture.createDefaultAttackChainRun())
            .withAttackChainNode(
                attackChainNodeComposer
                    .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
                    .withAttackChainNodeStatus(
                        attackChainNodeStatusComposer.forAttackChainNodeStatus(
                            AttackChainNodeStatusFixture.createSuccessStatus())))
            .withAttackChainNode(
                attackChainNodeComposer
                    .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
                    .withAttackChainNodeStatus(
                        attackChainNodeStatusComposer.forAttackChainNodeStatus(
                            AttackChainNodeStatusFixture.createSuccessStatus())));

    attackChainNodeComposer.generatedItems.forEach(
        i -> i.setCollectExecutionStatus(CollectExecutionStatus.COMPLETED));
    attackChainRunWrapper.get().setStatus(AttackChainRunStatus.RUNNING);
    attackChainRunWrapper.persist();
    entityManager.flush();

    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // assert
    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobRepository.findBySimulation(attackChainRunWrapper.get());
    assertThat(job).isEmpty();
  }

  @Test
  @DisplayName(
      "When auto closing of NON stix-created simulation, DOES NOT trigger stix coverage job")
  public void shouldRaiseExceptionIfExpectationMalicious() {
    ReflectionTestUtils.setField(job, "injectDependenciesRepository", attackChainEdgesRepository);
    AttackChainNode attackChainNode =
        attackChainNodeComposer
            .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
            .get();
    attackChainNode.setId(UUID.randomUUID().toString());
    AttackChainEdge attackChainEdge = new AttackChainEdge();
    attackChainEdge
        .getCompositeId()
        .setAttackChainNodeParent(
            AttackChainNodeFixture.createAttackChainNodeWithManualExpectation(
                NodeContractFixture.createDefaultNodeContract(),
                "parent",
                "T(java.lang.Runtime).getRuntime().exec('gedit');"));
    attackChainEdge
        .getCompositeId()
        .setAttackChainNodeChildren(AttackChainNodeFixture.getDefaultAttackChainNode());
    attackChainEdge.setAttackChainEdgeCondition(
        new AttackChainEdgeConditions.AttackChainEdgeCondition());
    AttackChainEdgeConditions.Condition condition = new AttackChainEdgeConditions.Condition();
    condition.setOperator(AttackChainEdgeConditions.DependencyOperator.eq);
    condition.setValue(true);
    condition.setKey("T(java.lang.Runtime).getRuntime().exec('gedit');");
    attackChainEdge.getAttackChainEdgeCondition().setConditions(List.of(condition));
    when(attackChainEdgesRepository.findParents(any())).thenReturn(List.of(attackChainEdge));
    try {
      this.job.checkErrorMessagesPreExecution(UUID.randomUUID().toString(), attackChainNode);
      fail("Should have raised an exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ErrorMessagesPreExecutionException.class);
      assertThat(e.getMessage())
          .isEqualTo("There was an error during the evaluation of the condition of the inject");
    }
  }

  @Nested
  @DisplayName("handlePendingInject")
  class HandlePendingAttackChainNodeTest {

    @Test
    @DisplayName("given pending inject without traces should mark status as error with timeout")
    void given_pendingAttackChainNodeWithoutTraces_should_markStatusAsMaybePrevented() {
      // Arrange
      AttackChainNodeStatus statusToSave =
          AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
      statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
      AttackChainNode attackChainNode =
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withAttackChainNodeStatus(
                  attackChainNodeStatusComposer.forAttackChainNodeStatus(statusToSave))
              .persist()
              .get();
      entityManager.flush();

      // Act
      job.handlePendingAttackChainNode();
      entityManager.flush();
      entityManager.clear();

      // Assert
      AttackChainNode savedAttackChainNode =
          attackChainNodeRepository.findById(attackChainNode.getId()).orElseThrow();
      AttackChainNodeStatus savedStatus = savedAttackChainNode.getStatus().orElseThrow();
      assertEquals(ExecutionStatus.ERROR, savedStatus.getName());
    }

    @Test
    @DisplayName(
        "given pending inject without complete traces should mark status as error with timeout")
    void given_pendingAttackChainNodeWithoutCompleteTraces_should_markStatusAsMaybePrevented() {
      // Arrange
      AgentComposer.Composer agentComposerRef =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      AttackChainNodeStatus statusToSave =
          AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
      statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
      AttackChainNodeStatusComposer.Composer statusComposer =
          attackChainNodeStatusComposer
              .forAttackChainNodeStatus(statusToSave)
              .withExecutionTrace(
                  executionTraceComposer
                      .forExecutionTrace(ExecutionTraceFixture.createDefaultExecutionTraceStart())
                      .withAgent(agentComposerRef));

      AttackChainNode attackChainNode =
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withEndpoint(
                  endpointComposer
                      .forEndpoint(EndpointFixture.createEndpoint())
                      .withAgent(agentComposerRef))
              .withAttackChainNodeStatus(statusComposer)
              .persist()
              .get();
      entityManager.flush();

      // Act
      job.handlePendingAttackChainNode();
      entityManager.flush();
      entityManager.clear();

      // Assert
      AttackChainNode savedAttackChainNode =
          attackChainNodeRepository.findById(attackChainNode.getId()).orElseThrow();
      AttackChainNodeStatus savedStatus = savedAttackChainNode.getStatus().orElseThrow();
      assertEquals(ExecutionStatus.ERROR, savedStatus.getName());
      assertTrue(
          savedStatus.getTraces().stream()
              .anyMatch(
                  trace ->
                      ExecutionTraceStatus.TIMEOUT.equals(trace.getStatus())
                          && trace.getMessage().contains("did not respond within the")));
    }

    @Test
    @DisplayName("given pending inject with complete traces should compute final status")
    void given_pendingAttackChainNodeWithCompleteTraces_should_computeFinalStatus() {
      // Arrange
      AgentComposer.Composer agentComposerRef =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      AttackChainNodeStatus statusToSave =
          AttackChainNodeStatusFixture.createPendingAttackChainNodeStatus();
      statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
      AttackChainNodeStatusComposer.Composer statusComposer =
          attackChainNodeStatusComposer
              .forAttackChainNodeStatus(statusToSave)
              .withExecutionTrace(
                  executionTraceComposer
                      .forExecutionTrace(
                          ExecutionTraceFixture.createDefaultExecutionTraceComplete())
                      .withAgent(agentComposerRef));

      AttackChainNode attackChainNode =
          attackChainNodeComposer
              .forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode())
              .withEndpoint(
                  endpointComposer
                      .forEndpoint(EndpointFixture.createEndpoint())
                      .withAgent(agentComposerRef))
              .withAttackChainNodeStatus(statusComposer)
              .persist()
              .get();
      entityManager.flush();

      // Act
      job.handlePendingAttackChainNode();
      entityManager.flush();
      entityManager.clear();

      // Assert
      AttackChainNode savedAttackChainNode =
          attackChainNodeRepository.findById(attackChainNode.getId()).orElseThrow();
      AttackChainNodeStatus savedStatus = savedAttackChainNode.getStatus().orElseThrow();
      assertEquals(ExecutionStatus.SUCCESS, savedStatus.getName());
      assertTrue(
          savedStatus.getTraces().stream()
              .noneMatch(trace -> ExecutionTraceStatus.WARNING.equals(trace.getStatus())));
    }
  }
}
