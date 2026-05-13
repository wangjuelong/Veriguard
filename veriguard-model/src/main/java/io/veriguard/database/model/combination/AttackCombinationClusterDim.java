package io.veriguard.database.model.combination;

/**
 * 攻击组合聚类维度 —— IPv6 安全验证系统 §3.6 ★2 PR D3.
 *
 * <p>Lowercase 与 Java enum 直接对齐.
 *
 * <ul>
 *   <li>asset —— GROUP BY result.asset_id；cluster_key = asset_id；cluster_label = asset_name</li>
 *   <li>device —— GROUP BY 推导的 device_key（Endpoint.hostname / asset_name fallback）</li>
 * </ul>
 */
public enum AttackCombinationClusterDim {
  asset,
  device
}
