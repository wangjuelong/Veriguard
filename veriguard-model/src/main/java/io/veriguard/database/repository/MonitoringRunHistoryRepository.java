package io.veriguard.database.repository;

import io.veriguard.database.model.monitoring.MonitoringRunHistory;
import io.veriguard.database.model.monitoring.MonitoringRunStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 监控历史仓储 —— PR C4.
 *
 * <p>主要查询场景：
 *
 * <ul>
 *   <li>按 job_id 拉历史时间序列（趋势 + 列表）
 *   <li>按 job_id + 时间窗 + status 过滤分页（明细页）
 *   <li>HistoryUpdater 扫 status=triggered 的旧行回填计数（按 status + scheduled_at 过滤）
 * </ul>
 */
@Repository
public interface MonitoringRunHistoryRepository extends JpaRepository<MonitoringRunHistory, String> {

  Page<MonitoringRunHistory> findAllByJobIdOrderByScheduledAtDesc(String jobId, Pageable pageable);

  /** HistoryUpdater 后台扫描入口：拉所有未完成的 history（按时间倒序，限定上界以防穿透）. */
  List<MonitoringRunHistory> findAllByStatusAndScheduledAtBefore(
      MonitoringRunStatus status, Instant before);

  /** 时间窗 + 可选状态过滤（趋势聚合所需的原始数据）. */
  @Query(
      "select h from MonitoringRunHistory h "
          + "where h.jobId = :jobId "
          + "and h.scheduledAt >= :from and h.scheduledAt < :to "
          + "order by h.scheduledAt asc")
  List<MonitoringRunHistory> findByJobIdAndScheduledAtBetween(
      @Param("jobId") String jobId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  // 时间窗 + status 过滤分页（明细）—— 拆分为多个 finder 避免 PG 「could not determine
  // data type of parameter」（Hibernate 6 在 :param is null 时不发送 type hint）。
  Page<MonitoringRunHistory> findAllByJobId(String jobId, Pageable pageable);

  Page<MonitoringRunHistory> findAllByJobIdAndStatus(
      String jobId, MonitoringRunStatus status, Pageable pageable);

  Page<MonitoringRunHistory> findAllByJobIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
      String jobId, Instant from, Instant to, Pageable pageable);

  Page<MonitoringRunHistory>
      findAllByJobIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
          String jobId,
          MonitoringRunStatus status,
          Instant from,
          Instant to,
          Pageable pageable);

  Page<MonitoringRunHistory> findAllByJobIdAndScheduledAtGreaterThanEqual(
      String jobId, Instant from, Pageable pageable);

  Page<MonitoringRunHistory> findAllByJobIdAndStatusAndScheduledAtGreaterThanEqual(
      String jobId, MonitoringRunStatus status, Instant from, Pageable pageable);

  Page<MonitoringRunHistory> findAllByJobIdAndScheduledAtLessThan(
      String jobId, Instant to, Pageable pageable);

  Page<MonitoringRunHistory> findAllByJobIdAndStatusAndScheduledAtLessThan(
      String jobId, MonitoringRunStatus status, Instant to, Pageable pageable);
}
