package io.veriguard.rest.monitoring;

import io.veriguard.database.model.coverage.CoverageBaseline;
import io.veriguard.database.model.monitoring.MonitoringJob;
import io.veriguard.database.model.monitoring.MonitoringRunHistory;
import io.veriguard.database.model.monitoring.MonitoringRunStatus;
import io.veriguard.database.repository.CoverageBaselineRepository;
import io.veriguard.database.repository.MonitoringJobRepository;
import io.veriguard.database.repository.MonitoringRunHistoryRepository;
import io.veriguard.monitoring.CronValidator;
import io.veriguard.monitoring.MonitoringTriggerService;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringHistoryPageOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringJobOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringJobPageOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringRunHistoryOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringTrendBucket;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringTrendOutput;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringTriggerOutput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监控子模块业务服务 —— PR C4 招标 §3.2.
 *
 * <p>聚合 monitoring_job CRUD + history 列表 + 趋势聚合.
 */
@Service
public class MonitoringService {

  /** 聚合视图最多拉的 history 行数（防爆炸）—— 调用方需通过 from/to 限制范围. */
  public static final int MAX_TREND_FETCH = 5_000;

  private static final DateTimeFormatter HOUR_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00:00'Z'");

  private final MonitoringJobRepository jobRepository;
  private final MonitoringRunHistoryRepository historyRepository;
  private final CoverageBaselineRepository baselineRepository;
  private final MonitoringTriggerService triggerService;

  public MonitoringService(
      MonitoringJobRepository jobRepository,
      MonitoringRunHistoryRepository historyRepository,
      CoverageBaselineRepository baselineRepository,
      MonitoringTriggerService triggerService) {
    this.jobRepository = jobRepository;
    this.historyRepository = historyRepository;
    this.baselineRepository = baselineRepository;
    this.triggerService = triggerService;
  }

  // ============================================================
  // Job CRUD
  // ============================================================

  @Transactional
  public MonitoringJobOutput createJob(JobRequest req) {
    validateJob(req);
    MonitoringJob job = new MonitoringJob();
    job.setName(req.name());
    job.setBaselineId(req.baselineId());
    job.setCronExpression(req.cronExpression());
    if (req.enabled() != null) {
      job.setEnabled(req.enabled());
    }
    job.setDescription(req.description());
    return toJobOutput(jobRepository.save(job));
  }

  @Transactional(readOnly = true)
  public MonitoringJobPageOutput listJobs(Optional<Boolean> enabled, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<MonitoringJob> p =
        enabled
            .map(e -> jobRepository.findAllByEnabled(e, pageable))
            .orElseGet(() -> jobRepository.findAll(pageable));
    return new MonitoringJobPageOutput(
        p.getContent().stream().map(this::toJobOutput).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public MonitoringJobOutput getJob(String id) {
    return toJobOutput(loadJob(id));
  }

  @Transactional
  public MonitoringJobOutput updateJob(String id, JobRequest req) {
    validateJob(req);
    MonitoringJob job = loadJob(id);
    job.setName(req.name());
    job.setBaselineId(req.baselineId());
    job.setCronExpression(req.cronExpression());
    if (req.enabled() != null) {
      job.setEnabled(req.enabled());
    }
    job.setDescription(req.description());
    return toJobOutput(jobRepository.save(job));
  }

  @Transactional
  public void deleteJob(String id) {
    MonitoringJob job = loadJob(id);
    jobRepository.delete(job);
  }

  @Transactional
  public MonitoringJobOutput setEnabled(String id, boolean enabled) {
    MonitoringJob job = loadJob(id);
    job.setEnabled(enabled);
    return toJobOutput(jobRepository.save(job));
  }

  // ============================================================
  // Trigger
  // ============================================================

  @Transactional
  public MonitoringTriggerOutput triggerNow(String id) {
    MonitoringJob job = loadJob(id);
    MonitoringRunHistory history = triggerService.trigger(job);
    return new MonitoringTriggerOutput(
        history.getId(), history.getStatus().name(), history.getCoverageRunId());
  }

  // ============================================================
  // History
  // ============================================================

  @Transactional(readOnly = true)
  public MonitoringHistoryPageOutput listHistory(
      String jobId,
      Optional<Instant> from,
      Optional<Instant> to,
      Optional<MonitoringRunStatus> status,
      int page,
      int size) {
    loadJob(jobId);
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "scheduledAt"));
    Page<MonitoringRunHistory> p =
        historyRepository.findFilteredByJobId(
            jobId, from.orElse(null), to.orElse(null), status.orElse(null), pageable);
    return new MonitoringHistoryPageOutput(
        p.getContent().stream().map(MonitoringService::toHistoryOutput).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }

  // ============================================================
  // Trend aggregation
  // ============================================================

  @Transactional(readOnly = true)
  public MonitoringTrendOutput trend(String jobId, String aggregation, Instant from, Instant to) {
    loadJob(jobId);
    if (from == null || to == null) {
      throw new IllegalArgumentException("trend requires both from and to");
    }
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("trend requires from < to");
    }
    Aggregation agg = parseAggregation(aggregation);
    List<MonitoringRunHistory> rows =
        historyRepository.findByJobIdAndScheduledAtBetween(jobId, from, to);
    if (rows.size() > MAX_TREND_FETCH) {
      throw new IllegalArgumentException(
          "trend window too wide: " + rows.size() + " rows > MAX_TREND_FETCH=" + MAX_TREND_FETCH);
    }
    return new MonitoringTrendOutput(jobId, agg.wire(), aggregateRows(rows, agg));
  }

  static List<MonitoringTrendBucket> aggregateRows(
      List<MonitoringRunHistory> rows, Aggregation agg) {
    Map<String, Accumulator> byBucket = new LinkedHashMap<>();
    for (MonitoringRunHistory h : rows) {
      // 仅 completed 行计入趋势分母（triggered 还未回填，failed 没有可信计数）
      if (h.getStatus() != MonitoringRunStatus.completed) {
        continue;
      }
      String key = bucketKey(h.getScheduledAt(), agg);
      byBucket.computeIfAbsent(key, k -> new Accumulator()).add(h);
    }
    return byBucket.entrySet().stream()
        .map(e -> e.getValue().toBucket(e.getKey()))
        .toList();
  }

  static String bucketKey(Instant t, Aggregation agg) {
    ZonedDateTime z = t.atZone(ZoneOffset.UTC);
    return switch (agg) {
      case hour -> z.format(HOUR_FMT);
      case day -> z.toLocalDate().toString();
    };
  }

  // ============================================================
  // Helpers
  // ============================================================

  private MonitoringJob loadJob(String id) {
    return jobRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Unknown monitoring_job_id: " + id));
  }

  private void validateJob(JobRequest req) {
    if (req.name() == null || req.name().isBlank()) {
      throw new IllegalArgumentException("monitoring_job_name must not be blank");
    }
    if (req.baselineId() == null || req.baselineId().isBlank()) {
      throw new IllegalArgumentException("monitoring_job_baseline_id must not be blank");
    }
    Optional<CoverageBaseline> b = baselineRepository.findById(req.baselineId());
    if (b.isEmpty()) {
      throw new IllegalArgumentException(
          "Unknown coverage_baseline_id: " + req.baselineId());
    }
    if (req.cronExpression() == null || req.cronExpression().isBlank()) {
      throw new IllegalArgumentException("monitoring_job_cron_expression must not be blank");
    }
    // 抛 InvalidCronException —— REST 层映射 400
    CronValidator.validate(req.cronExpression());
  }

  private MonitoringJobOutput toJobOutput(MonitoringJob j) {
    Instant nextFire = null;
    try {
      nextFire = CronValidator.nextFireTime(j.getCronExpression());
    } catch (RuntimeException e) {
      // detail 输出不阻断；nextFire 保持 null
      nextFire = null;
    }
    return new MonitoringJobOutput(
        j.getId(),
        j.getName(),
        j.getBaselineId(),
        j.getCronExpression(),
        j.isEnabled(),
        j.getDescription(),
        j.getLastTriggeredAt(),
        nextFire,
        j.getCreatedAt(),
        j.getUpdatedAt());
  }

  static MonitoringRunHistoryOutput toHistoryOutput(MonitoringRunHistory h) {
    BigDecimal hitRate = computeHitRate(h);
    return new MonitoringRunHistoryOutput(
        h.getId(),
        h.getJobId(),
        h.getCoverageRunId(),
        h.getScheduledAt(),
        h.getFinishedAt(),
        h.getStatus().name(),
        h.getHitCount(),
        h.getMissCount(),
        h.getTimeoutCount(),
        h.getOutOfScopeCount(),
        h.getTotalCount(),
        hitRate,
        h.getErrorMessage(),
        h.getCreatedAt());
  }

  static BigDecimal computeHitRate(MonitoringRunHistory h) {
    if (h.getTotalCount() == null || h.getTotalCount() == 0 || h.getHitCount() == null) {
      return null;
    }
    return BigDecimal.valueOf(h.getHitCount())
        .divide(BigDecimal.valueOf(h.getTotalCount()), 4, RoundingMode.HALF_UP);
  }

  private static Aggregation parseAggregation(String raw) {
    if (raw == null || raw.isBlank()) {
      return Aggregation.day;
    }
    try {
      return Aggregation.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown aggregation: " + raw + " (expected: hour / day)");
    }
  }

  // ============================================================
  // Records / inner types
  // ============================================================

  public record JobRequest(
      String name,
      String baselineId,
      String cronExpression,
      Boolean enabled,
      String description) {}

  public enum Aggregation {
    hour,
    day;

    String wire() {
      return name();
    }
  }

  /** 单个聚合桶累加器. */
  static final class Accumulator {
    long count;
    long hitSum;
    long missSum;
    long timeoutSum;
    long outOfScopeSum;
    long totalSum;

    void add(MonitoringRunHistory h) {
      count++;
      hitSum += zero(h.getHitCount());
      missSum += zero(h.getMissCount());
      timeoutSum += zero(h.getTimeoutCount());
      outOfScopeSum += zero(h.getOutOfScopeCount());
      totalSum += zero(h.getTotalCount());
    }

    MonitoringTrendBucket toBucket(String bucket) {
      BigDecimal avg;
      if (totalSum == 0) {
        avg = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
      } else {
        avg =
            BigDecimal.valueOf(hitSum)
                .divide(BigDecimal.valueOf(totalSum), 4, RoundingMode.HALF_UP);
      }
      return new MonitoringTrendBucket(
          bucket, avg, hitSum, missSum, timeoutSum, outOfScopeSum, totalSum, count);
    }

    static long zero(Integer i) {
      return i == null ? 0L : i.longValue();
    }
  }

  /** 计算 LocalDate（UTC）—— 测试用. */
  static LocalDate toUtcDay(Instant t) {
    return t.atZone(ZoneOffset.UTC).toLocalDate();
  }
}
