package io.veriguard.coverage.soc;

import java.time.Instant;
import java.util.List;

/**
 * SOC 告警查询参数 —— PR C3.
 *
 * @param assetIps      待查资产 IP 列表（后续可扩展为 hostname / IPv6 / 资产 ID）
 * @param from          时间窗起点（含）
 * @param to            时间窗终点（含）
 * @param ruleCategories 仅返回这些 rule_category 的告警；null / 空表示不限.
 */
public record SocAlertQuery(
    List<String> assetIps, Instant from, Instant to, List<String> ruleCategories) {

  public SocAlertQuery {
    if (assetIps == null) {
      throw new IllegalArgumentException("assetIps must not be null");
    }
    if (from == null || to == null) {
      throw new IllegalArgumentException("from / to must not be null");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to must not be before from");
    }
  }
}
