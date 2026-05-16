package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;

@Getter
@Setter
public class StatusPayload {

  @JsonProperty("payload_name")
  private String name;

  @JsonProperty("payload_description")
  private String description;

  @JsonProperty("payload_type")
  private String type;

  @JsonProperty("payload_cleanup_executor")
  private String cleanupExecutor;

  @JsonProperty("payload_command_blocks")
  @Singular
  private List<PayloadCommandBlock> payloadCommandBlocks = new ArrayList<>();

  @JsonProperty("payload_arguments")
  private List<PayloadArgument> arguments = new ArrayList<>();

  @JsonProperty("payload_prerequisites")
  private List<PayloadPrerequisite> prerequisites = new ArrayList<>();

  @JsonProperty("payload_external_id")
  private String externalId;

  @JsonProperty("executable_file")
  private StatusPayloadDocument executableFile;

  @JsonProperty("file_drop_file")
  private StatusPayloadDocument fileDropFile;

  @JsonProperty("dns_resolution_hostname")
  private String hostname;

  @JsonProperty("network_traffic_ip_src")
  @NotNull
  private String ipSrc;

  @JsonProperty("network_traffic_ip_dst")
  @NotNull
  private String ipDst;

  @JsonProperty("network_traffic_port_src")
  @NotNull
  private Integer portSrc;

  @JsonProperty("network_traffic_port_dst")
  @NotNull
  private Integer portDst;

  @JsonProperty("network_traffic_protocol")
  @NotNull
  private String protocol;

  /**
   * 额外四元组 wire 形态 —— IPv6 安全验证系统招标 §4 "支持同一个流量安全验证用例中，包含多个端口不同的四元组".
   *
   * <p>主四元组继续由上方 5 个标量字段承载（向后兼容）。本字段从 {@link NetworkTraffic#getExtraTuples()} 透传，让 executor / 前端 /
   * 报告侧能拿到 payload 声明的全部 (src/dst/port/proto) 组合，无需再回查 Payload entity。
   *
   * <p>非 NetworkTraffic payload 此字段为 null（不输出 JSON 节点 / Jackson 默认不写入 null）。
   */
  @JsonProperty("network_traffic_extra_tuples")
  private List<NetworkTrafficTuple> extraTuples;

  public StatusPayload() {}

  public StatusPayload(
      String name,
      String description,
      String type,
      String protocol,
      Integer portDst,
      Integer portSrc,
      String ipDst,
      String ipSrc,
      String hostname,
      Document fileDropFile,
      Document executableFile,
      String externalId,
      List<PayloadPrerequisite> prerequisites,
      List<PayloadArgument> arguments,
      List<PayloadCommandBlock> payloadCommandBlocks,
      String cleanupExecutor) {
    this.name = name;
    this.description = description;
    this.type = type;
    this.protocol = protocol;
    this.portDst = portDst;
    this.portSrc = portSrc;
    this.ipDst = ipDst;
    this.ipSrc = ipSrc;
    this.hostname = hostname;
    if (fileDropFile != null) {
      this.fileDropFile = new StatusPayloadDocument(fileDropFile);
    }
    if (executableFile != null) {
      this.executableFile = new StatusPayloadDocument(executableFile);
    }
    this.externalId = externalId;
    this.prerequisites = prerequisites;
    this.arguments = arguments;
    this.payloadCommandBlocks = payloadCommandBlocks;
    this.cleanupExecutor = cleanupExecutor;
  }
}
