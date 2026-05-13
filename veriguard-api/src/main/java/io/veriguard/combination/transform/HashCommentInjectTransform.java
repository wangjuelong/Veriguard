package io.veriguard.combination.transform;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * SQL / HTML 注释注入变换. category=comment.
 *
 * <p>在 payload 的空白处插入注释 token，模拟 SQL injection 中的
 * {@code SELECT/**\/FROM} 或 XSS 中的 {@code <!-- ... -->} 绕过策略.
 *
 * <p>支持配置：
 * <ul>
 *   <li>{@code style}（string, 必填）—— {@code sql_block}（/&#42;&#42;/）/
 *       {@code sql_line}（# 或 -- 行末注释）/ {@code html_comment}（&lt;!-- --&gt;）</li>
 *   <li>{@code position}（string, 默认 {@code between_tokens}）——
 *       {@code between_tokens}（在每个空格处插入）/ {@code prefix}（仅最前）/
 *       {@code suffix}（仅最后）</li>
 * </ul>
 */
@Component
public class HashCommentInjectTransform implements PayloadTransform {

  public static final String TYPE = "comment_inject";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String apply(String basePayload, Map<String, Object> config) {
    if (basePayload == null) {
      throw new IllegalArgumentException("basePayload must not be null");
    }
    String style = TransformConfigs.requiredString(config, "style");
    String position = TransformConfigs.stringValue(config, "position", "between_tokens");

    String token =
        switch (style) {
          case "sql_block" -> "/**/";
          case "sql_line" -> "-- ";
          case "html_comment" -> "<!---->";
          default ->
              throw new IllegalArgumentException(
                  "Unknown comment style: '"
                      + style
                      + "' (expected sql_block / sql_line / html_comment)");
        };

    return switch (position) {
      case "prefix" -> token + basePayload;
      case "suffix" -> basePayload + token;
      case "between_tokens" -> basePayload.replace(" ", token);
      default ->
          throw new IllegalArgumentException(
              "Unknown comment position: '"
                  + position
                  + "' (expected between_tokens / prefix / suffix)");
    };
  }
}
