package io.veriguard.combination.severity;

import io.veriguard.database.model.combination.BaseAttackType;
import io.veriguard.database.repository.BaseAttackTypeRepository;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基础攻击类型 → 严重度评分（0-100）映射 —— IPv6 安全验证系统 §3.6 ★2.
 *
 * <p>PR D4 阶段为 12 类内置硬编码 Map；PR B5 改为 DB-driven：启动时从
 * {@code base_attack_types} 表读取 ≥ 250 类 severity_score 缓存到内存。
 *
 * <p>Fallback 策略：当 DB 注入但表为空（首次启动 / 测试态使用 no-arg 构造），落到
 * 内置 LEGACY_SEVERITY 的 12 类硬编码（保 D4 行为，避免 SeverityClassifier 全部回 50）。
 *
 * <p>未知 base_attack_type 一律返回 {@link #UNKNOWN_SEVERITY}（与 D4 对齐）。
 */
@Component
@Slf4j
public class BaseAttackTypeSeverityCatalog {

  /** 未知类型 fallback 分值（中性） */
  public static final int UNKNOWN_SEVERITY = 50;

  /** D4 内置默认 severity —— DB 表为空 / 未注入 repo 时回退使用. */
  static final Map<String, Integer> LEGACY_SEVERITY =
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

  private final BaseAttackTypeRepository repository;

  /** 运行时缓存（小写 name → severity 0-100）；线程安全便于热加载场景. */
  private final Map<String, Integer> cache = new ConcurrentHashMap<>();

  /** 标记：true = DB 加载成功且非空；false = 走 LEGACY_SEVERITY. */
  private boolean dbBacked;

  /** Spring 注入构造器（生产路径）. */
  public BaseAttackTypeSeverityCatalog(BaseAttackTypeRepository repository) {
    this.repository = repository;
  }

  /**
   * No-arg 构造器 —— 仅供既有非 Spring 单测兼容（{@link SeverityClassifier} 测试 / 旧
   * BaseAttackTypeSeverityCatalogTest）。直接落到 LEGACY_SEVERITY 行为。
   */
  public BaseAttackTypeSeverityCatalog() {
    this.repository = null;
    this.cache.putAll(LEGACY_SEVERITY);
    this.dbBacked = false;
  }

  /** 启动时尝试从 DB 加载；表空 / repo 缺失 → 落到 LEGACY_SEVERITY（fail-safe）. */
  @PostConstruct
  void loadFromDb() {
    if (repository == null) {
      return;
    }
    Map<String, Integer> loaded = new HashMap<>();
    for (BaseAttackType t : repository.findAll()) {
      String name = t.getName();
      Integer score = t.getSeverityScore();
      if (name == null || name.isBlank() || score == null) {
        continue;
      }
      loaded.put(name.toLowerCase(Locale.ROOT), score);
    }
    if (loaded.isEmpty()) {
      log.warn(
          "BaseAttackTypeSeverityCatalog: base_attack_types DB table is empty;"
              + " falling back to {} hardcoded defaults",
          LEGACY_SEVERITY.size());
      cache.clear();
      cache.putAll(LEGACY_SEVERITY);
      dbBacked = false;
    } else {
      cache.clear();
      cache.putAll(loaded);
      dbBacked = true;
      log.info(
          "BaseAttackTypeSeverityCatalog: loaded {} severity entries from DB", loaded.size());
    }
  }

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
    Integer v = cache.get(baseAttackType.toLowerCase(Locale.ROOT));
    return v == null ? UNKNOWN_SEVERITY : v;
  }

  /** 当前是否由 DB 表驱动（vs. fallback 到内置 hardcoded）. */
  public boolean isDbBacked() {
    return dbBacked;
  }

  /** 当前缓存大小（测试观测用）. */
  public int cacheSize() {
    return cache.size();
  }
}
