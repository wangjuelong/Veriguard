package io.veriguard.combination.severity;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.Tag;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 资产敏感度评分（0-100）—— IPv6 安全验证系统 §3.6 ★2 PR D4.
 *
 * <p>用 Asset.tags（name 字段）匹配关键字打分：
 * <ul>
 *   <li>含 "production" / "critical" / "core" → {@link #HIGH_SENSITIVITY} (100)</li>
 *   <li>含 "dev" / "test" / "staging"        → {@link #LOW_SENSITIVITY} (25)</li>
 *   <li>其他 / null asset                      → {@link #DEFAULT_SENSITIVITY} (50)</li>
 * </ul>
 *
 * <p>高敏感关键字优先于低敏感关键字（同时命中按高分计）。
 * 标签匹配 case-insensitive、子串包含（substring contains）。
 */
@Component
public class AssetSensitivityScorer {

  public static final int HIGH_SENSITIVITY = 100;
  public static final int DEFAULT_SENSITIVITY = 50;
  public static final int LOW_SENSITIVITY = 25;

  private static final Set<String> HIGH_KEYWORDS =
      Set.of("production", "critical", "core");
  private static final Set<String> LOW_KEYWORDS =
      Set.of("dev", "test", "staging");

  /**
   * @return 0-100 敏感度评分；null asset 返回默认值
   */
  public int scoreFor(Asset asset) {
    if (asset == null) {
      return DEFAULT_SENSITIVITY;
    }
    Set<Tag> tags = asset.getTags();
    if (tags == null || tags.isEmpty()) {
      return DEFAULT_SENSITIVITY;
    }
    boolean hasHigh = false;
    boolean hasLow = false;
    for (Tag tag : tags) {
      String name = tag.getName();
      if (name == null || name.isBlank()) {
        continue;
      }
      String lowered = name.toLowerCase(Locale.ROOT);
      if (matchesAny(lowered, HIGH_KEYWORDS)) {
        hasHigh = true;
      } else if (matchesAny(lowered, LOW_KEYWORDS)) {
        hasLow = true;
      }
    }
    if (hasHigh) {
      return HIGH_SENSITIVITY;
    }
    if (hasLow) {
      return LOW_SENSITIVITY;
    }
    return DEFAULT_SENSITIVITY;
  }

  private static boolean matchesAny(String lowered, Set<String> keywords) {
    for (String kw : keywords) {
      if (lowered.contains(kw)) {
        return true;
      }
    }
    return false;
  }
}
