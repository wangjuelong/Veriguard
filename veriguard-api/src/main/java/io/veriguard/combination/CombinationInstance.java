package io.veriguard.combination;

/**
 * 单条组合实例 —— 任务执行时由 {@link AttackCombinationGenerator} 流式产出.
 *
 * <p>不持久化为表行；transformedPayload + payloadSample 在派发时计算好直接给 executor 使用.
 *
 * @param runId 所属任务 id
 * @param combinationId 虚拟组合 ID = baseAttackType + ":" + bypassDimensionId
 * @param baseAttackType §3.5 attack_category 枚举字符串（如 sql_injection / xss / xxe）
 * @param bypassDimensionId V11 bypass_dimensions.id
 * @param assetId 目标资产 id
 * @param transformedPayload 经 PayloadTransform 处理后的完整 payload（送给 executor）
 * @param payloadSample 截断后的 payload 样本（≤ 1024 字符，存入 result.payload_sample）
 */
public record CombinationInstance(
    String runId,
    String combinationId,
    String baseAttackType,
    String bypassDimensionId,
    String assetId,
    String transformedPayload,
    String payloadSample) {

  /** payload_sample 最大长度. */
  public static final int PAYLOAD_SAMPLE_MAX_LENGTH = 1024;

  /** 截断到 {@link #PAYLOAD_SAMPLE_MAX_LENGTH} 字符. */
  public static String truncatePayload(String payload) {
    if (payload == null) {
      return null;
    }
    if (payload.length() <= PAYLOAD_SAMPLE_MAX_LENGTH) {
      return payload;
    }
    return payload.substring(0, PAYLOAD_SAMPLE_MAX_LENGTH);
  }

  /** 组合 ID 拼接规则（确保与 result 表 combination_id 列字符串一致）. */
  public static String buildCombinationId(String baseAttackType, String bypassDimensionId) {
    if (baseAttackType == null || bypassDimensionId == null) {
      throw new IllegalArgumentException(
          "baseAttackType and bypassDimensionId must not be null");
    }
    return baseAttackType + ":" + bypassDimensionId;
  }
}
