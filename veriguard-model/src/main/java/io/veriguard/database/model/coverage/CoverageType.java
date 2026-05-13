package io.veriguard.database.model.coverage;

/**
 * 覆盖度场景类型 —— IPv6 安全验证系统 PR C3.
 *
 * <p>对应招标条款：
 * <ul>
 *   <li>{@link #boundary} —— §3.1 边界资产覆盖度验证（资产 × 策略矩阵）</li>
 *   <li>{@link #traffic}  —— §4.1 流量边界覆盖度验证（资产 × 策略矩阵 + 流量复盘）</li>
 * </ul>
 *
 * <p>两场景共用同一组表，由 {@link CoverageBaseline#getCoverageType()} 区分。
 */
public enum CoverageType {
  boundary,
  traffic;
}
