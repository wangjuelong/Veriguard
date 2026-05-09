package io.veriguard.attackchain.soc;

import java.time.Instant;
import java.util.Map;

/**
 * 链路级 SOC correlation rule 查询参数（spec §4.2）.
 *
 * @param runId 触发查询的 attackChainRun ID
 * @param runStartedAt run 开始时刻（查询窗口起点）
 * @param queryWindowEnd 查询窗口终点（通常 = runStartedAt + soc_rule_ref.match_window_seconds）
 * @param ruleId SOC 平台内的规则 ID 或 saved search ID（来自 {@code SocCorrelationRuleRef.ruleId}）
 * @param connectorParams connector 实现自定义键值对（不进数据库）
 */
public record CorrelationRuleQuery(
    String runId,
    Instant runStartedAt,
    Instant queryWindowEnd,
    String ruleId,
    Map<String, String> connectorParams) {

  public CorrelationRuleQuery {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("runId required");
    }
    if (runStartedAt == null) {
      throw new IllegalArgumentException("runStartedAt required");
    }
    if (queryWindowEnd == null) {
      throw new IllegalArgumentException("queryWindowEnd required");
    }
    if (queryWindowEnd.isBefore(runStartedAt)) {
      throw new IllegalArgumentException("queryWindowEnd must be >= runStartedAt");
    }
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("ruleId required");
    }
    connectorParams = connectorParams == null ? Map.of() : Map.copyOf(connectorParams);
  }
}
