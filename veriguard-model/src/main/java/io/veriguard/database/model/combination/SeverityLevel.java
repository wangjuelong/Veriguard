package io.veriguard.database.model.combination;

/**
 * 攻击组合 cluster 严重度分级 —— IPv6 安全验证系统 §3.6 ★2 PR D4.
 *
 * <p>Lowercase 与 Java enum 直接对齐（@Enumerated(STRING) 写入 DB / 输出 wire）.
 *
 * <ul>
 *   <li>critical —— score &gt; critical_threshold（默认 &gt; 70）</li>
 *   <li>high     —— score &gt; high_threshold     （默认 &gt; 40）</li>
 *   <li>medium   —— score &gt; medium_threshold   （默认 &gt; 10）</li>
 *   <li>info     —— 兜底（默认 ≤ 10）</li>
 * </ul>
 */
public enum SeverityLevel {
  critical,
  high,
  medium,
  info
}
