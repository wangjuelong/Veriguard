package io.veriguard.combination.severity;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 基础攻击类型 → 严重度评分（0-100）映射 —— IPv6 安全验证系统 §3.6 ★2 PR D4.
 *
 * <p>固定查表，不读 DB / 不可配（YAGNI；如需客户可调，后续 PR 扩展）。
 * 评分代表"若该类型攻击被放过去后的潜在破坏度"，不是当下命中率。
 *
 * <p>未知 base_attack_type 一律返回 {@link #UNKNOWN_SEVERITY}。
 */
@Component
public class BaseAttackTypeSeverityCatalog {

  /** 未知类型 fallback 分值（中性） */
  public static final int UNKNOWN_SEVERITY = 50;

  /** lowercase key → 0-100 严重度 */
  private static final Map<String, Integer> SEVERITY_BY_TYPE =
      Map.ofEntries(
          Map.entry("sql_injection", 90),
          Map.entry("command_execution", 95),
          Map.entry("xxe", 85),
          Map.entry("ssrf", 80),
          Map.entry("ssti", 85),
          Map.entry("xss", 60),
          Map.entry("csrf", 45),
          Map.entry("directory_traversal", 70),
          Map.entry("brute_force", 50),
          Map.entry("upload_bypass", 75),
          Map.entry("weak_credential", 55),
          Map.entry("oversized_upload", 35),
          Map.entry("unknown", UNKNOWN_SEVERITY));

  /**
   * 查表（case-insensitive）。
   *
   * @param baseAttackType 例如 "sql_injection" / "SQL_INJECTION" / null
   * @return 0-100 严重度
   */
  public int severityFor(String baseAttackType) {
    if (baseAttackType == null || baseAttackType.isBlank()) {
      return UNKNOWN_SEVERITY;
    }
    Integer v = SEVERITY_BY_TYPE.get(baseAttackType.toLowerCase(Locale.ROOT));
    return v == null ? UNKNOWN_SEVERITY : v;
  }
}
