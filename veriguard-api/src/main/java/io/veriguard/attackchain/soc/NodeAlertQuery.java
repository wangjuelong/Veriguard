package io.veriguard.attackchain.soc;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 节点级 SOC 告警查询参数（spec §4.2）.
 *
 * @param nodeId 触发查询的节点 ID
 * @param injectExecutedAt 节点 inject 执行时刻（查询窗口起点）
 * @param queryWindowEnd 查询窗口终点（通常 = injectExecutedAt + node.expectation.expiration_time）
 * @param targetIps 节点目标资产的 IP 集合，让 connector 拼 {@code host.ip IN [...]} 过滤；无则空集
 * @param nodeContractTags 节点 contract 上的标签（如 {@code mitre:T1059}），可用于关联规则匹配
 * @param connectorParams connector 实现自定义键值对（不进数据库；调用方按需提供）
 */
public record NodeAlertQuery(
    String nodeId,
    Instant injectExecutedAt,
    Instant queryWindowEnd,
    Set<String> targetIps,
    Set<String> nodeContractTags,
    Map<String, String> connectorParams) {

  public NodeAlertQuery {
    if (nodeId == null || nodeId.isBlank()) {
      throw new IllegalArgumentException("nodeId required");
    }
    if (injectExecutedAt == null) {
      throw new IllegalArgumentException("injectExecutedAt required");
    }
    if (queryWindowEnd == null) {
      throw new IllegalArgumentException("queryWindowEnd required");
    }
    if (queryWindowEnd.isBefore(injectExecutedAt)) {
      throw new IllegalArgumentException("queryWindowEnd must be >= injectExecutedAt");
    }
    targetIps = targetIps == null ? Set.of() : Set.copyOf(targetIps);
    nodeContractTags = nodeContractTags == null ? Set.of() : Set.copyOf(nodeContractTags);
    connectorParams = connectorParams == null ? Map.of() : Map.copyOf(connectorParams);
  }
}
