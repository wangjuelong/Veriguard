package io.veriguard.monitoring;

import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.TimeZone;
import org.quartz.CronExpression;

/**
 * Quartz cron 表达式校验器 —— PR C4 招标 §3.2.
 *
 * <p>规则：
 *
 * <ul>
 *   <li>必须是合法的 Quartz cron（6 字段：sec min hour day month dow，可加可选 year）
 *   <li>下两次触发的间隔必须 ≥ 1 小时（招标"按天 / 按小时"要求的下界）
 * </ul>
 *
 * <p>注：Quartz 的 {@link CronExpression} 已经处理了非法字段、范围越界等语法错误，
 * 我们额外加最小间隔约束。
 */
public final class CronValidator {

  /** 最小允许触发间隔（秒） —— 招标 §3.2 "按小时". */
  public static final long MIN_INTERVAL_SECONDS = 3_600L;

  private CronValidator() {}

  /**
   * 校验 cron 是否合法且最小触发间隔 ≥ 1 小时.
   *
   * @throws InvalidCronException 任何校验失败
   */
  public static void validate(String cron) {
    if (cron == null || cron.isBlank()) {
      throw new InvalidCronException("cron expression must not be blank");
    }
    CronExpression parsed;
    try {
      parsed = new CronExpression(cron);
    } catch (ParseException e) {
      throw new InvalidCronException(
          "invalid cron syntax: " + e.getMessage() + " (raw=" + cron + ")");
    }
    parsed.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date now = new Date();
    Date next = parsed.getNextValidTimeAfter(now);
    if (next == null) {
      throw new InvalidCronException(
          "cron has no valid future trigger time (raw=" + cron + ")");
    }
    Date afterNext = parsed.getNextValidTimeAfter(next);
    if (afterNext == null) {
      throw new InvalidCronException(
          "cron only has a single future trigger; cannot enforce min interval (raw=" + cron + ")");
    }
    long intervalSeconds = Duration.between(next.toInstant(), afterNext.toInstant()).getSeconds();
    if (intervalSeconds < MIN_INTERVAL_SECONDS) {
      throw new InvalidCronException(
          String.format(
              "cron trigger interval %ds < minimum %ds (1h); raw=%s",
              intervalSeconds, MIN_INTERVAL_SECONDS, cron));
    }
  }

  /** 计算下一次触发时间（用于 detail 输出 + 测试）. */
  public static java.time.Instant nextFireTime(String cron) {
    try {
      CronExpression parsed = new CronExpression(cron);
      parsed.setTimeZone(TimeZone.getTimeZone("UTC"));
      Date next = parsed.getNextValidTimeAfter(new Date());
      return next == null ? null : next.toInstant();
    } catch (ParseException e) {
      throw new InvalidCronException("invalid cron syntax: " + e.getMessage());
    }
  }
}
