package io.veriguard.combination.transform;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * PayloadTransform 注册中心.
 *
 * <p>启动期自动收集 Spring 容器内所有 {@link PayloadTransform} bean，按 {@code type()} 字符串建索引.
 * 重复 type 直接抛异常，禁止覆盖（fail-fast）.
 */
@Component
public class PayloadTransformRegistry {

  private final Map<String, PayloadTransform> transformsByType;

  public PayloadTransformRegistry(List<PayloadTransform> transforms) {
    this.transformsByType =
        transforms.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    PayloadTransform::type,
                    Function.identity(),
                    (a, b) -> {
                      throw new IllegalStateException(
                          "Duplicate PayloadTransform type: "
                              + a.type()
                              + " ("
                              + a.getClass().getName()
                              + " vs "
                              + b.getClass().getName()
                              + ")");
                    }));
  }

  /**
   * 按 type 字符串查找变换器.
   *
   * @throws IllegalArgumentException 当 type 未注册（fail-fast，不静默降级到 identity）
   */
  public PayloadTransform get(String type) {
    PayloadTransform transform = transformsByType.get(type);
    if (transform == null) {
      throw new IllegalArgumentException(
          "Unknown payload transform type: '" + type + "'. Known: " + transformsByType.keySet());
    }
    return transform;
  }

  /**
   * 应用变换的便捷方法.
   *
   * @param type 变换器 type 字符串（来自 BypassDimension.transformType）
   * @param basePayload 原始 payload
   * @param config 变换配置（来自 BypassDimension.transformConfig）
   * @return 变换后的 payload
   */
  public String transform(String type, String basePayload, Map<String, Object> config) {
    return get(type).apply(basePayload, config);
  }

  public Set<String> knownTypes() {
    return transformsByType.keySet();
  }
}
