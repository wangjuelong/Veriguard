package io.veriguard.rest.stability;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.StabilityTrendSnapshot;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 稳定性快照对外输出 DTO（PR C5 / 招标 §3.3 + §4.2）.
 *
 * <p>不直接序列化 JPA 实体 —— 避免 LAZY 关联（attackChainRun / attackChainNode）在控制器线程触发 N+1 / LazyInitializationException.
 */
public record StabilityTrendOutput(
    @JsonProperty("snapshot_id") String id,
    @JsonProperty("snapshot_run_id") String runId,
    @JsonProperty("snapshot_node_id") String nodeId,
    @JsonProperty("snapshot_device_id") String deviceId,
    @JsonProperty("snapshot_baseline_id") String baselineId,
    @JsonProperty("snapshot_hit_count") int hitCount,
    @JsonProperty("snapshot_total_count") int totalCount,
    @JsonProperty("snapshot_hit_rate") BigDecimal hitRate,
    @JsonProperty("snapshot_captured_at") Instant capturedAt) {

  public static StabilityTrendOutput from(StabilityTrendSnapshot snapshot) {
    return new StabilityTrendOutput(
        snapshot.getId(),
        snapshot.getAttackChainRun() == null ? null : snapshot.getAttackChainRun().getId(),
        snapshot.getAttackChainNode() == null ? null : snapshot.getAttackChainNode().getId(),
        snapshot.getDeviceId(),
        snapshot.getBaselineId(),
        snapshot.getHitCount(),
        snapshot.getTotalCount(),
        snapshot.getHitRate(),
        snapshot.getCapturedAt());
  }
}
