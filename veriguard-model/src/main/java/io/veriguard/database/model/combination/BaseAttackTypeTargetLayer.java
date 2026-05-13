package io.veriguard.database.model.combination;

/**
 * 基础攻击类型目标层 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR B5.
 *
 * <p>用于把攻击类型按目标防御层归类，便于前端按层过滤 / D5 任务编辑器分组展示.
 * lowercase 常量与 V17__base_attack_types.sql 中 base_attack_type_target_layer 列对齐.
 */
public enum BaseAttackTypeTargetLayer {
  /** 边界（防火墙 / WAF / 反代）. */
  boundary,
  /** 流量层（IDS / NDR / 流量探针）. */
  traffic,
  /** 主机层（HIDS / EDR / agent）. */
  host,
  /** 应用层（业务系统 / 中间件）. */
  application
}
