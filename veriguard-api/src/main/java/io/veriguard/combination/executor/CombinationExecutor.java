package io.veriguard.combination.executor;

import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.combination.AttackCombinationHitState;

/**
 * 组合执行器抽象 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>真实攻击执行能力（HTTP / PCAP / 邮件 inject）在 PR B1-B5 / D5 由具体实现提供，
 * 本 PR D2 仅定义抽象 + {@link StubCombinationExecutor} 默认实现.
 *
 * <p>实现类应：
 * <ul>
 *   <li>同步阻塞或超时返回（caller 负责并发控制）</li>
 *   <li>不抛出异常用于「未命中」/「超时」—— 用 hit_state 表达；只在系统级错误（资源不可达）时抛</li>
 *   <li>声明 {@link #supports(String)} 用于路由</li>
 * </ul>
 */
public interface CombinationExecutor {

  /**
   * 是否支持该 base_attack_type.
   *
   * <p>Router 会按此筛选具体实现.
   */
  boolean supports(String baseAttackType);

  /**
   * 执行一个组合实例并返回结果状态.
   *
   * @param instance 组合实例
   * @return hit / miss / timeout（不会返回 pending / running / failed —— 后两者由 caller 决定）
   * @throws RuntimeException 仅在系统级错误（DB / queue / network 不可达）时抛，
   *     caller 应当 catch + 标 retry / failed
   */
  AttackCombinationHitState execute(CombinationInstance instance);
}
