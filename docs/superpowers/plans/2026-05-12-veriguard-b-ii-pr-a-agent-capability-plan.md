# B-ii PR-A Implementation Plan — Agent Capability 机制

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Agent 实体引入 capability 声明机制（基础设施先行，不引入新 inject 类型 / 不改 Agent 客户端代码），让平台能按 capability 筛选 Agent，是 PR-B/C/D 共同依赖前提。

**Architecture:** 类似已合并的 `attack_chains.dynamic_filter` 模式 —— Agent 实体加 JSONB 列承载 `capabilities: List<String>` + AgentService 加按 capability 选 Agent 的工具方法 + REST 输出 capability 字段 + 前端 Agent 列表展示 capability chip。

**Tech Stack:** Spring Boot 3.3.7 / Java 21 / JPA / Flyway / Postgres JSONB / Hibernate JsonType / React 19 / TypeScript / MUI

---

## 准备工作

**Worktree（执行前必做）：**

```bash
cd /Users/lamba/github/Veriguard
git fetch origin main
git worktree add worktrees/b-ii-pr-a-agent-capability -b feat/b-ii-pr-a-agent-capability origin/main
cd worktrees/b-ii-pr-a-agent-capability/veriguard-front
yarn install --immutable
```

**环境变量（每个 mvn 命令前都要 export）：**

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Java 25 触发 Mockito 大批 "cannot modify class" 失败（已记忆）；后端 mvn test 必须 Java 21。

---

## Task 1：V7 Flyway migration — `agents.agent_capabilities` JSONB 列

**Why first:** 后续 entity 字段读写依赖 DB 列存在；migration 优先建保证后续 entity 改动测试可跑。当前 main 最新 migration 是 V6，新加从 V7 起。

**Files:**

- Create: `veriguard-api/src/main/resources/db/migration/V7__agent_capabilities.sql`

### Steps

- [ ] **Step 1.1：创建 V7 migration 文件**

写入以下内容：

```sql
-- V7: B-ii PR-A Agent Capability 机制
--
-- 加 agents.agent_capabilities JSONB 列，承载 Agent 声明的能力标签列表（字符串数组）.
-- 默认空数组 '[]' 表示该 Agent 未声明任何 capability，与现有 Agent 行为完全兼容.
-- 由 List<String> Jackson 序列化驱动；columnDefinition jsonb 与 Hibernate JsonType 配合.

ALTER TABLE agents
    ADD COLUMN agent_capabilities JSONB NOT NULL
    DEFAULT '[]'::jsonb;
```

- [ ] **Step 1.2：跑后端 compile 触发 Flyway 校验**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-a-agent-capability
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`（migration 文件不引发 compile 失败；实际 Flyway 执行在测试启动时）。

- [ ] **Step 1.3：跑 schema 加载相关测试看 Flyway 应用 V7 OK**

```bash
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'BUILD SUCCESS|BUILD FAILURE|Tests run' | head -5
```

Expected: `Tests run: 18, Failures: 0, Errors: 0` + BUILD SUCCESS（确认 V7 不破坏现有 schema）。

- [ ] **Step 1.4：Commit**

```bash
git add veriguard-api/src/main/resources/db/migration/V7__agent_capabilities.sql
git commit -m "$(cat <<'EOF'
执行：V7 Flyway 加 agents.agent_capabilities JSONB 列（B-ii PR-A Step 1）

承载 Agent 声明的能力标签列表（List<String>，JSONB 持久化）.
默认空数组 '[]' 保现有 Agent 行为兼容（未声明 capability 即不被任何
按 capability 筛选的 inject 命中）.

后续 AgentEntity.capabilities 字段映射此列.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2：Agent entity 加 `capabilities` 字段

**Files:**

- Modify: `veriguard-model/src/main/java/io/veriguard/database/model/Agent.java`

### Steps

- [ ] **Step 2.1：先读 AttackChain.java 中 dynamicFilter 字段的实现作为模板**

```bash
grep -nA8 'private FilterGroup dynamicFilter' veriguard-model/src/main/java/io/veriguard/database/model/AttackChain.java
```

理解 `@Type(JsonType.class) + @Column(jsonb) + @JsonProperty + default` 的写法。

- [ ] **Step 2.2：读 Agent.java 现有 imports 与字段尾部位置**

```bash
head -25 veriguard-model/src/main/java/io/veriguard/database/model/Agent.java
tail -40 veriguard-model/src/main/java/io/veriguard/database/model/Agent.java
```

注意现有 imports 与 `@Getter @Setter` class 注解。

- [ ] **Step 2.3：加 imports + capabilities 字段**

打开 `veriguard-model/src/main/java/io/veriguard/database/model/Agent.java`，在 import 区添加（保持字母序）：

```java
import io.hypersistence.utils.hibernate.type.json.JsonType;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Type;
```

如已存在则跳过。

在 class 内部、在最后一个字段后（`process_name` 之后），加：

```java
  // -- AGENT CAPABILITIES (B-ii PR-A) --
  // Agent 启动时通过配置文件声明的能力标签列表（如 command_exec / file_drop /
  // http_attack / pcap_replay 等）。平台据此匹配可下发的 inject 类型.
  // 默认空数组表示该 Agent 未声明任何能力，可避免现有未升级 Agent 异常.

  @Type(JsonType.class)
  @Column(name = "agent_capabilities", columnDefinition = "jsonb")
  @JsonProperty("agent_capabilities")
  @NotNull
  private List<String> capabilities = new ArrayList<>();
```

- [ ] **Step 2.4：跑后端 compile 验证**

```bash
mvn -pl veriguard-model -am compile -DskipTests -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2.5：跑基线测试无回归**

```bash
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: `Tests run: 18, Failures: 0, Errors: 0`.

- [ ] **Step 2.6：Commit**

```bash
git add veriguard-model/src/main/java/io/veriguard/database/model/Agent.java
git commit -m "$(cat <<'EOF'
执行：Agent entity 加 capabilities 字段（B-ii PR-A Step 2）

与 AttackChain.dynamicFilter 同模式：
- @Type(JsonType.class) @Column agent_capabilities JSONB → List<String> 持久化
- @JsonProperty("agent_capabilities") 暴露 wire format
- 默认 new ArrayList<>() 保 backward compat（未升级 Agent 不声明能力）

为 PR-B/C/D 各自的 inject 类型与 Agent capability 匹配提供存储基础.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：AgentRepository 加 `findByCapability` 查询方法

**Files:**

- Modify: `veriguard-model/src/main/java/io/veriguard/database/repository/AgentRepository.java`

### Steps

- [ ] **Step 3.1：读 AgentRepository 现有内容**

```bash
cat veriguard-model/src/main/java/io/veriguard/database/repository/AgentRepository.java | head -50
```

理解现有 `@Query nativeQuery=true` 风格。

- [ ] **Step 3.2：加 findByCapability 方法**

在 `AgentRepository` interface 内部（在 `rawAgentByIds` 之前），添加：

```java
  /**
   * 查询声明了指定 capability 的 Agent 列表（B-ii PR-A）.
   *
   * <p>JSONB 数组包含查询：matches 任何 capabilities 包含 {@code capability} 的 Agent.
   * 使用 PostgreSQL JSONB `?` 运算符判断 string element 是否存在于 array.
   */
  @Query(
      value =
          "SELECT a.* FROM agents a "
              + "WHERE a.agent_capabilities @> CAST(:capabilityJson AS jsonb)",
      nativeQuery = true)
  List<Agent> findByCapability(@Param("capabilityJson") String capabilityJson);
```

注意：`@>` 是 JSONB containment 运算符，`'["http_attack"]'::jsonb @> a.agent_capabilities` 检查左侧是否包含右侧；我们要反过来 — Agent 的数组包含 `["capability_name"]`，故用 `a.agent_capabilities @> '["..."]'::jsonb`。Service 层负责构造 JSON 数组字符串。

- [ ] **Step 3.3：跑 compile 验证**

```bash
mvn -pl veriguard-model -am compile -DskipTests -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 3.4：Commit**

```bash
git add veriguard-model/src/main/java/io/veriguard/database/repository/AgentRepository.java
git commit -m "$(cat <<'EOF'
执行：AgentRepository 加 findByCapability 查询方法（B-ii PR-A Step 3）

PostgreSQL JSONB containment 运算符 @> 判断 Agent 是否声明了指定 capability：
- 入参 capabilityJson 是 service 层构造的 JSON 数组字符串（如 '["http_attack"]'）
- a.agent_capabilities @> :capabilityJson 命中包含该 capability 的 Agent

后续 AgentService.selectAgentsForCapability 调用此方法.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：AgentService.selectAgentsForCapability + 8 单测（TDD）

**Why:** Service 派生方法是 capability → Agent 列表的核心；先纯单元 TDD 验证逻辑（不涉及 DB）。

**Files:**

- Modify: `veriguard-api/src/main/java/io/veriguard/service/AgentService.java`
- Create: `veriguard-api/src/test/java/io/veriguard/service/AgentServiceSelectByCapabilityTest.java`

### Steps

- [ ] **Step 4.1：先写失败测试**

完整测试文件，写入 `veriguard-api/src/test/java/io/veriguard/service/AgentServiceSelectByCapabilityTest.java`：

```java
package io.veriguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Agent;
import io.veriguard.database.repository.AgentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
class AgentServiceSelectByCapabilityTest {

  @Mock private AgentRepository agentRepository;

  @InjectMocks private AgentService service;

  private Agent agent(String id) {
    Agent a = new Agent();
    a.setId(id);
    return a;
  }

  @Test
  @DisplayName("null capability → 空列表，不查 repository")
  void nullCapability_returnsEmptyAndSkipsRepo() {
    List<Agent> result = service.selectAgentsForCapability(null);

    assertThat(result).isEmpty();
    verify(agentRepository, never()).findByCapability(any());
  }

  @Test
  @DisplayName("空字符串 capability → 空列表，不查 repository")
  void blankCapability_returnsEmptyAndSkipsRepo() {
    List<Agent> result = service.selectAgentsForCapability("   ");

    assertThat(result).isEmpty();
    verify(agentRepository, never()).findByCapability(any());
  }

  @Test
  @DisplayName("正常 capability → 构造 JSON 数组字符串调 repository")
  void normalCapability_buildsJsonArrayAndDelegates() {
    Agent a1 = agent("a1");
    when(agentRepository.findByCapability("[\"http_attack\"]")).thenReturn(List.of(a1));

    List<Agent> result = service.selectAgentsForCapability("http_attack");

    assertThat(result).containsExactly(a1);
    verify(agentRepository).findByCapability("[\"http_attack\"]");
  }

  @Test
  @DisplayName("多 Agent 命中 → 返回完整列表")
  void multipleAgentsMatch_returnsAllResults() {
    Agent a1 = agent("a1");
    Agent a2 = agent("a2");
    Agent a3 = agent("a3");
    when(agentRepository.findByCapability(any())).thenReturn(List.of(a1, a2, a3));

    List<Agent> result = service.selectAgentsForCapability("pcap_replay");

    assertThat(result).hasSize(3).containsExactly(a1, a2, a3);
  }

  @Test
  @DisplayName("0 命中 → 空列表（不抛错）")
  void noMatch_returnsEmpty() {
    when(agentRepository.findByCapability(any())).thenReturn(List.of());

    List<Agent> result = service.selectAgentsForCapability("nonexistent");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("含特殊字符的 capability 名 → JSON 转义安全")
  void capabilityWithQuote_buildsEscapedJsonArray() {
    when(agentRepository.findByCapability(any())).thenReturn(List.of());

    service.selectAgentsForCapability("evil\"name");

    // 期望调用时 JSON 转义引号
    verify(agentRepository).findByCapability("[\"evil\\\"name\"]");
  }

  @Test
  @DisplayName("repository 抛异常 → 异常透传，不静默吞")
  void repositoryThrows_propagates() {
    when(agentRepository.findByCapability(any()))
        .thenThrow(new RuntimeException("DB query failed"));

    assertThrows(
        RuntimeException.class, () -> service.selectAgentsForCapability("http_attack"));
  }

  @Test
  @DisplayName("正常 capability 名 → 不修改字符串大小写或前后空格")
  void capabilityCasePreserved() {
    when(agentRepository.findByCapability(any())).thenReturn(List.of());

    service.selectAgentsForCapability("HTTP_Attack");

    verify(agentRepository).findByCapability("[\"HTTP_Attack\"]");
  }
}
```

- [ ] **Step 4.2：跑测试确认全 fail（method 未实现）**

```bash
mvn -pl veriguard-api -am test -Dtest='AgentServiceSelectByCapabilityTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

Expected: 编译失败（`selectAgentsForCapability` 未定义）— 这是 TDD red phase 预期。

- [ ] **Step 4.3：实现 selectAgentsForCapability 方法**

打开 `veriguard-api/src/main/java/io/veriguard/service/AgentService.java`，加 imports（保持字母序，仅添加未存在的）：

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
```

在 class 末尾的最后一个 method 之后（在最后一个 `}` 之前），加：

```java
  private static final ObjectMapper CAPABILITY_JSON_MAPPER = new ObjectMapper();

  /**
   * 查询声明了指定 capability 的 Agent 列表（B-ii PR-A）.
   *
   * <p>null / 空 / 仅空白字符的 capability 返回空列表且不查询数据库（short-circuit）.
   * 否则构造单元素 JSON 数组字符串（如 {@code ["http_attack"]}）传入
   * {@link AgentRepository#findByCapability(String)}，通过 PostgreSQL JSONB
   * containment 运算符 {@code @>} 匹配数组中包含此元素的 Agent.
   *
   * @param capability capability 标签名（如 command_exec / http_attack / pcap_replay）
   * @return 命中的 Agent 列表（保持 repository 返回顺序）
   */
  public List<Agent> selectAgentsForCapability(String capability) {
    if (capability == null || capability.isBlank()) {
      return List.of();
    }
    try {
      String capabilityJson =
          CAPABILITY_JSON_MAPPER.writeValueAsString(List.of(capability));
      return agentRepository.findByCapability(capabilityJson);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Failed to serialize capability to JSON: " + capability, e);
    }
  }
```

- [ ] **Step 4.4：跑测试确认全 pass**

```bash
mvn -pl veriguard-api -am test -Dtest='AgentServiceSelectByCapabilityTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head -5
```

Expected: `Tests run: 8, Failures: 0, Errors: 0` + BUILD SUCCESS.

- [ ] **Step 4.5：spotless format**

```bash
mvn spotless:apply -q 2>&1 | tail -3
```

- [ ] **Step 4.6：Commit**

```bash
git add veriguard-api/src/main/java/io/veriguard/service/AgentService.java \
        veriguard-api/src/test/java/io/veriguard/service/AgentServiceSelectByCapabilityTest.java
git commit -m "$(cat <<'EOF'
执行：AgentService.selectAgentsForCapability + 8 单测（B-ii PR-A Step 4）

- null / blank capability short-circuit 返空列表，不查 repository
- 非空 capability 用 Jackson 构造单元素 JSON 数组（如 ["http_attack"]）
  传入 AgentRepository.findByCapability
- JSON 序列化失败抛 IllegalStateException（不静默吞）

单测 8 场景：null / blank / 正常 / 多 Agent 命中 / 0 命中 /
含引号转义 / repo 抛异常透传 / 大小写保留.

PR-B/C/D 各自的 inject 派发服务调此方法选择可下发 Agent.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5：AgentOutput DTO 加 `capabilities` 字段

**Why:** 前端通过 Endpoint API 拿 AgentOutput，需在 DTO 中暴露 capability 字段。

**Files:**

- Modify: `veriguard-api/src/main/java/io/veriguard/rest/asset/endpoint/form/AgentOutput.java`
- Modify: `veriguard-api/src/main/java/io/veriguard/utils/mapper/EndpointMapper.java`（如果存在 Agent → AgentOutput 映射）

### Steps

- [ ] **Step 5.1：读 AgentOutput 现有字段**

```bash
cat veriguard-api/src/main/java/io/veriguard/rest/asset/endpoint/form/AgentOutput.java
```

理解 `@Schema + @JsonProperty + Setter/Getter` 风格。

- [ ] **Step 5.2：加 capabilities 字段**

打开 `veriguard-api/src/main/java/io/veriguard/rest/asset/endpoint/form/AgentOutput.java`，在 import 区加（如未存在）：

```java
import java.util.List;
```

在 class 内部（在 `isActive` 字段之后），加：

```java
  @Schema(description = "Agent capabilities (B-ii PR-A): declared capability tags like command_exec, http_attack, pcap_replay")
  @JsonProperty("agent_capabilities")
  private List<String> capabilities;
```

- [ ] **Step 5.3：查找并修改 Agent → AgentOutput 的映射处**

```bash
grep -rn 'AgentOutput.builder\|new AgentOutput' veriguard-api/src/main/java --include='*.java' | head
```

定位映射代码（典型在 EndpointMapper 或类似 utils）。如有映射 builder，在合适位置加上：

```java
.capabilities(agent.getCapabilities())
```

如果没找到任何 builder 调用，搜：

```bash
grep -rn 'AgentOutput' veriguard-api/src/main/java --include='*.java' | head -20
```

定位实际映射位置后加 `.capabilities(agent.getCapabilities())` 到 builder 链。

- [ ] **Step 5.4：跑后端 compile + 测试无回归**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: BUILD SUCCESS / 18 测试过.

- [ ] **Step 5.5：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/rest/asset/endpoint/form/AgentOutput.java
# 如修改了 mapper 也加进来
git status -s  # 查看实际改动
git add veriguard-api/src/main/java/io/veriguard/utils/mapper/EndpointMapper.java 2>/dev/null || true
git commit -m "$(cat <<'EOF'
执行：AgentOutput DTO 加 capabilities 字段（B-ii PR-A Step 5）

REST 层 wire format 暴露 Agent capability 标签列表，前端 AgentList 用此
字段展示 chip 样式 capability 标签.

@JsonProperty("agent_capabilities") + Agent entity getter 直接映射到
List<String>，无需 converter.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6：AgentRegisterInput 接受 capabilities

**Why:** Agent 客户端注册时上报自身 capability 清单，平台持久化。本期不改 Agent 客户端代码（PR-B/C/D 才会改），但 REST 端点要能接受，未升级的 Agent 不传则保持空数组。

**Files:**

- Modify: `veriguard-api/src/main/java/io/veriguard/executors/model/AgentRegisterInput.java`
- Modify: `veriguard-api/src/main/java/io/veriguard/executors/caldera/service/CalderaExecutorService.java`（Caldera 注册路径写入 capabilities）

### Steps

- [ ] **Step 6.1：AgentRegisterInput 加字段**

打开 `veriguard-api/src/main/java/io/veriguard/executors/model/AgentRegisterInput.java`，在 import 区添加：

```java
import java.util.ArrayList;
import java.util.List;
```

在 class 内部其他字段之后加：

```java
  /** B-ii PR-A: Agent 启动时通过配置文件声明的能力标签列表。未上报则为空。 */
  private List<String> capabilities = new ArrayList<>();
```

- [ ] **Step 6.2：CalderaExecutorService 写入 capabilities**

读 CalderaExecutorService 的 `createOrUpdateAgent` 方法：

```bash
grep -nA20 'private void createOrUpdateAgent' veriguard-api/src/main/java/io/veriguard/executors/caldera/service/CalderaExecutorService.java | head -30
```

在 Agent 字段赋值序列中（其他 `agent.setXxx(input.getXxx())` 调用附近），加：

```java
    agent.setCapabilities(input.getCapabilities());
```

- [ ] **Step 6.3：搜索其他 AgentRegisterInput 使用点同步处理**

```bash
grep -rn 'AgentRegisterInput' veriguard-api/src/main/java --include='*.java' | head
```

对每个使用 AgentRegisterInput 创建 / 更新 Agent 的位置，确保 `agent.setCapabilities(input.getCapabilities())` 被调用。

如未升级的客户端不传此字段，`@Data` 默认值 `new ArrayList<>()` 兜底，Agent 的 capabilities 保持空数组。

- [ ] **Step 6.4：跑 compile 验证**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -5
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: BUILD SUCCESS / 18 测试过.

- [ ] **Step 6.5：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/executors/model/AgentRegisterInput.java \
        veriguard-api/src/main/java/io/veriguard/executors/caldera/service/CalderaExecutorService.java
git commit -m "$(cat <<'EOF'
执行：AgentRegisterInput 接受 capabilities + Caldera 注册路径写入（B-ii PR-A Step 6）

- AgentRegisterInput.capabilities: List<String>，默认空数组
- CalderaExecutorService.createOrUpdateAgent 将 input.capabilities 写入 Agent entity

未升级的 Agent 客户端不传此字段时 @Data 默认值兜底为空数组，capability 筛选时
不会被任何 inject 命中（PR-B/C/D 的 inject 派发逻辑用 selectAgentsForCapability
按需筛选）.

PR-B/C/D 各自需要时再扩展 Agent 客户端代码以声明 capability.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7：前端 AgentList 加 capability chip 列

**Files:**

- Modify: `veriguard-front/src/utils/api-types.d.ts`（手工加字段；非 yarn generate-types-from-api，因为后端尚未 push）
- Modify: `veriguard-front/src/admin/components/assets/endpoints/endpoint/AgentList.tsx`

### Steps

- [ ] **Step 7.1：api-types.d.ts 手工加字段**

打开 `veriguard-front/src/utils/api-types.d.ts`，找 `AgentOutput` interface（约第 50 行附近），在合适位置加：

```ts
  /** Agent capabilities (B-ii PR-A): declared capability tags */
  agent_capabilities?: string[];
```

同时找 `Agent` interface（约第 15 行附近），加同一字段以保持模型一致：

```ts
  agent_capabilities?: string[];
```

- [ ] **Step 7.2：AgentList.tsx 加 capability chip 列**

打开 `veriguard-front/src/admin/components/assets/endpoints/endpoint/AgentList.tsx`：

在 import 区添加（保持字母序，仅添加未存在的）：

```tsx
import { Chip, Stack } from '@mui/material';
```

在 `inlineStyles` 对象中，调整宽度并加 `agent_capabilities`：

```tsx
const inlineStyles: Record<string, CSSProperties> = {
  agent_executed_by_user: { width: '20%' },
  agent_executor: {
    width: '12%',
    display: 'flex',
    alignItems: 'center',
    cursor: 'default',
  },
  agent_privilege: { width: '10%' },
  agent_deployment_mode: { width: '10%' },
  agent_capabilities: { width: '20%' },
  agent_active: { width: '8%' },
  agent_version: { width: '5%' },
  agent_last_seen: { width: '15%' },
};
```

在 `headers` 数组中，在 `agent_deployment_mode` 之后、`agent_active` 之前插入：

```tsx
    {
      field: 'agent_capabilities',
      label: 'Capabilities',
      isSortable: false,
      value: (agent: AgentOutput) => {
        const caps = agent.agent_capabilities ?? [];
        if (caps.length === 0) {
          return <span style={{ opacity: 0.5 }}>{t('None')}</span>;
        }
        return (
          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
            {caps.map(cap => (
              <Chip key={cap} label={cap} size="small" />
            ))}
          </Stack>
        );
      },
    },
```

- [ ] **Step 7.3：check-ts + lint**

```bash
cd veriguard-front
yarn check-ts 2>&1 | tail -5
npx eslint src/admin/components/assets/endpoints/endpoint/AgentList.tsx --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -3
```

Expected: 0 ts errors / 0 lint errors.

- [ ] **Step 7.4：跑现有测试不破**

```bash
yarn test 2>&1 | grep -E 'Test Files|Tests' | tail -3
```

Expected: 全套通过 0 回归.

- [ ] **Step 7.5：Commit**

```bash
git add veriguard-front/src/utils/api-types.d.ts \
        veriguard-front/src/admin/components/assets/endpoints/endpoint/AgentList.tsx
git commit -m "$(cat <<'EOF'
执行：前端 AgentList 加 capability chip 列（B-ii PR-A Step 7）

- api-types.d.ts: Agent / AgentOutput 加 agent_capabilities?: string[] 字段
- AgentList.tsx: 在 deployment 和 status 之间插入 Capabilities 列
  - 空数组显示 "None"（灰色）
  - 非空时用 MUI Chip 标签 + Stack flex-wrap 横向排列
- inlineStyles 调整列宽腾出空间

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8：PR-A 验证 + push + 创建 PR

**Files:** 无新改动，仅验证。

### Steps

- [ ] **Step 8.1：跑 PR-A 全套后端测试**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-a-agent-capability
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am test -Dtest='AgentServiceSelectByCapabilityTest,LinkExpectationServiceTest,AttackChainServiceDynamicContractsTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | tail
```

Expected: 全 BUILD SUCCESS / 8 + 18 + 8 = 34 测试过.

- [ ] **Step 8.2：跑前端 check-ts + test + lint**

```bash
cd veriguard-front
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | grep -E 'Test Files|Tests' | tail -3
npx eslint src/admin/components/assets/endpoints/endpoint/AgentList.tsx src/utils/api-types.d.ts --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -3
```

Expected: 0 ts errors / 0 lint errors / 全套测试通过.

- [ ] **Step 8.3：master 锁验证**

```bash
git -C /Users/lamba/github/Veriguard ls-remote origin master | head -1
```

Expected: `5d7e05da627639491274175d2a77cf95b2c0fe7d	refs/heads/master`（不变）.

- [ ] **Step 8.4：push branch**

```bash
cd /Users/lamba/github/Veriguard/worktrees/b-ii-pr-a-agent-capability
git push -u origin feat/b-ii-pr-a-agent-capability 2>&1 | tail -5
```

- [ ] **Step 8.5：创建 PR-A**

```bash
gh pr create --base main --title "二开 B-ii PR-A: Agent Capability 机制（基础设施先行）" --body "$(cat <<'EOF'
## Summary

B-ii sub-project 第 1 个 PR：为 Agent 实体引入 capability 声明机制，作为后续 PR-B（邮件 inject）/ PR-C（Web 攻击包 + Agent HTTP 能力）/ PR-D（pcap 回放 + Agent tcpreplay 能力）的共同基础。

设计稿：\`docs/superpowers/specs/2026-05-12-veriguard-b-ii-design.md\`（main 已合并）

7 个 commit（Step 1-7）：

- **Step 1** V7 Flyway: \`agents.agent_capabilities\` JSONB 列
- **Step 2** Agent entity 加 \`capabilities: List<String>\` 字段
- **Step 3** AgentRepository.findByCapability（JSONB @> 容器查询）
- **Step 4** AgentService.selectAgentsForCapability + 8 单测（TDD）
- **Step 5** AgentOutput DTO 加 capabilities 字段（前端 wire format）
- **Step 6** AgentRegisterInput 接受 capabilities + Caldera 注册路径写入
- **Step 7** 前端 AgentList 加 capability chip 列

## 设计要点

- 模式与 \`attack_chains.dynamic_filter\` 一致（JSONB + JsonType + 默认值）
- Capability 是字符串标签清单，标签语义在 PR-B/C/D 各自引入（如 \`http_attack\` / \`pcap_replay\`）
- 现有未升级 Agent 默认 capabilities=\`[]\`，不会被任何 capability 筛选命中（向后兼容）
- 本 PR 不引入新 inject 类型 / 不改 Agent 客户端代码

## Test plan

- [x] mvn 后端 BUILD SUCCESS
- [x] AgentServiceSelectByCapabilityTest 8 passed
- [x] LinkExpectationServiceTest 18 baseline passed（0 回归）
- [x] AttackChainServiceDynamicContractsTest 8 baseline passed（0 回归）
- [x] yarn check-ts 0 errors
- [x] yarn test 0 回归
- [x] eslint 触及文件 0 errors
- [x] origin/master 仍锁 \`5d7e05da6\`

## 范围 boundary（spec §12.4）

不做：capability 自动发现 / 版本化 / 依赖关系图 / 运行时动态启用 / 客户自定义。

## 后续

PR-A merged main 后启动 PR-B（邮件 inject + SMTP profile 管理）writing-plans。PR-B/C/D 之间无相互依赖，理论可并行。
EOF
)" 2>&1 | tail -3
```

- [ ] **Step 8.6：等用户 review / merge PR-A**

PR-A merged main 后，进入下一阶段 PR-B writing-plans。

---

## Summary

7 个 commit / 8 个 task / 1 个 worktree / 1 个 PR：

| Task | 改动文件 | 测试 |
|---|---|---|
| 1 | V7 migration | 跑 LinkExpectationServiceTest 校验 Flyway 应用 |
| 2 | Agent entity | 同上 |
| 3 | AgentRepository | compile 校验 |
| 4 | AgentService + 8 单测 | TDD 8 测试 |
| 5 | AgentOutput DTO + mapper | compile + baseline |
| 6 | AgentRegisterInput + CalderaExecutorService | compile + baseline |
| 7 | 前端 AgentList + api-types | check-ts + lint + 现有测试 |
| 8 | 验证 + push + PR | 全套测试 + master 锁 + push |

## Test plan

- [ ] mvn 后端 BUILD SUCCESS
- [ ] AgentServiceSelectByCapabilityTest 8 passed
- [ ] LinkExpectationServiceTest 18 passed（baseline，0 回归）
- [ ] AttackChainServiceDynamicContractsTest 8 passed（baseline，0 回归）
- [ ] yarn check-ts 0 errors
- [ ] yarn test 0 回归
- [ ] eslint 触及文件 0 errors
- [ ] origin/master 仍锁 `5d7e05da6`
- [ ] PR base=main，标题含 "B-ii PR-A"

## 范围 boundary（spec §12.4 — 本 plan 不做）

- ❌ Capability 自动发现（Agent 根据所在主机环境自动声明）
- ❌ Capability 版本化（capability 是无版本字符串标签）
- ❌ Capability 依赖关系图
- ❌ 运行时动态启用 / 禁用（重启 Agent 才能改）
- ❌ 客户自定义 capability 名（本期由平台预置标签清单）
- ❌ 新 inject 类型（PR-B/C/D 才引入）
- ❌ Agent 客户端代码改动（PR-C/D 才引入）

## 自审 checklist

完成所有 task 后做最后一遍：

- [ ] V7 Flyway 正确加 JSONB 列，默认 `'[]'`，应用启动不报错
- [ ] Agent.capabilities 字段映射 JSONB 列，默认 `new ArrayList<>()`
- [ ] AgentRepository.findByCapability 用 `@>` 容器查询
- [ ] AgentService.selectAgentsForCapability null / blank short-circuit
- [ ] AgentOutput JSON 暴露 agent_capabilities 字段
- [ ] AgentRegisterInput 默认 `new ArrayList<>()`，未升级 Agent 不报错
- [ ] CalderaExecutorService 写入 capabilities
- [ ] 前端 AgentList 显示 chip 列，空数组显示 "None"
- [ ] 7 个 commit 全部含 `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`
- [ ] master 锁 `5d7e05da6` 不变
