/**
 * Phase 11 SOC Connector 状态页 + 规则选择器共享类型（PRD §2.4 / spec §6.3.6）.
 *
 * 与后端 io.veriguard.attackchain.soc.HealthCheckResult.Status / SocAlertConnector / AvailableRule
 * 对齐，但暂未通过 yarn generate-types-from-api 同步到 api-types.d.ts —— 给前端组件提供独立类型表面。
 */

/** Connector 健康状态（与后端 HealthCheckResult.Status 对齐）. */
export type SocConnectorHealthStatus = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'DISABLED';

/** Connector 状态摘要（来自 /api/soc_connectors）. */
export interface SocConnectorStatusItem {
  connectorId: string;
  displayName: string;
  status: SocConnectorHealthStatus;
  /** 健康检查 message；UNHEALTHY 显示原因，HEALTHY 时可空 */
  message?: string;
  /** {@code listAvailableRules()} 返回的规则数；DISABLED 时通常 undefined */
  availableRuleCount?: number;
  /** 上次健康检查时刻 ISO 字符串；为空时显示 "—" */
  lastCheckedAt?: string;
}

/** 单条 correlation rule 选项（来自 connector 的 listAvailableRules）. */
export interface SocConnectorRuleOption {
  /** 该 SOC 平台内规则 ID（与 SocCorrelationRuleRef.rule_id 对齐） */
  ruleId: string;
  displayName: string;
  description?: string;
  category?: string;
}
