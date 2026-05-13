package io.veriguard.database.model.combination;

/**
 * 攻击组合任务状态机 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D2.
 *
 * <p>Lowercase 与 Java enum 直接对齐，方便 DB 行 / JSON wire / Java 三处统一.
 *
 * <p>合法 transitions:
 * <ul>
 *   <li>pending -&gt; running</li>
 *   <li>running -&gt; paused / completed / cancelled / failed</li>
 *   <li>paused -&gt; running / cancelled / failed</li>
 *   <li>pending -&gt; cancelled（未启动直接取消）</li>
 * </ul>
 *
 * <p>终态：completed / cancelled / failed —— 不再转出.
 */
public enum AttackCombinationRunStatus {
  pending,
  running,
  paused,
  completed,
  cancelled,
  failed;

  /** 是否可暂停（仅 running -&gt; paused）. */
  public boolean canPause() {
    return this == running;
  }

  /** 是否可恢复（仅 paused -&gt; running）. */
  public boolean canResume() {
    return this == paused;
  }

  /** 是否可取消（非终态都可以）. */
  public boolean canCancel() {
    return this == pending || this == running || this == paused;
  }

  /** 是否终态. */
  public boolean isTerminal() {
    return this == completed || this == cancelled || this == failed;
  }
}
