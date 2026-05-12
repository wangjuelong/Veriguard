package io.veriguard.rest.stability;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.database.model.StabilityTrendSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * StabilityApi.aggregateByDay 纯函数测试（PR C5 / 招标 §3.3 验收要点："按天聚合"视图）.
 *
 * <p>不依赖 Spring 上下文 —— 仅测算法层. Controller HTTP 层 + RBAC 由后续 IntegrationTest 覆盖（当 Flyway 修复后）.
 */
class StabilityApiAggregateTest {

  @Test
  @DisplayName("空输入 → 空列表")
  void empty_snapshots_yields_empty() {
    assertThat(StabilityApi.aggregateByDay(List.of())).isEmpty();
  }

  @Test
  @DisplayName("单日多个 snapshot → 一个聚合点，avg = sum(hit) / sum(total)")
  void single_day_multiple_snapshots() {
    Instant t1 = Instant.parse("2026-05-12T08:00:00Z");
    Instant t2 = Instant.parse("2026-05-12T14:30:00Z");
    StabilityTrendSnapshot s1 = snapshot(t1, 7, 10);
    StabilityTrendSnapshot s2 = snapshot(t2, 3, 10);

    List<StabilityDailyAggregate> result = StabilityApi.aggregateByDay(List.of(s1, s2));

    assertThat(result).hasSize(1);
    StabilityDailyAggregate agg = result.get(0);
    assertThat(agg.day()).isEqualTo(LocalDate.of(2026, 5, 12));
    assertThat(agg.snapshotCount()).isEqualTo(2);
    assertThat(agg.totalHitCount()).isEqualTo(10);
    assertThat(agg.totalCountSum()).isEqualTo(20);
    assertThat(agg.avgHitRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
  }

  @Test
  @DisplayName("跨日 snapshot → 按天分组，按 day asc 排序（TreeMap 默认序）")
  void cross_day_snapshots_sorted() {
    Instant day3 = Instant.parse("2026-05-14T10:00:00Z");
    Instant day1 = Instant.parse("2026-05-12T10:00:00Z");
    Instant day2 = Instant.parse("2026-05-13T10:00:00Z");

    List<StabilityDailyAggregate> result =
        StabilityApi.aggregateByDay(
            List.of(snapshot(day3, 5, 10), snapshot(day1, 9, 10), snapshot(day2, 0, 10)));

    assertThat(result).hasSize(3);
    assertThat(result.get(0).day()).isEqualTo(LocalDate.of(2026, 5, 12));
    assertThat(result.get(0).avgHitRate()).isEqualByComparingTo(new BigDecimal("0.9000"));
    assertThat(result.get(1).day()).isEqualTo(LocalDate.of(2026, 5, 13));
    assertThat(result.get(1).avgHitRate()).isEqualByComparingTo(new BigDecimal("0.0000"));
    assertThat(result.get(2).day()).isEqualTo(LocalDate.of(2026, 5, 14));
    assertThat(result.get(2).avgHitRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
  }

  @Test
  @DisplayName("totalCountSum=0 防御性 → avgHitRate = 0.0000")
  void zero_total_safe() {
    // 这种情况理论上不会出现（DB CHECK total > 0），但代码层有防御
    StabilityTrendSnapshot zero = new StabilityTrendSnapshot();
    zero.setCapturedAt(Instant.parse("2026-05-12T00:00:00Z"));
    zero.setHitCount(0);
    zero.setTotalCount(0);
    zero.setHitRate(BigDecimal.ZERO);

    List<StabilityDailyAggregate> result = StabilityApi.aggregateByDay(List.of(zero));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).avgHitRate())
        .isEqualByComparingTo(new BigDecimal("0.0000"));
  }

  // ----- helpers -----

  private static StabilityTrendSnapshot snapshot(Instant capturedAt, int hit, int total) {
    StabilityTrendSnapshot s = new StabilityTrendSnapshot();
    s.setCapturedAt(capturedAt);
    s.setHitCount(hit);
    s.setTotalCount(total);
    s.setHitRate(
        total == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(hit)
                .divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
    return s;
  }
}
