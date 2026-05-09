package io.veriguard.attackchain.soc;

import java.util.List;

/**
 * SOC 告警平台连接器 SPI（PRD §2.4 / spec §4.1）.
 *
 * <p>Veriguard 把"打"（NodeExecutor / 攻击执行）和"查告警"（SocAlertConnector / 防御侧观察）拆成两套独立 SPI —— 后者面向 Elastic
 * SIEM、Splunk、QRadar 等 SOC 平台，目的是验证攻击是否被防御方检测/告警。
 *
 * <p>实现类应：
 *
 * <ul>
 *   <li>通过 Spring {@code @Component} 暴露 —— {@link SocConnectorRegistry} 自动发现并按 {@link
 *       #getConnectorId()} 索引
 *   <li>用 {@code @ConditionalOnProperty} 等机制让运维通过 {@code application.properties} + 环境变量启停，
 *       不入数据库（避免凭证泄漏面）
 *   <li>失败显式：网络/凭证/超时错误向上抛 {@link RuntimeException}，由调用方决定重试 / 写入 UNKNOWN trace； <b>不返回空列表掩盖错误</b>
 * </ul>
 *
 * <p>调用时序见 spec §4.6：节点级查询发生在 {@code AWAITING_EXPECTATION} 阶段每 30s 轮询；链路级查询发生在 所有节点 SETTLED 后一次性调用
 * + 失败 exponential backoff 重试 ≤ 3 次。
 */
public interface SocAlertConnector {

  /**
   * 连接器唯一标识（{@code "elastic"} / {@code "splunk"} / {@code "qradar"} / 自研）；与 {@code
   * SocCorrelationRuleRef.connectorId} 配对查表。
   */
  String getConnectorId();

  /** 给前端 / 文档展示的友好名（如 "Elastic SIEM"）。 */
  String getDisplayName();

  /**
   * 节点级查询：某个 inject 触发后，SOC 是否产生匹配告警？
   *
   * <p>时机：节点 {@code AWAITING_EXPECTATION} 期间，定时轮询 + 可选 push 触发。
   *
   * <p>输出：写入 {@code NodeExpectationTrace}（DETECTION 维度）→ 累加 score。
   */
  List<DetectionMatch> queryNodeAlert(NodeAlertQuery query);

  /**
   * 链路级查询：某 correlation rule 在指定时间窗口内是否触发？
   *
   * <p>时机：所有节点 {@code SETTLED} 后一次性调用 + 失败重试（exponential backoff，≤ 3 次）。
   *
   * <p>输出：写入 {@code LinkExpectationTrace} → 累加 score（Phase 7 落地）。
   */
  List<CorrelationMatch> queryCorrelationRule(CorrelationRuleQuery query);

  /** 给 UI 下拉选规则：列出该 SOC 平台上可用的 correlation rules / saved searches；建议本地缓存 5 分钟。 */
  List<AvailableRule> listAvailableRules();

  /** 配置健康检查（凭证 / 网络 / 权限）；用于 admin UI "集成 → SOC 连接器" 红绿灯。 */
  HealthCheckResult checkHealth();
}
