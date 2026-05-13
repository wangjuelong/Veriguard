package io.veriguard.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CronValidatorTest {

  @Test
  void hourly_cron_is_valid() {
    // 每小时 0 分触发
    CronValidator.validate("0 0 * * * ?");
  }

  @Test
  void daily_cron_is_valid() {
    // 每天 03:00 触发
    CronValidator.validate("0 0 3 * * ?");
  }

  @Test
  void rejects_sub_hour_interval() {
    // 每 5 分钟触发 → 间隔 < 1h
    assertThatThrownBy(() -> CronValidator.validate("0 0/5 * * * ?"))
        .isInstanceOf(InvalidCronException.class)
        .hasMessageContaining("< minimum");
  }

  @Test
  void rejects_invalid_syntax() {
    assertThatThrownBy(() -> CronValidator.validate("not a cron"))
        .isInstanceOf(InvalidCronException.class)
        .hasMessageContaining("invalid cron syntax");
  }

  @Test
  void rejects_blank() {
    assertThatThrownBy(() -> CronValidator.validate(""))
        .isInstanceOf(InvalidCronException.class)
        .hasMessageContaining("must not be blank");

    assertThatThrownBy(() -> CronValidator.validate(null))
        .isInstanceOf(InvalidCronException.class);
  }

  @Test
  void next_fire_time_returns_future_instant() {
    // 每小时 0 分
    var next = CronValidator.nextFireTime("0 0 * * * ?");
    assertThat(next).isNotNull();
    assertThat(next).isAfter(java.time.Instant.now().minusSeconds(1));
  }
}
