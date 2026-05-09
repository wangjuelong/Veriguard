package io.veriguard.attackchain.execution;

import io.veriguard.attackchain.soc.ConnectorNotFoundException;
import io.veriguard.attackchain.soc.CorrelationMatch;
import io.veriguard.attackchain.soc.CorrelationRuleQuery;
import io.veriguard.attackchain.soc.SocAlertConnector;
import io.veriguard.attackchain.soc.SocConnectorRegistry;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.AttackChainLinkExpectation;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.LinkExpectationStatus;
import io.veriguard.database.model.LinkExpectationTrace;
import io.veriguard.database.model.SocCorrelationRuleRef;
import io.veriguard.database.repository.AttackChainLinkExpectationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 链路级 SOC DETECTION expectation 编排（PRD §2.4 / spec §3.5 + §4.6）.
 *
 * <p>两件事：
 *
 * <ul>
 *   <li>{@link #instantiateForRun(AttackChainRun)} —— 在 run 进入终态前若尚未实例化，从 {@code
 *       AttackChain.socCorrelationRules} 物化 {@link AttackChainLinkExpectation} 记录（idempotent）
 *   <li>{@link #evaluateForRun(AttackChainRun)} —— 对每条 PENDING / PARTIAL 的 link expectation 通过
 *       {@link SocConnectorRegistry} 查 SOC，写 {@link LinkExpectationTrace} + 累加 score，按 score vs
 *       expectedScore 终态化为 SUCCESS / PARTIAL / FAILED / UNKNOWN
 * </ul>
 *
 * <p>失败处理（spec §3.6）：connector 调用失败 ≤ 3 次重试 exponential backoff；耗尽 / 过期 → 标 UNKNOWN。 不静默吞错。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LinkExpectationService {

  static final int MAX_RETRY_ATTEMPTS = 3;
  static final long INITIAL_BACKOFF_MILLIS = 200L;

  private final SocConnectorRegistry connectorRegistry;
  private final AttackChainLinkExpectationRepository repository;

  /**
   * Run 即将终态化时确保对应 link expectations 已物化。idempotent —— 重复调用安全。
   *
   * <p>来源：{@code run.attackChain.socCorrelationRules}（template 配置）。run 没挂模板（atomic-style） 或
   * socCorrelationRules 为空 → 不实例化任何记录。
   */
  public List<AttackChainLinkExpectation> instantiateForRun(AttackChainRun run) {
    if (run == null) {
      return List.of();
    }
    AttackChain template = run.getAttackChain();
    if (template == null
        || template.getSocCorrelationRules() == null
        || template.getSocCorrelationRules().isEmpty()) {
      return List.of();
    }
    List<AttackChainLinkExpectation> existing = repository.findByAttackChainRunId(run.getId());
    if (!existing.isEmpty()) {
      return existing;
    }
    Instant now = Instant.now();
    List<AttackChainLinkExpectation> created = new java.util.ArrayList<>();
    for (SocCorrelationRuleRef rule : template.getSocCorrelationRules()) {
      AttackChainLinkExpectation e = new AttackChainLinkExpectation();
      e.setAttackChainRun(run);
      e.setSocRuleRef(rule);
      e.setExpectedScore(100);
      e.setStatus(LinkExpectationStatus.PENDING);
      e.setExpirationTime(now.plus(Duration.ofSeconds(rule.matchWindowSeconds())));
      created.add(repository.save(e));
    }
    return created;
  }

  /**
   * 对 run 的每条 PENDING/PARTIAL link expectation 查 SOC 并按结果终态化。
   *
   * <p>已是 SUCCESS / FAILED / UNKNOWN 的不再查询（终态）。run 没挂模板 / 没 link expectations 直接返回。
   */
  public void evaluateForRun(AttackChainRun run) {
    if (run == null) {
      return;
    }
    List<AttackChainLinkExpectation> expectations = repository.findByAttackChainRunId(run.getId());
    if (expectations.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    for (AttackChainLinkExpectation expectation : expectations) {
      if (isTerminal(expectation.getStatus())) {
        continue;
      }
      if (expectation.getExpirationTime() != null
          && expectation.getExpirationTime().isBefore(now)) {
        markUnknown(expectation, "expired before evaluation");
        continue;
      }
      evaluateOne(run, expectation, now);
    }
  }

  private void evaluateOne(
      AttackChainRun run, AttackChainLinkExpectation expectation, Instant now) {
    SocCorrelationRuleRef rule = expectation.getSocRuleRef();
    SocAlertConnector connector;
    try {
      connector = connectorRegistry.get(rule.connectorId());
    } catch (ConnectorNotFoundException e) {
      log.warn(
          "SOC connector '{}' not found for link expectation {} run {}",
          rule.connectorId(),
          expectation.getId(),
          run.getId());
      markUnknown(expectation, "connector " + rule.connectorId() + " not registered");
      return;
    }
    Instant runStart = run.getStart().orElse(run.getCreatedAt() != null ? run.getCreatedAt() : now);
    Instant windowEnd = runStart.plus(Duration.ofSeconds(rule.matchWindowSeconds()));
    if (windowEnd.isBefore(now)) {
      windowEnd = now;
    }
    CorrelationRuleQuery query =
        new CorrelationRuleQuery(run.getId(), runStart, windowEnd, rule.ruleId(), Map.of());
    List<CorrelationMatch> matches;
    try {
      matches = queryWithRetry(connector, query);
    } catch (RuntimeException e) {
      log.warn(
          "SOC connector '{}' query failed after {} retries for run {}: {}",
          rule.connectorId(),
          MAX_RETRY_ATTEMPTS,
          run.getId(),
          e.getMessage());
      markUnknown(expectation, "soc query failed: " + truncate(e.getMessage()));
      return;
    }
    int totalDelta = 0;
    for (CorrelationMatch match : matches) {
      LinkExpectationTrace trace = new LinkExpectationTrace();
      trace.setLinkExpectation(expectation);
      trace.setIncidentId(match.incidentId());
      trace.setCorrelationRuleName(match.correlationRuleName());
      trace.setTriggeredAt(match.triggeredAt() != null ? match.triggeredAt() : now);
      trace.setScoreDelta(match.score());
      trace.setRawPayload(coerceMap(match.raw()));
      expectation.getTraces().add(trace);
      totalDelta += match.score();
    }
    expectation.setScore(expectation.getScore() + totalDelta);
    expectation.setStatus(computeStatus(expectation.getScore(), expectation.getExpectedScore()));
    expectation.setUpdatedAt(now);
    repository.save(expectation);
  }

  private List<CorrelationMatch> queryWithRetry(
      SocAlertConnector connector, CorrelationRuleQuery query) {
    RuntimeException last = null;
    long backoff = INITIAL_BACKOFF_MILLIS;
    for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
      try {
        return connector.queryCorrelationRule(query);
      } catch (RuntimeException e) {
        last = e;
        if (attempt == MAX_RETRY_ATTEMPTS) {
          break;
        }
        sleep(backoff);
        backoff *= 2;
      }
    }
    throw last == null ? new IllegalStateException("query failed without exception") : last;
  }

  /** 测试可 override 跳过实际 sleep。 */
  protected void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("interrupted during retry backoff", e);
    }
  }

  private void markUnknown(AttackChainLinkExpectation expectation, String reason) {
    expectation.setStatus(LinkExpectationStatus.UNKNOWN);
    expectation.setUpdatedAt(Instant.now());
    repository.save(expectation);
    log.info("Link expectation {} → UNKNOWN: {}", expectation.getId(), reason);
  }

  static LinkExpectationStatus computeStatus(int score, int expectedScore) {
    if (expectedScore <= 0) {
      // 没设期望分 → 任何命中都算 SUCCESS（防御视角）；没命中 FAILED
      return score > 0 ? LinkExpectationStatus.SUCCESS : LinkExpectationStatus.FAILED;
    }
    if (score >= expectedScore) {
      return LinkExpectationStatus.SUCCESS;
    }
    if (score > 0) {
      return LinkExpectationStatus.PARTIAL;
    }
    return LinkExpectationStatus.FAILED;
  }

  private static boolean isTerminal(LinkExpectationStatus status) {
    return status == LinkExpectationStatus.SUCCESS
        || status == LinkExpectationStatus.FAILED
        || status == LinkExpectationStatus.UNKNOWN;
  }

  private static Map<String, Object> coerceMap(Map<String, Object> raw) {
    if (raw == null || raw.isEmpty()) {
      return null; // jsonb 列可空
    }
    return new LinkedHashMap<>(raw);
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() > 200 ? s.substring(0, 200) + "..." : s;
  }
}
