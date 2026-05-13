package io.veriguard.coverage.soc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub SocAdapter —— PR C3 默认实现.
 *
 * <p>用途：在拿到甲方 NxSOC API 之前，按可配置 hit_rate / timeout_rate 模拟告警查询，
 * 让覆盖度评估端到端跑通 + 截图演示.
 *
 * <p>语义：
 * <ul>
 *   <li>对每个 (assetIp) 滚一次随机数：</li>
 *   <li>roll &lt; timeout_rate → 抛 SocQueryTimeoutException（CoverageRunner 捕获标记 timeout）</li>
 *   <li>否则若 roll &lt; timeout_rate + hit_rate → 返回 1 条 mock alert</li>
 *   <li>否则 → 返回空列表（视为 miss）</li>
 * </ul>
 *
 * <p>配置（application.properties）：
 * <ul>
 *   <li>{@code veriguard.soc.stub.enabled} 默认 true（matchIfMissing）</li>
 *   <li>{@code veriguard.soc.stub.hit-rate} 默认 0.6</li>
 *   <li>{@code veriguard.soc.stub.timeout-rate} 默认 0.05</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "veriguard.soc.stub.enabled", havingValue = "true", matchIfMissing = true)
public class StubSocAdapter implements SocAdapter {

  public static final String NAME = "stub";

  private final double hitRate;
  private final double timeoutRate;

  public StubSocAdapter(
      @Value("${veriguard.soc.stub.hit-rate:0.6}") double hitRate,
      @Value("${veriguard.soc.stub.timeout-rate:0.05}") double timeoutRate) {
    if (hitRate < 0.0 || hitRate > 1.0) {
      throw new IllegalArgumentException("veriguard.soc.stub.hit-rate must be in [0,1]");
    }
    if (timeoutRate < 0.0 || timeoutRate > 1.0) {
      throw new IllegalArgumentException("veriguard.soc.stub.timeout-rate must be in [0,1]");
    }
    if (hitRate + timeoutRate > 1.0) {
      throw new IllegalArgumentException(
          "hit-rate + timeout-rate must be <= 1.0, got " + (hitRate + timeoutRate));
    }
    this.hitRate = hitRate;
    this.timeoutRate = timeoutRate;
  }

  @Override
  public List<SocAlert> queryAlerts(SocAlertQuery query) {
    if (query.assetIps().isEmpty()) {
      return List.of();
    }
    List<SocAlert> out = new ArrayList<>();
    Instant mid = midpoint(query.from(), query.to());
    for (String assetIp : query.assetIps()) {
      double roll = ThreadLocalRandom.current().nextDouble();
      if (roll < timeoutRate) {
        throw new SocQueryTimeoutException(
            "stub timeout for asset " + assetIp + " (rate=" + timeoutRate + ")");
      }
      if (roll < timeoutRate + hitRate) {
        String ruleCategory =
            (query.ruleCategories() == null || query.ruleCategories().isEmpty())
                ? "stub-category"
                : query.ruleCategories().get(0);
        out.add(
            new SocAlert(
                "stub-" + UUID.randomUUID(),
                assetIp,
                "stub-rule-" + ruleCategory,
                ruleCategory,
                mid,
                "medium"));
      }
    }
    return out;
  }

  @Override
  public HealthStatus health() {
    return HealthStatus.healthy;
  }

  @Override
  public String name() {
    return NAME;
  }

  private static Instant midpoint(Instant from, Instant to) {
    long avg = (from.getEpochSecond() + to.getEpochSecond()) / 2;
    return Instant.ofEpochSecond(avg);
  }
}
