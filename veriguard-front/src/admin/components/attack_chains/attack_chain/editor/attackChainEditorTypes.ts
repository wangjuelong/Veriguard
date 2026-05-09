/**
 * Phase 8 编辑器组件共用类型（PRD §2.4 / spec §6）.
 *
 * 这些类型与后端 V3 schema 对齐，但暂未通过 yarn generate-types-from-api 同步到 api-types.d.ts
 * （需启动 API + 重生成）。本文件给前端组件提供独立的最小类型表面，让 UI 可以独立开发与单测，
 * 后续 i18n / 路由整合 phase 把这些类型对齐到 api-types 即可。
 */

export type ExecutionMode = 'STOP_ON_BLOCK' | 'CONTINUE';

export type ExpectationDimension = 'PREVENTION' | 'DETECTION' | 'MANUAL';

export type ExpectationConditionStatus
  = | 'ANY_SUCCESS'
    | 'ANY_FAILED'
    | 'ALL_SUCCESS'
    | 'ALL_FAILED'
    | 'SETTLED';

export type EdgeBoolean = 'AND' | 'OR';

/** 单条 expectation 条件（叶子节点） */
export interface EdgeConditionLeaf {
  kind: 'leaf';
  dimension: ExpectationDimension;
  status: ExpectationConditionStatus;
}

/** 复合条件（AND / OR 嵌套） */
export interface EdgeConditionGroup {
  kind: 'group';
  op: EdgeBoolean;
  children: EdgeConditionTree[];
}

/** Edge 条件树（叶子或组）；ConditionEdgePopover 编辑此结构后写回 AttackChainEdge.condition. */
export type EdgeConditionTree = EdgeConditionLeaf | EdgeConditionGroup;

/** 链路级 SOC correlation rule 引用（与后端 SocCorrelationRuleRef record 对齐）. */
export interface SocCorrelationRuleRef {
  connector_id: string;
  rule_id: string;
  display_name: string;
  match_window_seconds: number;
}

/** 链路级设置 drawer 表单值（AttackChain 模板字段子集）. */
export interface AttackChainSettingsValue {
  execution_mode: ExecutionMode;
  validation_parameter_set_id: string | null;
  soc_correlation_rules: SocCorrelationRuleRef[];
}

/** 节点级编辑 drawer 表单值（AttackChainNode 关键字段）. */
export interface AttackChainNodeEditValue {
  node_title: string;
  node_description: string | null;
  validation_parameter_set_id: string | null;
  repeat_count: number;
  repeat_interval_seconds: number;
}

/** ⚙ 节点角标的可视化状态（决定颜色 / 文案）. */
export type NodeBadgeState
  = | { kind: 'OK' }
    | { kind: 'OVERRIDE' }
    | {
      kind: 'REPEAT';
      count: number;
    }
    | {
      kind: 'INCOMPLETE';
      reason: string;
    };

/** ParameterSet 选项（picker 下拉填充）. */
export interface ParameterSetOption {
  id: string;
  name: string;
  is_template: boolean;
}
