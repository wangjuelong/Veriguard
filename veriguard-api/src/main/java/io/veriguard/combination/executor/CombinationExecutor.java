package io.veriguard.combination.executor;

import io.veriguard.combination.CombinationInstance;

/**
 * 组合执行器抽象 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>真实攻击执行能力（HTTP / PCAP / 邮件 inject）在 PR B1-B5 / D5 由具体实现提供， 本 PR D2 仅定义抽象 + {@link
 * StubCombinationExecutor} 默认实现.
 *
 * <p>实现类应：
 *
 * <ul>
 *   <li>同步阻塞或超时返回（caller 负责并发控制）
 *   <li>不抛出异常用于「未命中」/「超时」—— 用 hit_state 表达；只在系统级错误（资源不可达）时抛
 *   <li>声明 {@link #supports(String)} 用于路由
 * </ul>
 *
 * <p>C1-Platform-3 follow-up (AB-2)：返回类型由 {@code AttackCombinationHitState} 升级为 {@link
 * CombinationExecutionResult}，让 HTTP 攻击包 executor 把 agent 端 stdout / stderr / exit_code 透传给上层
 * scheduler 写库；不需要透传的实现（Stub / PCAP）调 {@link CombinationExecutionResult#of} 把 hit-state 包成纯结果即可。
 */
public interface CombinationExecutor {

  /**
   * 是否支持该 base_attack_type.
   *
   * <p>Router 会按此筛选具体实现.
   */
  boolean supports(String baseAttackType);

  /**
   * 执行一个组合实例并返回结果（hit_state + 可选 stdout/stderr/exit_code）.
   *
   * @param instance 组合实例
   * @return {@link CombinationExecutionResult} 含 hit / miss / timeout（不会返回 pending / running /
   *     failed —— 后两者由 caller 决定）+ 可选 agent 输出字段
   * @throws RuntimeException 仅在系统级错误（DB / queue / network 不可达）时抛， caller 应当 catch + 标 retry /
   *     failed
   */
  CombinationExecutionResult execute(CombinationInstance instance);
}
