package io.veriguard.rest.soc_connectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.attackchain.soc.HealthCheckResult;
import java.time.Instant;

/**
 * SOC connector 状态输出 DTO（spec §6.3.6 状态页 + §4.5 健康检查）.
 *
 * <p>给前端 {@code SocConnectorStatusList} / {@code SocConnectorRulePicker}
 * 提供统一的快照视图：connector 元信息 + 最近一次 {@link HealthCheckResult} +
 * 已知规则数 + 上次检查时刻。
 */
public record SocConnectorOutput(
    @JsonProperty("connector_id") String connectorId,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("status") HealthCheckResult.Status status,
    @JsonProperty("message") String message,
    @JsonProperty("available_rule_count") Integer availableRuleCount,
    @JsonProperty("last_checked_at") Instant lastCheckedAt) {}
