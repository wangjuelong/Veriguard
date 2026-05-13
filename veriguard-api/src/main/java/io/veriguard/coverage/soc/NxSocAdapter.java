package io.veriguard.coverage.soc;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 蓝盾 NxSOC 真实接入骨架 —— PR C3.
 *
 * <p>当前 NxSOC API 文档未到（甲方启动会议待澄清），方法均抛 {@link UnsupportedOperationException}，
 * 通过 {@code @ConditionalOnProperty} 默认禁用，等 PR A1 接通后再实现.
 *
 * <p>启用配置（默认禁用）：
 * <pre>
 *   veriguard.soc.nxsoc.enabled=true
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "veriguard.soc.nxsoc.enabled", havingValue = "true")
public class NxSocAdapter implements SocAdapter {

  public static final String NAME = "nxsoc";

  @Override
  public List<SocAlert> queryAlerts(SocAlertQuery query) {
    throw new UnsupportedOperationException(
        "NxSocAdapter not implemented yet (waiting for vendor API spec — see PR A1)");
  }

  @Override
  public HealthStatus health() {
    return HealthStatus.unreachable;
  }

  @Override
  public String name() {
    return NAME;
  }
}
