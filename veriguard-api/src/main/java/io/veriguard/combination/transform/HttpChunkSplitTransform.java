package io.veriguard.combination.transform;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HTTP chunked 分块变换. category=chunking.
 *
 * <p>将 payload 拆分为若干 chunk，按 HTTP/1.1 transfer-encoding: chunked 的 hex-length 头格式
 * 输出（每段：{@code <hex_len>\r\n<bytes>\r\n}），结尾加 {@code 0\r\n\r\n}.
 *
 * <p>支持配置：
 * <ul>
 *   <li>{@code chunk_size}（int, 必填）—— 每段字节数，最小 1</li>
 * </ul>
 */
@Component
public class HttpChunkSplitTransform implements PayloadTransform {

  public static final String TYPE = "http_chunk_split";

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public String apply(String basePayload, Map<String, Object> config) {
    if (basePayload == null) {
      throw new IllegalArgumentException("basePayload must not be null");
    }
    int chunkSize = TransformConfigs.requiredInt(config, "chunk_size");
    if (chunkSize < 1) {
      throw new IllegalArgumentException("chunk_size must be >= 1, got " + chunkSize);
    }

    StringBuilder out = new StringBuilder();
    int len = basePayload.length();
    for (int i = 0; i < len; i += chunkSize) {
      int end = Math.min(i + chunkSize, len);
      String slice = basePayload.substring(i, end);
      out.append(Integer.toHexString(slice.length())).append("\r\n").append(slice).append("\r\n");
    }
    out.append("0\r\n\r\n");
    return out.toString();
  }
}
