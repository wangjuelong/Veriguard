package io.veriguard.attackchain.soc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocConnectorRegistryTest {

  @Test
  @DisplayName("注册多个 connector → 按 connectorId 索引")
  void registers_and_indexes_by_id() {
    SocAlertConnector elastic = stub("elastic", "Elastic SIEM");
    SocAlertConnector splunk = stub("splunk", "Splunk Cloud");
    SocConnectorRegistry registry = new SocConnectorRegistry(List.of(elastic, splunk));

    assertThat(registry.has("elastic")).isTrue();
    assertThat(registry.has("splunk")).isTrue();
    assertThat(registry.get("elastic")).isSameAs(elastic);
    assertThat(registry.get("splunk")).isSameAs(splunk);
    assertThat(registry.all()).containsExactlyInAnyOrder(elastic, splunk);
  }

  @Test
  @DisplayName("查不存在的 connector → ConnectorNotFoundException")
  void unknown_connector_throws() {
    SocConnectorRegistry registry = new SocConnectorRegistry(List.of());

    assertThat(registry.has("elastic")).isFalse();
    assertThatThrownBy(() -> registry.get("elastic"))
        .isInstanceOf(ConnectorNotFoundException.class)
        .hasMessageContaining("elastic");
  }

  @Test
  @DisplayName("空 connector 列表 → all() 空，不抛错（Phase 11 状态页用）")
  void empty_registry_safe() {
    SocConnectorRegistry registry = new SocConnectorRegistry(List.of());
    assertThat(registry.all()).isEmpty();
  }

  @Test
  @DisplayName("两个 connector 返回相同 connectorId → 启动失败（避免歧义）")
  void duplicate_connector_id_fails_fast() {
    SocAlertConnector a = stub("elastic", "A");
    SocAlertConnector b = stub("elastic", "B");

    assertThatThrownBy(() -> new SocConnectorRegistry(List.of(a, b)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate")
        .hasMessageContaining("elastic");
  }

  @Test
  @DisplayName("connector 返回空 connectorId → 启动失败")
  void blank_connector_id_fails_fast() {
    SocAlertConnector blank = stub(" ", "Blank");

    assertThatThrownBy(() -> new SocConnectorRegistry(List.of(blank)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("blank connectorId");
  }

  // ---- helpers ----

  private static SocAlertConnector stub(String id, String name) {
    return new SocAlertConnector() {
      @Override
      public String getConnectorId() {
        return id;
      }

      @Override
      public String getDisplayName() {
        return name;
      }

      @Override
      public List<DetectionMatch> queryNodeAlert(NodeAlertQuery query) {
        return List.of();
      }

      @Override
      public List<CorrelationMatch> queryCorrelationRule(CorrelationRuleQuery query) {
        return List.of();
      }

      @Override
      public List<AvailableRule> listAvailableRules() {
        return List.of();
      }

      @Override
      public HealthCheckResult checkHealth() {
        return HealthCheckResult.healthy();
      }
    };
  }
}
