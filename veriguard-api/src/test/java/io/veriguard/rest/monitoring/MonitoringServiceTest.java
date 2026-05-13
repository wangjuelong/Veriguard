package io.veriguard.rest.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.database.model.monitoring.MonitoringRunHistory;
import io.veriguard.database.model.monitoring.MonitoringRunStatus;
import io.veriguard.rest.monitoring.MonitoringDtos.MonitoringTrendBucket;
import io.veriguard.rest.monitoring.MonitoringService.Aggregation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonitoringServiceTest {

  @Test
  void aggregate_by_day_groups_completed_rows() {
    Instant day1 = Instant.parse("2026-05-10T08:00:00Z");
    Instant day1Late = Instant.parse("2026-05-10T20:00:00Z");
    Instant day2 = Instant.parse("2026-05-11T08:00:00Z");

    List<MonitoringRunHistory> rows =
        List.of(
            completed(day1, 8, 2, 0, 0, 10),
            completed(day1Late, 6, 4, 0, 0, 10),
            completed(day2, 9, 1, 0, 0, 10));

    List<MonitoringTrendBucket> buckets = MonitoringService.aggregateRows(rows, Aggregation.day);

    assertThat(buckets).hasSize(2);
    MonitoringTrendBucket d1 = bucketFor(buckets, "2026-05-10");
    assertThat(d1.runCount()).isEqualTo(2);
    assertThat(d1.hitSum()).isEqualTo(14);
    assertThat(d1.totalSum()).isEqualTo(20);
    // 14/20 = 0.7000
    assertThat(d1.avgHitRate()).isEqualByComparingTo(new BigDecimal("0.7000"));

    MonitoringTrendBucket d2 = bucketFor(buckets, "2026-05-11");
    assertThat(d2.runCount()).isEqualTo(1);
    assertThat(d2.hitSum()).isEqualTo(9);
    assertThat(d2.avgHitRate()).isEqualByComparingTo(new BigDecimal("0.9000"));
  }

  @Test
  void aggregate_by_hour_uses_iso_hour_key() {
    Instant t1 = Instant.parse("2026-05-10T08:15:00Z");
    Instant t2 = Instant.parse("2026-05-10T08:50:00Z");
    Instant t3 = Instant.parse("2026-05-10T09:05:00Z");

    List<MonitoringRunHistory> rows =
        List.of(
            completed(t1, 5, 5, 0, 0, 10),
            completed(t2, 7, 3, 0, 0, 10),
            completed(t3, 1, 9, 0, 0, 10));

    List<MonitoringTrendBucket> buckets = MonitoringService.aggregateRows(rows, Aggregation.hour);

    assertThat(buckets).hasSize(2);
    MonitoringTrendBucket h8 = bucketFor(buckets, "2026-05-10T08:00:00Z");
    assertThat(h8.runCount()).isEqualTo(2);
    assertThat(h8.hitSum()).isEqualTo(12);
    assertThat(h8.totalSum()).isEqualTo(20);

    MonitoringTrendBucket h9 = bucketFor(buckets, "2026-05-10T09:00:00Z");
    assertThat(h9.runCount()).isEqualTo(1);
  }

  @Test
  void aggregate_excludes_non_completed_rows() {
    Instant t = Instant.parse("2026-05-10T08:00:00Z");
    MonitoringRunHistory triggered = newRow(t, MonitoringRunStatus.triggered, 0, 0, 0, 0, 0);
    MonitoringRunHistory failed = newRow(t, MonitoringRunStatus.failed, 0, 0, 0, 0, 0);
    MonitoringRunHistory completed = completed(t, 5, 5, 0, 0, 10);

    List<MonitoringTrendBucket> buckets =
        MonitoringService.aggregateRows(
            List.of(triggered, failed, completed), Aggregation.day);

    assertThat(buckets).hasSize(1);
    assertThat(buckets.get(0).runCount()).isEqualTo(1);
    assertThat(buckets.get(0).hitSum()).isEqualTo(5);
  }

  @Test
  void hit_rate_computation_handles_null_total() {
    MonitoringRunHistory h = new MonitoringRunHistory();
    h.setHitCount(5);
    h.setTotalCount(null);
    assertThat(MonitoringService.computeHitRate(h)).isNull();

    h.setTotalCount(0);
    assertThat(MonitoringService.computeHitRate(h)).isNull();

    h.setTotalCount(10);
    assertThat(MonitoringService.computeHitRate(h))
        .isEqualByComparingTo(new BigDecimal("0.5000"));
  }

  @Test
  void bucket_key_is_deterministic_across_aggregations() {
    Instant t = Instant.parse("2026-05-10T13:42:30Z");
    assertThat(MonitoringService.bucketKey(t, Aggregation.day)).isEqualTo("2026-05-10");
    assertThat(MonitoringService.bucketKey(t, Aggregation.hour))
        .isEqualTo("2026-05-10T13:00:00Z");
  }

  // ------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------

  private static MonitoringRunHistory completed(
      Instant scheduledAt, int hit, int miss, int timeout, int oos, int total) {
    return newRow(scheduledAt, MonitoringRunStatus.completed, hit, miss, timeout, oos, total);
  }

  private static MonitoringRunHistory newRow(
      Instant scheduledAt,
      MonitoringRunStatus status,
      int hit,
      int miss,
      int timeout,
      int oos,
      int total) {
    MonitoringRunHistory h = new MonitoringRunHistory();
    h.setId(UUID.randomUUID().toString());
    h.setJobId("job-x");
    h.setScheduledAt(scheduledAt);
    h.setStatus(status);
    h.setHitCount(hit);
    h.setMissCount(miss);
    h.setTimeoutCount(timeout);
    h.setOutOfScopeCount(oos);
    h.setTotalCount(total);
    return h;
  }

  private static MonitoringTrendBucket bucketFor(List<MonitoringTrendBucket> buckets, String key) {
    return buckets.stream()
        .filter(b -> b.bucket().equals(key))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing bucket: " + key));
  }
}
