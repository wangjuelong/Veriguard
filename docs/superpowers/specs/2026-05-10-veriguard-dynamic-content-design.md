# Spec: 攻击链路动态用例集（B-iii / Phase 12c-Biii）

> 设计日期：2026-05-10
> PRD 来源：`docs/prd/产品要求.md` §2.3 第 4 + 5 行（筛选→创建场景 + 自动联动）
> Workstream 拆解：B（§2.3 自定义验证）→ B-iii（动态场景 saved-query 机制）
> 状态：design approved，待 writing-plans 进入实施
> 相关已合 PR：Phase 0-12 + 12b-A/B/C/D + 12c-Bi（main = `923bf06f9`）

---

## 1. 问题陈述

PRD §2.3 第 4+5 行要求：

- **第 4 行**：「支持用户从全量用例中进行筛选，并将筛选得到的用例创建为自定义验证场景。」
- **第 5 行**：「支持场景包含的具体用例范围根据筛选条件自动变化；当用例更新时，符合筛选条件的用例自动进入自定义场景。」

**当前缺口**：

- AttackChain（场景）的节点列表 100% 由用户手动 add，**无 saved-query 机制**
- 新增/更新的 NodeContract 不会自动联动到现有 chain
- 用户期望「批量覆盖」语义（如"ATT&CK Reconnaissance 战术下所有 contracts 全跑"）需手动 add 每条 contract

**数据模型基础设施已具**：
- `Filters.FilterGroup` 已存在（mode AND/OR + List<Filter>）
- `AssetGroup.dynamicFilter` 是 OpenBAS 已落地的"双轨制（手动成员 + 动态成员）"先例
- `NodeContractService.searchContracts(filterGroup)` 已支持按 FilterGroup 过 NodeContract
- `Specification` JPA Criteria 框架已用于 NodeContract 搜索

---

## 2. 设计决策（已与用户协商）

| 决策点 | 选择 | 理由 |
|---|---|---|
| "用例" 在 OpenBAS 模型对应 | **`NodeContract`**（也称 `injector_contract`） | NodeContract 关联 attack_patterns / kill_chain_phases / domains / payload，符合 PRD §2.3 第 3 行 6 类用例语义 |
| 动态 contracts 与 AttackChain 节点的关系 | **A. OpenBAS 双轨制**：手动节点（精细编排）+ 动态 contracts 自动 instantiate 为简化节点 | 复用 `AssetGroup.dynamicFilter` 成熟模式；完整满足 PRD 第 4+5 行；与 §2.4 编排能力**互补**而非冲突 |
| 动态节点执行语义 | **1. 全平行 t=0**（无依赖、无时延、默认 1 repeat） | 动态用例集本质是"集合"非"流程"；与手动节点解耦；OpenBAS AssetGroup 同构（动态成员是扁平集合） |
| 实施方案 | **方案 1：AttackChain 加 `dynamicFilter` 字段（与 AssetGroup 同构）** | DRY；OpenBAS 已有先例；零创新风险；PRD 没要求跨 chain 共享 query（不需独立 SavedQuery entity） |
| 编辑器入口 | **toolbar "Dynamic content (N)" 按钮 → Drawer** | 与已落地的"链路设置" / view-mode toggle 同行排版；N 显示当前匹配数量 |
| 动态节点视觉区分 | **X. Dashed border + 紫色 + ↻ 角标**（运行画布下叠加 verdict 背景色）| 视觉一眼区分手动 vs 动态；运行时双重信息（border = 动态语义 + 背景 = verdict）|
| 动态节点 `attack_chain_nodes` 表占位 | **持久化 + `is_dynamic=true` 标记 + run 结束 cleanup**（A4 design fix 2026-05-11）| 原 "不写表" 假设基于错误的 executor 路径调研。实际 `Executor.execute()` → `AttackChainNodeStatusService.initializeAttackChainNodeStatus()` → `attackChainNodeRepository.findById(...).orElseThrow()` 硬要求节点已持久化。改为写表 + 标记 + cleanup → 复用整套现有 executor/expectation/verdict 链；避免引入并行执行路径（双倍维护成本 + drift 风险） |
| 1 chain 多个 dynamicFilter | **不支持**（1 chain = 1 filter） | 与 AssetGroup 一致；YAGNI |

---

## 3. 架构

### 3.1 文件清单

**后端：**

| 文件 | 类型 | 改动 |
|---|---|---|
| `veriguard-api/src/main/resources/db/migration/V5__attack_chain_dynamic_filter.sql` | 新（Flyway 5）| `ALTER TABLE attack_chains ADD COLUMN dynamic_filter JSONB NOT NULL DEFAULT '{"mode":"and","filters":[]}'::jsonb`（main 已有 V4__attack_chain_rename_es_reindex，故从 V5 起）|
| `veriguard-api/src/main/resources/db/migration/V6__attack_chain_node_is_dynamic.sql` | 新（Flyway 6）| `ALTER TABLE attack_chain_nodes ADD COLUMN is_dynamic BOOLEAN NOT NULL DEFAULT FALSE` + 部分索引 `CREATE INDEX idx_attack_chain_nodes_dynamic ON attack_chain_nodes (attack_chain_id) WHERE is_dynamic = TRUE` 加速 cleanup query（A4 design fix）|
| `veriguard-model/src/main/java/io/veriguard/database/model/AttackChain.java` | 改 | 加 `FilterGroup dynamicFilter` + `@Transient List<NodeContract> dynamicContracts` |
| `veriguard-model/src/main/java/io/veriguard/database/model/AttackChainNode.java` | 改 | 加 `@Column(name = "is_dynamic") private boolean isDynamic = false`（A4 design fix）|
| `veriguard-model/src/main/java/io/veriguard/database/repository/AttackChainNodeRepository.java` | 改 | 加 cleanup query：`@Modifying @Query` 删除 `attack_chain_id` + `is_dynamic = true` 匹配的所有动态节点（A4 design fix）|
| `veriguard-api/src/main/java/io/veriguard/service/attack_chain/AttackChainService.java` | 改 | 加 `computeDynamicContracts(AttackChain): List<NodeContract>` |
| `veriguard-api/src/main/java/io/veriguard/scheduler/jobs/AttackChainNodesExecutionJob.java` | 改 | (a) `handleAutoStartAttackChainRuns`：转 RUNNING 前为每个 starting run 派生 + 持久化 dynamic 节点；(b) `handleAutoClosingAttackChainRuns`：转 FINISHED 后调 cleanup 删除该 chain 的所有 is_dynamic=true 节点（A4 design fix）|
| `veriguard-api/src/main/java/io/veriguard/rest/attack_chain/AttackChainApi.java` | 改 | 加 `PUT /api/attack_chains/{id}/dynamic_filter` 端点 |
| `veriguard-api/src/main/java/io/veriguard/rest/attack_chain/form/AttackChainDynamicFilterInput.java` | 新（DTO）| record `(@JsonProperty("dynamic_filter") FilterGroup dynamicFilter)` |
| `veriguard-api/src/test/java/io/veriguard/service/attack_chain/AttackChainServiceDynamicContractsTest.java` | 新 | service 单测 8 场景 |
| `veriguard-api/src/test/java/io/veriguard/scheduler/jobs/AttackChainNodesExecutionJobDynamicContractsTest.java` | 新 | 执行 job 单测 ≥3 场景：start hook 派生 + save / 空 filter short-circuit / close hook cleanup 调用（A4 design fix）|
| `veriguard-api/src/test/java/io/veriguard/rest/attack_chain/AttackChainApiDynamicFilterTest.java` | 新 | PUT endpoint 单测 4 场景 |

**前端：**

| 文件 | 类型 | 改动 |
|---|---|---|
| `veriguard-front/src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx` | 新 | drawer 内嵌 FilterGroup 编辑器（复用 OpenBAS `FilterChips` / AssetGroup form 现有组件）+ 实时 contracts 数量预览 + Save 触发 PUT |
| `veriguard-front/src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx` | 改 | 编辑器 toolbar 加 "Dynamic content (N)" 按钮 → 触发 drawer；helper 拿 dynamicContracts 派生注入 ChainedTimeline |
| `veriguard-front/src/components/ChainedTimeline.tsx` | 改 | 接 `dynamicContracts: NodeContract[]` prop（默认 []），把每个 contract 渲染为临时节点（id=`dynamic-${contract_id}`，position=独立行，与手动节点共存）|
| `veriguard-front/src/components/nodes/NodeAttackChainNodeWrapper.tsx` | 改 | 通过 props 或 RuntimeNodeContext 区分动态节点 → dashed border + 紫色 + ↻ badge + 禁止 click edit；运行画布下叠加 verdict 背景色 |
| `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx` | 改 | 运行画布拿 chain.attack_chain_dynamic_contracts，传给 ChainedTimeline + 用同一 verdict 着色逻辑 |
| `veriguard-front/src/actions/attack_chains/attack_chain-actions.ts` | 改 | `updateAttackChainDynamicFilter(id, filter)` action |
| `veriguard-front/src/admin/components/.../runtime/dynamicContractsAdapter.ts` | 新 | adapter：`NodeContract[] → DynamicNodeViewModel[]`（含位置布局）|
| `veriguard-front/src/admin/components/.../runtime/__tests__/dynamicContractsAdapter.test.ts` | 新 | adapter 单测 ≥6 场景 |
| `veriguard-front/src/utils/lang/zh.json` + `en.json` | 改 | 加 5-6 keys（`Dynamic content` / `Dynamic contracts` / `N contracts match` / `Edit dynamic filter` / `Filter mode` / `No dynamic contracts`） |

### 3.2 数据流

**编辑时：**

```
用户在编辑器点 "Dynamic content" → drawer 打开
  → drawer 渲染当前 dynamicFilter（从 AttackChain helper getter）
  → 用户改 filter → 触发实时预览（前端调 NodeContract search 端点 with filter）
  → "N contracts match" 显示
  → 用户点 Save
  → POST → 后端 PUT /api/attack_chains/{id}/dynamic_filter → 写 attack_chains.dynamic_filter
  → 前端 dispatch fetchAttackChain(id) → helper 缓存更新
  → 编辑器 ChainedTimeline 拿 chain.attack_chain_dynamic_contracts（后端 @Transient 字段）→ 渲染动态节点
```

**运行时（A4 design fix 2026-05-11）：**

```
chain run 启动（handleAutoStartAttackChainRuns）
  → 在 setStatus(RUNNING) 前对每个 starting run：
    → service.computeDynamicContracts(chain) 用 Specification 过 NodeContractRepository
    → 每个 contract → new AttackChainNode：
        - id = "dynamic-${contract_id}-${run_id}"（前缀 + run_id 避免跨 run / 与 UUID 冲突）
        - is_dynamic = true（V6 加列 + 标记位）
        - attack_chain = chain / node_injector_contract = contract
        - depends_duration = 0 / repeat_count = 1 / 无依赖
      → attackChainNodeRepository.save(node)（持久化，让现有 executor 链可走通）
  → 现有 executor 链遍历 chain.getAttackChainNodes() → 手动 + 动态节点统一调度
  → expectation/status/verdict 全链路复用

chain run 结束（handleAutoClosingAttackChainRuns）
  → setStatus(FINISHED) 之后、instantiateForRun/evaluateForRun 之前：
    → attackChainNodeRepository.deleteByAttackChainIdAndIsDynamicTrue(chain.id)
    → 清理本 chain 的所有动态节点（手动节点保留）
```

**关键决策：动态节点 ID**
- 持久化 ID：`dynamic-${contract_id}-${run_id}`（含 run_id 避免跨 run 同 contract 主键冲突；前缀 `dynamic-` 让前端识别走 dashed border）
- 前端 ReactFlow 节点 id 同此格式（编辑器画布 view-model adapter 略去 run_id 用 `dynamic-${contract_id}`，因编辑器无 run；运行画布读 persisted node id）

### 3.3 后端兼容

- `attack_chains.dynamic_filter` 默认 `{"mode":"and","filters":[]}` → 现有 chain 语义不变（filter 空 = 无动态 contracts）
- `attack_chain_nodes.is_dynamic` 新列默认 false → 现有手动节点完全不受影响（A4 design fix）
- `AttackChainNodesExecutionJob` 在 `dynamicFilter` 空时 short-circuit（service.computeDynamicContracts 返 [] → 不 save 任何动态节点 → 不污染表）
- 现有 chain run 端点 wire format **加新字段** `attack_chain_dynamic_contracts`（@Transient @JsonProperty 派生，nullable），现有 caller 不感知此字段无影响
- Cleanup hook：run 转 FINISHED 时清；ERROR / TIMEOUT 路径理论上脏数据残留可接受（下次 run 启动时 deleteBy 会覆盖；如要严谨可在 A4 后续追加 ERROR 分支 cleanup）

---

## 4. 视觉区分

### 4.1 编辑器画布

- 手动节点：实色背景（按 §2.4 contract type 着色）+ 实线 border
- 动态节点：透明背景 / dashed 紫色 border + ↻ 角标 + 副标题 "动态 (filter)"
- 动态节点不可 click edit（onClick no-op）；可 hover 显示 contract 信息 tooltip

### 4.2 运行画布

- 手动节点：现状（B4 双层卡 verdict 着色）
- 动态节点：dashed 紫色 border + ↻ 角标 + **背景色 = verdict color**（来自 `NODE_LAYER_STATUS_STYLE`）
  - 双重信息：border 标"动态"语义 + 背景标 verdict 状态

### 4.3 Drawer 内 FilterGroup 编辑器

- 复用 OpenBAS 现有 FilterGroup 编辑器（AssetGroup form 内已有）
- 实时预览面板：显示匹配 contracts 数量 + 前 N 条 contract 名（默认前 10 条 + "and M more..."）

---

## 5. 测试策略

| 测试类型 | 文件 | 核心场景 |
|---|---|---|
| 后端 service 单测（重点）| `AttackChainServiceDynamicContractsTest.java` | ≥8 场景：空 filter / 单 filter (eq attack_pattern) / 多 filter AND / 多 filter OR / contract 不匹配 / not_empty operator / 非法 filter key 防御 / 大量 contracts 性能（mock 100+ entries） |
| 后端执行 job 集成测试 | `AttackChainNodesExecutionJobDynamicContractsTest.java` | mock service.computeDynamicContracts 返 N contracts → verify runtime 执行单元（t=0 平行 / 无依赖 / 默认 1 repeat / NodeExpectation 写正确）|
| 后端 PUT endpoint 测试 | `AttackChainApiDynamicFilterTest.java` | PUT 正常 / 空 filter / 非法 JSON / @RBAC 验证 |
| 前端 dynamicContractsAdapter 单测 | `runtime/__tests__/dynamicContractsAdapter.test.ts` | NodeContract → DynamicNodeViewModel 映射 / 位置布局 / 空数组 / 单 contract / 多 contracts |
| 现有 baseline | 全套 vitest + Spring boot test | 0 回归（main = 398 vitest tests + 后端 baseline）|

---

## 6. 风险点 & 缓解

| 风险 | 缓解 |
|---|---|
| 大量 NodeContract 时 dynamicFilter 派生性能 | 复用 NodeContractRepository 现有 Specification + Pageable 框架；JPA 已加 attack_pattern_id / kill_chain_phase_id 索引 |
| 动态节点 + 手动节点 ID 冲突 | runtime ID 用 `dynamic-${contract_id}` 前缀，确保与 UUID 节点不冲突 |
| chain run 中途 NodeContract 删除 | service.computeDynamicContracts 在 run start 时一次性 query；删除的 contract 在派生时已不在结果集；run 期间不刷新 |
| 前端 ReactFlow 节点位置布局 | 动态节点放在画布固定区域（如 y=固定）；不参与依赖图布局算法 |
| 现有 AttackChainRunComponent.tsx MitreMatrix 调用是否受影响 | 不受影响（V4 仅加列，default 空 filter，run 端点 wire format 兼容）|
| Flyway V4 与已合 V3 兼容 | 标准 ALTER TABLE 加列 + 默认值，无 backward incompatible |

---

## 7. Workstream

- 起 worktree `worktrees/phase-12c-Biii-dynamic-content`，分支 `feat/attack-chain-phase-12c-Biii-dynamic-content` base=main
- **2 PR 串成**：
  - **PR-A 后端**：V4 migration + AttackChain.dynamicFilter + service.computeDynamicContracts + AttackChainNodesExecutionJob 接动态 + REST endpoint + 后端测试
  - **PR-B 前端**：DynamicFilterDrawer + NodeWrapper 动态节点视觉 + ChainedTimeline 接 dynamicContracts + 编辑器/运行画布 host 接线 + i18n。Base PR-A merged main
- PR-A 独立可合并验证；PR-B 等 PR-A 合并后再起

提交风格：中文 `执行：...（Phase 12c-Biii Step N）` + `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`。

---

## 8. 范围 Boundary（YAGNI — 本 spec 不做）

| 项 | 原因 |
|---|---|
| ❌ 跨 chain 共享 saved query（独立 SavedQuery entity） | 方案 2 排除：YAGNI；OpenBAS 无先例 |
| ❌ "Convert dynamic to static" 操作（用户 fork 动态 contracts 为手动节点） | PRD 没要求；可未来加 |
| ❌ 动态节点的依赖关系编辑 | 设计决策：动态节点 t=0 平行无依赖 |
| ❌ 动态节点的重复执行 / 时延配置 | 同上 — 默认 1 repeat / t=0 |
| ❌ 多 dynamicFilter（一个 chain 多个 filter） | 设计决策：1 chain = 1 filter，与 AssetGroup 一致 |
| ❌ filter expression 编辑器全功能（复杂嵌套 / SQL-like） | 复用 OpenBAS 现有 FilterGroup 编辑器，不扩功能 |
| ❌ 动态节点 expectation 自定义 | 默认用 contract.injector_contract_default_expectations |
| ❌ 跨链路全局动态用例覆盖度 | 与 B-i ATT&CK matrix 范围一致，跨链路都没做 |

---

## 9. 完成标准

- ✅ PRD §2.3 第 4 行（编辑器内设 filter = 创建动态用例集）
- ✅ PRD §2.3 第 5 行（实时联动 — filter 匹配的新 contracts 自动加入）
- ✅ AttackChain 编辑器 + 运行画布两处都正确显示动态节点（dashed border + verdict）
- ✅ 现有 §2.4 编辑能力（手动节点精细编排）零受损
- ✅ origin/master 仍锁 `5d7e05da6`，PR base=main
- ✅ 测试全过 + 0 回归

---

## 10. 后续相关 sub-project

按 D 顺序（A→C→B）独立 spec：

- **B-ii**：3 个 inject 类型补全（web 攻击包 / pcap / 邮件）— 与 §2.1 流量回放引擎一起规划
- **§2.5**：沙箱 M2/M3 — 独立 sub-project
