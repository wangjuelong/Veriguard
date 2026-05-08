package io.veriguard.database.model;

/**
 * 攻击链执行策略 (PRD §2.4).
 *
 * <p>{@link #STOP_ON_BLOCK} —— 一旦上游节点 PREVENTION 期望被防御方拦截 (verdict=BLOCKED)，调度器停止下游节点。
 *
 * <p>{@link #CONTINUE} —— 即使被拦截也继续向下传播，用于跑完全链路收集数据。
 */
public enum ExecutionMode {
  STOP_ON_BLOCK,
  CONTINUE
}
