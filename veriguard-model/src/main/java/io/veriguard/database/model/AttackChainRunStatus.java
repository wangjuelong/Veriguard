package io.veriguard.database.model;

public enum AttackChainRunStatus {
  SCHEDULED,
  CANCELED,
  RUNNING,
  PAUSED,
  FINISHED,
  /**
   * 攻击编排链路在 STOP_ON_BLOCK 执行模式下，因任一节点 PREVENTION expectation 达到 SUCCESS 而被截停（spec
   * §3.3 / PRD §2.4 拦截后停止）。所有 PENDING / SCHEDULED 状态的节点会被同时置为 SKIPPED。视作一种特殊
   * 的 FINISHED —— run 不再调度新节点，但已 RUNNING 的节点允许自然结束。
   */
  STOPPED_ON_BLOCK
}
