package io.veriguard.combination.transform;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Base64 编码变换. category=encoding.
 *
 * <p>支持配置：
 * <ul>
 *   <li>{@code url_safe}（boolean, 默认 false）—— 是否使用 URL-safe 字母表（- 和 _ 代替 + 和 /）</li>
 *   <li>{@code padding}（boolean, 默认 true）—— 是否保留 = 填充</li>
 * </ul>
 */
@Component
public class Base64EncodeTransform implements PayloadTransform {

  public static final String TYPE = "base64_encode";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String apply(String basePayload, Map<String, Object> config) {
    if (basePayload == null) {
      throw new IllegalArgumentException("basePayload must not be null");
    }
    boolean urlSafe = TransformConfigs.boolValue(config, "url_safe", false);
    boolean padding = TransformConfigs.boolValue(config, "padding", true);

    Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
    if (!padding) {
      encoder = encoder.withoutPadding();
    }
    return encoder.encodeToString(basePayload.getBytes(StandardCharsets.UTF_8));
  }
}
