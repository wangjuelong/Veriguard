package io.veriguard.combination.executor;

import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真实 HTTP inject 执行器 —— IPv6 安全验证系统 §3.6 ★2 PR D5.
 *
 * <p>承接 D2 的执行器路由抽象，针对 Web 类基础攻击（§3.5 attack_category）派发到协作主机 Agent
 * 的 {@code http_attack} 能力（与 §2.3 B-ii PR-C / {@code WebAttackContract} 同一上行通道）。
 *
 * <p>本 PR 范围内为 <b>骨架</b>：{@link #supports(String)} 已覆盖 11 类 Web 攻击；
 * {@link #execute(CombinationInstance)} 抛 {@link UnsupportedOperationException} 表示
 * 真实 dispatch 通道尚未接通 —— 与 {@link CombinationExecutor#execute(CombinationInstance)}
 * 契约一致：系统级错误抛 {@code RuntimeException}，由 caller（{@code CombinationScheduler}）
 * 重试或最终标 {@code failed}。后续接通 {@code WebAttackDispatchService} 时只改本方法。
 *
 * <p>fail-fast：不返回伪造的 hit/miss/timeout。
 *
 * <p>注册策略：由 {@code veriguard.combination.http-inject.enabled=true} 显式开启
 * （默认关闭）。默认关闭时所有 base type 都路由到 {@link StubCombinationExecutor}，便于
 * 演示 / 截图 / 自测；真实环境接通 dispatch 后置 true 即可上线。
 */
@Component
@ConditionalOnProperty(
    prefix = "veriguard.combination.http-inject",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class HttpInjectExecutor implements CombinationExecutor {

  private static final Logger log = LoggerFactory.getLogger(HttpInjectExecutor.class);

  /** §3.5 attack_category 中映射到 HTTP inject 通道的 11 类基础攻击. */
  static final Set<String> SUPPORTED_BASE_TYPES =
      Set.of(
          "sql_injection",
          "xss",
          "xxe",
          "ssrf",
          "ssti",
          "command_execution",
          "directory_traversal",
          "csrf",
          "weak_credential",
          "upload_bypass",
          "oversized_upload");

  @Override
  public boolean supports(String baseAttackType) {
    if (baseAttackType == null) {
      return false;
    }
    return SUPPORTED_BASE_TYPES.contains(baseAttackType);
  }

  @Override
  public AttackCombinationHitState execute(CombinationInstance instance) {
    if (instance == null) {
      throw new IllegalArgumentException("instance must not be null");
    }
    // TODO(PR D5+): 接通 WebAttackDispatchService —— 真实接通后根据 agent 回执映射为
    // hit/miss/timeout（hit=安全设备阻断、miss=请求成功未被拦截、timeout=Agent 未在期内回执）。
    log.info(
        "HttpInjectExecutor dispatch placeholder: run={}, combination={}, base_type={},"
            + " bypass_dim={}, asset={}",
        instance.runId(),
        instance.combinationId(),
        instance.baseAttackType(),
        instance.bypassDimensionId(),
        instance.assetId());
    throw new UnsupportedOperationException(
        "HttpInjectExecutor skeleton: real WebAttackDispatchService wiring pending (PR D5+);"
            + " base_type="
            + instance.baseAttackType());
  }
}
