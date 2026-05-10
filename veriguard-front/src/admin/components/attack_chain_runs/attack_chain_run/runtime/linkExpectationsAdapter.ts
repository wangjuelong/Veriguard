import { type AttackChainLinkExpectationWire } from '../../../../../actions/attack_chain_runs/attack_chain_run-action';
import { type LinkExpectationItem } from './attackChainRuntimeTypes';

/**
 * 后端 wire-format `AttackChainLinkExpectationOutput` 适配为 panel 视图模型 `LinkExpectationItem`.
 *
 * 字段映射规则：
 * - `display_name` 缺失时回退到 `connector_id / rule_id`
 * - SUCCESS：取首条 trace 的 `incident_id` 作为 incidentRef，没 trace 但 status=SUCCESS 时给 generic
 * - PARTIAL：score / expectedScore 透传给 panel 显示分数比
 * - FAILED：失败原因 `0 命中（窗口 ${seconds}s）`
 * - UNKNOWN：从首条 trace 的 raw_payload.reason 提取（service 用 markUnknown 时不写 trace —— UI 兜底文案）
 *
 * 不抛错，缺字段给降级文案，避免运行画布崩。
 */
export const toLinkExpectationItem = (
  wire: AttackChainLinkExpectationWire,
): LinkExpectationItem => {
  const ruleDisplayName = wire.display_name ?? `${wire.connector_id} / ${wire.rule_id}`;
  const base: LinkExpectationItem = {
    id: wire.link_expectation_id,
    connectorId: wire.connector_id,
    ruleDisplayName,
    status: wire.status,
  };

  switch (wire.status) {
    case 'SUCCESS': {
      const firstTrace = wire.traces[0];
      return {
        ...base,
        incidentRef: firstTrace?.incident_id ? `#${firstTrace.incident_id}` : undefined,
        score: wire.score,
        expectedScore: wire.expected_score,
      };
    }
    case 'PARTIAL':
      return {
        ...base,
        score: wire.score,
        expectedScore: wire.expected_score,
      };
    case 'FAILED':
      return {
        ...base,
        failureReason: `0 命中（窗口 ${wire.match_window_seconds}s）`,
      };
    case 'UNKNOWN':
      return {
        ...base,
        unknownReason: '查询失败 / 已过期',
      };
    case 'PENDING':
    default:
      return base;
  }
};

export const toLinkExpectationItems = (
  wires: AttackChainLinkExpectationWire[] | undefined | null,
): LinkExpectationItem[] => {
  if (!wires) {
    return [];
  }
  return wires.map(toLinkExpectationItem);
};
