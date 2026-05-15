package io.veriguard.combination.executor;

import io.veriguard.combination.CombinationInstance;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Executor 路由器 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>按 base_attack_type 选择第一个 {@link CombinationExecutor#supports(String)} 返回 true 的实现. 具体 executor
 * 在 PR D5+ 注册到容器（如 HttpInjectExecutor / XmlPayloadExecutor / SmtpInjectExecutor 等）， {@link
 * StubCombinationExecutor} 永远作为兜底（顺序由 Spring bean 注入顺序保证，stub 用 @Order 控制最低优先级）.
 *
 * <p>当前 PR D2 容器内只有 stub，故所有 base_type 都路由到 stub.
 */
@Component
public class CombinationExecutorRouter {

  private final List<CombinationExecutor> executors;
  private final StubCombinationExecutor fallback;

  public CombinationExecutorRouter(
      List<CombinationExecutor> executors, StubCombinationExecutor fallback) {
    this.executors = executors;
    this.fallback = fallback;
  }

  /**
   * 选择匹配的 executor.
   *
   * <p>遍历顺序：除 fallback 外的具体 executor 优先；都不支持时回落到 stub.
   */
  public CombinationExecutor select(String baseAttackType) {
    if (baseAttackType == null || baseAttackType.isBlank()) {
      throw new IllegalArgumentException("baseAttackType must not be blank");
    }
    for (CombinationExecutor exec : executors) {
      if (exec == fallback) {
        continue;
      }
      if (exec.supports(baseAttackType)) {
        return exec;
      }
    }
    return fallback;
  }

  /** 派发执行：路由 + 执行. */
  public CombinationExecutionResult dispatch(CombinationInstance instance) {
    return select(instance.baseAttackType()).execute(instance);
  }
}
