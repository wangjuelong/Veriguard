package io.veriguard.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P1.2.b 多端口四元组 schema 单元测试 —— 招标 §4 "同一流量验证用例中包含多个端口不同的四元组".
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>{@code extraTuples} 字段默认初始化为空 list（避免 null）
 *   <li>{@link NetworkTraffic#allTuples()} 主在首位 + 额外按序追加
 *   <li>主四元组任一字段 null → 不出现在 allTuples
 *   <li>{@code extraTuples = null} → allTuples 不报 NPE
 *   <li>JSON ser/de round-trip：主 + 额外都还原
 * </ul>
 */
class NetworkTrafficExtraTuplesTest {

  @Test
  void default_extra_tuples_is_empty_list() {
    NetworkTraffic nt = new NetworkTraffic();
    assertThat(nt.getExtraTuples()).isNotNull().isEmpty();
  }

  @Test
  void all_tuples_primary_first_then_extras() {
    NetworkTraffic nt = newPrimary("2001:db8::1", "2001:db8::2", 32768, 443, "TCP");
    nt.setExtraTuples(
        List.of(
            new NetworkTrafficTuple("2001:db8::1", "2001:db8::2", 32769, 8080, "TCP"),
            new NetworkTrafficTuple("2001:db8::3", "2001:db8::4", 5000, 53, "UDP")));

    List<NetworkTrafficTuple> all = nt.allTuples();

    assertThat(all).hasSize(3);
    assertThat(all.get(0))
        .isEqualTo(new NetworkTrafficTuple("2001:db8::1", "2001:db8::2", 32768, 443, "TCP"));
    assertThat(all.get(1))
        .isEqualTo(new NetworkTrafficTuple("2001:db8::1", "2001:db8::2", 32769, 8080, "TCP"));
    assertThat(all.get(2))
        .isEqualTo(new NetworkTrafficTuple("2001:db8::3", "2001:db8::4", 5000, 53, "UDP"));
  }

  @Test
  void all_tuples_skips_primary_when_field_null() {
    NetworkTraffic nt = new NetworkTraffic();
    // ipDst left null → primary tuple incomplete, must be skipped
    nt.setIpSrc("10.0.0.1");
    nt.setPortSrc(1024);
    nt.setPortDst(80);
    nt.setProtocol("TCP");
    nt.setExtraTuples(List.of(new NetworkTrafficTuple("10.0.0.1", "10.0.0.2", 1024, 443, "TCP")));

    List<NetworkTrafficTuple> all = nt.allTuples();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).portDst()).isEqualTo(443);
  }

  @Test
  void all_tuples_handles_null_extra_list() {
    NetworkTraffic nt = newPrimary("a", "b", 1, 2, "TCP");
    nt.setExtraTuples(null);
    List<NetworkTrafficTuple> all = nt.allTuples();
    assertThat(all).hasSize(1);
  }

  @Test
  void json_round_trip_preserves_extras() throws Exception {
    NetworkTraffic original = newPrimary("10.0.0.1", "10.0.0.2", 1024, 443, "TCP");
    original.setExtraTuples(
        List.of(
            new NetworkTrafficTuple("10.0.0.1", "10.0.0.2", 1025, 8080, "TCP"),
            new NetworkTrafficTuple("10.0.0.1", "10.0.0.3", 5000, 5353, "UDP")));

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    String json = mapper.writeValueAsString(original);

    // Spot-check the JSON shape contains the expected snake_case key and 2 extras.
    assertThat(json).contains("\"network_traffic_extra_tuples\"");
    NetworkTraffic decoded = mapper.readValue(json, NetworkTraffic.class);
    assertThat(decoded.getIpSrc()).isEqualTo("10.0.0.1");
    assertThat(decoded.getExtraTuples()).hasSize(2);
    assertThat(decoded.getExtraTuples().get(0).portDst()).isEqualTo(8080);
    assertThat(decoded.getExtraTuples().get(1).protocol()).isEqualTo("UDP");
    assertThat(decoded.allTuples()).hasSize(3);
  }

  @Test
  void json_decode_when_extra_tuples_absent_keeps_default_empty() throws Exception {
    // Simulate legacy NetworkTraffic JSON (pre V24) that omits the new key entirely.
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ObjectNode legacy = mapper.createObjectNode();
    legacy.put("payload_type", "NetworkTraffic");
    legacy.put("network_traffic_ip_src", "192.168.0.1");
    legacy.put("network_traffic_ip_dst", "192.168.0.2");
    legacy.put("network_traffic_port_src", 1234);
    legacy.put("network_traffic_port_dst", 80);
    legacy.put("network_traffic_protocol", "TCP");
    NetworkTraffic decoded =
        mapper.readValue(mapper.writeValueAsString(legacy), NetworkTraffic.class);
    assertThat(decoded.getExtraTuples()).isNotNull().isEmpty();
    assertThat(decoded.allTuples()).hasSize(1);
  }

  @Test
  void array_node_serialization_round_trip_via_jackson_tree() {
    // Use Jackson tree API to sanity-check extra_tuples is emitted as a JSON array.
    NetworkTraffic nt = newPrimary("a", "b", 1, 2, "TCP");
    nt.setExtraTuples(List.of(new NetworkTrafficTuple("c", "d", 3, 4, "UDP")));
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ObjectNode tree = (ObjectNode) mapper.valueToTree(nt);
    assertThat(tree.has("network_traffic_extra_tuples")).isTrue();
    ArrayNode arr = (ArrayNode) tree.get("network_traffic_extra_tuples");
    assertThat(arr.size()).isEqualTo(1);
    assertThat(arr.get(0).get("network_traffic_protocol").asText()).isEqualTo("UDP");
    assertThat(arr.get(0).get("network_traffic_port_dst").asInt()).isEqualTo(4);
  }

  private static NetworkTraffic newPrimary(
      String src, String dst, int psrc, int pdst, String proto) {
    // Use the (id, type, name) constructor so Payload.type (the parent discriminator
    // field, @Setter(NONE)) is initialized — Jackson's getTypeEnum() reads it during
    // serialization and rejects null with "No PayloadType found for key: null".
    NetworkTraffic nt = new NetworkTraffic("test", NetworkTraffic.NETWORK_TRAFFIC_TYPE, "t");
    nt.setIpSrc(src);
    nt.setIpDst(dst);
    nt.setPortSrc(psrc);
    nt.setPortDst(pdst);
    nt.setProtocol(proto);
    return nt;
  }
}
