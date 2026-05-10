# 攻击链路 ATT&CK 矩阵视图 Implementation Plan（Phase 12c-Bi）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PRD §2.3 第 1 行 ATT&CK 自动划分维度落地：编辑器 + 运行画布两处加 ATT&CK 矩阵 tab，复用现有 `MitreMatrix` 组件并扩展支持紧凑/完整 + 覆盖度/verdict 着色 + PREVENTION/DETECTION 维度切换。

**Architecture:** 共享组件（MitreMatrix/KillChainPhaseColumn/AttackPatternBox）加 backward-compat props；编辑器用纯函数 adapter 把 nodes → `NodeExpectationResultsByAttackPattern[]`（COVERED 占位）；运行画布直接 fetch 现有 `/api/attack_chain_runs/{id}/nodes/results-by-attack-patterns` 端点；颜色复用 §2.4 B4 已建 `STATUS_STYLE`（先小重构提为 `attackChainRuntimeTypes.ts` 导出常量，单一来源）；后端 0 改动。

**Tech Stack:** React 19 / TypeScript / Vite / vitest / yarn 4 / MUI / `@xyflow/react`（不动）；Spec 文档 `docs/superpowers/specs/2026-05-10-veriguard-attack-pattern-matrix-design.md`

---

## 准备工作

**Worktree（执行前必做）：**

```bash
cd /Users/lamba/github/Veriguard
git fetch origin main
git worktree add worktrees/phase-12c-Bi-attack-pattern-matrix -b feat/attack-chain-phase-12c-Bi-attack-pattern-matrix origin/main
cd worktrees/phase-12c-Bi-attack-pattern-matrix/veriguard-front
yarn install --immutable
```

**环境变量（每个 shell 都要 export）：**

后端构建走 Java 21（Java 25 触发 Mockito 大批 "cannot modify class"，project memory 已记录）：

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

本 PR 不改后端，但跑 `mvn` 任何命令都需要这个 env。

---

## Task 1：将 STATUS_STYLE 提取为可复用导出

**Why first:** AttackPatternBox 在 verdict 着色模式下需要复用 §2.4 B4 已建的颜色映射（绿/红/橙/灰/N_A）。现状 `STATUS_STYLE` inline 在 `DoubleLayerNode.tsx`，不能 import。先做一个零行为变更的小重构：把它提取到 `attackChainRuntimeTypes.ts` 作为 `export const`，DoubleLayerNode 改 import。

**Files:**

- Modify: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/runtime/attackChainRuntimeTypes.ts` — 新增 `export const NODE_LAYER_STATUS_STYLE`
- Modify: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/runtime/DoubleLayerNode.tsx` — 删除内部 STATUS_STYLE 常量，改 import
- Test: 沿用 `__tests__/DoubleLayerNode.test.tsx`（已有），验证不破

### Steps

- [ ] **Step 1.1：在 `attackChainRuntimeTypes.ts` 末尾追加 `NODE_LAYER_STATUS_STYLE` 常量**

把当前 `DoubleLayerNode.tsx` 内的 `STATUS_STYLE` 一字不改地复制到 `attackChainRuntimeTypes.ts` 末尾，重命名为 `NODE_LAYER_STATUS_STYLE`：

```ts
import { type ReactElement } from 'react';
import {
  Block as BlockIcon,
  CheckCircle as CheckCircleIcon,
  HelpOutline as HelpOutlineIcon,
  HourglassEmpty as HourglassEmptyIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';

// ... existing types ...

/**
 * 节点双层 / ATT&CK matrix verdict 着色单一来源（PRD §2.4 + §2.3）.
 *
 * 颜色与 LinkExpectationPanel / VerdictBanner / AttackPatternBox 共享，避免多处分散.
 */
export const NODE_LAYER_STATUS_STYLE: Record<NodeLayerStatus, {
  preventionLabel: string;
  detectionLabel: string;
  background: string;
  color: string;
  borderStyle?: 'solid' | 'dashed';
  icon?: ReactElement;
}> = {
  SUCCESS: {
    preventionLabel: '已拦下',
    detectionLabel: '已检出',
    background: '#1b5e20',
    color: '#ffffff',
    icon: <CheckCircleIcon fontSize="small" color="success" />,
  },
  FAILED: {
    preventionLabel: '未拦下',
    detectionLabel: '未检出',
    background: '#b71c1c',
    color: '#ffffff',
    icon: <BlockIcon fontSize="small" color="error" />,
  },
  PARTIAL: {
    preventionLabel: '部分拦下',
    detectionLabel: '部分检出',
    background: '#e65100',
    color: '#ffffff',
    icon: <WarningIcon fontSize="small" color="warning" />,
  },
  PENDING: {
    preventionLabel: '等待结算',
    detectionLabel: '等待结算',
    background: '#9e9e9e',
    color: '#ffffff',
    icon: <HourglassEmptyIcon fontSize="small" color="info" />,
  },
  SKIPPED: {
    preventionLabel: '跳过',
    detectionLabel: '—',
    background: '#bdbdbd',
    color: '#000000',
    borderStyle: 'dashed',
  },
  N_A: {
    preventionLabel: '—',
    detectionLabel: '—',
    background: '#eeeeee',
    color: '#616161',
    icon: <HelpOutlineIcon fontSize="small" color="disabled" />,
  },
};
```

- [ ] **Step 1.2：`DoubleLayerNode.tsx` 删除内部 STATUS_STYLE 常量改 import**

```diff
-import { Repeat as RepeatIcon } from '@mui/icons-material';
-import { Box, Stack, Tooltip, Typography } from '@mui/material';
-
-import { type DoubleLayerNodeData, type NodeLayerStatus } from './attackChainRuntimeTypes';
+import { Repeat as RepeatIcon } from '@mui/icons-material';
+import { Box, Stack, Tooltip, Typography } from '@mui/material';
+
+import { type DoubleLayerNodeData, NODE_LAYER_STATUS_STYLE } from './attackChainRuntimeTypes';
```

删除整个 `const STATUS_STYLE: Record<NodeLayerStatus, {...}> = { SUCCESS: {...}, ... };`

把 `STATUS_STYLE[data.preventionStatus]` 改为 `NODE_LAYER_STATUS_STYLE[data.preventionStatus]`（替换两处：preventionStyle / detectionStyle）。

- [ ] **Step 1.3：跑现有测试验证 0 回归**

```bash
yarn vitest run src/admin/components/attack_chain_runs/attack_chain_run/runtime/__tests__/DoubleLayerNode.test.tsx
```

Expected: 全部 pass（行为零变更）。

- [ ] **Step 1.4：跑全套测试验证 baseline 不破**

```bash
yarn test
```

Expected: 27 files / 376 passed（与 main 基线一致）。

- [ ] **Step 1.5：Commit**

```bash
git add veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/runtime/attackChainRuntimeTypes.ts \
        veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/runtime/DoubleLayerNode.tsx
git commit -m "$(cat <<'EOF'
重构：STATUS_STYLE 提为 attackChainRuntimeTypes 导出常量（Phase 12c-Bi 准备）

为后续 AttackPatternBox 在 verdict 着色模式下复用同一颜色源做准备。
零行为变更：DoubleLayerNode 改 import，STATUS_STYLE → NODE_LAYER_STATUS_STYLE。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2：编辑器场景 adapter + 单测（pure function，先验证逻辑）

**Why:** 编辑器场景没有运行结果端点，要把节点 + injector_contract 的 `attack_patterns` 派生为 `NodeExpectationResultsByAttackPattern[]`（results 用 COVERED 占位）。运行画布场景直接用现有 `/api/attack_chain_runs/{id}/nodes/results-by-attack-patterns` 端点（`AttackChainRunComponent.tsx` 已示范），不需要 adapter。

**Files:**

- Create: `veriguard-front/src/admin/components/common/matrix/attackPatternMatrixAdapter.ts`
- Test: `veriguard-front/src/admin/components/common/matrix/__tests__/attackPatternMatrixAdapter.test.ts`

### Steps

- [ ] **Step 2.1：先写失败测试**

```ts
// __tests__/attackPatternMatrixAdapter.test.ts
import { describe, expect, it } from 'vitest';

import { type AttackChainNode, type NodeContract } from '../../../../../utils/api-types';
import { toAttackPatternResultsForEditor } from '../attackPatternMatrixAdapter';

const contract = (
  attackPatterns: string[],
  contractId = 'c1',
): Pick<NodeContract, 'injector_contract_id' | 'injector_contract_attack_patterns'> => ({
  injector_contract_id: contractId,
  injector_contract_attack_patterns: attackPatterns,
});

const node = (
  overrides: Partial<AttackChainNode> = {},
): AttackChainNode => ({
  node_id: `n-${Math.random()}`,
  node_title: 'node',
  node_depends_duration: 0,
  ...overrides,
} as AttackChainNode);

describe('toAttackPatternResultsForEditor', () => {
  it('空节点 → 空数组', () => {
    expect(toAttackPatternResultsForEditor([])).toEqual([]);
  });

  it('节点无 injector_contract → 跳过（不抛错）', () => {
    expect(toAttackPatternResultsForEditor([node()])).toEqual([]);
  });

  it('节点 contract.attack_patterns 为空 → 跳过', () => {
    const n = node({ node_injector_contract: contract([]) as NodeContract });
    expect(toAttackPatternResultsForEditor([n])).toEqual([]);
  });

  it('单节点单 pattern → 输出 1 条 result', () => {
    const n = node({
      node_id: 'n1',
      node_title: 'phishing',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n]);
    expect(result).toHaveLength(1);
    expect(result[0].node_attack_pattern).toBe('T1566');
    expect(result[0].node_expectation_results).toHaveLength(1);
    expect(result[0].node_expectation_results![0].node_id).toBe('n1');
    expect(result[0].node_expectation_results![0].node_title).toBe('phishing');
  });

  it('单节点多 patterns → 每个 pattern 一条 result，节点同时出现在每个 result 的 expectation_results', () => {
    const n = node({
      node_id: 'n1',
      node_injector_contract: contract(['T1566', 'T1059']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n]);
    expect(result.map(r => r.node_attack_pattern).sort()).toEqual(['T1059', 'T1566']);
    result.forEach((r) => {
      expect(r.node_expectation_results).toHaveLength(1);
      expect(r.node_expectation_results![0].node_id).toBe('n1');
    });
  });

  it('多节点同 pattern → 聚合到同一 result.node_expectation_results 数组', () => {
    const n1 = node({ node_id: 'n1', node_injector_contract: contract(['T1566']) as NodeContract });
    const n2 = node({ node_id: 'n2', node_injector_contract: contract(['T1566']) as NodeContract });
    const result = toAttackPatternResultsForEditor([n1, n2]);
    expect(result).toHaveLength(1);
    expect(result[0].node_attack_pattern).toBe('T1566');
    expect(result[0].node_expectation_results).toHaveLength(2);
    expect(result[0].node_expectation_results!.map(r => r.node_id).sort()).toEqual(['n1', 'n2']);
  });

  it('多节点不同 pattern → 各自一条 result', () => {
    const n1 = node({ node_id: 'n1', node_injector_contract: contract(['T1566']) as NodeContract });
    const n2 = node({ node_id: 'n2', node_injector_contract: contract(['T1059']) as NodeContract });
    const result = toAttackPatternResultsForEditor([n1, n2]);
    expect(result).toHaveLength(2);
  });

  it('混合：n1 (T1566+T1059), n2 (T1566) → T1566 含 [n1,n2]，T1059 含 [n1]', () => {
    const n1 = node({ node_id: 'n1', node_injector_contract: contract(['T1566', 'T1059']) as NodeContract });
    const n2 = node({ node_id: 'n2', node_injector_contract: contract(['T1566']) as NodeContract });
    const result = toAttackPatternResultsForEditor([n1, n2]);
    const byPattern = new Map(result.map(r => [r.node_attack_pattern, r.node_expectation_results!.map(x => x.node_id).sort()]));
    expect(byPattern.get('T1566')).toEqual(['n1', 'n2']);
    expect(byPattern.get('T1059')).toEqual(['n1']);
  });

  it('节点 expectation_results 数组中每条 results 字段都为 [] 占位（编辑器无 verdict）', () => {
    const n = node({ node_id: 'n1', node_injector_contract: contract(['T1566']) as NodeContract });
    const result = toAttackPatternResultsForEditor([n]);
    expect(result[0].node_expectation_results![0].results).toEqual([]);
  });

  it('保持节点顺序：第一个出现的节点排在 expectation_results 前面', () => {
    const n1 = node({ node_id: 'n1', node_injector_contract: contract(['T1566']) as NodeContract });
    const n2 = node({ node_id: 'n2', node_injector_contract: contract(['T1566']) as NodeContract });
    const n3 = node({ node_id: 'n3', node_injector_contract: contract(['T1566']) as NodeContract });
    const result = toAttackPatternResultsForEditor([n3, n1, n2]);
    expect(result[0].node_expectation_results!.map(r => r.node_id)).toEqual(['n3', 'n1', 'n2']);
  });

  it('null / undefined attack_patterns 字段安全处理', () => {
    const n = node({
      node_id: 'n1',
      node_injector_contract: { injector_contract_id: 'c1' } as NodeContract,
    });
    expect(toAttackPatternResultsForEditor([n])).toEqual([]);
  });
});
```

- [ ] **Step 2.2：跑测试确认失败**

```bash
yarn vitest run src/admin/components/common/matrix/__tests__/attackPatternMatrixAdapter.test.ts
```

Expected: FAIL with "Cannot find module '../attackPatternMatrixAdapter'" 或类似。

- [ ] **Step 2.3：实现 adapter**

```ts
// src/admin/components/common/matrix/attackPatternMatrixAdapter.ts
import { type AttackChainNode, type NodeExpectationResultsByAttackPattern } from '../../../../utils/api-types';

/**
 * 编辑器场景 adapter：节点 → ATT&CK matrix 输入 view-model.
 *
 * 运行画布场景**不**用此 adapter ——直接用现有 `/api/attack_chain_runs/{id}/nodes/results-by-attack-patterns`
 * 端点（{@code fetchAttackChainRunAttackChainNodeExpectationResults}）返回的同结构数据.
 *
 * 编辑器场景没有运行结果，把节点 contract 上挂的 attack_patterns 展开成
 * {@link NodeExpectationResultsByAttackPattern}[]，每个节点的 results 字段为 [] 占位.
 * AttackPatternBox 在 coloringScheme='coverage' 模式下只看是否有 expectation_results
 * 条目（≥1 节点 → 已覆盖），不看 results 内容.
 */
export const toAttackPatternResultsForEditor = (
  nodes: readonly AttackChainNode[],
): NodeExpectationResultsByAttackPattern[] => {
  const byPattern = new Map<string, { node_id?: string; node_title?: string; results: never[] }[]>();

  for (const node of nodes) {
    const patterns = node.node_injector_contract?.injector_contract_attack_patterns;
    if (!patterns || patterns.length === 0) {
      continue;
    }
    for (const patternId of patterns) {
      const list = byPattern.get(patternId);
      const entry = {
        node_id: node.node_id,
        node_title: node.node_title,
        results: [],
      };
      if (list) {
        list.push(entry);
      } else {
        byPattern.set(patternId, [entry]);
      }
    }
  }

  return Array.from(byPattern.entries()).map(([patternId, nodeEntries]) => ({
    node_attack_pattern: patternId,
    node_expectation_results: nodeEntries,
  }));
};
```

- [ ] **Step 2.4：跑测试确认通过**

```bash
yarn vitest run src/admin/components/common/matrix/__tests__/attackPatternMatrixAdapter.test.ts
```

Expected: 11 passed。

- [ ] **Step 2.5：lint 干净**

```bash
npx eslint src/admin/components/common/matrix/attackPatternMatrixAdapter.ts src/admin/components/common/matrix/__tests__/attackPatternMatrixAdapter.test.ts --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}"
```

Expected: 0 errors。如有 import 顺序错，跑 `--fix`。

- [ ] **Step 2.6：Commit**

```bash
git add veriguard-front/src/admin/components/common/matrix/attackPatternMatrixAdapter.ts \
        veriguard-front/src/admin/components/common/matrix/__tests__/attackPatternMatrixAdapter.test.ts
git commit -m "$(cat <<'EOF'
执行：编辑器场景 ATT&CK matrix adapter + 单测（Phase 12c-Bi Step 1）

toAttackPatternResultsForEditor 把 AttackChainNode[] 派生为
NodeExpectationResultsByAttackPattern[]：每个节点 contract 上的 attack_patterns
展开成 lookup，多节点同 pattern 聚合到同一 result.node_expectation_results.
results 字段 [] 占位，coverage 模式只用条目数判断"是否覆盖".

运行画布场景不走 adapter — 直接用现有
/api/attack_chain_runs/{id}/nodes/results-by-attack-patterns 端点
(fetchAttackChainRunAttackChainNodeExpectationResults，已在 AttackChainRunComponent 用过).

单测 11 个：空输入 / 无 contract / 空 patterns / 单节点单/多 pattern /
多节点同/不同 pattern / 混合聚合 / 顺序保留 / null 安全 / 占位 results.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：扩展共享 matrix 组件支持新着色 / 维度

**Why:** 三个共享组件加 props（带 default 保 backward compat）。AttackPatternBox 是核心：实现 `coverage`（编辑器二色：绿/灰）+ `verdict`（运行画布按 NODE_LAYER_STATUS_STYLE 着色 + verdictDimension 过滤）。MitreMatrix 在 `mode='full'` 时渲染所有 KillChainPhase + AttackPattern（来自 helper map）而非仅链路涉及。

**Files:**

- Modify: `veriguard-front/src/admin/components/common/matrix/MitreMatrix.tsx`
- Modify: `veriguard-front/src/admin/components/common/matrix/KillChainPhaseColumn.tsx`
- Modify: `veriguard-front/src/admin/components/common/matrix/AttackPatternBox.tsx`
- Test: 现有调用方 `AttackChainRunComponent.tsx` 不传新 props 时行为不变

### Steps

- [ ] **Step 3.1：MitreMatrix 加 props（默认值保 backward compat）**

```ts
// veriguard-front/src/admin/components/common/matrix/MitreMatrix.tsx
export type MatrixMode = 'compact' | 'full';
export type MatrixColoringScheme = 'coverage' | 'verdict';
export type MatrixVerdictDimension = 'prevention' | 'detection';

interface Props {
  goToLink?: string;
  injectResults: NodeExpectationResultsByAttackPattern[];
  mode?: MatrixMode;
  coloringScheme?: MatrixColoringScheme;
  verdictDimension?: MatrixVerdictDimension;
}

const MitreMatrix: FunctionComponent<Props> = ({
  goToLink,
  injectResults,
  mode = 'compact',
  coloringScheme = 'verdict',
  verdictDimension = 'prevention',
}) => {
  // ... existing useStyles / useHelper ...

  if (!injectResults) {
    return <MitreMatrixDummy />;
  }

  // 紧凑模式：仅链路涉及的 phases + patterns（现状）
  // 完整模式：全量 helper.getAttackPatternsMap() + KillChainPhasesMap()
  let phases: KillChainPhase[];
  let patternsByPhase: (phase: KillChainPhase) => AttackPattern[];

  if (mode === 'full') {
    const allPatterns = Object.values(attackPatternMap);
    phases = Object.values(killChainPhaseMap);
    patternsByPhase = (phase) =>
      allPatterns.filter(p => p.attack_pattern_kill_chain_phases?.includes(phase.phase_id));
  } else {
    // existing compact logic preserved
    const resultAttackPatternIds = R.uniq(
      injectResults
        .filter(injectResult => !!injectResult.node_attack_pattern)
        .flatMap(injectResult => injectResult.node_attack_pattern) as unknown as string[],
    );
    const resultAttackPatterns = resultAttackPatternIds
      .map((id: string) => attackPatternMap[id])
      .filter((p): p is AttackPattern => !!p);
    phases = R.uniq(
      resultAttackPatterns
        .flatMap(p => p.attack_pattern_kill_chain_phases ?? [])
        .map((phaseId: string) => killChainPhaseMap[phaseId])
        .filter((phase): phase is KillChainPhase => !!phase),
    );
    patternsByPhase = (phase) =>
      resultAttackPatterns.filter(p => p.attack_pattern_kill_chain_phases?.includes(phase.phase_id));
  }

  return (
    <div className={classes.container}>
      {[...phases].sort(sortKillChainPhase).map(phase => (
        <KillChainPhaseColumn
          key={phase.phase_id}
          goToLink={goToLink}
          killChainPhase={phase}
          attackPatterns={patternsByPhase(phase)}
          injectResults={injectResults}
          coloringScheme={coloringScheme}
          verdictDimension={verdictDimension}
        />
      ))}
    </div>
  );
};
```

- [ ] **Step 3.2：KillChainPhaseColumn 透传 props**

```diff
 interface KillChainPhaseComponentProps {
   goToLink?: string;
   killChainPhase: KillChainPhase;
   attackPatterns: AttackPattern[];
   injectResults: NodeExpectationResultsByAttackPattern[];
   dummy?: boolean;
+  coloringScheme?: 'coverage' | 'verdict';
+  verdictDimension?: 'prevention' | 'detection';
 }

 const KillChainPhaseColumn: FunctionComponent<KillChainPhaseComponentProps> = ({
   goToLink,
   killChainPhase,
   attackPatterns,
   injectResults,
   dummy,
+  coloringScheme = 'verdict',
+  verdictDimension = 'prevention',
 }) => {
   // ...
   <AttackPatternBox
     goToLink={goToLink}
     key={attackPattern.attack_pattern_id}
     attackPattern={attackPattern}
     injectResult={getAttackChainNodeResult(attackPattern)}
     dummy={dummy}
+    coloringScheme={coloringScheme}
+    verdictDimension={verdictDimension}
   />
```

- [ ] **Step 3.3：AttackPatternBox 着色逻辑**

新增 props + 在 verdict 模式下用 `NODE_LAYER_STATUS_STYLE` 染色 button 背景；coverage 模式下：有 `injectResult` → 绿 / 无 → 灰。verdict 模式下按 `verdictDimension` 过滤 `result.results.filter(r => r.type === 'PREVENTION'|'DETECTION')`。

```ts
// 顶部 import 加：
import { NODE_LAYER_STATUS_STYLE, type NodeLayerStatus }
  from '../../attack_chain_runs/attack_chain_run/runtime/attackChainRuntimeTypes';

// Props 增：
interface AttackPatternBoxProps {
  goToLink?: string;
  attackPattern: AttackPattern;
  injectResult: NodeExpectationResultsByAttackPattern | undefined;
  dummy?: boolean;
  coloringScheme?: 'coverage' | 'verdict';
  verdictDimension?: 'prevention' | 'detection';
}

const AttackPatternBox: FunctionComponent<AttackPatternBoxProps> = ({
  goToLink,
  attackPattern,
  injectResult,
  dummy,
  coloringScheme = 'verdict',
  verdictDimension = 'prevention',
}) => {
  // ... existing classes / state ...
  const results: NodeExpectationResultsByType[] = injectResult?.node_expectation_results ?? [];
  const isCovered = results.length > 0;

  // 派生 layer status（仅 verdict 模式用）
  const aggregateLayerStatus = (): NodeLayerStatus => {
    if (!isCovered) return 'N_A';
    const targetType = verdictDimension === 'prevention' ? 'PREVENTION' : 'DETECTION';
    const layerResults = results
      .flatMap(r => r.results ?? [])
      .filter(er => er.type === targetType)
      .map(er => er.avgResult);
    if (layerResults.length === 0) return 'N_A';
    if (layerResults.every(s => s === 'SUCCESS')) return 'SUCCESS';
    if (layerResults.every(s => s === 'FAILED')) return 'FAILED';
    if (layerResults.every(s => s === 'PENDING' || s === 'UNKNOWN')) return 'PENDING';
    return 'PARTIAL';
  };

  // 计算 box 着色（覆盖 default 的 background）
  let boxBackground: string | undefined;
  let boxColor: string | undefined;
  let boxBorderStyle: 'solid' | 'dashed' | undefined;

  if (!dummy) {
    if (coloringScheme === 'coverage') {
      boxBackground = isCovered ? NODE_LAYER_STATUS_STYLE.SUCCESS.background : NODE_LAYER_STATUS_STYLE.N_A.background;
      boxColor = isCovered ? NODE_LAYER_STATUS_STYLE.SUCCESS.color : NODE_LAYER_STATUS_STYLE.N_A.color;
    } else {
      const status = aggregateLayerStatus();
      const style = NODE_LAYER_STATUS_STYLE[status];
      boxBackground = style.background;
      boxColor = style.color;
      boxBorderStyle = style.borderStyle;
    }
  }
```

把现有 `<Button className={classes.button}>` 渲染分支保留作为 `dummy` 路径；非 dummy 路径改成：

```tsx
<Button
  aria-haspopup="true"
  aria-expanded={open ? 'true' : undefined}
  className={classes.button}
  onClick={event => handleOpen(event)}
  style={{
    background: boxBackground,
    color: boxColor,
    borderStyle: boxBorderStyle ?? 'solid',
    borderWidth: 1,
  }}
>
  ...
</Button>
```

- [ ] **Step 3.4：跑共享组件相关测试 + 现有调用方 smoke**

```bash
yarn check-ts
```

Expected: 0 errors。

```bash
yarn test
```

Expected: 27 files / 376 passed（与 main 基线一致；现有 `AttackChainRunComponent` 不传新 props 时行为不变 — 默认 mode=compact / coloringScheme=verdict / verdictDimension=prevention）。

- [ ] **Step 3.5：lint 干净**

```bash
npx eslint src/admin/components/common/matrix/MitreMatrix.tsx src/admin/components/common/matrix/KillChainPhaseColumn.tsx src/admin/components/common/matrix/AttackPatternBox.tsx --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}"
```

Expected: 0 errors（可能要 `--fix` import 顺序）。

- [ ] **Step 3.6：Commit**

```bash
git add veriguard-front/src/admin/components/common/matrix/MitreMatrix.tsx \
        veriguard-front/src/admin/components/common/matrix/KillChainPhaseColumn.tsx \
        veriguard-front/src/admin/components/common/matrix/AttackPatternBox.tsx
git commit -m "$(cat <<'EOF'
执行：MitreMatrix / KillChainPhaseColumn / AttackPatternBox 加 props 支持
新着色 + 完整 ATT&CK 矩阵模式（Phase 12c-Bi Step 2）

加 props（全 default 保 backward compat：mode='compact' / coloringScheme='verdict' /
verdictDimension='prevention'，与现状唯一行为一致）：

- MitreMatrix: mode 'compact' | 'full'（full 用 helper map 渲染全量 phases+patterns）
- KillChainPhaseColumn: 透传 coloringScheme + verdictDimension
- AttackPatternBox: coverage 模式按 isCovered 染绿/灰；verdict 模式按
  verdictDimension 过滤 type 后聚合 NodeLayerStatus 用 NODE_LAYER_STATUS_STYLE 染色

现有调用方 AttackChainRunComponent.tsx 0 改动；376 测试 0 回归.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：i18n 词条

**Files:**

- Modify: `veriguard-front/src/utils/lang/zh.json`
- Modify: `veriguard-front/src/utils/lang/en.json`

### Steps

- [ ] **Step 4.1：zh.json 加 key**

在 `"Attack chain orchestration"` 附近（按字母序）插入：

```diff
   "Attack chain": "攻击编排",
   "Attack chain orchestration": "攻击编排",
+  "Attack pattern matrix": "ATT&CK 矩阵",
+  "Compact view": "紧凑视图",
+  "Full ATT&CK matrix": "完整 ATT&CK 矩阵",
+  "Coverage": "覆盖度",
+  "By prevention": "按拦截维度",
+  "By detection": "按检测维度",
   "Attack chains": "攻击链路",
```

- [ ] **Step 4.2：en.json 加 key**

```diff
   "Attack chain": "Attack chain",
   "Attack chain orchestration": "Attack chain orchestration",
+  "Attack pattern matrix": "Attack pattern matrix",
+  "Compact view": "Compact view",
+  "Full ATT&CK matrix": "Full ATT&CK matrix",
+  "Coverage": "Coverage",
+  "By prevention": "By prevention",
+  "By detection": "By detection",
   "Attack chains": "Attack chains",
```

- [ ] **Step 4.3：JSON 合法性验证**

```bash
node -e "JSON.parse(require('fs').readFileSync('src/utils/lang/zh.json','utf8')); JSON.parse(require('fs').readFileSync('src/utils/lang/en.json','utf8')); console.log('JSON OK')"
```

Expected: `JSON OK`。

---

## Task 5：编辑器 host 加 viewMode 'matrix' tab

**Files:**

- Modify: `veriguard-front/src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx`

### Steps

- [ ] **Step 5.1：先看现有 host 当前 viewMode 实现**

```bash
grep -n 'availableButtons\|viewMode' src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx | head
```

预期：与运行画布 host (`AttackChainRunAttackChainNodes.tsx` Phase 12b-B4 已加 4 个 viewMode) 类似。

- [ ] **Step 5.2：在 imports + state 加 matrix 支持**

`availableButtons` 数组改：

```diff
-  const availableButtons = ['chain', 'list', 'distribution'];
+  const availableButtons = ['chain', 'list', 'distribution', 'matrix'];
```

新增 matrix-related state：

```ts
const [matrixMode, setMatrixMode] = useState<'compact' | 'full'>('compact');
```

- [ ] **Step 5.3：加 matrix view 渲染分支**

在 render 中（`viewMode === 'distribution'` 后面）加：

```tsx
{viewMode === 'matrix' && (
  <div style={{ marginTop: -12 }}>
    <ToggleButtonGroup
      size="small"
      exclusive
      style={{ float: 'right', marginBottom: 8 }}
      value={matrixMode}
      onChange={(_e, v) => v && setMatrixMode(v)}
    >
      <ToggleButton value="compact" aria-label="Compact view">{t('Compact view')}</ToggleButton>
      <ToggleButton value="full" aria-label="Full ATT&CK matrix">{t('Full ATT&CK matrix')}</ToggleButton>
    </ToggleButtonGroup>
    <Typography variant="h4">{t('Attack pattern matrix')}</Typography>
    <Paper variant="outlined" sx={{ padding: 2 }}>
      <MitreMatrix
        injectResults={toAttackPatternResultsForEditor(attackChainNodes ?? [])}
        mode={matrixMode}
        coloringScheme="coverage"
      />
    </Paper>
  </div>
)}
```

补 imports：

```ts
import { useMemo } from 'react';
import MitreMatrix from '../../../common/matrix/MitreMatrix';
import { toAttackPatternResultsForEditor } from '../../../common/matrix/attackPatternMatrixAdapter';
```

加 helper 拿节点（如尚未拿）：

```ts
const { attackChainNodes } = useHelper((helper: AttackChainNodeHelper) => ({
  attackChainNodes: helper.getAttackChainAttackChainNodes(scenarioId),
}));
```

memoize adapter：

```ts
const editorMatrixResults = useMemo(
  () => toAttackPatternResultsForEditor(attackChainNodes ?? []),
  [attackChainNodes],
);
// 用 {editorMatrixResults} 替换上面的 toAttackPatternResultsForEditor(...) 调用
```

注意：`scenarioId` / helper getter 名称按现有 host 文件中实际命名调整（编辑器 host 用 `scenarioId`，运行画布用 `exerciseId`）。

- [ ] **Step 5.4：toggle button 加 matrix 入口**

找到现有 `'chain' | 'list' | 'distribution'` toggle group（一般在 distribution 视图分支内的 `<ToggleButtonGroup>`），加第 4 个：

```tsx
<Tooltip title={t('Attack pattern matrix')}>
  <ToggleButton
    value="matrix"
    onClick={() => handleViewMode('matrix')}
    aria-label="Attack pattern matrix"
  >
    <GridOnOutlined fontSize="small" color="primary" />
  </ToggleButton>
</Tooltip>
```

补 `import { GridOnOutlined } from '@mui/icons-material';`。

注意：现有 `chain` / `list` viewMode 的 toggle group 在哪？搜 `<ToggleButtonGroup` 看现有是否在 chain/list 分支也展示 toggle。如果只在 distribution 分支显示 toggle group，则加在 distribution 的 toggle group + 在 chain/list 分支的 `<AttackChainNodes setViewMode=...>` 里也加（取决于 `AttackChainNodes` 组件接 availableButtons 渲染 toggle）。

`<AttackChainNodes>` 共享组件已接 `availableButtons` prop —— 第 5.2 步把 'matrix' 加进数组后，组件会自动渲染第 4 个 toggle。验证：跑 dev server 切到编辑器看是否多了一个 toggle button。本步骤目标：让 `availableButtons` 数组包含 `'matrix'`，剩下交给 `<AttackChainNodes>`。

如 `<AttackChainNodes>` 内部是否给 `'matrix'` 渲染 icon 是硬编码的话需要扩展那个组件的 toggle icon switch。先不动 — Task 5/6 完成后跑 vitest，再看 UI 验证再决定。

- [ ] **Step 5.5：跑测试 + lint**

```bash
yarn check-ts && yarn test
```

Expected: check-ts 0 errors / test 27 files 376+ passed（adapter 测试 11 已加，总测试数应升到 387）。

```bash
npx eslint src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}"
```

Expected: 0 errors。

---

## Task 6：运行画布 host 加 viewMode 'matrix' tab + verdict 维度 toggle

**Files:**

- Modify: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx`

### Steps

- [ ] **Step 6.1：加 matrix-related state**

```ts
const [matrixMode, setMatrixMode] = useState<'compact' | 'full'>('compact');
const [verdictDimension, setVerdictDimension] = useState<'prevention' | 'detection'>('prevention');
const [attackPatternResults, setAttackPatternResults] = useState<NodeExpectationResultsByAttackPattern[]>([]);
```

补 `import { type NodeExpectationResultsByAttackPattern } from '../../../../../utils/api-types';`

- [ ] **Step 6.2：useEffect fetch attack pattern results**

```ts
import { fetchAttackChainRunAttackChainNodeExpectationResults } from '../../../../../actions/attack_chain_runs/attack_chain_run-action';

// ...
useEffect(() => {
  let cancelled = false;
  fetchAttackChainRunAttackChainNodeExpectationResults(exerciseId)
    .then((response) => {
      if (cancelled || !response) return;
      const data = (response as { data?: NodeExpectationResultsByAttackPattern[] }).data;
      setAttackPatternResults(Array.isArray(data) ? data : []);
    })
    .catch(() => {
      // simpleCall 已 notifyErrorHandler；不二次显示
    });
  return () => { cancelled = true; };
}, [exerciseId]);
```

- [ ] **Step 6.3：availableButtons 加 matrix + 渲染分支**

```diff
-  const availableButtons = ['chain', 'list', 'distribution'];
+  const availableButtons = ['chain', 'list', 'distribution', 'matrix'];
```

在 distribution 分支后加 matrix 分支：

```tsx
{viewMode === 'matrix' && (
  <div style={{ marginTop: -12 }}>
    <Stack direction="row" spacing={1} sx={{ float: 'right', marginBottom: 1 }}>
      <ToggleButtonGroup size="small" exclusive value={matrixMode} onChange={(_e, v) => v && setMatrixMode(v)}>
        <ToggleButton value="compact">{t('Compact view')}</ToggleButton>
        <ToggleButton value="full">{t('Full ATT&CK matrix')}</ToggleButton>
      </ToggleButtonGroup>
      <ToggleButtonGroup size="small" exclusive value={verdictDimension} onChange={(_e, v) => v && setVerdictDimension(v)}>
        <ToggleButton value="prevention">{t('By prevention')}</ToggleButton>
        <ToggleButton value="detection">{t('By detection')}</ToggleButton>
      </ToggleButtonGroup>
    </Stack>
    <Typography variant="h4">{t('Attack pattern matrix')}</Typography>
    <Paper variant="outlined" sx={{ padding: 2 }}>
      <MitreMatrix
        injectResults={attackPatternResults}
        mode={matrixMode}
        coloringScheme="verdict"
        verdictDimension={verdictDimension}
        goToLink={`/admin/attack_chain_runs/${exerciseId}/nodes`}
      />
    </Paper>
  </div>
)}
```

补 imports：`Stack` from `@mui/material`、`MitreMatrix` from `'../../../common/matrix/MitreMatrix'`。

- [ ] **Step 6.4：跑测试 + lint**

```bash
yarn check-ts
yarn test
npx eslint src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx --max-warnings 0 --rule "{'import/namespace': 'error', 'import/no-cycle': ['error', {'ignoreExternal': true, 'disableScc': true}]}"
```

Expected: 全 0 errors / 376+ tests passed。

- [ ] **Step 6.5：Commit Task 4 + 5 + 6 一起**

```bash
git add veriguard-front/src/utils/lang/zh.json \
        veriguard-front/src/utils/lang/en.json \
        veriguard-front/src/admin/components/attack_chains/attack_chain/attack_chain_nodes/AttackChainAttackChainNodes.tsx \
        veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/attack_chain_nodes/AttackChainRunAttackChainNodes.tsx
git commit -m "$(cat <<'EOF'
执行：编辑器 + 运行画布 host 接 ATT&CK matrix tab + i18n（Phase 12c-Bi Step 3）

- 编辑器 (AttackChainAttackChainNodes.tsx)：viewMode 加 'matrix'，
  通过 toAttackPatternResultsForEditor 派生 results，
  <MitreMatrix coloringScheme="coverage" mode={compact|full}>
- 运行画布 (AttackChainRunAttackChainNodes.tsx)：viewMode 加 'matrix'，
  useEffect 调 fetchAttackChainRunAttackChainNodeExpectationResults（已有端点）拿数据，
  <MitreMatrix coloringScheme="verdict" verdictDimension={prevention|detection}>，
  双 toggle group（紧凑/完整 + 拦截/检测维度）
- i18n: zh + en 加 6 keys（Attack pattern matrix / Compact view /
  Full ATT&CK matrix / Coverage / By prevention / By detection）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7：验证 + push + PR

**Files:** 无新改动，仅验证。

### Steps

- [ ] **Step 7.1：再跑一次全套**

```bash
yarn check-ts
yarn test
yarn lint  # 可能 baseline 有 cache 问题；如全跑报基线 errors 是 baseline 问题非本 PR
```

Expected: check-ts 0 / test 27 files 387 passed (376 baseline + 11 adapter) / lint clean for touched files。

- [ ] **Step 7.2：master 锁验证**

```bash
git -C /Users/lamba/github/Veriguard ls-remote origin master | head -1
```

Expected: `5d7e05da627639491274175d2a77cf95b2c0fe7d	refs/heads/master`（不变）。

- [ ] **Step 7.3：push branch**

```bash
git push -u origin feat/attack-chain-phase-12c-Bi-attack-pattern-matrix
```

- [ ] **Step 7.4：创建 PR**

```bash
gh pr create --base main --title "二开 Phase 12c-Bi: 攻击链路 ATT&CK 矩阵视图（编辑器 + 运行画布双场景）" --body "$(cat <<'EOF'
## Summary

PRD §2.3 第 1 行 ATT&CK 自动划分维度落地。复用现有 \`MitreMatrix\` 共享组件，在攻击链路编辑器 + 运行画布两处加 viewMode='matrix' tab。

设计稿：\`docs/superpowers/specs/2026-05-10-veriguard-attack-pattern-matrix-design.md\`

3 个 commit：

- **Step 1**: 编辑器 adapter \`toAttackPatternResultsForEditor\`（pure function）+ 11 单测
- **Step 2**: \`MitreMatrix\` / \`KillChainPhaseColumn\` / \`AttackPatternBox\` 加 props（全 default 保 backward compat）；新增 mode='full' / coloringScheme='coverage' / verdictDimension toggle 支持
- **Step 3**: 编辑器 + 运行画布两 host 接 matrix tab + i18n

\`STATUS_STYLE\` 提取为 \`runtime/attackChainRuntimeTypes.ts\` 的 \`NODE_LAYER_STATUS_STYLE\` 导出常量（小重构作为前置 commit）—— 让 AttackPatternBox 复用同一颜色源.

后端 0 改动（运行画布场景直接用现有 \`/api/attack_chain_runs/{id}/nodes/results-by-attack-patterns\` 端点）.

## Test plan

- [x] yarn check-ts 0 errors
- [x] yarn test 387 passed (376 baseline + 11 adapter)
- [x] npx eslint touched files 0 errors  
- [x] origin/master 仍锁 5d7e05da6
- [ ] dev server 视觉验证 matrix tab 渲染正确
- [ ] 编辑器 matrix 紧凑/完整 toggle 切换正确
- [ ] 运行画布 prevention/detection 维度 toggle 切换着色变化

EOF
)"
```

- [ ] **Step 7.5：报告 PR URL 给用户**

PR 创建后输出 URL 给用户。

---

## 自审 checklist

完成所有 task 后做最后一遍：

- [ ] PRD §2.3 第 1 行 ATT&CK 自动划分覆盖（spec §11 完成标准#1）
- [ ] 编辑器 + 运行画布两处都有 matrix tab（spec §11 完成标准#2）
- [ ] Backward compat：现有 MitreMatrix 调用方（`AttackChainRunComponent`）行为零变更（spec §5）
- [ ] Adapter 单测 ≥10 场景（spec §7）—— 实际 11
- [ ] master 不动（spec §11 完成标准#5）
- [ ] PR base=main（用户 feedback memory）

---

## 范围 boundary 提醒（spec §10 — 本 PR 不做）

- ❌ 跨链路全局 ATT&CK 覆盖度页
- ❌ 纵深防御维度
- ❌ 点格子展开节点详情 popover（AttackPatternBox 现有 menu 行为保持）
- ❌ ATT&CK seed 数据补全
- ❌ ATT&CK sub-techniques
- ❌ ATT&CK Navigator JSON 导出
- ❌ 后端新 endpoint
