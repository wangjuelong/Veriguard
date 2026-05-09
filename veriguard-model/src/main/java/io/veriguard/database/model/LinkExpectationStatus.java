package io.veriguard.database.model;

/**
 * 链路级 expectation 状态机（PRD §2.4 / spec §2.2.6）.
 *
 * <ul>
 *   <li>{@link #PENDING} —— 实例化后等查询，未到 expirationTime
 *   <li>{@link #SUCCESS} —— 累加分数 ≥ expectedScore（防守方 SOC 规则成功命中）
 *   <li>{@link #PARTIAL} —— 累加分数 > 0 但 &lt; expectedScore
 *   <li>{@link #FAILED} —— 已查过 SOC 但 0 命中
 *   <li>{@link #UNKNOWN} —— 查询失败（重试耗尽）/ 过期未结算 —— 不计入 verdict 分母
 * </ul>
 */
public enum LinkExpectationStatus {
  PENDING,
  SUCCESS,
  PARTIAL,
  FAILED,
  UNKNOWN
}
