package io.veriguard.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.veriguard.attackchain.execution.EdgeConditionEvaluator;
import io.veriguard.attackchain.execution.LinkExpectationService;
import io.veriguard.attackchain.execution.LinkVerdictCalculator;
import io.veriguard.database.model.*;
import io.veriguard.database.model.LinkVerdict;
import io.veriguard.database.repository.AttackChainEdgesRepository;
import io.veriguard.database.repository.AttackChainLinkExpectationRepository;
import io.veriguard.database.repository.AttackChainNodeExpectationRepository;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.executors.Executor;
import io.veriguard.helper.AttackChainNodeHelper;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeStatusService;
import io.veriguard.service.NotificationEventService;
import io.veriguard.service.SecurityCoverageSendJobService;
import io.veriguard.service.attack_chain.AttackChainService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

/**
 * Phase 12c-Biii: 动态节点持久化 + cleanup hook 的纯 Mockito 单元测试.
 *
 * <p>独立测试类，不继承 IntegrationTest，避免 Spring 上下文和 DB 依赖.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttackChainNodesExecutionJobDynamicContractsTest {

  @Mock private Environment env;
  @Mock private AttackChainNodeHelper attackChainNodeHelper;
  @Mock private AttackChainNodeService attackChainNodeService;
  @Mock private AttackChainRunRepository attackChainRunRepository;
  @Mock private AttackChainEdgesRepository attackChainEdgesRepository;
  @Mock private AttackChainNodeExpectationRepository attackChainNodeExpectationRepository;
  @Mock private AttackChainNodeStatusService attackChainNodeStatusService;
  @Mock private Executor executor;
  @Mock private NotificationEventService notificationEventService;
  @Mock private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Mock private LinkVerdictCalculator linkVerdictCalculator;
  @Mock private LinkExpectationService linkExpectationService;
  @Mock private EdgeConditionEvaluator edgeConditionEvaluator;
  @Mock private AttackChainLinkExpectationRepository attackChainLinkExpectationRepository;
  @Mock private AttackChainService attackChainService;
  @Mock private AttackChainNodeRepository attackChainNodeRepository;

  private AttackChainNodesExecutionJob job;

  @BeforeEach
  void setUp() {
    // 手动构造避免 @PostConstruct 对 Environment 的依赖影响测试
    job =
        new AttackChainNodesExecutionJob(
            env,
            attackChainNodeHelper,
            attackChainNodeService,
            attackChainRunRepository,
            attackChainEdgesRepository,
            attackChainNodeExpectationRepository,
            attackChainNodeStatusService,
            executor,
            notificationEventService,
            securityCoverageSendJobService,
            linkVerdictCalculator,
            linkExpectationService,
            edgeConditionEvaluator,
            attackChainLinkExpectationRepository,
            attackChainService,
            attackChainNodeRepository);
  }

  // -- Scenario 1: empty filter → no dynamic node saved --

  @DisplayName("空 dynamic_filter 时，不持久化任何动态节点")
  @Test
  void givenEmptyDynamicFilter_whenHandleAutoStart_thenNoNodeSaved() {
    // Arrange
    AttackChain chain = new AttackChain();
    AttackChainRun run = new AttackChainRun();
    run.setAttackChain(chain);

    when(attackChainRunRepository.findAllShouldBeInRunningState(any())).thenReturn(List.of(run));
    when(attackChainService.computeDynamicContracts(chain)).thenReturn(List.of());
    when(attackChainRunRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    // Act
    job.handleAutoStartAttackChainRuns();

    // Assert
    verify(attackChainNodeRepository, never()).save(any());
    verify(attackChainNodeRepository, never()).saveAll(any());
  }

  // -- Scenario 2: two contracts → two dynamic nodes saved --

  @DisplayName("2 个 NodeContract 时，save 2 个 is_dynamic=true 的动态节点")
  @Test
  void givenTwoContracts_whenHandleAutoStart_thenTwoDynamicNodesSaved() {
    // Arrange
    String runId = "run-111";
    String contractId1 = "c1-uuid";
    String contractId2 = "c2-uuid";

    AttackChain chain = new AttackChain();
    AttackChainRun run = new AttackChainRun();
    run.setId(runId);
    run.setAttackChain(chain);

    NodeContract contract1 = new NodeContract();
    contract1.setId(contractId1);
    contract1.setLabels(Map.of("en", "Contract One"));

    NodeContract contract2 = new NodeContract();
    contract2.setId(contractId2);
    contract2.setLabels(Map.of("en", "Contract Two"));

    when(attackChainRunRepository.findAllShouldBeInRunningState(any())).thenReturn(List.of(run));
    when(attackChainService.computeDynamicContracts(chain))
        .thenReturn(List.of(contract1, contract2));
    when(attackChainRunRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    // Act
    job.handleAutoStartAttackChainRuns();

    // Assert
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<AttackChainNode>> captor = ArgumentCaptor.forClass(List.class);
    verify(attackChainNodeRepository, times(1)).saveAll(captor.capture());
    verify(attackChainNodeRepository, never()).save(any());

    List<AttackChainNode> saved = captor.getValue();
    assertThat(saved).hasSize(2);

    AttackChainNode node1 = saved.get(0);
    assertThat(node1.getId()).isEqualTo("dynamic-" + contractId1 + "-" + runId);
    assertThat(node1.isDynamic()).isTrue();
    assertThat(node1.getDependsDuration()).isEqualTo(0L);
    assertThat(node1.getRepeatCount()).isEqualTo(1);

    AttackChainNode node2 = saved.get(1);
    assertThat(node2.getId()).isEqualTo("dynamic-" + contractId2 + "-" + runId);
    assertThat(node2.isDynamic()).isTrue();
    assertThat(node2.getDependsDuration()).isEqualTo(0L);
    assertThat(node2.getRepeatCount()).isEqualTo(1);
  }

  // -- Scenario 3: close hook → deleteByAttackChainIdAndIsDynamicTrue called --

  @DisplayName("run 转 FINISHED 时，close hook 调用 deleteByAttackChainIdAndIsDynamicTrue")
  @Test
  void givenFinishingRun_whenHandleAutoClose_thenCleanupInvoked() {
    // Arrange
    String chainId = "chain-abc";
    AttackChain chain = new AttackChain();
    chain.setId(chainId);

    AttackChainRun run = new AttackChainRun();
    run.setId("run-999");
    run.setAttackChain(chain);
    // 模拟 run 具备 start date (required by thatMustBeFinished semantics)
    run.setStart(Instant.now().minusSeconds(3600));

    when(attackChainRunRepository.thatMustBeFinished()).thenReturn(List.of(run));
    when(attackChainRunRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    when(attackChainNodeRepository.deleteByAttackChainIdAndIsDynamicTrue(chainId)).thenReturn(0);
    when(attackChainLinkExpectationRepository.findByAttackChainRunId(any())).thenReturn(List.of());
    when(linkVerdictCalculator.compute(any(), any()))
        .thenReturn(new LinkVerdictCalculator.LinkVerdictResult(LinkVerdict.N_A, LinkVerdict.N_A));
    doNothing()
        .when(securityCoverageSendJobService)
        .createOrUpdateCoverageSendJobForSimulationsIfReady(any());

    // Act
    job.handleAutoClosingAttackChainRuns();

    // Assert
    verify(attackChainNodeRepository, times(1)).deleteByAttackChainIdAndIsDynamicTrue(chainId);
  }
}
