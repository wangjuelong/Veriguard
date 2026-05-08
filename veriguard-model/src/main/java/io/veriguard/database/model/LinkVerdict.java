package io.veriguard.database.model;

/**
 * 链路级 verdict (PRD §2.4 / spec §2.2.5).
 *
 * <ul>
 *   <li>{@link #FULL_BREACH} —— 全链路有效（攻击全成功，防御全败）
 *   <li>{@link #FULL_BLOCKED} —— 全链路失效（攻击全失败，防御全胜）
 *   <li>{@link #PARTIAL} —— 部分失效
 *   <li>{@link #PENDING} —— 计算中
 *   <li>{@link #N_A} —— 不适用（如 DETECTION 维度未配置 SOC 规则）
 * </ul>
 */
public enum LinkVerdict {
  FULL_BREACH,
  FULL_BLOCKED,
  PARTIAL,
  PENDING,
  N_A
}
