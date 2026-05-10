package io.veriguard.rest.attack_chain_run.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.AttackChainLinkExpectation;
import io.veriguard.database.model.LinkExpectationStatus;
import io.veriguard.database.model.SocCorrelationRuleRef;
import java.time.Instant;
import java.util.List;

/**
 * 链路级 SOC DETECTION 期望的 wire format（给运行画布 LinkExpectationPanel 渲染用）。
 *
 * <p>不直接序列化 {@link AttackChainLinkExpectation} 实体，避免 LAZY-loaded {@code attackChainRun} 反引发 cycle /
 * N+1 序列化。把 {@link SocCorrelationRuleRef} 嵌入的 connectorId / ruleId 等扁平化到顶层， 前端不需要再下钻一层。
 */
public record AttackChainLinkExpectationOutput(
    @JsonProperty("link_expectation_id") String id,
    @JsonProperty("attack_chain_run_id") String attackChainRunId,
    @JsonProperty("connector_id") String connectorId,
    @JsonProperty("rule_id") String ruleId,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("match_window_seconds") int matchWindowSeconds,
    @JsonProperty("score") int score,
    @JsonProperty("expected_score") int expectedScore,
    @JsonProperty("status") LinkExpectationStatus status,
    @JsonProperty("expiration_time") Instant expirationTime,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("traces") List<LinkExpectationTraceOutput> traces) {

  public static AttackChainLinkExpectationOutput from(AttackChainLinkExpectation entity) {
    SocCorrelationRuleRef rule = entity.getSocRuleRef();
    return new AttackChainLinkExpectationOutput(
        entity.getId() != null ? entity.getId().toString() : null,
        entity.getAttackChainRun() != null ? entity.getAttackChainRun().getId() : null,
        rule != null ? rule.connectorId() : null,
        rule != null ? rule.ruleId() : null,
        rule != null ? rule.displayName() : null,
        rule != null ? rule.matchWindowSeconds() : 0,
        entity.getScore(),
        entity.getExpectedScore(),
        entity.getStatus(),
        entity.getExpirationTime(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getTraces().stream().map(LinkExpectationTraceOutput::from).toList());
  }
}
