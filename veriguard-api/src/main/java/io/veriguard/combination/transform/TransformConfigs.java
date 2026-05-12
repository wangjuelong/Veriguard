package io.veriguard.combination.transform;

import java.util.Map;

/**
 * 共享的 transform_config 读取工具.
 *
 * <p>所有 {@link PayloadTransform} 实现都通过本类读取 config 项，统一 fail-fast 行为：
 * 必填项缺失抛 {@link IllegalArgumentException}；类型不匹配抛 {@link ClassCastException}.
 * 不写默认值降级（参见项目 CLAUDE.md "No fallback code" 约定）.
 */
public final class TransformConfigs {

  private TransformConfigs() {}

  public static String requiredString(Map<String, Object> config, String key) {
    if (config == null) {
      throw new IllegalArgumentException("config must not be null (required key=" + key + ")");
    }
    Object v = config.get(key);
    if (v == null) {
      throw new IllegalArgumentException("Missing required config key: '" + key + "'");
    }
    if (!(v instanceof String s)) {
      throw new IllegalArgumentException(
          "Config key '" + key + "' must be String, got " + v.getClass().getSimpleName());
    }
    return s;
  }

  public static int requiredInt(Map<String, Object> config, String key) {
    if (config == null) {
      throw new IllegalArgumentException("config must not be null (required key=" + key + ")");
    }
    Object v = config.get(key);
    if (v == null) {
      throw new IllegalArgumentException("Missing required config key: '" + key + "'");
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    throw new IllegalArgumentException(
        "Config key '" + key + "' must be Number, got " + v.getClass().getSimpleName());
  }

  public static String stringValue(Map<String, Object> config, String key, String defaultValue) {
    if (config == null) {
      return defaultValue;
    }
    Object v = config.get(key);
    if (v == null) {
      return defaultValue;
    }
    if (!(v instanceof String s)) {
      throw new IllegalArgumentException(
          "Config key '" + key + "' must be String, got " + v.getClass().getSimpleName());
    }
    return s;
  }

  public static int intValue(Map<String, Object> config, String key, int defaultValue) {
    if (config == null) {
      return defaultValue;
    }
    Object v = config.get(key);
    if (v == null) {
      return defaultValue;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    throw new IllegalArgumentException(
        "Config key '" + key + "' must be Number, got " + v.getClass().getSimpleName());
  }

  public static boolean boolValue(Map<String, Object> config, String key, boolean defaultValue) {
    if (config == null) {
      return defaultValue;
    }
    Object v = config.get(key);
    if (v == null) {
      return defaultValue;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    throw new IllegalArgumentException(
        "Config key '" + key + "' must be Boolean, got " + v.getClass().getSimpleName());
  }
}
