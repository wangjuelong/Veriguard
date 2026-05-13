package io.veriguard.coverage.soc;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SocAdapter 选择器 —— PR C3.
 *
 * <p>按配置 {@code veriguard.soc.preferred-adapter}（默认 "stub"）从容器内 {@link SocAdapter}
 * 实现中选目标；找不到时 fail-fast 抛异常（不静默回退，遵循 CLAUDE.md "no fallback code" 约定）.
 */
@Slf4j
@Component
public class SocAdapterRouter {

  private final List<SocAdapter> adapters;
  private final String preferredAdapter;

  public SocAdapterRouter(
      List<SocAdapter> adapters,
      @Value("${veriguard.soc.preferred-adapter:stub}") String preferredAdapter) {
    this.adapters = adapters;
    this.preferredAdapter = preferredAdapter;
  }

  /**
   * 选目标 adapter.
   *
   * @throws IllegalStateException 若 preferred adapter 在容器中未注册
   */
  public SocAdapter select() {
    for (SocAdapter a : adapters) {
      if (a.name().equals(preferredAdapter)) {
        return a;
      }
    }
    throw new IllegalStateException(
        "Preferred SocAdapter '"
            + preferredAdapter
            + "' is not registered. Available: "
            + adapters.stream().map(SocAdapter::name).toList()
            + ". Check veriguard.soc.*.enabled flags.");
  }

  /** 仅用于测试 / 健康端点：列出所有已注册 adapter 名. */
  public List<String> listAvailable() {
    return adapters.stream().map(SocAdapter::name).toList();
  }

  public String preferredAdapter() {
    return preferredAdapter;
  }
}
