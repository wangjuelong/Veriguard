package io.veriguard.database.model;

/**
 * AttackChainNode 运行时状态机 (PRD §2.4 / spec §3.1).
 *
 * <p>用于 {@code attack_chain_nodes.node_state} 列追踪单节点在重复执行循环内的状态：
 *
 * <ul>
 *   <li>{@link #PENDING} —— 等待调度（默认）
 *   <li>{@link #SCHEDULED} —— 调度器已排期，正在等待依赖完成
 *   <li>{@link #RUNNING} —— 已下发到执行器，等待节点级期望反馈
 *   <li>{@link #SETTLED} —— 期望全部出结果（成功/失败/超时），可推动下游
 *   <li>{@link #SKIPPED} —— 上游 verdict=BLOCKED 且 STOP_ON_BLOCK 模式下跳过
 *   <li>{@link #FAILED} —— 执行器返回错误（不是节点期望失败，而是基础设施失败）
 * </ul>
 */
public enum NodeState {
  PENDING,
  SCHEDULED,
  RUNNING,
  SETTLED,
  SKIPPED,
  FAILED
}
