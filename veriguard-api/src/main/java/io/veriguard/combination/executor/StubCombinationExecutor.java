package io.veriguard.combination.executor;

import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 默认 Stub Executor —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>用于演示 / 自测：根据可配置概率返回 hit / miss / timeout，不真实发起任何网络攻击.
 * 真实 executor（PR D5）将按 base_attack_type 接入 §2.3 inject 链路.
 *
 * <p>{@link #supports(String)} 始终返回 true —— 作为兜底 executor 注册到 Router.
 *
 * <p>配置项（application.properties）：
 * <ul>
 *   <li>{@code veriguard.combination.stub.hit-probability} 默认 0.35</li>
 *   <li>{@code veriguard.combination.stub.miss-probability} 默认 0.55</li>
 *   <li>{@code veriguard.combination.stub.timeout-probability} 默认 0.10</li>
 * </ul>
 */
@Component
public class StubCombinationExecutor implements CombinationExecutor {

  private final double hitProbability;
  private final double missProbability;
  private final double timeoutProbability;

  public StubCombinationExecutor(
      @Value("${veriguard.combination.stub.hit-probability:0.35}") double hitProbability,
      @Value("${veriguard.combination.stub.miss-probability:0.55}") double missProbability,
      @Value("${veriguard.combination.stub.timeout-probability:0.10}") double timeoutProbability) {
    double sum = hitProbability + missProbability + timeoutProbability;
    if (Math.abs(sum - 1.0) > 1e-6) {
      throw new IllegalStateException(
          "Stub executor probabilities must sum to 1.0, got " + sum);
    }
    this.hitProbability = hitProbability;
    this.missProbability = missProbability;
    this.timeoutProbability = timeoutProbability;
  }

  @Override
  public boolean supports(String baseAttackType) {
    // 兜底 executor：所有未被具体 executor 接管的 base_type 都走这里
    return true;
  }

  @Override
  public AttackCombinationHitState execute(CombinationInstance instance) {
    double roll = ThreadLocalRandom.current().nextDouble();
    if (roll < hitProbability) {
      return AttackCombinationHitState.hit;
    }
    if (roll < hitProbability + missProbability) {
      return AttackCombinationHitState.miss;
    }
    return AttackCombinationHitState.timeout;
  }
}
