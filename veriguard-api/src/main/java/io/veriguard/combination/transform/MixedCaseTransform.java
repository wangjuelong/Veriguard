package io.veriguard.combination.transform;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 大小写混淆变换. category=casing.
 *
 * <p>支持配置：
 * <ul>
 *   <li>{@code strategy}（string, 必填）—— {@code alternate}（aBcDeF）/
 *       {@code upper}（全大写）/ {@code lower}（全小写）/ {@code invert}（翻转每个字母）</li>
 * </ul>
 */
@Component
public class MixedCaseTransform implements PayloadTransform {

  public static final String TYPE = "mixed_case";

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

    return switch (strategy) {
      case "alternate" -> alternate(basePayload);
      case "upper" -> basePayload.toUpperCase();
      case "lower" -> basePayload.toLowerCase();
      case "invert" -> invert(basePayload);
      default ->
          throw new IllegalArgumentException(
              "Unknown mixed_case strategy: '"
                  + strategy
                  + "' (expected alternate / upper / lower / invert)");
    };
  }

  private static String alternate(String input) {
    StringBuilder sb = new StringBuilder(input.length());
    boolean upper = true;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (Character.isLetter(c)) {
        sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
        upper = !upper;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String invert(String input) {
    StringBuilder sb = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (Character.isUpperCase(c)) {
        sb.append(Character.toLowerCase(c));
      } else if (Character.isLowerCase(c)) {
        sb.append(Character.toUpperCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
