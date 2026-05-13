package io.veriguard.combination.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.Endpoint;
import org.junit.jupiter.api.Test;

/** 单测 —— PR D3 设备 key 推导优先级（Endpoint.hostname > asset_name > unknown 兜底）. */
class DeviceKeyExtractorTest {

  private final DeviceKeyExtractor extractor = new DeviceKeyExtractor();

  @Test
  void endpoint_with_hostname_uses_hostname() {
    Endpoint ep = new Endpoint();
    ep.setId("ep-1");
    ep.setName("display-name");
    ep.setHostname("Host-1"); // setter lowercases
    assertThat(extractor.deriveDeviceKey(ep)).isEqualTo("host-1");
  }

  @Test
  void endpoint_with_blank_hostname_falls_back_to_asset_name() {
    Endpoint ep = new Endpoint();
    ep.setId("ep-2");
    ep.setName("fallback-name");
    // hostname unset (null)
    assertThat(extractor.deriveDeviceKey(ep)).isEqualTo("fallback-name");
  }

  @Test
  void non_endpoint_asset_uses_asset_name() {
    Asset a = new Asset();
    a.setId("a-1");
    a.setName("Plain-Asset");
    assertThat(extractor.deriveDeviceKey(a)).isEqualTo("Plain-Asset");
  }

  @Test
  void null_name_falls_back_to_unknown_device_id() {
    Asset a = new Asset();
    a.setId("a-2");
    // name unset (null); validation skipped because we test extractor in isolation
    assertThat(extractor.deriveDeviceKey(a)).isEqualTo("unknown-device-a-2");
  }

  @Test
  void null_asset_throws() {
    assertThatThrownBy(() -> extractor.deriveDeviceKey(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
