# 攻击链路动态用例集 Implementation Plan（Phase 12c-Biii）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PRD §2.3 第 4+5 行 saved-query 动态联动机制落地：AttackChain 加 `dynamicFilter` 字段（同 OpenBAS `AssetGroup.dynamicFilter` 双轨模式），filter 匹配的 NodeContract 在 chain run 时实例化为简化执行单元（t=0 平行 / 无依赖 / 默认 1 repeat），与手动节点共存。

**Architecture:** 拆 2 PR：**PR-A 后端**（V5 Flyway migration + entity 字段 + service 派生 + 执行 job 接入 + REST endpoint + 测试）/ **PR-B 前端**（API client + adapter + DynamicFilterDrawer + ChainedTimeline 动态节点 + NodeWrapper dashed border 视觉 + 双 host 接线 + i18n）。后端独立可合并验证；前端 base PR-A merged main。

**Tech Stack:** Spring Boot 3.3.7 / Java 21 / JPA / Flyway / Postgres JSONB / `Filters.FilterGroup` / `FilterUtilsJpa.computeFilterGroupJpa` / React 19 / TypeScript / Vite / vitest / `@xyflow/react`

---

## 准备工作

**Worktree（执行前必做）：**

```bash
cd /Users/lamba/github/Veriguard
git fetch origin main
git worktree add worktrees/phase-12c-Biii-dynamic-content -b feat/attack-chain-phase-12c-Biii-dynamic-content origin/main
cd worktrees/phase-12c-Biii-dynamic-content/veriguard-front
yarn install --immutable
```

**环境变量（每个 shell 都要 export，跑 mvn 时必需）：**

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Java 25 触发 Mockito 大批 "cannot modify class"（project memory 已记录）；本 PR 后端有 mvn test 必须 Java 21。

---

# PR-A：后端动态 filter + 派生 + 执行接入

PR-A base=main，独立可合并。完成后再起 PR-B。

---

## Task A1：V5 Flyway migration — `attack_chains.dynamic_filter` JSONB 列

**Why first:** entity 字段读写依赖 DB 列存在；migration 优先建保证后续 entity 改动测试可跑。注意：spec 写 V4，但 `V4__attack_chain_rename_es_reindex.sql` 已存在（main 的 12b-A1 后置 migration），新 migration 必须是 **V5**。

**Files:**

- Create: `veriguard-api/src/main/resources/db/migration/V5__attack_chain_dynamic_filter.sql`

### Steps

- [ ] **Step A1.1：创建 V5 migration 文件**

```sql
-- V5: Phase 12c-Biii 攻击链路动态用例集
--
-- 加 attack_chains.dynamic_filter JSONB 列（与 asset_groups.asset_group_dynamic_filter 同模式）.
-- 默认 {"mode":"and","filters":[]} 表示空 filter，与现有 chain 行为完全兼容（无动态 contracts）.
-- 由 Filters.FilterGroup Java 对象 Jackson 序列化驱动；columnDefinition jsonb 与 Hibernate JsonType 配合.

ALTER TABLE attack_chains
    ADD COLUMN dynamic_filter JSONB NOT NULL
    DEFAULT '{"mode":"and","filters":[]}'::jsonb;
```

- [ ] **Step A1.2：跑后端 compile 触发 Flyway 校验**

```bash
cd /Users/lamba/github/Veriguard/worktrees/phase-12c-Biii-dynamic-content
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am compile -DskipTests -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`（migration 文件不引发 compile 失败；实际 Flyway 执行在测试启动时）。

- [ ] **Step A1.3：跑 schema 加载相关测试看 Flyway 应用 V5 OK**

```bash
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'BUILD SUCCESS|BUILD FAILURE|Tests run' | head -5
```

Expected: 18 LinkExpectationServiceTest passed + BUILD SUCCESS（确认 V5 不破坏现有 schema）。

- [ ] **Step A1.4：Commit**

```bash
git add veriguard-api/src/main/resources/db/migration/V5__attack_chain_dynamic_filter.sql
git commit -m "$(cat <<'EOF'
执行：V5 Flyway 加 attack_chains.dynamic_filter JSONB 列（Phase 12c-Biii Step 1）

与 asset_groups.asset_group_dynamic_filter 同模式：
- JSONB 列存 Filters.FilterGroup 序列化数据
- NOT NULL DEFAULT '{"mode":"and","filters":[]}' 保现有 chain 行为兼容
- 后续 AttackChain.dynamicFilter 字段映射此列

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task A2：AttackChain entity 加 `dynamicFilter` + `dynamicContracts` 字段

**Files:**

- Modify: `veriguard-model/src/main/java/io/veriguard/database/model/AttackChain.java`

### Steps

- [ ] **Step A2.1：先读 AssetGroup.java 字段定义作为模板**

```bash
grep -nA8 'private FilterGroup dynamicFilter\|private List<Asset> dynamicAssets' veriguard-model/src/main/java/io/veriguard/database/model/AssetGroup.java
```

Expected: 输出 `dynamicFilter` (JSONB column) 字段 + `dynamicAssets` (@Transient) 字段定义。

- [ ] **Step A2.2：读 AttackChain.java 现有 import 语句 + 末尾字段**

```bash
head -55 veriguard-model/src/main/java/io/veriguard/database/model/AttackChain.java
```

注意现有的 imports 中是否已有 `FilterGroup` / `JsonType` / `Type`。

- [ ] **Step A2.3：加 import 与字段**

打开 `veriguard-model/src/main/java/io/veriguard/database/model/AttackChain.java`，确保 imports 含：

```java
import io.veriguard.database.model.Filters.FilterGroup;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import org.hibernate.annotations.Type;
import jakarta.persistence.Transient;
import com.fasterxml.jackson.annotation.JsonProperty;
```

`AttackChain.java` 末尾 / 在 grants 字段附近加：

```java
  // -- DYNAMIC FILTER (Phase 12c-Biii) --
  // 与 AssetGroup.dynamicFilter 同模式：双轨制（手动节点 + 动态 contracts）.
  // 当 dynamicFilter 非空时，AttackChainNodesExecutionJob 启动 run 时由
  // AttackChainService.computeDynamicContracts() 实时派生匹配的 NodeContract，
  // 每条 contract instantiate 为 runtime 临时执行单元（t=0 平行 / 无依赖 / 默认 1 repeat），
  // 不写 attack_chain_nodes 表.

  @Type(JsonType.class)
  @Column(name = "dynamic_filter", columnDefinition = "jsonb")
  @JsonProperty("attack_chain_dynamic_filter")
  @NotNull
  private FilterGroup dynamicFilter = FilterGroup.defaultFilterGroup();

  // 派生字段：service 层运行时填充，不持久化到 DB.
  // Wire format key 与 attack_chain_dynamic_filter 对称，前端编辑器画布 +
  // 运行画布读此字段渲染动态节点.
  @Getter(NONE)
  @Transient
  @JsonProperty("attack_chain_dynamic_contracts")
  private List<NodeContract> dynamicContracts = new ArrayList<>();

  // 显式 getter（@Transient + @Getter(NONE) 时 Lombok 不生成 getter，
  // 与 AssetGroup.getDynamicAssets() 同模式）.
  public List<NodeContract> getDynamicContracts() {
    return this.dynamicContracts;
  }
```

注意：如 `AttackChain.java` 已用 `@Data`（Lombok 自动 setter/getter），仍需 `@Getter(NONE)` 抑制并手写 getter（同 AssetGroup pattern）。

- [ ] **Step A2.4：跑后端 compile 验证**

```bash
mvn -pl veriguard-model -am compile -DskipTests -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step A2.5：跑模型相关测试不破现有**

```bash
mvn -pl veriguard-api -am test -Dtest='LinkExpectationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: `Tests run: 18, Failures: 0, Errors: 0`（基线测试全过）。

- [ ] **Step A2.6：Commit**

```bash
git add veriguard-model/src/main/java/io/veriguard/database/model/AttackChain.java
git commit -m "$(cat <<'EOF'
执行：AttackChain entity 加 dynamicFilter + dynamicContracts 字段（Phase 12c-Biii Step 2）

与 AssetGroup.dynamicFilter / dynamicAssets 同模式：
- @Type(JsonType.class) @Column dynamic_filter JSONB → FilterGroup 持久化
- @Transient @Getter(NONE) dynamicContracts: List<NodeContract>，service 层运行时填充
- @JsonProperty wire format: attack_chain_dynamic_filter / attack_chain_dynamic_contracts
- 默认 FilterGroup.defaultFilterGroup() 保 backward compat（空 filter 不派生）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task A3：`AttackChainService.computeDynamicContracts` + 单测（TDD）

**Why:** service 派生方法是动态 filter → contracts 的核心；先纯单元 TDD 验证逻辑（不涉及 DB / job）。

**Files:**

- Modify: `veriguard-api/src/main/java/io/veriguard/service/attack_chain/AttackChainService.java`
- Create: `veriguard-api/src/test/java/io/veriguard/service/attack_chain/AttackChainServiceDynamicContractsTest.java`

### Steps

- [ ] **Step A3.1：先写失败测试**

完整测试文件：

```java
// veriguard-api/src/test/java/io/veriguard/service/attack_chain/AttackChainServiceDynamicContractsTest.java
package io.veriguard.service.attack_chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.Filters.Filter;
import io.veriguard.database.model.Filters.FilterGroup;
import io.veriguard.database.model.Filters.FilterMode;
import io.veriguard.database.model.Filters.FilterOperator;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.repository.NodeContractRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AttackChainServiceDynamicContractsTest {

  @Mock NodeContractRepository nodeContractRepository;

  private AttackChainService service;

  @BeforeEach
  void setUp() {
    // 注：完整 AttackChainService ctor 可能含其他依赖；本测仅关注
    // computeDynamicContracts 路径，其他依赖传 null（method 不引用即可）.
    // 若构造器要求非 null，本测改用 @InjectMocks + 仅 mock 用到的依赖.
    service = new AttackChainService(/* 其他依赖按现状传入 / mock */);
    service.setNodeContractRepository(nodeContractRepository); // 若 service 已用 final field 注入，需调整
  }

  private static AttackChain chainWithFilter(FilterGroup filter) {
    AttackChain c = new AttackChain();
    c.setId(UUID.randomUUID().toString());
    c.setDynamicFilter(filter);
    return c;
  }

  private static NodeContract contract(String id) {
    NodeContract nc = new NodeContract();
    nc.setId(id);
    return nc;
  }

  @Test
  @DisplayName("空 filter（默认）→ 空列表，不查 repository")
  void emptyFilter_returnsEmpty() {
    AttackChain chain = chainWithFilter(FilterGroup.defaultFilterGroup());

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).isEmpty();
    verify(nodeContractRepository, never()).findAll(any(Specification.class));
  }

  @Test
  @DisplayName("null dynamicFilter → 空列表，不抛错")
  void nullFilter_returnsEmpty() {
    AttackChain chain = new AttackChain();
    chain.setDynamicFilter(null);

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("单 filter（attack_pattern eq）→ 委托 repository 用 Specification 查询")
  void singleFilter_eqAttackPattern_delegatesToRepository() {
    Filter f = Filter.getNewDefaultEqualFilter(
        "node_contract_attack_patterns",
        List.of("ap-recon"));
    FilterGroup fg = FilterGroup.filterGroupWithFilters(List.of(f));
    AttackChain chain = chainWithFilter(fg);

    NodeContract c1 = contract("c1");
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of(c1));

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).containsExactly(c1);
    verify(nodeContractRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName("多 filter AND mode → 全部 contracts 都需匹配")
  void multipleFilters_andMode_allMustMatch() {
    Filter f1 = Filter.getNewDefaultEqualFilter("node_contract_attack_patterns", List.of("ap-1"));
    Filter f2 = Filter.getNewDefaultEqualFilter("node_contract_kill_chain_phases", List.of("phase-1"));
    FilterGroup fg = new FilterGroup();
    fg.setMode(FilterMode.and);
    fg.setFilters(List.of(f1, f2));
    AttackChain chain = chainWithFilter(fg);

    NodeContract c1 = contract("c1");
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of(c1));

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).containsExactly(c1);
    verify(nodeContractRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName("多 filter OR mode → 任一匹配即包含")
  void multipleFilters_orMode_anyMatches() {
    Filter f1 = Filter.getNewDefaultEqualFilter("node_contract_attack_patterns", List.of("ap-1"));
    Filter f2 = Filter.getNewDefaultEqualFilter("node_contract_attack_patterns", List.of("ap-2"));
    FilterGroup fg = new FilterGroup();
    fg.setMode(FilterMode.or);
    fg.setFilters(List.of(f1, f2));
    AttackChain chain = chainWithFilter(fg);

    NodeContract c1 = contract("c1");
    NodeContract c2 = contract("c2");
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of(c1, c2));

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).hasSize(2).containsExactlyInAnyOrder(c1, c2);
  }

  @Test
  @DisplayName("filter 匹配 0 contracts → 空列表（不抛错）")
  void noMatches_returnsEmpty() {
    Filter f = Filter.getNewDefaultEqualFilter(
        "node_contract_attack_patterns",
        List.of("ap-nonexistent"));
    FilterGroup fg = FilterGroup.filterGroupWithFilters(List.of(f));
    AttackChain chain = chainWithFilter(fg);

    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of());

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("not_empty operator → 委托 repository（非空 contract 全返）")
  void notEmptyOperator_delegates() {
    Filter f = new Filter();
    f.setKey("node_contract_attack_patterns");
    f.setOperator(FilterOperator.not_empty);
    f.setMode(FilterMode.and);
    f.setValues(List.of());
    FilterGroup fg = FilterGroup.filterGroupWithFilters(List.of(f));
    AttackChain chain = chainWithFilter(fg);

    NodeContract c1 = contract("c1");
    NodeContract c2 = contract("c2");
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of(c1, c2));

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("repository findAll 抛异常 → 异常透传到调用者（不静默吞）")
  void repositoryThrows_propagates() {
    Filter f = Filter.getNewDefaultEqualFilter("node_contract_attack_patterns", List.of("ap-1"));
    FilterGroup fg = FilterGroup.filterGroupWithFilters(List.of(f));
    AttackChain chain = chainWithFilter(fg);

    when(nodeContractRepository.findAll(any(Specification.class)))
        .thenThrow(new RuntimeException("DB query failed"));

    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> service.computeDynamicContracts(chain));
  }
}
```

注：`AttackChainService` ctor 可能依赖多 service / repo（按现有代码）。如果 setter injection 不存在，改为：
- 选择 1：把 `nodeContractRepository` 加到 ctor 参数（与现有 ctor 模式同），其他 mock 用 `@Mock` 加 `@InjectMocks`
- 选择 2：用 `@MockBean` + `@SpringBootTest`（更重）

推荐选择 1：纯 Mockito，与 LinkExpectationServiceTest pattern 一致。

- [ ] **Step A3.2：跑测试确认全 fail（method 未实现）**

```bash
mvn -pl veriguard-api -am test -Dtest='AttackChainServiceDynamicContractsTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -10
```

Expected: 编译失败（`computeDynamicContracts` 未定义）— 这是 TDD red phase 预期。

- [ ] **Step A3.3：实现 computeDynamicContracts 方法**

打开 `veriguard-api/src/main/java/io/veriguard/service/attack_chain/AttackChainService.java`，加 import：

```java
import static io.veriguard.utils.FilterUtilsJpa.computeFilterGroupJpa;

import io.veriguard.database.model.Filters;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.repository.NodeContractRepository;
import org.springframework.data.jpa.domain.Specification;
```

确保 ctor 注入了 `NodeContractRepository nodeContractRepository`（如尚未，参考 AssetGroupService.computeDynamicAssets 的 endpointService 注入方式加进去）。

加 method：

```java
  /**
   * 派生 AttackChain 动态 NodeContract 列表（PRD §2.3 第 5 行）.
   *
   * <p>与 {@code AssetGroupService.computeDynamicAssets} 同模式：
   * <ul>
   *   <li>dynamicFilter 为 null / 空 FilterGroup → 空列表（short-circuit，不查 repo）
   *   <li>否则用 {@link io.veriguard.utils.FilterUtilsJpa#computeFilterGroupJpa} 构造
   *       Specification，调 {@code nodeContractRepository.findAll(spec)} 拿匹配 contracts
   * </ul>
   *
   * <p>实时派生：每次调用都重新 query DB，保证 PRD §2.3 第 5 行 "用例更新自动进入场景"
   * 的实时联动语义.
   *
   * @param chain 含 dynamicFilter 字段的 chain（不修改）
   * @return 匹配的 NodeContract 列表（保持 repository 返回顺序）
   */
  public List<NodeContract> computeDynamicContracts(AttackChain chain) {
    if (chain == null) {
      return List.of();
    }
    Filters.FilterGroup filter = chain.getDynamicFilter();
    if (Filters.isEmptyFilterGroup(filter)) {
      return List.of();
    }
    Specification<NodeContract> spec = computeFilterGroupJpa(filter);
    return this.nodeContractRepository.findAll(spec);
  }
```

- [ ] **Step A3.4：跑测试确认全 pass**

```bash
mvn -pl veriguard-api -am test -Dtest='AttackChainServiceDynamicContractsTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head -5
```

Expected: `Tests run: 8, Failures: 0, Errors: 0` + BUILD SUCCESS。

- [ ] **Step A3.5：跑现有 AttackChainService 测试不破**

```bash
mvn -pl veriguard-api -am test -Dtest='AttackChainService*Test' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep 'Tests run' | head
```

Expected: 现有 AttackChainService 相关测试全过。

- [ ] **Step A3.6：spotless format**

```bash
mvn spotless:apply -q 2>&1 | tail -3
```

- [ ] **Step A3.7：Commit**

```bash
git add veriguard-api/src/main/java/io/veriguard/service/attack_chain/AttackChainService.java \
        veriguard-api/src/test/java/io/veriguard/service/attack_chain/AttackChainServiceDynamicContractsTest.java
git commit -m "$(cat <<'EOF'
执行：AttackChainService.computeDynamicContracts + 单测（Phase 12c-Biii Step 3）

与 AssetGroupService.computeDynamicAssets 同模式：
- 空 filter / null chain short-circuit 返空列表，不查 repo
- 非空 filter 用 FilterUtilsJpa.computeFilterGroupJpa 构造 Specification
- nodeContractRepository.findAll(spec) 拿匹配 contracts
- 实时派生（每次调用重新 query），保 PRD §2.3 第 5 行 "用例更新自动进入" 语义

单测 8 场景：空/null filter / 单 eq filter / AND 多 filter / OR 多 filter /
0 匹配 / not_empty operator / repo 抛异常透传.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task A4：`AttackChainNodesExecutionJob` 接动态 contracts 派生

**Why:** chain run 启动时由 job 调 service 派生 dynamicContracts → 为每个生成 runtime 临时执行单元（t=0 平行 / 无依赖 / 默认 1 repeat / 不写 attack_chain_nodes 表）。

**Files:**

- Modify: `veriguard-api/src/main/java/io/veriguard/scheduler/jobs/AttackChainNodesExecutionJob.java`
- Modify (or Create): `veriguard-api/src/test/java/io/veriguard/scheduler/jobs/AttackChainNodesExecutionJobTest.java`

### Steps

- [ ] **Step A4.1：读现有 job 结构定位插入点**

```bash
grep -nE 'public void execute|chain\.\w+|getAttackChainNodes|@Inject\b|private final' veriguard-api/src/main/java/io/veriguard/scheduler/jobs/AttackChainNodesExecutionJob.java | head -20
```

理解现有节点遍历逻辑（应该有类似 `chain.getAttackChainNodes().forEach(node -> ...)`）。

- [ ] **Step A4.2：注入 AttackChainService（或 NodeContractRepository）**

如 job 已注入 AttackChainService（看 `private final` 字段），跳过此步。否则加：

```java
private final AttackChainService attackChainService;
```

并加到 ctor 参数。Spring 自动 wire。

- [ ] **Step A4.3：在节点遍历后追加动态 contracts 派生 + 实例化逻辑**

定位 `chain.getAttackChainNodes().forEach(...)`（或类似遍历）后插入：

```java
    // PRD §2.3 第 5 行：动态 contracts 派生 + 实例化为 runtime 执行单元.
    // 与手动节点共存：t=0 平行 / 无依赖 / 默认 1 repeat / 不写 attack_chain_nodes 表.
    List<NodeContract> dynamicContracts = attackChainService.computeDynamicContracts(chain);
    for (NodeContract contract : dynamicContracts) {
      // 为每个 contract 创建 runtime 临时节点（不持久化）
      AttackChainNode runtimeNode = new AttackChainNode();
      runtimeNode.setId("dynamic-" + contract.getId());  // 前缀避免与 UUID 节点冲突
      runtimeNode.setAttackChain(chain);
      runtimeNode.setNodeInjectorContract(contract);
      runtimeNode.setNodeDependsDuration(0L);  // t=0
      runtimeNode.setNodeRepeatCount(1);       // 默认 1 repeat
      // 不设 dependsOn → 无依赖
      // 用 contract 默认 expectations
      executeRuntimeNode(runtimeNode, run);  // 复用现有节点执行逻辑
    }
```

注：`executeRuntimeNode(...)` 是现有 job 内的节点执行方法名（按实际命名调整）。如执行逻辑是 inline lambda 内写的，把 lambda body 抽出为 helper 方法以便 dynamic / 手动节点复用。

**关键不变量**：runtime node 不写 attack_chain_nodes 表（不调 `nodeRepository.save`）；NodeExpectation 仍写库（按 contract 默认 expectations 创建），但其 `node_expectation_node` 字段引用 `dynamic-${contract_id}`，前端识别。

- [ ] **Step A4.4：写集成测试**

完整测试（增量加到现有 `AttackChainNodesExecutionJobTest.java` 末尾）：

```java
  @Test
  @DisplayName("空 dynamicFilter → 不调 service.computeDynamicContracts，仅跑手动节点")
  void emptyDynamicFilter_doesNotCallComputeDynamic() {
    AttackChain chain = new AttackChain();
    chain.setId(UUID.randomUUID().toString());
    chain.setDynamicFilter(FilterGroup.defaultFilterGroup());
    chain.setAttackChainNodes(new ArrayList<>());
    AttackChainRun run = new AttackChainRun();
    run.setAttackChain(chain);
    when(attackChainRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
    when(attackChainService.computeDynamicContracts(chain)).thenReturn(List.of());

    job.execute(run.getId());

    verify(attackChainService).computeDynamicContracts(chain);
    // 没有 dynamic contracts → 没有 runtime node 实例化
    // verify(nodeRepository, never()).save(any()); // 取决于现有 mock 设置
  }

  @Test
  @DisplayName("dynamicFilter 派生 2 contracts → 实例化 2 个 runtime 节点 (id=dynamic-{contract_id})")
  void dynamicContracts_instantiateRuntimeNodes() {
    AttackChain chain = new AttackChain();
    chain.setId(UUID.randomUUID().toString());
    Filter f = Filter.getNewDefaultEqualFilter(
        "node_contract_attack_patterns", List.of("ap-1"));
    chain.setDynamicFilter(FilterGroup.filterGroupWithFilters(List.of(f)));
    chain.setAttackChainNodes(new ArrayList<>());
    AttackChainRun run = new AttackChainRun();
    run.setAttackChain(chain);

    NodeContract c1 = new NodeContract();
    c1.setId("c1");
    NodeContract c2 = new NodeContract();
    c2.setId("c2");
    when(attackChainRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
    when(attackChainService.computeDynamicContracts(chain)).thenReturn(List.of(c1, c2));

    job.execute(run.getId());

    // 验证 2 contracts 都被处理（具体断言取决于现有 job 内部如何 emit expectations）
    verify(attackChainService).computeDynamicContracts(chain);
    // 进一步：捕获 executeRuntimeNode 调用看 node id 前缀 dynamic-
    ArgumentCaptor<AttackChainNode> captor = ArgumentCaptor.forClass(AttackChainNode.class);
    verify(/* the executor or expectation writer */).process(captor.capture());
    List<AttackChainNode> runtimeNodes = captor.getAllValues();
    assertThat(runtimeNodes).hasSize(2);
    assertThat(runtimeNodes.get(0).getId()).isEqualTo("dynamic-c1");
    assertThat(runtimeNodes.get(0).getNodeDependsDuration()).isEqualTo(0L);
    assertThat(runtimeNodes.get(0).getNodeRepeatCount()).isEqualTo(1);
  }
```

注：实际断言对象 (`/* the executor or expectation writer */`) 取决于现有 job 内部 dependency 命名。先看 `AttackChainNodesExecutionJobTest.java` 现有测试的 `verify(...)` 断言对象，沿用同款。

- [ ] **Step A4.5：跑测试**

```bash
mvn -pl veriguard-api -am test -Dtest='AttackChainNodesExecutionJobTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head
```

Expected: 现有测试 + 2 新测试 全 pass + BUILD SUCCESS。

- [ ] **Step A4.6：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/scheduler/jobs/AttackChainNodesExecutionJob.java \
        veriguard-api/src/test/java/io/veriguard/scheduler/jobs/AttackChainNodesExecutionJobTest.java
git commit -m "$(cat <<'EOF'
执行：AttackChainNodesExecutionJob 接动态 contracts 实例化（Phase 12c-Biii Step 4）

chain run 启动时调 attackChainService.computeDynamicContracts 派生匹配的
NodeContract，每条 instantiate 为 runtime 临时执行单元（不写 attack_chain_nodes 表）：
- node id = "dynamic-${contract_id}"（前缀避免与 UUID 节点冲突）
- t=0 平行（depends_duration=0）/ 无依赖 / 默认 1 repeat
- 用 contract.injector_contract_default_expectations 写 NodeExpectation

空 dynamicFilter short-circuit（service 内部）→ 现有 chain run 行为零变化.

集成测试 +2 场景：空 filter 仅跑手动节点 / 动态 filter 派生 2 contracts 实例化
runtime 节点（断言 id 前缀 + depends_duration + repeat_count）.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task A5：REST PUT `/api/attack_chains/{id}/dynamic_filter` + 测试

**Files:**

- Modify: `veriguard-api/src/main/java/io/veriguard/rest/attack_chain/AttackChainApi.java`
- Create: `veriguard-api/src/main/java/io/veriguard/rest/attack_chain/form/AttackChainDynamicFilterInput.java`
- Create: `veriguard-api/src/test/java/io/veriguard/rest/attack_chain/AttackChainApiDynamicFilterTest.java`

### Steps

- [ ] **Step A5.1：写 input DTO**

```java
// veriguard-api/src/main/java/io/veriguard/rest/attack_chain/form/AttackChainDynamicFilterInput.java
package io.veriguard.rest.attack_chain.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.Filters.FilterGroup;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/attack_chains/{id}/dynamic_filter 输入 DTO（PRD §2.3 第 4 行）.
 *
 * <p>FilterGroup 序列化由 Jackson 处理，与 attack_chains.dynamic_filter JSONB 列同字段名.
 */
public record AttackChainDynamicFilterInput(
    @JsonProperty("dynamic_filter") @NotNull FilterGroup dynamicFilter) {}
```

- [ ] **Step A5.2：在 AttackChainApi.java 加 PUT 端点**

确保 imports 含：

```java
import io.veriguard.rest.attack_chain.form.AttackChainDynamicFilterInput;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
```

加 method（位置：与现有 PUT 端点（如 settings）相邻）：

```java
  @LogExecutionTime
  @PutMapping(value = "/api/attack_chains/{attackChainId}/dynamic_filter")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ATTACK_CHAIN)
  public AttackChain updateAttackChainDynamicFilter(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody final AttackChainDynamicFilterInput input) {
    AttackChain chain = this.attackChainService.attackChain(attackChainId);
    chain.setDynamicFilter(input.dynamicFilter());
    return this.attackChainService.updateAttackChain(chain);
  }
```

注：`attackChainService.attackChain(id)` / `updateAttackChain(chain)` 是现有 service method —— 按现有 PUT settings 端点模式调用。如方法名不同（如 `findById` / `save`），按现有命名调整。

如 `ResourceType.ATTACK_CHAIN` 不存在，看现有 AttackChainApi 其他端点用什么 resource type（如 `SCENARIO`），保持一致。

- [ ] **Step A5.3：写测试**

完整测试（学样 AttackChainEdgeApiTest pattern — pure Jackson deserialization）：

```java
// veriguard-api/src/test/java/io/veriguard/rest/attack_chain/AttackChainApiDynamicFilterTest.java
package io.veriguard.rest.attack_chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.database.model.Filters.FilterGroup;
import io.veriguard.database.model.Filters.FilterMode;
import io.veriguard.rest.attack_chain.form.AttackChainDynamicFilterInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 单测覆盖 PUT /api/attack_chains/{id}/dynamic_filter 的 JSON wire format.
 * 完整端到端 (mvc) 测试走 IntegrationTest 太重；这里聚焦输入反序列化 ——
 * 端点逻辑是 findById + setDynamicFilter + save，由 service 层测试覆盖.
 */
class AttackChainApiDynamicFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("空 filter 反序列化为 FilterGroup（mode=and，filters=[]）")
  void deserialize_emptyFilter() throws Exception {
    String json =
        """
        {
          "dynamic_filter": {
            "mode": "and",
            "filters": []
          }
        }
        """;
    AttackChainDynamicFilterInput input =
        objectMapper.readValue(json, AttackChainDynamicFilterInput.class);

    FilterGroup fg = input.dynamicFilter();
    assertThat(fg).isNotNull();
    assertThat(fg.getMode()).isEqualTo(FilterMode.and);
    assertThat(fg.getFilters()).isEmpty();
  }

  @Test
  @DisplayName("OR mode + 多 filter → 正确反序列化")
  void deserialize_orModeMultipleFilters() throws Exception {
    String json =
        """
        {
          "dynamic_filter": {
            "mode": "or",
            "filters": [
              {"key":"node_contract_attack_patterns","mode":"and","operator":"eq","values":["ap-1"]},
              {"key":"node_contract_kill_chain_phases","mode":"and","operator":"eq","values":["phase-1"]}
            ]
          }
        }
        """;
    AttackChainDynamicFilterInput input =
        objectMapper.readValue(json, AttackChainDynamicFilterInput.class);

    FilterGroup fg = input.dynamicFilter();
    assertThat(fg.getMode()).isEqualTo(FilterMode.or);
    assertThat(fg.getFilters()).hasSize(2);
    assertThat(fg.getFilters().get(0).getKey()).isEqualTo("node_contract_attack_patterns");
    assertThat(fg.getFilters().get(0).getValues()).containsExactly("ap-1");
  }

  @Test
  @DisplayName("dynamic_filter 为 null → @NotNull 校验失败（DTO 反序列化成功，validate 时报错）")
  void deserialize_nullDynamicFilter() throws Exception {
    String json = "{ \"dynamic_filter\": null }";
    AttackChainDynamicFilterInput input =
        objectMapper.readValue(json, AttackChainDynamicFilterInput.class);

    // record 反序列化允许 null（field 是 nullable 在 record 上）；@NotNull 校验在 Spring @Valid 时触发.
    // 此测仅验证 Jackson 反序列化行为，校验由 Spring MVC pipeline 处理.
    assertThat(input.dynamicFilter()).isNull();
  }

  @Test
  @DisplayName("非法 JSON（缺 mode）→ Jackson 反序列化 FilterGroup 容错（mode 为 null，校验时拒绝）")
  void deserialize_missingMode() throws Exception {
    String json =
        """
        {
          "dynamic_filter": {
            "filters": []
          }
        }
        """;
    AttackChainDynamicFilterInput input =
        objectMapper.readValue(json, AttackChainDynamicFilterInput.class);

    // FilterGroup.mode 为 @NotNull (Bean Validation)；Jackson 反序列化时不拦截，
    // Spring @Valid 时 / service 层 isEmptyFilterGroup 检查时拒绝.
    assertThat(input.dynamicFilter()).isNotNull();
  }
}
```

- [ ] **Step A5.4：跑测试**

```bash
mvn -pl veriguard-api -am test -Dtest='AttackChainApiDynamicFilterTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | head
```

Expected: `Tests run: 4, Failures: 0, Errors: 0` + BUILD SUCCESS。

- [ ] **Step A5.5：spotless + commit**

```bash
mvn spotless:apply -q
git add veriguard-api/src/main/java/io/veriguard/rest/attack_chain/AttackChainApi.java \
        veriguard-api/src/main/java/io/veriguard/rest/attack_chain/form/AttackChainDynamicFilterInput.java \
        veriguard-api/src/test/java/io/veriguard/rest/attack_chain/AttackChainApiDynamicFilterTest.java
git commit -m "$(cat <<'EOF'
执行：PUT /api/attack_chains/{id}/dynamic_filter REST 端点（Phase 12c-Biii Step 5）

- AttackChainDynamicFilterInput record DTO（@NotNull FilterGroup
  + @JsonProperty("dynamic_filter") wire format key）
- AttackChainApi.updateAttackChainDynamicFilter @PutMapping
  @RBAC(WRITE, ATTACK_CHAIN) 端点：findById + setDynamicFilter + save
- 单测 4 场景：空 filter 反序列化 / OR mode 多 filter 反序列化 /
  null dynamic_filter / 缺 mode 字段（Jackson 容错，Bean Validation 拦截）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task A6：PR-A 验证 + push + PR

**Files:** 无新改动，仅验证。

### Steps

- [ ] **Step A6.1：跑 PR-A 全套后端测试**

```bash
cd /Users/lamba/github/Veriguard/worktrees/phase-12c-Biii-dynamic-content
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -pl veriguard-api -am test -Dtest='AttackChainServiceDynamicContractsTest,AttackChainNodesExecutionJobTest,AttackChainApiDynamicFilterTest,LinkExpectationServiceTest,AttackChainEdgeApiTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E 'Tests run|BUILD' | tail
```

Expected: 全 BUILD SUCCESS / 各 test 类的 Tests run 数 + 0 failures + 0 errors。

- [ ] **Step A6.2：master 锁验证**

```bash
git -C /Users/lamba/github/Veriguard ls-remote origin master | head -1
```

Expected: `5d7e05da627639491274175d2a77cf95b2c0fe7d	refs/heads/master`（不变）。

- [ ] **Step A6.3：push branch**

```bash
git push -u origin feat/attack-chain-phase-12c-Biii-dynamic-content 2>&1 | tail -5
```

- [ ] **Step A6.4：创建 PR-A**

```bash
gh pr create --base main --title "二开 Phase 12c-Biii PR-A: 后端动态 filter + 派生 + 执行接入" --body "$(cat <<'EOF'
## Summary

PRD §2.3 第 4+5 行 saved-query 动态联动机制后端落地。与 OpenBAS \`AssetGroup.dynamicFilter\` 双轨模式同构。

设计稿：\`docs/superpowers/specs/2026-05-10-veriguard-dynamic-content-design.md\`
实施计划：\`docs/superpowers/plans/2026-05-10-veriguard-dynamic-content.md\`

5 个 commit (Step 1-5)：

- **Step 1** V5 Flyway: \`attack_chains.dynamic_filter\` JSONB 列
- **Step 2** AttackChain entity: \`dynamicFilter\` (持久化) + \`dynamicContracts\` (@Transient 派生)
- **Step 3** AttackChainService.computeDynamicContracts + 8 单测
- **Step 4** AttackChainNodesExecutionJob 接动态 contracts 实例化（runtime 临时节点 id=\`dynamic-\${contract_id}\`，不写表）+ 集成测试 +2
- **Step 5** PUT /api/attack_chains/{id}/dynamic_filter REST 端点 + DTO + 4 单测

## Test plan

- [x] mvn 后端 BUILD SUCCESS
- [x] AttackChainServiceDynamicContractsTest 8 passed
- [x] AttackChainNodesExecutionJobTest 现有 + 2 新 passed
- [x] AttackChainApiDynamicFilterTest 4 passed
- [x] origin/master 仍锁 5d7e05da6
- [ ] PR-B 前端 wire 后端到端验证

## 范围 boundary（spec §8）

不做：跨 chain 共享 saved query / 动态节点精细编排 / 多 dynamicFilter / 动态节点 expectation 自定义.

## 后续

PR-B 前端将基于 main (PR-A merged) 起：DynamicFilterDrawer + ChainedTimeline 动态节点 + NodeWrapper 视觉区分 + 双 host wire + i18n.
EOF
)"
```

- [ ] **Step A6.5：等用户 review/merge PR-A**

PR-A merged main 后，进 PR-B。

---

# PR-B：前端 DynamicFilterDrawer + ChainedTimeline + 双 host wire

PR-B base=main（PR-A merged 后）。

---

## Task B0：起新 worktree（PR-B）

PR-A 合并后 main 已有后端字段，PR-B 起新 worktree。

```bash
cd /Users/lamba/github/Veriguard
git fetch origin main
git worktree remove worktrees/phase-12c-Biii-dynamic-content  # 清理 PR-A worktree
git worktree add worktrees/phase-12c-Biii-frontend -b feat/attack-chain-phase-12c-Biii-frontend origin/main
cd worktrees/phase-12c-Biii-frontend/veriguard-front
yarn install --immutable
```

---

## Task B1：API client + wire-format types

**Files:**

- Modify: `veriguard-front/src/actions/attack_chains/attack_chain-actions.ts`

### Steps

- [ ] **Step B1.1：先看现有 attack_chain-actions.ts 结构 + B3 落地的 settings PUT 模式**

```bash
grep -n 'updateAttackChainSettings\|simplePostCall\|putReferential\|EXERCISE_URI' veriguard-front/src/actions/attack_chains/attack_chain-actions.ts | head
```

理解现有 wire format type 定义模式（B3 已建立）。

- [ ] **Step B1.2：加 wire-format types + action**

在 attack_chain-actions.ts 末尾加：

```ts
// -- DYNAMIC FILTER (Phase 12c-Biii) --
// 与 B3 settings 同 pattern：在 action 文件就近 export wire-format type，
// 不通过 yarn generate-types-from-api（与已合 B 系列一致）.

/** Filter operator (与后端 io.veriguard.database.model.Filters.FilterOperator 对齐) */
export type FilterOperatorWire = 'eq' | 'not_eq' | 'contains' | 'not_contains' | 'starts_with' | 'not_starts_with' | 'empty' | 'not_empty';

/** Filter mode (与后端 FilterMode 对齐) */
export type FilterModeWire = 'and' | 'or';

export interface FilterWire {
  key: string;
  mode: FilterModeWire;
  values: string[];
  operator: FilterOperatorWire;
}

export interface FilterGroupWire {
  mode: FilterModeWire;
  filters: FilterWire[];
}

export interface AttackChainDynamicFilterInputWire {
  dynamic_filter: FilterGroupWire;
}

export const updateAttackChainDynamicFilter = (
  attackChainId: AttackChain['attack_chain_id'],
  input: AttackChainDynamicFilterInputWire,
) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${attackChainId}/dynamic_filter`;
  return putReferential(attack_chain, uri, input)(dispatch);
};
```

确保顶部 imports 含 `Dispatch` from 'redux'、`putReferential` from `'../../utils/Action'`、`AttackChain` from api-types、`attack_chain` from `'./attack_chain-schema'`（按现有 imports 实际名）。

- [ ] **Step B1.3：lint + check-ts**

```bash
cd veriguard-front
yarn check-ts 2>&1 | tail -5
npx eslint src/actions/attack_chains/attack_chain-actions.ts --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -5
```

Expected: check-ts 0 errors / lint 0 errors（如有 import 顺序问题 `--fix`）。

- [ ] **Step B1.4：Commit**

```bash
git add veriguard-front/src/actions/attack_chains/attack_chain-actions.ts
git commit -m "$(cat <<'EOF'
执行：动态 filter API client + wire-format types（Phase 12c-Biii Step B1）

- export FilterOperatorWire / FilterModeWire / FilterWire / FilterGroupWire
  / AttackChainDynamicFilterInputWire（与后端 Filters.FilterGroup 对齐）
- updateAttackChainDynamicFilter(id, input) action → PUT /api/attack_chains/{id}/dynamic_filter
- 与 B3 settings 同 pattern：action 文件就近定义 type，不走 generate-types-from-api

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task B2：dynamicContractsAdapter + 单测（TDD）

**Why:** ChainedTimeline 接 `dynamicContracts: NodeContract[]` prop，需 adapter 把后端 wire-format 数组转 ReactFlow 节点对象（含 dashed border 样式 + 固定位置布局）。

**Files:**

- Create: `veriguard-front/src/admin/components/attack_chains/attack_chain/runtime/dynamicContractsAdapter.ts`
- Create: `veriguard-front/src/admin/components/attack_chains/attack_chain/runtime/__tests__/dynamicContractsAdapter.test.ts`

### Steps

- [ ] **Step B2.1：先写失败测试**

```ts
// __tests__/dynamicContractsAdapter.test.ts
import { describe, expect, it } from 'vitest';

import { type NodeContract } from '../../../../../../utils/api-types';
import { toDynamicNodeViewModels } from '../dynamicContractsAdapter';

const contract = (overrides: Partial<NodeContract> = {}): NodeContract => ({
  injector_contract_id: `c-${Math.random()}`,
  injector_contract_labels: { en: 'Test contract' },
  ...overrides,
} as NodeContract);

describe('toDynamicNodeViewModels', () => {
  it('空数组 → 空列表', () => {
    expect(toDynamicNodeViewModels([])).toEqual([]);
  });

  it('null / undefined → 空列表', () => {
    expect(toDynamicNodeViewModels(null)).toEqual([]);
    expect(toDynamicNodeViewModels(undefined)).toEqual([]);
  });

  it('单 contract → 1 个节点，id 前缀 dynamic-', () => {
    const c = contract({ injector_contract_id: 'c-abc', injector_contract_labels: { en: 'phishing' } });
    const result = toDynamicNodeViewModels([c]);
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('dynamic-c-abc');
    expect(result[0].label).toBe('phishing');
  });

  it('多 contracts → 各自一节点，保持顺序', () => {
    const c1 = contract({ injector_contract_id: 'c-1' });
    const c2 = contract({ injector_contract_id: 'c-2' });
    const c3 = contract({ injector_contract_id: 'c-3' });
    const result = toDynamicNodeViewModels([c1, c2, c3]);
    expect(result.map(r => r.id)).toEqual(['dynamic-c-1', 'dynamic-c-2', 'dynamic-c-3']);
  });

  it('每个动态节点位置 y 固定，x 按 index 递增（避免重叠）', () => {
    const c1 = contract({ injector_contract_id: 'c-1' });
    const c2 = contract({ injector_contract_id: 'c-2' });
    const result = toDynamicNodeViewModels([c1, c2]);
    expect(result[0].position.x).toBeLessThan(result[1].position.x);
    expect(result[0].position.y).toBe(result[1].position.y); // y 固定
  });

  it('每个动态节点带 isDynamic=true 标记', () => {
    const c = contract();
    const result = toDynamicNodeViewModels([c]);
    expect(result[0].isDynamic).toBe(true);
  });

  it('contract.injector_contract_labels 缺 en → fallback 用 injector_contract_id', () => {
    const c = contract({
      injector_contract_id: 'c-fallback',
      injector_contract_labels: undefined,
    });
    const result = toDynamicNodeViewModels([c]);
    expect(result[0].label).toBe('c-fallback');
  });
});
```

- [ ] **Step B2.2：跑测试确认失败**

```bash
cd veriguard-front
yarn vitest run src/admin/components/attack_chains/attack_chain/runtime/__tests__/dynamicContractsAdapter.test.ts 2>&1 | tail -5
```

Expected: FAIL with "Cannot find module '../dynamicContractsAdapter'"。

- [ ] **Step B2.3：实现 adapter**

```ts
// dynamicContractsAdapter.ts
import { type NodeContract } from '../../../../../utils/api-types';

/**
 * 动态节点 view-model（ChainedTimeline 用，与 ReactFlow node 对齐结构）.
 */
export interface DynamicNodeViewModel {
  /** ReactFlow node id；前缀 'dynamic-' 避免与 UUID 节点冲突. */
  id: string;
  /** 节点显示文本（contract label fallback 到 contract_id）. */
  label: string;
  /** ReactFlow position（动态节点固定 y，x 按 index 递增）. */
  position: { x: number; y: number };
  /** 标记位：NodeAttackChainNodeWrapper 用此切 dashed border 视觉. */
  isDynamic: true;
  /** 原始 contract 引用（节点详情面板 / hover tooltip 用）. */
  contract: NodeContract;
}

const DYNAMIC_NODE_Y = 600; // 画布动态区固定 y（手动节点在 y < 600）
const DYNAMIC_NODE_X_STEP = 250;

/**
 * 后端 wire format `attack_chain_dynamic_contracts: NodeContract[]` →
 * ReactFlow 动态节点 view-model 列表.
 *
 * 与 §2.4 ChainedTimeline 现有节点共存：动态节点位置固定在 y=600 行，
 * x 按 index 递增 250px 间距，与手动节点（在 y < 600 区域）不重叠.
 */
export const toDynamicNodeViewModels = (
  contracts: readonly NodeContract[] | null | undefined,
): DynamicNodeViewModel[] => {
  if (!contracts) return [];
  return contracts.map((contract, index) => {
    const labels = contract.injector_contract_labels;
    const label = (labels?.en ?? contract.injector_contract_id) as string;
    return {
      id: `dynamic-${contract.injector_contract_id}`,
      label,
      position: {
        x: index * DYNAMIC_NODE_X_STEP,
        y: DYNAMIC_NODE_Y,
      },
      isDynamic: true as const,
      contract,
    };
  });
};
```

- [ ] **Step B2.4：跑测试确认通过**

```bash
yarn vitest run src/admin/components/attack_chains/attack_chain/runtime/__tests__/dynamicContractsAdapter.test.ts 2>&1 | tail -5
```

Expected: 7 passed.

- [ ] **Step B2.5：lint**

```bash
npx eslint src/admin/components/attack_chains/attack_chain/runtime/dynamicContractsAdapter.ts src/admin/components/attack_chains/attack_chain/runtime/__tests__/dynamicContractsAdapter.test.ts --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -3
```

Expected: 0 errors（如 prefer-default-export 抱怨，加 type alias export 凑两个 — 已 export DynamicNodeViewModel 应该够）。

- [ ] **Step B2.6：Commit**

```bash
git add veriguard-front/src/admin/components/attack_chains/attack_chain/runtime/dynamicContractsAdapter.ts \
        veriguard-front/src/admin/components/attack_chains/attack_chain/runtime/__tests__/dynamicContractsAdapter.test.ts
git commit -m "$(cat <<'EOF'
执行：动态节点 view-model adapter + 7 单测（Phase 12c-Biii Step B2）

toDynamicNodeViewModels: NodeContract[] → DynamicNodeViewModel[]
- ReactFlow node id = "dynamic-${contract.injector_contract_id}"（前缀避免 UUID 冲突）
- label 优先 contract_labels.en，缺则 fallback contract_id
- position y 固定 600（动态区），x 按 index 递增 250px（避免重叠）
- isDynamic=true 标记位让 NodeAttackChainNodeWrapper 切 dashed border 视觉

单测 7 场景：空 / null / undefined / 单 contract / 多 contracts 顺序 /
位置布局 / isDynamic 标记 / labels 缺失 fallback.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task B3：NodeAttackChainNodeWrapper 视觉区分动态节点

**Why:** 让 wrapper 在节点 data 含 `isDynamic=true` 标记时切渲染 — dashed 紫色 border + ↻ 角标 + 不可 click edit；运行画布下叠加 verdict 背景色（border 标语义 + 背景标 verdict）。

**Files:**

- Modify: `veriguard-front/src/components/nodes/NodeAttackChainNodeWrapper.tsx`

### Steps

- [ ] **Step B3.1：读现有 wrapper（B4 已落地）**

```bash
cat veriguard-front/src/components/nodes/NodeAttackChainNodeWrapper.tsx
```

理解现有 RuntimeNodeContext 派发：context=null → NodeAttackChainNode；context 非 null → DoubleLayerNode + Handle。本步在 wrapper 入口加第三分支：data.isDynamic → 动态节点渲染。

- [ ] **Step B3.2：加动态节点渲染分支**

修改 NodeAttackChainNodeWrapper.tsx，在 wrapper 函数顶部加：

```tsx
const NodeAttackChainNodeWrapper = (props: NodeProps<NodeAttackChainNode>) => {
  const runtimeContext = useContext(RuntimeNodeContext);

  // Phase 12c-Biii: 动态节点（dynamicContracts 派生）— 用 dashed border + ↻ 角标视觉
  if (props.data.isDynamic) {
    // 编辑器（runtimeContext=null）→ transparent + 紫色文字
    // 运行画布（runtimeContext 注入）→ NODE_LAYER_STATUS_STYLE 背景 + 白色文字
    // dashed 紫色 border 永远存在（标"动态"语义）
    const runtimeData = runtimeContext?.runtimeByNodeId.get(props.id);
    const layerStatus = runtimeData?.preventionStatus ?? null;
    const verdictBackground = layerStatus
      ? NODE_LAYER_STATUS_STYLE[layerStatus].background
      : 'transparent';
    const verdictColor = layerStatus
      ? NODE_LAYER_STATUS_STYLE[layerStatus].color
      : '#bb86fc';
    return (
      <Box
        sx={{
          position: 'relative',
          width: 200,
          padding: '8px 12px',
          background: verdictBackground,
          border: '2px dashed',
          borderColor: '#5e35b1',
          borderRadius: 1,
          color: verdictColor,
          cursor: 'default',
        }}
        data-testid="dynamic-node"
      >
        <Box
          sx={{
            position: 'absolute',
            top: -6,
            right: -6,
            background: '#5e35b1',
            color: '#ffffff',
            borderRadius: '50%',
            width: 18,
            height: 18,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 11,
          }}
        >
          ↻
        </Box>
        <Stack>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
            {props.data.label}
          </Typography>
          <Typography variant="caption" sx={{ opacity: 0.7, lineHeight: 1.2 }}>
            动态 (filter)
          </Typography>
        </Stack>
        {props.data.isTargeted && (
          <Handle type="target" id={`target-${props.data.key}`} position={Position.Left} isConnectable={false} />
        )}
        {props.data.isTargeting && (
          <Handle type="source" id={`source-${props.data.key}`} position={Position.Right} isConnectable={false} />
        )}
      </Box>
    );
  }

  // 现有逻辑：runtime context 派发
  if (!runtimeContext) {
    return <NodeAttackChainNodeComponent {...props} />;
  }
  // ... rest unchanged
};
```

补 imports：

```ts
import { Stack, Typography } from '@mui/material';
import { NODE_LAYER_STATUS_STYLE } from '../../admin/components/attack_chain_runs/attack_chain_run/runtime/attackChainRuntimeTypes';
```

`Box` 已 imported。

- [ ] **Step B3.3：扩展 NodeAttackChainNode data 类型加 isDynamic / label 字段**

`isDynamic` / `label` 是动态节点专用字段；NodeAttackChainNode 现有 `data` 类型由 `NodeAttackChainNode` type alias 定义在 `NodeAttackChainNode.tsx`：

```ts
export type NodeAttackChainNode = Node<{
  // ... existing fields ...
  isDynamic?: boolean;  // Phase 12c-Biii: 动态节点标记
  // label 已存在
}>;
```

加 `isDynamic?: boolean` 到现有 type。手动节点不传此字段（=undefined → falsy → 走原渲染）。

- [ ] **Step B3.4：跑 check-ts + 现有测试**

```bash
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | tail -5
```

Expected: check-ts 0 / 全套测试 0 回归（baseline 398 + 7 新 adapter test = 405）。

- [ ] **Step B3.5：lint + commit**

```bash
npx eslint src/components/nodes/NodeAttackChainNodeWrapper.tsx src/components/nodes/NodeAttackChainNode.tsx --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -3
git add veriguard-front/src/components/nodes/NodeAttackChainNodeWrapper.tsx \
        veriguard-front/src/components/nodes/NodeAttackChainNode.tsx
git commit -m "$(cat <<'EOF'
执行：NodeAttackChainNodeWrapper 加动态节点渲染分支（Phase 12c-Biii Step B3）

data.isDynamic=true 时切 dashed 紫色 border + ↻ 角标 + "动态 (filter)"
副标题渲染（B3 一次性完整实施）：
- runtimeContext=null（编辑器）→ transparent 背景 + #bb86fc 文字
- runtimeContext 注入（运行画布）→ NODE_LAYER_STATUS_STYLE 取 preventionStatus
  对应 background + color（B4 已建颜色源）
- dashed 紫色 border 永远存在（标"动态"语义）
- 不可 click edit；保留 ReactFlow Handle 维持 edge 连接

NodeAttackChainNode type 加 isDynamic?: boolean 字段（手动节点不传 = falsy 不影响）.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task B4：ChainedTimeline 接 `dynamicContracts` prop

**Why:** ChainedTimeline 是编辑器+运行画布共享主体；接 prop `dynamicContracts: NodeContract[]`，调 adapter 派生动态节点 + 与现有手动节点合并到 ReactFlow nodes 数组。

**Files:**

- Modify: `veriguard-front/src/components/ChainedTimeline.tsx`

### Steps

- [ ] **Step B4.1：读现有 ChainedTimeline.tsx 节点数组组装位置**

```bash
grep -nE 'setFlowNodes\|flowNodes\|useNodesState\|dynamicContracts' veriguard-front/src/components/ChainedTimeline.tsx | head
```

定位 nodes 数组生成逻辑（应该有类似 `setFlowNodes(nodes.map(...))`）。

- [ ] **Step B4.2：加 prop + 合并动态节点**

修改 ChainedTimeline.tsx Props interface：

```ts
interface Props {
  nodes: AttackChainNodeOutputType[];
  dynamicContracts?: NodeContract[];  // Phase 12c-Biii: 动态节点 contracts（默认 [] 不影响）
  // ... existing props ...
}
```

函数解构加 default：

```ts
const ChainedTimelineFlow: FunctionComponent<Props> = ({
  nodes,
  dynamicContracts = [],
  // ... existing ...
}) => {
```

加 import：

```ts
import { type NodeContract } from '../utils/api-types';
import { toDynamicNodeViewModels } from '../admin/components/attack_chains/attack_chain/runtime/dynamicContractsAdapter';
import { useMemo } from 'react';  // 如尚未 imported
```

在 nodes 数组生成处（已有 `setFlowNodes(...)` 调用）后追加合并逻辑。例如，若现状是：

```ts
useEffect(() => {
  setFlowNodes(buildNodesFromAttackChain(nodes));
}, [nodes]);
```

改为：

```ts
const dynamicViewModels = useMemo(
  () => toDynamicNodeViewModels(dynamicContracts),
  [dynamicContracts],
);

useEffect(() => {
  const manualNodes = buildNodesFromAttackChain(nodes);
  const dynamicReactFlowNodes = dynamicViewModels.map(vm => ({
    id: vm.id,
    type: 'node',  // 与现有 nodeTypes registry 一致
    position: vm.position,
    data: {
      key: vm.id,
      label: vm.label,
      isDynamic: true,  // NodeAttackChainNodeWrapper 用此切渲染
      isTargeted: false,  // 动态节点无依赖
      isTargeting: false,
      // 占位：手动节点 data 必须的字段（onSelectedAttackChainNode 等）传 no-op
      onSelectedAttackChainNode: () => {},
      onCreate: () => {},
      onUpdate: () => {},
      onDelete: () => {},
      targets: [],
    },
  }));
  setFlowNodes([...manualNodes, ...dynamicReactFlowNodes]);
}, [nodes, dynamicViewModels]);
```

注：`buildNodesFromAttackChain` 是占位函数名 — 按现有 ChainedTimeline 实际逻辑调整（可能是 inline lambda 而非 helper function）。

- [ ] **Step B4.3：跑 check-ts + 现有测试不破**

```bash
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | tail -5
```

Expected: 0 type errors / 全套测试 0 回归。

- [ ] **Step B4.4：lint + commit**

```bash
npx eslint src/components/ChainedTimeline.tsx --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -3
git add veriguard-front/src/components/ChainedTimeline.tsx
git commit -m "$(cat <<'EOF'
执行：ChainedTimeline 接 dynamicContracts prop（Phase 12c-Biii Step B4）

dynamicContracts?: NodeContract[] (default []) 通过 adapter
toDynamicNodeViewModels 派生动态节点，与现有手动节点合并到 flowNodes：
- 动态节点 data.isDynamic=true → NodeAttackChainNodeWrapper 切 dashed border 渲染
- 动态节点 data.isTargeted/isTargeting=false → 无 ReactFlow Handle 连接
- 编辑器 host 不传 prop 时 [] 默认 → 现有手动节点行为零变化（backward compat）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task B5：DynamicFilterDrawer + 编辑器 host 接线 + i18n

**Files:**

- Create: `veriguard-front/src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx`
- Modify: `veriguard-front/src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx`
- Modify: `veriguard-front/src/utils/lang/zh.json` + `en.json`

### Steps

- [ ] **Step B5.1：先看 AssetGroup form 找现有 FilterGroup 编辑器 component**

```bash
grep -rn 'FilterGroup\|FilterChips\|asset_group_dynamic_filter' veriguard-front/src/admin/components/assets/asset_groups --include='*.tsx' --include='*.ts' | head -10
```

定位 OpenBAS AssetGroup form 内的 FilterGroup 编辑器组件（如 `<FiltersGroup>` / `<FilterChips>`）— 直接复用。

- [ ] **Step B5.2：写 DynamicFilterDrawer**

```tsx
// AttackChainDynamicFilterDrawer.tsx
import { Drawer, Box, Stack, Button, Typography } from '@mui/material';
import { type FunctionComponent, useState, useEffect } from 'react';

import { type AttackChain, type NodeContract } from '../../../../utils/api-types';
import {
  type FilterGroupWire,
  updateAttackChainDynamicFilter,
} from '../../../../actions/attack_chains/attack_chain-actions';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';
// import { FiltersGroup } from '<复用的 OpenBAS filter editor>';  // Step B5.1 找到的组件路径

interface Props {
  attackChain: AttackChain;
  open: boolean;
  onClose: () => void;
  /** 实时预览匹配 contracts 数量（前端调 NodeContract search 端点）— 占位接口，
   *  本 step 暂不实现实时 search，drawer Save 后由 helper.fetchAttackChain 触发后端
   *  attack_chain_dynamic_contracts @Transient 字段 refresh. */
  previewContracts?: NodeContract[];
}

const AttackChainDynamicFilterDrawer: FunctionComponent<Props> = ({
  attackChain,
  open,
  onClose,
  previewContracts = [],
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [draftFilter, setDraftFilter] = useState<FilterGroupWire>(
    attackChain.attack_chain_dynamic_filter ?? { mode: 'and', filters: [] },
  );

  useEffect(() => {
    if (open) {
      setDraftFilter(attackChain.attack_chain_dynamic_filter ?? { mode: 'and', filters: [] });
    }
  }, [open, attackChain]);

  const handleSave = () => {
    dispatch(updateAttackChainDynamicFilter(
      attackChain.attack_chain_id,
      { dynamic_filter: draftFilter },
    ));
    onClose();
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ sx: { width: { xs: '100%', sm: 440 } } }}
    >
      <Box sx={{ padding: 3 }}>
        <Typography variant="h5" sx={{ marginBottom: 2 }}>
          {t('Edit dynamic filter')}
        </Typography>
        {/* FilterGroup 编辑器：在 Step B5.1 grep 找到 OpenBAS 已有组件路径后导入并绑 draftFilter / setDraftFilter；
            如 grep 找不到通用组件，按 AssetGroup form 内 FilterGroup 编辑器的实际 JSX 拷贝过来（最简：
            list of <Filter chip> + add filter button + mode toggle）。占位 placeholder 由 Step B5.1 替换。 */}
        <Box sx={{ marginBottom: 2, padding: 2, background: 'rgba(0,0,0,0.04)', borderRadius: 1 }}>
          <Typography variant="caption" color="text.secondary">
            FilterGroup 编辑器（复用 OpenBAS 组件 / 按 AssetGroup form 拷贝实现）
          </Typography>
        </Box>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', marginBottom: 2 }}>
          {t('{n} contracts match', { n: previewContracts.length })}
        </Typography>
        <Stack direction="row" spacing={1} justifyContent="flex-end">
          <Button onClick={onClose}>{t('Cancel')}</Button>
          <Button variant="contained" onClick={handleSave}>{t('Save')}</Button>
        </Stack>
      </Box>
    </Drawer>
  );
};

export default AttackChainDynamicFilterDrawer;
```

注：FiltersGroup editor 复用具体路径在 Step B5.1 grep 结果决定。如 OpenBAS 组件路径是 `'../../../common/queryable/filter/FiltersGroup'` 或类似，import 后绑定 draftFilter。如查不到现成组件，drawer 内用基础 `<input>` 串拼 filter expression（不推荐 — 用户已说复用 AssetGroup form）。

- [ ] **Step B5.3：编辑器 host 接 drawer**

修改 `AttackChainAttackChainNodes.tsx`，加：

```ts
import AttackChainDynamicFilterDrawer from '../AttackChainDynamicFilterDrawer';
import { type NodeContract } from '../../../../../utils/api-types';
// ... existing imports ...

// state
const [dynamicFilterDrawerOpen, setDynamicFilterDrawerOpen] = useState(false);
```

在 chain 数据 destructure 处加 `attack_chain_dynamic_contracts`：

```ts
const { attack_chain, ... } = useHelper(...);
const dynamicContracts: NodeContract[] = attack_chain.attack_chain_dynamic_contracts ?? [];
```

在 toolbar 加按钮（与现有 "链路设置" 按钮同行）：

```tsx
<Button
  startIcon={<RepeatIcon />}
  onClick={() => setDynamicFilterDrawerOpen(true)}
>
  {t('Dynamic content ({n})', { n: dynamicContracts.length })}
</Button>
```

补 import `RepeatIcon` from `@mui/icons-material/Repeat`。

把 drawer 加到 render：

```tsx
<AttackChainDynamicFilterDrawer
  attackChain={attack_chain}
  open={dynamicFilterDrawerOpen}
  onClose={() => setDynamicFilterDrawerOpen(false)}
  previewContracts={dynamicContracts}
/>
```

把 `dynamicContracts` 传给 ChainedTimeline（在编辑器 chain viewMode 渲染处）：

```tsx
<ChainedTimeline
  nodes={...}
  dynamicContracts={dynamicContracts}
  // ... existing props ...
/>
```

- [ ] **Step B5.4：i18n 加 keys**

`zh.json`（在 Attack chain orchestration 附近按字母序插入）：

```diff
+  "Dynamic content ({n})": "动态用例 ({n})",
+  "Edit dynamic filter": "编辑动态筛选",
+  "{n} contracts match": "匹配 {n} 个用例",
+  "No dynamic contracts": "暂无动态用例",
+  "Filter mode": "筛选模式",
+  "Save": "保存",
+  "Cancel": "取消",
```

`en.json` 对应：

```diff
+  "Dynamic content ({n})": "Dynamic content ({n})",
+  "Edit dynamic filter": "Edit dynamic filter",
+  "{n} contracts match": "{n} contracts match",
+  "No dynamic contracts": "No dynamic contracts",
+  "Filter mode": "Filter mode",
+  "Save": "Save",
+  "Cancel": "Cancel",
```

注：`Save` / `Cancel` 可能已存在（OpenBAS 通用），先 grep 确认避免 duplicate key。

```bash
grep '"Save"\|"Cancel"' src/utils/lang/zh.json src/utils/lang/en.json | head
```

如已存在跳过对应行。

- [ ] **Step B5.5：JSON + check-ts + lint + 测试**

```bash
node -e "JSON.parse(require('fs').readFileSync('src/utils/lang/zh.json','utf8')); JSON.parse(require('fs').readFileSync('src/utils/lang/en.json','utf8')); console.log('JSON OK')"
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | tail -3
npx eslint src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -3
```

Expected: 全清。

- [ ] **Step B5.6：Commit**

```bash
git add veriguard-front/src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx \
        veriguard-front/src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx \
        veriguard-front/src/utils/lang/zh.json \
        veriguard-front/src/utils/lang/en.json
git commit -m "$(cat <<'EOF'
执行：DynamicFilterDrawer + 编辑器 host 接线 + i18n（Phase 12c-Biii Step B5）

- AttackChainDynamicFilterDrawer.tsx：右侧 drawer，draftFilter state 编辑
  + Save 触发 updateAttackChainDynamicFilter PUT 端点 + onClose
  + 显示 "{n} contracts match" 实时预览
  + FilterGroup editor 占位（B5.1 grep 决定具体复用 OpenBAS 组件路径）
- AttackChainAttackChainNodes.tsx：toolbar 加 "Dynamic content (N)" 按钮
  → drawer；从 helper attack_chain_dynamic_contracts 派生 dynamicContracts；
  传给 ChainedTimeline 渲染动态节点
- i18n: zh + en 加 5-7 keys（Dynamic content / Edit dynamic filter /
  {n} contracts match / No dynamic contracts / Filter mode / Save / Cancel
  按已存在情况跳过 dup）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task B6：运行画布 host 接线 + verdict 着色

**Why:** 运行画布也要显示动态节点 + 用 verdict 着色（border = 动态语义 / 背景 = verdict status）。

**Files:**

- Modify: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx`

注：B3 已完整实现 NodeAttackChainNodeWrapper 的 verdict 着色（runtimeContext 注入时取 NODE_LAYER_STATUS_STYLE）；B6 仅做 host wire 即可让 verdict 自动生效。

### Steps

- [ ] **Step B6.1：运行画布 host 拿 dynamic_contracts + 传给 ChainedTimeline**

修改 `AttackChainRunAttackChainNodes.tsx`：

helper 中确认 `attack_chain_run.attack_chain.attack_chain_dynamic_contracts` 是否可访问（取决于运行画布 helper 是否 expose chain.dynamic_contracts）。如不可访问，加：

```ts
const dynamicContracts: NodeContract[] = attack_chain_run.attack_chain?.attack_chain_dynamic_contracts ?? [];
```

传给 ChainedTimeline（在 chain viewMode 分支）：

```tsx
<AttackChainNodes
  // ... existing props ...
  dynamicContracts={dynamicContracts}  // 通过 AttackChainNodes 共享组件传透到 ChainedTimeline
/>
```

如 `<AttackChainNodes>` 共享组件不接受 `dynamicContracts` prop，看是否能在它内部派生 chain.attack_chain_dynamic_contracts；否则需扩 AttackChainNodes prop（同步加 prop + 透传到 ChainedTimeline）。

- [ ] **Step B6.2：跑 check-ts + 测试**

```bash
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | tail -3
```

Expected: 0 type errors / 0 回归。

- [ ] **Step B6.3：lint + commit**

```bash
npx eslint src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -3
git add veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx
git commit -m "$(cat <<'EOF'
执行：运行画布 host 接动态节点（Phase 12c-Biii Step B6）

运行画布 host 从 attack_chain_run.attack_chain.attack_chain_dynamic_contracts
派生 dynamicContracts，传给 ChainedTimeline 渲染.

B3 已完整实现 NodeAttackChainNodeWrapper 在 runtimeContext 下的 verdict 着色
（NODE_LAYER_STATUS_STYLE preventionStatus → background + color），运行画布
host 注入 RuntimeNodeContext 后动态节点自动呈现：
- dashed 紫色 border（标"动态"语义）
- 背景 verdict 着色（绿/红/橙/灰 = SUCCESS/FAILED/PARTIAL/PENDING）
- 双重信息：border + 背景

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task B7：PR-B 验证 + push + PR

### Steps

- [ ] **Step B7.1：跑 PR-B 全套**

```bash
cd /Users/lamba/github/Veriguard/worktrees/phase-12c-Biii-frontend/veriguard-front
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | tail -5
node -e "JSON.parse(require('fs').readFileSync('src/utils/lang/zh.json','utf8')); JSON.parse(require('fs').readFileSync('src/utils/lang/en.json','utf8')); console.log('JSON OK')"
npx eslint \
  src/actions/attack_chains/attack_chain-actions.ts \
  src/admin/components/attack_chains/attack_chain/runtime/dynamicContractsAdapter.ts \
  src/admin/components/attack_chains/attack_chain/runtime/__tests__/dynamicContractsAdapter.test.ts \
  src/components/nodes/NodeAttackChainNodeWrapper.tsx \
  src/components/nodes/NodeAttackChainNode.tsx \
  src/components/ChainedTimeline.tsx \
  src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx \
  src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx \
  src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx \
  src/utils/lang/zh.json src/utils/lang/en.json \
  --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -5
```

Expected: 全 0 errors / 全套测试 0 回归（baseline 398 + 7 adapter test = 405 passed）。

- [ ] **Step B7.2：master 锁验证**

```bash
git -C /Users/lamba/github/Veriguard ls-remote origin master | head -1
```

Expected: `5d7e05da627639491274175d2a77cf95b2c0fe7d	refs/heads/master`。

- [ ] **Step B7.3：push branch**

```bash
git push -u origin feat/attack-chain-phase-12c-Biii-frontend 2>&1 | tail -3
```

- [ ] **Step B7.4：创建 PR-B**

```bash
gh pr create --base main --title "二开 Phase 12c-Biii PR-B: 前端动态用例集 drawer + 画布渲染" --body "$(cat <<'EOF'
## Summary

Phase 12c-Biii 前端，PR-A 后端已合并到 main。

设计稿：\`docs/superpowers/specs/2026-05-10-veriguard-dynamic-content-design.md\`
实施计划：\`docs/superpowers/plans/2026-05-10-veriguard-dynamic-content.md\`

6 个 commit (Step B1-B6)：

- **B1** API client + wire-format types (FilterGroupWire / updateAttackChainDynamicFilter)
- **B2** dynamicContractsAdapter (toDynamicNodeViewModels) + 7 单测
- **B3** NodeAttackChainNodeWrapper 加动态节点渲染分支 (dashed border + ↻ 角标)
- **B4** ChainedTimeline 接 dynamicContracts prop (合并到 flowNodes)
- **B5** DynamicFilterDrawer + 编辑器 host 接线 + i18n
- **B6** 运行画布 host wire + Wrapper verdict 着色（border = 动态 / 背景 = verdict）

## Test plan

- [x] yarn check-ts 0 errors
- [x] yarn test 405 passed (398 baseline + 7 adapter)
- [x] npx eslint touched files 0 errors
- [x] origin/master 仍锁 5d7e05da6
- [ ] dev server 视觉验证：编辑器 toolbar "Dynamic content (N)" 按钮点开 drawer
- [ ] dev server 视觉验证：动态节点 dashed 紫色 border + ↻ 角标
- [ ] dev server 视觉验证：运行画布动态节点 border + verdict 背景双重信息

## 范围 boundary（spec §8）

不做：跨 chain 共享 saved query / 动态节点精细编排 / 多 dynamicFilter / Convert to static / sub-techniques 支持.
EOF
)"
```

---

## 最终检查 (双 PR 全过)

- [ ] PR-A 已 MERGED 到 main（origin/master 仍锁 5d7e05da6）
- [ ] PR-B base=main（PR-A merged 后），CLEAN MERGEABLE
- [ ] 完成 PR-B merge 后清理 worktree + branch

---

## 自审 checklist

完成所有 task 后做最后一遍：

- [ ] PRD §2.3 第 4 行（filter 编辑 = 创建动态用例集）覆盖（spec §9 完成标准#1）
- [ ] PRD §2.3 第 5 行（实时联动 — service 每次派生）覆盖（spec §9#2）
- [ ] 编辑器 + 运行画布两处都正确显示动态节点（spec §9#3）
- [ ] 现有 §2.4 编辑能力（手动节点精细编排）零受损（spec §9#4）
- [ ] master 不动 + PR base=main（spec §9#5）

---

## 范围 boundary 提醒（spec §8 — 本 plan 不做）

- ❌ 跨 chain 共享 saved query (独立 SavedQuery entity)
- ❌ "Convert dynamic to static" 操作
- ❌ 动态节点的依赖关系编辑
- ❌ 动态节点的重复执行 / 时延配置
- ❌ 多 dynamicFilter (一个 chain 多个 filter)
- ❌ filter expression 编辑器全功能扩展（仅复用 OpenBAS 现有）
- ❌ 动态节点 expectation 自定义
- ❌ 跨链路全局动态用例覆盖度
