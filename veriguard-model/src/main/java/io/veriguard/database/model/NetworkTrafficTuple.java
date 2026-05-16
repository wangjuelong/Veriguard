package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 额外四元组 value 类型 —— IPv6 安全验证系统招标 §4 "支持同一个流量安全验证用例中，包含多个端口不同的四元组".
 *
 * <p>{@link NetworkTraffic} payload 的主四元组由实体上的 5 个 {@code network_traffic_*}
 * 列承载（ipSrc/ipDst/portSrc/portDst/protocol）； 本 record 描述 *额外* 的四元组，作为 {@code
 * List<NetworkTrafficTuple>} 存入 {@code network_traffic_extra_tuples} JSONB 列。
 *
 * <p>字段命名与 {@link NetworkTraffic} 主四元组 wire JSON key 一致（{@code network_traffic_*}）—— 同名同义，便于前端 /
 * API 调用方统一处理。
 *
 * <p>调用方建议通过 {@link NetworkTraffic#allTuples()} 统一拿到主 + 额外四元组列表，无需区分两层来源。
 */
public record NetworkTrafficTuple(
    @JsonProperty("network_traffic_ip_src") @NotBlank String ipSrc,
    @JsonProperty("network_traffic_ip_dst") @NotBlank String ipDst,
    @JsonProperty("network_traffic_port_src") @NotNull Integer portSrc,
    @JsonProperty("network_traffic_port_dst") @NotNull Integer portDst,
    @JsonProperty("network_traffic_protocol") @NotBlank String protocol) {}
