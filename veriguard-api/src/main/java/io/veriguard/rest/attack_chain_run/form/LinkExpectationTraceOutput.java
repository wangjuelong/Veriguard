package io.veriguard.rest.attack_chain_run.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.LinkExpectationTrace;
import java.time.Instant;
import java.util.Map;

/**
 * 单条 SOC correlation rule 命中追溯的 wire format。
 *
 * <p>对应 {@link LinkExpectationTrace} 但显式映射，避免 LAZY 字段触发 JPA load。
 */
public record LinkExpectationTraceOutput(
    @JsonProperty("trace_id") String id,
    @JsonProperty("incident_id") String incidentId,
    @JsonProperty("correlation_rule_name") String correlationRuleName,
    @JsonProperty("triggered_at") Instant triggeredAt,
    @JsonProperty("score_delta") int scoreDelta,
    @JsonProperty("raw_payload") Map<String, Object> rawPayload,
    @JsonProperty("created_at") Instant createdAt) {

  public static LinkExpectationTraceOutput from(LinkExpectationTrace trace) {
    return new LinkExpectationTraceOutput(
        trace.getId() != null ? trace.getId().toString() : null,
        trace.getIncidentId(),
        trace.getCorrelationRuleName(),
        trace.getTriggeredAt(),
        trace.getScoreDelta(),
        trace.getRawPayload(),
        trace.getCreatedAt());
  }
}
