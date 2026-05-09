package io.veriguard.attackchain.soc;

import java.time.Instant;
import java.util.Map;

/**
 * 节点级告警匹配结果（spec §4.2）—— SOC 平台返回的一条匹配记录。
 *
 * @param alertId SOC 平台内告警唯一 ID
 * @param ruleName 触发的规则名（用于审计 / UI 展示）
 * @param triggeredAt 告警触发时刻
 * @param score 该告警贡献的检测分数；调用方累加到 {@code NodeExpectationTrace.score}
 * @param raw SOC 原始返回的 JSON 字段，便于排查；调用方仅做 read-only 留痕
 */
public record DetectionMatch(
    String alertId, String ruleName, Instant triggeredAt, int score, Map<String, Object> raw) {

  public DetectionMatch {
    if (alertId == null || alertId.isBlank()) {
      throw new IllegalArgumentException("alertId required");
    }
    raw = raw == null ? Map.of() : Map.copyOf(raw);
  }
}
