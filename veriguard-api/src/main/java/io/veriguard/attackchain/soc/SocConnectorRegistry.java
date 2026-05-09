package io.veriguard.attackchain.soc;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SOC connector 注册表（spec §4.3）.
 *
 * <p>构造时注入所有 Spring 容器中可见的 {@link SocAlertConnector} 实现，按 {@link
 * SocAlertConnector#getConnectorId()} 索引。{@link #get(String)} 找不到抛 {@link
 * ConnectorNotFoundException}（调用方多半是 {@code SocCorrelationRuleRef.connectorId} 写错或对应 connector 被
 * {@code @ConditionalOnProperty} 禁用）。
 *
 * <p>未启用任何 connector 时 {@link #all()} 返回空 —— 不抛错，因为本注册表也用于 {@code /api/soc_connectors} 元信息端点（Phase
 * 11 前端 SOC 状态页）。
 */
@Service
@Slf4j
public class SocConnectorRegistry {

  private final Map<String, SocAlertConnector> connectors;

  public SocConnectorRegistry(List<SocAlertConnector> implementations) {
    Map<String, SocAlertConnector> indexed = new HashMap<>();
    for (SocAlertConnector connector : implementations) {
      String id = connector.getConnectorId();
      if (id == null || id.isBlank()) {
        throw new IllegalStateException(
            "SocAlertConnector " + connector.getClass().getName() + " returned blank connectorId");
      }
      SocAlertConnector previous = indexed.put(id, connector);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate SocAlertConnector connectorId='"
                + id
                + "': "
                + previous.getClass().getName()
                + " vs "
                + connector.getClass().getName());
      }
    }
    this.connectors = Map.copyOf(indexed);
    log.info(
        "Registered SOC connectors: {}",
        this.connectors.values().stream()
            .map(c -> c.getConnectorId() + " (" + c.getDisplayName() + ")")
            .collect(Collectors.joining(", ")));
  }

  /** 按 connectorId 查 connector；找不到抛 {@link ConnectorNotFoundException}。 */
  public SocAlertConnector get(String connectorId) {
    SocAlertConnector connector = connectors.get(connectorId);
    if (connector == null) {
      throw new ConnectorNotFoundException(connectorId);
    }
    return connector;
  }

  /** 列出所有已注册的 connector（用于 admin UI 状态页 / 健康检查批量调用）。 */
  public Collection<SocAlertConnector> all() {
    return connectors.values();
  }

  /** connector 是否已注册（不会抛错）；用于上层在调用 {@link #get(String)} 前的可选校验。 */
  public boolean has(String connectorId) {
    return connectors.containsKey(connectorId);
  }
}
