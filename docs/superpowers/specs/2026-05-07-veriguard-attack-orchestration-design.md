# Veriguard 攻击编排（PRD §2.4）设计稿

> 作者：brainstorm 协同（Claude Opus 4.7 + 用户）
> 日期：2026-05-07
> 状态：已通过分节 review，待 plan 阶段

---

## 摘要

实现 PRD §2.4「攻击编排」全部 10 条原子要求。**改造路径**：将现有 `Scenario` 模块**改造**为 `AttackChain` 模块（path C），不新建独立模块、不保留 OpenBAS 演练语义。

**关键收益**：

- 复用 80% 现有基础设施（执行引擎 / 节点执行器 / RBAC / 导入导出 / 多对多关联）
- 一次性清理 OpenBAS 演练遗留字段（path b 大清洗），代码语义最干净
- 同时为未来 C 决策节点演进保留迁移路径（C-ready 设计）

**不在本次范围**：

- §2.1 流量验证 / §2.2 主机验证（外部适配器，需具体目标设备厂商后单独 spec）
- §2.3 自定义验证（path C-2：拆出独立 `custom_test_cases` 模块，留给后续 spec）
- §2.5 沙箱样本执行链路（M2/M3，留给沙箱后续 spec）

---

## 决策汇总（12 项）

| # | 决策 | 选择 |
|---|---|---|
| 1 | 本次 spec 范围 | 仅 §2.4 攻击编排 |
| 2 | 节点"被拦截"定义 | PREVENTION 独立 / DETECTION 独立两个 verdict 维度 |
| 3 | 链路 verdict 算法 | 计数法（blocked / 分母 → 全 / 失 / 部分）|
| 4 | 执行模式 stop 边界 | 整链路停 |
| 5 | 验证参数集 | 独立 `ValidationParameterSet` 实体（可命名复用） |
| 6 | 重复执行语义 | 累加聚合 + stop-on-block 任意一次拦截即停 |
| 7 | SOC 平台 + 粒度 | 抽象 `SocAlertConnector` + Elastic 示例；链路级 + 节点级 detection 都支持 |
| 8 | 条件分支 UI | A 边标签（C 决策节点作为未来演进 ready）|
| 9 | 改名彻底程度 | iii 全栈彻底改名（Java + DB + API + 前端） |
| 10 | 数据迁移策略 | b 大清洗（TRUNCATE CASCADE + DROP/RENAME/ADD） |
| 11 | Inject 命名 | β → AttackChainNode（不保留 BAS 行话）|
| 12 | 路径图结果叠加 | B 双层卡（顶部 PREVENTION + 底部 DETECTION）+ 顶部 verdict 横幅 |

完整决策日志见附录 C。

---

## 1. 架构概览

### 1.1 模块拆分

新建 Java 包 `io.veriguard.attackchain`，下分 7 子包：

| 子包 | 职责 | 主要类 |
|---|---|---|
| `attackchain` | AttackChain（链路模板）CRUD + 元信息 | `AttackChain` 实体、`AttackChainService`、`AttackChainApi` |
| `attackchain.node` | 节点定义 + 执行器绑定（即原 Inject）| `AttackChainNode`、`NodeService`、`NodeContract`（原 InjectorContract）|
| `attackchain.edge` | 边定义 + 条件 eval 函数 | `AttackChainEdge`、`EdgeConditionEvaluator`（独立函数，C-ready）|
| `attackchain.validation` | ValidationParameterSet + 期望生成 | `ValidationParameterSet` 实体、`ValidationParameterService`、`ExpectationFactory` |
| `attackchain.execution` | AttackChainRun 实例化 + 调度 + 状态机 | `AttackChainRun` 实体、`AttackChainScheduler`、`NodeStateMachine`、`ExecutableNode` |
| `attackchain.verdict` | 链路级 verdict 计算 | `LinkVerdictCalculator`（PREVENTION 维度 + DETECTION 维度独立算）|
| `attackchain.soc` | SOC 告警匹配抽象 + 实现 | `SocAlertConnector` 接口、`ElasticSocConnector`、`SocCorrelationRuleRef` 配置 |

### 1.2 职责边界

- **Template 域**：纯定义，不知道运行时
- **Runtime 域**：拿 Template 快照（深拷贝节点 + 边 + 参数集）实例化，独立运行；template 改动不影响进行中的 run
- **NodeExecutor 接口**：保持现有 `Injector` 抽象（只是改名 `NodeExecutor`），所有现有 executor 适配（Caldera / CrowdStrike / SentinelOne / Tanium / Veriguard agent）改名后即用
- **SocAlertConnector**：独立 SPI，不与 NodeExecutor 共享接口（一个是"打"，一个是"查告警"，语义不同）
- **Collectors**：现有 collectors（`expectations_expiration_manager` / `expectations_vulnerability_manager`）继续承担节点级 expectation 状态维护，新增 SocAlertConnector 在链路结束时单独触发链路级查询

### 1.3 数据流（一次完整 run）

```
1. User 在画布上创建 AttackChain (template) → 持久化
                ↓
2. User 点 "运行" → AttackChainScheduler.launch(template)
                ↓
3. Scheduler 深拷贝 template → AttackChainRun 实例
                ↓
4. Scheduler 排出 root nodes（无入边的节点）
                ↓
5. 每个 root node：NodeExecutor.execute() → 外部 (Caldera/agent/...)
                ↓
6. Collectors 持续更新 NodeExpectation（PREVENTION 分数、DETECTION 分数）
                ↓
7. NodeStateMachine 监测节点是否"稳定"（expectation 全部 SETTLED 或超时）
                ↓
8. 节点稳定 → EdgeConditionEvaluator 算出哪些下游节点该跑
                ↓ stop-on-block 模式 + 节点 PREVENTION = SUCCESS → 整链停
                ↓
9. 所有节点稳定 → SocAlertConnector 查链路级 correlation rule
                ↓
10. LinkVerdictCalculator 算 PREVENTION verdict + DETECTION verdict
                ↓
11. AttackChainRun 状态 = COMPLETED；前端订阅刷新
```

### 1.4 与现有系统的边界

**保留不动**：

- `Tag` / `Team` / `User` / `Asset` / `AssetGroup` / `Document` / `Variable` / `Grant` 等共享实体
- `NodeContract`（原 InjectorContract）payload 类型枚举（Command / Executable / FileDrop / DnsResolution / NetworkTraffic）
- `Caldera` / `CrowdStrike` / `SentinelOne` / `Tanium` / `Veriguard agent` 现有 NodeExecutor 实现
- 现有 collectors 框架

**改造**：

- Scenario module 全部 → AttackChain module（机械改名 + 字段调整）
- Exercise module → AttackChainRun module
- Inject\* → AttackChainNode\* 系列
- 前端 `src/admin/components/scenarios/` → `attack_chains/`
- 前端 `src/admin/components/simulations/` → `attack_chain_runs/`

**新增**：

- `ValidationParameterSet` 实体 + CRUD UI
- `SocAlertConnector` SPI + Elastic 实现
- `LinkVerdictCalculator` 服务
- 边条件 popover + ⚙ 节点角标（前端）
- 双层卡运行结果节点组件（前端）
- 顶部 verdict 横幅

---

## 2. 数据模型

### 2.1 ER 关系图

```
                    ValidationParameterSet (新)
                            ▲
                            │ FK (default for chain)
                            │
                       AttackChain (改造 Scenario)
                            │
                            │ 1..N
                            ▼
              ┌──── AttackChainNode (改造 Inject) ◄──── ValidationParameterSet
              │     (template + runtime 双用)             FK (node-level override, nullable)
              │
              │ N..N via AttackChainEdge (改造 InjectDependency)
              │      └─ condition: JSON (eq/and/or, C-ready)
              │
   "运行" 触发  ▼
              AttackChainRun (改造 Exercise)
              ├── 持有该 run 的所有 AttackChainNode 副本
              ├── verdict_prevention / verdict_detection 缓存
              └── AttackChainLinkExpectation (新)  ◄── 链路级 SOC 告警匹配
                     N..1 → SocCorrelationRuleRef (embeddable)
```

### 2.2 实体明细

#### 2.2.1 `AttackChain` (改造 `Scenario`)

| 字段 | 类型 | 说明 | 来源 |
|---|---|---|---|
| `attack_chain_id` | UUID PK | — | rename `scenario_id` |
| `name`, `description`, `subtitle` | text | — | KEEP |
| `category`, `main_focus`, `severity` | text/enum | 攻击编排分类（语义改） | KEEP |
| `recurrence` | cron | 周期触发链路验证 | KEEP |
| `tags`, `documents`, `teams`, `variables`, `grants` | 多对多关联 | — | KEEP |
| **`execution_mode`** | enum `STOP_ON_BLOCK / CONTINUE` | 拦截后停 / 继续 | **NEW** |
| **`validation_parameter_set_id`** | FK ValidationParameterSet | 默认参数集 | **NEW** |
| **`soc_correlation_rules`** | List<SocCorrelationRuleRef> (embedded JSON) | 链路级 SOC 告警匹配规则 | **NEW** |
| `created_at`, `updated_at` | timestamp | — | KEEP |
| ~~`header`, `footer`, `from`, `reply_tos`~~ | — | 邮件演练遗留 | **DROP** |
| ~~`lessons_categories`~~ | — | 事后复盘演练遗留 | **DROP** |
| ~~`articles`, `challenges`~~ 关联 | — | Phase 11.5 已删 | — |

#### 2.2.2 `AttackChainNode` (改造 `Inject`，template + runtime 双用)

| 字段 | 类型 | 说明 | 来源 |
|---|---|---|---|
| `node_id` | UUID PK | — | rename `inject_id` |
| `node_attack_chain_id` | FK AttackChain (nullable) | 模板时非空 | rename `inject_scenario` |
| `node_attack_chain_run_id` | FK AttackChainRun (nullable) | 运行时非空 | rename `inject_exercise` |
| `title`, `description`, `enabled`, `content` (JSON) | — | KEEP |
| `node_contract_id` | FK NodeContract | 节点用例模板 | rename `inject_contract` |
| `depends_duration` | long | 节点延迟（已有） | KEEP |
| **`repeat_count`** | int default 1 | 重复执行次数 | **NEW** |
| **`repeat_interval_seconds`** | long default 0 | 重复间隔 | **NEW** |
| **`validation_parameter_set_id`** | FK (nullable) | 节点级覆盖；NULL = 继承 chain 默认 | **NEW** |
| `teams`, `assets`, `asset_groups`, `documents`, `tags` | 多对多 | — | KEEP |
| `created_at`, `updated_at` | timestamp | — | KEEP |

CHECK constraint：`(node_attack_chain_id IS NULL) <> (node_attack_chain_run_id IS NULL)` —— 模板和运行时互斥。

#### 2.2.3 `AttackChainEdge` (改造 `InjectDependency`)

| 字段 | 类型 | 说明 | 来源 |
|---|---|---|---|
| `edge_id` | UUID PK | (替代复合主键) | **改造** |
| `parent_node_id` | FK AttackChainNode | — | rename |
| `child_node_id` | FK AttackChainNode | — | rename |
| `condition` | JSON (`EdgeCondition` 嵌入对象) | eq/and/or 表达式 | KEEP（C-ready schema）|
| `created_at`, `updated_at` | timestamp | — | KEEP |

**C-ready 约束**：`condition` JSON schema 中每条原子条件是一个独立对象（含 `id`、`type`、`operator`、`expected_status`、`expected_expectation_type`），未来抽出为 ConditionNode 实体时直接当字段映射即可。

#### 2.2.4 `ValidationParameterSet` (新)

| 字段 | 类型 | 说明 |
|---|---|---|
| `parameter_set_id` | UUID PK | — |
| `name` | text unique | "PCI-DSS 严格"、"日常巡检"等命名 |
| `description` | text nullable | — |
| `is_template` | boolean default false | 标记为系统预设模板（不可删） |
| `default_targets` | JSON List<Asset/AssetGroup ref> | 默认验证目标 |
| `prevention_expected_score` | int default 100 | PREVENTION 期望成功分 |
| `prevention_expiration_seconds` | int default 1800 | PREVENTION 期望超时 |
| `detection_expected_score` | int default 100 | DETECTION 期望成功分 |
| `detection_expiration_seconds` | int default 1800 | DETECTION 期望超时 |
| `soc_correlation_rules` | List<SocCorrelationRuleRef> (embedded JSON) | 默认 SOC 告警规则集 |
| `tags` | 多对多 Tag | — |
| `created_at`, `updated_at` | timestamp | — |

**种子数据**：V2 migration 自动插入 3 个 `is_template=true` 的预设：

- `严格`（默认 100 / 30min）
- `宽松`（80 / 15min）
- `快速演练`（50 / 5min）

客户可以基于这些复制再改。

#### 2.2.5 `AttackChainRun` (改造 `Exercise`)

| 字段 | 类型 | 说明 | 来源 |
|---|---|---|---|
| `run_id` | UUID PK | — | rename `exercise_id` |
| `run_attack_chain_id` | FK AttackChain | 来源模板 | rename `exercise_scenario` |
| `name`, `description`, `start_date`, `end_date` | — | KEEP |
| `status` | enum `SCHEDULED / RUNNING / STOPPED_ON_BLOCK / COMPLETED / CANCELED` | 运行时状态机 | **改造**（加 STOPPED_ON_BLOCK）|
| **`verdict_prevention`** | enum `FULL_BREACH / FULL_BLOCKED / PARTIAL / PENDING / N_A` | PREVENTION 维度 verdict 缓存 | **NEW** |
| **`verdict_detection`** | enum 同上 | DETECTION 维度 verdict 缓存 | **NEW** |
| **`verdict_computed_at`** | timestamp nullable | verdict 何时算出 | **NEW** |
| `created_at`, `updated_at` | timestamp | — | KEEP |
| ~~邮件 / 演练相关字段~~ | — | DROP（同 Scenario 一并清）| **DROP** |

**verdict 命名**：

- `FULL_BREACH` = 全链路有效（攻击全成功，防御全败）
- `FULL_BLOCKED` = 全链路失效（攻击全失败，防御全胜）
- `PARTIAL` = 部分失效
- `PENDING` = 计算中
- `N_A` = 不适用（如 DETECTION 维度，链路无 SOC 规则配置）

#### 2.2.6 `AttackChainLinkExpectation` (新)

链路级 DETECTION 期望（每个 SocCorrelationRuleRef 一条）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `link_expectation_id` | UUID PK | — |
| `attack_chain_run_id` | FK AttackChainRun | 所属 run |
| `soc_rule_ref` | embedded `SocCorrelationRuleRef` | 要查的 SOC 规则 |
| `score` | int default 0 | 当前分数（SocAlertConnector 累加） |
| `expected_score` | int | 期望分（来自 ParameterSet 快照） |
| `status` | enum `PENDING / SUCCESS / PARTIAL / FAILED / UNKNOWN` | 复用 `InjectExpectation.computeStatus` 算法 |
| `expiration_time` | timestamp | 过期变 UNKNOWN |
| `traces` | OneToMany `LinkExpectationTrace` | SOC 上报的具体匹配记录 |
| `created_at`, `updated_at` | — | — |

#### 2.2.7 `SocCorrelationRuleRef` (embeddable，不是表)

| 字段 | 类型 | 说明 |
|---|---|---|
| `connector_id` | text | 哪个 SocAlertConnector 实现（`elastic` / future others） |
| `rule_id` | text | 该 SOC 平台内的规则 ID 或 saved search ID |
| `display_name` | text | 给前端展示的友好名 |
| `match_window_seconds` | int default 7200 | 链路结束后查询多久窗口的告警 |

#### 2.2.8 `AttackChainNodeExpectation` (改造 `InjectExpectation`)

字段几乎不变，仅做命名同步：`inject_expectation_id` → `node_expectation_id`、FK rename。**保留** PREVENTION / DETECTION / MANUAL / VULNERABILITY 4 类（DETECTION 现在也用于 SOC 节点级告警匹配 collector）。

### 2.3 关键关系约束

1. **`AttackChainNode.attack_chain_id` 与 `attack_chain_run_id` 互斥**：CHECK constraint 保证恰有一个非空
2. **`AttackChainEdge` 的 parent / child 必须属于同一 `attack_chain_id` 或 `attack_chain_run_id`**：DB CHECK 或 service 校验
3. **`ValidationParameterSet` 删除前必须无引用**：FK ON DELETE RESTRICT
4. **`AttackChainLinkExpectation` 仅存在于 runtime**：链路级 SOC 规则在 template 上是配置，run 时实例化为 expectation

---

## 3. 执行引擎

### 3.1 节点状态机

```
   ┌─────────────┐
   │  SCHEDULED  │ 等待 depends_duration 倒计时 / 等待父节点 SETTLED
   └──────┬──────┘
          │ 时间到 + 父节点条件满足
          ▼
   ┌─────────────┐
   │   RUNNING   │ NodeExecutor.execute() 已下发，等执行器完成
   └──────┬──────┘
          │ executor 返回（成功 / 失败）
          ▼
   ┌──────────────────────┐
   │ AWAITING_EXPECTATION │ inject 已落地，等 collectors 填 expectation 状态
   └──────┬───────────────┘
          │ 全部 expectation SETTLED 或 expiration_time 到
          ▼
   ┌─────────────┐    ┌─────────────────────┐
   │   SETTLED   │    │ repeat_count > iter │
   └──────┬──────┘    │ → 继续重复          │
          │           └─────────┬───────────┘
          │                     │ wait repeat_interval
          │                     ▼ 回 RUNNING（同一节点）
          ▼
   "节点最终状态" = PREVENTION / DETECTION 各自聚合到 SUCCESS / FAILED / PARTIAL / UNKNOWN

   分支：
   ├─ FAILED       executor 抛异常（连不上 / 超时 / 配置错）
   └─ SKIPPED      父节点条件不满足，或链路 stop-on-block 后取消
```

**稳定 (SETTLED) 判定**：节点的所有 `AttackChainNodeExpectation` 状态从 PENDING 进入终态（SUCCESS / FAILED / PARTIAL）或超过 `expiration_time` 强转为 UNKNOWN，节点即 SETTLED。

### 3.2 重复执行循环（伪代码）

```
on node enter SCHEDULED:
    iteration = 0
    schedule iteration_0 with depends_duration delay

on iteration N AWAITING_EXPECTATION → SETTLED:
    if continue_mode:
        if iteration < repeat_count:
            wait repeat_interval
            iteration += 1
            schedule next iteration → RUNNING
        else:
            node enters SETTLED
            propagate to children
    if stop_on_block_mode:
        if any iteration's PREVENTION = SUCCESS:
            node enters SETTLED (early exit)
            trigger chain stop-on-block
        elif iteration < repeat_count:
            wait repeat_interval
            iteration += 1
            schedule next iteration
        else:
            node enters SETTLED
            propagate to children

节点级 PREVENTION 状态 = N 次迭代 expectation 用 expectation_group 聚合
节点级 DETECTION 状态 = 同样聚合
```

### 3.3 链路调度（`AttackChainScheduler`）

```
launch(template):
    1. 深拷贝 template → AttackChainRun + 拷贝所有节点（attack_chain_run_id 非空）+ 边
    2. 解析 ValidationParameterSet（chain default + 每个节点 override），快照到 NodeExpectation 模板
    3. 找 root nodes（入度 = 0）→ SCHEDULED
    4. AttackChainRun.status = RUNNING

on node N SETTLED (or SKIPPED):
    1. 持久化 N 的最终 PREVENTION / DETECTION 状态
    2. 检查 stop_on_block：
       if execution_mode == STOP_ON_BLOCK and N.prevention == SUCCESS:
           AttackChainRun.status = STOPPED_ON_BLOCK
           对所有 SCHEDULED 状态的节点 → SKIPPED
           等所有 RUNNING / AWAITING_EXPECTATION 节点自然结束 (不取消已下发的 inject)
           goto check_run_complete
    3. 否则：找出 N 的所有出边
       for each edge in outgoing(N):
           if EdgeConditionEvaluator.evaluate(edge.condition, N.状态):
               child = edge.child
               if all parents of child are SETTLED:
                   child.status = SCHEDULED
                   schedule child with depends_duration delay
           else:
               (该路径不走，但 child 可能有其他父节点；child 的最终 SKIPPED 由"所有父节点都拒走"判定)
    4. goto check_run_complete

check_run_complete:
    if all nodes in (SETTLED, SKIPPED, FAILED):
        触发链路级 SOC 查询：SocAlertConnector.poll(chain_link_expectations)
        等 link_expectations 全部 SETTLED 或 expired
        LinkVerdictCalculator.compute(run) → 写 verdict_prevention / verdict_detection
        AttackChainRun.status = COMPLETED
        verdict_computed_at = now
```

### 3.4 Edge 条件求值（`EdgeConditionEvaluator`，C-ready）

独立纯函数，可单测、可未来包入 ConditionNode 路由层：

```java
class EdgeConditionEvaluator {
    /**
     * @return true 表示边可达（child 应被调度），false 表示该路径不走
     */
    boolean evaluate(EdgeCondition condition, NodeFinalStatus parentStatus) {
        // 走 condition 树（eq/and/or）
        // 比对 parentStatus 中 PREVENTION / DETECTION / MANUAL 等维度
    }
}
```

`EdgeCondition` 是 sealed interface：`Eq`、`And`、`Or` 三种实现（C-ready，未来 `Decision` 节点直接复用同一颗树）。

### 3.5 链路 Verdict 计算（`LinkVerdictCalculator`）

```
PREVENTION 维度：
    分母 = run 的所有 SETTLED 节点中 PREVENTION expectation 非空的数量
    分子 = 分母中 PREVENTION = SUCCESS 的数量

    if 分母 == 0: verdict_prevention = N_A
    else if 分子 / 分母 == 1.0: verdict_prevention = FULL_BLOCKED  (全失效，防御全胜)
    else if 分子 / 分母 == 0.0: verdict_prevention = FULL_BREACH   (全有效，防御全败)
    else: verdict_prevention = PARTIAL

DETECTION 维度（同时考虑节点级 + 链路级）：
    节点级分母 = 同上 PREVENTION，但看 DETECTION expectation
    节点级分子 = DETECTION = SUCCESS 数
    链路级分母 = AttackChainLinkExpectation 总数
    链路级分子 = AttackChainLinkExpectation = SUCCESS 数

    总分母 = 节点级分母 + 链路级分母
    总分子 = 节点级分子 + 链路级分子

    if 总分母 == 0: verdict_detection = N_A
    else 类似 PREVENTION 的算法

stop_on_block 截断时:
    分母 只算"已 SETTLED 节点"（被 SKIPPED 的不算）
    通常情况下：第一个被拦节点 → 分母=1，分子=1 → FULL_BLOCKED
```

### 3.6 异常处理

| 场景 | 处理 |
|---|---|
| `NodeExecutor.execute()` 抛异常 | 节点 → FAILED；expectations 保持 PENDING；按 expiration_time 转 UNKNOWN |
| Collector 长期不上报 expectation | `expiration_time` 到时强转 UNKNOWN，节点照常 SETTLED |
| `SocAlertConnector` 调用失败 | 记日志；该 link expectation 标 UNKNOWN（不无限重试）；verdict_detection 算到该条时计 PARTIAL |
| 节点 `repeat` 中 executor 第 K 次失败 | 该次 iteration → FAILED；累计到 expectation_group；继续下一次 iteration（除非 stop_on_block 触发）|
| `AttackChainRun` 进程崩溃 | scheduler 启动时扫 RUNNING / AWAITING_EXPECTATION 状态运行 → 比对 expiration_time 决定继续等 / 转 UNKNOWN |
| 用户手动 cancel run | 所有 SCHEDULED → SKIPPED；RUNNING 节点正常完成；run 状态 = CANCELED；verdict 仍计算（按已 SETTLED 节点）|

### 3.7 运行实例与模板的隔离

- 启动 run 时**深拷贝节点 + 边**（包括 ValidationParameterSet 解析结果快照到 expectation 的 expected_score / expiration_time）
- 模板事后改 ParameterSet → 已启动的 run **不受影响**
- 这条隔离保证客户在长跑链路过程中调整参数集不会污染历史 run 的 verdict

---

## 4. SOC Connector 抽象

### 4.1 SPI 接口

```java
public interface SocAlertConnector {
    /** 连接器唯一标识（"elastic" / "splunk" / "qradar" / 自研） */
    String getConnectorId();

    /** 给前端展示的友好名 */
    String getDisplayName();

    /**
     * 节点级查询：某个 inject 触发后，SOC 是否产生匹配告警？
     * 时机：节点 AWAITING_EXPECTATION 期间，定时轮询 + 可选 push
     * 输出：写入 NodeExpectationTrace（DETECTION 维度）→ 累加 score
     */
    List<DetectionMatch> queryNodeAlert(NodeAlertQuery query);

    /**
     * 链路级查询：某 correlation rule 在指定时间窗口内是否触发？
     * 时机：所有节点 SETTLED 后一次性调用 + 失败重试（exponential backoff，≤ 3 次）
     * 输出：写入 LinkExpectationTrace → 累加 score
     */
    List<CorrelationMatch> queryCorrelationRule(CorrelationRuleQuery query);

    /**
     * 给 UI 下拉选规则：列出该 SOC 平台上可用的 correlation rules / saved searches
     */
    List<AvailableRule> listAvailableRules();

    /** 配置健康检查（凭证 / 网络 / 权限） */
    HealthCheckResult checkHealth();
}
```

### 4.2 DTO

```java
record NodeAlertQuery(
    UUID nodeId,
    Instant injectExecutedAt,
    Instant queryWindowEnd,
    List<Asset> targetAssets,
    Set<String> nodeContractTags,
    Map<String, String> connectorParams
) {}

record CorrelationRuleQuery(
    UUID runId,
    Instant runStartedAt,
    Instant queryWindowEnd,
    String ruleId,
    Map<String, String> connectorParams
) {}

record DetectionMatch(
    String alertId,
    String ruleName,
    Instant triggeredAt,
    int score,
    Map<String, Object> raw
) {}

record CorrelationMatch(
    String incidentId,
    String correlationRuleName,
    Instant triggeredAt,
    int score,
    Map<String, Object> raw
) {}

record AvailableRule(
    String ruleId,
    String displayName,
    String description,
    String category
) {}
```

### 4.3 Connector 注册

通过 Spring `@Component` 自动发现：

```java
@Component
public class ElasticSocConnector implements SocAlertConnector {
    @Override
    public String getConnectorId() { return "elastic"; }
    // ...
}

@Service
public class SocConnectorRegistry {
    private final Map<String, SocAlertConnector> connectors;

    @Autowired
    public SocConnectorRegistry(List<SocAlertConnector> implementations) {
        this.connectors = implementations.stream()
            .collect(toMap(SocAlertConnector::getConnectorId, identity()));
    }

    public SocAlertConnector get(String connectorId) {
        return Optional.ofNullable(connectors.get(connectorId))
            .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
    }
}
```

### 4.4 Elastic Reference 实现

依赖：`co.elastic.clients:elasticsearch-java`（已在 pom，veriguard-dev 跑了 OpenSearch / Elastic）。

**配置**：

```properties
veriguard.soc.elastic.enabled=true
veriguard.soc.elastic.url=https://elastic.internal:9200
veriguard.soc.elastic.api-key=${ELASTIC_API_KEY}
veriguard.soc.elastic.alert-index=.alerts-security.alerts-*
veriguard.soc.elastic.detection-rules-api=/api/detection_engine/rules/_find
veriguard.soc.elastic.query-timeout-seconds=10
```

**实现要点**：

- 节点级查询：拼 Elastic Query：`event.type:alert AND signal.original_time >= ${injectExecutedAt} AND signal.original_time <= ${queryWindowEnd} AND host.ip IN ${targetAssets.ip}`
- 链路级查询：拼 Elastic Query：`event.type:signal AND signal.rule.id == ${ruleId} AND @timestamp >= ${runStartedAt} AND @timestamp <= ${queryWindowEnd}`
- listAvailableRules：调 Kibana Detection Engine API `/api/detection_engine/rules/_find`，缓存 5 分钟
- checkHealth：ping `${url}/_cluster/health`

### 4.5 凭证管理

- 配置走 `application.properties` + 环境变量（密钥）
- **不入数据库**（避免凭证泄漏面），不走 admin UI 在线编辑
- 部署时 ops 通过 docker compose env 注入
- `checkHealth()` 暴露在 admin UI 的"集成 → SOC 连接器"页签 —— 红绿灯式，凭证错时只显示"配置错误，请检查 ops 日志"

### 4.6 调用时序

```
节点级（AWAITING_EXPECTATION 阶段）：

  scheduler.tick (每 30s):
    for each node in AWAITING_EXPECTATION with DETECTION expectation:
      for each connector_id in node's expected detection sources:
        connector = registry.get(connector_id)
        matches = connector.queryNodeAlert(NodeAlertQuery(node, ...))
        for match in matches:
          NodeExpectationTrace.create(match → score)


链路级（chain run 完成后）：

  on AttackChainRun all nodes SETTLED:
    for each link_expectation in run.linkExpectations:
      connector = registry.get(link_expectation.soc_rule_ref.connector_id)
      try with retry (3, exp backoff):
        matches = connector.queryCorrelationRule(CorrelationRuleQuery(run, ...))
        for match in matches:
          LinkExpectationTrace.create(match → score)
      catch:
        link_expectation.status = UNKNOWN
        log warn
    LinkVerdictCalculator.compute(run)
```

### 4.7 未来扩展接入新 SOC

1. 实现 `SocAlertConnector` 接口（一个 Java 类）
2. 添加 `application.properties` 配置项
3. 注册为 `@Component`
4. 完成 —— 无需改 `AttackChainScheduler` / `LinkVerdictCalculator` / 任何上层代码

---

## 5. REST API Surface

### 5.1 URI 命名约定

- 资源路径全部 snake_case 复数：`/api/attack_chains`、`/api/attack_chain_runs`、`/api/validation_parameter_sets`、`/api/soc_connectors`
- RBAC 通过现有 `@RBAC` AOP（保留 `Action.READ / WRITE / DELETE / EXECUTE` + 新建 `ResourceType.ATTACK_CHAIN` / `ATTACK_CHAIN_RUN` / `VALIDATION_PARAMETER_SET`）
- 搜索用现有 `SearchPaginationInput` 框架
- 错误响应用现有 `ProblemDetail` 包装

### 5.2 `/api/attack_chains` —— 模板 CRUD

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/attack_chains` | 创建 |
| GET | `/api/attack_chains/{id}` | 详情（含 nodes / edges / 默认参数集） |
| PUT | `/api/attack_chains/{id}` | 更新元信息（不含 nodes/edges）|
| DELETE | `/api/attack_chains/{id}` | 删除（级联删未启动的 run） |
| POST | `/api/attack_chains/search` | 分页搜索 |
| POST | `/api/attack_chains/{id}/duplicate` | 拷贝模板 |
| POST | `/api/attack_chains/{id}/launch` | **启动一次 run** |

```java
record AttackChainInput(
    String name,
    String description,
    String category,
    String mainFocus,
    String severity,
    String recurrence,
    ExecutionMode executionMode,
    UUID validationParameterSetId,
    List<SocCorrelationRuleRef> socCorrelationRules,
    Set<String> tagIds,
    Set<UUID> teamIds,
    Set<String> documentIds
) {}

record LaunchOptions(
    Instant scheduledStart,
    String runName,
    UUID validationParameterSetIdOverride
) {}
```

### 5.3 `/api/attack_chain_nodes` —— 节点 CRUD（扁平资源）

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/attack_chain_nodes` | 创建 |
| GET | `/api/attack_chain_nodes/{id}` | 详情 |
| PUT | `/api/attack_chain_nodes/{id}` | 更新 |
| DELETE | `/api/attack_chain_nodes/{id}` | 删除 |
| POST | `/api/attack_chain_nodes/bulk_update_dependencies` | 批量更新一批节点的边 |

```java
record AttackChainNodeInput(
    UUID attackChainId,
    UUID attackChainRunId,
    String title,
    String description,
    boolean enabled,
    UUID nodeContractId,
    JsonNode content,
    long dependsDuration,
    int repeatCount,
    long repeatIntervalSeconds,
    UUID validationParameterSetId,
    Set<UUID> teamIds,
    Set<UUID> assetIds,
    Set<UUID> assetGroupIds,
    Set<String> documentIds,
    Set<String> tagIds
) {}
```

### 5.4 `/api/attack_chain_edges` —— 边 CRUD

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/attack_chain_edges` | 创建 |
| PUT | `/api/attack_chain_edges/{id}` | 更新条件 |
| DELETE | `/api/attack_chain_edges/{id}` | 删除 |
| POST | `/api/attack_chain_edges/bulk` | 批量改 |

```java
record AttackChainEdgeInput(
    UUID parentNodeId,
    UUID childNodeId,
    EdgeCondition condition
) {}

sealed interface EdgeCondition {
    record Eq(
        ExpectationDimension dimension,
        ExpectationStatusGroup status
    ) implements EdgeCondition {}
    record And(List<EdgeCondition> children) implements EdgeCondition {}
    record Or(List<EdgeCondition> children) implements EdgeCondition {}
}
```

### 5.5 `/api/attack_chain_runs` —— 运行实例

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/attack_chain_runs/{id}` | 详情（含 nodes 实时状态 + verdict） |
| POST | `/api/attack_chain_runs/search` | 分页搜索 |
| POST | `/api/attack_chain_runs/{id}/cancel` | 手动 cancel |
| GET | `/api/attack_chain_runs/{id}/timeline` | 时间轴数据 |
| GET | `/api/attack_chain_runs/{id}/verdict` | 当前 verdict 快照 |
| POST | `/api/attack_chain_runs/{id}/recompute_verdict` | 强制重算 |
| GET | `/api/attack_chain_runs/{id}/link_expectations` | 链路级 expectations 列表 |

```java
record AttackChainRunOutput(
    UUID id,
    UUID attackChainId,
    String name,
    Instant startDate,
    Instant endDate,
    RunStatus status,
    LinkVerdict verdictPrevention,
    LinkVerdict verdictDetection,
    Instant verdictComputedAt,
    int totalNodes,
    int settledNodes,
    int blockedNodes,
    int detectedNodes,
    List<AttackChainNodeRuntimeOutput> nodes,
    List<LinkExpectationOutput> linkExpectations
) {}
```

### 5.6 `/api/validation_parameter_sets` —— 参数集 CRUD

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/validation_parameter_sets` | 创建 |
| GET | `/api/validation_parameter_sets/{id}` | 详情 |
| PUT | `/api/validation_parameter_sets/{id}` | 更新 |
| DELETE | `/api/validation_parameter_sets/{id}` | 删除（FK RESTRICT） |
| POST | `/api/validation_parameter_sets/search` | 分页 |
| POST | `/api/validation_parameter_sets/{id}/duplicate` | 模板派生 |

```java
record ValidationParameterSetInput(
    String name,
    String description,
    List<TargetRef> defaultTargets,
    int preventionExpectedScore,
    int preventionExpirationSeconds,
    int detectionExpectedScore,
    int detectionExpirationSeconds,
    List<SocCorrelationRuleRef> socCorrelationRules,
    Set<String> tagIds
) {}
```

### 5.7 `/api/soc_connectors` —— SOC 连接器元信息

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/soc_connectors` | 列出已注册连接器 + 健康状态 |
| GET | `/api/soc_connectors/{connector_id}/rules` | 列出该连接器可用规则 |
| GET | `/api/soc_connectors/{connector_id}/health` | 单独触发健康检查 |

### 5.8 命名一致性

旧端点 → 新端点的全表 mapping 在 plan 阶段单独维护一份 `api-rename-table.md`，前端 `src/utils/api-types.d.ts` 通过 `yarn generate-types-from-api` 全量重生成。

---

## 6. 前端

### 6.1 路由 / 导航

`src/admin/Index.tsx` 左侧导航顶层项：

```
- 攻击编排                           /admin/attack_chains          (新顶层入口)
   ├─ 链路 (chains)                  /admin/attack_chains
   ├─ 运行 (runs)                    /admin/attack_chain_runs
   ├─ 参数集 (parameter sets)        /admin/validation_parameter_sets
   └─ SOC 连接器 (status)             /admin/integrations/soc_connectors
```

旧 "Scenarios" / "Simulations" 顶层入口移除（i18n 文案彻底替换）。

### 6.2 前端文件结构

```
veriguard-front/src/admin/components/
├── attack_chains/                                   (改造原 scenarios/)
│   ├── AttackChainList.tsx
│   ├── AttackChainCreate.tsx
│   ├── AttackChainOutput.tsx
│   ├── attack_chain/
│   │   ├── AttackChainHeader.tsx
│   │   ├── AttackChainDefinition.tsx
│   │   ├── AttackChainCanvas.tsx                    (改造 ChainedTimeline)
│   │   ├── AttackChainSettingsDrawer.tsx            (新)
│   │   ├── nodes/
│   │   │   ├── EditorNode.tsx
│   │   │   ├── ConditionEdgePopover.tsx             (新)
│   │   │   └── NodeContextMenu.tsx
│   │   └── runs/
│   │       └── AttackChainRunsTab.tsx
├── attack_chain_runs/                               (改造原 simulations/)
│   ├── AttackChainRunList.tsx
│   ├── AttackChainRunOutput.tsx
│   ├── attack_chain_run/
│   │   ├── AttackChainRunHeader.tsx
│   │   ├── AttackChainRunCanvas.tsx
│   │   ├── nodes/
│   │   │   ├── DoubleLayerNode.tsx                  (新：B 方案双层卡)
│   │   │   └── NodeRunDetailDrawer.tsx
│   │   ├── VerdictBanner.tsx                        (新：链路 verdict 顶部横幅)
│   │   ├── LinkExpectationPanel.tsx                 (新)
│   │   ├── TimelineOverview.tsx                     (改造，复用)
│   │   └── LogsPanel.tsx
├── validation_parameter_sets/
│   ├── ParameterSetList.tsx
│   ├── ParameterSetEditDialog.tsx
│   └── ParameterSetOutput.tsx
└── integrations/
    └── soc_connectors/
        ├── SocConnectorStatusList.tsx
        └── SocConnectorRulePicker.tsx               (复用组件)
```

### 6.3 关键交互

#### 6.3.1 编辑器 (`AttackChainCanvas.tsx`)

- 复用 `ChainedTimeline` 的 ReactFlow 底盘 + `NodeInject` (改名 `EditorNode`)
- **新增**：边点击 → 弹 `ConditionEdgePopover`（半透明 popover，宽 320px）
  - 选 expectation dimension（PREVENTION / DETECTION / MANUAL）
  - 选状态条件（任一成功 / 任一失败 / 全成功 / 已结算 / ...）
  - 点 "+ 加条件" 嵌套 AND / OR
  - 实时校验 → 同步写 `EdgeCondition` 树
- **新增**：节点右上角 ⚙ 角标
  - 当节点 `validation_parameter_set_id` 不为空 → ⚙ 显示蓝色"已覆盖"
  - 当节点 `repeat_count > 1` → ⚙ 旁边加 `↻ x N` 标
  - 当节点配置不完整 → ⚙ 红色"未配置"
  - 点击 ⚙ → 打开 `NodeEditDrawer`，含 节点字段 + ParameterSet 覆盖选择器 + 重复执行配置
- **新增**：链路级设置 drawer（`AttackChainSettingsDrawer`）右上角按钮触发
  - execution_mode 切换（STOP_ON_BLOCK / CONTINUE）
  - 默认 ParameterSet 选择器
  - 链路级 SOC correlation rules 多选

#### 6.3.2 运行结果画布 (`AttackChainRunCanvas.tsx`)

- ReactFlow 只读模式
- 节点用 `DoubleLayerNode`：
  - **顶部色块**：节点名 + 角色，背景色按 PREVENTION 状态（绿 SUCCESS / 红 FAILED / 橙 PARTIAL / 灰 PENDING / 灰虚线 SKIPPED）
  - **底部色块**：DETECTION 状态指示（蓝 DETECTED / 灰 NOT_DETECTED / "—" SKIPPED）
  - 角标小图标：`↻ x N/M` 显示重复执行进度
- 点节点 → 右侧 drawer (`NodeRunDetailDrawer`)：节点级 expectations 列表 / 重复迭代独立展示 / SKIPPED 解释

#### 6.3.3 Verdict Banner (`VerdictBanner.tsx`)

顶部全宽横幅（参考 GitHub PR check status 设计），可折叠：

```
┌─────────────────────────────────────────────────────────────┐
│ 🟢 PREVENTION VERDICT: PARTIAL FAIL                  ⌃     │
│    3 of 5 nodes blocked · stopped on block at N3            │
│ 🔵 DETECTION  VERDICT: FULL DETECTED                        │
│    5 of 5 nodes detected + 2 of 2 link rules matched        │
└─────────────────────────────────────────────────────────────┘
```

- 红 `FULL_BREACH` / 橙 `PARTIAL` / 绿 `FULL_BLOCKED` / 灰 `PENDING` / 半透明 `N_A`
- RUNNING 时精简为单行（`Running: 4/8 settled · 1 blocked · 2 detected`）
- 点 verdict → 弹 `LinkExpectationPanel`

#### 6.3.4 Link Expectation Panel (`LinkExpectationPanel.tsx`)

侧边面板：

```
SOC Correlation Rules (链路级)
├── elastic / Lateral Movement Detected      ✓ matched (incident #4521)
├── elastic / Ransomware Kill Chain          ✗ no match (window 2h)
└── elastic / Privilege Escalation Sequence  ⏳ pending (查询中...)
```

#### 6.3.5 ParameterSet 编辑 (`ParameterSetEditDialog.tsx`)

模态对话框：name / description / defaultTargets / 数值输入 / SOC rules / tags。`is_template = true` 锁图标，编辑只能"复制后修改"。

#### 6.3.6 SOC Connector Status (`SocConnectorStatusList.tsx`)

简单表格：Connector / Display Name / Status / Available Rules / Last Health Check。

### 6.4 i18n

新增 zh-cn keys（节选）：

```json
{
  "attack_chain": {
    "title": "攻击编排",
    "list_title": "链路",
    "create": "新建链路",
    "execution_mode": {
      "stop_on_block": "拦截后停止",
      "continue": "无视拦截继续"
    },
    "verdict": {
      "full_breach": "全链路有效",
      "full_blocked": "全链路失效",
      "partial": "部分失效",
      "pending": "计算中",
      "n_a": "不适用"
    }
  },
  "attack_chain_node": {
    "repeat_count": "重复执行次数",
    "repeat_interval": "重复间隔",
    "validation_param_override": "覆盖参数集"
  },
  "validation_parameter_set": {
    "title": "参数集",
    "is_template": "系统预设"
  }
}
```

删除：所有 `scenario.*` / `simulation.*` / `lesson.*` / `email.*` 旧 keys。

### 6.5 复用现有组件

- ReactFlow `@xyflow/react`：ChainedTimeline / NodeInject / DraggableEdge 改造保留
- MUI `Drawer / Popover / Dialog / Banner` 一律复用
- `MitreFilter` / `Tag` / `Search`：复用

---

## 7. 迁移 + 测试 + 未来演进 C

### 7.1 Flyway V2 Migration（一次性大清洗）

**文件**：`veriguard-api/src/main/resources/db/migration/V2__attack_chain_module_init.sql`

```sql
-- 步骤 1：清空数据
TRUNCATE TABLE scenarios CASCADE;

-- 步骤 2：DROP 演练遗留字段
ALTER TABLE scenarios DROP COLUMN scenario_header;
ALTER TABLE scenarios DROP COLUMN scenario_footer;
ALTER TABLE scenarios DROP COLUMN scenario_mail_from;
ALTER TABLE scenarios DROP COLUMN scenario_mails_reply_to;
ALTER TABLE scenarios DROP COLUMN scenario_message_header;
ALTER TABLE scenarios DROP COLUMN scenario_message_footer;
ALTER TABLE exercises  DROP COLUMN ... (同名字段);
DROP TABLE scenarios_lessons_categories;
DROP TABLE exercises_lessons_categories;
DROP TABLE lessons_categories;

-- 步骤 3：RENAME 表
ALTER TABLE inject_dependencies   RENAME TO attack_chain_edges;
ALTER TABLE inject_expectations   RENAME TO attack_chain_node_expectations;
ALTER TABLE injects               RENAME TO attack_chain_nodes;
ALTER TABLE exercises             RENAME TO attack_chain_runs;
ALTER TABLE scenarios             RENAME TO attack_chains;

-- 步骤 4：RENAME 字段
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_scenario TO node_attack_chain_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_exercise TO node_attack_chain_run_id;
-- ... (~50 columns 整改)

-- 步骤 5：ADD 新字段
ALTER TABLE attack_chains ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'STOP_ON_BLOCK';
ALTER TABLE attack_chains ADD COLUMN validation_parameter_set_id UUID;
ALTER TABLE attack_chains ADD COLUMN soc_correlation_rules JSONB DEFAULT '[]'::jsonb;
ALTER TABLE attack_chain_nodes ADD COLUMN repeat_count INT NOT NULL DEFAULT 1;
ALTER TABLE attack_chain_nodes ADD COLUMN repeat_interval_seconds BIGINT NOT NULL DEFAULT 0;
ALTER TABLE attack_chain_nodes ADD COLUMN validation_parameter_set_id UUID;
ALTER TABLE attack_chain_runs ADD COLUMN verdict_prevention VARCHAR(32);
ALTER TABLE attack_chain_runs ADD COLUMN verdict_detection VARCHAR(32);
ALTER TABLE attack_chain_runs ADD COLUMN verdict_computed_at TIMESTAMPTZ;

-- 步骤 6：CREATE 新表
CREATE TABLE validation_parameter_sets (
    parameter_set_id UUID PRIMARY KEY,
    name TEXT UNIQUE NOT NULL,
    description TEXT,
    is_template BOOLEAN NOT NULL DEFAULT FALSE,
    default_targets JSONB DEFAULT '[]'::jsonb,
    prevention_expected_score INT NOT NULL DEFAULT 100,
    prevention_expiration_seconds INT NOT NULL DEFAULT 1800,
    detection_expected_score INT NOT NULL DEFAULT 100,
    detection_expiration_seconds INT NOT NULL DEFAULT 1800,
    soc_correlation_rules JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE validation_parameter_set_tags (...);
CREATE TABLE attack_chain_link_expectations (...);
CREATE TABLE link_expectation_traces (...);

-- 步骤 7：FK + CHECK 约束
ALTER TABLE attack_chains ADD CONSTRAINT fk_chain_param_set
    FOREIGN KEY (validation_parameter_set_id) REFERENCES validation_parameter_sets ON DELETE RESTRICT;
ALTER TABLE attack_chain_nodes ADD CONSTRAINT chk_node_owner
    CHECK ((node_attack_chain_id IS NULL) <> (node_attack_chain_run_id IS NULL));

-- 步骤 8：种子数据
INSERT INTO validation_parameter_sets (name, is_template, ...) VALUES
    ('严格', true, 100, 1800, 100, 1800, ...),
    ('宽松', true, 80, 900, 80, 900, ...),
    ('快速演练', true, 50, 300, 50, 300, ...);
```

完整 SQL 在 plan 阶段填充（约 200 行）。

### 7.2 子 PR 拆分（13 个）

| # | 内容 | 估算 |
|---|---|---|
| 0 | 机械改名 Scenario → AttackChain 等（**单独 PR，先合**） | 2 day |
| 1 | V2 Flyway migration（drop + rename + add） | 1 day |
| 2 | ValidationParameterSet entity + CRUD + 种子 | 2 day |
| 3 | ExecutionMode + StopOnBlock scheduler | 2 day |
| 4 | RepeatCount + RepeatInterval + state machine | 2 day |
| 5 | LinkVerdictCalculator + verdict fields | 1 day |
| 6 | SocAlertConnector SPI + ElasticSocConnector | 3 day |
| 7 | AttackChainLinkExpectation + 链路级集成 | 2 day |
| 8 | 前端 - 编辑器（条件 popover + ⚙ + ParameterSet drawer） | 3 day |
| 9 | 前端 - 运行画布（DoubleLayerNode + VerdictBanner + LinkExpectationPanel） | 3 day |
| 10 | 前端 - ValidationParameterSet UI | 2 day |
| 11 | 前端 - SOC Connector 状态页 | 1 day |
| 12 | i18n 清洗 + 路由更新 | 1 day |
| **总** | | **~25 day** |

> 估算单人单线开发理想耗时；并行 + review 缓冲后实际约 5-6 周。

### 7.3 测试策略

#### 单元测试（≥ 80% 覆盖）

- `EdgeConditionEvaluator.evaluate()` —— 纯函数；穷举 Eq / And / Or 嵌套树 × 各种 NodeFinalStatus 组合
- `LinkVerdictCalculator.compute()` —— 纯函数；穷举 0 分母 / 全 SUCCESS / 全 FAILED / 部分 / DETECTION 维度独立
- `NodeStateMachine` —— 状态转换表
- `RepeatCountScheduler` —— iteration 计数 + 早停
- `ValidationParameterResolver` —— 节点级覆盖 vs 链路默认 vs 系统兜底

#### 集成测试（Spring Boot + Testcontainers Postgres）

- `AttackChainSchedulerIntegrationTest` —— 启动一条 5 节点链路，mock NodeExecutor + 手动触发 expectation 上报
- `StopOnBlockIntegrationTest` —— 第一个节点拦截 → 整链停 → 后续节点 SKIPPED
- `RepeatExecutionIntegrationTest` —— 节点 5 次重复 + 第 3 次拦截 → stop_on_block 早停
- `SocAlertConnectorIntegrationTest` —— mock Elastic API 响应（WireMock）
- `V2MigrationTest` —— 启动空白 DB → 跑 V1 → 跑 V2 → 校验 schema

#### E2E（Playwright）

- `e2e/attack_chain/create-and-run.spec.ts` —— 完整链路创建 → 配 5 节点 + 3 边 → 启动 → 断言 verdict
- `e2e/attack_chain/repeat-and-stop.spec.ts` —— repeat=3 + STOP_ON_BLOCK
- `e2e/attack_chain/parameter-set-crud.spec.ts` —— 创建 + 模板复制 + FK RESTRICT
- `e2e/attack_chain/condition-popover.spec.ts` —— 边条件编辑 + 嵌套 AND/OR
- `e2e/integrations/soc-connector-status.spec.ts` —— 健康灯 + listAvailableRules

### 7.4 未来演进到 C（决策节点）

C-ready 设计要点：

| C 升级所需 | A 阶段已就绪 |
|---|---|
| ConditionNode 实体 schema | `EdgeCondition` 已是树状 sealed interface（Eq/And/Or），可直接抽出为 ConditionNode 字段 |
| ExecutorService 识别 ConditionNode | `EdgeConditionEvaluator.evaluate()` 是独立纯函数；C 阶段在 scheduler 加路由层即可 |
| 数据迁移 | edge.condition JSON → 抽到 condition_nodes 表行；纯 lift-and-shift |
| 前端 ReactFlow 节点类型 | NodeContext / 节点定义已抽象，加一个 `DecisionNode` 类型即可 |

**不需改的**：AttackChain / AttackChainNode / ValidationParameterSet / AttackChainRun / LinkExpectation / verdict 算法 / SOC 连接器 —— 全部 C-immune。

升级到 C 的工作（估算）：

1. `condition_nodes` 表 + entity (1 day)
2. V3 migration: edge → condition_node 提取 (1 day)
3. ExecutorService 路由层 (2 day)
4. 前端 DecisionNode + 工具栏 (3 day)
5. 测试 + verdict 兼容验证 (2 day)

总计 **9 day** —— 远低于"从 0 做 C 方案"的 4-6 周。

### 7.5 风险登记

| 风险 | 严重 | 缓解 |
|---|---|---|
| 机械改名 PR diff 巨大，review 难 | 高 | 用 IDE 自动化 + diff sample review + 跑全套测试验证语义不变 |
| TRUNCATE 数据丢失 | 中 | 用户已确认 path b；dev/test 环境无生产数据 |
| Flyway V2 在某环境失败 | 中 | 先在 192.168.2.124 跑通 + dev / test 各跑一遍 + 备份脚本 |
| 链路 100+ 节点性能不达标 | 低 | BAS 场景实际链路 < 30 节点；监控关注 ReactFlow 渲染 + verdict 计算 |
| SOC 连接器凭证泄漏 | 中 | 走 env，不入库；checkHealth 不返回凭证 |
| Elastic API 变化导致 connector 失效 | 中 | 用官方 client；契约测试覆盖 connector 边界 |
| 改名同时改语义引入 bug | 高 | 严格分 Task 0（纯改名）+ 后续 task；Task 0 必须先合 |
| 重复执行 + stop_on_block 边界 bug | 中 | 集成测试覆盖 5+ 边界场景；状态机加日志便于复盘 |

---

## 附录 A：命名映射表

| 旧名 | 新名 |
|---|---|
| Scenario | AttackChain |
| Exercise（后端） / Simulation（前端） | AttackChainRun |
| Inject | AttackChainNode |
| Injector | NodeExecutor |
| InjectorContract | NodeContract |
| InjectStatus | AttackChainNodeStatus |
| InjectExpectation | AttackChainNodeExpectation |
| InjectExpectationTrace | NodeExpectationTrace |
| InjectExpectationResult | NodeExpectationResult |
| InjectExpectationSignature | NodeExpectationSignature |
| InjectDependency | AttackChainEdge |
| InjectModelHelper | NodeModelHelper |
| ExecutableInject | ExecutableNode |
| ScenarioInjectApi | AttackChainNodeApi |
| ScenarioToExerciseService | AttackChainRunFactory |
| `inject_scenario` (FK) | `node_attack_chain_id` |
| `inject_exercise` (FK) | `node_attack_chain_run_id` |
| `/api/scenarios/*` | `/api/attack_chains/*` |
| `/api/injects/*` | `/api/attack_chain_nodes/*` |
| `/api/exercises/*` | `/api/attack_chain_runs/*` |
| `/admin/scenarios` | `/admin/attack_chains` |
| `/admin/simulations` | `/admin/attack_chain_runs` |

---

## 附录 B：PRD §2.4 要求映射

| PRD 原子要求 | 本设计实现 |
|---|---|
| 可视化路径图配置 | 改造 `ChainedTimeline.tsx` 为 `AttackChainCanvas.tsx`；ReactFlow + EditorNode |
| 节点配具体用例 | `AttackChainNode.node_contract_id` FK NodeContract（原 InjectorContract）|
| 节点延迟 / 重复执行（次数 + 间隔）| `depends_duration` (KEEP) + `repeat_count` / `repeat_interval_seconds` (NEW) |
| 节点间执行逻辑（按上一节点拦截结果）| `AttackChainEdge.condition` (Eq/And/Or)；`EdgeConditionEvaluator` |
| 全局 / 节点级验证参数 | `ValidationParameterSet` 实体；`AttackChain.validation_parameter_set_id` (默认) + `AttackChainNode.validation_parameter_set_id` (覆盖) |
| 执行模式：拦截后继续 / 停止 | `AttackChain.execution_mode` enum；`AttackChainScheduler` stop-on-block 逻辑 |
| SOC 告警规则匹配 + 配置条件 | `SocAlertConnector` SPI；`SocCorrelationRuleRef` 配置；链路级 + 节点级双层 |
| 路径图展示执行详情（拦截 + 检测） | `AttackChainRunCanvas.tsx` + `DoubleLayerNode`（顶 PREVENTION + 底 DETECTION） |
| 时间轴执行详情 | `TimelineOverview.tsx` 改造复用 |
| 链路结果分类（全 / 失 / 部分） | `AttackChainRun.verdict_prevention / verdict_detection`；`LinkVerdictCalculator` 计数法 |

---

## 附录 C：决策日志

| # | 提问 | 选项 | 用户选择 | 理由 |
|---|---|---|---|---|
| 0 | A/B/C 路径选择 | A 独立新建 / B 复用扩展双语义 / C 改造 Scenario | C | 与 path b 配合最干净；不 sync upstream 后 C 成本归零 |
| 0a | §2.3 自定义验证安置 | C-1 同表 / C-2 拆出 | C-2 | 两个 PRD 模块逻辑不同，拆出独立 `custom_test_cases` 模块 |
| 1 | 本次 spec 范围 | (a) 仅 §2.4 / (b) §2.4 + §2.3 / (c) 三模块齐做 | a | 一个 spec 一件事，可执行性最高 |
| 2 | 节点"被拦截"定义 | (a) 仅 PREVENTION / (b) PREVENTION ∨ DETECTION / (c) 节点级声明 | a | block ≠ detect，verdict 维度必须分开 |
| 3 | 链路 verdict 算法 | (a) 计数法 / (b) 关键节点法 / (c) 终点法 | a | 直观可解释，YAGNI 不引入关键节点配置 |
| 4 | stop-on-block 边界 | (a) 整链停 / (b) 当前分支停 / (c) 任务+节点级标记 | a | PRD 字面默认整链；verdict 解释最干净 |
| 5 | 验证参数集存储 | (a) 列模式 / (b) JSON / (c) 独立实体 | c | 用户问"实现彻底"答 c；可命名复用、可批量改 |
| 6 | 重复执行语义 | (a) 累加 + 任意拦截即停 / (b) 末次为准 / (c) 用户配置聚合 | a | 复用 expectationGroup 机制；客户直觉对齐 |
| 7a | 目标 SOC 平台 | (i) Splunk / (ii) Elastic / (iii) QRadar / (iv) 自研 / (v) 抽象+示例 | v | 客户平台未定，抽象 + Elastic 作 reference |
| 7b | SOC 告警匹配粒度 | (i) 仅链路级 / (ii) 仅节点级 / (iii) 链路+节点 | iii | 节点级现有；链路级 PRD 必须；都做信息最完整 |
| 8 | 条件分支 UI | A 边标签 / B 双 port / C 决策节点 | A | 复用现有 ReactFlow 模型；C 留作未来演进 |
| 9 | 改名彻底程度 | (i) 仅 UI / (ii) API 层 / (iii) 全部改名 | iii | 已不 sync upstream，干净彻底优先 |
| 10 | 数据迁移策略 | (a) 破坏性重置 / (b) 大清洗 / (c) 软迁移 | b | dev/test 无生产数据，最干净 |
| 11 | Inject 命名 | α 保留 / β 改 AttackChainNode | β | 一致命名风格优先 |
| 12 | 路径图结果叠加 | A 边框+角标 / B 双层卡 / D 边框+ribbon | B | 信息密度最高，两维度对等 |
| 13 | Verdict 显示位置 | (i) 横幅 / (ii) 浮层 / (iii) 侧栏 / (iv) 底部 | i | 第一眼看到 + 业界惯例 + 全宽空间 |

---

## 文档历史

| 日期 | 作者 | 变更 |
|---|---|---|
| 2026-05-07 | brainstorm 协同 | 初稿，分 7 节通过 review |
