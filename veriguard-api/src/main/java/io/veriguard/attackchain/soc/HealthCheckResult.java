package io.veriguard.attackchain.soc;

/**
 * SOC connector 健康检查结果（spec §4.5 / §6.3 admin UI 红绿灯）.
 *
 * @param status 健康状态
 * @param message 给运维看的简短描述（成功时可空，失败时建议有可定位的关键字，如 {@code "auth"} / {@code "timeout"} / {@code
 *     "unreachable"}；不要回传完整 stack trace 暴露内部细节）
 */
public record HealthCheckResult(Status status, String message) {

  public enum Status {
    /** 凭证 + 网络 + 权限均 OK，可以发起查询 */
    HEALTHY,
    /** 临时不可用（网络 / 速率限制 / 部分降级），后续重试可能恢复 */
    DEGRADED,
    /** 配置错误 / 凭证失效 / 平台不可达，需运维介入 */
    UNHEALTHY,
    /** 连接器已禁用（{@code enabled=false}） */
    DISABLED
  }

  public HealthCheckResult {
    if (status == null) {
      throw new IllegalArgumentException("status required");
    }
  }

  public static HealthCheckResult healthy() {
    return new HealthCheckResult(Status.HEALTHY, null);
  }

  public static HealthCheckResult disabled() {
    return new HealthCheckResult(Status.DISABLED, "connector disabled");
  }

  public static HealthCheckResult unhealthy(String reason) {
    return new HealthCheckResult(Status.UNHEALTHY, reason);
  }

  public static HealthCheckResult degraded(String reason) {
    return new HealthCheckResult(Status.DEGRADED, reason);
  }
}
