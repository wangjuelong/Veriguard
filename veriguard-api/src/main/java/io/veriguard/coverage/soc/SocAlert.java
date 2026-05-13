package io.veriguard.coverage.soc;

import java.time.Instant;

/**
 * SOC 告警 DTO —— PR C3.
 *
 * <p>语义：在 {@code observedAt} 时刻，针对资产 {@code assetIp} 命中 rule {@code ruleId}（属 {@code ruleCategory} 类）.
 */
public record SocAlert(
    String alertId,
    String assetIp,
    String ruleId,
    String ruleCategory,
    Instant observedAt,
    String severity) {}
