# PRD 满足度审计

> 审计日期：2026-05-07
> 审计源：`docs/prd/产品要求.md`（§2.1–§2.5，共 24 条原子要求）
> 审计仓库 HEAD：见 `git log` 当前分支

按 PRD §2 每条原子要求逐项对照当前系统。状态分级：

- ✅ **满足**：当前系统已实现
- 🟡 **部分**：底层数据/能力存在，但功能链路或 UI 不完整
- ❌ **未满足**：核心能力缺失

---

## §2.1 流量安全验证（4 项｜0 ✅ / 1 🟡 / 3 ❌）

| 要求 | 状态 | 现状 / 缺口 |
|---|---|---|
| 网络边界流量覆盖度验证（按资产 + 策略） | 🟡 | 数据模型有 `Asset` / `SecurityPlatform`（NTA/IDS 抽象），但**无覆盖度矩阵 UI**；需新建"资产 × 策略命中度"视图 + 流量探针对接 |
| 流量安全设备稳定性趋势图 | ❌ | Custom Dashboards 框架在，但**无稳定性 widget**；需建模"周期任务跑同样用例 → 检出率折线" |
| 验证 NTA/IDS 对各类流量攻击防护（≥300 用例 / ≥10 类） | ❌ | 现有 inject 库只有 `Manual` + `Veriguard Implant` + dummy injectors（nmap / nuclei / email 都是种子假数据）；**无真正的流量回放引擎**与对应用例库；payload `NetworkTraffic` 类型存在但仅为 schema |
| 同一用例内多端口四元组 | ❌ | `NetworkTraffic` payload 没看到多元组字段；需扩 schema |

**结论**：流量验证模块**几乎全空** —— 核心是缺**流量回放引擎**（外部适配器）+ 用例库。

---

## §2.2 应用与服务器安全验证（1 项｜0 ✅ / 1 🟡 / 0 ❌）

| 要求 | 状态 | 现状 / 缺口 |
|---|---|---|
| 验证 HIDS 对主机攻击防护（≥300 用例 / ≥10 类） | 🟡 | **架构在**：Atomic Testing 模块 + Veriguard agent + Caldera / CrowdStrike / SentinelOne / Tanium executor；payload 已支持 Command / Executable / FileDrop / DnsResolution；ATT&CK + KillChainPhase 数据模型完整。**缺**：(1) 实际部署的 HIDS 告警采集 collector（`collectors/` 只剩 expectations_* 两个工具型，没有 HIDS-specific）；(2) 沉淀好的 ≥300 主机攻击用例库（当前 payload 表只 11 条 Command） |

**结论**：底盘最完整的一块，差**用例内容**和**告警采集适配器**。

---

## §2.3 自定义验证（5 项｜2 ✅ / 2 🟡 / 1 ❌）

| 要求 | 状态 | 现状 / 缺口 |
|---|---|---|
| 自定义场景 + 指定用例执行顺序 | ✅ | Scenario + ordered injects + `InjectDependency` |
| ATT&CK + 纵深防御维度自动划分 | 🟡 | `AttackPattern` + `KillChainPhase` 数据已挂在 InjectorContract 上，前端 `MitreFilter` 可筛选；**缺**按 ATT&CK / 防御层自动分组视图 |
| 批量导入用例生成场景 | ✅ | `Importer` + `ScenarioImportApi` + xls/zip 导入 |
| 6 种自定义用例类型 | 🟡 | 已支持：✅ 命令执行 / ✅ 上传可执行文件并执行；缺：❌ 构造 web 攻击包（无 HTTP 请求构造器）/ ❌ 上传 pcap 流量包（无 pcap injector）/ 🟡 上传样本文件（沙箱预设有，但缺"样本→沙箱执行" inject 类型）/ ❌ 配置邮件形式（Phase 11.5 已删 Email Injector） |
| 全量用例筛选 → 创建场景 + 动态场景 | ❌ | 当前是手动 add inject 静态列表；缺 saved-query 式动态场景机制 |

---

## §2.4 攻击编排（10 项｜0 ✅ / 4 🟡 / 6 ❌）

| 要求 | 状态 | 现状 / 缺口 |
|---|---|---|
| 可视化路径图配置 | ❌ | 当前 Scenario 是平铺 inject 列表（前端有 `@xyflow/react` 库但仅用于 Atomic 结果展示），**编辑场景没有图形化画布** |
| 节点配具体用例 | 🟡 | inject 即节点，能配 InjectorContract；但绑在线性列表里 |
| 节点延迟 / 重复执行 | 🟡 | inject 有 `inject_depends_duration` 偏移；**重复执行无原生支持** |
| 节点间执行逻辑（按上一节点拦截结果） | 🟡 | `InjectDependencyConditions` 有 `eq` / `and` / `or` —— 能表达"前驱 expectation 结果 == X"，但只是数据模型，UI 没有"分支 IF/ELSE"编辑器 |
| 全局 / 节点级验证参数 | ❌ | Scenario 有"reply_to"等几个全局字段，**没有"验证参数集"概念**；inject 的 expectations 是单条配置，无全局批改 |
| 执行模式：拦截后继续 / 停止 | ❌ | Scenario 没有这个开关；执行器目前都是 best-effort 全跑 |
| SOC 告警规则匹配 | ❌ | DETECTION 类 expectation 存在，但**无 SOC 系统对接 collector**，无"告警规则匹配条件"配置入口 |
| 路径图展示执行详情（拦截 + 检测） | ❌ | 仅 Atomic 详情有 ReactFlow；Scenario 执行结果是表格 |
| 时间轴执行详情 | 🟡 | Simulation 详情有 `TimelineOverview`，能展示 inject 时间 + 状态；**未对应"攻击编排链路"语义** |
| 链路结果分类（全 / 失 / 部分） | ❌ | 只有 inject 维度的 expectation 状态聚合；无链路级 verdict |

**结论**：核心**未实现** —— 当前所谓"场景"是 OpenBAS 的剧本概念，PRD 攻击编排需要新建一个独立模块（带图形画布 + 条件分支 + SOC 集成 + 链路级 verdict）。

---

## §2.5 沙箱管理（4 项｜2 ✅ / 1 🟡 / 1 ❌）

| 要求 | 状态 | 现状 / 缺口 |
|---|---|---|
| 沙箱 CRUD | ✅ | `SandboxApi` + `SandboxService` + 前端 `SandboxList` / `SandboxDialog`（M1 完工） |
| 配置网络访问控制策略 | ✅ | `NetworkRuleEditor` + `SandboxNetworkRule` + iptables / routing.conf 导出 |
| 用例执行完成后自动还原 | 🟡 | M1 有 `auto_revert` 字段 + 校验；**实际还原由沙箱驱动负责，当前 `NotImplementedSandboxDriver` 占位**（M2/M3 接 CAPEv2 时落地） |
| 用沙箱执行真实恶意样本（勒索 / 挖矿 / 蠕虫…） | ❌ | M1 是控制平面，**无样本执行链路**：缺 (1) 样本上传 inject 类型；(2) 真实虚拟化驱动；(3) 样本→沙箱执行→报告回采 pipeline |

---

## 汇总

| 模块 | 总项 | ✅ | 🟡 | ❌ | 备注 |
|---|---|---|---|---|---|
| §2.1 流量验证 | 4 | 0 | 1 | 3 | 缺流量回放 + NTA/IDS 采集 |
| §2.2 主机验证 | 1 | 0 | 1 | 0 | 底盘最全，差 HIDS 采集 + 用例内容 |
| §2.3 自定义验证 | 5 | 2 | 2 | 1 | 缺 3/6 inject 类型 + 动态场景 |
| §2.4 攻击编排 | 10 | 0 | 4 | 6 | 整体未实现，需新建模块 |
| §2.5 沙箱管理 | 4 | 2 | 1 | 1 | 控制平面 done，执行链路 0 |
| **合计** | **24** | **4** | **9** | **11** | **总满足率 17%** |

---

## 关键缺口归类

### 1. 外部适配器（不在代码里写假数据，必须接真设备）

- 流量回放引擎（§2.1）
- NTA/IDS 告警采集 collector（§2.1）
- HIDS 告警采集 collector（§2.2）
- SOC 告警匹配 collector（§2.4）
- 沙箱虚拟化驱动 CAPEv2（§2.5）

### 2. UI 视图层缺失

- 边界覆盖度矩阵 / 稳定性趋势图（§2.1）
- ATT&CK / 纵深防御分组视图（§2.3）
- 攻击编排路径图编辑器（§2.4）
- 链路结果可视化 + verdict（§2.4）

### 3. 数据 / 逻辑模型扩展

- `NetworkTraffic` 多元组（§2.1）
- HTTP 请求 / pcap / 邮件 inject 类型补回（§2.3）
- 动态场景（saved-query 机制）（§2.3）
- 节点重复执行 / 拦截后停止 / 验证参数集（§2.4）

### 4. 用例内容沉淀（数据，不是代码）

- 流量侧 ≥300 用例 / ≥10 类
- 主机侧 ≥300 用例 / ≥10 类
- 沙箱样本库（勒索 / 挖矿 / 蠕虫等 8 类）

---

## 路线建议

如果要继续二开，推荐拆成以下独立可交付的 spec / plan：

1. **§2.4 攻击编排** —— 完整新模块（最大块）
2. **§2.3 自定义验证补全** —— 补回 3 个 inject 类型 + 动态场景 + ATT&CK 视图
3. **§2.5 沙箱执行链路** —— Sandbox M2/M3（CAPEv2 接入 + 样本上传 inject）
4. **§2.1 / §2.2 外部适配器集成** —— 视具体目标安全设备（哪家 NTA/IDS/HIDS/SOC）单独做
5. **覆盖度 + 稳定性 dashboards** —— 两个独立 widget，可走 `custom_dashboards` 框架

每一项进入设计阶段时再单独走 brainstorming → spec → plan 流程。
