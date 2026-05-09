package io.veriguard.attackchain.soc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DtoValidationTest {

  // ---- NodeAlertQuery ----

  @Test
  @DisplayName("NodeAlertQuery: 空 nodeId → 抛错")
  void node_alert_query_blank_id() {
    assertThatThrownBy(
            () ->
                new NodeAlertQuery(
                    " ", Instant.now(), Instant.now().plusSeconds(60), null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nodeId");
  }

  @Test
  @DisplayName("NodeAlertQuery: queryWindowEnd < injectExecutedAt → 抛错")
  void node_alert_query_window_inverted() {
    Instant base = Instant.now();
    assertThatThrownBy(() -> new NodeAlertQuery("n1", base, base.minusSeconds(1), null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("queryWindowEnd");
  }

  @Test
  @DisplayName("NodeAlertQuery: null 集合 → 默认空（不允许调用方下游 NPE）")
  void node_alert_query_defaults_collections() {
    Instant base = Instant.parse("2026-05-01T00:00:00Z");
    NodeAlertQuery q = new NodeAlertQuery("n1", base, base.plusSeconds(10), null, null, null);
    assertThat(q.targetIps()).isEmpty();
    assertThat(q.nodeContractTags()).isEmpty();
    assertThat(q.connectorParams()).isEmpty();
  }

  @Test
  @DisplayName("NodeAlertQuery: 集合不可变（防止下游修改）")
  void node_alert_query_collections_immutable() {
    Instant base = Instant.parse("2026-05-01T00:00:00Z");
    NodeAlertQuery q =
        new NodeAlertQuery(
            "n1",
            base,
            base.plusSeconds(10),
            Set.of("10.0.0.1"),
            Set.of("mitre:T1059"),
            Map.of("key", "value"));
    assertThatThrownBy(() -> q.targetIps().add("10.0.0.2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ---- CorrelationRuleQuery ----

  @Test
  @DisplayName("CorrelationRuleQuery: 空 ruleId → 抛错")
  void correlation_query_blank_rule_id() {
    Instant base = Instant.now();
    assertThatThrownBy(() -> new CorrelationRuleQuery("r1", base, base.plusSeconds(10), " ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ruleId");
  }

  @Test
  @DisplayName("CorrelationRuleQuery: 空 runId → 抛错")
  void correlation_query_blank_run_id() {
    Instant base = Instant.now();
    assertThatThrownBy(
            () -> new CorrelationRuleQuery(null, base, base.plusSeconds(10), "rule-1", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- DetectionMatch / CorrelationMatch ----

  @Test
  @DisplayName("DetectionMatch: 空 alertId → 抛错")
  void detection_match_blank_alert_id() {
    assertThatThrownBy(() -> new DetectionMatch(" ", "rule", Instant.now(), 50, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("CorrelationMatch: null raw → 默认空 map")
  void correlation_match_default_raw() {
    CorrelationMatch m = new CorrelationMatch("inc-1", "rule", Instant.now(), 75, null);
    assertThat(m.raw()).isEmpty();
  }

  // ---- AvailableRule ----

  @Test
  @DisplayName("AvailableRule: 空 displayName → 抛错")
  void available_rule_blank_name() {
    assertThatThrownBy(() -> new AvailableRule("rule-1", " ", "desc", "Correlation"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- HealthCheckResult ----

  @Test
  @DisplayName("HealthCheckResult 工厂方法 + 状态判定")
  void health_check_factories() {
    assertThat(HealthCheckResult.healthy().status()).isEqualTo(HealthCheckResult.Status.HEALTHY);
    assertThat(HealthCheckResult.disabled().status()).isEqualTo(HealthCheckResult.Status.DISABLED);
    assertThat(HealthCheckResult.unhealthy("auth").message()).contains("auth");
    assertThat(HealthCheckResult.degraded("yellow").status())
        .isEqualTo(HealthCheckResult.Status.DEGRADED);
  }

  @Test
  @DisplayName("HealthCheckResult: null status → 抛错")
  void health_check_null_status() {
    assertThatThrownBy(() -> new HealthCheckResult(null, "msg"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
