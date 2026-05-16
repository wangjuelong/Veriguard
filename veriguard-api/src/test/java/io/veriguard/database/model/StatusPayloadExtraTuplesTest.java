package io.veriguard.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * §4.4 IPv6 安全验证系统招标 "同一流量验证用例中包含多个端口不同的四元组" —— wire 形态 {@link StatusPayload} 携带 extraTuples 的单测.
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>新字段默认 null（不破坏 Jackson 缺省序列化形态）
 *   <li>setExtraTuples 后 JSON 输出含 {@code network_traffic_extra_tuples} 数组
 *   <li>JSON round-trip 还原 List<NetworkTrafficTuple>
 *   <li>缺失 JSON key 时回退到 null（向后兼容旧 wire）
 * </ul>
 */
class StatusPayloadExtraTuplesTest {

  @Test
  void default_extra_tuples_is_null() {
    StatusPayload sp = new StatusPayload();
    assertThat(sp.getExtraTuples()).isNull();
  }

  @Test
  void json_round_trip_preserves_extras() throws Exception {
    StatusPayload original = newNetworkTrafficStatusPayload();
    original.setExtraTuples(
        List.of(
            new NetworkTrafficTuple("2001:db8::1", "2001:db8::2", 32769, 8080, "TCP"),
            new NetworkTrafficTuple("2001:db8::3", "2001:db8::4", 5000, 53, "UDP")));

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"network_traffic_extra_tuples\"");
    assertThat(json).contains("\"network_traffic_port_dst\":8080");
    assertThat(json).contains("\"network_traffic_protocol\":\"UDP\"");

    StatusPayload decoded = mapper.readValue(json, StatusPayload.class);
    assertThat(decoded.getIpSrc()).isEqualTo("2001:db8::1");
    assertThat(decoded.getPortDst()).isEqualTo(443);
    assertThat(decoded.getExtraTuples()).hasSize(2);
    assertThat(decoded.getExtraTuples().get(0).portDst()).isEqualTo(8080);
    assertThat(decoded.getExtraTuples().get(1).protocol()).isEqualTo("UDP");
  }

  @Test
  void tree_serialization_emits_array() {
    StatusPayload sp = newNetworkTrafficStatusPayload();
    sp.setExtraTuples(List.of(new NetworkTrafficTuple("10.0.0.1", "10.0.0.2", 1025, 8080, "TCP")));

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ObjectNode tree = (ObjectNode) mapper.valueToTree(sp);
    assertThat(tree.has("network_traffic_extra_tuples")).isTrue();
    ArrayNode arr = (ArrayNode) tree.get("network_traffic_extra_tuples");
    assertThat(arr.size()).isEqualTo(1);
    assertThat(arr.get(0).get("network_traffic_protocol").asText()).isEqualTo("TCP");
    assertThat(arr.get(0).get("network_traffic_port_dst").asInt()).isEqualTo(8080);
  }

  @Test
  void json_decode_when_extra_tuples_absent_keeps_default_null() throws Exception {
    // Legacy executor wire form (pre §4.4) omits the new key entirely.
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ObjectNode legacy = mapper.createObjectNode();
    legacy.put("payload_name", "legacy");
    legacy.put("payload_type", "NetworkTraffic");
    legacy.put("network_traffic_ip_src", "192.168.0.1");
    legacy.put("network_traffic_ip_dst", "192.168.0.2");
    legacy.put("network_traffic_port_src", 1234);
    legacy.put("network_traffic_port_dst", 80);
    legacy.put("network_traffic_protocol", "TCP");
    StatusPayload decoded =
        mapper.readValue(mapper.writeValueAsString(legacy), StatusPayload.class);
    assertThat(decoded.getExtraTuples()).isNull();
    assertThat(decoded.getPortSrc()).isEqualTo(1234);
  }

  private static StatusPayload newNetworkTrafficStatusPayload() {
    return new StatusPayload(
        "NTA-MULTI-TUPLE-001",
        "test multi-tuple traffic payload",
        "NetworkTraffic",
        "TCP",
        443,
        32768,
        "2001:db8::2",
        "2001:db8::1",
        null,
        null,
        null,
        null,
        null,
        null,
        java.util.Collections.emptyList(),
        null);
  }
}
