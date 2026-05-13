package io.veriguard.database.model.coverage;

/**
 * 覆盖度评估任务状态 —— PR C3.
 *
 * <p>合法 transitions：
 * <ul>
 *   <li>pending -&gt; running -&gt; completed / failed</li>
 *   <li>pending / running -&gt; cancelled</li>
 * </ul>
 *
 * <p>终态：{@link #completed} / {@link #failed} / {@link #cancelled}.
 */
public enum CoverageRunStatus {
  pending,
  running,
  completed,
  failed,
  cancelled;

  public boolean isTerminal() {
    return this == completed || this == failed || this == cancelled;
  }
}
