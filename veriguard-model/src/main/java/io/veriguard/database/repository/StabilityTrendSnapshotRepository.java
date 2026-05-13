package io.veriguard.database.repository;

import io.veriguard.database.model.StabilityTrendSnapshot;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA 仓库 —— 稳定性趋势快照（PR C5）.
 *
 * <p>主要查询场景：
 *
 * <ul>
 *   <li>按 device_id 拉时间序列（绘趋势折线）
 *   <li>按时间范围分页拉（默认视图）
 *   <li>按 run_id 反查节点稳定性（运行详情面板）
 *   <li>按天聚合（"按天 / 按任务"视图切换）
 * </ul>
 */
@Repository
public interface StabilityTrendSnapshotRepository
    extends JpaRepository<StabilityTrendSnapshot, String> {

  @NotNull
  Optional<StabilityTrendSnapshot> findById(@NotNull String id);

  /** 按 device_id + 时间范围 + 排序 capturedAt asc 拉时间序列（趋势折线渲染）. */
  @Query(
      "select s from StabilityTrendSnapshot s "
          + "where s.deviceId = :deviceId "
          + "and s.capturedAt >= :from and s.capturedAt < :to "
          + "order by s.capturedAt asc")
  List<StabilityTrendSnapshot> findByDeviceIdAndCapturedAtBetween(
      @Param("deviceId") String deviceId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /** 按 run_id 反查（运行详情面板显示该 run 下所有节点的稳定性快照）. */
  @Query(
      "select s from StabilityTrendSnapshot s where s.attackChainRun.id = :runId "
          + "order by s.capturedAt asc")
  List<StabilityTrendSnapshot> findByAttackChainRunId(@Param("runId") String runId);

  /** 全量分页（默认列表视图）. */
  @Query(
      value = "select s from StabilityTrendSnapshot s",
      countQuery = "select count(s) from StabilityTrendSnapshot s")
  Page<StabilityTrendSnapshot> findAllPaged(Pageable pageable);

  /**
   * 按时间范围分页（默认 + 时间过滤组合）.
   *
   * <p>当 deviceId 为 null 时返回全部设备的趋势点；否则仅返回该设备.
   */
  @Query(
      value =
          "select s from StabilityTrendSnapshot s "
              + "where (:deviceId is null or s.deviceId = :deviceId) "
              + "and (:from is null or s.capturedAt >= :from) "
              + "and (:to is null or s.capturedAt < :to)",
      countQuery =
          "select count(s) from StabilityTrendSnapshot s "
              + "where (:deviceId is null or s.deviceId = :deviceId) "
              + "and (:from is null or s.capturedAt >= :from) "
              + "and (:to is null or s.capturedAt < :to)")
  Page<StabilityTrendSnapshot> findFiltered(
      @Param("deviceId") String deviceId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
