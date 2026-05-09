package io.veriguard.attackchain.soc;

/**
 * 调用方按 connectorId 查 {@link SocConnectorRegistry} 时找不到对应实现 —— 多半是 {@code
 * SocCorrelationRuleRef.connectorId} 配置写错或对应 connector 通过 {@code @ConditionalOnProperty} 被禁用。
 */
public class ConnectorNotFoundException extends RuntimeException {

  public ConnectorNotFoundException(String connectorId) {
    super("SOC connector not found: " + connectorId);
  }
}
