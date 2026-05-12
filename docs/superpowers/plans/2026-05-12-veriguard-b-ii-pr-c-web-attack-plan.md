# B-ii PR-C：Web 攻击包 Inject Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `veriguard_web_attack` inject type，让用户在攻击编排链路上配置 HTTP 请求作为用例（method / url / headers / body / 期望状态码），平台通过 `agent_capabilities="http_attack"` 选择协作主机 Agent 派发执行。

**Architecture:**
1. **不新建 PayloadType**——遵循 PR-B 邮件 inject 模式：HTTP 请求细节存 `inject_content` JSONB（`WebAttackContent` POJO）；可复用模板抽象由 `WebAttackContract`（InjectorContract）承担，与现有 5 种 PayloadType（Command/Executable/FileDrop/DnsResolution/NetworkTraffic）正交.
2. **简化派发模型**——`WebAttackExecutor` 通过 `agentService.findByCapability("http_attack")` 选协作主机 Agent，写入 dispatch trace + `ExecutionProcess(false)` 即返回。**真实结果回填、协作主机 Agent 客户端代码、响应特征判定均在本 PR 范围外**（独立 PR-E / 协作主机 Agent 子项目）.
3. **单请求模式**——spec §5.2 的"请求序列"高级模式在本 PR 范围外，留作 follow-up.

**Tech Stack:** Spring Boot 3.3.7 / Java 21 / JPA / Hibernate JsonType / Jackson / Mockito（@ExtendWith(MockitoExtension.class)）/ Lombok.

---

## 准备工作

- **Worktree**: `/Users/lamba/github/Veriguard/worktrees/b-ii-pr-c-web-attack`，base=main，HEAD=`3377f723a` (PR-B merge).
- **Java 21**: 所有 `mvn` 命令前需 `export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`，否则 Mockito 在 Java 25 上会大批失败。
- **Master 锁**: `5d7e05da6` 永不动；PR base=main.
- **AI commit trailer**: 每个 commit 末尾 `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- **中文 commit 风格**: `执行：...` / `修复：...` / `设计：...`.
- **依赖前置（已在 main）**:
  - PR-A `agent_capabilities` JSONB 列（V7）+ `AgentRepository.findByCapability(String capabilityJson)` + `AgentService.findByCapability(String capability)` 已实现.
  - PR-B 邮件 inject 完整模板可参照 `veriguard-api/src/main/java/io/veriguard/injectors/email/`.
- **Flyway**: 下一个版本 V9（V8 已被 SMTP profiles 占用），但本 PR 不引入 schema 变更，跳过 V9 migration.

---

## 文件结构

### 后端 (`veriguard-api`)

```
src/main/java/io/veriguard/injectors/web_attack/
  WebAttackContract.java                     # Contractor 子类：注册 inject 类型 + 字段
  WebAttackExecutor.java                      # NodeExecutor 子类：派发到 Agent
  model/
    WebAttackContent.java                     # JSONB POJO：method/url/headers/body/...
    WebRequestHeader.java                     # KV pair 嵌套类型，给 headers 用
  service/
    WebAttackDispatchService.java             # 选 Agent + 记 dispatch trace 的服务

src/main/java/io/veriguard/integration/impl/injectors/web_attack/
  WebAttackNodeExecutorIntegration.java       # autostart + 注册 NodeExecutor
  WebAttackNodeExecutorIntegrationFactory.java # @Service Factory（被 connector instance autostart 选中）

src/main/resources/img/
  icon-web_attack.png                          # 占位 icon（复用 icon-manual.png 临时用）

src/test/java/io/veriguard/injectors/web_attack/
  service/WebAttackDispatchServiceTest.java   # 6 TDD 单测
  WebAttackExecutorTest.java                   # 4 TDD 单测
```

### 前端 (`veriguard-front`)

无需新增文件——`/api/images/injectors/veriguard_web_attack` 通用 endpoint 自动从 Contractor 的 `getIcon()` 取 `icon-web_attack.png`；inject 编辑器通过 contract 字段 schema 自动生成表单（参考 PR-B 邮件 inject 编辑器同款机制）。

---

## Task 1：WebAttackContent + WebRequestHeader 数据模型

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/web_attack/model/WebAttackContent.java`
- Create: `veriguard-api/src/main/java/io/veriguard/injectors/web_attack/model/WebRequestHeader.java`

### Steps

- [ ] **Step 1.1：创建 WebRequestHeader（KV pair POJO）**

```java
package io.veriguard.injectors.web_attack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** Single HTTP header KV pair (used inside WebAttackContent.headers list). */
@Getter
@Setter
public class WebRequestHeader {

  @JsonProperty("name")
  private String name;

  @JsonProperty("value")
  private String value;
}
```

- [ ] **Step 1.2：创建 WebAttackContent（inject_content JSONB POJO）**

```java
package io.veriguard.injectors.web_attack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.model.attack_chain_node.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * POJO mirroring {@code inject_content} JSONB for the {@code veriguard_web_attack} inject type.
 *
 * <p>Field names mirror spec §5.1 wire format (snake_case).
 */
@Getter
@Setter
public class WebAttackContent {

  @JsonProperty("web_request_method")
  private String method;

  @JsonProperty("web_request_url")
  private String url;

  @JsonProperty("web_request_headers")
  private List<WebRequestHeader> headers = new ArrayList<>();

  @JsonProperty("web_request_body")
  private String body;

  @JsonProperty("web_request_body_type")
  private String bodyType;

  @JsonProperty("web_request_cookies")
  private String cookies;

  @JsonProperty("web_request_follow_redirects")
  private boolean followRedirects = false;

  @JsonProperty("web_request_verify_tls")
  private boolean verifyTls = false;

  @JsonProperty("web_request_timeout_seconds")
  private int timeoutSeconds = 30;

  @JsonProperty("expected_status_codes")
  private List<Integer> expectedStatusCodes = new ArrayList<>();

  @JsonProperty("expected_body_regex")
  private String expectedBodyRegex;

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
```

- [ ] **Step 1.3：编译验证**

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 1.4：Spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/injectors/web_attack/model/WebAttackContent.java \
        veriguard-api/src/main/java/io/veriguard/injectors/web_attack/model/WebRequestHeader.java
git commit -m "$(cat <<'EOF'
执行：WebAttackContent + WebRequestHeader 数据模型（B-ii PR-C Step 1）

JSONB 反序列化目标 POJO，字段名遵循 spec §5.1 snake_case wire 约定：
- method / url / headers(List<KV>) / body / body_type / cookies
- follow_redirects / verify_tls / timeout_seconds
- expected_status_codes / expected_body_regex / expectations

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2：WebAttackContract Contractor 注册

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/web_attack/WebAttackContract.java`
- Create: `veriguard-api/src/main/resources/img/icon-web_attack.png`（占位，从 `icon-manual.png` 复制）

### Steps

- [ ] **Step 2.1：放置占位 icon**

```bash
ls veriguard-api/src/main/resources/img/icon-*.png | head -5
cp veriguard-api/src/main/resources/img/icon-manual.png \
   veriguard-api/src/main/resources/img/icon-web_attack.png
```

正式 icon 在 follow-up 中补充设计师产物。

- [ ] **Step 2.2：创建 WebAttackContract**

```java
package io.veriguard.injectors.web_attack;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;
import static io.veriguard.injector_contract.Contract.executableContract;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractExpectations.expectationsField;
import static io.veriguard.injector_contract.fields.ContractTeam.teamField;
import static io.veriguard.injector_contract.fields.ContractText.textField;
import static io.veriguard.injector_contract.fields.ContractTextArea.textareaField;

import io.veriguard.database.model.Endpoint;
import io.veriguard.helper.SupportedLanguage;
import io.veriguard.injector_contract.Contract;
import io.veriguard.injector_contract.ContractConfig;
import io.veriguard.injector_contract.Contractor;
import io.veriguard.injector_contract.ContractorIcon;
import io.veriguard.injector_contract.fields.ContractElement;
import io.veriguard.rest.domain.enums.PresetDomain;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Contractor that registers the {@code veriguard_web_attack} inject type.
 *
 * <p>HTTP request payload is dispatched to a 协作主机 Agent declaring capability {@code
 * http_attack}. Actual HTTP issuance happens agent-side; this contract defines the platform-side
 * form fields only.
 */
@Component
public class WebAttackContract extends Contractor {

  public static final String TYPE = "veriguard_web_attack";

  public static final String WEB_ATTACK_DEFAULT = "0c89e7d2-3b41-4c5d-8a6e-9f1b2c3d4e5f";

  public static final String CAPABILITY_HTTP_ATTACK = "http_attack";

  private final List<Contract> contracts;

  private final ContractConfig config;

  public WebAttackContract() {
    ContractElement teams = teamField(Multiple);
    ContractElement method = textField("web_request_method", "HTTP method (GET/POST/PUT/...)");
    ContractElement url = textField("web_request_url", "Request URL");
    ContractElement headers = textareaField("web_request_headers", "Headers (JSON array)");
    ContractElement body = textareaField("web_request_body", "Request body");
    ContractElement bodyType =
        textField("web_request_body_type", "Body content type (text/json/form/multipart)");
    ContractElement cookies = textField("web_request_cookies", "Cookies header value");
    ContractElement timeout = textField("web_request_timeout_seconds", "Timeout (seconds, default 30)");
    ContractElement expectedStatus =
        textField("expected_status_codes", "Expected HTTP status codes (CSV, e.g. 200,302)");
    ContractElement expectedBody = textField("expected_body_regex", "Expected body regex match");
    ContractElement expectations = expectationsField();

    Map<SupportedLanguage, String> label = Map.of(en, "Web Attack", fr, "Attaque Web");
    config = new ContractConfig(TYPE, label, "#d32f2f", "#d32f2f", "/img/icon-web_attack.png");

    List<ContractElement> fields =
        contractBuilder()
            .mandatory(teams)
            .mandatory(method)
            .mandatory(url)
            .optional(headers)
            .optional(body)
            .optional(bodyType)
            .optional(cookies)
            .optional(timeout)
            .optional(expectedStatus)
            .optional(expectedBody)
            .optional(expectations)
            .build();

    contracts =
        List.of(
            executableContract(
                config,
                WEB_ATTACK_DEFAULT,
                Map.of(
                    en,
                    "Send HTTP attack via cooperative agent",
                    fr,
                    "Envoyer une attaque HTTP via agent coopératif"),
                fields,
                List.of(Endpoint.PLATFORM_TYPE.Generic),
                false,
                Set.of(PresetDomain.WEB_APP)));
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ContractConfig getConfig() {
    return config;
  }

  @Override
  public List<Contract> contracts() {
    return contracts;
  }

  @Override
  public ContractorIcon getIcon() {
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-web_attack.png");
    return new ContractorIcon(iconStream);
  }
}
```

注：`PresetDomain` 现有 `WEB_APP`，无需新建 domain（spec 隐含 web 攻击属 WEB_APP 类别）。

- [ ] **Step 2.3：编译 + 基线 PR-B 测试**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test -Dtest='MailInjectorTest,SmtpProfileServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: BUILD SUCCESS + PR-B 13 测试不回归.

- [ ] **Step 2.4：Spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/injectors/web_attack/WebAttackContract.java \
        veriguard-api/src/main/resources/img/icon-web_attack.png
git commit -m "$(cat <<'EOF'
执行：WebAttackContract Contractor 注册（B-ii PR-C Step 2）

注册 veriguard_web_attack inject 类型元数据：
- TYPE = "veriguard_web_attack"，默认 contract id 0c89e7d2-...
- 字段定义：teams（执行归属）/ method / url / headers / body / body_type /
  cookies / timeout / expected_status_codes / expected_body_regex / expectations
- 颜色 #d32f2f（红色 = 进攻），占位 icon 复用 manual icon
- domain = WEB_APP
- 暴露 CAPABILITY_HTTP_ATTACK = "http_attack" 常量供后续 service 引用

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：WebAttackDispatchService + 6 TDD 单测

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/web_attack/service/WebAttackDispatchService.java`
- Create: `veriguard-api/src/test/java/io/veriguard/injectors/web_attack/service/WebAttackDispatchServiceTest.java`

### Steps

- [ ] **Step 3.1：写 6 个失败的 TDD 测试**

```java
package io.veriguard.injectors.web_attack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.injectors.web_attack.WebAttackContract;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.service.AgentService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebAttackDispatchServiceTest {

  @Mock private AgentService agentService;

  @InjectMocks private WebAttackDispatchService dispatchService;

  private WebAttackContent validContent() {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("GET");
    c.setUrl("https://example.com/path");
    return c;
  }

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  @Test
  @DisplayName("validateContent: 缺 url → 抛 IllegalArgumentException")
  void validate_missingUrl() {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("GET");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url");
  }

  @Test
  @DisplayName("validateContent: 缺 method → 抛 IllegalArgumentException")
  void validate_missingMethod() {
    WebAttackContent c = new WebAttackContent();
    c.setUrl("https://example.com/");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("method");
  }

  @Test
  @DisplayName("validateContent: method 非法（如 BREW）→ 抛 IllegalArgumentException")
  void validate_invalidMethod() {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("BREW");
    c.setUrl("https://example.com/");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("method");
  }

  @Test
  @DisplayName("selectAgent: capability=http_attack 匹配到 1 个 → 返回该 Agent")
  void select_singleAgentMatch() {
    when(agentService.findByCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK))
        .thenReturn(List.of(agent("a1")));
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("a1");
  }

  @Test
  @DisplayName("selectAgent: 多个 Agent 匹配 → 返回第一个（确定性）")
  void select_multipleAgents() {
    when(agentService.findByCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK))
        .thenReturn(List.of(agent("a1"), agent("a2"), agent("a3")));
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("a1");
  }

  @Test
  @DisplayName("selectAgent: 无匹配 Agent → 返回 Optional.empty")
  void select_noAgent() {
    when(agentService.findByCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK))
        .thenReturn(List.of());
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isEmpty();
  }
}
```

- [ ] **Step 3.2：跑测试验证 RED**

```bash
mvn -pl veriguard-api -am test -Dtest='WebAttackDispatchServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

Expected: 编译失败（WebAttackDispatchService 不存在）.

- [ ] **Step 3.3：实现 WebAttackDispatchService**

```java
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
          "Invalid web_request_method: " + content.getMethod()
              + " (allowed: " + ALLOWED_METHODS + ")");
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
    List<Agent> candidates = agentService.findByCapability(WebAttackContract.CAPABILITY_HTTP_ATTACK);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0));
  }
}
```

- [ ] **Step 3.4：跑测试验证 GREEN**

```bash
mvn -pl veriguard-api -am test -Dtest='WebAttackDispatchServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head
```

Expected: `Tests run: 6, Failures: 0, Errors: 0` + BUILD SUCCESS.

- [ ] **Step 3.5：Spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/injectors/web_attack/service/WebAttackDispatchService.java \
        veriguard-api/src/test/java/io/veriguard/injectors/web_attack/service/WebAttackDispatchServiceTest.java
git commit -m "$(cat <<'EOF'
执行：WebAttackDispatchService + 6 单测（B-ii PR-C Step 3）

服务承担：
- validateContent：method（在 GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS 内）+ url 必填
- selectAgent：调 agentService.findByCapability("http_attack")，多个匹配取首个

6 场景 TDD：缺 url / 缺 method / method 非法 / 单 Agent 匹配 / 多 Agent 匹配 / 无 Agent.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：WebAttackExecutor + 4 TDD 单测

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/web_attack/WebAttackExecutor.java`
- Create: `veriguard-api/src/test/java/io/veriguard/injectors/web_attack/WebAttackExecutorTest.java`

### Steps

- [ ] **Step 4.1：先 recon EmailExecutor 的 contentConvert 调用 + ExecutionTrace 工厂方法**

```bash
grep -nE 'contentConvert|getNewSuccessTrace|getNewErrorTrace' \
  veriguard-api/src/main/java/io/veriguard/injectors/email/EmailExecutor.java \
  veriguard-model/src/main/java/io/veriguard/database/model/ExecutionTrace.java 2>&1 | head -10
```

确认 `NodeExecutor.contentConvert(injection, ContentClass.class)` 和 `ExecutionTrace.getNewSuccessTrace(message, action)` / `getNewErrorTrace(message, action)` 签名.

- [ ] **Step 4.2：写 4 个失败 TDD 测试**

```java
package io.veriguard.injectors.web_attack;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Execution;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.service.AttackChainNodeExpectationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebAttackExecutorTest {

  @Mock private NodeExecutorContext context;
  @Mock private WebAttackDispatchService dispatchService;
  @Mock private AttackChainNodeExpectationService attackChainNodeExpectationService;
  @Mock private Execution execution;
  @Mock private ExecutableNode injection;

  private WebAttackExecutor newExecutor() {
    return new WebAttackExecutor(context, dispatchService, attackChainNodeExpectationService);
  }

  private WebAttackContent validContent() {
    WebAttackContent c = new WebAttackContent();
    c.setMethod("GET");
    c.setUrl("https://example.com/");
    return c;
  }

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  @Test
  @DisplayName("process: validate 通过 + Agent 找到 → success trace + ExecutionProcess(false)")
  void process_dispatchSuccess() throws Exception {
    WebAttackExecutor executor = newExecutor();
    WebAttackContent c = validContent();
    // contentConvert is final on superclass; stub via spy is overkill — we wire content through a
    // protected hook. For this test we'll use a spy:
    WebAttackExecutor spied = org.mockito.Mockito.spy(executor);
    org.mockito.Mockito.doReturn(c).when(spied).convertContent(injection);

    doNothing().when(dispatchService).validateContent(c);
    when(dispatchService.selectAgent()).thenReturn(Optional.of(agent("a1")));

    ExecutionProcess result = spied.process(execution, injection);

    org.assertj.core.api.Assertions.assertThat(result.isAsync()).isFalse();
    verify(dispatchService).validateContent(c);
    verify(dispatchService).selectAgent();
  }

  @Test
  @DisplayName("process: 无可用 Agent → error trace + ExecutionProcess(false)")
  void process_noAgent() throws Exception {
    WebAttackExecutor executor = newExecutor();
    WebAttackExecutor spied = org.mockito.Mockito.spy(executor);
    WebAttackContent c = validContent();
    org.mockito.Mockito.doReturn(c).when(spied).convertContent(injection);

    doNothing().when(dispatchService).validateContent(c);
    when(dispatchService.selectAgent()).thenReturn(Optional.empty());

    ExecutionProcess result = spied.process(execution, injection);

    org.assertj.core.api.Assertions.assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（缺 url）→ error trace")
  void process_validateFailure_missingUrl() throws Exception {
    WebAttackExecutor executor = newExecutor();
    WebAttackExecutor spied = org.mockito.Mockito.spy(executor);
    WebAttackContent c = new WebAttackContent();
    c.setMethod("GET");
    // no url
    org.mockito.Mockito.doReturn(c).when(spied).convertContent(injection);

    org.mockito.Mockito.doThrow(new IllegalArgumentException("web_request_url is required"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    org.assertj.core.api.Assertions.assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（method 非法）→ error trace")
  void process_validateFailure_invalidMethod() throws Exception {
    WebAttackExecutor executor = newExecutor();
    WebAttackExecutor spied = org.mockito.Mockito.spy(executor);
    WebAttackContent c = new WebAttackContent();
    c.setMethod("BREW");
    c.setUrl("https://example.com/");
    org.mockito.Mockito.doReturn(c).when(spied).convertContent(injection);

    org.mockito.Mockito.doThrow(new IllegalArgumentException("Invalid web_request_method: BREW"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    org.assertj.core.api.Assertions.assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }
}
```

注：`contentConvert` 是父类 NodeExecutor 的 final / protected 方法；为了可测试，子类暴露一个**protected hook** `convertContent(ExecutableNode)` 包装它，让测试用 spy 替换。实现在 Step 4.3.

- [ ] **Step 4.3：跑测试验证 RED**

```bash
mvn -pl veriguard-api -am test -Dtest='WebAttackExecutorTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

Expected: 编译失败（WebAttackExecutor 不存在）.

- [ ] **Step 4.4：实现 WebAttackExecutor**

```java
package io.veriguard.injectors.web_attack;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.database.model.ExecutionTrace.getNewSuccessTrace;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.web_attack.model.WebAttackContent;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.model.Expectation;
import io.veriguard.model.expectation.ManualExpectation;
import io.veriguard.service.AttackChainNodeExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * NodeExecutor 子类，处理 Web 攻击包 inject（B-ii PR-C）.
 *
 * <p>process() 流程：
 *
 * <ol>
 *   <li>反序列化 inject_content → WebAttackContent
 *   <li>校验内容（method 合法 + url 非空）
 *   <li>选有 http_attack 能力的协作主机 Agent
 *   <li>记 dispatch trace（success: 含 agent id + url；error: 无 agent / 校验失败）
 *   <li>保存 ManualExpectation
 *   <li>返回 ExecutionProcess(false) —— 不等待 Agent 异步回填（本 PR 范围外）
 * </ol>
 */
@Slf4j
public class WebAttackExecutor extends NodeExecutor {

  private final WebAttackDispatchService dispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public WebAttackExecutor(
      NodeExecutorContext context,
      WebAttackDispatchService dispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(context);
    this.dispatchService = dispatchService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  /**
   * Protected hook that wraps {@code contentConvert} so tests can spy and inject a stub content.
   *
   * <p>Production code path: delegates straight to the parent {@code contentConvert}.
   */
  protected WebAttackContent convertContent(ExecutableNode injection) throws Exception {
    return contentConvert(injection, WebAttackContent.class);
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableNode injection) throws Exception {

    WebAttackContent content;
    try {
      content = convertContent(injection);
    } catch (Exception e) {
      log.warn("Failed to deserialize WebAttackContent: {}", e.getMessage());
      execution.addTrace(
          getNewErrorTrace(
              "Invalid web_attack content: " + e.getMessage(), ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }

    // 1) Validate (throws IllegalArgumentException for bad content)
    try {
      dispatchService.validateContent(content);
    } catch (IllegalArgumentException e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }

    // 2) Pick agent
    Optional<Agent> agentOpt = dispatchService.selectAgent();
    if (agentOpt.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "No agent available with capability '"
                  + WebAttackContract.CAPABILITY_HTTP_ATTACK
                  + "'; deploy a 协作主机 Agent to execute web attack injects",
              ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }
    Agent agent = agentOpt.get();

    // 3) Save ManualExpectation entries (response 自动判定 in follow-up)
    List<Expectation> expectations =
        content.getExpectations().stream()
            .flatMap(
                entry ->
                    switch (entry.getType()) {
                      case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                      default -> Stream.of();
                    })
            .toList();
    attackChainNodeExpectationService.buildAndSaveAttackChainNodeExpectations(
        injection, expectations);

    // 4) Dispatch trace
    execution.addTrace(
        getNewSuccessTrace(
            "Web attack dispatched to agent " + agent.getId()
                + " (" + content.getMethod() + " " + content.getUrl() + ")",
            ExecutionTraceAction.COMPLETE));

    return new ExecutionProcess(false);
  }
}
```

- [ ] **Step 4.5：跑测试验证 GREEN**

```bash
mvn -pl veriguard-api -am test -Dtest='WebAttackExecutorTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head
```

Expected: `Tests run: 4, Failures: 0, Errors: 0` + BUILD SUCCESS.

- [ ] **Step 4.6：Spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/injectors/web_attack/WebAttackExecutor.java \
        veriguard-api/src/test/java/io/veriguard/injectors/web_attack/WebAttackExecutorTest.java
git commit -m "$(cat <<'EOF'
执行：WebAttackExecutor 实现 + 4 单测（B-ii PR-C Step 4）

NodeExecutor 子类，process 流程：
- 反序列化 WebAttackContent
- 委托 WebAttackDispatchService 校验 + 选 Agent
- 写 dispatch trace（success: 含 agent + method + url；error: 缺 Agent / 校验失败 / 反序列化失败）
- 保存 ManualExpectation
- 返回 ExecutionProcess(false)

注：本 PR 不等待 Agent 异步结果，真实 HTTP 执行 + 响应判定在协作主机 Agent 客户端项目落地后补 follow-up PR.

protected convertContent(...) hook 让单测可 spy 注入 stub content.

4 场景 TDD：dispatch 成功 / 无 Agent / 缺 url / 非法 method.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5：WebAttackNodeExecutorIntegration + Factory

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/web_attack/WebAttackNodeExecutorIntegration.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/web_attack/WebAttackNodeExecutorIntegrationFactory.java`

### Steps

- [ ] **Step 5.1：读 EmailNodeExecutorIntegration + Factory 作为模板**

```bash
cat veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailNodeExecutorIntegration.java
cat veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailNodeExecutorIntegrationFactory.java
```

- [ ] **Step 5.2：创建 WebAttackNodeExecutorIntegration**

```java
package io.veriguard.integration.impl.injectors.web_attack;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.web_attack.WebAttackContract;
import io.veriguard.injectors.web_attack.WebAttackExecutor;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class WebAttackNodeExecutorIntegration extends IntegrationInMemory {

  public static final String WEB_ATTACK_INJECTOR_ID = "31a5b8e3-8c4f-4d6b-c8fa-e9f2b3c4d5e6";
  private static final String WEB_ATTACK_INJECTOR_NAME = "Web Attack";

  private final WebAttackContract webAttackContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final NodeExecutorService nodeExecutorService;
  private final WebAttackDispatchService webAttackDispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  @QualifiedComponent(identifier = {WebAttackContract.TYPE, WEB_ATTACK_INJECTOR_ID})
  private WebAttackExecutor webAttackExecutor;

  public WebAttackNodeExecutorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      WebAttackContract webAttackContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      WebAttackDispatchService webAttackDispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.webAttackContract = webAttackContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.webAttackDispatchService = webAttackDispatchService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    nodeExecutorService.registerBuiltinNodeExecutor(
        WEB_ATTACK_INJECTOR_ID,
        WEB_ATTACK_INJECTOR_NAME,
        webAttackContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.webAttackExecutor =
        new WebAttackExecutor(
            nodeExecutorContext, webAttackDispatchService, attackChainNodeExpectationService);
  }

  @Override
  protected void innerStop() {
    // not possible to stop this integration
  }
}
```

- [ ] **Step 5.3：创建 WebAttackNodeExecutorIntegrationFactory**

```java
package io.veriguard.integration.impl.injectors.web_attack;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.web_attack.WebAttackContract;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WebAttackNodeExecutorIntegrationFactory extends IntegrationFactory {

  private final WebAttackContract webAttackContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final ConnectorInstanceService connectorInstanceService;
  private final NodeExecutorService nodeExecutorService;
  private final WebAttackDispatchService webAttackDispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final ComponentRequestEngine componentRequestEngine;

  public WebAttackNodeExecutorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      WebAttackContract webAttackContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      WebAttackDispatchService webAttackDispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.webAttackContract = webAttackContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.webAttackDispatchService = webAttackDispatchService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            WebAttackNodeExecutorIntegration.WEB_ATTACK_INJECTOR_ID,
            this.getClassName(),
            ConnectorType.INJECTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new WebAttackNodeExecutorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        webAttackContract,
        nodeExecutorContext,
        nodeExecutorService,
        webAttackDispatchService,
        attackChainNodeExpectationService);
  }
}
```

- [ ] **Step 5.4：编译 + 全部 PR-C 测试 + PR-B 回归测试**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test \
  -Dtest='WebAttackDispatchServiceTest,WebAttackExecutorTest,MailInjectorTest,SmtpProfileServiceTest,LinkExpectationServiceTest,AgentServiceSelectByCapabilityTest' \
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | tail
```

Expected: BUILD SUCCESS + 6 + 4 + 6 + 7 + 18 + 8 = 49 测试过.

- [ ] **Step 5.5：Spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/web_attack/
git commit -m "$(cat <<'EOF'
执行：WebAttackNodeExecutorIntegration + Factory（B-ii PR-C Step 5）

参考 EmailNodeExecutorIntegration 模式注册 veriguard_web_attack inject 类型到
NodeExecutorService：
- WEB_ATTACK_INJECTOR_ID 固定 UUID（31a5b8e3-...），用于 ConnectorInstance autostart
- innerStart 调 nodeExecutorService.registerBuiltinNodeExecutor 注册 contract
- @QualifiedComponent 标记 WebAttackExecutor 实例
- Factory @Service 提供 spawn + findRelatedInstances 给 autostart 机制使用

启动后 veriguard_web_attack inject 类型对前端可见，用户可选 inject 并填 method/url/headers/body.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6：前端 inject 编辑器手工冒烟（不写代码）

**Files:** 无新增/修改 — 前端通过 InjectorContract field schema 自动渲染表单 + canvas 节点 icon 自动走 `/api/images/injectors/veriguard_web_attack`。

### Steps

- [ ] **Step 6.1：启 dev 环境**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-c-web-attack
cd veriguard-dev && docker compose up -d
cd .. && export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am spring-boot:run &
```

等启动完成 (`http://localhost:8080` 可访问).

- [ ] **Step 6.2：前端启动 + 验证**

```bash
cd veriguard-front
yarn install --immutable
yarn check-ts 2>&1 | tail -3
yarn start &
```

打开浏览器 `http://localhost:5173/admin/attack_chains`：
1. 新建攻击编排链路
2. 添加节点 → inject 类型下拉应能看到 **"Web Attack"** 选项（伴 `#d32f2f` 红色 + icon）
3. 选中后表单字段应自动出现：method / url / headers / body / body_type / cookies / timeout / expected_status_codes / expected_body_regex / teams / expectations

如果 inject 类型列表里看不到 Web Attack，可能 connector_instance 没自动启动 → 检查 `mvn spring-boot:run` 日志是否含 `WebAttackNodeExecutorIntegrationFactory` startup line.

- [ ] **Step 6.3：API 层 smoke test**

```bash
# 列所有 injector contract types，应含 veriguard_web_attack
curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" \
  http://localhost:8080/api/injectors_contracts | jq '[.[] | select(.injector_contract_labels.en | contains("Web Attack"))]'
```

- [ ] **Step 6.4：清理 dev 环境**

```bash
# Ctrl+C the spring-boot:run + yarn start processes
cd veriguard-dev && docker compose down
```

**本步无 commit**（无代码改动；冒烟只是验证 Task 1-5 落地正常）.

---

## Task 7：验证 + push + 创建 PR

### Steps

- [ ] **Step 7.1：全 PR-C + PR-B + PR-A 测试不回归**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-c-web-attack
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am test \
  -Dtest='WebAttackDispatchServiceTest,WebAttackExecutorTest,SmtpProfileServiceTest,MailInjectorTest,LinkExpectationServiceTest,AgentServiceSelectByCapabilityTest' \
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | tail
```

Expected: 全 BUILD SUCCESS / 6 + 4 + 7 + 6 + 18 + 8 = 49 测试过.

- [ ] **Step 7.2：前端 check-ts 不回归**

```bash
cd veriguard-front
yarn check-ts 2>&1 | tail -3
```

Expected: 0 errors.

- [ ] **Step 7.3：master 锁验证**

```bash
git -C /Users/lamba/github/Veriguard ls-remote origin master | head -1
```

Expected: `5d7e05da627639491274175d2a77cf95b2c0fe7d	refs/heads/master`.

- [ ] **Step 7.4：push branch**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-c-web-attack
git push -u origin feat/b-ii-pr-c-web-attack 2>&1 | tail -3
```

- [ ] **Step 7.5：创建 PR**

```bash
gh pr create --base main --title "二开 B-ii PR-C: Web 攻击包 Inject + 协作主机 Agent capability 派发" --body "$(cat <<'EOF'
## Summary

B-ii sub-project 第 3 个 PR（独立，可与 PR-D 并行）：落地 Web 攻击包 inject 类型，覆盖 PRD §2.3 第 3 行 6 类自定义用例中的「构造 web 攻击包」。

- 设计稿：\`docs/superpowers/specs/2026-05-12-veriguard-b-ii-design.md\`（§5 Web 攻击包 Inject 详细设计）
- 实施计划：\`docs/superpowers/plans/2026-05-12-veriguard-b-ii-pr-c-web-attack-plan.md\`

## 改动概览

### 后端（5 commits）

- **WebAttackContent + WebRequestHeader**：JSONB POJO（method/url/headers(List<KV>)/body/body_type/cookies/follow_redirects/verify_tls/timeout/expected_status_codes/expected_body_regex/expectations）
- **WebAttackContract**：注册 \`veriguard_web_attack\` inject 类型 + 11 个字段（teams/method/url 必填，余可选）+ \`CAPABILITY_HTTP_ATTACK = "http_attack"\` 常量
- **WebAttackDispatchService + 6 TDD 单测**：method 校验（GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS）+ url 必填 + selectAgent 走 \`agentService.findByCapability("http_attack")\`
- **WebAttackExecutor + 4 TDD 单测**：NodeExecutor 子类，process 流程 = convertContent → validate → selectAgent → dispatch trace → 保存 ManualExpectation → ExecutionProcess(false)
- **WebAttackNodeExecutorIntegration + Factory**：autostart 注册 inject 类型到 NodeExecutorService

### 不在本 PR 范围（独立 follow-up）

- 协作主机 Agent **客户端代码**（独立项目，fork OpenAEV-Platform/agent）
- Agent 真实 HTTP 请求执行 + 响应抓取
- Agent 结果回填 endpoint（待 agent 项目落地后加 \`POST /api/inject/{id}/web-attack-result\`）
- 响应特征自动判定（status code / body regex / timeout 评估）
- 请求序列模式（spec §5.2 多步骤 chain）
- 边界用例数据集自动导入（\`datasets/nuclei-templates\` + \`coreruleset\` 批量 → \`veriguard_web_attack\` inject contract，独立子项目）

## 设计要点

- **不新建 PayloadType**：HTTP 请求细节走 inject_content JSONB（WebAttackContent POJO），与现有 5 种 PayloadType 正交，遵循 PR-B 邮件 inject 同款模式.
- **简化派发**：本 PR 仅完成"找到协作主机 Agent + 写 dispatch trace"两步；真实 HTTP 执行依赖协作主机 Agent 客户端实现（独立项目）.
- **协作主机 Agent 选择**：复用 PR-A 的 \`AgentService.findByCapability("http_attack")\`，多 Agent 匹配取首个（确定性）.
- **杠 master 锁**：master 仍锁在 \`5d7e05da6\`；PR base = main.

## Test plan

- [x] \`mvn -pl veriguard-api -am test -Dtest='WebAttackDispatchServiceTest,WebAttackExecutorTest,SmtpProfileServiceTest,MailInjectorTest,LinkExpectationServiceTest,AgentServiceSelectByCapabilityTest'\` → 49/49 PASS
- [x] \`yarn check-ts\` → 0 errors
- [x] 启动 dev 环境验证 inject 编辑器下拉含 "Web Attack" 类型，表单字段渲染齐全
- [ ] Manual smoke：注册一个测试 Agent（agent_capabilities=["http_attack"]）+ 触发 web_attack inject → trace 应记 dispatched
- [ ] Real attack：依赖协作主机 Agent 项目落地后补做

## 范围 boundary（spec §5 + PR splitting）

- 邮件接收 / IMAP 验证、响应判定 → 协作主机 Agent 项目
- 请求序列模式 → follow-up
- 边界 12 类用例库自动导入 → 独立子项目

## 后续

- PR-D：pcap inject + Agent tcpreplay 能力（独立 PR，仅依赖 PR-A）
- 协作主机 Agent 客户端（fork OpenAEV-Platform/agent，加 http_attack + pcap_replay 能力声明）
- Web 攻击包数据集批量导入（\`datasets/nuclei-templates\` + \`coreruleset\` → \`veriguard_web_attack\`）
EOF
)"
```

---

## Summary

| Task | 后端文件 | 单测 | Commits |
|---|---|---:|---:|
| 1. WebAttackContent + WebRequestHeader | 2 创建 | 0 | 1 |
| 2. WebAttackContract + icon 占位 | 1 创建 + 1 resource | 0 | 1 |
| 3. WebAttackDispatchService | 1 创建 + 1 测试 | 6 | 1 |
| 4. WebAttackExecutor | 1 创建 + 1 测试 | 4 | 1 |
| 5. WebAttackNodeExecutorIntegration + Factory | 2 创建 | 0 | 1 |
| 6. 前端冒烟 | 0 | 0 | 0 |
| 7. 验证 + push + PR | 0 | 0 | 0 |
| **合计** | **7 创建（5 java + 1 java 测试 + 1 png）** | **10 测试** | **5 实施 commits + 1 plan + 1 PR** |

## 自审 checklist

**1. Spec coverage:**

- ✅ §5.1 用例契约：所有 9 字段在 WebAttackContent + WebAttackContract（method / url / headers / body / body_type / cookies / follow_redirects / verify_tls / timeout）
- ✅ §5.5 期望评估：expected_status_codes + expected_body_regex 已加 content；自动判定逻辑在 follow-up
- ⚠️ §5.2 请求序列：留 follow-up（本 PR 单请求模式）
- ⚠️ §5.4 执行时序：本 PR 仅完成第 1 步"平台→Agent 下发"中的 dispatch trace 部分；2-6 步依赖 Agent 客户端
- ✅ §5.6 Agent HTTP 客户端能力：声明在 Agent 项目中，本 PR 只用 capability 标签匹配
- ✅ §5.7 安全约束：white-list method 已实现；目标白名单 / 审计日志在 Agent 项目
- ✅ §3.2 inject 与攻击源映射："Web 攻击包 → 协作主机 Agent HTTP 客户端" 通过 capability 匹配落地

**2. Placeholder scan:**

- ✅ 无 TBD / TODO / "implement later"
- ✅ 每个代码 step 都含完整可粘贴的源码
- ✅ 所有 mvn / git 命令含 expected output 说明

**3. Type consistency:**

- ✅ `WebAttackContent` 在 Task 1 定义，Task 4 / 5 使用一致
- ✅ `WebAttackDispatchService.validateContent(WebAttackContent)` + `selectAgent(): Optional<Agent>` 在 Task 3 定义，Task 4 使用一致
- ✅ `WebAttackExecutor.convertContent(ExecutableNode): WebAttackContent` 在 Task 4 定义并被测试 spy 替换
- ✅ `WebAttackContract.TYPE = "veriguard_web_attack"` + `CAPABILITY_HTTP_ATTACK = "http_attack"` 在 Task 2 定义，Task 3 / 5 引用一致
- ✅ Flyway 版本号未冲突（本 PR 不新增 migration）
