package io.veriguard.combination.transform;

import java.util.Map;

/**
 * 攻击 payload 变换器 —— IPv6 安全验证系统 §3.6 ★2 攻击组合.
 *
 * <p>每个 {@link io.veriguard.database.model.combination.BypassDimension} 通过
 * {@code transformType} 字符串路由到一个 {@link PayloadTransform} 实现.
 * 实现按 {@code transformConfig} 提供的参数（如 chunk_size / strategy）对 base payload
 * 应用确定性变换并返回新字符串.
 *
 * <p>实现要求：
 * <ul>
 *   <li>纯函数：相同入参（base + config）必须返回相同输出</li>
 *   <li>不可变：不修改入参 config Map / base 字符串</li>
 *   <li>Fail-fast：必填配置缺失抛 {@link IllegalArgumentException}，
 *       不写 fallback 默认值（参见项目 CLAUDE.md "No fallback code" 约定）</li>
 * </ul>
 */
public interface PayloadTransform {

  /**
   * @return 变换器唯一标识，与 {@code bypass_dimension_transform_type} 列字符串对齐
   */
  String type();

  /**
   * 对 base payload 应用本变换.
   *
   * @param basePayload 原始 payload（不为 null）
   * @param config 变换参数（来源于 {@code bypass_dimension_transform_config}，不为 null）
   * @return 变换后的 payload
   */
  String apply(String basePayload, Map<String, Object> config);
}
