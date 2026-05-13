package io.veriguard.database.model.monitoring;

/**
 * 监控历史记录状态 —— PR C4 招标 §3.2.
 *
 * <p>合法 transitions：
 *
 * <ul>
 *   <li>triggered -&gt; completed（关联的 coverage_run 终结后回填 4 计数）
 *   <li>triggered -&gt; failed（触发本身失败，例如 baseline 已删 / runner 异常）
 * </ul>
 *
 * <p>注：不复用 {@code CoverageRunStatus}，因为 monitoring 关心的是「触发-完成-失败」三态，
 * 而 coverage_run 的 pending/running/cancelled 是其内部生命周期，不应外泄。
 */
public enum MonitoringRunStatus {
  triggered,
  completed,
  failed;

  public boolean isTerminal() {
    return this == completed || this == failed;
  }
}
