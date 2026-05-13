package io.veriguard.monitoring;

/**
 * cron 校验失败 —— PR C4 招标 §3.2.
 *
 * <p>由 {@link CronValidator#validate(String)} 抛出；REST 层 catch 后映射 400.
 */
public class InvalidCronException extends RuntimeException {

  public InvalidCronException(String message) {
    super(message);
  }
}
