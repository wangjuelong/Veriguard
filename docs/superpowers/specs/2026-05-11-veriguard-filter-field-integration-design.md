# Spec: DynamicFilterDrawer 集成 OpenBAS FilterField（B-iii follow-up）

> 设计日期：2026-05-11
> 上游 sub-project：Phase 12c-Biii 动态用例集（PR #30 后端 + PR #31 前端 已合并到 main `83d433451`）
> 类型：closing-the-loop polish（移除 PR #31 留下的 JSON 占位 TODO）
> 状态：design approved，进入 writing-plans / 实施

---

## 1. 问题陈述

PR #31 落地的 `AttackChainDynamicFilterDrawer.tsx` 内 FilterGroup 编辑器目前是 **JSON 调试视图**（`<pre>{JSON.stringify(draftFilter, null, 2)}</pre>`），inline TODO 标明集成点：

```
后续集成 OpenBAS FilterField / DynamicAssetField 时，替换 TODO 区块即可：
  import FilterField from '../../../../components/common/queryable/filter/FilterField';
  import useFiltersState from '../../../../components/common/queryable/filter/useFiltersState';
  ...
```

PRD §2.3 第 4 行（"filter 编辑 = 创建动态用例集"）UI 完成度尚未 100%：用户实际编辑动态 filter 还得手写 JSON 或调 PUT 端点。本 spec 收尾。

---

## 2. 设计决策

| 点 | 选择 | 理由 |
|---|---|---|
| FilterField `entityPrefix` | **`'injector_contract'`** | 既有 `useRetrieveOptions` / `useSearchOptions` switch case 都用此前缀；property schema 数据库也用它（OpenBAS 内部命名未跟 12b-A `Inject*` → `Node*` rename 同步，但 NodeContract 实体的 @Queryable 注解保持 `injector_contract_*`）|
| `availableFilterNames` | **`['injector_contract_attack_patterns', 'injector_contract_kill_chain_phases', 'injector_contract_domains', 'injector_contract_injector']`** | 既有 `useRetrieveOptions` 已支持的 4 key；覆盖 PRD §2.3 第 3 行 6 类用例的主维度（ATT&CK / kill chain / domain / 执行器类型）|
| 类型对齐 | **方案 B：丢 `FilterGroupWire`，drawer 直接用 api-types `FilterGroup`** | useFiltersState 返 api-types FilterGroup；保留 FilterGroupWire 要写双向 converter（values 可选 vs 必填，每个 filter 都要补 `?? []`）；丢掉 → 零 converter 零兼容代码；后端 Jackson 序列化形态完全一致（`{mode, filters: [{key, mode, values, operator}]}`），无需后端改动 |
| backward 兼容 | `FilterGroupWire` / `FilterWire` / `FilterOperatorWire` / `FilterModeWire` 退化为 api-types alias，保留 export 不破潜在 import | 类型私有（只 drawer 用），但 export 仍在 — 安全保留 alias |
| `previewContracts` 数量预览 | 保持现状（drawer 内 "{n} contracts match"）。**不**在 drawer 内实时调 NodeContract search 端点 | YAGNI。Drawer 关闭后 helper 重拉 chain → `attack_chain_dynamic_contracts` 派生字段刷新，进 timeline 视觉验证。实时预览是另一个 sub-feature |

---

## 3. 架构

### 3.1 文件清单

| 文件 | 类型 | 改动 |
|---|---|---|
| `veriguard-front/src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx` | 改（主体重写）| 删 JSON 视图 + 自定义 EMPTY_FILTER_GROUP 常量；改用 `useFiltersState(initial)` + `<FilterField entityPrefix="injector_contract" availableFilterNames={NODE_CONTRACT_FILTER_KEYS} filterGroup={...} helpers={...} />`；state 类型从 `FilterGroupWire` 改为 api-types `FilterGroup`；Save 直接传 hook 内部 filterGroup 给 dispatch |
| `veriguard-front/src/actions/attack_chains/attack_chain-actions.ts` | 改 | `AttackChainDynamicFilterInputWire.dynamic_filter`：`FilterGroupWire` → `FilterGroup`；`AttackChainWithDynamic.attack_chain_dynamic_filter?`：`FilterGroupWire` → `FilterGroup`；`FilterGroupWire` / `FilterWire` / `FilterOperatorWire` / `FilterModeWire` 退化为 api-types alias（`export type FilterGroupWire = FilterGroup` 等）|

### 3.2 数据流（编辑动态 filter）

```
用户点 toolbar "Dynamic content (N)" → drawer 打开
  → useEffect 用链路当前 attack_chain_dynamic_filter 初始化 useFiltersState
  → FilterField 渲染 chip 风格筛选器，availableFilterNames 限定为 NodeContract 4 keys
  → 用户加/改/删 filter chip → useFiltersState 内部 state 更新
  → 用户点 Save
    → dispatch(updateAttackChainDynamicFilter(id, { dynamic_filter: hookFilterGroup }))
    → 后端 PUT 端点写 attack_chains.dynamic_filter（与 PR #30 实现一致）
    → onClose()
  → 编辑器 host useMemo([attack_chain]) refresh dynamicContracts → ChainedTimeline 重渲染动态节点
```

### 3.3 后端兼容

零改动。FilterField 生成的 FilterGroup JSON 序列化形态与 PR #30 后端 `Filters.FilterGroup` Jackson 反序列化完全一致（同样字段：mode/filters[].key/mode/operator/values）。

---

## 4. 测试

- 不加新单测（FilterField 是已落地组件，自身已有覆盖；本 PR 主要是 wiring）
- 验证 baseline 0 回归：405 baseline test 全过 / yarn check-ts 0 / lint clean

---

## 5. 风险点 & 缓解

| 风险 | 缓解 |
|---|---|
| 后端 `FilterUtilsJpa.computeFilterGroupJpa(NodeContract)` 用 JPA Criteria 解析 `injector_contract_*` filter key 时，依赖 NodeContract 实体的 @Queryable 注解 | 如解析失败：实施时跑一个端到端 PUT + 触发 chain run 看是否能拉到 contracts。失败则退到 follow-up issue：加 @Queryable filterable=true 到 NodeContract 实体相关字段。本 PR 仍合并 — 因为 backend 端点 / DB 列 / UI 都已就位，缺的只是 @Queryable 标记 |
| useFiltersState onChange 触发时机 | 不依赖 onChange 回调；直接读 hook 返回的 filterGroup state 在 Save 时 dispatch |
| Drawer 重开 state 不刷新 | 沿用 PR #31 现有 useEffect([open, attackChain]) 模式，drawer 关闭再开时根据 prop attackChain 重新初始化 hook（key prop trick 或显式 reset helper）|

---

## 6. 范围 Boundary（YAGNI）

| 项 | 原因 |
|---|---|
| ❌ 实时预览匹配 contracts 数量（drawer 内调 NodeContract search）| Save 后 helper 重拉派生字段刷新；实时预览要额外建端点 + 节流 / debounce，独立 sub-feature |
| ❌ 加更多 filter key（如 `injector_contract_payload_type`）| 当前 4 key 覆盖 PRD 主维度；后续按需扩展 |
| ❌ Filter key 多语言显示 | useFilterableProperties 已通过 property schema 处理 |
| ❌ 后端 @Queryable 标记审计 / 补齐 | 风险点列出，但范围超出 UI follow-up |

---

## 7. 完成标准

- ✅ DynamicFilterDrawer 显示 FilterField chip UI（编辑动态 filter）
- ✅ Save 调用 `updateAttackChainDynamicFilter`，PUT 端点接受 FilterGroup
- ✅ Drawer 重开时恢复链路当前 filter
- ✅ 405 baseline test 0 回归 / check-ts 0 / lint clean
- ✅ origin/master 仍锁 `5d7e05da6`，PR base=main
