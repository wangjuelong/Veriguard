package io.veriguard.rest.stability;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 按天聚合的稳定性输出（PR C5 / 招标 §3.3 验收要点："按天聚合"视图切换）.
 *
 * <p>{@code avg_hit_rate} 为该日所有 snapshot 的 hit_rate 算术平均；{@code snapshot_count} 为该日 snapshot 数.
 */
public record StabilityDailyAggregate(
    @JsonProperty("day") LocalDate day,
    @JsonProperty("avg_hit_rate") BigDecimal avgHitRate,
    @JsonProperty("snapshot_count") long snapshotCount,
    @JsonProperty("total_hit_count") long totalHitCount,
    @JsonProperty("total_count_sum") long totalCountSum) {}
