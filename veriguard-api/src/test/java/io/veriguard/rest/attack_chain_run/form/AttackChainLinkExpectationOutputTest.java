package io.veriguard.rest.attack_chain_run.form;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.veriguard.database.model.AttackChainLinkExpectation;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.LinkExpectationStatus;
import io.veriguard.database.model.LinkExpectationTrace;
import io.veriguard.database.model.SocCorrelationRuleRef;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单测覆盖 {@link AttackChainLinkExpectationOutput#from} 字段映射 + JSON wire format 字段名。
 *
 * <p>不需要起 Spring 上下文 —— 纯 record + Jackson default。
 */
class AttackChainLinkExpectationOutputTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  @DisplayName("from: 完整字段映射 + 嵌入 SocCorrelationRuleRef 扁平化到顶层")
  void from_maps_all_fields() {
    UUID id = UUID.randomUUID();
    AttackChainRun run = new AttackChainRun();
    run.setId("run-1");

    SocCorrelationRuleRef rule =
        new SocCorrelationRuleRef("elastic", "rule-abc", "Brute force", 1800);
    AttackChainLinkExpectation entity = new AttackChainLinkExpectation();
    entity.setId(id);
    entity.setAttackChainRun(run);
    entity.setSocRuleRef(rule);
    entity.setScore(60);
    entity.setExpectedScore(100);
    entity.setStatus(LinkExpectationStatus.PARTIAL);
    Instant expiration = Instant.parse("2026-05-10T12:00:00Z");
    entity.setExpirationTime(expiration);
    Instant created = Instant.parse("2026-05-10T10:00:00Z");
    Instant updated = Instant.parse("2026-05-10T11:00:00Z");
    entity.setCreatedAt(created);
    entity.setUpdatedAt(updated);

    LinkExpectationTrace trace = new LinkExpectationTrace();
    UUID traceId = UUID.randomUUID();
    trace.setId(traceId);
    trace.setLinkExpectation(entity);
    trace.setIncidentId("inc-1");
    trace.setCorrelationRuleName("Brute force detected");
    Instant triggered = Instant.parse("2026-05-10T10:30:00Z");
    trace.setTriggeredAt(triggered);
    trace.setScoreDelta(60);
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("source_ip", "10.0.0.1");
    trace.setRawPayload(raw);
    trace.setCreatedAt(Instant.parse("2026-05-10T10:30:00Z"));
    entity.getTraces().add(trace);

    AttackChainLinkExpectationOutput output = AttackChainLinkExpectationOutput.from(entity);

    assertThat(output.id()).isEqualTo(id.toString());
    assertThat(output.attackChainRunId()).isEqualTo("run-1");
    assertThat(output.connectorId()).isEqualTo("elastic");
    assertThat(output.ruleId()).isEqualTo("rule-abc");
    assertThat(output.displayName()).isEqualTo("Brute force");
    assertThat(output.matchWindowSeconds()).isEqualTo(1800);
    assertThat(output.score()).isEqualTo(60);
    assertThat(output.expectedScore()).isEqualTo(100);
    assertThat(output.status()).isEqualTo(LinkExpectationStatus.PARTIAL);
    assertThat(output.expirationTime()).isEqualTo(expiration);
    assertThat(output.createdAt()).isEqualTo(created);
    assertThat(output.updatedAt()).isEqualTo(updated);
    assertThat(output.traces()).hasSize(1);

    LinkExpectationTraceOutput traceOut = output.traces().get(0);
    assertThat(traceOut.id()).isEqualTo(traceId.toString());
    assertThat(traceOut.incidentId()).isEqualTo("inc-1");
    assertThat(traceOut.correlationRuleName()).isEqualTo("Brute force detected");
    assertThat(traceOut.triggeredAt()).isEqualTo(triggered);
    assertThat(traceOut.scoreDelta()).isEqualTo(60);
    assertThat(traceOut.rawPayload()).containsEntry("source_ip", "10.0.0.1");
  }

  @Test
  @DisplayName("from: 空 traces 列表 → 输出 traces=[]")
  void from_empty_traces() {
    AttackChainRun run = new AttackChainRun();
    run.setId("run-1");
    AttackChainLinkExpectation entity = new AttackChainLinkExpectation();
    entity.setId(UUID.randomUUID());
    entity.setAttackChainRun(run);
    entity.setSocRuleRef(new SocCorrelationRuleRef("elastic", "r", "n", 60));
    entity.setExpirationTime(Instant.now());

    AttackChainLinkExpectationOutput output = AttackChainLinkExpectationOutput.from(entity);

    assertThat(output.traces()).isEmpty();
  }

  @Test
  @DisplayName("JSON 序列化使用 snake_case 字段名（前端 wire format）")
  void serializes_snake_case() throws Exception {
    AttackChainRun run = new AttackChainRun();
    run.setId("run-1");
    AttackChainLinkExpectation entity = new AttackChainLinkExpectation();
    entity.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    entity.setAttackChainRun(run);
    entity.setSocRuleRef(new SocCorrelationRuleRef("elastic", "r1", "Rule One", 1800));
    entity.setStatus(LinkExpectationStatus.PENDING);
    entity.setExpectedScore(100);
    entity.setExpirationTime(Instant.parse("2026-05-10T12:00:00Z"));
    entity.setCreatedAt(Instant.parse("2026-05-10T10:00:00Z"));
    entity.setUpdatedAt(Instant.parse("2026-05-10T10:00:00Z"));

    String json = objectMapper.writeValueAsString(AttackChainLinkExpectationOutput.from(entity));

    assertThat(json).contains("\"link_expectation_id\":\"00000000-0000-0000-0000-000000000001\"");
    assertThat(json).contains("\"attack_chain_run_id\":\"run-1\"");
    assertThat(json).contains("\"connector_id\":\"elastic\"");
    assertThat(json).contains("\"rule_id\":\"r1\"");
    assertThat(json).contains("\"display_name\":\"Rule One\"");
    assertThat(json).contains("\"match_window_seconds\":1800");
    assertThat(json).contains("\"expected_score\":100");
    assertThat(json).contains("\"status\":\"PENDING\"");
    assertThat(json).contains("\"traces\":[]");
  }

  @Test
  @DisplayName("LinkExpectationTraceOutput.from: 完整字段映射 + null UUID 安全")
  void trace_output_from_maps_fields() {
    LinkExpectationTrace trace = new LinkExpectationTrace();
    trace.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    trace.setIncidentId("inc-2");
    trace.setCorrelationRuleName("rule");
    trace.setTriggeredAt(Instant.parse("2026-05-10T10:00:00Z"));
    trace.setScoreDelta(40);
    trace.setRawPayload(Map.of("k", "v"));
    trace.setCreatedAt(Instant.parse("2026-05-10T10:00:01Z"));

    LinkExpectationTraceOutput out = LinkExpectationTraceOutput.from(trace);

    assertThat(out.id()).isEqualTo("00000000-0000-0000-0000-000000000002");
    assertThat(out.scoreDelta()).isEqualTo(40);
    assertThat(out.rawPayload()).containsEntry("k", "v");

    LinkExpectationTrace traceNoId = new LinkExpectationTrace();
    traceNoId.setTriggeredAt(Instant.now());
    LinkExpectationTraceOutput outNoId = LinkExpectationTraceOutput.from(traceNoId);
    assertThat(outNoId.id()).isNull();
  }

  @Test
  @DisplayName("from: null traces list 时不 NPE（防御）")
  void from_handles_null_attack_chain_run() {
    AttackChainLinkExpectation entity = new AttackChainLinkExpectation();
    entity.setId(UUID.randomUUID());
    entity.setSocRuleRef(new SocCorrelationRuleRef("elastic", "r", "n", 60));
    entity.setExpirationTime(Instant.now());
    // attackChainRun 显式不设 → null

    AttackChainLinkExpectationOutput output = AttackChainLinkExpectationOutput.from(entity);

    assertThat(output.attackChainRunId()).isNull();
    assertThat(output.connectorId()).isEqualTo("elastic");
  }

  @Test
  @DisplayName("List<LinkExpectationStatus> 涵盖所有终态枚举值（保险）")
  void status_enum_covers_expected_values() {
    assertThat(List.of(LinkExpectationStatus.values()))
        .containsExactlyInAnyOrder(
            LinkExpectationStatus.PENDING,
            LinkExpectationStatus.SUCCESS,
            LinkExpectationStatus.PARTIAL,
            LinkExpectationStatus.FAILED,
            LinkExpectationStatus.UNKNOWN);
  }
}
