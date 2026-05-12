package io.veriguard.rest.stability;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.model.StabilityTrendSnapshot;
import io.veriguard.database.repository.StabilityTrendSnapshotRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 稳定性趋势 REST API（PR C5 / 招标 §3.3 ★1 + §4.2 ★3）.
 *
 * <p>URI 全部归集到 {@link #STABILITY_URI} 前缀，便于前端 actions 层统一访问.
 *
 * <p>RBAC：所有读端点限制为 {@link ResourceType#SIMULATION} READ —— 稳定性快照与 attack_chain_run 同 scope.
 */
@RestController
@RequiredArgsConstructor
public class StabilityApi {

  public static final String STABILITY_URI = "/api/stability";
  public static final String TREND_URI = STABILITY_URI + "/trend";
  public static final String TREND_DAILY_URI = STABILITY_URI + "/trend/daily";

  /** 默认页大小. */
  private static final int DEFAULT_PAGE_SIZE = 100;

  /** 默认页大小上限（避免一次拉太多点压垮前端 chart）. */
  private static final int MAX_PAGE_SIZE = 1000;

  private final StabilityTrendSnapshotRepository repository;

  /** 按可选 device_id / 时间范围分页拉趋势点（"按任务"视图）. */
  @GetMapping(TREND_URI)
  @Operation(summary = "List stability trend snapshots (per-task view)")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public StabilityTrendListOutput listTrend(
      @RequestParam(required = false) String deviceId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false, defaultValue = "0") @Min(0) int page,
      @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Page<StabilityTrendSnapshot> result =
        repository.findFiltered(
            deviceId,
            from,
            to,
            PageRequest.of(page, effectiveSize, Sort.by(Sort.Order.asc("capturedAt"))));
    List<StabilityTrendOutput> items =
        result.getContent().stream().map(StabilityTrendOutput::from).toList();
    return new StabilityTrendListOutput(items, result.getTotalElements(), page, effectiveSize);
  }

  /** 按天聚合视图（"按天"视图切换）—— 简单 Java 端聚合，数据量大时可改 SQL GROUP BY. */
  @GetMapping(TREND_DAILY_URI)
  @Operation(summary = "List stability trend aggregated by day")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public List<StabilityDailyAggregate> dailyAggregate(
      @RequestParam(required = false) String deviceId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    // 拉全量（聚合视图不分页，调用方应通过 from/to 限制范围）
    Page<StabilityTrendSnapshot> result =
        repository.findFiltered(
            deviceId, from, to, PageRequest.of(0, MAX_PAGE_SIZE, Sort.by(Sort.Order.asc("capturedAt"))));
    return aggregateByDay(result.getContent());
  }

  static List<StabilityDailyAggregate> aggregateByDay(List<StabilityTrendSnapshot> snapshots) {
    Map<LocalDate, DailyAccumulator> byDay = new TreeMap<>();
    for (StabilityTrendSnapshot s : snapshots) {
      LocalDate day = s.getCapturedAt().atZone(ZoneOffset.UTC).toLocalDate();
      byDay.computeIfAbsent(day, k -> new DailyAccumulator()).add(s);
    }
    return byDay.entrySet().stream()
        .map(e -> e.getValue().toAggregate(e.getKey()))
        .toList();
  }

  private static final class DailyAccumulator {
    private long count = 0;
    private long hitSum = 0;
    private long totalSum = 0;

    void add(StabilityTrendSnapshot s) {
      count++;
      hitSum += s.getHitCount();
      totalSum += s.getTotalCount();
    }

    StabilityDailyAggregate toAggregate(LocalDate day) {
      BigDecimal avg;
      if (totalSum == 0) {
        avg = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
      } else {
        avg =
            BigDecimal.valueOf(hitSum)
                .divide(BigDecimal.valueOf(totalSum), 4, RoundingMode.HALF_UP);
      }
      return new StabilityDailyAggregate(day, avg, count, hitSum, totalSum);
    }
  }
}
