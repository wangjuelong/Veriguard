package io.veriguard.combination.transform;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Unicode 全角变换. category=unicode.
 *
 * <p>将 ASCII 范围（0x21-0x7E）的字符映射到 Unicode 全角区（FF01-FF5E）.
 * 空格映射到 U+3000（全角空格）.
 *
 * <p>本变换不接受配置参数；保留 config 参数以满足接口契约.
 */
@Component
public class UnicodeFullWidthTransform implements PayloadTransform {

  public static final String TYPE = "unicode_fullwidth";

  private static final int ASCII_PRINTABLE_START = 0x21;
  private static final int ASCII_PRINTABLE_END = 0x7E;
  private static final int FULLWIDTH_OFFSET = 0xFEE0; // FF01 - 0021

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String apply(String basePayload, Map<String, Object> config) {
    if (basePayload == null) {
      throw new IllegalArgumentException("basePayload must not be null");
    }
    StringBuilder sb = new StringBuilder(basePayload.length());
    for (int i = 0; i < basePayload.length(); i++) {
      char c = basePayload.charAt(i);
      if (c == ' ') {
        sb.append('　');
      } else if (c >= ASCII_PRINTABLE_START && c <= ASCII_PRINTABLE_END) {
        sb.append((char) (c + FULLWIDTH_OFFSET));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
