package io.veriguard.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

/**
 * P1.2 TrafficPattern PayloadType 子类单元测试 —— 招标 §4 网络流量行为分析验证.
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>{@link Payload#getTypeEnum()} 解析 discriminator "TrafficPattern"
 *   <li>JSON ser/de round-trip：5 标量列全字段还原
 *   <li>可选列（pcap_path / detection_hint）缺失时为 null
 *   <li>Jackson tree 直接读字段
 * </ul>
 */
class TrafficPatternPayloadTest {

  @Test
  void type_enum_resolves_traffic_pattern() {
    TrafficPatternPayload p = newPayload();
    assertThat(p.getTypeEnum()).isEqualTo(PayloadType.TRAFFIC_PATTERN);
  }

  @Test
  void json_round_trip_preserves_all_fields() throws Exception {
    TrafficPatternPayload original = newPayload();
    original.setNtaPcapPath("datasets/pcap/exfil_001.pcap");
    original.setNtaDetectionHint("看 dns.qry.name 长度>200 字节");

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"payload_type\":\"TrafficPattern\"");
    assertThat(json).contains("\"nta_category\":\"data_exfiltration\"");
    assertThat(json).contains("\"nta_protocol\":\"DNS\"");
    assertThat(json).contains("\"nta_signature\"");
    assertThat(json).contains("\"nta_pcap_path\":\"datasets/pcap/exfil_001.pcap\"");
    assertThat(json).contains("\"nta_detection_hint\"");

    TrafficPatternPayload decoded = mapper.readValue(json, TrafficPatternPayload.class);
    assertThat(decoded.getNtaCategory()).isEqualTo("data_exfiltration");
    assertThat(decoded.getNtaProtocol()).isEqualTo("DNS");
    assertThat(decoded.getNtaSignature())
        .isEqualTo("alert dns any any -> any any (msg:\"ET POLICY DNS TXT exfil\";)");
    assertThat(decoded.getNtaPcapPath()).isEqualTo("datasets/pcap/exfil_001.pcap");
    assertThat(decoded.getNtaDetectionHint()).isEqualTo("看 dns.qry.name 长度>200 字节");
  }

  @Test
  void json_decode_when_optional_fields_absent_leaves_them_null() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ObjectNode legacy = mapper.createObjectNode();
    legacy.put("payload_type", "TrafficPattern");
    legacy.put("nta_category", "data_exfiltration");
    legacy.put("nta_protocol", "DNS");
    legacy.put("nta_signature", "alert dns any any -> any any (msg:\"x\";)");
    TrafficPatternPayload decoded =
        mapper.readValue(mapper.writeValueAsString(legacy), TrafficPatternPayload.class);
    assertThat(decoded.getNtaPcapPath()).isNull();
    assertThat(decoded.getNtaDetectionHint()).isNull();
  }

  @Test
  void tree_serialization_exposes_snake_case_keys() {
    TrafficPatternPayload p = newPayload();
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ObjectNode tree = (ObjectNode) mapper.valueToTree(p);
    assertThat(tree.get("payload_type").asText()).isEqualTo("TrafficPattern");
    assertThat(tree.get("nta_category").asText()).isEqualTo("data_exfiltration");
    assertThat(tree.get("nta_protocol").asText()).isEqualTo("DNS");
    assertThat(tree.has("nta_signature")).isTrue();
  }

  private static TrafficPatternPayload newPayload() {
    // Use the (id, type, name) constructor so Payload.type (the parent discriminator field,
    // @Setter(NONE)) is initialized — getTypeEnum() reads it and rejects null.
    TrafficPatternPayload p =
        new TrafficPatternPayload(
            "test-id", TrafficPatternPayload.TRAFFIC_PATTERN_TYPE, "NTA-EXFIL-001");
    p.setNtaCategory("data_exfiltration");
    p.setNtaProtocol("DNS");
    p.setNtaSignature("alert dns any any -> any any (msg:\"ET POLICY DNS TXT exfil\";)");
    return p;
  }
}
