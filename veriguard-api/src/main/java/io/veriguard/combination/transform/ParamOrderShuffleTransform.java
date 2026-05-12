package io.veriguard.combination.transform;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 参数顺序扰乱变换. category=param_order.
 *
 * <p>将形如 {@code k1=v1&k2=v2&k3=v3} 的 query string / form body 按指定策略重排.
 *
 * <p>支持配置：
 * <ul>
 *   <li>{@code strategy}（string, 必填）—— {@code reverse}（倒序）/
 *       {@code sort_asc}（按 key 字母升序）/ {@code sort_desc}（降序）</li>
 *   <li>{@code separator}（string, 默认 {@code &}）—— 参数分隔符</li>
 * </ul>
 */
@Component
public class ParamOrderShuffleTransform implements PayloadTransform {

  public static final String TYPE = "param_order_shuffle";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String apply(String basePayload, Map<String, Object> config) {
    if (basePayload == null) {
      throw new IllegalArgumentException("basePayload must not be null");
    }
    String strategy = TransformConfigs.requiredString(config, "strategy");
    String separator = TransformConfigs.stringValue(config, "separator", "&");

    String[] parts = basePayload.split(java.util.regex.Pattern.quote(separator));

    Comparator<String> comparator =
        switch (strategy) {
          case "reverse" -> null;
          case "sort_asc" -> Comparator.comparing(ParamOrderShuffleTransform::keyOf);
          case "sort_desc" ->
              Comparator.comparing(ParamOrderShuffleTransform::keyOf, Comparator.reverseOrder());
          default ->
              throw new IllegalArgumentException(
                  "Unknown param_order strategy: '"
                      + strategy
                      + "' (expected reverse / sort_asc / sort_desc)");
        };

    if (comparator == null) {
      // reverse
      String[] reversed = new String[parts.length];
      for (int i = 0; i < parts.length; i++) {
        reversed[parts.length - 1 - i] = parts[i];
      }
      return String.join(separator, reversed);
    }

    return Arrays.stream(parts).sorted(comparator).collect(Collectors.joining(separator));
  }

  private static String keyOf(String part) {
    int eq = part.indexOf('=');
    return eq < 0 ? part : part.substring(0, eq);
  }
}
