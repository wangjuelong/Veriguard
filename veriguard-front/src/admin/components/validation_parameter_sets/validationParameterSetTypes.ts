/**
 * Phase 10 ParameterSet UI 共享类型（PRD §2.4 / spec §2.2.4 + §6.3.5）.
 *
 * 与后端 ValidationParameterSet 实体对齐，但暂未通过 yarn generate-types-from-api 同步到
 * api-types.d.ts —— 给前端组件提供独立类型表面，让 UI 可独立开发与单测。
 */

/** 链路级 SOC correlation rule 引用（与 Phase 8 类型同形）. */
export interface SocCorrelationRuleRef {
  connector_id: string;
  rule_id: string;
  display_name: string;
  match_window_seconds: number;
}

/** 默认验证目标条目 —— Asset / AssetGroup 的最小可识别字段. */
export interface ParameterSetTargetRef {
  /** 类型标识：'asset' / 'asset_group' */
  kind: 'asset' | 'asset_group';
  id: string;
  /** 显示名（编辑界面用；持久化只存 kind+id）. */
  display_name?: string;
}

/** Tag 引用 —— UI 上做 chip 显示. */
export interface TagRef {
  id: string;
  name: string;
}

/** ValidationParameterSet 表单值（创建 / 编辑） */
export interface ValidationParameterSetValue {
  name: string;
  description: string | null;
  is_template: boolean;
  default_targets: ParameterSetTargetRef[];
  prevention_expected_score: number;
  prevention_expiration_seconds: number;
  detection_expected_score: number;
  detection_expiration_seconds: number;
  soc_correlation_rules: SocCorrelationRuleRef[];
  tags: TagRef[];
}

/** ParameterSet 列表卡片展示数据（含 ID + 时间戳）. */
export interface ValidationParameterSetSummary extends ValidationParameterSetValue {
  id: string;
  created_at: string;
  updated_at: string;
}
