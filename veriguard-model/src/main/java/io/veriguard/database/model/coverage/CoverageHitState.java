package io.veriguard.database.model.coverage;

/**
 * 覆盖度单元格 4 态语义 —— PR C3.
 *
 * <ul>
 *   <li>{@link #hit}          用例下发且 SOC 在时间窗内有匹配告警 → 该 (asset, policy) 单元格"有覆盖"</li>
 *   <li>{@link #miss}         用例下发但 SOC 在时间窗内无告警 → "无覆盖"（招标关注重点）</li>
 *   <li>{@link #timeout}      用例下发后超时未回 → 通信问题</li>
 *   <li>{@link #out_of_scope} 策略类型不适用此资产（如 ICS 设备 vs WAF 策略） → N/A</li>
 * </ul>
 */
public enum CoverageHitState {
  hit,
  miss,
  timeout,
  out_of_scope;
}
