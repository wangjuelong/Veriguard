package io.veriguard.coverage.soc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubSocAdapterTest {

  private SocAlertQuery defaultQuery(List<String> ips) {
    Instant now = Instant.now();
    return new SocAlertQuery(ips, now.minus(Duration.ofMinutes(5)), now, List.of("waf"));
  }

  @Test
  void hit_rate_one_returns_alert_for_each_asset() {
    StubSocAdapter adapter = new StubSocAdapter(1.0, 0.0);
    List<SocAlert> alerts = adapter.queryAlerts(defaultQuery(List.of("1.1.1.1", "2.2.2.2")));
    assertThat(alerts).hasSize(2);
    assertThat(alerts).allSatisfy(a -> assertThat(a.assetIp()).isNotBlank());
  }

  @Test
  void hit_rate_zero_returns_empty() {
    StubSocAdapter adapter = new StubSocAdapter(0.0, 0.0);
    List<SocAlert> alerts = adapter.queryAlerts(defaultQuery(List.of("1.1.1.1", "2.2.2.2")));
    assertThat(alerts).isEmpty();
  }

  @Test
  void timeout_rate_one_throws_timeout() {
    StubSocAdapter adapter = new StubSocAdapter(0.0, 1.0);
    assertThatThrownBy(() -> adapter.queryAlerts(defaultQuery(List.of("1.1.1.1"))))
        .isInstanceOf(SocQueryTimeoutException.class);
  }

  @Test
  void invalid_rates_throw_at_construction() {
    assertThatThrownBy(() -> new StubSocAdapter(-0.1, 0.0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StubSocAdapter(0.6, 0.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be <= 1.0");
  }

  @Test
  void empty_assets_returns_empty_no_throw() {
    StubSocAdapter adapter = new StubSocAdapter(1.0, 0.0);
    assertThat(adapter.queryAlerts(defaultQuery(List.of()))).isEmpty();
  }

  @Test
  void name_and_health_are_stable() {
    StubSocAdapter adapter = new StubSocAdapter(0.5, 0.0);
    assertThat(adapter.name()).isEqualTo("stub");
    assertThat(adapter.health()).isEqualTo(HealthStatus.healthy);
  }
}
