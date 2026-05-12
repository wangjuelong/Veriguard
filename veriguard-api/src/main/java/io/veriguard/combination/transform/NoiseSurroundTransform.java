package io.veriguard.combination.transform;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 噪声前后缀变换. category=noise.
 *
 * <p>在 payload 前后包裹噪声字符串（空白 / tab / 控制字符 / 注释 / 随机 token）.
 *
 * <p>支持配置：
 * <ul>
 *   <li>{@code prefix}（string, 默认空串）—— 前缀</li>
 *   <li>{@code suffix}（string, 默认空串）—— 后缀</li>
 *   <li>{@code repeat}（int, 默认 1, 最小 1）—— 前后缀重复次数</li>
 * </ul>
 */
@Component
public class NoiseSurroundTransform implements PayloadTransform {

  public static final String TYPE = "noise_surround";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String apply(String basePayload, Map<String, Object> config) {
    if (basePayload == null) {
      throw new IllegalArgumentException("basePayload must not be null");
    }
    String prefix = TransformConfigs.stringValue(config, "prefix", "");
    String suffix = TransformConfigs.stringValue(config, "suffix", "");
    int repeat = TransformConfigs.intValue(config, "repeat", 1);
    if (repeat < 1) {
      throw new IllegalArgumentException("repeat must be >= 1, got " + repeat);
    }
    return prefix.repeat(repeat) + basePayload + suffix.repeat(repeat);
  }
}
