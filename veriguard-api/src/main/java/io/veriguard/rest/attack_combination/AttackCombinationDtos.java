package io.veriguard.rest.attack_combination;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTO 集合 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D1 REST 输出. */
public final class AttackCombinationDtos {

  private AttackCombinationDtos() {}

  public record BypassDimensionOutput(
      @JsonProperty("bypass_dimension_id") String id,
      @JsonProperty("bypass_dimension_name") String name,
      @JsonProperty("bypass_dimension_category") String category,
      @JsonProperty("bypass_dimension_description") String description,
      @JsonProperty("bypass_dimension_transform_type") String transformType,
      @JsonProperty("bypass_dimension_transform_config") Map<String, Object> transformConfig,
      @JsonProperty("bypass_dimension_created_at") Instant createdAt,
      @JsonProperty("bypass_dimension_updated_at") Instant updatedAt) {}

  public record BypassDimensionPageOutput(
      @JsonProperty("content") List<BypassDimensionOutput> content,
      @JsonProperty("page") int page,
      @JsonProperty("size") int size,
      @JsonProperty("total_elements") long totalElements,
      @JsonProperty("total_pages") int totalPages) {}

  public record CombinationSampleOutput(
      @JsonProperty("base_attack_type") String baseAttackType,
      @JsonProperty("bypass_dimension_id") String bypassDimensionId,
      @JsonProperty("bypass_dimension_name") String bypassDimensionName,
      @JsonProperty("preview_payload") String previewPayload) {}

  public record CombinationPreviewOutput(
      @JsonProperty("base_attack_types") List<String> baseAttackTypes,
      @JsonProperty("bypass_dimension_ids") List<String> bypassDimensionIds,
      @JsonProperty("total_combinations") long totalCombinations,
      @JsonProperty("sample_size") int sampleSize,
      @JsonProperty("samples") List<CombinationSampleOutput> samples,
      @JsonProperty("preview_base_payload") String previewBasePayload) {}
}
