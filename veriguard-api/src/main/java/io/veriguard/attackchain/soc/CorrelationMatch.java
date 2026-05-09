package io.veriguard.attackchain.soc;

import java.time.Instant;
import java.util.Map;

/**
 * 链路级 correlation rule 匹配结果（spec §4.2）—— SOC 平台返回的一条 correlation/incident 记录。
 *
 * @param incidentId SOC 平台内 incident / correlation 唯一 ID
 * @param correlationRuleName 触发的 correlation 规则名
 * @param triggeredAt 触发时刻
 * @param score 该匹配贡献的检测分数；调用方累加到 {@code LinkExpectationTrace.score}
 * @param raw SOC 原始返回的 JSON 字段
 */
public record CorrelationMatch(
    String incidentId,
    String correlationRuleName,
    Instant triggeredAt,
    int score,
    Map<String, Object> raw) {

  public CorrelationMatch {
    if (incidentId == null || incidentId.isBlank()) {
      throw new IllegalArgumentException("incidentId required");
    }
    raw = raw == null ? Map.of() : Map.copyOf(raw);
  }
}
