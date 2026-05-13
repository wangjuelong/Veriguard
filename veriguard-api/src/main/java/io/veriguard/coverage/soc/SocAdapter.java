package io.veriguard.coverage.soc;

import java.util.List;

/**
 * 蓝盾 NxSOC 告警查询抽象 —— PR C3 边界覆盖度子模块.
 *
 * <p>覆盖度计算依赖反查 SOC 告警以判定单元格 hit/miss；NxSOC API 文档当前未到（甲方启动会议待澄清，
 * 见 docs/IPv6安全验证系统-甲方待澄清清单.md）。本接口先以 Stub 实现支撑 UI 演示与端到端测试，
 * 真实接入留给后续 PR A1。
 *
 * <p>实现选型由 {@link SocAdapterRouter} 按 Spring 配置 {@code veriguard.soc.*.enabled} 决定，
 * 默认走 {@link StubSocAdapter}。
 */
public interface SocAdapter {

  /**
   * 查询给定时间窗内匹配的告警.
   *
   * @return 告警列表（实现应保证按 observedAt 升序）；无匹配则返回空列表（不抛异常）.
   */
  List<SocAlert> queryAlerts(SocAlertQuery query);

  /** 健康检查；用于运行画布 UI 显示连接状态. */
  HealthStatus health();

  /** 实现名称（用于日志 / UI 显示，例如 "stub" / "nxsoc"）. */
  String name();
}
