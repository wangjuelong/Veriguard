# Spec: 攻击链路 ATT&CK 矩阵视图（B-i / Phase 12c-Bi）

> 设计日期：2026-05-10
> PRD 来源：`docs/prd/产品要求.md` §2.3 第 1 行（前半 ATT&CK 自动划分部分）
> Workstream 拆解：B（§2.3 自定义验证）→ B-i（ATT&CK 自动划分视图）
> 状态：design approved，待 writing-plans 进入实施
> 相关已合 PR：Phase 0-12 + 12b-A/B/C/D（main = `a647e5be4`）

---

## 1. 问题陈述

PRD §2.3 第 1 行要求："基于 ATT&CK 和**纵深防御**维度对场景内用例进行自动划分"。

**本 Spec 范围**：仅 ATT&CK 维度。"纵深防御维度"在用户讨论中明确跳过（"只是一个概念，不属于具体需求点"）。

**当前缺口**：

- §2.4 已完成的攻击链路画布（ChainedTimeline + ReactFlow）按"依赖关系拓扑 + 时间偏移"布局，**不按 ATT&CK 阶段分类**
- 节点的 attack_patterns 数据已挂在 `node_injector_contract.injector_contract_attack_patterns`，但**前端无以 ATT&CK 维度展示链路覆盖度的视图**
- 仓库已有 `MitreMatrix.tsx` / `KillChainPhaseColumn.tsx` / `AttackPatternBox.tsx`（settings 页用于 AttackPattern CRUD），但**未挂到攻击链路场景**

---

## 2. 设计决策（已与用户协商）

| 决策点 | 选择 | 理由 |
|---|---|---|
| 展现形态 | **B：独立 ATT&CK Matrix Tab**（行业 MITRE Navigator 风格）| PRD"自动划分"语义契合度最高；`MitreMatrix` 组件已存在可复用；与 §2.4 已完成画布解耦无冲突 |
| 入口位置 | **3：编辑器 + 运行画布两处**（共享同一组件）| 编排时看覆盖度 + 运行时看防守效果，PRD §2.3 第 1 行 + §2.4 第 8 行双重满足 |
| 数据范围 | **Z：Hybrid（默认紧凑 + toggle 切完整 ATT&CK 标准矩阵）** | 紧凑视图视觉聚焦，完整矩阵能看"未覆盖盲区"，由用户决定；与现有 `MitreMatrix` 紧凑模式向后兼容 |
| 状态着色 | **2：编辑器仅覆盖度 + 运行画布带 verdict（PREVENTION/DETECTION 通过 toggle 切换）** | 复用 §2.4 verdict 颜色语义；运行画布与编辑器有差异化价值 |
| 实现方案 | **方案 1：扩展现有 MitreMatrix（不重写）** | DRY；与已落地的"复用 + context-injection"模式一致；后端 0 改动 |

---

## 3. 架构

### 3.1 文件清单

| 文件 | 类型 | 改动 |
|---|---|---|
| `veriguard-front/src/admin/components/common/matrix/MitreMatrix.tsx` | 共享组件改 | 加 props：`mode: 'compact' \| 'full'`、`coloringScheme: 'coverage' \| 'verdict'`、`verdictDimension: 'prevention' \| 'detection'`，全部带 default |
| `veriguard-front/src/admin/components/common/matrix/KillChainPhaseColumn.tsx` | 共享组件改 | 透传新 props 给 `AttackPatternBox` |
| `veriguard-front/src/admin/components/common/matrix/AttackPatternBox.tsx` | 共享组件改 | 着色逻辑切换：`coverage` → 绿/灰；`verdict` → 复用 `runtime/attackChainRuntimeTypes.ts` 的 `STATUS_STYLE` 4 色 |
| `veriguard-front/src/admin/components/common/matrix/attackPatternMatrixAdapter.ts` | 新文件（纯函数 adapter） | `(nodes, expectations?, dimension?) => NodeExpectationResultsByAttackPattern[]` |
| `veriguard-front/src/admin/components/common/matrix/__tests__/attackPatternMatrixAdapter.test.ts` | 新测试 | ≥10 场景 |
| `veriguard-front/src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx` | 编辑器 host 改 | viewMode 加 `'matrix'`；matrix view 渲染 adapter + `<MitreMatrix>` |
| `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx` | 运行画布 host 改 | 同上 + verdict dimension toggle |
| `veriguard-front/src/utils/lang/zh.json` + `en.json` | i18n | 新增 4-6 个 key（`Attack pattern matrix` / `Compact view` / `Full ATT&CK matrix` / `Coverage` / `By prevention` / `By detection`） |

**后端：0 改动**。所有数据来自现有 helper / endpoints。

### 3.2 数据流

**编辑器场景**：

```
helper.getAttackChainAttackChainNodes(scenarioId)             [已有]
    → adapter (nodes only, no expectations)
    → derived NodeExpectationResultsByAttackPattern[]（status='COVERED' 占位）
    → <MitreMatrix mode={...} coloringScheme="coverage" />
```

**运行画布场景**：

```
helper.getAttackChainRunAttackChainNodes(exerciseId)          [B4 已用]
+ helper.getAttackChainRunAttackChainNodeExpectations(...)    [B4 已用]
    → adapter (nodes + expectations, dimension)
    → derived NodeExpectationResultsByAttackPattern[]（聚合 verdict）
    → <MitreMatrix mode={...} coloringScheme="verdict" verdictDimension={...} />
```

**Hybrid mode toggle** 与 **verdict dimension toggle**：state 在 host 维护，传给 `MitreMatrix`。Full mode 时由 MitreMatrix 内部用现有 `getKillChainPhasesMap()` / `getAttackPatternsMap()` helper 拿全量。

---

## 4. 着色方案

### 4.1 `coloringScheme = 'coverage'`（编辑器）

| 状态 | 颜色 |
|---|---|
| 覆盖（≥1 节点引用该 attack_pattern） | 绿（沿用现有 success 色）|
| 未覆盖（仅 full mode 显示） | 灰 |

### 4.2 `coloringScheme = 'verdict'`（运行画布）

聚合规则与 §2.4 `runtimeNodeAdapter.aggregate` 一致（多节点同 pattern 同维度 expectation 聚合）：

| 该 pattern 节点 expectations 状态分布 | 颜色 | 来源（B4 已建）|
|---|---|---|
| 全 SUCCESS | 绿 | `STATUS_STYLE.SUCCESS` |
| 全 FAILED | 红 | `STATUS_STYLE.FAILED` |
| 全 PENDING / UNKNOWN | 灰 | `STATUS_STYLE.PENDING` |
| 混合（含 SUCCESS+FAILED）| 橙 | `STATUS_STYLE.PARTIAL` |
| 节点该维度无 expectation | 浅灰（dashed border）| `STATUS_STYLE.N_A` |

颜色直接 `import` from `runtime/attackChainRuntimeTypes.ts`（B4 已建立的 STATUS_STYLE），单一来源。

---

## 5. Backward Compat（关键）

`MitreMatrix` 现有调用方需 grep 找全（如 `AttackChainRunDistribution.tsx` 等），新 props 全有 default：

- `mode = 'compact'`（保现状仅显示有结果的 phases）
- `coloringScheme = 'verdict'`
- `verdictDimension = 'prevention'`

→ 现有调用方不传 props 时行为完全不变（这正是当前唯一行为）。

**验证策略**：现有调用方现有测试**不修改即过**。`yarn test` baseline 376 passed 必须保持。

---

## 6. 入口（viewMode toggle）

- 现有 `viewMode: 'chain' | 'list' | 'distribution'` → 加 `'matrix'`
- 编辑器 + 运行画布两 host 各加 4 个 toggle button（list / chain / distribution / matrix）
- LocalStorage key 复用现有 `attack_chain_or_attack_chain_run_view_mode`
- Toggle button icon：mdi-material-ui `GridOn` 或 `ViewModule`

LeftBar 不改（Phase 12b-C 已稳定）；Matrix 是 detail 页内部的 view mode 切换，不是顶层路由。

---

## 7. 测试策略

| 测试类型 | 文件 | 核心场景 |
|---|---|---|
| Adapter 单测（重点）| `attackPatternMatrixAdapter.test.ts` | 编辑器 covered / 运行 全 SUCCESS / 全 FAILED / PARTIAL / PENDING 聚合 / PREVENTION vs DETECTION 维度切换 / 节点无 pattern / 多节点同 pattern 聚合 / 空输入 / N_A 状态。≥10 场景 |
| Backward compat smoke | 现有 `MitreMatrix` 调用方相关测试 | grep 全部调用方；新 props default 不破坏既有 376 测试 |
| Host wiring | 编辑器/运行画布 host 集成测试 | viewMode='matrix' 切换 + LocalStorage 持久化 + mode/dimension toggle state 不丢 |

---

## 8. 风险点 & 缓解

| 风险 | 缓解 |
|---|---|
| Full mode 数据量大（~14 战术 × ~200 主 techniques）| MitreMatrix 已支持 horizontal scroll；full mode 默认 off；不渲染 sub-techniques |
| ATT&CK seed 数据可能不完整 | 开发期 verify `attack_patterns` 表有 enterprise full seed；缺漏由后续 PR 单独补 seed，不阻塞本 PR |
| `node_injector_contract.injector_contract_attack_patterns` wire format 字段挂载 | 现有 `AttackChainRunDistributionByNodeContract` 已用此字段，wire format OK；如缺需补后端 DTO（不在本 PR 范围）|
| 多节点聚合到同 pattern 格 | 聚合规则与 §2.4 `runtimeNodeAdapter.aggregate` 同款，单测覆盖 |
| 共享组件改动影响 settings 页 | Backward compat 通过 props default 保证；现有测试 0 修改即过 |

---

## 9. Workstream

- 起 worktree `worktrees/phase-12c-Bi-attack-pattern-matrix`，分支 `feat/attack-chain-phase-12c-Bi-attack-pattern-matrix` base=main
- 1 PR / 4 commit 串成单 PR，PR base=main：
  1. **Adapter + 单测**（纯函数，先验证逻辑）
  2. **Shared component 扩展**（`MitreMatrix` / `KillChainPhaseColumn` / `AttackPatternBox` 加 props，default 保 backward compat）+ 现有调用方 smoke
  3. **Host wiring**（编辑器 + 运行画布加 viewMode='matrix' tab）+ i18n
  4. **验证 + PR**：`yarn check-ts` / `yarn lint` / `yarn test` 全过 + master 锁 `5d7e05da6` 验证 + 起 PR `--base main`

提交风格：中文 `执行：...（Phase 12c-Bi ATT&CK matrix Step N）` + `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`。

---

## 10. 范围 Boundary（YAGNI — 本 spec 不做）

| 项 | 原因 |
|---|---|
| 跨链路全局 ATT&CK 覆盖度（顶层独立页 `/admin/attack_chain_matrix`）| Scope decomposition 时排除：YAGNI，PRD §2.3 未明确要求项目级跨链路视图 |
| 纵深防御维度（任何形式）| 用户明确跳过 |
| 点格子展开节点详情 popover | PRD 没明确要求 click 行为；本 PR 仅展示；后续 PR 可加 |
| ATT&CK seed 数据补全 | 如缺 enterprise full set，单独 PR 处理，不阻塞 |
| ATT&CK sub-techniques 支持 | 与 MitreMatrix 现状一致，仅渲染 main techniques |
| ATT&CK Navigator JSON 导出兼容 | 行业 nice-to-have，PRD 没要求 |
| 列表 view 加 ATT&CK 列分组 | C 选项（scope decomposition 排除） |
| 后端新 endpoint 聚合 view-model | 方案 3，YAGNI 排除 |

---

## 11. 完成标准

- ✅ PRD §2.3 第 1 行 ATT&CK 自动划分维度满足
- ✅ 编辑器 / 运行画布两处都有 matrix tab，支持 Hybrid 紧凑/完整 + verdict 维度切换
- ✅ Backward compat：现有 376 测试 0 回归
- ✅ Adapter 单测 ≥10 场景
- ✅ origin/master 仍锁 `5d7e05da6`，PR base=main

---

## 12. 后续相关 sub-project

按 D 顺序（A→C→B）独立 spec：

- **B-iii（C 选项）**：动态场景 saved-query 机制（PRD §2.3 第 4+5 行）—— 下一个 brainstorming
- **B-ii（B 选项）**：3 个 inject 类型补全（web 攻击包 / pcap / 邮件，样本→沙箱执行类型留给 §2.5）—— 最后做
