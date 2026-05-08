package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ParameterSet 默认验证目标的引用 (PRD §2.4 / spec §2.2.4).
 *
 * <p>不是表，是嵌入在 {@code validation_parameter_sets.default_targets} JSONB 列中的值对象。
 *
 * @param targetType 目标种类（asset / asset_group）
 * @param targetId 目标对象 ID（asset_id / asset_group_id）
 */
public record TargetRef(
    @JsonProperty("target_type") TargetType targetType,
    @JsonProperty("target_id") String targetId) {

  public TargetRef {
    if (targetType == null) {
      throw new IllegalArgumentException("target_type required");
    }
    if (targetId == null || targetId.isBlank()) {
      throw new IllegalArgumentException("target_id required");
    }
  }

  /** ParameterSet 默认目标种类。 */
  public enum TargetType {
    ASSET,
    ASSET_GROUP
  }
}
