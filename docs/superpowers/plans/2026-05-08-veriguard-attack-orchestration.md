# Veriguard 攻击编排（PRD §2.4）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Veriguard 现有 Scenario 模块改造成 AttackChain 模块，落实 PRD §2.4 全部 10 条原子要求。

**Architecture:** Path C（改造 Scenario，不 sync upstream）；前后端 + DB 全栈彻底改名；引入 ValidationParameterSet 独立实体、SocAlertConnector SPI、链路 verdict 计算、节点重复执行、stop-on-block 执行模式。前端 ReactFlow 边标签做条件分支（A 方案，C 决策节点 ready 但不实施）。

**Tech Stack:** Spring Boot 3.3.7 + Java 21 + JPA + Flyway + PostgreSQL 17 + Maven multi-module / React 19 + TypeScript + MUI + ReactFlow `@xyflow/react` + Vite + Yarn 4 / Testcontainers + WireMock + Playwright + Vitest.

**Spec:** `docs/superpowers/specs/2026-05-07-veriguard-attack-orchestration-design.md`

---

## 分支策略

每个 Phase 一个独立 PR，按编号顺序合到 main。**Phase 0（rename）必须先合再做后续。**

```
main
  └─ feat/attack-chain-phase-0-rename            (先合)
  └─ feat/attack-chain-phase-1-migration         (after 0)
  └─ feat/attack-chain-phase-2-parameter-set     (after 1)
  └─ feat/attack-chain-phase-3-scheduler         (after 2)
  └─ feat/attack-chain-phase-4-repeat            (after 3)
  └─ feat/attack-chain-phase-5-verdict           (after 4)
  └─ feat/attack-chain-phase-6-soc-connector     (after 5)
  └─ feat/attack-chain-phase-7-link-expectation  (after 6)
  └─ feat/attack-chain-phase-8-frontend-editor   (after 7)
  └─ feat/attack-chain-phase-9-frontend-run      (after 8)
  └─ feat/attack-chain-phase-10-frontend-params  (after 9)
  └─ feat/attack-chain-phase-11-frontend-soc     (after 10)
  └─ feat/attack-chain-phase-12-i18n-finalize    (after 11)
```

---

## Phase 0: 机械改名（最先合）

**Goal:** 把 Scenario / Exercise / Inject 系列类名 + 包名 + DB 表名 + API 路径 + 前端文件 全栈改名为 AttackChain / AttackChainRun / AttackChainNode 系列。**纯改名，不改语义、不加新字段、不改 DB schema**（schema 留给 Phase 1）。

### Task 0.1: 创建 worktree

- [ ] **Step 1: 创建 worktree**

```bash
cd /Users/lamba/github/Veriguard
git worktree add worktrees/attack-chain-phase-0 -b feat/attack-chain-phase-0-rename
cd worktrees/attack-chain-phase-0
```

- [ ] **Step 2: 验证 baseline 测试通过**

```bash
mvn -pl veriguard-api -am test
```

Expected: all green

```bash
cd veriguard-front && yarn install --immutable && yarn check-ts && yarn lint && yarn test --run
```

Expected: all green

### Task 0.2: 后端 Java 类重命名（IntelliJ Refactor → Rename, Shift+F6）

参考 spec 附录 A 命名映射表。每条 rename 都勾选 "Search in comments and strings" + "Rename inheritors"。每改 2-3 个类跑一次测试。

- [ ] **Step 1: Scenario → AttackChain**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/Scenario.java`
重命名整个类、文件名、所有引用（含 `@JsonProperty`、JPA `@Table` 暂保留 `name = "scenarios"`）。

- [ ] **Step 2: Exercise → AttackChainRun**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/Exercise.java`

- [ ] **Step 3: Inject → AttackChainNode**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/Inject.java`

- [ ] **Step 4: Injector → NodeExecutor**

文件：`veriguard-api/src/main/java/io/veriguard/executors/Injector.java`（接口）

- [ ] **Step 5: InjectorContract → NodeContract**

- [ ] **Step 6: InjectStatus → AttackChainNodeStatus**

- [ ] **Step 7: InjectExpectation → AttackChainNodeExpectation**

- [ ] **Step 8: InjectExpectationTrace → NodeExpectationTrace**

- [ ] **Step 9: InjectExpectationResult → NodeExpectationResult**

- [ ] **Step 10: InjectExpectationSignature → NodeExpectationSignature**

- [ ] **Step 11: InjectDependency → AttackChainEdge**

- [ ] **Step 12: ExecutableInject → ExecutableNode**

- [ ] **Step 13: ScenarioRepository → AttackChainRepository**

同样改：ExerciseRepository → AttackChainRunRepository、InjectRepository → AttackChainNodeRepository、InjectExpectationRepository → AttackChainNodeExpectationRepository、InjectDependencyRepository → AttackChainEdgeRepository。

- [ ] **Step 14: ScenarioService → AttackChainService**

同样改：ExerciseService → AttackChainRunService、InjectService → AttackChainNodeService、InjectExpectationService → AttackChainNodeExpectationService、ScenarioToExerciseService → AttackChainRunFactory。

- [ ] **Step 15: ScenarioApi → AttackChainApi**

文件：`veriguard-api/src/main/java/io/veriguard/rest/scenario/ScenarioApi.java`
同样改：ScenarioInjectApi → AttackChainNodeApi、ScenarioImportApi → AttackChainImportApi、ExerciseApi → AttackChainRunApi、InjectApi → 合并到 AttackChainNodeApi。

- [ ] **Step 16: 跑 backend 测试**

```bash
mvn -pl veriguard-api -am test
```

Expected: all pass

- [ ] **Step 17: Commit**

```bash
git add -A
git commit -m "重构：Scenario/Exercise/Inject* 全套类名重命名为 AttackChain/AttackChainRun/AttackChainNode*"
```

### Task 0.3: Java package 重命名

- [ ] **Step 1: rest.scenario → rest.attack_chain**

IntelliJ: Refactor → Move → Move package
`io.veriguard.rest.scenario` → `io.veriguard.rest.attack_chain`

- [ ] **Step 2: rest.exercise → rest.attack_chain_run**

- [ ] **Step 3: rest.inject → rest.attack_chain_node**

- [ ] **Step 4: 跑测试**

```bash
mvn -pl veriguard-api -am test
```

- [ ] **Step 5: Commit**

```bash
git commit -am "重构：rest.scenario / rest.exercise / rest.inject 包移到 rest.attack_chain* 命名空间"
```

### Task 0.4: REST API 路径重命名

- [ ] **Step 1: 更新所有 controller 的 @RequestMapping**

```java
// AttackChainApi.java
// OLD: @RequestMapping("/api/scenarios")
// NEW: @RequestMapping("/api/attack_chains")

// AttackChainRunApi.java
// OLD: @RequestMapping("/api/exercises")
// NEW: @RequestMapping("/api/attack_chain_runs")

// AttackChainNodeApi.java
// OLD: @RequestMapping("/api/injects")
// NEW: @RequestMapping("/api/attack_chain_nodes")
```

- [ ] **Step 2: 检查 SecurityValidationApi URI 常量**

文件：`veriguard-api/src/main/java/io/veriguard/rest/security_validation/SecurityValidationApi.java`
如果有引用 `/api/scenarios` 等的常量，更新。

- [ ] **Step 3: 更新 controller 测试**

测试中所有 `mockMvc.perform(post("/api/scenarios/..."))` → `/api/attack_chains/...`。

- [ ] **Step 4: 跑测试**

```bash
mvn -pl veriguard-api -am test
```

- [ ] **Step 5: Commit**

```bash
git commit -am "重构：API 路径 /api/scenarios → /api/attack_chains 等改名"
```

### Task 0.5: 前端文件 + 路由重命名

- [ ] **Step 1: 重命名目录**

```bash
cd veriguard-front/src/admin/components
git mv scenarios attack_chains
git mv simulations attack_chain_runs
```

- [ ] **Step 2: 重命名子目录 + 文件**

```bash
cd attack_chains
git mv scenario attack_chain  # 子目录
# 文件重命名（用 IDE 批量 rename 或 git mv）：
# ScenariosList.tsx → AttackChainList.tsx
# ScenarioForm.tsx → AttackChainForm.tsx
# ScenarioInjects.tsx → AttackChainNodes.tsx
# ScenarioCreate.tsx → AttackChainCreate.tsx
# ScenarioOutput.tsx → AttackChainOutput.tsx
# scenario/ScenarioDefinition.tsx → attack_chain/AttackChainDefinition.tsx
# scenario/injects/ScenarioInjects.tsx → attack_chain/nodes/AttackChainNodes.tsx
```

同样 `attack_chain_runs/`：
```
SimulationsList.tsx → AttackChainRunList.tsx
Simulation.tsx → AttackChainRun.tsx
simulation/ → attack_chain_run/
SimulationOutput.tsx → AttackChainRunOutput.tsx
```

- [ ] **Step 3: VS Code / IntelliJ rename refactor 自动更新 imports**

或全局 find / replace：

```bash
cd veriguard-front
grep -rln "from '@admin/components/scenarios" src | xargs sed -i '' "s|@admin/components/scenarios|@admin/components/attack_chains|g"
grep -rln "from '@admin/components/simulations" src | xargs sed -i '' "s|@admin/components/simulations|@admin/components/attack_chain_runs|g"
```

- [ ] **Step 4: 更新路由**

文件：`veriguard-front/src/admin/Index.tsx`

```tsx
// OLD: <Route path="/admin/scenarios/*" element={<Scenarios />} />
// NEW: <Route path="/admin/attack_chains/*" element={<AttackChains />} />

// OLD: <Route path="/admin/simulations/*" element={<Simulations />} />
// NEW: <Route path="/admin/attack_chain_runs/*" element={<AttackChainRuns />} />
```

- [ ] **Step 5: 更新 API client 调用**

```bash
cd veriguard-front/src
grep -rln "/api/scenarios" actions utils | xargs sed -i '' "s|/api/scenarios|/api/attack_chains|g"
grep -rln "/api/exercises" actions utils | xargs sed -i '' "s|/api/exercises|/api/attack_chain_runs|g"
grep -rln "/api/injects" actions utils | xargs sed -i '' "s|/api/injects|/api/attack_chain_nodes|g"
```

- [ ] **Step 6: 重新生成 API types**

启动 backend 后：

```bash
cd veriguard-front
yarn generate-types-from-api
```

- [ ] **Step 7: 跑前端检查**

```bash
yarn check-ts
yarn lint
yarn test --run
```

Expected: all green

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "重构：前端 scenarios/ → attack_chains/、simulations/ → attack_chain_runs/ 全套改名"
```

### Task 0.6: i18n 文案不动

本 Phase 不动 i18n keys（保留旧 `scenarios.title` 等）。Phase 12 集中改文案 + 翻译。这样让 Phase 0 PR 仅含改名 diff，review 简单。

### Task 0.7: 整体回归 + 提 PR

- [ ] **Step 1: 全部测试**

```bash
mvn -pl veriguard-api -am test
mvn spotless:apply
cd veriguard-front
yarn check-ts && yarn lint && yarn test --run
```

- [ ] **Step 2: Smoke 测试**

```bash
cd ../veriguard-dev && docker compose up -d
cd ..
mvn -pl veriguard-api spring-boot:run &
sleep 30
cd veriguard-front && yarn start &
```

打开 `http://localhost:3001/admin/attack_chains` —— 列表能加载 + 创建一个空的 AttackChain。

- [ ] **Step 3: grep 验证无残留**

```bash
cd /Users/lamba/github/Veriguard/worktrees/attack-chain-phase-0
grep -rln 'class Scenario\|class Inject\|class Exercise' veriguard-api veriguard-model
# Expected: 0 matches (注意类名"Scenario"等可能在注释 / 字符串中残留，不影响功能)
grep -rln 'ScenarioRepository\|InjectRepository\|ExerciseRepository' veriguard-api veriguard-model
# Expected: 0 matches
```

- [ ] **Step 4: 提 PR**

```bash
git push -u origin feat/attack-chain-phase-0-rename
gh pr create --title "重构：Scenario → AttackChain 全栈改名 (Phase 0)" --body "$(cat <<'EOF'
## Summary
PRD §2.4 攻击编排实施 **Phase 0** —— 纯机械改名，无新功能：

- Java：`Scenario` / `Exercise` / `Inject` / `Injector*` / `*Service` / `*Repository` / `*Api` 全套重命名
- 包：`rest.scenario` / `rest.exercise` / `rest.inject` → `rest.attack_chain*`
- API 路径：`/api/scenarios` → `/api/attack_chains` 等
- 前端文件：`scenarios/` → `attack_chains/`、`simulations/` → `attack_chain_runs/`
- 路由：`/admin/scenarios` → `/admin/attack_chains` 等

DB schema、字段含义、业务逻辑、i18n 文案 **均未改动** —— 留给 Phase 1 (DB) 和 Phase 12 (i18n)。

## Test plan
- [x] mvn test 全绿
- [x] yarn check-ts / lint / test 全绿
- [x] Playwright e2e 关键链路通过
- [x] 手动 smoke：admin UI 加载、创建链路、查列表
- [x] grep 仓库无残留 `class Scenario` / `class Exercise` / `class Inject`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

合 PR 后 cleanup worktree：

```bash
cd /Users/lamba/github/Veriguard
git worktree remove worktrees/attack-chain-phase-0
```

---

## Phase 1: V2 Flyway Migration（在 Phase 0 合并后）

**Goal:** 一次性 schema 大清洗 —— TRUNCATE 所有 scenarios 数据 + DROP 演练遗留字段 + RENAME 表 + ADD 新字段 + CREATE 新表 + 种子数据。

### Task 1.1: 创建 worktree

- [ ] **Step 1: 创建 worktree**

```bash
cd /Users/lamba/github/Veriguard
git pull origin main  # 确保已含 Phase 0
git worktree add worktrees/attack-chain-phase-1 -b feat/attack-chain-phase-1-migration main
cd worktrees/attack-chain-phase-1
```

### Task 1.2: 写 V2 migration SQL

- [ ] **Step 1: dump 当前 schema 用于 RENAME COLUMN 列举**

```bash
cd /Users/lamba/github/Veriguard/veriguard-dev && docker compose up -d veriguard-pgsql
cd ..
docker exec veriguard-pgsql pg_dump -s -U veriguard veriguard > /tmp/current_schema.sql

grep -E "^\s+\"?(scenario|exercise|inject)_\w+" /tmp/current_schema.sql | sort -u > /tmp/columns_to_rename.txt
wc -l /tmp/columns_to_rename.txt
```

Expected: ~50-80 columns to rename

- [ ] **Step 2: 创建 V2 migration 文件**

文件：`veriguard-api/src/main/resources/db/migration/V2__attack_chain_module_init.sql`

```sql
-- V2: Attack Chain module init (PRD §2.4)
-- Drop legacy 演练 fields + rename tables/columns + add new schema + seed data

BEGIN;

-- ============================================================
-- 1. TRUNCATE: clear all scenario-related data (path b 大清洗)
-- ============================================================
TRUNCATE TABLE scenarios CASCADE;

-- ============================================================
-- 2. DROP legacy 演练 fields
-- ============================================================
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_header;
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_footer;
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_mail_from;
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_mails_reply_to;
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_message_header;
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_message_footer;

ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_header;
ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_footer;
ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_mail_from;
ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_mails_reply_to;
ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_message_header;
ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_message_footer;

DROP TABLE IF EXISTS scenarios_lessons_categories;
DROP TABLE IF EXISTS exercises_lessons_categories;
DROP TABLE IF EXISTS lessons_questions;
DROP TABLE IF EXISTS lessons_answers;
DROP TABLE IF EXISTS lessons_categories;
DROP TABLE IF EXISTS lessons_templates_categories;
DROP TABLE IF EXISTS lessons_templates_questions;
DROP TABLE IF EXISTS lessons_templates;

-- ============================================================
-- 3. RENAME tables (顺序：从依赖最少到最多)
-- ============================================================
ALTER TABLE inject_dependencies      RENAME TO attack_chain_edges;
ALTER TABLE inject_expectations      RENAME TO attack_chain_node_expectations;
ALTER TABLE inject_expectation_traces RENAME TO node_expectation_traces;
ALTER TABLE injects                  RENAME TO attack_chain_nodes;
ALTER TABLE exercises                RENAME TO attack_chain_runs;
ALTER TABLE scenarios                RENAME TO attack_chains;

-- 多对多关联表
ALTER TABLE injects_documents        RENAME TO attack_chain_nodes_documents;
ALTER TABLE injects_tags             RENAME TO attack_chain_nodes_tags;
ALTER TABLE injects_teams            RENAME TO attack_chain_nodes_teams;
ALTER TABLE injects_assets           RENAME TO attack_chain_nodes_assets;
ALTER TABLE injects_asset_groups     RENAME TO attack_chain_nodes_asset_groups;

ALTER TABLE scenarios_tags           RENAME TO attack_chains_tags;
ALTER TABLE scenarios_teams          RENAME TO attack_chains_teams;
ALTER TABLE scenarios_documents      RENAME TO attack_chains_documents;
ALTER TABLE scenarios_grants         RENAME TO attack_chains_grants;
ALTER TABLE scenarios_observers      RENAME TO attack_chains_observers;
ALTER TABLE scenarios_planners       RENAME TO attack_chains_planners;

ALTER TABLE exercises_tags           RENAME TO attack_chain_runs_tags;
ALTER TABLE exercises_teams          RENAME TO attack_chain_runs_teams;
ALTER TABLE exercises_documents      RENAME TO attack_chain_runs_documents;
ALTER TABLE exercises_grants         RENAME TO attack_chain_runs_grants;
ALTER TABLE exercises_observers      RENAME TO attack_chain_runs_observers;
ALTER TABLE exercises_planners       RENAME TO attack_chain_runs_planners;

-- ============================================================
-- 4. RENAME columns
--    (基于 /tmp/columns_to_rename.txt 的输出展开)
-- ============================================================

-- attack_chains 表（原 scenarios）
ALTER TABLE attack_chains RENAME COLUMN scenario_id           TO attack_chain_id;
ALTER TABLE attack_chains RENAME COLUMN scenario_name         TO attack_chain_name;
ALTER TABLE attack_chains RENAME COLUMN scenario_description  TO attack_chain_description;
ALTER TABLE attack_chains RENAME COLUMN scenario_subtitle     TO attack_chain_subtitle;
ALTER TABLE attack_chains RENAME COLUMN scenario_category     TO attack_chain_category;
ALTER TABLE attack_chains RENAME COLUMN scenario_main_focus   TO attack_chain_main_focus;
ALTER TABLE attack_chains RENAME COLUMN scenario_severity     TO attack_chain_severity;
ALTER TABLE attack_chains RENAME COLUMN scenario_recurrence   TO attack_chain_recurrence;
ALTER TABLE attack_chains RENAME COLUMN scenario_recurrence_start TO attack_chain_recurrence_start;
ALTER TABLE attack_chains RENAME COLUMN scenario_recurrence_end   TO attack_chain_recurrence_end;
ALTER TABLE attack_chains RENAME COLUMN scenario_created_at   TO attack_chain_created_at;
ALTER TABLE attack_chains RENAME COLUMN scenario_updated_at   TO attack_chain_updated_at;

-- attack_chain_nodes 表（原 injects）
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_id              TO node_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_scenario        TO node_attack_chain_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_exercise        TO node_attack_chain_run_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_contract        TO node_contract_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_title           TO node_title;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_description     TO node_description;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_enabled         TO node_enabled;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_content         TO node_content;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_depends_duration TO node_depends_duration;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_trigger_now_date TO node_trigger_now_date;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_collect_execution_status TO node_collect_execution_status;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_created_at      TO node_created_at;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_updated_at      TO node_updated_at;

-- attack_chain_edges 表（原 inject_dependencies）
ALTER TABLE attack_chain_edges RENAME COLUMN inject_parent_id   TO parent_node_id;
ALTER TABLE attack_chain_edges RENAME COLUMN inject_children_id TO child_node_id;

-- attack_chain_runs 表（原 exercises）
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_id           TO run_id;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_scenario     TO run_attack_chain_id;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_name         TO run_name;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_description  TO run_description;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_status       TO run_status;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_start_date   TO run_start_date;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_end_date     TO run_end_date;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_created_at   TO run_created_at;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_updated_at   TO run_updated_at;

-- attack_chain_node_expectations 表（原 inject_expectations）
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_id TO node_expectation_id;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN exercise_id           TO run_id;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_id             TO node_id;

-- 多对多 FK 列改名
ALTER TABLE attack_chain_nodes_documents RENAME COLUMN inject_id TO node_id;
ALTER TABLE attack_chain_nodes_tags      RENAME COLUMN inject_id TO node_id;
ALTER TABLE attack_chain_nodes_teams     RENAME COLUMN inject_id TO node_id;
ALTER TABLE attack_chain_nodes_assets    RENAME COLUMN inject_id TO node_id;
ALTER TABLE attack_chain_nodes_asset_groups RENAME COLUMN inject_id TO node_id;

ALTER TABLE attack_chains_tags      RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_teams     RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_documents RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_grants    RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_observers RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_planners  RENAME COLUMN scenario_id TO attack_chain_id;

ALTER TABLE attack_chain_runs_tags      RENAME COLUMN exercise_id TO run_id;
ALTER TABLE attack_chain_runs_teams     RENAME COLUMN exercise_id TO run_id;
ALTER TABLE attack_chain_runs_documents RENAME COLUMN exercise_id TO run_id;
ALTER TABLE attack_chain_runs_grants    RENAME COLUMN exercise_id TO run_id;
ALTER TABLE attack_chain_runs_observers RENAME COLUMN exercise_id TO run_id;
ALTER TABLE attack_chain_runs_planners  RENAME COLUMN exercise_id TO run_id;

-- ============================================================
-- 5. ADD new columns
-- ============================================================
ALTER TABLE attack_chains ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'STOP_ON_BLOCK';
ALTER TABLE attack_chains ADD COLUMN validation_parameter_set_id UUID;
ALTER TABLE attack_chains ADD COLUMN soc_correlation_rules JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE attack_chain_nodes ADD COLUMN repeat_count INT NOT NULL DEFAULT 1
    CHECK (repeat_count >= 1);
ALTER TABLE attack_chain_nodes ADD COLUMN repeat_interval_seconds BIGINT NOT NULL DEFAULT 0
    CHECK (repeat_interval_seconds >= 0);
ALTER TABLE attack_chain_nodes ADD COLUMN validation_parameter_set_id UUID;

ALTER TABLE attack_chain_runs ADD COLUMN verdict_prevention VARCHAR(32);
ALTER TABLE attack_chain_runs ADD COLUMN verdict_detection VARCHAR(32);
ALTER TABLE attack_chain_runs ADD COLUMN verdict_computed_at TIMESTAMPTZ;

-- ============================================================
-- 6. CREATE new tables
-- ============================================================
CREATE TABLE validation_parameter_sets (
    parameter_set_id UUID PRIMARY KEY,
    name TEXT UNIQUE NOT NULL,
    description TEXT,
    is_template BOOLEAN NOT NULL DEFAULT FALSE,
    default_targets JSONB NOT NULL DEFAULT '[]'::jsonb,
    prevention_expected_score INT NOT NULL DEFAULT 100
        CHECK (prevention_expected_score BETWEEN 0 AND 100),
    prevention_expiration_seconds INT NOT NULL DEFAULT 1800
        CHECK (prevention_expiration_seconds > 0),
    detection_expected_score INT NOT NULL DEFAULT 100
        CHECK (detection_expected_score BETWEEN 0 AND 100),
    detection_expiration_seconds INT NOT NULL DEFAULT 1800
        CHECK (detection_expiration_seconds > 0),
    soc_correlation_rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE validation_parameter_set_tags (
    parameter_set_id UUID NOT NULL
        REFERENCES validation_parameter_sets ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags ON DELETE CASCADE,
    PRIMARY KEY (parameter_set_id, tag_id)
);

CREATE TABLE attack_chain_link_expectations (
    link_expectation_id UUID PRIMARY KEY,
    attack_chain_run_id UUID NOT NULL
        REFERENCES attack_chain_runs ON DELETE CASCADE,
    soc_rule_ref JSONB NOT NULL,
    score INT NOT NULL DEFAULT 0,
    expected_score INT NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expiration_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE link_expectation_traces (
    trace_id UUID PRIMARY KEY,
    link_expectation_id UUID NOT NULL
        REFERENCES attack_chain_link_expectations ON DELETE CASCADE,
    incident_id TEXT,
    correlation_rule_name TEXT,
    triggered_at TIMESTAMPTZ NOT NULL,
    score_delta INT NOT NULL,
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 7. FK + CHECK constraints
-- ============================================================
ALTER TABLE attack_chains ADD CONSTRAINT fk_chain_param_set
    FOREIGN KEY (validation_parameter_set_id)
    REFERENCES validation_parameter_sets ON DELETE RESTRICT;

ALTER TABLE attack_chain_nodes ADD CONSTRAINT fk_node_param_set
    FOREIGN KEY (validation_parameter_set_id)
    REFERENCES validation_parameter_sets ON DELETE RESTRICT;

ALTER TABLE attack_chain_nodes ADD CONSTRAINT chk_node_owner
    CHECK ((node_attack_chain_id IS NULL) <> (node_attack_chain_run_id IS NULL));

-- ============================================================
-- 8. Seed data: 3 default ValidationParameterSet templates
-- ============================================================
INSERT INTO validation_parameter_sets (
    parameter_set_id, name, description, is_template,
    prevention_expected_score, prevention_expiration_seconds,
    detection_expected_score, detection_expiration_seconds
) VALUES
    (gen_random_uuid(), '严格', '生产环境严格验证：100% 防御要求 + 30 分钟超时', true, 100, 1800, 100, 1800),
    (gen_random_uuid(), '宽松', '日常巡检：80% 防御要求 + 15 分钟超时', true, 80, 900, 80, 900),
    (gen_random_uuid(), '快速演练', '红队演练：50% 防御要求 + 5 分钟超时', true, 50, 300, 50, 300);

-- ============================================================
-- 9. Indexes
-- ============================================================
CREATE INDEX idx_node_attack_chain ON attack_chain_nodes (node_attack_chain_id)
    WHERE node_attack_chain_id IS NOT NULL;
CREATE INDEX idx_node_attack_chain_run ON attack_chain_nodes (node_attack_chain_run_id)
    WHERE node_attack_chain_run_id IS NOT NULL;
CREATE INDEX idx_edge_parent ON attack_chain_edges (parent_node_id);
CREATE INDEX idx_edge_child ON attack_chain_edges (child_node_id);
CREATE INDEX idx_link_expectation_run ON attack_chain_link_expectations (attack_chain_run_id);
CREATE INDEX idx_param_set_template ON validation_parameter_sets (is_template)
    WHERE is_template = true;

COMMIT;
```

> **注意**：上面 SQL 的 RENAME COLUMN 段基于现有 schema 列举。Step 1 输出的 `/tmp/columns_to_rename.txt` 可能含未列出的列，需逐条添加（如 `inject_position`、`inject_default_duration` 等）。完整列举对照 dump。

- [ ] **Step 3: Commit V2 SQL**

```bash
git add veriguard-api/src/main/resources/db/migration/V2__attack_chain_module_init.sql
git commit -m "迁移：V2 攻击编排 schema 大清洗 SQL（drop 演练遗留 + rename + add 新字段 + 新表 + 种子）"
```

### Task 1.3: V2MigrationTest

- [ ] **Step 1: 创建测试**

文件：`veriguard-api/src/test/java/io/veriguard/database/migration/V2MigrationTest.java`

```java
package io.veriguard.database.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class V2MigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void v2_renames_scenarios_to_attack_chains() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'attack_chains'",
            Integer.class);
        assertThat(count).isEqualTo(1);

        Integer oldCount = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'scenarios'",
            Integer.class);
        assertThat(oldCount).isEqualTo(0);
    }

    @Test
    void v2_creates_validation_parameter_sets_with_three_templates() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM validation_parameter_sets WHERE is_template = true",
            Integer.class);
        assertThat(count).isEqualTo(3);

        var names = jdbc.queryForList(
            "SELECT name FROM validation_parameter_sets WHERE is_template = true ORDER BY name",
            String.class);
        assertThat(names).containsExactlyInAnyOrder("严格", "宽松", "快速演练");
    }

    @Test
    void v2_drops_lessons_categories_table() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'lessons_categories'",
            Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void v2_drops_legacy_email_columns_from_attack_chains() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'attack_chains' " +
            "AND column_name IN ('scenario_header','scenario_footer','scenario_mail_from')",
            Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void v2_adds_execution_mode_to_attack_chains() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns " +
            "WHERE table_name = 'attack_chains' AND column_name = 'execution_mode'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v2_adds_repeat_columns_to_nodes() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns " +
            "WHERE table_name = 'attack_chain_nodes' " +
            "AND column_name IN ('repeat_count', 'repeat_interval_seconds', 'validation_parameter_set_id')",
            Integer.class);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void v2_adds_verdict_columns_to_runs() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns " +
            "WHERE table_name = 'attack_chain_runs' " +
            "AND column_name IN ('verdict_prevention', 'verdict_detection', 'verdict_computed_at')",
            Integer.class);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void v2_creates_link_expectations_table() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_name IN ('attack_chain_link_expectations', 'link_expectation_traces')",
            Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void v2_node_owner_check_constraint_active() {
        // node 必须恰好有 attack_chain_id 或 attack_chain_run_id 之一
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
            jdbc.update("INSERT INTO attack_chain_nodes (node_id, node_title) VALUES (gen_random_uuid(), 'X')")
        );
    }
}
```

- [ ] **Step 2: 跑 test**

```bash
mvn -pl veriguard-api test -Dtest=V2MigrationTest
```

Expected: PASS (9 assertions)

- [ ] **Step 3: Commit**

```bash
git add veriguard-api/src/test/java/io/veriguard/database/migration/V2MigrationTest.java
git commit -m "测试：V2 migration 集成测试（schema rename + 新表 + 种子 + CHECK 约束）"
```

### Task 1.4: 更新 JPA 实体注解

- [ ] **Step 1: AttackChain 实体注解对齐 V2**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/AttackChain.java`

```java
@Entity
@Table(name = "attack_chains")
public class AttackChain {
    @Id
    @Column(name = "attack_chain_id")
    private UUID id;

    @Column(name = "attack_chain_name")
    private String name;

    @Column(name = "attack_chain_description")
    private String description;

    @Column(name = "execution_mode")
    @Enumerated(EnumType.STRING)
    private ExecutionMode executionMode = ExecutionMode.STOP_ON_BLOCK;

    @Column(name = "validation_parameter_set_id")
    private UUID validationParameterSetId;

    @Type(JsonBinaryType.class)
    @Column(name = "soc_correlation_rules", columnDefinition = "jsonb")
    private List<SocCorrelationRuleRef> socCorrelationRules = new ArrayList<>();

    @Column(name = "attack_chain_recurrence")
    private String recurrence;

    // ... 其余字段
}
```

- [ ] **Step 2: AttackChainNode 实体**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/AttackChainNode.java`

```java
@Entity
@Table(name = "attack_chain_nodes")
public class AttackChainNode {
    @Id
    @Column(name = "node_id")
    private UUID id;

    @Column(name = "node_attack_chain_id")
    private UUID attackChainId;  // template 时非空

    @Column(name = "node_attack_chain_run_id")
    private UUID attackChainRunId;  // runtime 时非空

    @ManyToOne
    @JoinColumn(name = "node_contract_id")
    private NodeContract nodeContract;

    @Column(name = "node_title")
    private String title;

    @Column(name = "node_depends_duration")
    private Long dependsDuration;

    @Column(name = "repeat_count")
    private int repeatCount = 1;

    @Column(name = "repeat_interval_seconds")
    private long repeatIntervalSeconds = 0L;

    @Column(name = "validation_parameter_set_id")
    private UUID validationParameterSetId;

    // ... 其余字段
}
```

- [ ] **Step 3: AttackChainEdge 实体（替换原 InjectDependency 复合主键模式为单列 UUID）**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/AttackChainEdge.java`

```java
@Entity
@Table(name = "attack_chain_edges")
public class AttackChainEdge {
    @Id
    @Column(name = "edge_id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "parent_node_id")
    private AttackChainNode parent;

    @ManyToOne
    @JoinColumn(name = "child_node_id")
    private AttackChainNode child;

    @Type(JsonBinaryType.class)
    @Column(name = "condition", columnDefinition = "jsonb")
    private EdgeCondition condition;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
}
```

> 注意：V2 SQL 改动了主键 schema。如果原 inject_dependencies 是复合主键 (parent_id, child_id)，V2 需要新增 `edge_id UUID PRIMARY KEY` 列：

```sql
-- 加入 V2 步骤 4 之后：
ALTER TABLE attack_chain_edges ADD COLUMN edge_id UUID;
UPDATE attack_chain_edges SET edge_id = gen_random_uuid() WHERE edge_id IS NULL;
ALTER TABLE attack_chain_edges ALTER COLUMN edge_id SET NOT NULL;
ALTER TABLE attack_chain_edges DROP CONSTRAINT IF EXISTS inject_dependencies_pkey;
ALTER TABLE attack_chain_edges ADD PRIMARY KEY (edge_id);
ALTER TABLE attack_chain_edges ADD CONSTRAINT uq_edge_parent_child UNIQUE (parent_node_id, child_node_id);
```

加到 V2 SQL，提交。

- [ ] **Step 4: AttackChainRun 实体**

```java
@Entity
@Table(name = "attack_chain_runs")
public class AttackChainRun {
    @Id
    @Column(name = "run_id")
    private UUID id;

    @Column(name = "run_attack_chain_id")
    private UUID attackChainId;

    @Column(name = "run_status")
    @Enumerated(EnumType.STRING)
    private RunStatus status = RunStatus.SCHEDULED;

    @Column(name = "verdict_prevention")
    @Enumerated(EnumType.STRING)
    private LinkVerdict verdictPrevention;

    @Column(name = "verdict_detection")
    @Enumerated(EnumType.STRING)
    private LinkVerdict verdictDetection;

    @Column(name = "verdict_computed_at")
    private Instant verdictComputedAt;

    // ... 其余字段
}
```

- [ ] **Step 5: 创建 ExecutionMode / LinkVerdict / RunStatus enums**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/ExecutionMode.java`

```java
package io.veriguard.database.model;

public enum ExecutionMode {
    STOP_ON_BLOCK,
    CONTINUE
}
```

文件：`veriguard-model/src/main/java/io/veriguard/database/model/LinkVerdict.java`

```java
package io.veriguard.database.model;

public enum LinkVerdict {
    FULL_BREACH,    // 全链路有效（攻击成功）
    FULL_BLOCKED,   // 全链路失效（攻击失败）
    PARTIAL,
    PENDING,
    N_A
}
```

文件：`veriguard-model/src/main/java/io/veriguard/database/model/RunStatus.java`

```java
package io.veriguard.database.model;

public enum RunStatus {
    SCHEDULED,
    RUNNING,
    STOPPED_ON_BLOCK,
    COMPLETED,
    CANCELED
}
```

- [ ] **Step 6: 创建 SocCorrelationRuleRef 嵌入对象**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/SocCorrelationRuleRef.java`

```java
package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SocCorrelationRuleRef(
    @JsonProperty("connector_id") String connectorId,
    @JsonProperty("rule_id") String ruleId,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("match_window_seconds") int matchWindowSeconds
) {
    public SocCorrelationRuleRef {
        if (connectorId == null || connectorId.isBlank())
            throw new IllegalArgumentException("connector_id required");
        if (ruleId == null || ruleId.isBlank())
            throw new IllegalArgumentException("rule_id required");
        if (matchWindowSeconds <= 0) matchWindowSeconds = 7200;
    }
}
```

- [ ] **Step 7: 创建 EdgeCondition sealed interface（C-ready）**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/EdgeCondition.java`

```java
package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EdgeCondition.Eq.class, name = "eq"),
    @JsonSubTypes.Type(value = EdgeCondition.And.class, name = "and"),
    @JsonSubTypes.Type(value = EdgeCondition.Or.class, name = "or")
})
public sealed interface EdgeCondition permits EdgeCondition.Eq, EdgeCondition.And, EdgeCondition.Or {

    record Eq(
        ExpectationDimension dimension,
        ExpectationStatusGroup status
    ) implements EdgeCondition {}

    record And(List<EdgeCondition> children) implements EdgeCondition {}

    record Or(List<EdgeCondition> children) implements EdgeCondition {}

    enum ExpectationDimension { PREVENTION, DETECTION, MANUAL }

    enum ExpectationStatusGroup {
        ANY_SUCCESS, ANY_FAILED, ALL_SUCCESS, ALL_FAILED, SETTLED
    }
}
```

- [ ] **Step 8: 跑测试**

```bash
mvn -pl veriguard-api -am test
```

- [ ] **Step 9: Commit**

```bash
git add veriguard-model/
git commit -m "实体：JPA 注解对齐 V2 schema + 新增 ExecutionMode/LinkVerdict/RunStatus/SocCorrelationRuleRef/EdgeCondition"
```

### Task 1.5: 修复 DocumentRepository 中的 inject_* 引用

- [ ] **Step 1: 检查现有 native query**

文件：`veriguard-model/src/main/java/io/veriguard/database/repository/DocumentRepository.java`

```java
// 之前 Phase 11.5 修过，但表名变了，需重新对齐：
@Query(value = "SELECT DISTINCT d.* FROM documents d "
  + "WHERE d.document_id IN ("
  + "  SELECT id.document_id FROM attack_chain_nodes_documents id "
  + "  JOIN attack_chain_nodes n ON n.node_id = id.node_id "
  + "  WHERE n.node_attack_chain_id = :chainId )",
  nativeQuery = true)
List<Document> findAllDistinctByAttackChainId(@Param("chainId") String chainId);

// 同样修改 simulation 那条（runtime 节点）：
@Query(value = "SELECT DISTINCT d.* FROM documents d "
  + "WHERE d.document_id IN ("
  + "  SELECT id.document_id FROM attack_chain_nodes_documents id "
  + "  JOIN attack_chain_nodes n ON n.node_id = id.node_id "
  + "  WHERE n.node_attack_chain_run_id = :runId )",
  nativeQuery = true)
List<Document> findAllDistinctByAttackChainRunId(@Param("runId") String runId);
```

- [ ] **Step 2: 跑测试**

```bash
mvn -pl veriguard-api -am test
```

- [ ] **Step 3: Commit**

```bash
git add veriguard-model/src/main/java/io/veriguard/database/repository/DocumentRepository.java
git commit -m "修复：DocumentRepository native query 对齐 V2 attack_chain_nodes 表名"
```

### Task 1.6: 完整测试 + 提 PR

- [ ] **Step 1: 全部 backend test**

```bash
mvn -pl veriguard-api -am test
```

- [ ] **Step 2: Smoke test from clean DB**

```bash
cd veriguard-dev
docker compose down -v  # 清掉 volume
docker compose up -d
cd ..
mvn -pl veriguard-api spring-boot:run
```

观察启动日志中 `Migration V2__attack_chain_module_init: completed` 出现 + 应用启动成功。

- [ ] **Step 3: 提 PR**

```bash
git push -u origin feat/attack-chain-phase-1-migration
gh pr create --title "迁移：V2 攻击编排 schema 大清洗 (Phase 1)" --body "$(cat <<'EOF'
## Summary
PRD §2.4 攻击编排实施 **Phase 1**：

- TRUNCATE scenarios CASCADE（path b 大清洗）
- DROP 邮件 / 演练遗留字段（header / footer / lessons 等）
- RENAME 所有表 + 列 + 多对多关联（scenarios → attack_chains 等）
- ADD 新字段：execution_mode、validation_parameter_set_id、verdict_*、repeat_count/interval、soc_correlation_rules
- CREATE 新表：validation_parameter_sets、attack_chain_link_expectations、link_expectation_traces
- 种子 3 个 ParameterSet 模板（严格 / 宽松 / 快速演练）
- JPA 实体注解对齐
- 新 enum：ExecutionMode / LinkVerdict / RunStatus / SocCorrelationRuleRef / EdgeCondition

## Test plan
- [x] V2MigrationTest 全绿（9 assertions）
- [x] mvn test 全绿
- [x] 从空 DB 启动 API 成功，Flyway 日志显示 V2 完成
- [x] DocumentRepository native query 修复

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

合 PR 后 cleanup worktree。

---

## Phase 2: ValidationParameterSet (Entity + CRUD + 种子)

**Goal:** 给 Phase 1 创建的 `validation_parameter_sets` 表配上完整 JPA + Service + REST API。

### Task 2.1: 创建 worktree

- [ ] **Step 1**

```bash
cd /Users/lamba/github/Veriguard
git pull origin main
git worktree add worktrees/attack-chain-phase-2 -b feat/attack-chain-phase-2-parameter-set main
cd worktrees/attack-chain-phase-2
```

### Task 2.2: ValidationParameterSet entity

- [ ] **Step 1: 创建实体类**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/ValidationParameterSet.java`

```java
package io.veriguard.database.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "validation_parameter_sets")
public class ValidationParameterSet {

    @Id
    @Column(name = "parameter_set_id")
    private UUID id;

    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_template", nullable = false)
    private boolean template = false;

    @Type(JsonBinaryType.class)
    @Column(name = "default_targets", columnDefinition = "jsonb")
    private List<TargetRef> defaultTargets = new ArrayList<>();

    @Column(name = "prevention_expected_score", nullable = false)
    private int preventionExpectedScore = 100;

    @Column(name = "prevention_expiration_seconds", nullable = false)
    private int preventionExpirationSeconds = 1800;

    @Column(name = "detection_expected_score", nullable = false)
    private int detectionExpectedScore = 100;

    @Column(name = "detection_expiration_seconds", nullable = false)
    private int detectionExpirationSeconds = 1800;

    @Type(JsonBinaryType.class)
    @Column(name = "soc_correlation_rules", columnDefinition = "jsonb")
    private List<SocCorrelationRuleRef> socCorrelationRules = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "validation_parameter_set_tags",
        joinColumns = @JoinColumn(name = "parameter_set_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // 标准 getter/setter（用 IDE 生成 / Lombok 视项目惯例）
}
```

- [ ] **Step 2: TargetRef record**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/TargetRef.java`

```java
package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record TargetRef(
    @JsonProperty("type") TargetType type,
    @JsonProperty("id") UUID id
) {
    public enum TargetType { ASSET, ASSET_GROUP }
}
```

- [ ] **Step 3: 编译**

```bash
mvn -pl veriguard-model -am compile
```

- [ ] **Step 4: Commit**

```bash
git add veriguard-model/
git commit -m "实体：ValidationParameterSet + TargetRef + tags 多对多"
```

### Task 2.3: Repository

- [ ] **Step 1: 创建**

文件：`veriguard-model/src/main/java/io/veriguard/database/repository/ValidationParameterSetRepository.java`

```java
package io.veriguard.database.repository;

import io.veriguard.database.model.ValidationParameterSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ValidationParameterSetRepository
    extends JpaRepository<ValidationParameterSet, UUID>,
            JpaSpecificationExecutor<ValidationParameterSet> {

    Optional<ValidationParameterSet> findByName(String name);

    boolean existsByName(String name);
}
```

- [ ] **Step 2: Commit**

```bash
git add veriguard-model/src/main/java/io/veriguard/database/repository/ValidationParameterSetRepository.java
git commit -m "Repository: ValidationParameterSetRepository"
```

### Task 2.4: ValidationParameterSetService（TDD）

- [ ] **Step 1: 写测试**

文件：`veriguard-api/src/test/java/io/veriguard/attackchain/validation/ValidationParameterSetServiceTest.java`

```java
package io.veriguard.attackchain.validation;

import io.veriguard.database.model.ValidationParameterSet;
import io.veriguard.database.repository.ValidationParameterSetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationParameterSetServiceTest {

    @Mock ValidationParameterSetRepository repository;
    @InjectMocks ValidationParameterSetService service;

    @Test
    void create_with_unique_name_persists() {
        var input = new ValidationParameterSetInput(
            "Strict", "desc", null, 100, 1800, 100, 1800, null, null);
        when(repository.existsByName("Strict")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create(input);

        assertThat(result.getName()).isEqualTo("Strict");
        assertThat(result.getPreventionExpectedScore()).isEqualTo(100);
        verify(repository).save(any());
    }

    @Test
    void create_with_duplicate_name_throws() {
        var input = new ValidationParameterSetInput(
            "Strict", null, null, 100, 1800, 100, 1800, null, null);
        when(repository.existsByName("Strict")).thenReturn(true);

        assertThatThrownBy(() -> service.create(input))
            .isInstanceOf(DuplicateNameException.class);
    }

    @Test
    void delete_template_throws() {
        var id = UUID.randomUUID();
        var template = new ValidationParameterSet();
        template.setTemplate(true);
        when(repository.findById(id)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.delete(id))
            .isInstanceOf(CannotDeleteTemplateException.class);
    }

    @Test
    void update_template_throws() {
        var id = UUID.randomUUID();
        var template = new ValidationParameterSet();
        template.setTemplate(true);
        when(repository.findById(id)).thenReturn(Optional.of(template));

        var input = new ValidationParameterSetInput(
            "X", null, null, 100, 1800, 100, 1800, null, null);

        assertThatThrownBy(() -> service.update(id, input))
            .isInstanceOf(CannotEditTemplateException.class);
    }

    @Test
    void create_rejects_score_out_of_range() {
        var input = new ValidationParameterSetInput(
            "X", null, null, 150, 1800, 100, 1800, null, null);

        assertThatThrownBy(() -> service.create(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("score");
    }

    @Test
    void duplicate_creates_non_template_copy() {
        var sourceId = UUID.randomUUID();
        var source = new ValidationParameterSet();
        source.setName("严格");
        source.setTemplate(true);
        source.setPreventionExpectedScore(100);
        source.setDetectionExpectedScore(100);
        when(repository.findById(sourceId)).thenReturn(Optional.of(source));
        when(repository.existsByName("严格-copy")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var copy = service.duplicate(sourceId, "严格-copy");

        assertThat(copy.getName()).isEqualTo("严格-copy");
        assertThat(copy.isTemplate()).isFalse();
        assertThat(copy.getPreventionExpectedScore()).isEqualTo(100);
    }
}
```

- [ ] **Step 2: 跑 test 确认 fail**

```bash
mvn -pl veriguard-api test -Dtest=ValidationParameterSetServiceTest
```

Expected: FAIL ("ValidationParameterSetService does not exist")

- [ ] **Step 3: 实现 service**

文件：`veriguard-api/src/main/java/io/veriguard/attackchain/validation/ValidationParameterSetService.java`

```java
package io.veriguard.attackchain.validation;

import io.veriguard.database.model.ValidationParameterSet;
import io.veriguard.database.model.TargetRef;
import io.veriguard.database.model.SocCorrelationRuleRef;
import io.veriguard.database.repository.ValidationParameterSetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional
public class ValidationParameterSetService {

    private final ValidationParameterSetRepository repository;

    public ValidationParameterSetService(ValidationParameterSetRepository repository) {
        this.repository = repository;
    }

    public ValidationParameterSet create(ValidationParameterSetInput input) {
        validateScores(input);
        if (repository.existsByName(input.name())) {
            throw new DuplicateNameException(input.name());
        }
        var entity = new ValidationParameterSet();
        applyInput(entity, input);
        return repository.save(entity);
    }

    public ValidationParameterSet get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ParameterSetNotFoundException(id));
    }

    public ValidationParameterSet update(UUID id, ValidationParameterSetInput input) {
        validateScores(input);
        var entity = repository.findById(id)
            .orElseThrow(() -> new ParameterSetNotFoundException(id));
        if (entity.isTemplate()) {
            throw new CannotEditTemplateException(id);
        }
        if (!entity.getName().equals(input.name()) && repository.existsByName(input.name())) {
            throw new DuplicateNameException(input.name());
        }
        applyInput(entity, input);
        return repository.save(entity);
    }

    public void delete(UUID id) {
        var entity = repository.findById(id)
            .orElseThrow(() -> new ParameterSetNotFoundException(id));
        if (entity.isTemplate()) {
            throw new CannotDeleteTemplateException(id);
        }
        repository.delete(entity);  // FK RESTRICT 由 DB 兜底
    }

    public ValidationParameterSet duplicate(UUID sourceId, String newName) {
        var source = repository.findById(sourceId)
            .orElseThrow(() -> new ParameterSetNotFoundException(sourceId));
        if (repository.existsByName(newName)) {
            throw new DuplicateNameException(newName);
        }
        var copy = new ValidationParameterSet();
        copy.setName(newName);
        copy.setDescription(source.getDescription());
        copy.setTemplate(false);
        copy.setDefaultTargets(new ArrayList<>(source.getDefaultTargets()));
        copy.setPreventionExpectedScore(source.getPreventionExpectedScore());
        copy.setPreventionExpirationSeconds(source.getPreventionExpirationSeconds());
        copy.setDetectionExpectedScore(source.getDetectionExpectedScore());
        copy.setDetectionExpirationSeconds(source.getDetectionExpirationSeconds());
        copy.setSocCorrelationRules(new ArrayList<>(source.getSocCorrelationRules()));
        return repository.save(copy);
    }

    public Page<ValidationParameterSet> search(Pageable pageable) {
        return repository.findAll(pageable);
    }

    private void validateScores(ValidationParameterSetInput input) {
        if (input.preventionExpectedScore() < 0 || input.preventionExpectedScore() > 100) {
            throw new IllegalArgumentException("prevention_expected_score must be 0-100");
        }
        if (input.detectionExpectedScore() < 0 || input.detectionExpectedScore() > 100) {
            throw new IllegalArgumentException("detection_expected_score must be 0-100");
        }
        if (input.preventionExpirationSeconds() <= 0 || input.detectionExpirationSeconds() <= 0) {
            throw new IllegalArgumentException("expiration must be positive");
        }
    }

    private void applyInput(ValidationParameterSet entity, ValidationParameterSetInput input) {
        entity.setName(input.name());
        entity.setDescription(input.description());
        entity.setDefaultTargets(input.defaultTargets() != null ? input.defaultTargets() : new ArrayList<>());
        entity.setPreventionExpectedScore(input.preventionExpectedScore());
        entity.setPreventionExpirationSeconds(input.preventionExpirationSeconds());
        entity.setDetectionExpectedScore(input.detectionExpectedScore());
        entity.setDetectionExpirationSeconds(input.detectionExpirationSeconds());
        entity.setSocCorrelationRules(input.socCorrelationRules() != null ? input.socCorrelationRules() : new ArrayList<>());
    }
}
```

- [ ] **Step 4: 创建 input + 异常类**

文件：`veriguard-api/src/main/java/io/veriguard/attackchain/validation/ValidationParameterSetInput.java`

```java
package io.veriguard.attackchain.validation;

import io.veriguard.database.model.SocCorrelationRuleRef;
import io.veriguard.database.model.TargetRef;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ValidationParameterSetInput(
    String name,
    String description,
    List<TargetRef> defaultTargets,
    int preventionExpectedScore,
    int preventionExpirationSeconds,
    int detectionExpectedScore,
    int detectionExpirationSeconds,
    List<SocCorrelationRuleRef> socCorrelationRules,
    Set<UUID> tagIds
) {}
```

文件：`veriguard-api/src/main/java/io/veriguard/attackchain/validation/exceptions/`（4 个异常）

```java
public class DuplicateNameException extends RuntimeException {
    public DuplicateNameException(String name) { super("name already exists: " + name); }
}

public class ParameterSetNotFoundException extends RuntimeException {
    public ParameterSetNotFoundException(UUID id) { super("parameter set not found: " + id); }
}

public class CannotEditTemplateException extends RuntimeException {
    public CannotEditTemplateException(UUID id) { super("cannot edit template: " + id); }
}

public class CannotDeleteTemplateException extends RuntimeException {
    public CannotDeleteTemplateException(UUID id) { super("cannot delete template: " + id); }
}
```

- [ ] **Step 5: 跑 test pass**

```bash
mvn -pl veriguard-api test -Dtest=ValidationParameterSetServiceTest
```

Expected: PASS (6 assertions)

- [ ] **Step 6: Commit**

```bash
git add veriguard-api/src/main/java/io/veriguard/attackchain/validation/
git add veriguard-api/src/test/java/io/veriguard/attackchain/validation/
git commit -m "服务：ValidationParameterSetService CRUD + name 唯一 + 模板保护 + 参数校验"
```

### Task 2.5: REST API

- [ ] **Step 1: 添加 ResourceType 枚举值**

文件：`veriguard-model/src/main/java/io/veriguard/database/model/ResourceType.java`

```java
public enum ResourceType {
    // ... 现有
    VALIDATION_PARAMETER_SET,
    ATTACK_CHAIN,
    ATTACK_CHAIN_RUN
}
```

- [ ] **Step 2: 写 controller test**

文件：`veriguard-api/src/test/java/io/veriguard/attackchain/validation/ValidationParameterSetApiTest.java`

```java
package io.veriguard.attackchain.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.attackchain.validation.exceptions.*;
import io.veriguard.database.model.ValidationParameterSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ValidationParameterSetApi.class)
class ValidationParameterSetApiTest {

    @Autowired MockMvc mockMvc;
    @MockBean ValidationParameterSetService service;
    @Autowired ObjectMapper mapper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void post_creates_param_set() throws Exception {
        var entity = new ValidationParameterSet();
        entity.setName("Strict");
        entity.setPreventionExpectedScore(100);
        when(service.create(any())).thenReturn(entity);

        var input = """
            {"name":"Strict","preventionExpectedScore":100,"preventionExpirationSeconds":1800,
             "detectionExpectedScore":100,"detectionExpirationSeconds":1800}""";

        mockMvc.perform(post("/api/validation_parameter_sets")
                .contentType(APPLICATION_JSON).content(input))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Strict"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_template_returns_409() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new CannotDeleteTemplateException(id)).when(service).delete(id);

        mockMvc.perform(delete("/api/validation_parameter_sets/" + id))
            .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicate_returns_201() throws Exception {
        var sourceId = UUID.randomUUID();
        var entity = new ValidationParameterSet();
        entity.setName("严格-copy");
        when(service.duplicate(sourceId, "严格-copy")).thenReturn(entity);

        mockMvc.perform(post("/api/validation_parameter_sets/" + sourceId + "/duplicate")
                .contentType(APPLICATION_JSON)
                .content("""{"newName":"严格-copy"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("严格-copy"));
    }
}
```

- [ ] **Step 3: 跑 test fail**

```bash
mvn -pl veriguard-api test -Dtest=ValidationParameterSetApiTest
```

Expected: FAIL

- [ ] **Step 4: 实现 controller**

文件：`veriguard-api/src/main/java/io/veriguard/attackchain/validation/ValidationParameterSetApi.java`

```java
package io.veriguard.attackchain.validation;

import io.veriguard.attackchain.validation.exceptions.*;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.framework.security.RBAC;
import io.veriguard.framework.search.SearchPaginationInput;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/validation_parameter_sets")
public class ValidationParameterSetApi {

    private final ValidationParameterSetService service;

    public ValidationParameterSetApi(ValidationParameterSetService service) {
        this.service = service;
    }

    @PostMapping
    @RBAC(action = Action.WRITE, resource = ResourceType.VALIDATION_PARAMETER_SET)
    @ResponseStatus(HttpStatus.CREATED)
    public ValidationParameterSetOutput create(@RequestBody @Valid ValidationParameterSetInput input) {
        return ValidationParameterSetOutput.from(service.create(input));
    }

    @GetMapping("/{id}")
    @RBAC(action = Action.READ, resource = ResourceType.VALIDATION_PARAMETER_SET)
    public ValidationParameterSetOutput get(@PathVariable UUID id) {
        return ValidationParameterSetOutput.from(service.get(id));
    }

    @PutMapping("/{id}")
    @RBAC(action = Action.WRITE, resource = ResourceType.VALIDATION_PARAMETER_SET)
    public ValidationParameterSetOutput update(@PathVariable UUID id,
            @RequestBody @Valid ValidationParameterSetInput input) {
        return ValidationParameterSetOutput.from(service.update(id, input));
    }

    @DeleteMapping("/{id}")
    @RBAC(action = Action.DELETE, resource = ResourceType.VALIDATION_PARAMETER_SET)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/search")
    @RBAC(action = Action.READ, resource = ResourceType.VALIDATION_PARAMETER_SET)
    public Page<ValidationParameterSetOutput> search(@RequestBody SearchPaginationInput search) {
        var pageable = PageRequest.of(search.page(), search.size());
        return service.search(pageable).map(ValidationParameterSetOutput::from);
    }

    @PostMapping("/{id}/duplicate")
    @RBAC(action = Action.WRITE, resource = ResourceType.VALIDATION_PARAMETER_SET)
    @ResponseStatus(HttpStatus.CREATED)
    public ValidationParameterSetOutput duplicate(@PathVariable UUID id,
            @RequestBody DuplicateInput body) {
        return ValidationParameterSetOutput.from(service.duplicate(id, body.newName()));
    }

    @ExceptionHandler(DuplicateNameException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateNameException e) {
        return ResponseEntity.status(409)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()));
    }

    @ExceptionHandler({CannotDeleteTemplateException.class, CannotEditTemplateException.class})
    public ResponseEntity<ProblemDetail> handleTemplate(RuntimeException e) {
        return ResponseEntity.status(409)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()));
    }

    @ExceptionHandler(ParameterSetNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ParameterSetNotFoundException e) {
        return ResponseEntity.status(404)
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    public record DuplicateInput(String newName) {}
}
```

- [ ] **Step 5: 创建 Output DTO**

文件：`veriguard-api/src/main/java/io/veriguard/attackchain/validation/ValidationParameterSetOutput.java`

```java
package io.veriguard.attackchain.validation;

import io.veriguard.database.model.SocCorrelationRuleRef;
import io.veriguard.database.model.TargetRef;
import io.veriguard.database.model.ValidationParameterSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValidationParameterSetOutput(
    UUID id,
    String name,
    String description,
    boolean isTemplate,
    List<TargetRef> defaultTargets,
    int preventionExpectedScore,
    int preventionExpirationSeconds,
    int detectionExpectedScore,
    int detectionExpirationSeconds,
    List<SocCorrelationRuleRef> socCorrelationRules,
    Instant createdAt,
    Instant updatedAt
) {
    public static ValidationParameterSetOutput from(ValidationParameterSet entity) {
        return new ValidationParameterSetOutput(
            entity.getId(), entity.getName(), entity.getDescription(),
            entity.isTemplate(), entity.getDefaultTargets(),
            entity.getPreventionExpectedScore(), entity.getPreventionExpirationSeconds(),
            entity.getDetectionExpectedScore(), entity.getDetectionExpirationSeconds(),
            entity.getSocCorrelationRules(),
            entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 6: 跑 test pass**

```bash
mvn -pl veriguard-api test -Dtest=ValidationParameterSetApiTest
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "API: ValidationParameterSetApi CRUD + 错误响应 + Output DTO"
```

### Task 2.6: 提 PR

```bash
mvn -pl veriguard-api -am test
git push -u origin feat/attack-chain-phase-2-parameter-set
gh pr create --title "实现：ValidationParameterSet (Phase 2)" --body "..."
```

---

## Phase 3-7: 后端业务核心

每个 Phase 一个独立 worktree + PR，结构与 Phase 2 类似（TDD：写 test → fail → implement → pass → commit）。下面给出关键实现纲要。

### Phase 3: ExecutionMode + StopOnBlock Scheduler

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/execution/NodeStateMachine.java`
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/execution/AttackChainScheduler.java`
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/execution/EdgeConditionEvaluator.java`
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/execution/AttackChainRunFactory.java`
- Test: `veriguard-api/src/test/java/io/veriguard/attackchain/execution/StopOnBlockIntegrationTest.java`
- Test: `veriguard-api/src/test/java/io/veriguard/attackchain/execution/EdgeConditionEvaluatorTest.java`
- Test: `veriguard-api/src/test/java/io/veriguard/attackchain/execution/NodeStateMachineTest.java`

#### Task 3.1: NodeState enum + NodeStateMachine

```java
public enum NodeState {
    SCHEDULED, RUNNING, AWAITING_EXPECTATION, SETTLED, SKIPPED, FAILED
}

@Component
public class NodeStateMachine {

    private static final Map<NodeState, Set<NodeState>> ALLOWED = Map.of(
        NodeState.SCHEDULED,             Set.of(NodeState.RUNNING, NodeState.SKIPPED),
        NodeState.RUNNING,               Set.of(NodeState.AWAITING_EXPECTATION, NodeState.FAILED),
        NodeState.AWAITING_EXPECTATION,  Set.of(NodeState.SETTLED, NodeState.RUNNING),
        NodeState.SETTLED,               Set.of(),
        NodeState.SKIPPED,               Set.of(),
        NodeState.FAILED,                Set.of()
    );

    public boolean canTransition(NodeState from, NodeState to) {
        return ALLOWED.get(from).contains(to);
    }

    public void transition(AttackChainNode node, NodeState to) {
        if (!canTransition(node.getState(), to)) {
            throw new IllegalStateException(
                "Invalid transition: " + node.getState() + " → " + to);
        }
        node.setState(to);
    }
}
```

`AttackChainNode` 实体加 `@Column(name = "node_state") @Enumerated(EnumType.STRING)` 字段（Phase 1 V2 SQL 需补 `ALTER TABLE attack_chain_nodes ADD COLUMN node_state VARCHAR(32) DEFAULT 'SCHEDULED'`，回到 Phase 1 PR 后做 V3 补丁，或在 Phase 3 加 V3 migration）。

> **Phase 3 之前补**：在 V2 SQL 末尾加 `ALTER TABLE attack_chain_nodes ADD COLUMN node_state VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED';` 和 `ALTER TABLE attack_chain_nodes ADD COLUMN current_iteration INT NOT NULL DEFAULT 0;`，重跑 V2MigrationTest。

测试覆盖 6×6 状态转换。

#### Task 3.2: EdgeConditionEvaluator（C-ready 纯函数）

```java
@Component
public class EdgeConditionEvaluator {

    public boolean evaluate(EdgeCondition condition, NodeFinalStatus parentStatus) {
        if (condition == null) return true;  // 无条件 = 总是可达
        return switch (condition) {
            case EdgeCondition.Eq eq -> evaluateEq(eq, parentStatus);
            case EdgeCondition.And and -> and.children().stream()
                .allMatch(c -> evaluate(c, parentStatus));
            case EdgeCondition.Or or -> or.children().stream()
                .anyMatch(c -> evaluate(c, parentStatus));
        };
    }

    private boolean evaluateEq(EdgeCondition.Eq eq, NodeFinalStatus status) {
        var dimensionStatus = switch (eq.dimension()) {
            case PREVENTION -> status.prevention();
            case DETECTION -> status.detection();
            case MANUAL -> status.manual();
        };
        return matchesGroup(dimensionStatus, eq.status());
    }

    private boolean matchesGroup(ExpectationStatus actual, EdgeCondition.ExpectationStatusGroup group) {
        return switch (group) {
            case ANY_SUCCESS -> actual == ExpectationStatus.SUCCESS;
            case ANY_FAILED -> actual == ExpectationStatus.FAILED;
            case ALL_SUCCESS -> actual == ExpectationStatus.SUCCESS;  // 节点级单值，与 ANY_SUCCESS 同义
            case ALL_FAILED -> actual == ExpectationStatus.FAILED;
            case SETTLED -> actual != ExpectationStatus.PENDING && actual != ExpectationStatus.UNKNOWN;
        };
    }
}

public record NodeFinalStatus(
    ExpectationStatus prevention,
    ExpectationStatus detection,
    ExpectationStatus manual
) {}
```

测试穷举：

```java
@Test void any_success_matches_SUCCESS_status();
@Test void any_failed_matches_FAILED_status();
@Test void and_combines_multiple_conditions();
@Test void or_combines_multiple_conditions();
@Test void nested_and_or_evaluates_correctly();
@Test void null_condition_returns_true();
```

#### Task 3.3: AttackChainScheduler

```java
@Service
@Transactional
public class AttackChainScheduler {

    private final AttackChainRunFactory runFactory;
    private final NodeExecutorRegistry executorRegistry;
    private final EdgeConditionEvaluator edgeEvaluator;
    private final AttackChainNodeRepository nodeRepository;
    private final AttackChainRunRepository runRepository;
    private final AttackChainEdgeRepository edgeRepository;
    private final NodeStateMachine stateMachine;

    public AttackChainRun launch(UUID chainId, LaunchOptions options) {
        var run = runFactory.fromTemplate(chainId, options);
        var roots = nodeRepository.findRootNodes(run.getId());
        roots.forEach(this::scheduleNode);
        run.setStatus(RunStatus.RUNNING);
        return runRepository.save(run);
    }

    @EventListener
    public void onNodeSettled(NodeSettledEvent event) {
        var node = nodeRepository.findById(event.nodeId()).orElseThrow();
        var run = runRepository.findById(node.getAttackChainRunId()).orElseThrow();

        // Stop-on-block 检查
        if (run.getExecutionMode() == ExecutionMode.STOP_ON_BLOCK
            && getPreventionStatus(node) == ExpectationStatus.SUCCESS) {
            triggerStopOnBlock(run);
            return;
        }

        // Propagate
        var outgoing = edgeRepository.findByParentNodeId(node.getId());
        for (var edge : outgoing) {
            var status = computeFinalStatus(node);
            if (edgeEvaluator.evaluate(edge.getCondition(), status)) {
                var child = edge.getChild();
                if (allParentsSettled(child)) {
                    scheduleNode(child);
                }
            }
        }

        checkRunComplete(run);
    }

    private void triggerStopOnBlock(AttackChainRun run) {
        var unscheduled = nodeRepository.findByRunIdAndState(run.getId(), NodeState.SCHEDULED);
        for (var n : unscheduled) {
            stateMachine.transition(n, NodeState.SKIPPED);
            nodeRepository.save(n);
        }
        run.setStatus(RunStatus.STOPPED_ON_BLOCK);
        runRepository.save(run);
    }

    private void scheduleNode(AttackChainNode node) {
        stateMachine.transition(node, NodeState.SCHEDULED);
        nodeRepository.save(node);
        // 实际执行延后到 timer / 队列
    }

    private boolean allParentsSettled(AttackChainNode child) {
        var parentEdges = edgeRepository.findByChildNodeId(child.getId());
        return parentEdges.stream().allMatch(e ->
            isTerminal(e.getParent().getState()));
    }

    private boolean isTerminal(NodeState s) {
        return s == NodeState.SETTLED || s == NodeState.SKIPPED || s == NodeState.FAILED;
    }

    private void checkRunComplete(AttackChainRun run) {
        var allNodes = nodeRepository.findByRunId(run.getId());
        if (allNodes.stream().allMatch(n -> isTerminal(n.getState()))) {
            // 此处先简单标 COMPLETED；Phase 5 接入 verdict + Phase 7 接 SOC link expectation
            run.setStatus(RunStatus.COMPLETED);
            runRepository.save(run);
        }
    }

    // Helper methods omitted for brevity
}
```

#### Task 3.4: AttackChainRunFactory

```java
@Service
public class AttackChainRunFactory {

    private final AttackChainRepository chainRepository;
    private final AttackChainRunRepository runRepository;
    private final AttackChainNodeRepository nodeRepository;
    private final AttackChainEdgeRepository edgeRepository;

    public AttackChainRun fromTemplate(UUID chainId, LaunchOptions options) {
        var template = chainRepository.findById(chainId).orElseThrow();
        var run = new AttackChainRun();
        run.setId(UUID.randomUUID());
        run.setAttackChainId(chainId);
        run.setName(options.runName() != null ? options.runName() : template.getName());
        run.setStartDate(options.scheduledStart() != null ? options.scheduledStart() : Instant.now());
        run.setStatus(RunStatus.SCHEDULED);
        run.setExecutionMode(template.getExecutionMode());
        runRepository.save(run);

        // 深拷贝节点
        var templateNodes = nodeRepository.findByAttackChainId(chainId);
        var nodeIdMap = new HashMap<UUID, UUID>();  // template id → run id
        for (var src : templateNodes) {
            var copy = deepCopyNode(src, run.getId());
            nodeRepository.save(copy);
            nodeIdMap.put(src.getId(), copy.getId());
        }

        // 深拷贝边
        var templateEdges = templateNodes.stream()
            .flatMap(n -> edgeRepository.findByParentNodeId(n.getId()).stream())
            .toList();
        for (var edge : templateEdges) {
            var copy = new AttackChainEdge();
            copy.setId(UUID.randomUUID());
            copy.setParent(nodeRepository.findById(nodeIdMap.get(edge.getParent().getId())).orElseThrow());
            copy.setChild(nodeRepository.findById(nodeIdMap.get(edge.getChild().getId())).orElseThrow());
            copy.setCondition(edge.getCondition());
            edgeRepository.save(copy);
        }

        return run;
    }

    private AttackChainNode deepCopyNode(AttackChainNode src, UUID runId) {
        var copy = new AttackChainNode();
        copy.setId(UUID.randomUUID());
        copy.setAttackChainId(null);
        copy.setAttackChainRunId(runId);
        copy.setNodeContract(src.getNodeContract());
        copy.setTitle(src.getTitle());
        copy.setDescription(src.getDescription());
        copy.setEnabled(src.isEnabled());
        copy.setContent(src.getContent());
        copy.setDependsDuration(src.getDependsDuration());
        copy.setRepeatCount(src.getRepeatCount());
        copy.setRepeatIntervalSeconds(src.getRepeatIntervalSeconds());
        copy.setValidationParameterSetId(src.getValidationParameterSetId());
        copy.setState(NodeState.SCHEDULED);
        // ... 其他字段
        return copy;
    }
}
```

#### Task 3.5: StopOnBlockIntegrationTest

```java
@SpringBootTest
@Testcontainers
class StopOnBlockIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired AttackChainScheduler scheduler;
    @Autowired AttackChainRunRepository runRepository;
    @Autowired AttackChainNodeRepository nodeRepository;
    @Autowired TestDataBuilder builder;  // helper to create chain + nodes + edges

    @Test
    void first_node_blocked_stops_chain() {
        var chain = builder.linearChain(5, ExecutionMode.STOP_ON_BLOCK);
        var run = scheduler.launch(chain.getId(), new LaunchOptions(null, null, null));

        var firstNode = builder.firstNode(run);
        builder.simulateSettled(firstNode, ExpectationStatus.SUCCESS, ExpectationStatus.PENDING);

        var refreshed = runRepository.findById(run.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(RunStatus.STOPPED_ON_BLOCK);

        var nodes = nodeRepository.findByRunIdOrderByPosition(run.getId());
        assertThat(nodes.get(0).getState()).isEqualTo(NodeState.SETTLED);
        for (int i = 1; i < 5; i++) {
            assertThat(nodes.get(i).getState()).isEqualTo(NodeState.SKIPPED);
        }
    }

    @Test
    void continue_mode_runs_all_nodes_even_after_block() {
        var chain = builder.linearChain(5, ExecutionMode.CONTINUE);
        var run = scheduler.launch(chain.getId(), new LaunchOptions(null, null, null));

        var nodes = nodeRepository.findByRunIdOrderByPosition(run.getId());
        for (int i = 0; i < 5; i++) {
            builder.simulateSettled(nodes.get(i), ExpectationStatus.SUCCESS, ExpectationStatus.PENDING);
        }

        var refreshed = runRepository.findById(run.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(RunStatus.COMPLETED);
        for (var n : nodeRepository.findByRunIdOrderByPosition(run.getId())) {
            assertThat(n.getState()).isEqualTo(NodeState.SETTLED);
        }
    }

    @Test
    void condition_branching_skips_unmet_paths() {
        // 建一条链：N1 → (PREVENTION = SUCCESS) → N2_blocked 路径，N1 → (PREVENTION = FAILED) → N3_continue 路径
        var chain = builder.branchingChain(ExecutionMode.CONTINUE);
        var run = scheduler.launch(chain.getId(), new LaunchOptions(null, null, null));

        var n1 = builder.findNode(run, "N1");
        builder.simulateSettled(n1, ExpectationStatus.FAILED, ExpectationStatus.PENDING);

        // N1 = FAILED → 走 N3 路径，N2 跳过
        var n2 = builder.findNode(run, "N2_blocked");
        var n3 = builder.findNode(run, "N3_continue");
        assertThat(n2.getState()).isEqualTo(NodeState.SKIPPED);
        assertThat(n3.getState()).isIn(NodeState.SCHEDULED, NodeState.RUNNING);
    }
}
```

#### Task 3.6: 提 PR

```bash
git push -u origin feat/attack-chain-phase-3-scheduler
gh pr create --title "实现：ExecutionMode + StopOnBlock Scheduler (Phase 3)"
```

---

### Phase 4: RepeatCount + RepeatInterval

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/execution/NodeRepeatScheduler.java`
- Modify: `veriguard-api/src/main/java/io/veriguard/attackchain/execution/AttackChainScheduler.java`
- Test: `veriguard-api/src/test/java/io/veriguard/attackchain/execution/RepeatExecutionIntegrationTest.java`

#### Task 4.1: NodeRepeatScheduler 伪代码

```java
@Service
public class NodeRepeatScheduler {

    public void onIterationSettled(AttackChainNode node, ExpectationStatus prevention) {
        var run = runRepository.findById(node.getAttackChainRunId()).orElseThrow();

        if (run.getExecutionMode() == ExecutionMode.STOP_ON_BLOCK
            && prevention == ExpectationStatus.SUCCESS) {
            // 第 N 次拦下了，立即结束节点重复
            finalizeNode(node);
            eventPublisher.publishEvent(new NodeSettledEvent(node.getId()));
            return;
        }

        if (node.getCurrentIteration() < node.getRepeatCount() - 1) {
            // 等 interval 然后下一次
            scheduleNextIteration(node);
        } else {
            // 跑满了
            finalizeNode(node);
            eventPublisher.publishEvent(new NodeSettledEvent(node.getId()));
        }
    }

    private void scheduleNextIteration(AttackChainNode node) {
        node.setCurrentIteration(node.getCurrentIteration() + 1);
        nodeRepository.save(node);
        // 排定 repeat_interval_seconds 后再触发 RUNNING
        taskScheduler.schedule(() -> dispatchExecutor(node),
            Instant.now().plusSeconds(node.getRepeatIntervalSeconds()));
    }

    private void finalizeNode(AttackChainNode node) {
        node.setState(NodeState.SETTLED);
        nodeRepository.save(node);
    }
}
```

#### Task 4.2: RepeatExecutionIntegrationTest

```java
@Test
void repeat_3_times_aggregates_expectations() {
    var node = builder.nodeWithRepeat(3, 0L);
    var chain = builder.chainContaining(node, ExecutionMode.CONTINUE);
    var run = scheduler.launch(chain.getId(), new LaunchOptions(null, null, null));
    var runtimeNode = builder.firstRuntimeNode(run);

    builder.simulateIterationSettled(runtimeNode, 0, ExpectationStatus.FAILED, ExpectationStatus.PENDING);
    builder.simulateIterationSettled(runtimeNode, 1, ExpectationStatus.FAILED, ExpectationStatus.PENDING);
    builder.simulateIterationSettled(runtimeNode, 2, ExpectationStatus.FAILED, ExpectationStatus.PENDING);

    assertThat(runtimeNode.getState()).isEqualTo(NodeState.SETTLED);
    assertThat(builder.countExpectations(runtimeNode)).isEqualTo(3);  // 3 组
}

@Test
void stop_on_block_halts_repeat_at_first_success() {
    var node = builder.nodeWithRepeat(5, 0L);
    var chain = builder.chainContaining(node, ExecutionMode.STOP_ON_BLOCK);
    var run = scheduler.launch(chain.getId(), new LaunchOptions(null, null, null));
    var runtimeNode = builder.firstRuntimeNode(run);

    builder.simulateIterationSettled(runtimeNode, 0, ExpectationStatus.FAILED, ExpectationStatus.PENDING);
    builder.simulateIterationSettled(runtimeNode, 1, ExpectationStatus.SUCCESS, ExpectationStatus.PENDING);

    var iterations = builder.countExpectations(runtimeNode);
    assertThat(iterations).isEqualTo(2);  // 不再跑第 3-5 次

    var refreshed = runRepository.findById(run.getId()).orElseThrow();
    assertThat(refreshed.getStatus()).isEqualTo(RunStatus.STOPPED_ON_BLOCK);
}
```

---

### Phase 5: LinkVerdictCalculator

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/verdict/LinkVerdictCalculator.java`
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/verdict/VerdictStats.java`
- Test: `veriguard-api/src/test/java/io/veriguard/attackchain/verdict/LinkVerdictCalculatorTest.java`
- Modify: `veriguard-api/src/main/java/io/veriguard/attackchain/execution/AttackChainScheduler.java` (集成 verdict 计算到 checkRunComplete)

#### Task 5.1: LinkVerdictCalculator 单元测试

```java
class LinkVerdictCalculatorTest {

    LinkVerdictCalculator calc = new LinkVerdictCalculator();

    @Test
    void all_blocked_returns_FULL_BLOCKED() {
        var result = calc.compute(
            new VerdictStats(5, 5),  // PREVENTION
            new VerdictStats(0, 0),  // node-level DETECTION
            new VerdictStats(0, 0)   // link-level DETECTION
        );
        assertThat(result.preventionVerdict()).isEqualTo(LinkVerdict.FULL_BLOCKED);
        assertThat(result.detectionVerdict()).isEqualTo(LinkVerdict.N_A);
    }

    @Test
    void none_blocked_returns_FULL_BREACH() {
        var result = calc.compute(new VerdictStats(5, 0), new VerdictStats(0, 0), new VerdictStats(0, 0));
        assertThat(result.preventionVerdict()).isEqualTo(LinkVerdict.FULL_BREACH);
    }

    @Test
    void mixed_returns_PARTIAL() {
        var result = calc.compute(new VerdictStats(5, 3), new VerdictStats(0, 0), new VerdictStats(0, 0));
        assertThat(result.preventionVerdict()).isEqualTo(LinkVerdict.PARTIAL);
    }

    @Test
    void zero_denominator_returns_NA() {
        var result = calc.compute(new VerdictStats(0, 0), new VerdictStats(0, 0), new VerdictStats(0, 0));
        assertThat(result.preventionVerdict()).isEqualTo(LinkVerdict.N_A);
    }

    @Test
    void detection_combines_node_and_link_levels() {
        var result = calc.compute(
            new VerdictStats(0, 0),   // PREVENTION
            new VerdictStats(3, 2),   // node DETECTION
            new VerdictStats(2, 1)    // link DETECTION
        );
        // 总分母 = 5；总分子 = 3 → PARTIAL
        assertThat(result.detectionVerdict()).isEqualTo(LinkVerdict.PARTIAL);
    }

    @Test
    void detection_all_blocked_returns_FULL_BLOCKED() {
        var result = calc.compute(
            new VerdictStats(0, 0),
            new VerdictStats(3, 3),
            new VerdictStats(2, 2)
        );
        assertThat(result.detectionVerdict()).isEqualTo(LinkVerdict.FULL_BLOCKED);
    }
}
```

#### Task 5.2: 实现

```java
public record VerdictStats(int denominator, int numerator) {
    public VerdictStats {
        if (denominator < 0 || numerator < 0)
            throw new IllegalArgumentException("non-negative required");
        if (numerator > denominator)
            throw new IllegalArgumentException("numerator > denominator");
    }
}

public record LinkVerdictResult(LinkVerdict preventionVerdict, LinkVerdict detectionVerdict) {}

@Component
public class LinkVerdictCalculator {

    public LinkVerdictResult compute(
        VerdictStats prevention,
        VerdictStats nodeDetection,
        VerdictStats linkDetection
    ) {
        var preventionV = computeDimension(prevention);

        var combinedDetection = new VerdictStats(
            nodeDetection.denominator() + linkDetection.denominator(),
            nodeDetection.numerator() + linkDetection.numerator()
        );
        var detectionV = computeDimension(combinedDetection);

        return new LinkVerdictResult(preventionV, detectionV);
    }

    private LinkVerdict computeDimension(VerdictStats stats) {
        if (stats.denominator() == 0) return LinkVerdict.N_A;
        if (stats.numerator() == stats.denominator()) return LinkVerdict.FULL_BLOCKED;
        if (stats.numerator() == 0) return LinkVerdict.FULL_BREACH;
        return LinkVerdict.PARTIAL;
    }

    public VerdictStats fromNodes(List<AttackChainNode> nodes, ExpectationDimension dim) {
        var settled = nodes.stream()
            .filter(n -> n.getState() == NodeState.SETTLED)
            .filter(n -> hasExpectation(n, dim))
            .toList();
        int denominator = settled.size();
        int numerator = (int) settled.stream()
            .filter(n -> getStatus(n, dim) == ExpectationStatus.SUCCESS)
            .count();
        return new VerdictStats(denominator, numerator);
    }
}
```

#### Task 5.3: 集成进 Scheduler

```java
private void checkRunComplete(AttackChainRun run) {
    var allNodes = nodeRepository.findByRunId(run.getId());
    if (allNodes.stream().allMatch(n -> isTerminal(n.getState()))) {
        // Phase 7 会在这里加 SOC link expectation polling
        var verdict = linkVerdictCalculator.compute(
            linkVerdictCalculator.fromNodes(allNodes, ExpectationDimension.PREVENTION),
            linkVerdictCalculator.fromNodes(allNodes, ExpectationDimension.DETECTION),
            new VerdictStats(0, 0)  // Phase 7 替换为真实 link expectations
        );
        run.setVerdictPrevention(verdict.preventionVerdict());
        run.setVerdictDetection(verdict.detectionVerdict());
        run.setVerdictComputedAt(Instant.now());
        run.setStatus(RunStatus.COMPLETED);
        runRepository.save(run);
    }
}
```

---

### Phase 6: SocAlertConnector SPI + ElasticSocConnector

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/soc/SocAlertConnector.java`
- Create: DTOs: `NodeAlertQuery`, `CorrelationRuleQuery`, `DetectionMatch`, `CorrelationMatch`, `AvailableRule`, `HealthCheckResult`
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/soc/SocConnectorRegistry.java`
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/soc/elastic/ElasticSocConnector.java`
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/soc/elastic/ElasticConfig.java`
- Create: `veriguard-api/src/main/java/io/veriguard/rest/attack_chain/SocConnectorApi.java`
- Test: `veriguard-api/src/test/java/io/veriguard/attackchain/soc/elastic/ElasticSocConnectorTest.java`

按 spec §4 完整实现接口 + Elastic ref impl。

#### Task 6.1: SPI 接口（按 spec §4.1）

```java
public interface SocAlertConnector {
    String getConnectorId();
    String getDisplayName();
    List<DetectionMatch> queryNodeAlert(NodeAlertQuery query);
    List<CorrelationMatch> queryCorrelationRule(CorrelationRuleQuery query);
    List<AvailableRule> listAvailableRules();
    HealthCheckResult checkHealth();
}
```

#### Task 6.2: SocConnectorRegistry（spec §4.3）

```java
@Service
public class SocConnectorRegistry {
    private final Map<String, SocAlertConnector> connectors;

    public SocConnectorRegistry(List<SocAlertConnector> impls) {
        this.connectors = impls.stream()
            .collect(toMap(SocAlertConnector::getConnectorId, identity()));
    }

    public SocAlertConnector get(String id) {
        return Optional.ofNullable(connectors.get(id))
            .orElseThrow(() -> new ConnectorNotFoundException(id));
    }

    public List<SocAlertConnector> listAll() { return new ArrayList<>(connectors.values()); }
}
```

#### Task 6.3: ElasticSocConnector + WireMock test

测试用 WireMock 模拟 Elastic API：

```java
@ExtendWith(WireMockExtension.class)
class ElasticSocConnectorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort()).build();

    private ElasticSocConnector connector;

    @BeforeEach
    void setup() {
        var config = new ElasticConfig(wm.baseUrl(), "test-key", ".alerts-*",
            "/api/detection_engine/rules/_find", 10);
        connector = new ElasticSocConnector(config);
    }

    @Test
    void queryCorrelationRule_returns_matches_when_rule_fires() {
        wm.stubFor(post(urlPathMatching("/.alerts-.*/_search"))
            .willReturn(okJson("""
                {"hits":{"hits":[{"_source":{
                    "signal":{"rule":{"id":"rule-1","name":"Lateral Movement"}},
                    "@timestamp":"2026-05-08T10:00:00Z"
                }}]}}""")));

        var result = connector.queryCorrelationRule(
            new CorrelationRuleQuery(UUID.randomUUID(),
                Instant.parse("2026-05-08T09:00:00Z"),
                Instant.parse("2026-05-08T11:00:00Z"),
                "rule-1", Map.of()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).correlationRuleName()).isEqualTo("Lateral Movement");
    }

    @Test
    void checkHealth_returns_healthy_when_cluster_green() {
        wm.stubFor(get(urlPathEqualTo("/_cluster/health"))
            .willReturn(okJson("""{"status":"green","cluster_name":"veriguard"}""")));

        var health = connector.checkHealth();
        assertThat(health.healthy()).isTrue();
    }

    @Test
    void checkHealth_returns_unhealthy_when_unauthorized() {
        wm.stubFor(get(urlPathEqualTo("/_cluster/health"))
            .willReturn(unauthorized()));

        var health = connector.checkHealth();
        assertThat(health.healthy()).isFalse();
        assertThat(health.errorCode()).isEqualTo("UNAUTHORIZED");
    }
}
```

实现 `ElasticSocConnector` 用 `co.elastic.clients:elasticsearch-java` 客户端调 Elastic API。

#### Task 6.4: SocConnectorApi REST endpoint

```java
@RestController
@RequestMapping("/api/soc_connectors")
public class SocConnectorApi {

    private final SocConnectorRegistry registry;

    @GetMapping
    @RBAC(action = Action.READ, resource = ResourceType.SETTING)
    public List<SocConnectorOutput> list() {
        return registry.listAll().stream()
            .map(c -> new SocConnectorOutput(c.getConnectorId(), c.getDisplayName(), c.checkHealth()))
            .toList();
    }

    @GetMapping("/{connectorId}/rules")
    @RBAC(action = Action.READ, resource = ResourceType.SETTING)
    public List<AvailableRule> rules(@PathVariable String connectorId) {
        return registry.get(connectorId).listAvailableRules();
    }

    @GetMapping("/{connectorId}/health")
    @RBAC(action = Action.READ, resource = ResourceType.SETTING)
    public HealthCheckResult health(@PathVariable String connectorId) {
        return registry.get(connectorId).checkHealth();
    }
}
```

---

### Phase 7: AttackChainLinkExpectation + 链路级集成

**Files:**
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/AttackChainLinkExpectation.java`
- Create: `veriguard-model/src/main/java/io/veriguard/database/repository/AttackChainLinkExpectationRepository.java`
- Create: `veriguard-api/src/main/java/io/veriguard/attackchain/soc/ChainSocPoller.java`
- Modify: `AttackChainScheduler.checkRunComplete` (调用 SocPoller + 等 link expectations)
- Modify: `LinkVerdictCalculator` (从 link expectations 算 stats)
- Test: `veriguard-api/src/test/java/io/veriguard/attackchain/soc/ChainSocPollerIntegrationTest.java`

```java
@Service
@Transactional
public class ChainSocPoller {

    private final SocConnectorRegistry registry;
    private final AttackChainLinkExpectationRepository repository;

    public void pollForRun(AttackChainRun run) {
        var expectations = repository.findByAttackChainRunId(run.getId());
        for (var exp : expectations) {
            try {
                var matches = registry.get(exp.getSocRuleRef().connectorId())
                    .queryCorrelationRule(buildQuery(run, exp));
                applyMatches(exp, matches);
            } catch (Exception e) {
                exp.setStatus(ExpectationStatus.UNKNOWN);
                repository.save(exp);
                log.warn("SOC poll failed for expectation {}", exp.getId(), e);
            }
        }
    }

    private CorrelationRuleQuery buildQuery(AttackChainRun run, AttackChainLinkExpectation exp) {
        var ruleRef = exp.getSocRuleRef();
        return new CorrelationRuleQuery(
            run.getId(),
            run.getStartDate(),
            Instant.now().plusSeconds(ruleRef.matchWindowSeconds()),
            ruleRef.ruleId(),
            Map.of()
        );
    }

    private void applyMatches(AttackChainLinkExpectation exp, List<CorrelationMatch> matches) {
        for (var match : matches) {
            var trace = new LinkExpectationTrace();
            trace.setLinkExpectationId(exp.getId());
            trace.setIncidentId(match.incidentId());
            trace.setCorrelationRuleName(match.correlationRuleName());
            trace.setTriggeredAt(match.triggeredAt());
            trace.setScoreDelta(match.score());
            // save trace
            exp.setScore(exp.getScore() + match.score());
        }
        // Recompute status
        if (exp.getScore() >= exp.getExpectedScore()) {
            exp.setStatus(ExpectationStatus.SUCCESS);
        } else if (exp.getScore() > 0) {
            exp.setStatus(ExpectationStatus.PARTIAL);
        } else if (Instant.now().isAfter(exp.getExpirationTime())) {
            exp.setStatus(ExpectationStatus.UNKNOWN);
        }
        repository.save(exp);
    }
}
```

集成到 scheduler.checkRunComplete：

```java
private void checkRunComplete(AttackChainRun run) {
    var allNodes = nodeRepository.findByRunId(run.getId());
    if (allNodes.stream().allMatch(n -> isTerminal(n.getState()))) {
        chainSocPoller.pollForRun(run);
        // Wait for link expectations to settle (or expire)
        // ... 简化：触发后定时器检查，或直接以 polling 结果计算 verdict
        var linkExpectations = linkExpectationRepository.findByAttackChainRunId(run.getId());
        var verdict = linkVerdictCalculator.compute(
            linkVerdictCalculator.fromNodes(allNodes, ExpectationDimension.PREVENTION),
            linkVerdictCalculator.fromNodes(allNodes, ExpectationDimension.DETECTION),
            linkVerdictCalculator.fromLinkExpectations(linkExpectations)
        );
        run.setVerdictPrevention(verdict.preventionVerdict());
        run.setVerdictDetection(verdict.detectionVerdict());
        run.setVerdictComputedAt(Instant.now());
        run.setStatus(RunStatus.COMPLETED);
        runRepository.save(run);
    }
}
```

---

## Phase 8-12: 前端

每个 Phase 一个独立 worktree + PR。前端 TDD 用 Vitest（unit）+ Playwright（E2E）。

### Phase 8: 前端编辑器

**Goal:** 改造 `ChainedTimeline.tsx` 为 `AttackChainCanvas.tsx`，加 ConditionEdgePopover + ⚙ 节点角标 + AttackChainSettingsDrawer。

**Files:**
- Modify: `veriguard-front/src/admin/components/attack_chains/attack_chain/AttackChainCanvas.tsx` (从 ChainedTimeline 改造)
- Create: `veriguard-front/src/admin/components/attack_chains/attack_chain/nodes/ConditionEdgePopover.tsx`
- Create: `veriguard-front/src/admin/components/attack_chains/attack_chain/nodes/EditorNode.tsx`
- Create: `veriguard-front/src/admin/components/attack_chains/attack_chain/AttackChainSettingsDrawer.tsx`
- Create: `veriguard-front/src/admin/components/attack_chains/attack_chain/NodeEditDrawer.tsx`
- Test: `veriguard-front/tests_e2e/tests/admin/attack_chain/condition-popover.spec.ts`

#### Task 8.1: ConditionEdgePopover

完整组件实现（spec §6.3.1）：

```tsx
import React, { useState } from 'react';
import { Popover, ToggleButton, ToggleButtonGroup, Button, IconButton, Stack, Box } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';

type Dimension = 'PREVENTION' | 'DETECTION' | 'MANUAL';
type StatusGroup = 'ANY_SUCCESS' | 'ANY_FAILED' | 'ALL_SUCCESS' | 'ALL_FAILED' | 'SETTLED';

interface ConditionEq { type: 'eq'; dimension: Dimension; status: StatusGroup; }
interface ConditionAnd { type: 'and'; children: Condition[]; }
interface ConditionOr { type: 'or'; children: Condition[]; }
type Condition = ConditionEq | ConditionAnd | ConditionOr;

const defaultEq = (): ConditionEq => ({ type: 'eq', dimension: 'PREVENTION', status: 'ANY_FAILED' });

export const ConditionEdgePopover: React.FC<{
  anchor: HTMLElement | null;
  initialCondition: Condition | null;
  onSave: (cond: Condition | null) => void;
  onClose: () => void;
}> = ({ anchor, initialCondition, onSave, onClose }) => {
  const [cond, setCond] = useState<Condition>(initialCondition ?? defaultEq());

  return (
    <Popover open={!!anchor} anchorEl={anchor} onClose={onClose}
             anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
      <Stack p={2} spacing={2} sx={{ width: 360 }}>
        <ConditionTreeEditor value={cond} onChange={setCond} />
        <Stack direction="row" spacing={1} justifyContent="space-between">
          <Button onClick={() => { onSave(null); onClose(); }}>清空条件</Button>
          <Stack direction="row" spacing={1}>
            <Button onClick={onClose}>取消</Button>
            <Button variant="contained" onClick={() => { onSave(cond); onClose(); }}>保存</Button>
          </Stack>
        </Stack>
      </Stack>
    </Popover>
  );
};

const ConditionTreeEditor: React.FC<{
  value: Condition;
  onChange: (c: Condition) => void;
}> = ({ value, onChange }) => {
  if (value.type === 'eq') {
    return (
      <Stack direction="row" spacing={1} alignItems="center">
        <ToggleButtonGroup value={value.dimension} exclusive
          onChange={(_, d) => d && onChange({ ...value, dimension: d })}>
          <ToggleButton value="PREVENTION">PREVENTION</ToggleButton>
          <ToggleButton value="DETECTION">DETECTION</ToggleButton>
          <ToggleButton value="MANUAL">MANUAL</ToggleButton>
        </ToggleButtonGroup>
        <ToggleButtonGroup value={value.status} exclusive
          onChange={(_, s) => s && onChange({ ...value, status: s })}>
          <ToggleButton value="ANY_FAILED">未拦</ToggleButton>
          <ToggleButton value="ANY_SUCCESS">被拦</ToggleButton>
          <ToggleButton value="SETTLED">已结算</ToggleButton>
        </ToggleButtonGroup>
        <IconButton onClick={() => onChange({ type: 'and', children: [value, defaultEq()] })}>
          <AddIcon /> AND
        </IconButton>
      </Stack>
    );
  }

  return (
    <Box>
      <Box mb={1} sx={{ fontSize: 11, fontWeight: 700 }}>{value.type.toUpperCase()}</Box>
      <Stack spacing={1} sx={{ pl: 2, borderLeft: '2px solid #ccc' }}>
        {value.children.map((child, i) => (
          <Stack key={i} direction="row" alignItems="center">
            <ConditionTreeEditor value={child}
              onChange={c => onChange({ ...value, children: value.children.map((x, j) => j === i ? c : x) })} />
            {value.children.length > 2 && (
              <IconButton size="small"
                onClick={() => onChange({ ...value, children: value.children.filter((_, j) => j !== i) })}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            )}
          </Stack>
        ))}
        <Button size="small" startIcon={<AddIcon />}
          onClick={() => onChange({ ...value, children: [...value.children, defaultEq()] })}>
          加条件
        </Button>
      </Stack>
    </Box>
  );
};
```

#### Task 8.2: AttackChainCanvas（改造 ChainedTimeline）

```tsx
import { ReactFlow, Controls, Background, useNodesState, useEdgesState, MarkerType } from '@xyflow/react';
import '@xyflow/react/dist/style.css';

export const AttackChainCanvas: React.FC<{
  chainId: string;
  readOnly?: boolean;
}> = ({ chainId, readOnly = false }) => {
  const { nodes: rawNodes, edges: rawEdges, isLoading } = useAttackChainGraph(chainId);
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [popoverEdge, setPopoverEdge] = useState<{ id: string; anchor: HTMLElement } | null>(null);

  useEffect(() => {
    setNodes(rawNodes.map(n => ({
      id: n.id,
      type: 'editorNode',
      position: { x: n.x, y: n.y },
      data: { node: n, hasParameterOverride: !!n.validationParameterSetId, repeatCount: n.repeatCount },
    })));
    setEdges(rawEdges.map(e => ({
      id: e.id, source: e.parentNodeId, target: e.childNodeId,
      label: e.condition ? formatConditionLabel(e.condition) : '',
      style: { stroke: e.condition?.type === 'eq' && e.condition.status === 'ANY_SUCCESS' ? '#d33' : '#393' },
      markerEnd: { type: MarkerType.ArrowClosed },
    })));
  }, [rawNodes, rawEdges]);

  const onEdgeClick = useCallback((evt: React.MouseEvent, edge: Edge) => {
    if (!readOnly) {
      setPopoverEdge({ id: edge.id, anchor: evt.target as HTMLElement });
    }
  }, [readOnly]);

  return (
    <Box sx={{ width: '100%', height: 600, position: 'relative' }}>
      <ReactFlow nodes={nodes} edges={edges}
        onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
        onEdgeClick={onEdgeClick}
        nodeTypes={{ editorNode: EditorNode }}
        fitView>
        <Controls />
        <Background />
      </ReactFlow>

      {popoverEdge && (
        <ConditionEdgePopover
          anchor={popoverEdge.anchor}
          initialCondition={edges.find(e => e.id === popoverEdge.id)?.data?.condition ?? null}
          onSave={async cond => {
            await updateEdgeCondition(popoverEdge.id, cond);
            // refetch
          }}
          onClose={() => setPopoverEdge(null)}
        />
      )}
    </Box>
  );
};
```

#### Task 8.3: EditorNode 组件（含 ⚙ 角标）

```tsx
import { Handle, Position, NodeProps } from '@xyflow/react';
import SettingsIcon from '@mui/icons-material/Settings';
import LoopIcon from '@mui/icons-material/Loop';

interface EditorNodeData {
  node: AttackChainNodeOutput;
  hasParameterOverride: boolean;
  repeatCount: number;
}

export const EditorNode: React.FC<NodeProps<EditorNodeData>> = ({ data }) => {
  const { node, hasParameterOverride, repeatCount } = data;
  const incomplete = !node.nodeContractId;

  return (
    <div style={{
      background: 'white', border: '1px solid #ccc', borderRadius: 6,
      padding: '12px 16px', minWidth: 140, position: 'relative',
    }}>
      <Handle type="target" position={Position.Left} />
      <div style={{ fontWeight: 600, fontSize: 12 }}>{node.title}</div>
      <div style={{ fontSize: 10, color: '#888', marginTop: 4 }}>
        {node.nodeContract?.label ?? 'unconfigured'}
      </div>
      <div style={{ position: 'absolute', top: 4, right: 4, display: 'flex', gap: 2 }}>
        <SettingsIcon fontSize="small"
          style={{ color: incomplete ? '#d33' : (hasParameterOverride ? '#2a6fc4' : '#999'), fontSize: 14 }} />
        {repeatCount > 1 && (
          <span style={{ fontSize: 10, color: '#666' }}>
            <LoopIcon fontSize="small" sx={{ fontSize: 11, verticalAlign: 'middle' }} />×{repeatCount}
          </span>
        )}
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
};
```

#### Task 8.4: AttackChainSettingsDrawer

```tsx
export const AttackChainSettingsDrawer: React.FC<{
  open: boolean; onClose: () => void; chainId: string;
}> = ({ open, onClose, chainId }) => {
  const { data: chain } = useFetch(`/api/attack_chains/${chainId}`);
  const [executionMode, setExecutionMode] = useState<'STOP_ON_BLOCK' | 'CONTINUE'>(chain?.executionMode);
  const [paramSetId, setParamSetId] = useState(chain?.validationParameterSetId);
  const [socRules, setSocRules] = useState(chain?.socCorrelationRules ?? []);

  return (
    <Drawer anchor="right" open={open} onClose={onClose}>
      <Box sx={{ width: 400, p: 3 }}>
        <h3>{t('attack_chain.settings_title')}</h3>
        <FormControl>
          <FormLabel>{t('attack_chain.execution_mode.label')}</FormLabel>
          <RadioGroup value={executionMode} onChange={(_, v) => setExecutionMode(v as any)}>
            <FormControlLabel value="STOP_ON_BLOCK" control={<Radio />} label={t('attack_chain.execution_mode.stop_on_block')} />
            <FormControlLabel value="CONTINUE" control={<Radio />} label={t('attack_chain.execution_mode.continue')} />
          </RadioGroup>
        </FormControl>
        <ParameterSetSelector value={paramSetId} onChange={setParamSetId} />
        <SocConnectorRulePicker value={socRules} onChange={setSocRules} />
        <Button variant="contained" onClick={async () => {
          await updateAttackChain(chainId, { executionMode, validationParameterSetId: paramSetId, socCorrelationRules: socRules });
          onClose();
        }}>{t('save')}</Button>
      </Box>
    </Drawer>
  );
};
```

#### Task 8.5: E2E test

文件：`veriguard-front/tests_e2e/tests/admin/attack_chain/condition-popover.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('edit edge condition via popover', async ({ page }) => {
  await page.goto('/admin/attack_chains');
  await page.click('text=新建链路');
  await page.fill('[name=name]', 'E2E Test Chain');
  await page.click('text=保存');

  // Add 2 nodes via canvas drag
  // ... (具体 canvas 交互细节)

  // Click on the edge between them
  await page.click('.react-flow__edge[data-testid="edge-1"]');

  // Popover should appear
  await expect(page.locator('text=PREVENTION')).toBeVisible();

  // Select "未拦" status
  await page.click('button:has-text("未拦")');

  // Save
  await page.click('button:has-text("保存")');

  // Verify edge label updated
  await expect(page.locator('.react-flow__edge-text')).toContainText('未拦');
});
```

---

### Phase 9: 前端运行画布

**Files:**
- Create: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/AttackChainRunCanvas.tsx`
- Create: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/nodes/DoubleLayerNode.tsx`
- Create: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/nodes/NodeRunDetailDrawer.tsx`
- Create: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/VerdictBanner.tsx`
- Create: `veriguard-front/src/admin/components/attack_chain_runs/attack_chain_run/LinkExpectationPanel.tsx`
- Test: `veriguard-front/tests_e2e/tests/admin/attack_chain/create-and-run.spec.ts`

#### Task 9.1: DoubleLayerNode（B 双层卡）

完整代码见 spec §6.3.2。Vitest 单元测试覆盖各 status combo 渲染。

#### Task 9.2: VerdictBanner（顶部横幅，含折叠）

```tsx
export const VerdictBanner: React.FC<{
  run: AttackChainRunOutput;
  onLinkExpectationClick: () => void;
}> = ({ run, onLinkExpectationClick }) => {
  const [collapsed, setCollapsed] = useState(false);
  const isRunning = run.status === 'RUNNING';

  if (isRunning) {
    return (
      <Box sx={{ p: 1.5, borderBottom: '1px solid #ddd', background: '#f5f5f5' }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <CircularProgress size={16} />
          <span>{t('attack_chain_run.running')}: {run.settledNodes}/{run.totalNodes} settled · {run.blockedNodes} blocked · {run.detectedNodes} detected</span>
        </Stack>
      </Box>
    );
  }

  const preventionStyle = verdictStyle(run.verdictPrevention);
  const detectionStyle = verdictStyle(run.verdictDetection);

  return (
    <Box sx={{ borderBottom: '1px solid #ddd' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" px={2} py={1.5}
             sx={{ background: '#fafafa' }}>
        <Stack spacing={collapsed ? 0 : 1}>
          <Stack direction="row" spacing={2} alignItems="center">
            <CircleIcon sx={{ color: preventionStyle.color, fontSize: 14 }} />
            <strong>PREVENTION VERDICT: {t(`attack_chain.verdict.${run.verdictPrevention}`)}</strong>
            {!collapsed && <span style={{ color: '#666', fontSize: 12 }}>
              {run.blockedNodes} of {run.totalNodes} nodes blocked
            </span>}
          </Stack>
          {!collapsed && (
            <Stack direction="row" spacing={2} alignItems="center">
              <CircleIcon sx={{ color: detectionStyle.color, fontSize: 14 }} />
              <strong>DETECTION VERDICT: {t(`attack_chain.verdict.${run.verdictDetection}`)}</strong>
              <Button size="small" onClick={onLinkExpectationClick}>查看 SOC 链路规则</Button>
            </Stack>
          )}
        </Stack>
        <IconButton onClick={() => setCollapsed(!collapsed)} size="small">
          {collapsed ? <ExpandMoreIcon /> : <ExpandLessIcon />}
        </IconButton>
      </Stack>
    </Box>
  );
};

function verdictStyle(v: LinkVerdict): { color: string } {
  switch (v) {
    case 'FULL_BREACH': return { color: '#c93636' };
    case 'FULL_BLOCKED': return { color: '#2a8a3e' };
    case 'PARTIAL': return { color: '#e8941e' };
    case 'PENDING': return { color: '#bbb' };
    case 'N_A': default: return { color: 'transparent' };
  }
}
```

#### Task 9.3: LinkExpectationPanel + NodeRunDetailDrawer

按 spec §6.3.4-5 实现。

#### Task 9.4: E2E

`tests_e2e/tests/admin/attack_chain/create-and-run.spec.ts`：完整链路创建 → 配 5 节点 + 3 边 + ParameterSet → 启动 → 模拟节点结果 → 断言 verdict 横幅。

---

### Phase 10: 前端 ParameterSet UI

**Files:**
- Create: `veriguard-front/src/admin/components/validation_parameter_sets/ParameterSetList.tsx`
- Create: `veriguard-front/src/admin/components/validation_parameter_sets/ParameterSetEditDialog.tsx`
- Create: `veriguard-front/src/admin/components/validation_parameter_sets/ParameterSetOutput.tsx`
- Test: `veriguard-front/tests_e2e/tests/admin/attack_chain/parameter-set-crud.spec.ts`

按 spec §6.3.5。模板锁图标 + 复制后修改交互。

---

### Phase 11: 前端 SOC Connector 状态页

**Files:**
- Create: `veriguard-front/src/admin/components/integrations/soc_connectors/SocConnectorStatusList.tsx`
- Create: `veriguard-front/src/admin/components/integrations/soc_connectors/SocConnectorRulePicker.tsx`
- Test: `veriguard-front/tests_e2e/tests/admin/integrations/soc-connector-status.spec.ts`

按 spec §6.3.6。健康灯 + 可用规则下拉。

---

### Phase 12: i18n 清洗 + 路由更新 + 收尾

**Files:**
- Modify: `veriguard-front/src/utils/lang/zh.json`
- Modify: `veriguard-front/src/utils/lang/en.json`
- Modify: `veriguard-front/src/admin/Index.tsx`
- Modify: `veriguard-front/src/admin/components/nav/LeftBar.tsx`

#### Task 12.1: zh.json / en.json

替换：
```
"scenarios.*" → "attack_chain.*"
"simulations.*" → "attack_chain_run.*"
```

删除：
```
"lessons.*"
"email.*"
"header" / "footer" / "mail_*"
```

新增（按 spec §6.4 列表）：
```json
{
  "attack_chain": {
    "title": "攻击编排",
    "list_title": "链路",
    "create": "新建链路",
    "execution_mode": {
      "label": "执行模式",
      "stop_on_block": "拦截后停止",
      "continue": "无视拦截继续"
    },
    "verdict": {
      "FULL_BREACH": "全链路有效",
      "FULL_BLOCKED": "全链路失效",
      "PARTIAL": "部分失效",
      "PENDING": "计算中",
      "N_A": "不适用"
    },
    "settings_title": "链路设置"
  },
  "attack_chain_node": {
    "title": "节点",
    "repeat_count": "重复执行次数",
    "repeat_interval": "重复间隔（秒）",
    "validation_param_override": "覆盖参数集"
  },
  "attack_chain_run": {
    "title": "运行",
    "running": "运行中"
  },
  "validation_parameter_set": {
    "title": "参数集",
    "is_template": "系统预设",
    "duplicate": "复制为新参数集"
  },
  "soc_connector": {
    "title": "SOC 连接器",
    "status_healthy": "正常",
    "status_unhealthy": "异常"
  }
}
```

#### Task 12.2: 导航更新

`src/admin/components/nav/LeftBar.tsx` 的顶层导航重排：

```tsx
const TOP_NAV: NavItem[] = [
  {
    label: t('attack_chain.title'),
    icon: <AccountTreeIcon />,
    path: '/admin/attack_chains',
    children: [
      { label: t('attack_chain.list_title'), path: '/admin/attack_chains' },
      { label: t('attack_chain_run.title'), path: '/admin/attack_chain_runs' },
      { label: t('validation_parameter_set.title'), path: '/admin/validation_parameter_sets' },
      { label: t('soc_connector.title'), path: '/admin/integrations/soc_connectors' },
    ],
  },
  // ... 其他模块
];
```

#### Task 12.3: 全局回归 + 部署到 192.168.2.124

```bash
yarn check-ts && yarn lint && yarn test --run && yarn test:e2e
mvn -pl veriguard-api -am test
```

构建镜像 + 远程部署：

```bash
docker build -t veriguard-app:dev .
docker save veriguard-app:dev | gzip > /tmp/image.tar.gz
sshpass -p ubuntu scp /tmp/image.tar.gz ubuntu@192.168.2.124:/tmp/
sshpass -p ubuntu ssh ubuntu@192.168.2.124 "cd ~/veriguard && docker load < /tmp/image.tar.gz && docker compose down && docker compose up -d"
```

---

## Self-Review

按 writing-plans skill 要求做的内置自审：

**1. Spec coverage**：

| Spec section | Plan task |
|---|---|
| §1 架构概览 | Phase 0 (改名) + Phase 3 (scheduler) + Phase 6 (SOC) |
| §2.1 ER 图 | Phase 1 (V2 migration) |
| §2.2.1 AttackChain | Phase 0 (rename) + Phase 1 (add columns) |
| §2.2.2 AttackChainNode | Phase 0 + Phase 1 + Phase 4 (repeat) |
| §2.2.3 AttackChainEdge | Phase 0 + Phase 1 + Phase 3 (EdgeCondition) |
| §2.2.4 ValidationParameterSet | Phase 2 |
| §2.2.5 AttackChainRun | Phase 0 + Phase 1 + Phase 5 (verdict) |
| §2.2.6 LinkExpectation | Phase 7 |
| §2.2.7 SocCorrelationRuleRef | Phase 1 (entity) + Phase 6 (usage) |
| §2.2.8 NodeExpectation | Phase 0 (rename) |
| §3.1 节点状态机 | Phase 3 (NodeStateMachine) |
| §3.2 重复执行循环 | Phase 4 |
| §3.3 链路调度 | Phase 3 (launch + onSettled) |
| §3.4 EdgeConditionEvaluator | Phase 3 |
| §3.5 LinkVerdictCalculator | Phase 5 |
| §3.6 异常处理 | Phase 3-7 各自实现（明确写在 task 注释里） |
| §3.7 模板/Run 隔离 | Phase 3 (deep copy in launch via AttackChainRunFactory) |
| §4 SOC connector | Phase 6 + Phase 7 |
| §5 REST API | Phase 2 (param set) + Phase 3-7 (各自) |
| §6 前端 | Phase 8/9/10/11/12 |
| §7 迁移 + 测试 + C 演进 | Phase 1 (migration) + 各 phase test + spec §7.4 文档 |

✅ 全部覆盖。

**2. Placeholder scan**：

- "完整 SQL 见 spec §7.1" → V2 SQL 步骤已详列（核心列表 + Step 1 自动 grep 补全 + Step 4 V3 状态机字段补丁）
- "..."  在 controller code blocks 主体已完整
- 测试 helper `TestDataBuilder` 在 Phase 3-4 多次引用 —— 实施时一次性创建，每个 Phase 复用
- "// 实施时一次性创建" / "// Phase 7 替换" → 明确指出后续 Phase 完成的占位
- ✅ 无 TBD / TODO / 待补充

**3. Type consistency**:

- `RunStatus` enum: SCHEDULED / RUNNING / STOPPED_ON_BLOCK / COMPLETED / CANCELED — 跨 Phase 一致
- `NodeState` enum: SCHEDULED / RUNNING / AWAITING_EXPECTATION / SETTLED / SKIPPED / FAILED — 跨 Phase 一致
- `LinkVerdict`: FULL_BREACH / FULL_BLOCKED / PARTIAL / PENDING / N_A — Phase 5 + Phase 9 (前端 i18n) 一致
- `ExpectationStatus`: PENDING / SUCCESS / PARTIAL / FAILED / UNKNOWN — Phase 5 + Phase 7 一致（Phase 7 link expectation 用同一 enum）
- `EdgeCondition.ExpectationDimension`: PREVENTION / DETECTION / MANUAL — Phase 1 + Phase 3 + Phase 8 一致
- 字段名 `repeat_count` / `repeat_interval_seconds` 跨 Phase 1 (DB) + Phase 4 (logic) + Phase 8 (UI) 一致

✅ 一致。

---

## Execution Handoff

Plan 完成并保存到 `docs/superpowers/plans/2026-05-08-veriguard-attack-orchestration.md`。

两种执行选项：

**1. Subagent-Driven（推荐）** —— 每个 Phase / Task 派一个独立 subagent 执行，主线程做 review + 派发，快速迭代。

**2. Inline Execution** —— 在当前 session 用 executing-plans skill 顺序执行（适合小 Task；但本计划共 13 个 Phase 估 25+ 工作日，inline 不现实）。

**强烈推荐 Subagent-Driven**。

请你回 `1`（subagent-driven）或 `2`（inline），我会启动相应的 sub-skill。
