package io.veriguard.attackchain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.attackchain.soc.ConnectorNotFoundException;
import io.veriguard.attackchain.soc.CorrelationMatch;
import io.veriguard.attackchain.soc.CorrelationRuleQuery;
import io.veriguard.attackchain.soc.SocAlertConnector;
import io.veriguard.attackchain.soc.SocConnectorRegistry;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainLinkExpectation;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.LinkExpectationStatus;
import io.veriguard.database.model.SocCorrelationRuleRef;
import io.veriguard.database.repository.AttackChainLinkExpectationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkExpectationServiceTest {

  @Mock SocConnectorRegistry registry;
  @Mock AttackChainLinkExpectationRepository repository;
  @Mock SocAlertConnector connector;

  private LinkExpectationService service;

  @BeforeEach
  void setUp() {
    service =
        new LinkExpectationService(registry, repository) {
          @Override
          protected void sleep(long millis) {
            // 测试关掉 backoff sleep，加快迭代
          }
        };
  }

  // ---- instantiateForRun ----

  @Test
  @DisplayName("instantiateForRun: 模板有 socCorrelationRules → 每条物化一个 link expectation")
  void instantiate_creates_one_per_rule() {
    AttackChainRun run = run();
    AttackChain template = template(rule("elastic", "r1"), rule("elastic", "r2"));
    run.setAttackChain(template);
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    List<AttackChainLinkExpectation> created = service.instantiateForRun(run);

    assertThat(created).hasSize(2);
    assertThat(created.stream().map(AttackChainLinkExpectation::getStatus))
        .containsOnly(LinkExpectationStatus.PENDING);
    verify(repository, times(2)).save(any());
  }

  @Test
  @DisplayName("instantiateForRun: 已存在 link expectations → idempotent，不重复 save")
  void instantiate_idempotent() {
    AttackChainRun run = run();
    run.setAttackChain(template(rule("elastic", "r1")));
    AttackChainLinkExpectation existing = expectation(run, rule("elastic", "r1"));
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(existing));

    List<AttackChainLinkExpectation> result = service.instantiateForRun(run);

    assertThat(result).containsExactly(existing);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("instantiateForRun: 无模板 / null run / 无规则 → 空列表 + 不 save")
  void instantiate_no_template() {
    assertThat(service.instantiateForRun(null)).isEmpty();
    AttackChainRun runNoChain = run();
    assertThat(service.instantiateForRun(runNoChain)).isEmpty();
    AttackChainRun runEmptyRules = run();
    runEmptyRules.setAttackChain(template());
    assertThat(service.instantiateForRun(runEmptyRules)).isEmpty();
    verify(repository, never()).save(any());
  }

  // ---- findByRun ----

  @Test
  @DisplayName("findByRun: 委托 repository.findByAttackChainRunId")
  void findByRun_delegates_to_repository() {
    AttackChainRun run = run();
    AttackChainLinkExpectation a = expectation(run, rule("elastic", "r1"));
    AttackChainLinkExpectation b = expectation(run, rule("elastic", "r2"));
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(a, b));

    List<AttackChainLinkExpectation> result = service.findByRun(run.getId());

    assertThat(result).containsExactly(a, b);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("findByRun: null / blank id → 空列表，不查 repository")
  void findByRun_null_or_blank_id() {
    assertThat(service.findByRun(null)).isEmpty();
    assertThat(service.findByRun("")).isEmpty();
    assertThat(service.findByRun("   ")).isEmpty();
    verify(repository, never()).findByAttackChainRunId(anyString());
  }

  // ---- evaluateForRun ----

  @Test
  @DisplayName("evaluateForRun: connector 返回命中 → 写 trace、累加 score、状态终态化")
  void evaluate_writes_traces_and_finalizes() {
    AttackChainRun run = run();
    AttackChainLinkExpectation expectation = expectation(run, rule("elastic", "r1"));
    expectation.setExpectedScore(100);
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(expectation));
    when(registry.get("elastic")).thenReturn(connector);
    when(connector.queryCorrelationRule(any()))
        .thenReturn(
            List.of(
                new CorrelationMatch(
                    "i1", "rule-name", Instant.parse("2026-05-01T00:30:00Z"), 60, Map.of()),
                new CorrelationMatch(
                    "i2", "rule-name", Instant.parse("2026-05-01T00:31:00Z"), 50, Map.of())));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.evaluateForRun(run);

    assertThat(expectation.getStatus()).isEqualTo(LinkExpectationStatus.SUCCESS);
    assertThat(expectation.getScore()).isEqualTo(110);
    assertThat(expectation.getTraces()).hasSize(2);
    assertThat(expectation.getTraces().get(0).getIncidentId()).isEqualTo("i1");
    assertThat(expectation.getTraces().get(0).getScoreDelta()).isEqualTo(60);
  }

  @Test
  @DisplayName("evaluateForRun: 命中分数 < expected → PARTIAL")
  void evaluate_partial() {
    AttackChainRun run = run();
    AttackChainLinkExpectation expectation = expectation(run, rule("elastic", "r1"));
    expectation.setExpectedScore(100);
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(expectation));
    when(registry.get("elastic")).thenReturn(connector);
    when(connector.queryCorrelationRule(any()))
        .thenReturn(List.of(new CorrelationMatch("i1", "n", Instant.now(), 30, Map.of())));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.evaluateForRun(run);

    assertThat(expectation.getStatus()).isEqualTo(LinkExpectationStatus.PARTIAL);
    assertThat(expectation.getScore()).isEqualTo(30);
  }

  @Test
  @DisplayName("evaluateForRun: 0 命中 → FAILED")
  void evaluate_no_hits_failed() {
    AttackChainRun run = run();
    AttackChainLinkExpectation expectation = expectation(run, rule("elastic", "r1"));
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(expectation));
    when(registry.get("elastic")).thenReturn(connector);
    when(connector.queryCorrelationRule(any())).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.evaluateForRun(run);

    assertThat(expectation.getStatus()).isEqualTo(LinkExpectationStatus.FAILED);
    assertThat(expectation.getScore()).isEqualTo(0);
  }

  @Test
  @DisplayName("evaluateForRun: connector 抛错 重试 3 次后仍失败 → UNKNOWN")
  void evaluate_query_failure_after_retry() {
    AttackChainRun run = run();
    AttackChainLinkExpectation expectation = expectation(run, rule("elastic", "r1"));
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(expectation));
    when(registry.get("elastic")).thenReturn(connector);
    when(connector.queryCorrelationRule(any())).thenThrow(new RuntimeException("network down"));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.evaluateForRun(run);

    assertThat(expectation.getStatus()).isEqualTo(LinkExpectationStatus.UNKNOWN);
    verify(connector, times(LinkExpectationService.MAX_RETRY_ATTEMPTS)).queryCorrelationRule(any());
  }

  @Test
  @DisplayName("evaluateForRun: 重试中第 2 次成功 → 不再重试，正常落库")
  void evaluate_retry_eventually_succeeds() {
    AttackChainRun run = run();
    AttackChainLinkExpectation expectation = expectation(run, rule("elastic", "r1"));
    expectation.setExpectedScore(50);
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(expectation));
    when(registry.get("elastic")).thenReturn(connector);
    when(connector.queryCorrelationRule(any()))
        .thenThrow(new RuntimeException("transient"))
        .thenReturn(List.of(new CorrelationMatch("i1", "n", Instant.now(), 60, Map.of())));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.evaluateForRun(run);

    assertThat(expectation.getStatus()).isEqualTo(LinkExpectationStatus.SUCCESS);
    verify(connector, times(2)).queryCorrelationRule(any());
  }

  @Test
  @DisplayName("evaluateForRun: connector 不存在 → UNKNOWN，不抛上层")
  void evaluate_connector_missing() {
    AttackChainRun run = run();
    AttackChainLinkExpectation expectation = expectation(run, rule("nonexistent", "r1"));
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(expectation));
    when(registry.get("nonexistent")).thenThrow(new ConnectorNotFoundException("nonexistent"));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.evaluateForRun(run);

    assertThat(expectation.getStatus()).isEqualTo(LinkExpectationStatus.UNKNOWN);
    verify(connector, never()).queryCorrelationRule(any());
  }

  @Test
  @DisplayName("evaluateForRun: expirationTime 已过 → UNKNOWN，不查 SOC")
  void evaluate_expired() {
    AttackChainRun run = run();
    AttackChainLinkExpectation expectation = expectation(run, rule("elastic", "r1"));
    expectation.setExpirationTime(Instant.now().minusSeconds(3600));
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(expectation));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.evaluateForRun(run);

    assertThat(expectation.getStatus()).isEqualTo(LinkExpectationStatus.UNKNOWN);
    verify(registry, never()).get(anyString());
  }

  @Test
  @DisplayName("evaluateForRun: 已是 SUCCESS / FAILED / UNKNOWN 的不再查询")
  void evaluate_skips_terminal() {
    AttackChainRun run = run();
    AttackChainLinkExpectation success = expectation(run, rule("elastic", "r1"));
    success.setStatus(LinkExpectationStatus.SUCCESS);
    AttackChainLinkExpectation failed = expectation(run, rule("elastic", "r2"));
    failed.setStatus(LinkExpectationStatus.FAILED);
    AttackChainLinkExpectation unknown = expectation(run, rule("elastic", "r3"));
    unknown.setStatus(LinkExpectationStatus.UNKNOWN);
    when(repository.findByAttackChainRunId(run.getId()))
        .thenReturn(List.of(success, failed, unknown));

    service.evaluateForRun(run);

    verify(registry, never()).get(anyString());
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("evaluateForRun: CorrelationRuleQuery 包含 runId / runStartedAt / ruleId")
  void evaluate_query_construction() {
    AttackChainRun run = run();
    Instant runStart = Instant.parse("2026-05-01T00:00:00Z");
    run.setStart(runStart);
    SocCorrelationRuleRef ruleRef = rule("elastic", "rule-abc");
    AttackChainLinkExpectation expectation = expectation(run, ruleRef);
    when(repository.findByAttackChainRunId(run.getId())).thenReturn(List.of(expectation));
    when(registry.get("elastic")).thenReturn(connector);
    when(connector.queryCorrelationRule(any())).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.evaluateForRun(run);

    ArgumentCaptor<CorrelationRuleQuery> captor =
        ArgumentCaptor.forClass(CorrelationRuleQuery.class);
    verify(connector).queryCorrelationRule(captor.capture());
    CorrelationRuleQuery query = captor.getValue();
    assertThat(query.runId()).isEqualTo(run.getId());
    assertThat(query.runStartedAt()).isEqualTo(runStart);
    assertThat(query.ruleId()).isEqualTo("rule-abc");
  }

  // ---- computeStatus 直接验证 ----

  @Test
  @DisplayName("computeStatus: score >= expected → SUCCESS")
  void compute_status_success() {
    assertThat(LinkExpectationService.computeStatus(100, 100))
        .isEqualTo(LinkExpectationStatus.SUCCESS);
    assertThat(LinkExpectationService.computeStatus(150, 100))
        .isEqualTo(LinkExpectationStatus.SUCCESS);
  }

  @Test
  @DisplayName("computeStatus: 0 < score < expected → PARTIAL")
  void compute_status_partial() {
    assertThat(LinkExpectationService.computeStatus(50, 100))
        .isEqualTo(LinkExpectationStatus.PARTIAL);
  }

  @Test
  @DisplayName("computeStatus: score = 0 → FAILED")
  void compute_status_failed() {
    assertThat(LinkExpectationService.computeStatus(0, 100))
        .isEqualTo(LinkExpectationStatus.FAILED);
  }

  @Test
  @DisplayName("computeStatus: expected <= 0 + 任何命中 → SUCCESS（兜底）")
  void compute_status_zero_expected() {
    assertThat(LinkExpectationService.computeStatus(10, 0))
        .isEqualTo(LinkExpectationStatus.SUCCESS);
    assertThat(LinkExpectationService.computeStatus(0, 0)).isEqualTo(LinkExpectationStatus.FAILED);
  }

  // ---- helpers ----

  private static AttackChainRun run() {
    AttackChainRun r = new AttackChainRun();
    r.setId(UUID.randomUUID().toString());
    r.setAttackChainNodes(new ArrayList<>());
    return r;
  }

  private static AttackChain template(SocCorrelationRuleRef... rules) {
    AttackChain c = new AttackChain();
    c.setId(UUID.randomUUID().toString());
    List<SocCorrelationRuleRef> list = new ArrayList<>();
    for (SocCorrelationRuleRef r : rules) {
      list.add(r);
    }
    c.setSocCorrelationRules(list);
    return c;
  }

  private static SocCorrelationRuleRef rule(String connectorId, String ruleId) {
    return new SocCorrelationRuleRef(connectorId, ruleId, ruleId + "-name", 7200);
  }

  private static AttackChainLinkExpectation expectation(
      AttackChainRun run, SocCorrelationRuleRef rule) {
    AttackChainLinkExpectation e = new AttackChainLinkExpectation();
    e.setId(UUID.randomUUID());
    e.setAttackChainRun(run);
    e.setSocRuleRef(rule);
    e.setExpectedScore(100);
    e.setStatus(LinkExpectationStatus.PENDING);
    e.setExpirationTime(Instant.now().plusSeconds(3600));
    e.setTraces(new ArrayList<>());
    return e;
  }
}
