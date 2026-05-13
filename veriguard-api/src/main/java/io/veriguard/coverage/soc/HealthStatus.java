package io.veriguard.coverage.soc;

/**
 * SocAdapter 健康状态 —— PR C3.
 *
 * <ul>
 *   <li>{@link #healthy}     正常</li>
 *   <li>{@link #degraded}    可达但部分功能受限（如 SOC 后端响应慢）</li>
 *   <li>{@link #unreachable} 不可达 / 未配置</li>
 * </ul>
 */
public enum HealthStatus {
  healthy,
  degraded,
  unreachable;
}
