# Implementation Plan: DynamicFilterDrawer FilterField 集成（B-iii follow-up）

> Spec: `docs/superpowers/specs/2026-05-11-veriguard-filter-field-integration-design.md`
> Scope: 2 files / 1 PR / ~半天
> Base: main `83d433451`

---

## 准备工作

worktree 已起：`worktrees/phase-12c-Biii-filter-editor`，branch `feat/attack-chain-phase-12c-Biii-filter-editor` base=origin/main.

`yarn install` 在 worktree 已完成（后台执行）。

---

## Task 1：actions.ts 类型对齐

**文件**：`veriguard-front/src/actions/attack_chains/attack_chain-actions.ts`

### Steps

- [ ] **Step 1.1：从 api-types 导入 FilterGroup**

如尚未导入，加：

```ts
import {
  type AttackChain,
  // ... existing imports
  type FilterGroup,
  // ... existing imports
} from '../../utils/api-types';
```

- [ ] **Step 1.2：FilterGroupWire / 等改为 api-types alias**

定位现有定义（约第 329-355 行）：

```ts
export type FilterOperatorWire = | 'eq' | 'not_eq' | ... ;
export type FilterModeWire = 'and' | 'or';
export interface FilterWire { ... }
export interface FilterGroupWire { ... }
export interface AttackChainDynamicFilterInputWire { dynamic_filter: FilterGroupWire }
```

改为：

```ts
// Phase 12c-Biii follow-up：原 FilterGroupWire / FilterWire 等是 B1 step 自造的
// 镜像类型；改用 api-types FilterGroup / Filter 后这些 alias 仅作 backward-compat
// export 保留，避免破坏潜在 import。

import { type Filter, type FilterGroup, type FilterOperator, type FilterMode } from '../../utils/api-types';

/** @deprecated 用 api-types FilterOperator. */
export type FilterOperatorWire = FilterOperator;

/** @deprecated 用 api-types FilterMode. */
export type FilterModeWire = FilterMode;

/** @deprecated 用 api-types Filter. */
export type FilterWire = Filter;

/** @deprecated 用 api-types FilterGroup. */
export type FilterGroupWire = FilterGroup;

export interface AttackChainDynamicFilterInputWire { dynamic_filter: FilterGroup }
```

注意：先 grep 确认 api-types 内 `FilterOperator` / `FilterMode` 类型是否存在；如果只有 `FilterGroup` / `Filter` 实体类型（无独立 enum），就保留 `FilterOperatorWire` / `FilterModeWire` 原 union literal 形态。

- [ ] **Step 1.3：AttackChainWithDynamic 字段类型对齐**

```ts
export interface AttackChainWithDynamic extends AttackChain {
  attack_chain_dynamic_filter?: FilterGroup;  // 从 FilterGroupWire 改
  attack_chain_dynamic_contracts?: NodeContract[];
}
```

- [ ] **Step 1.4：updateAttackChainDynamicFilter signature 类型对齐**

```ts
export const updateAttackChainDynamicFilter
  = (
    attackChainId: AttackChain['attack_chain_id'],
    input: AttackChainDynamicFilterInputWire,  // 仍是这个 interface，但内部 dynamic_filter 已是 FilterGroup
  ) =>
    (dispatch: Dispatch) => {
      const uri = `${SCENARIO_URI}/${attackChainId}/dynamic_filter`;
      return putReferential(attack_chain, uri, input)(dispatch);
    };
```

无需改 dispatch 逻辑，只是参数 input 内层类型变了。

- [ ] **Step 1.5：check-ts 验证**

```bash
cd veriguard-front && yarn check-ts 2>&1 | tail -3
```

Expected：0 errors。如果 DynamicFilterDrawer.tsx 此时还引用 `FilterGroupWire`，可能 alias 解析过去能编（FilterGroupWire = FilterGroup 同构）—— 但 Step 2 会主动改 drawer 用 FilterGroup。

---

## Task 2：DynamicFilterDrawer.tsx 集成 FilterField

**文件**：`veriguard-front/src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx`

### Steps

- [ ] **Step 2.1：先读 DynamicAssetField 作为模板**

```bash
cat veriguard-front/src/admin/components/assets/asset_groups/DynamicAssetField.tsx
```

理解 useFiltersState 用法 + FilterField props + `emptyFilterGroup` 来源。

- [ ] **Step 2.2：重写 drawer imports + 主体**

替换 drawer 内容核心：

```tsx
/* eslint-disable i18next/no-literal-string -- Phase 12c-Biii 二开 UI 硬编码中文，未来统一 i18n 清洗。 */
import { Box, Button, Drawer, Stack, Typography } from '@mui/material';
import { type FunctionComponent, useEffect } from 'react';

import {
  type AttackChainDynamicFilterInputWire,
  type AttackChainWithDynamic,
  updateAttackChainDynamicFilter,
} from '../../../../actions/attack_chains/attack_chain-actions';
import FilterField from '../../../../components/common/queryable/filter/FilterField';
import { emptyFilterGroup } from '../../../../components/common/queryable/filter/FilterUtils';
import useFiltersState from '../../../../components/common/queryable/filter/useFiltersState';
import { useFormatter } from '../../../../components/i18n';
import { type AttackChain, type FilterGroup, type NodeContract } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

const DRAWER_WIDTH = 440;

/** NodeContract 维度筛选 key（与 useRetrieveOptions/useSearchOptions 已支持的 case 对齐）. */
const NODE_CONTRACT_FILTER_KEYS = [
  'injector_contract_attack_patterns',
  'injector_contract_kill_chain_phases',
  'injector_contract_domains',
  'injector_contract_injector',
];

interface Props {
  attackChain: AttackChain;
  open: boolean;
  onClose: () => void;
  /** 当前命中的动态用例列表（来自 attack_chain_dynamic_contracts；用于预览计数）. */
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

  // useFiltersState 用 chain 当前 filter 初始化；onChange 不需要回调（直接在 Save 时读 hook 返回值）
  const initial = (attackChain as AttackChainWithDynamic).attack_chain_dynamic_filter ?? emptyFilterGroup;
  const [filterGroup, helpers] = useFiltersState(initial, emptyFilterGroup);

  // Drawer 重开时重置 filter state（attackChain 引用变化触发）
  useEffect(() => {
    if (open) {
      const fresh = (attackChain as AttackChainWithDynamic).attack_chain_dynamic_filter ?? emptyFilterGroup;
      helpers.handleClearAllFilters?.();
      // 若 useFiltersState 不暴露 setter，依靠 key prop 重渲染 — 见下方 <Drawer> 用 key={`drawer-${attackChain.attack_chain_id}-${open}`}
    }
  }, [open, attackChain, helpers]);

  const handleSave = async () => {
    const input: AttackChainDynamicFilterInputWire = { dynamic_filter: filterGroup };
    await dispatch(updateAttackChainDynamicFilter(attackChain.attack_chain_id, input));
    onClose();
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ style: { width: DRAWER_WIDTH } }}
    >
      <Box sx={{ padding: 3 }}>
        <Typography variant="h5" sx={{ marginBottom: 2 }}>
          {t('Edit dynamic filter')}
        </Typography>
        <FilterField
          entityPrefix="injector_contract"
          availableFilterNames={NODE_CONTRACT_FILTER_KEYS}
          filterGroup={filterGroup}
          helpers={helpers}
          style={{ marginTop: 12 }}
        />
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', marginTop: 2 }}>
          {t('{n} contracts match', { n: previewContracts.length })}
        </Typography>
        <Stack direction="row" spacing={1} justifyContent="flex-end" sx={{ marginTop: 3 }}>
          <Button onClick={onClose}>{t('Cancel')}</Button>
          <Button variant="contained" onClick={handleSave}>{t('Save')}</Button>
        </Stack>
      </Box>
    </Drawer>
  );
};

export default AttackChainDynamicFilterDrawer;
```

注意：

1. **useEffect 重置策略**：useFiltersState 的 `helpers.handleClearAllFilters` 应该把 filter 还原到 defaultFilters。如果想用 chain 当前 filter 重置（不是 default），策略 B 是：在 `<Drawer>` 上加 `key={`${attackChain.attack_chain_id}-${open}`}`，drawer 每次开都强制重 mount → useFiltersState 用新 initial。**这个方案更直接**，避免依赖 helpers 的具体 reset 行为。

实施时优先用 key prop 强制 remount 方案；useEffect 那段如果用不到就删掉。

2. **空 helpers.handleClearAllFilters 防御**：上面的 `?.()` 是为兼容 helpers 类型万一没暴露该方法。读 useFiltersState 源后按实际清理。

- [ ] **Step 2.3：check-ts + test + lint**

```bash
cd veriguard-front
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | grep -E 'Test Files|Tests' | tail -2
npx eslint src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx src/actions/attack_chains/attack_chain-actions.ts --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}" 2>&1 | tail -3
```

Expected：check-ts 0 / 405 测试全过（0 回归）/ eslint 0 errors。

- [ ] **Step 2.4：Commit**

```bash
git add veriguard-front/src/admin/components/attack_chains/attack_chain/AttackChainDynamicFilterDrawer.tsx \
        veriguard-front/src/actions/attack_chains/attack_chain-actions.ts
git commit -m "$(cat <<'EOF'
执行：DynamicFilterDrawer 集成 OpenBAS FilterField（Phase 12c-Biii follow-up）

替换 PR #31 留下的 JSON 调试视图占位为真实 chip 编辑器：
- DynamicFilterDrawer 用 useFiltersState + FilterField (entityPrefix=injector_contract,
  availableFilterNames=[attack_patterns/kill_chain_phases/domains/injector])
- key prop 在 drawer remount 时强制用 chain 当前 filter 重新初始化 state
- Save 直接 dispatch hook 返回的 FilterGroup（无 converter）

actions.ts 类型对齐：
- AttackChainDynamicFilterInputWire.dynamic_filter / AttackChainWithDynamic.attack_chain_dynamic_filter
  从 FilterGroupWire 改为 api-types FilterGroup（useFiltersState 直接产 api-types
  类型，零 converter 维护成本）
- FilterGroupWire / FilterWire / FilterOperatorWire / FilterModeWire 退化为
  @deprecated alias 保 export，避免破坏潜在 import

完成 PRD §2.3 第 4 行 UI 100% 落地（编辑动态用例集 filter）.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：PR-final 验证 + push + create PR

### Steps

- [ ] **Step 3.1：最终验证**

```bash
cd veriguard-front
yarn check-ts 2>&1 | tail -3
yarn test 2>&1 | grep -E 'Test Files|Tests' | tail -2
```

- [ ] **Step 3.2：master 锁验证**

```bash
git -C /Users/lamba/github/Veriguard ls-remote origin master | head -1
```

Expected: `5d7e05da6...`

- [ ] **Step 3.3：push**

```bash
git push -u origin feat/attack-chain-phase-12c-Biii-filter-editor 2>&1 | tail -3
```

- [ ] **Step 3.4：gh pr create**

```bash
gh pr create --base main --title "二开 Phase 12c-Biii follow-up: DynamicFilterDrawer 集成 OpenBAS FilterField" --body "..."
```

PR body 见下文模板。

---

## 自审 checklist

- [ ] DynamicFilterDrawer 显示 FilterField chip UI
- [ ] Save → PUT dispatch → onClose 链路完整
- [ ] Drawer remount 用 chain 当前 filter 重新初始化
- [ ] 405 baseline test 0 回归 / check-ts 0 / lint clean
- [ ] master 锁定不变
- [ ] PR base=main，标题含 "follow-up"

---

## 范围 boundary 提醒（spec §6）

- ❌ drawer 内实时预览匹配 contracts 数量
- ❌ 加更多 filter key（仅 4 个）
- ❌ 后端 @Queryable 标记审计 / 补齐
