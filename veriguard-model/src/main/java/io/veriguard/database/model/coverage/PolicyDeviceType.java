package io.veriguard.database.model.coverage;

/**
 * 安全设备类型 —— PR C3.
 *
 * <p>对应蓝盾 NxSOC 的告警来源类型；用于 (asset_type, policy_device_type) 组合判定 out_of_scope。
 *
 * <ul>
 *   <li>{@link #waf}  Web 应用防火墙</li>
 *   <li>{@link #ips}  入侵防御系统</li>
 *   <li>{@link #ids}  入侵检测系统</li>
 *   <li>{@link #nta}  网络流量分析</li>
 *   <li>{@link #hids} 主机入侵检测</li>
 * </ul>
 */
public enum PolicyDeviceType {
  waf,
  ips,
  ids,
  nta,
  hids;
}
