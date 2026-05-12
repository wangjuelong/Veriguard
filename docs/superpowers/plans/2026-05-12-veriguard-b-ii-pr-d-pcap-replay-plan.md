# B-ii PR-D：pcap 回放 Inject Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `veriguard_pcap_replay` inject type，让用户在攻击编排链路上配置 pcap 回放用例（pcap_file_id / interface / replay mode），平台通过 `agent_capabilities="pcap_replay"` 选择协作主机 Agent 派发执行 tcpreplay。

**Architecture:**
1. **不新建 PayloadType**——遵循 PR-C 设计哲学：pcap 回放参数存 `inject_content` JSONB（`PcapReplayContent` POJO）；不引入新 PayloadType，与 PR-B 邮件 / PR-C web_attack 一致.
2. **简化派发模型**——`PcapReplayExecutor` 通过 `agentService.selectAgentsForCapability("pcap_replay")` 选协作主机 Agent，写入 dispatch trace + `ExecutionProcess(false)` 即返回。**真实 tcpreplay 执行、结果回填、协作主机 Agent 客户端代码均在本 PR 范围外**.
3. **pcap_file_id 只是字符串字段**——本 PR 不引入文件上传流程；待独立子项目落地 MinIO 文件管理后再加 Document FK 强约束.

**Tech Stack:** Spring Boot 3.3.7 / Java 21 / JPA / Hibernate JsonType / Jackson / Mockito（@ExtendWith(MockitoExtension.class)）/ Lombok.

---

## 准备工作

- **Worktree**: `/Users/lamba/github/Veriguard/worktrees/b-ii-pr-d-pcap-replay`，base=main，HEAD=`f12c47e08` (PR-C merge).
- **Java 21**: 所有 `mvn` 命令前需 `export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`，否则 Mockito 在 Java 25 上会大批失败。
- **Master 锁**: `5d7e05da6` 永不动；PR base=main.
- **AI commit trailer**: 每个 commit 末尾 `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- **中文 commit 风格**: `执行：...` / `修复：...` / `设计：...`.
- **Commit 写法**: 必须用 `cat > /tmp/<task>.txt <<'EOF' ... EOF` + `git commit -F /tmp/<task>.txt`，**禁用 heredoc 内嵌 `git commit -m "$(cat...)"`**（会触发 block-no-verify hook 误判）.
- **依赖前置（已在 main）**:
  - PR-A `agent_capabilities` JSONB 列（V7）+ `AgentService.selectAgentsForCapability(String)` 已实现.
  - PR-B 邮件 inject 完整模板 → `veriguard-api/src/main/java/io/veriguard/injectors/email/`.
  - PR-C web_attack inject 完整模板 → `veriguard-api/src/main/java/io/veriguard/injectors/web_attack/` + `integration/impl/injectors/web_attack/`，与 PR-D 结构 1:1 对应（仅字段语义不同）.
- **Flyway**: 下一个版本 V10（V9 已被 injectors_contracts_field_extension 占用），但本 PR 不引入 schema 变更，跳过 V10 migration.

---

## 文件结构

### 后端 (`veriguard-api`)

```
src/main/java/io/veriguard/injectors/pcap_replay/
  PcapReplayContract.java                     # Contractor 子类：注册 inject 类型 + 字段
  PcapReplayExecutor.java                      # NodeExecutor 子类：派发到 Agent
  model/
    PcapReplayContent.java                     # JSONB POJO：pcap_file_id/interface/mode/rate
  service/
    PcapReplayDispatchService.java             # 选 Agent + 校验 mode 合法性

src/main/java/io/veriguard/integration/impl/injectors/pcap_replay/
  PcapReplayNodeExecutorIntegration.java       # autostart + 注册 NodeExecutor
  PcapReplayNodeExecutorIntegrationFactory.java # @Service Factory（被 connector instance autostart 选中）

src/main/resources/img/
  icon-pcap_replay.png                          # 占位 icon（复用 icon-manual.png 临时用）

src/test/java/io/veriguard/injectors/pcap_replay/
  service/PcapReplayDispatchServiceTest.java   # 6 TDD 单测
  PcapReplayExecutorTest.java                   # 4 TDD 单测
```

### 前端 (`veriguard-front`)

无需新增文件——`/api/images/injectors/veriguard_pcap_replay` 通用 endpoint 自动从 Contractor 的 `getIcon()` 取 `icon-pcap_replay.png`；inject 编辑器通过 contract 字段 schema 自动生成表单（参考 PR-B / PR-C 同款机制）。

---

## Task 1：PcapReplayContent 数据模型

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/model/PcapReplayContent.java`

### Steps

- [ ] **Step 1.1：创建 PcapReplayContent POJO**

```java
package io.veriguard.injectors.pcap_replay.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.model.attack_chain_node.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * POJO mirroring {@code inject_content} JSONB for the {@code veriguard_pcap_replay} inject type.
 *
 * <p>Field names mirror spec §6.1 wire format (snake_case).
 */
@Getter
@Setter
public class PcapReplayContent {

  @JsonProperty("pcap_file_id")
  private String pcapFileId;

  @JsonProperty("pcap_target_interface")
  private String targetInterface;

  @JsonProperty("pcap_replay_mode")
  private String replayMode;

  @JsonProperty("pcap_replay_rate")
  private Double replayRate;

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
```

- [ ] **Step 1.2：编译验证**

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

如 `Expectation` import 路径不同，参考 `veriguard-api/src/main/java/io/veriguard/injectors/email/model/EmailContent.java` 同款 (`io.veriguard.model.attack_chain_node.form.Expectation`).

- [ ] **Step 1.3：Spotless + commit**

```bash
mvn spotless:apply -q

cat > /tmp/pr-d-task1-msg.txt <<'EOF'
执行：PcapReplayContent 数据模型（B-ii PR-D Step 1）

JSONB 反序列化目标 POJO，字段名遵循 spec §6.1 snake_case wire 约定：
- pcap_file_id（关联的 pcap 文件 ID，本 PR 仅字符串引用，文件上传独立项目）
- pcap_target_interface（协作主机回放使用的网卡名）
- pcap_replay_mode（速率模式：ORIGINAL/MBPS/PPS/MULTIPLIER/TOPSPEED）
- pcap_replay_rate（速率参数，按 mode 语义；ORIGINAL/TOPSPEED 忽略此字段）
- expectations

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF

git add veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/model/PcapReplayContent.java
git commit -F /tmp/pr-d-task1-msg.txt 2>&1 | tail -3
```

---

## Task 2：PcapReplayContract Contractor 注册

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/PcapReplayContract.java`
- Create: `veriguard-api/src/main/resources/img/icon-pcap_replay.png`（占位，从 `icon-manual.png` 复制）

### Steps

- [ ] **Step 2.1：放置占位 icon**

```bash
cp veriguard-api/src/main/resources/img/icon-manual.png \
   veriguard-api/src/main/resources/img/icon-pcap_replay.png
```

正式 icon 在 follow-up 中补充。

- [ ] **Step 2.2：创建 PcapReplayContract**

```java
package io.veriguard.injectors.pcap_replay;

import static io.veriguard.helper.SupportedLanguage.en;
import static io.veriguard.helper.SupportedLanguage.fr;
import static io.veriguard.injector_contract.Contract.executableContract;
import static io.veriguard.injector_contract.ContractCardinality.Multiple;
import static io.veriguard.injector_contract.ContractDef.contractBuilder;
import static io.veriguard.injector_contract.fields.ContractExpectations.expectationsField;
import static io.veriguard.injector_contract.fields.ContractTeam.teamField;
import static io.veriguard.injector_contract.fields.ContractText.textField;

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
 * Contractor that registers the {@code veriguard_pcap_replay} inject type.
 *
 * <p>pcap replay parameters are dispatched to a 协作主机 Agent declaring capability {@code
 * pcap_replay}. Actual tcpreplay execution happens agent-side; this contract defines the
 * platform-side form fields only.
 */
@Component
public class PcapReplayContract extends Contractor {

  public static final String TYPE = "veriguard_pcap_replay";

  public static final String PCAP_REPLAY_DEFAULT = "1d9af8e3-4c52-4e6d-9b7a-fa2c3b4d5e6f";

  public static final String CAPABILITY_PCAP_REPLAY = "pcap_replay";

  private final List<Contract> contracts;

  private final ContractConfig config;

  public PcapReplayContract() {
    ContractElement teams = teamField(Multiple);
    ContractElement pcapFileId = textField("pcap_file_id", "pcap file id (uploaded asset)");
    ContractElement targetInterface =
        textField("pcap_target_interface", "Network interface (e.g. eth0)");
    ContractElement replayMode =
        textField(
            "pcap_replay_mode", "Replay mode: ORIGINAL / MBPS / PPS / MULTIPLIER / TOPSPEED");
    ContractElement replayRate =
        textField("pcap_replay_rate", "Rate value (Mbps / pps / multiplier; ignored for ORIGINAL/TOPSPEED)");
    ContractElement expectations = expectationsField();

    Map<SupportedLanguage, String> label = Map.of(en, "PCAP Replay", fr, "Rejeu PCAP");
    config = new ContractConfig(TYPE, label, "#009933", "#009933", "/img/icon-pcap_replay.png");

    List<ContractElement> fields =
        contractBuilder()
            .mandatory(teams)
            .mandatory(pcapFileId)
            .mandatory(targetInterface)
            .mandatory(replayMode)
            .optional(replayRate)
            .optional(expectations)
            .build();

    contracts =
        List.of(
            executableContract(
                config,
                PCAP_REPLAY_DEFAULT,
                Map.of(
                    en,
                    "Replay pcap traffic via cooperative agent (tcpreplay)",
                    fr,
                    "Rejouer le pcap via l'agent coopératif (tcpreplay)"),
                fields,
                List.of(Endpoint.PLATFORM_TYPE.Generic),
                false,
                Set.of(PresetDomain.NETWORK)));
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
    InputStream iconStream = getClass().getResourceAsStream("/img/icon-pcap_replay.png");
    return new ContractorIcon(iconStream);
  }
}
```

注：颜色 `#009933` 对应 `PresetDomain.NETWORK` 的官方绿色，标识该 inject 属流量侧.

- [ ] **Step 2.3：编译 + PR-B/PR-C 回归测试**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test \
  -Dtest='MailInjectorTest,SmtpProfileServiceTest,WebAttackDispatchServiceTest,WebAttackExecutorTest' \
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: BUILD SUCCESS + 6 + 7 + 6 + 4 = 23 tests pass.

- [ ] **Step 2.4：Spotless + commit**

```bash
mvn spotless:apply -q

cat > /tmp/pr-d-task2-msg.txt <<'EOF'
执行：PcapReplayContract Contractor 注册（B-ii PR-D Step 2）

注册 veriguard_pcap_replay inject 类型元数据：
- TYPE = "veriguard_pcap_replay"，默认 contract id 1d9af8e3-4c52-4e6d-9b7a-fa2c3b4d5e6f
- 字段定义：teams（执行归属）/ pcap_file_id / pcap_target_interface /
  pcap_replay_mode（必填）/ pcap_replay_rate（可选）/ expectations
- 颜色 #009933（绿色 = NETWORK domain），占位 icon 复用 manual icon
- domain = NETWORK
- 暴露 CAPABILITY_PCAP_REPLAY = "pcap_replay" 常量供后续 service 引用

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF

git add veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/PcapReplayContract.java \
        veriguard-api/src/main/resources/img/icon-pcap_replay.png
git status -s
git commit -F /tmp/pr-d-task2-msg.txt 2>&1 | tail -3
```

---

## Task 3：PcapReplayDispatchService + 6 TDD 单测

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/service/PcapReplayDispatchService.java`
- Create: `veriguard-api/src/test/java/io/veriguard/injectors/pcap_replay/service/PcapReplayDispatchServiceTest.java`

### Steps

- [ ] **Step 3.1：写 6 个失败的 TDD 测试**

```java
package io.veriguard.injectors.pcap_replay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
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
class PcapReplayDispatchServiceTest {

  @Mock private AgentService agentService;

  @InjectMocks private PcapReplayDispatchService dispatchService;

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  private PcapReplayContent validContent() {
    PcapReplayContent c = new PcapReplayContent();
    c.setPcapFileId("pcap-abc");
    c.setTargetInterface("eth0");
    c.setReplayMode("ORIGINAL");
    return c;
  }

  @Test
  @DisplayName("validateContent: 缺 pcap_file_id → 抛 IllegalArgumentException")
  void validate_missingPcapFile() {
    PcapReplayContent c = new PcapReplayContent();
    c.setTargetInterface("eth0");
    c.setReplayMode("ORIGINAL");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pcap_file_id");
  }

  @Test
  @DisplayName("validateContent: 缺 target_interface → 抛 IllegalArgumentException")
  void validate_missingInterface() {
    PcapReplayContent c = new PcapReplayContent();
    c.setPcapFileId("pcap-abc");
    c.setReplayMode("ORIGINAL");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("interface");
  }

  @Test
  @DisplayName("validateContent: mode 非法（如 LIGHTSPEED）→ 抛 IllegalArgumentException")
  void validate_invalidMode() {
    PcapReplayContent c = validContent();
    c.setReplayMode("LIGHTSPEED");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mode");
  }

  @Test
  @DisplayName("validateContent: MBPS 模式但无 rate → 抛 IllegalArgumentException")
  void validate_mbpsMissingRate() {
    PcapReplayContent c = validContent();
    c.setReplayMode("MBPS");
    assertThatThrownBy(() -> dispatchService.validateContent(c))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rate");
  }

  @Test
  @DisplayName("selectAgent: 多个 Agent 匹配 → 返回第一个（确定性）")
  void select_multipleAgents() {
    when(agentService.selectAgentsForCapability(PcapReplayContract.CAPABILITY_PCAP_REPLAY))
        .thenReturn(List.of(agent("a1"), agent("a2")));
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo("a1");
  }

  @Test
  @DisplayName("selectAgent: 无匹配 Agent → 返回 Optional.empty")
  void select_noAgent() {
    when(agentService.selectAgentsForCapability(PcapReplayContract.CAPABILITY_PCAP_REPLAY))
        .thenReturn(List.of());
    Optional<Agent> result = dispatchService.selectAgent();
    assertThat(result).isEmpty();
  }
}
```

- [ ] **Step 3.2：跑测试验证 RED**

```bash
mvn -pl veriguard-api -am test -Dtest='PcapReplayDispatchServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

Expected: 编译失败（PcapReplayDispatchService 不存在）.

- [ ] **Step 3.3：实现 PcapReplayDispatchService**

```java
package io.veriguard.injectors.pcap_replay.service;

import io.veriguard.database.model.Agent;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.service.AgentService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选择有 {@code pcap_replay} 能力的协作主机 Agent，并校验 pcap 回放 inject 内容.
 *
 * <p>本 PR 不发起真实 tcpreplay；agent 客户端独立项目落地后由 agent 侧完成 tcpreplay 执行 + 结果回填.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PcapReplayDispatchService {

  /** Modes that REQUIRE a {@code pcap_replay_rate} value to be present. */
  private static final Set<String> MODES_REQUIRING_RATE = Set.of("MBPS", "PPS", "MULTIPLIER");

  /** All allowed {@code pcap_replay_mode} values (spec §6.2). */
  private static final Set<String> ALLOWED_MODES =
      Set.of("ORIGINAL", "MBPS", "PPS", "MULTIPLIER", "TOPSPEED");

  private final AgentService agentService;

  /**
   * 校验 pcap_replay 内容必填字段 + mode 合法性 + mode/rate 一致性.
   *
   * @throws IllegalArgumentException 字段缺失、mode 不在允许集合，或 MBPS/PPS/MULTIPLIER 模式下缺 rate
   */
  public void validateContent(PcapReplayContent content) {
    if (content.getPcapFileId() == null || content.getPcapFileId().isBlank()) {
      throw new IllegalArgumentException("pcap_file_id is required");
    }
    if (content.getTargetInterface() == null || content.getTargetInterface().isBlank()) {
      throw new IllegalArgumentException("pcap_target_interface is required");
    }
    String mode = content.getReplayMode();
    if (mode == null || mode.isBlank()) {
      throw new IllegalArgumentException("pcap_replay_mode is required");
    }
    String upperMode = mode.toUpperCase();
    if (!ALLOWED_MODES.contains(upperMode)) {
      throw new IllegalArgumentException(
          "Invalid pcap_replay_mode: " + mode + " (allowed: " + ALLOWED_MODES + ")");
    }
    if (MODES_REQUIRING_RATE.contains(upperMode)
        && (content.getReplayRate() == null || content.getReplayRate() <= 0)) {
      throw new IllegalArgumentException(
          "pcap_replay_rate is required and must be > 0 for mode " + upperMode);
    }
  }

  /**
   * 选一个有 pcap_replay 能力的协作主机 Agent.
   *
   * <p>多个匹配 → 取首个（确定性，便于测试和复现）；后续可加负载策略.
   */
  public Optional<Agent> selectAgent() {
    List<Agent> candidates =
        agentService.selectAgentsForCapability(PcapReplayContract.CAPABILITY_PCAP_REPLAY);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0));
  }
}
```

- [ ] **Step 3.4：跑测试验证 GREEN**

```bash
mvn -pl veriguard-api -am test -Dtest='PcapReplayDispatchServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head
```

Expected: `Tests run: 6, Failures: 0, Errors: 0` + BUILD SUCCESS.

- [ ] **Step 3.5：Baseline regression**

```bash
mvn -pl veriguard-api -am test \
  -Dtest='MailInjectorTest,SmtpProfileServiceTest,WebAttackDispatchServiceTest,WebAttackExecutorTest,AgentServiceSelectByCapabilityTest' \
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: 6 + 7 + 6 + 4 + 8 = 31 tests pass.

- [ ] **Step 3.6：Spotless + commit**

```bash
mvn spotless:apply -q

cat > /tmp/pr-d-task3-msg.txt <<'EOF'
执行：PcapReplayDispatchService + 6 单测（B-ii PR-D Step 3）

服务承担：
- validateContent：pcap_file_id / target_interface / replay_mode 必填 +
  mode 在 ORIGINAL/MBPS/PPS/MULTIPLIER/TOPSPEED 内 + MBPS/PPS/MULTIPLIER 模式必带 rate > 0
- selectAgent：调 agentService.selectAgentsForCapability("pcap_replay")，多个匹配取首个

6 场景 TDD：缺 pcap_file_id / 缺 interface / mode 非法 /
  MBPS 模式缺 rate / 多 Agent 匹配 / 无 Agent.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF

git add veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/service/PcapReplayDispatchService.java \
        veriguard-api/src/test/java/io/veriguard/injectors/pcap_replay/service/PcapReplayDispatchServiceTest.java
git commit -F /tmp/pr-d-task3-msg.txt 2>&1 | tail -3
```

---

## Task 4：PcapReplayExecutor + 4 TDD 单测

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/PcapReplayExecutor.java`
- Create: `veriguard-api/src/test/java/io/veriguard/injectors/pcap_replay/PcapReplayExecutorTest.java`

### Steps

- [ ] **Step 4.1：写 4 个失败 TDD 测试**

```java
package io.veriguard.injectors.pcap_replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Execution;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
import io.veriguard.model.ExecutionProcess;
import io.veriguard.service.AttackChainNodeExpectationService;
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
class PcapReplayExecutorTest {

  @Mock private NodeExecutorContext context;
  @Mock private PcapReplayDispatchService dispatchService;
  @Mock private AttackChainNodeExpectationService attackChainNodeExpectationService;
  @Mock private Execution execution;
  @Mock private ExecutableNode injection;

  private PcapReplayExecutor newSpyExecutor(PcapReplayContent contentStub) throws Exception {
    PcapReplayExecutor real =
        new PcapReplayExecutor(context, dispatchService, attackChainNodeExpectationService);
    PcapReplayExecutor spied = spy(real);
    doReturn(contentStub).when(spied).convertContent(injection);
    return spied;
  }

  private PcapReplayContent validContent() {
    PcapReplayContent c = new PcapReplayContent();
    c.setPcapFileId("pcap-abc");
    c.setTargetInterface("eth0");
    c.setReplayMode("ORIGINAL");
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
    PcapReplayContent c = validContent();
    PcapReplayExecutor spied = newSpyExecutor(c);

    doNothing().when(dispatchService).validateContent(c);
    when(dispatchService.selectAgent()).thenReturn(Optional.of(agent("a1")));

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(dispatchService).validateContent(c);
    verify(dispatchService).selectAgent();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: 无可用 Agent → error trace + ExecutionProcess(false)")
  void process_noAgent() throws Exception {
    PcapReplayContent c = validContent();
    PcapReplayExecutor spied = newSpyExecutor(c);

    doNothing().when(dispatchService).validateContent(c);
    when(dispatchService.selectAgent()).thenReturn(Optional.empty());

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（缺 pcap_file_id）→ error trace")
  void process_validateFailure_missingPcap() throws Exception {
    PcapReplayContent c = new PcapReplayContent();
    c.setTargetInterface("eth0");
    c.setReplayMode("ORIGINAL");
    PcapReplayExecutor spied = newSpyExecutor(c);

    doThrow(new IllegalArgumentException("pcap_file_id is required"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }

  @Test
  @DisplayName("process: validateContent 抛异常（mode 非法 LIGHTSPEED）→ error trace")
  void process_validateFailure_invalidMode() throws Exception {
    PcapReplayContent c = validContent();
    c.setReplayMode("LIGHTSPEED");
    PcapReplayExecutor spied = newSpyExecutor(c);

    doThrow(new IllegalArgumentException("Invalid pcap_replay_mode: LIGHTSPEED"))
        .when(dispatchService)
        .validateContent(c);

    ExecutionProcess result = spied.process(execution, injection);

    assertThat(result.isAsync()).isFalse();
    verify(execution).addTrace(any());
  }
}
```

- [ ] **Step 4.2：跑测试验证 RED**

```bash
mvn -pl veriguard-api -am test -Dtest='PcapReplayExecutorTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

Expected: 编译失败（PcapReplayExecutor 不存在）.

- [ ] **Step 4.3：实现 PcapReplayExecutor**

```java
package io.veriguard.injectors.pcap_replay;

import static io.veriguard.database.model.ExecutionTrace.getNewErrorTrace;
import static io.veriguard.database.model.ExecutionTrace.getNewSuccessTrace;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Execution;
import io.veriguard.database.model.ExecutionTraceAction;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.executors.NodeExecutor;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.pcap_replay.model.PcapReplayContent;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
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
 * NodeExecutor 子类，处理 pcap 回放 inject（B-ii PR-D）.
 *
 * <p>process() 流程：
 *
 * <ol>
 *   <li>反序列化 inject_content → PcapReplayContent
 *   <li>校验内容（pcap_file_id + interface + mode + mode/rate 一致性）
 *   <li>选有 pcap_replay 能力的协作主机 Agent
 *   <li>记 dispatch trace（success: 含 agent id + interface + mode；error: 无 agent / 校验失败）
 *   <li>保存 ManualExpectation
 *   <li>返回 ExecutionProcess(false) —— 不等待 Agent 异步回填（本 PR 范围外）
 * </ol>
 */
@Slf4j
public class PcapReplayExecutor extends NodeExecutor {

  private final PcapReplayDispatchService dispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public PcapReplayExecutor(
      NodeExecutorContext context,
      PcapReplayDispatchService dispatchService,
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
  protected PcapReplayContent convertContent(ExecutableNode injection) throws Exception {
    return contentConvert(injection, PcapReplayContent.class);
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableNode injection)
      throws Exception {

    PcapReplayContent content;
    try {
      content = convertContent(injection);
    } catch (Exception e) {
      log.warn("Failed to deserialize PcapReplayContent: {}", e.getMessage());
      execution.addTrace(
          getNewErrorTrace(
              "Invalid pcap_replay content: " + e.getMessage(), ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }

    // 1) Validate
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
                  + PcapReplayContract.CAPABILITY_PCAP_REPLAY
                  + "'; deploy a 协作主机 Agent with tcpreplay to execute pcap replay injects",
              ExecutionTraceAction.COMPLETE));
      return new ExecutionProcess(false);
    }
    Agent agent = agentOpt.get();

    // 3) Save ManualExpectation entries
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
            "pcap replay dispatched to agent "
                + agent.getId()
                + " (interface="
                + content.getTargetInterface()
                + ", mode="
                + content.getReplayMode()
                + ", pcap="
                + content.getPcapFileId()
                + ")",
            ExecutionTraceAction.COMPLETE));

    return new ExecutionProcess(false);
  }
}
```

- [ ] **Step 4.4：跑测试验证 GREEN**

```bash
mvn -pl veriguard-api -am test -Dtest='PcapReplayExecutorTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head
```

Expected: `Tests run: 4, Failures: 0, Errors: 0` + BUILD SUCCESS.

- [ ] **Step 4.5：Baseline regression**

```bash
mvn -pl veriguard-api -am test \
  -Dtest='PcapReplayDispatchServiceTest,MailInjectorTest,SmtpProfileServiceTest,WebAttackDispatchServiceTest,WebAttackExecutorTest,LinkExpectationServiceTest,AgentServiceSelectByCapabilityTest' \
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: 6 + 6 + 7 + 6 + 4 + 18 + 8 = 55 tests pass.

- [ ] **Step 4.6：Spotless + commit**

```bash
mvn spotless:apply -q

cat > /tmp/pr-d-task4-msg.txt <<'EOF'
执行：PcapReplayExecutor 实现 + 4 单测（B-ii PR-D Step 4）

NodeExecutor 子类，process 流程：
- 反序列化 PcapReplayContent
- 委托 PcapReplayDispatchService 校验 + 选 Agent
- 写 dispatch trace（success: 含 agent + interface + mode + pcap_file_id；
  error: 缺 Agent / 校验失败 / 反序列化失败）
- 保存 ManualExpectation
- 返回 ExecutionProcess(false)

注：本 PR 不等待 Agent 异步结果，真实 tcpreplay 执行 + 结果回填在协作主机 Agent 客户端项目落地后补 follow-up PR.

protected convertContent(...) hook 让单测可 spy 注入 stub content.

4 场景 TDD：dispatch 成功 / 无 Agent / 缺 pcap_file_id / 非法 mode.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF

git add veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/PcapReplayExecutor.java \
        veriguard-api/src/test/java/io/veriguard/injectors/pcap_replay/PcapReplayExecutorTest.java
git commit -F /tmp/pr-d-task4-msg.txt 2>&1 | tail -3
```

---

## Task 5：PcapReplayNodeExecutorIntegration + Factory

**Files:**

- Create: `veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/pcap_replay/PcapReplayNodeExecutorIntegration.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/pcap_replay/PcapReplayNodeExecutorIntegrationFactory.java`

### Steps

- [ ] **Step 5.1：读 PR-C web_attack Integration + Factory 作为模板**

```bash
cat veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/web_attack/WebAttackNodeExecutorIntegration.java
cat veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/web_attack/WebAttackNodeExecutorIntegrationFactory.java
```

- [ ] **Step 5.2：创建 PcapReplayNodeExecutorIntegration**

```java
package io.veriguard.integration.impl.injectors.pcap_replay;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.PcapReplayExecutor;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class PcapReplayNodeExecutorIntegration extends IntegrationInMemory {

  public static final String PCAP_REPLAY_INJECTOR_ID = "42b6c9f4-9d5a-4e7c-d9fb-fa3c4d5e6f7a";
  private static final String PCAP_REPLAY_INJECTOR_NAME = "PCAP Replay";

  private final PcapReplayContract pcapReplayContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final NodeExecutorService nodeExecutorService;
  private final PcapReplayDispatchService pcapReplayDispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  @QualifiedComponent(identifier = {PcapReplayContract.TYPE, PCAP_REPLAY_INJECTOR_ID})
  private PcapReplayExecutor pcapReplayExecutor;

  public PcapReplayNodeExecutorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      PcapReplayContract pcapReplayContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      PcapReplayDispatchService pcapReplayDispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.pcapReplayContract = pcapReplayContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.pcapReplayDispatchService = pcapReplayDispatchService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    nodeExecutorService.registerBuiltinNodeExecutor(
        PCAP_REPLAY_INJECTOR_ID,
        PCAP_REPLAY_INJECTOR_NAME,
        pcapReplayContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.pcapReplayExecutor =
        new PcapReplayExecutor(
            nodeExecutorContext, pcapReplayDispatchService, attackChainNodeExpectationService);
  }

  @Override
  protected void innerStop() {
    // not possible to stop this integration
  }
}
```

- [ ] **Step 5.3：创建 PcapReplayNodeExecutorIntegrationFactory**

```java
package io.veriguard.integration.impl.injectors.pcap_replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
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
public class PcapReplayNodeExecutorIntegrationFactory extends IntegrationFactory {

  private final PcapReplayContract pcapReplayContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final ConnectorInstanceService connectorInstanceService;
  private final NodeExecutorService nodeExecutorService;
  private final PcapReplayDispatchService pcapReplayDispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final ComponentRequestEngine componentRequestEngine;

  public PcapReplayNodeExecutorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      PcapReplayContract pcapReplayContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      PcapReplayDispatchService pcapReplayDispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.pcapReplayContract = pcapReplayContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.pcapReplayDispatchService = pcapReplayDispatchService;
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
            PcapReplayNodeExecutorIntegration.PCAP_REPLAY_INJECTOR_ID,
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
    return new PcapReplayNodeExecutorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        pcapReplayContract,
        nodeExecutorContext,
        nodeExecutorService,
        pcapReplayDispatchService,
        attackChainNodeExpectationService);
  }
}
```

- [ ] **Step 5.4：编译 + 全部 PR-D 测试 + PR-B/C 回归**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -10

mvn -pl veriguard-api -am test \
  -Dtest='PcapReplayDispatchServiceTest,PcapReplayExecutorTest,WebAttackDispatchServiceTest,WebAttackExecutorTest,MailInjectorTest,SmtpProfileServiceTest,LinkExpectationServiceTest,AgentServiceSelectByCapabilityTest' \
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | tail
```

Expected: BUILD SUCCESS + 6 + 4 + 6 + 4 + 6 + 7 + 18 + 8 = 59 tests pass.

如编译失败于 `registerBuiltinNodeExecutor` arg count，复制 WebAttackNodeExecutorIntegration 的字面 arg 模式（9 个参数）.

- [ ] **Step 5.5：Spotless + commit**

```bash
mvn spotless:apply -q

cat > /tmp/pr-d-task5-msg.txt <<'EOF'
执行：PcapReplayNodeExecutorIntegration + Factory（B-ii PR-D Step 5）

参考 WebAttackNodeExecutorIntegration 模式注册 veriguard_pcap_replay inject 类型到
NodeExecutorService：
- PCAP_REPLAY_INJECTOR_ID 固定 UUID（42b6c9f4-...），用于 ConnectorInstance autostart
- innerStart 调 nodeExecutorService.registerBuiltinNodeExecutor 注册 contract
- @QualifiedComponent 标记 PcapReplayExecutor 实例
- Factory @Service 提供 spawn + findRelatedInstances 给 autostart 机制使用

启动后 veriguard_pcap_replay inject 类型对前端可见，用户可选 inject 并填 pcap_file_id /
interface / mode / rate.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF

git add veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/pcap_replay/
git status -s
git commit -F /tmp/pr-d-task5-msg.txt 2>&1 | tail -3
```

---

## Task 6：前端 inject 编辑器手工冒烟（不写代码）

**Files:** 无新增/修改 — 前端通过 InjectorContract field schema 自动渲染表单 + canvas 节点 icon 自动走 `/api/images/injectors/veriguard_pcap_replay`。

### Steps

- [ ] **Step 6.1：启 dev 环境**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-d-pcap-replay
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
2. 添加节点 → inject 类型下拉应能看到 **"PCAP Replay"** 选项（伴 `#009933` 绿色 + icon）
3. 选中后表单字段应自动出现：teams / pcap_file_id / pcap_target_interface / pcap_replay_mode / pcap_replay_rate / expectations

如果 inject 类型列表里看不到 PCAP Replay，可能 connector_instance 没自动启动 → 检查 `mvn spring-boot:run` 日志是否含 `PcapReplayNodeExecutorIntegrationFactory` startup line.

- [ ] **Step 6.3：API 层 smoke test**

```bash
curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" \
  http://localhost:8080/api/injectors_contracts | jq '[.[] | select(.injector_contract_labels.en | contains("PCAP Replay"))]'
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

- [ ] **Step 7.1：全 PR-D + PR-B/C + PR-A 测试不回归**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-d-pcap-replay
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am test \
  -Dtest='PcapReplayDispatchServiceTest,PcapReplayExecutorTest,WebAttackDispatchServiceTest,WebAttackExecutorTest,SmtpProfileServiceTest,MailInjectorTest,LinkExpectationServiceTest,AgentServiceSelectByCapabilityTest' \
  -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | tail
```

Expected: 全 BUILD SUCCESS / 6 + 4 + 6 + 4 + 7 + 6 + 18 + 8 = 59 测试过.

- [ ] **Step 7.2：master 锁验证**

```bash
git -C /Users/lamba/github/Veriguard ls-remote origin master | head -1
```

Expected: `5d7e05da627639491274175d2a77cf95b2c0fe7d	refs/heads/master`.

- [ ] **Step 7.3：push branch**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-d-pcap-replay
git push -u origin feat/b-ii-pr-d-pcap-replay 2>&1 | tail -3
```

- [ ] **Step 7.4：创建 PR**

```bash
cat > /tmp/pr-d-body.md <<'EOF'
## Summary

B-ii sub-project 第 4 个 PR（独立，与 PR-C 并行）：落地 pcap 回放 inject 类型，覆盖 PRD §2.3 第 3 行 6 类自定义用例中的「上传 pcap 流量包」。

- 设计稿：`docs/superpowers/specs/2026-05-12-veriguard-b-ii-design.md`（§6 pcap 回放 Inject 详细设计）
- 实施计划：`docs/superpowers/plans/2026-05-12-veriguard-b-ii-pr-d-pcap-replay-plan.md`

## 改动概览

### 后端（5 实施 commits + 1 plan commit）

- **PcapReplayContent**：JSONB POJO（pcap_file_id / pcap_target_interface / pcap_replay_mode / pcap_replay_rate / expectations）
- **PcapReplayContract**：注册 `veriguard_pcap_replay` inject 类型 + 6 个字段（teams/pcap_file_id/interface/mode 必填，rate/expectations 可选）+ `CAPABILITY_PCAP_REPLAY = "pcap_replay"` 常量
- **PcapReplayDispatchService + 6 TDD 单测**：mode 校验（ORIGINAL/MBPS/PPS/MULTIPLIER/TOPSPEED）+ pcap_file_id + interface 必填 + MBPS/PPS/MULTIPLIER 模式 rate > 0 + selectAgent 调 `agentService.selectAgentsForCapability("pcap_replay")`
- **PcapReplayExecutor + 4 TDD 单测**：NodeExecutor 子类，process 流程 = convertContent → validate → selectAgent → dispatch trace → 保存 ManualExpectation → ExecutionProcess(false)
- **PcapReplayNodeExecutorIntegration + Factory**：autostart 注册 inject 类型到 NodeExecutorService

### 前端

PR-D 后端 only。inject 编辑器表单 + 攻击编排画布节点 icon 通过现有通用机制自动渲染。

### 不在本 PR 范围（独立 follow-up）

- 协作主机 Agent **客户端代码**（独立项目，fork OpenAEV-Platform/agent，加 pcap_replay capability + tcpreplay 子进程包装）
- 真实 tcpreplay 执行 + 结果回填 endpoint
- pcap 文件上传到 MinIO + Document FK 强约束（spec §6.6 文件管理，独立子项目）
- pcap 元数据自动解析（包数 / 时长 / 首末包时间）
- 流量数据集自动导入（`datasets/PCAP-ATTACK` + `malware-traffic-analysis-pcaps` 批量 → `veriguard_pcap_replay` inject contract，独立子项目）

## 设计要点

- **不新建 PayloadType**：pcap 参数走 inject_content JSONB（PcapReplayContent POJO），与 PR-B 邮件 / PR-C web_attack 一致
- **简化派发**：本 PR 仅完成"找到协作主机 Agent + 写 dispatch trace"两步；真实 tcpreplay 依赖协作主机 Agent 客户端实现（独立项目）
- **5 个回放模式 + rate 一致性校验**：ORIGINAL 与 TOPSPEED 不需 rate；MBPS/PPS/MULTIPLIER 必须有 rate > 0
- **协作主机 Agent 选择**：复用 PR-A 的 `AgentService.selectAgentsForCapability("pcap_replay")`，多 Agent 匹配取首个
- **master 锁**：master 仍锁在 `5d7e05da6`；PR base = main

## Test plan

- [x] `mvn -pl veriguard-api -am test -Dtest='PcapReplayDispatchServiceTest,PcapReplayExecutorTest,WebAttackDispatchServiceTest,WebAttackExecutorTest,SmtpProfileServiceTest,MailInjectorTest,LinkExpectationServiceTest,AgentServiceSelectByCapabilityTest'` → 59/59 PASS
- [x] master lock 校验 `5d7e05da627639491274175d2a77cf95b2c0fe7d`
- [ ] Manual smoke：启 dev 环境验证 inject 编辑器下拉含 "PCAP Replay" 类型
- [ ] Manual smoke：注册一个测试 Agent（agent_capabilities=["pcap_replay"]）+ 触发 pcap_replay inject → trace 应记 dispatched
- [ ] Real replay：依赖协作主机 Agent 项目 + tcpreplay 部署后补做

## 后续

- 协作主机 Agent 客户端（fork OpenAEV-Platform/agent，加 http_attack + pcap_replay 能力声明 + tcpreplay 子进程包装）
- pcap 文件 MinIO 上传 + 元数据解析（独立子项目）
- 流量数据集自动导入（PCAP-ATTACK + malware-traffic-analysis-pcaps）

## B-ii sub-project 完成度

- ✅ PR-A: Agent capability 基础 (#35)
- ✅ PR-B: 邮件 Inject (#36)
- ✅ PR-C: Web 攻击包 Inject (#38)
- 🟢 **PR-D: pcap 回放 Inject (本 PR)** → B-ii 4 PR 全完成
EOF

gh pr create --base main --title "二开 B-ii PR-D: pcap 回放 Inject + 协作主机 Agent tcpreplay capability 派发" --body-file /tmp/pr-d-body.md 2>&1 | tail -3
```

---

## Summary

| Task | 后端文件 | 单测 | Commits |
|---|---|---:|---:|
| 1. PcapReplayContent | 1 创建 | 0 | 1 |
| 2. PcapReplayContract + icon 占位 | 1 创建 + 1 resource | 0 | 1 |
| 3. PcapReplayDispatchService | 1 创建 + 1 测试 | 6 | 1 |
| 4. PcapReplayExecutor | 1 创建 + 1 测试 | 4 | 1 |
| 5. PcapReplayNodeExecutorIntegration + Factory | 2 创建 | 0 | 1 |
| 6. 前端冒烟 | 0 | 0 | 0 |
| 7. 验证 + push + PR | 0 | 0 | 0 |
| **合计** | **7 创建（5 java + 1 java 测试 + 1 png）** | **10 测试** | **5 实施 commits + 1 plan + 1 PR** |

## 自审 checklist

**1. Spec coverage:**

- ✅ §6.1 用例契约：所有 4 字段在 PcapReplayContent + PcapReplayContract（pcap_file_id / target_interface / replay_mode / replay_rate）+ teams + expectations
- ✅ §6.2 回放速率模式：5 个 ALLOWED_MODES（ORIGINAL/MBPS/PPS/MULTIPLIER/TOPSPEED）+ MODES_REQUIRING_RATE 子集验证
- ⚠️ §6.3 运行时参数：agent_id 通过 dispatch 选择自动赋；接口名 / 速率覆盖留 follow-up（本 PR contract 字段即配置，没有"运行时覆盖"机制）
- ⚠️ §6.4 执行时序：本 PR 仅完成第 1 步"平台→Agent 下发"中的 dispatch trace 部分；2-9 步依赖 Agent 客户端
- ⚠️ §6.5 期望评估：ManualExpectation 已保存；SIEM 告警自动判定 in follow-up
- ⚠️ §6.6 pcap 文件管理：本 PR 仅引用 pcap_file_id 字符串字段；MinIO 上传 / 元数据解析 / SHA-256 去重独立子项目
- ⚠️ §6.7 协作主机权限：Agent 部署文档承担，本 PR 不实现
- ✅ §3.2 inject 与攻击源映射："pcap 回放 → 协作主机 Agent tcpreplay 子进程" 通过 capability 匹配落地
- ✅ §7.1 Capability 清单：CAPABILITY_PCAP_REPLAY = "pcap_replay" 已暴露常量

**2. Placeholder scan:**

- ✅ 无 TBD / TODO / "implement later"
- ✅ 每个代码 step 都含完整可粘贴的源码
- ✅ 所有 mvn / git 命令含 expected output 说明

**3. Type consistency:**

- ✅ `PcapReplayContent` 在 Task 1 定义，Task 3 / 4 使用一致
- ✅ `PcapReplayDispatchService.validateContent(PcapReplayContent)` + `selectAgent(): Optional<Agent>` 在 Task 3 定义，Task 4 使用一致
- ✅ `PcapReplayExecutor.convertContent(ExecutableNode): PcapReplayContent` 在 Task 4 定义并被测试 spy 替换
- ✅ `PcapReplayContract.TYPE = "veriguard_pcap_replay"` + `CAPABILITY_PCAP_REPLAY = "pcap_replay"` 在 Task 2 定义，Task 3 / 5 引用一致
- ✅ `PCAP_REPLAY_INJECTOR_ID = "42b6c9f4-9d5a-4e7c-d9fb-fa3c4d5e6f7a"` 不同于 PR-C 的 `31a5b8e3-...`，避免 UUID 冲突
- ✅ Flyway 版本号未冲突（本 PR 不新增 migration）
