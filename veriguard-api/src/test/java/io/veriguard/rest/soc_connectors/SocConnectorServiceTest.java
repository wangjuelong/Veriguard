package io.veriguard.rest.soc_connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.attackchain.soc.AvailableRule;
import io.veriguard.attackchain.soc.ConnectorNotFoundException;
import io.veriguard.attackchain.soc.HealthCheckResult;
import io.veriguard.attackchain.soc.SocAlertConnector;
import io.veriguard.attackchain.soc.SocConnectorRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocConnectorServiceTest {

  @Mock SocConnectorRegistry registry;
  @Mock SocAlertConnector elasticConnector;
  @Mock SocAlertConnector splunkConnector;

  SocConnectorService service;

  @BeforeEach
  void setUp() {
    service = new SocConnectorService(registry);
  }

  @Test
  @DisplayName("list aggregates connector id, display name, status, message, and rule count")
  void list_aggregatesAllFields() {
    when(registry.all()).thenReturn(List.of(elasticConnector));
    when(elasticConnector.getConnectorId()).thenReturn("elastic");
    when(elasticConnector.getDisplayName()).thenReturn("Elastic SIEM");
    when(elasticConnector.checkHealth()).thenReturn(HealthCheckResult.healthy());
    when(elasticConnector.listAvailableRules())
        .thenReturn(List.of(new AvailableRule("rule-1", "Suspicious Login", null, "Detection")));

    List<SocConnectorOutput> result = service.list();

    assertThat(result).hasSize(1);
    SocConnectorOutput item = result.get(0);
    assertThat(item.connectorId()).isEqualTo("elastic");
    assertThat(item.displayName()).isEqualTo("Elastic SIEM");
    assertThat(item.status()).isEqualTo(HealthCheckResult.Status.HEALTHY);
    assertThat(item.availableRuleCount()).isEqualTo(1);
    assertThat(item.lastCheckedAt()).isNotNull();
  }

  @Test
  @DisplayName("list caches health check; second call does not re-invoke checkHealth")
  void list_cachesHealthCheck() {
    when(registry.all()).thenReturn(List.of(elasticConnector));
    when(elasticConnector.getConnectorId()).thenReturn("elastic");
    when(elasticConnector.getDisplayName()).thenReturn("Elastic SIEM");
    when(elasticConnector.checkHealth()).thenReturn(HealthCheckResult.healthy());
    when(elasticConnector.listAvailableRules()).thenReturn(List.of());

    service.list();
    service.list();

    verify(elasticConnector, times(1)).checkHealth();
  }

  @Test
  @DisplayName("refresh forces a new checkHealth call and updates the cached snapshot")
  void refresh_forcesHealthCheck() {
    when(registry.all()).thenReturn(List.of(elasticConnector));
    when(registry.get("elastic")).thenReturn(elasticConnector);
    when(elasticConnector.getConnectorId()).thenReturn("elastic");
    when(elasticConnector.getDisplayName()).thenReturn("Elastic SIEM");
    when(elasticConnector.checkHealth())
        .thenReturn(HealthCheckResult.healthy(), HealthCheckResult.degraded("rate limited"));
    when(elasticConnector.listAvailableRules()).thenReturn(List.of());

    service.list();
    SocConnectorOutput refreshed = service.refresh("elastic");

    assertThat(refreshed.status()).isEqualTo(HealthCheckResult.Status.DEGRADED);
    assertThat(refreshed.message()).isEqualTo("rate limited");
    verify(elasticConnector, times(2)).checkHealth();
  }

  @Test
  @DisplayName("checkHealth throwing RuntimeException maps to UNHEALTHY status with the exception message")
  void checkHealthThrows_mapsToUnhealthy() {
    when(registry.all()).thenReturn(List.of(elasticConnector));
    when(elasticConnector.getConnectorId()).thenReturn("elastic");
    when(elasticConnector.getDisplayName()).thenReturn("Elastic SIEM");
    when(elasticConnector.checkHealth()).thenThrow(new RuntimeException("auth failed"));

    SocConnectorOutput item = service.list().get(0);

    assertThat(item.status()).isEqualTo(HealthCheckResult.Status.UNHEALTHY);
    assertThat(item.message()).isEqualTo("auth failed");
    assertThat(item.availableRuleCount()).isNull();
  }

  @Test
  @DisplayName("UNHEALTHY connector does not call listAvailableRules during status count")
  void unhealthyConnector_skipsRuleCount() {
    when(registry.all()).thenReturn(List.of(elasticConnector));
    when(elasticConnector.getConnectorId()).thenReturn("elastic");
    when(elasticConnector.getDisplayName()).thenReturn("Elastic SIEM");
    when(elasticConnector.checkHealth()).thenReturn(HealthCheckResult.unhealthy("down"));

    SocConnectorOutput item = service.list().get(0);

    assertThat(item.availableRuleCount()).isNull();
    verify(elasticConnector, times(0)).listAvailableRules();
  }

  @Test
  @DisplayName("listRules surfaces AvailableRule entries via the rule output DTO")
  void listRules_returnsRuleOutputs() {
    when(registry.get("elastic")).thenReturn(elasticConnector);
    when(elasticConnector.listAvailableRules())
        .thenReturn(
            List.of(
                new AvailableRule("rule-a", "Rule A", "desc A", "Correlation"),
                new AvailableRule("rule-b", "Rule B", null, null)));

    List<SocConnectorRuleOutput> result = service.listRules("elastic");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).ruleId()).isEqualTo("rule-a");
    assertThat(result.get(0).displayName()).isEqualTo("Rule A");
    assertThat(result.get(0).category()).isEqualTo("Correlation");
    assertThat(result.get(1).ruleId()).isEqualTo("rule-b");
    assertThat(result.get(1).description()).isNull();
  }

  @Test
  @DisplayName("refresh on unknown connector id propagates ConnectorNotFoundException")
  void refresh_unknownId_throwsConnectorNotFound() {
    when(registry.get("unknown")).thenThrow(new ConnectorNotFoundException("unknown"));

    assertThatThrownBy(() -> service.refresh("unknown"))
        .isInstanceOf(ConnectorNotFoundException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  @DisplayName("listRules propagates RuntimeException from connector.listAvailableRules")
  void listRules_propagatesRuntimeException() {
    when(registry.get("elastic")).thenReturn(elasticConnector);
    when(elasticConnector.listAvailableRules()).thenThrow(new RuntimeException("rate limited"));

    assertThatThrownBy(() -> service.listRules("elastic"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("rate limited");
  }

  @Test
  @DisplayName("multiple connectors aggregate independently in the snapshot list")
  void list_multipleConnectors() {
    when(registry.all()).thenReturn(List.of(elasticConnector, splunkConnector));
    when(elasticConnector.getConnectorId()).thenReturn("elastic");
    when(elasticConnector.getDisplayName()).thenReturn("Elastic SIEM");
    when(elasticConnector.checkHealth()).thenReturn(HealthCheckResult.healthy());
    when(elasticConnector.listAvailableRules()).thenReturn(List.of());
    when(splunkConnector.getConnectorId()).thenReturn("splunk");
    when(splunkConnector.getDisplayName()).thenReturn("Splunk");
    when(splunkConnector.checkHealth()).thenReturn(HealthCheckResult.disabled());

    List<SocConnectorOutput> result = service.list();

    assertThat(result).extracting(SocConnectorOutput::connectorId)
        .containsExactly("elastic", "splunk");
    assertThat(result.get(0).status()).isEqualTo(HealthCheckResult.Status.HEALTHY);
    assertThat(result.get(1).status()).isEqualTo(HealthCheckResult.Status.DISABLED);
  }
}
