package io.veriguard.injectors.web_attack.service;

import io.veriguard.database.model.Agent;
import io.veriguard.injectors.web_attack.WebAttackContract;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.service.AgentService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选择有 {@code http_attack} 能力的协作主机 Agent，并校验 Web 攻击包 inject 内容.
 *
 * <p>本 PR 不发起真实 HTTP 请求；agent 客户端独立项目落地后由 agent 侧完成 HTTP 执行 + 结果回填.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebAttackDispatchService {

  private static final Set<String> ALLOWED_METHODS =
      Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

  private final AgentService agentService;

  /**
   * 校验 web_attack 内容必填字段 + method 合法性.
   *
   * @throws IllegalArgumentException 字段缺失或 method 不在允许集合
   */
  public void validateContent(WebAttackContent content) {
    if (content.getMethod() == null || content.getMethod().isBlank()) {
      throw new IllegalArgumentException("web_request_method is required");
    }
    if (!ALLOWED_METHODS.contains(content.getMethod().toUpperCase())) {
      throw new IllegalArgumentException(
          "Invalid web_request_method: "
              + content.getMethod()
              + " (allowed: "
              + ALLOWED_METHODS
              + ")");
    }
    if (content.getUrl() == null || content.getUrl().isBlank()) {
      throw new IllegalArgumentException("web_request_url is required");
    }
  }

  /**
   * 选一个有 http_attack 能力的协作主机 Agent.
   *
   * <p>多个匹配 → 取首个（确定性，便于测试和复现）；后续可加负载策略.
   */
  public Optional<Agent> selectAgent() {
    List<Agent> candidates =
        agentService.selectAgentsForCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0));
  }
}
