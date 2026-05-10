package io.veriguard.rest.soc_connectors;

import io.veriguard.attackchain.soc.AvailableRule;
import io.veriguard.attackchain.soc.ConnectorNotFoundException;
import io.veriguard.attackchain.soc.HealthCheckResult;
import io.veriguard.attackchain.soc.SocAlertConnector;
import io.veriguard.attackchain.soc.SocConnectorRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SOC connector 状态聚合服务（spec §6.3.6）.
 *
 * <p>把 {@link SocConnectorRegistry} 暴露的连接器集合 + 每个连接器的最近一次
 * {@link HealthCheckResult} + 上次检查时刻 + 已知规则数缓存到内存中，给 REST API
 * 提供统一快照。健康检查在两种时机触发：
 *
 * <ul>
 *   <li>首次列表请求时为每个 connector 走一次 {@code checkHealth()}（懒加载）
 *   <li>POST {@code /api/soc_connectors/{id}/refresh} 显式触发某个 connector 重新检查
 * </ul>
 *
 * <p>失败显式：{@code checkHealth()} / {@code listAvailableRules()} 抛 RuntimeException
 * 时记录 UNHEALTHY 并把 message 短语保留下来，调用方收到 200 + UNHEALTHY 状态而非 500，
 * 这样前端"红绿灯"可以显示具体原因。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocConnectorService {

  private final SocConnectorRegistry registry;
  private final Map<String, HealthSnapshot> snapshots = new ConcurrentHashMap<>();

  /** 列出所有 connector 当前快照；首次访问的 connector 会同步触发一次健康检查。 */
  public List<SocConnectorOutput> list() {
    return registry.all().stream()
        .map(connector -> snapshot(connector, false))
        .toList();
  }

  /** 强制刷新指定 connector 的健康状态（返回最新快照）；找不到 ID 抛 ConnectorNotFoundException。 */
  public SocConnectorOutput refresh(String connectorId) {
    SocAlertConnector connector = registry.get(connectorId);
    return snapshot(connector, true);
  }

  /** 列出指定 connector 的可选 correlation rules（给规则选择器）；找不到 ID 抛 ConnectorNotFoundException。 */
  public List<SocConnectorRuleOutput> listRules(String connectorId) {
    SocAlertConnector connector = registry.get(connectorId);
    try {
      return connector.listAvailableRules().stream().map(SocConnectorService::toRuleOutput).toList();
    } catch (RuntimeException ex) {
      log.warn(
          "SOC connector {} listAvailableRules() failed: {}",
          connectorId,
          ex.getMessage());
      throw ex;
    }
  }

  private SocConnectorOutput snapshot(SocAlertConnector connector, boolean forceRefresh) {
    String id = connector.getConnectorId();
    HealthSnapshot existing = snapshots.get(id);
    if (existing == null || forceRefresh) {
      HealthCheckResult result = runHealthCheck(connector);
      Integer ruleCount = countRules(connector, result.status());
      existing = new HealthSnapshot(result, ruleCount, Instant.now());
      snapshots.put(id, existing);
    }
    return new SocConnectorOutput(
        id,
        connector.getDisplayName(),
        existing.result.status(),
        existing.result.message(),
        existing.availableRuleCount,
        existing.lastCheckedAt);
  }

  private HealthCheckResult runHealthCheck(SocAlertConnector connector) {
    try {
      HealthCheckResult result = connector.checkHealth();
      if (result == null) {
        log.warn(
            "SOC connector {} checkHealth() returned null; treating as UNHEALTHY",
            connector.getConnectorId());
        return HealthCheckResult.unhealthy("checkHealth returned null");
      }
      return result;
    } catch (RuntimeException ex) {
      log.warn(
          "SOC connector {} checkHealth() threw: {}",
          connector.getConnectorId(),
          ex.getMessage());
      return HealthCheckResult.unhealthy(ex.getMessage());
    }
  }

  private Integer countRules(SocAlertConnector connector, HealthCheckResult.Status status) {
    if (status == HealthCheckResult.Status.DISABLED || status == HealthCheckResult.Status.UNHEALTHY) {
      return null;
    }
    try {
      List<AvailableRule> rules = connector.listAvailableRules();
      return rules == null ? 0 : rules.size();
    } catch (RuntimeException ex) {
      log.debug(
          "SOC connector {} listAvailableRules() failed during status count: {}",
          connector.getConnectorId(),
          ex.getMessage());
      return null;
    }
  }

  private static SocConnectorRuleOutput toRuleOutput(AvailableRule rule) {
    return new SocConnectorRuleOutput(
        rule.ruleId(), rule.displayName(), rule.description(), rule.category());
  }

  private record HealthSnapshot(
      HealthCheckResult result, Integer availableRuleCount, Instant lastCheckedAt) {}
}
