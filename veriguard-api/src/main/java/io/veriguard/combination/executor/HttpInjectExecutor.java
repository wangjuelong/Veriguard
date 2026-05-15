package io.veriguard.combination.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.Agent;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
import io.veriguard.rest.agent.AgentDtos;
import io.veriguard.rest.agent.AgentTaskQueueService;
import io.veriguard.service.exception.CapabilityNotSupportedException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真实 HTTP inject 执行器 —— IPv6 安全验证系统 §3.6 ★2 PR D5 / C1-Platform-2 Task C.10.
 *
 * <p>承接 D2 的执行器路由抽象，针对 Web 类基础攻击（§3.5 attack_category）派发到协作主机 Agent 的 {@code http_attack} 能力（与 §2.3
 * B-ii PR-C / {@code WebAttackContract} 同一上行通道）。
 *
 * <p>C1-Platform-2 接通流程：
 *
 * <ol>
 *   <li>反序列化 {@code instance.transformedPayload()} → {@link WebAttackContent}
 *   <li>{@link WebAttackDispatchService#selectAgent()} 选有 http_attack 能力的 Agent
 *   <li>{@link WebAttackDispatchService#dispatch(String, Agent, WebAttackContent)} 派发任务
 *   <li>future 阻塞等 {@code timeout} (默认 30s) → 根据 {@code result.status} 映射:
 *       <ul>
 *         <li>{@code SUCCESS} → {@link AttackCombinationHitState#hit} (HIT_CONFIRMED)
 *         <li>{@code FAILED} → {@link AttackCombinationHitState#miss} (MISS — 被 WAF/IPS 阻断)
 *         <li>timeout / 无 agent → {@link AttackCombinationHitState#timeout} (INCONCLUSIVE)
 *       </ul>
 * </ol>
 *
 * <p>注册策略：由 {@code veriguard.combination.http-inject.enabled=true} 显式开启（默认关闭）。默认关闭时所有 base type
 * 都路由到 {@link StubCombinationExecutor}，便于演示 / 截图 / 自测；真实环境部署 Agent 后置 true 即可上线。
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

  /** 单实例 ObjectMapper — 无状态线程安全; 仅做 payload JSON 反序列化. */
  private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper();

  private final WebAttackDispatchService dispatchService;
  private final Duration awaitTimeout;

  /**
   * Spring 注入构造器 (默认 30s 超时).
   *
   * @param awaitTimeoutSeconds {@code veriguard.combination.http-inject.timeout-seconds}, 默认 30s
   */
  public HttpInjectExecutor(
      WebAttackDispatchService dispatchService,
      @Value("${veriguard.combination.http-inject.timeout-seconds:30}") long awaitTimeoutSeconds) {
    this(dispatchService, Duration.ofSeconds(awaitTimeoutSeconds));
  }

  /** Test-facing constructor — explicit timeout. */
  public HttpInjectExecutor(WebAttackDispatchService dispatchService, Duration awaitTimeout) {
    this.dispatchService = dispatchService;
    this.awaitTimeout = awaitTimeout;
  }

  @Override
  public boolean supports(String baseAttackType) {
    if (baseAttackType == null) {
      return false;
    }
    return SUPPORTED_BASE_TYPES.contains(baseAttackType);
  }

  @Override
  public CombinationExecutionResult execute(CombinationInstance instance) {
    if (instance == null) {
      throw new IllegalArgumentException("instance must not be null");
    }

    // 1) Parse payload as WebAttackContent.
    WebAttackContent content;
    try {
      content = PAYLOAD_MAPPER.readValue(instance.transformedPayload(), WebAttackContent.class);
    } catch (JsonProcessingException ex) {
      log.error(
          "HttpInjectExecutor: failed to parse payload for run={}, combination={}, base_type={}",
          instance.runId(),
          instance.combinationId(),
          instance.baseAttackType(),
          ex);
      // Unparseable payload — treat as system-level failure, caller marks failed/retry.
      throw new IllegalStateException(
          "Invalid web_attack payload JSON for combination " + instance.combinationId(), ex);
    }

    // 2) Validate content (method allowed + url non-blank).
    dispatchService.validateContent(content);

    // 3) Select agent with http_attack capability.
    Optional<Agent> agentOpt = dispatchService.selectAgent();
    if (agentOpt.isEmpty()) {
      log.warn(
          "HttpInjectExecutor: no Agent with http_attack capability for combination {} —"
              + " marking INCONCLUSIVE (timeout)",
          instance.combinationId());
      return CombinationExecutionResult.of(AttackCombinationHitState.timeout);
    }

    // 4) Dispatch + await with timeout.
    String taskId = instance.runId() + ":" + instance.combinationId();
    try {
      AgentTaskQueueService.ReceivedResult received =
          dispatchService
              .dispatch(taskId, agentOpt.get(), content)
              .get(awaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
      // AB-2: propagate agent stdout/stderr/exit_code/error_message so the scheduler can
      // persist them to attack_combination_results.{stdout,stderr,exit_code,error_message}
      // (Flyway V22) — admins can post-mortem each combination without re-running.
      AgentDtos.ResultInput input = received.input();
      return new CombinationExecutionResult(
          mapStatusToHitState(input.status()),
          input.stdout(),
          input.stderr(),
          input.exitCode(),
          input.errorMessage());
    } catch (TimeoutException ex) {
      log.info(
          "HttpInjectExecutor: dispatch timeout after {}ms for combination {} — INCONCLUSIVE",
          awaitTimeout.toMillis(),
          instance.combinationId());
      return CombinationExecutionResult.of(AttackCombinationHitState.timeout);
    } catch (CapabilityNotSupportedException ex) {
      // Agent we selected does not actually have the capability — shouldn't happen if
      // selectAgent() honored the filter, but fail visibly (no fallback) per project rule.
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while awaiting dispatch result", ex);
    } catch (ExecutionException ex) {
      throw new RuntimeException(
          "Dispatch future failed for combination " + instance.combinationId(), ex.getCause());
    }
  }

  /**
   * Map agent-reported task status to combination hit_state.
   *
   * <ul>
   *   <li>{@code SUCCESS} — HTTP request succeeded (got expected response code or body match) →
   *       绕过攻击成功 → MISS 是错的; actually agent reports SUCCESS = it sent the request and got the
   *       expected attack response, which from the platform's POV means the WAF/IPS did NOT block →
   *       MISS (Bypass 维度成功). But the wording here is intentionally ambiguous: we adopt the plan's
   *       mapping: SUCCESS → HIT_CONFIRMED (attack executed) for now; real semantics will be
   *       refined in C1-Integration when SOC integration distinguishes "agent sent" vs "device
   *       blocked".
   *   <li>{@code FAILED} — agent could not reach target / WAF blocked → MISS
   *   <li>other / null → timeout (INCONCLUSIVE)
   * </ul>
   */
  private AttackCombinationHitState mapStatusToHitState(String status) {
    if (status == null) {
      return AttackCombinationHitState.timeout;
    }
    return switch (status.toUpperCase()) {
      case "SUCCESS" -> AttackCombinationHitState.hit;
      case "FAILED" -> AttackCombinationHitState.miss;
      default -> AttackCombinationHitState.timeout;
    };
  }
}
