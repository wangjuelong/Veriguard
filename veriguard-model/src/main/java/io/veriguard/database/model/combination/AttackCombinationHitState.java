package io.veriguard.database.model.combination;

/**
 * 攻击组合结果命中状态 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>Lowercase 与 Java enum 直接对齐.
 *
 * <ul>
 *   <li>pending —— 尚未派发执行</li>
 *   <li>running —— 已派发，executor 正在处理</li>
 *   <li>hit —— 安全设备命中（成功阻断 / 告警）</li>
 *   <li>miss —— 安全设备未命中（绕过成功）</li>
 *   <li>timeout —— executor 超时未返回</li>
 *   <li>failed —— 系统错误（重试已用尽）</li>
 * </ul>
 */
public enum AttackCombinationHitState {
  pending,
  running,
  hit,
  miss,
  timeout,
  failed;

  /** 是否最终状态. */
  public boolean isTerminal() {
    return this == hit || this == miss || this == timeout || this == failed;
  }
}
