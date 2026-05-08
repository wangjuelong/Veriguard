package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SOC 告警关联规则引用 (PRD §2.4 / spec §2.2.7).
 *
 * <p>不是表，是嵌入在 {@code attack_chains.soc_correlation_rules} JSONB 列与 {@code
 * attack_chain_link_expectations.soc_rule_ref} 中的值对象。
 *
 * @param connectorId 哪个 SocAlertConnector 实现（{@code elastic} / future others）
 * @param ruleId 该 SOC 平台内的规则 ID 或 saved search ID
 * @param displayName 给前端展示的友好名
 * @param matchWindowSeconds 链路结束后查询多久窗口的告警，默认 7200s (2h)
 */
public record SocCorrelationRuleRef(
    @JsonProperty("connector_id") String connectorId,
    @JsonProperty("rule_id") String ruleId,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("match_window_seconds") int matchWindowSeconds) {

  /** Default match window when caller passes a non-positive value. */
  public static final int DEFAULT_MATCH_WINDOW_SECONDS = 7200;

  public SocCorrelationRuleRef {
    if (connectorId == null || connectorId.isBlank()) {
      throw new IllegalArgumentException("connector_id required");
    }
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("rule_id required");
    }
    if (matchWindowSeconds <= 0) {
      matchWindowSeconds = DEFAULT_MATCH_WINDOW_SECONDS;
    }
  }
}
