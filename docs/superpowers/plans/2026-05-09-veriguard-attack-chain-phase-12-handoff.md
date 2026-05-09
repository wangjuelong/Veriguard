# Veriguard 二开攻击编排 — Phase 12 handoff

## 摘要

PRD §2.4 攻击编排实施 Phase 0-11 全部 merged 到 `main`。Phase 12 在本 PR 落地 i18n key 词条预设（en/zh 各 86 条新键），其余整合工作（前端 Scenario→AttackChain rename / nav 接线 / api-types 重生成 / 完整 i18n 替换 / 部署） 收口为本文档说明的 4 条独立 workstream，留待后续推进。

## 状态

| Phase | 内容 | 状态 |
|---|---|---|
| 0 | Scenario → AttackChain 后端全栈改名 | ✅ merged (PR #1) |
| 1 | V3 Flyway schema + 7 模型 | ✅ merged (PR #2) |
| 2 | ValidationParameterSet 后端 CRUD | ✅ merged (PR #3) |
| 3 | NodeStateMachine + EdgeConditionEvaluator | ✅ merged (PR #4) |
| 3.5 | StopOnBlockHandler | ✅ merged (PR #5) |
| 4 | NodeRepeatPlanner | ✅ merged (PR #7) |
| 4.5 | NodeRepeatService 接调度器 | ✅ merged (PR #9) |
| 5 | LinkVerdictCalculator 计数法 | ✅ merged (PR #10) |
| 6 | SocAlertConnector SPI + ElasticSocConnector | ✅ merged (PR #11) |
| 7 | AttackChainLinkExpectation + 链路级 SOC 集成 | ✅ merged (PR #12) |
| 8 | 编辑器 ConditionEdgePopover + ⚙ 角标 + AttackChainSettingsDrawer | ✅ merged (PR #13) |
| 9 | 运行画布 DoubleLayerNode + VerdictBanner + LinkExpectationPanel | ✅ merged (PR #14) |
| 10 | ParameterSet UI | ✅ merged (PR #15) |
| 11 | SOC Connector 状态页 + RulePicker | ✅ merged (PR #16) |
| 12 | i18n key 词条预设 + handoff 文档（本 PR） | 🚧 |
| 12b | Scenario→AttackChain 全栈 rename / nav 接线 / api-types 重生成 / 部署 | ⏳ 待推 |
| 12-cleanup | 清掉 cleanup 一/二期之后的 ad-hoc 测试漂移 | （滚动）|

`origin/master` 锁定在 `5d7e05da6` baseline 不动；所有二开 PR 一律 base=main。

## Phase 12 已交付

### 1. i18n key 预设（86 条 / 语言）

`veriguard-front/src/utils/lang/en.json` + `zh.json` 追加 86 条键，覆盖 Phase 8/9/10/11 组件硬编码中文文案的英文 source key。后续 Phase 12b 把组件里的中文换成 `t('English Source Key')` 调用即可，文案中文已落库。

涉及范围：
- ExecutionMode（截停 / 继续）+ hint
- LinkVerdict 5 状态
- VerdictBanner 副文案模板（{settled}/{total} 等占位符）
- DoubleLayerNode 6 状态文案
- NodeBadge 4 状态
- ParameterSet 模板锁 / 复制并修改
- SOC Connector 4 状态 + RulePicker 搜索 / 失败提示
- LinkExpectationPanel 5 状态
- 维度 / 状态条件下拉

## Phase 12b 待推工作（4 个独立 workstream）

每条都可以独立 PR，互不阻塞。

### Workstream A — 前端 rename Scenario → AttackChain（最大）

**范围**：`veriguard-front/src` 内 274+ 处 `Scenario` / `scenario_*` / `Simulation` / `simulation_*` / `Exercise` / `exercise_*` 文本与符号。

**步骤**：
1. 启动后端：`cd veriguard-dev && docker compose up -d` + `mvn -pl veriguard-api spring-boot:run`
2. 重生成 API 类型：`cd veriguard-front && yarn generate-types-from-api` —— 会拉到 V3 字段（execution_mode / repeat_count / verdict_* / soc_correlation_rules 等）
3. 全局替换：
   - 路径：`scenarios/` → `attack_chains/`、`simulations/` → `attack_chain_runs/`
   - 类型：`Scenario` → `AttackChain`、`Simulation`/`Exercise` → `AttackChainRun`、`Inject` → `AttackChainNode`
   - 字段：`scenario_*` → `attack_chain_*`、`exercise_*` → `attack_chain_run_*`、`inject_*` → `node_*`
4. 删除老 i18n 键：`scenario.*` / `simulation.*` / `lesson.*` / `email.*`（spec §6.4 列表）
5. 跑 `yarn check-ts && yarn lint && yarn test:e2e` 确认无回归

**风险**：rename 触及 274 文件，必须分组 commit + 滚动验证；建议每 50 文件一个 commit 在同一个 PR 内推。

### Workstream B — 整合 Phase 8/9/10/11 独立组件

Phase 8/9/10/11 交付的组件是"独立可挂载"形态，未真正接入页面。整合点：

1. **AttackChainSettingsDrawer**：在 `AttackChainCanvas`（rename 后）右上角加按钮触发；存挂到 `useAttackChainStore`，提交时 PUT `/api/attack_chains/{id}`
2. **ConditionEdgePopover**：在 ReactFlow edge `onClick` 触发；提交时 PUT `/api/attack_chain_edges/{id}` 写 `condition` JSON
3. **NodeBadge + NodeEditDrawer**：NodeBadge 渲到 `EditorNode` 右上角；点击打开 NodeEditDrawer，提交 PUT `/api/attack_chain_nodes/{id}`
4. **DoubleLayerNode**：替换 `AttackChainRunCanvas` 的节点渲染器；状态从 `node.status.name` + 节点级 expectations 推导
5. **VerdictBanner**：在 `AttackChainRunOutput` 顶部挂载；数据从 `run.verdict_prevention` / `run.verdict_detection` 取
6. **LinkExpectationPanel**：作为 VerdictBanner 的 onClickDetection 弹出侧边面板；数据从新 endpoint `/api/attack_chain_runs/{id}/link_expectations`（Phase 7 已有 entity，需补 REST controller）
7. **ParameterSetEditDialog + ParameterSetCard**：独立路由 `/admin/validation_parameter_sets`；新 store + REST 调 `/api/validation_parameter_sets/*`（PR #3 已实现 backend CRUD）
8. **SocConnectorStatusList + SocConnectorRulePicker**：独立路由 `/admin/integrations/soc_connectors`；前者数据从 `/api/soc_connectors/health`，后者作为复用 dialog 在 AttackChainSettingsDrawer / ParameterSetEditDialog 选规则时弹出（替换当前的纯文本输入）

**前置依赖**：Workstream A（rename）。

### Workstream C — nav 路由 + 二开 console 接入

spec §6.1 顶层导航（`src/admin/Index.tsx`）新增 "攻击编排" 入口及子路由：

```
- 攻击编排                           /admin/attack_chains
   ├─ 链路 (chains)                  /admin/attack_chains
   ├─ 运行 (runs)                    /admin/attack_chain_runs
   ├─ 参数集 (parameter sets)        /admin/validation_parameter_sets
   └─ SOC 连接器 (status)             /admin/integrations/soc_connectors
```

并删除旧 "Scenarios" / "Simulations" 顶层入口（rename 后这些路由不复存在）。

**前置依赖**：Workstream A + B。

### Workstream D — 部署 / docker-compose 校验

PRD §2.4 强调"运行依赖必须由仓库内的开发环境承担"，需补：

1. `veriguard-dev/docker-compose.yml` 加 `veriguard.soc.elastic.*` 默认环境变量（默认 `enabled=false`，避免 dev 启动连不存在的 Elastic）
2. `veriguard-dev/.env.example` 提供凭证占位（`ELASTIC_API_KEY=...`）
3. `docs/参考资料/Veriguard二开落地说明.md` 补一节 "启用 SOC connector" 说明
4. 重跑后端 + 前端集成 e2e（`yarn test:e2e`）确认 V3 schema + 攻击编排端到端通

## 不在 Phase 12 处理的事项

- **后端 baseline 10 个 pre-existing 失败测试**：Phase 0 之前已存在的 4 GarbageCollector 品牌字符串漂移 / 3 V1_DataImporter 缺 TOCLASSIFY seed / AttackPattern + Smtp surefire 污染 / AttackChainNodesExecutionJobTest$HandlePending 断言漂移。属上游 baseline，不在攻击编排范围；后续单独 cleanup 三期处理。
- **api-types.d.ts 当前缺 V3 字段**：Phase 12b Workstream A 步骤 2 重生成。本 PR 不动它。
- **Scenario→AttackChain 全栈 rename 274 处**：Phase 12b Workstream A。

## 验证

- 本 PR 不改任何后端代码；后端 baseline 维持 10 failing（pre-existing）
- 前端 i18n 文件键数：`en.json` 2061 → 2147，`zh.json` 2061 → 2147（各 +86）
- `yarn check-ts` / `yarn lint` 无回归（本 PR 仅 JSON 改动）
