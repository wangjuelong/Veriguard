package io.veriguard.combination.transform;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * URL percent-encoding 变换. category=encoding.
 *
 * <p>支持配置：
 * <ul>
 *   <li>{@code double_encode}（boolean, 默认 false）—— 是否再编码一次（绕过单层 decode 的 WAF）</li>
 * </ul>
 */
@Component
public class UrlEncodeTransform implements PayloadTransform {

  public static final String TYPE = "url_encode";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String apply(String basePayload, Map<String, Object> config) {
    if (basePayload == null) {
      throw new IllegalArgumentException("basePayload must not be null");
    }
    boolean doubleEncode = TransformConfigs.boolValue(config, "double_encode", false);

    String encoded = URLEncoder.encode(basePayload, StandardCharsets.UTF_8);
    if (doubleEncode) {
      encoded = URLEncoder.encode(encoded, StandardCharsets.UTF_8);
    }
    return encoded;
  }
}
