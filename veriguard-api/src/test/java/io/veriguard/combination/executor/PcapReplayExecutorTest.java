package io.veriguard.combination.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.combination.CombinationInstance;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PcapReplayExecutor}. PR D5 Step 5. */
class PcapReplayExecutorTest {

  private final PcapReplayExecutor executor = new PcapReplayExecutor();

  @Test
  void supports_known_pcap_base_types() {
    assertThat(executor.supports("pcap_replay")).isTrue();
    assertThat(executor.supports("ipv6_extension_header_abuse")).isTrue();
    assertThat(executor.supports("ipv6_fragmentation_evasion")).isTrue();
    assertThat(executor.supports("rogue_router_advertisement")).isTrue();
    assertThat(executor.supports("neighbor_discovery_spoof")).isTrue();
    assertThat(executor.supports("dns_tunneling")).isTrue();
  }

  @Test
  void supports_web_or_unrelated_base_types_returns_false() {
    assertThat(executor.supports("sql_injection")).isFalse();
    assertThat(executor.supports("xss")).isFalse();
    assertThat(executor.supports(null)).isFalse();
    assertThat(executor.supports("")).isFalse();
  }

  @Test
  void execute_skeleton_throws_unsupported_operation() {
    CombinationInstance instance =
        new CombinationInstance(
            "run-1",
            "pcap_replay:dim-7",
            "pcap_replay",
            "dim-7",
            "asset-9",
            "<binary pcap bytes>",
            "<binary pcap bytes>");
    assertThatThrownBy(() -> executor.execute(instance))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("PcapReplayExecutor skeleton")
        .hasMessageContaining("pcap_replay");
  }
}
