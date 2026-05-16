package io.veriguard.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.NetworkTraffic;
import io.veriguard.database.model.NetworkTrafficTuple;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.model.StatusPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * §4.4 IPv6 安全验证系统招标 "同一流量验证用例中包含多个端口不同的四元组" —— dispatch propagation 单测.
 *
 * <p>验证 {@link AttackChainNodeUtils#getStatusPayloadFromAttackChainNode(AttackChainNode)} 在
 * NetworkTraffic payload 携带 {@code extraTuples} 时，把这份额外四元组列表透传到 wire 形态 {@link
 * StatusPayload#getExtraTuples()}.
 */
class AttackChainNodeUtilsMultiTupleDispatchTest {

  private final AttackChainNodeUtils utils = new AttackChainNodeUtils();

  @Test
  void network_traffic_extra_tuples_propagate_to_status_payload() {
    NetworkTraffic payload =
        new NetworkTraffic("payload-id", NetworkTraffic.NETWORK_TRAFFIC_TYPE, "NTA-MULTI-001");
    payload.setIpSrc("2001:db8::1");
    payload.setIpDst("2001:db8::2");
    payload.setPortSrc(32768);
    payload.setPortDst(443);
    payload.setProtocol("TCP");
    payload.setExtraTuples(
        List.of(
            new NetworkTrafficTuple("2001:db8::1", "2001:db8::2", 32769, 8080, "TCP"),
            new NetworkTrafficTuple("2001:db8::3", "2001:db8::4", 5000, 53, "UDP")));

    AttackChainNode attackChainNode = nodeWith(payload);

    StatusPayload result = utils.getStatusPayloadFromAttackChainNode(attackChainNode);

    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo(NetworkTraffic.NETWORK_TRAFFIC_TYPE);
    // primary tuple
    assertThat(result.getIpSrc()).isEqualTo("2001:db8::1");
    assertThat(result.getPortDst()).isEqualTo(443);
    assertThat(result.getProtocol()).isEqualTo("TCP");
    // extras propagated
    assertThat(result.getExtraTuples()).hasSize(2);
    assertThat(result.getExtraTuples().get(0).portDst()).isEqualTo(8080);
    assertThat(result.getExtraTuples().get(1).protocol()).isEqualTo("UDP");
  }

  @Test
  void network_traffic_without_extras_yields_empty_extras_on_wire() {
    NetworkTraffic payload =
        new NetworkTraffic("payload-id", NetworkTraffic.NETWORK_TRAFFIC_TYPE, "NTA-SINGLE-001");
    payload.setIpSrc("10.0.0.1");
    payload.setIpDst("10.0.0.2");
    payload.setPortSrc(1024);
    payload.setPortDst(80);
    payload.setProtocol("TCP");
    // NetworkTraffic.extraTuples default = empty list (not null)

    AttackChainNode attackChainNode = nodeWith(payload);

    StatusPayload result = utils.getStatusPayloadFromAttackChainNode(attackChainNode);

    assertThat(result).isNotNull();
    // wire field receives entity's empty list verbatim — contract is "propagate whatever entity
    // holds"; absent JSON key vs empty array is decided at Jackson serialization time.
    assertThat(result.getExtraTuples()).isNotNull().isEmpty();
  }

  private static AttackChainNode nodeWith(NetworkTraffic payload) {
    NodeContract contract = new NodeContract();
    contract.setPayload(payload);
    AttackChainNode node = new AttackChainNode();
    node.setNodeContract(contract);
    return node;
  }
}
