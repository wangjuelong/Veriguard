# IPv6 安全验证系统 — GAP 分析

> 来源：`docs/superpowers/specs/2026-05-12-ipv6-security-validation-refinement-design.md`
> 日期：2026-05-12
> 受众：项目实施方、研发资源协调、架构评审
> 用法：每条招标要求对应一行；分类标记是否需要补开发；后置链接到 [`IPv6安全验证系统-研发拆解.md`](IPv6安全验证系统-研发拆解.md) 中相应小节

---

## 1. 总览

### 1.1 满足程度统计

| 状态 | 含义 | 条数 |
| --- | --- | --- |
| ✅ 已满足 | Veriguard 当前实现已能满足，无须新增功能 | 6 |
| 🟡 部分满足 | 主体能力具备，但需补少量逻辑 / UI / 数据 | 12 |
| 🔴 未实现 | 需新增模块 / 新增大功能 | 7 |
| **合计** | | **25** |

### 1.2 Veriguard 当前实现快照（截至 2026-05-12）

| 模块 | 状态 | PR / Phase |
| --- | --- | --- |
| 攻击编排 PRD §2.4 全 | ✅ 已完成 | 已合 main |
| §2.3 B-i 用例筛选 | ✅ 已完成 | 已合 main |
| §2.3 B-iii 动态用例集联动 + follow-up | ✅ 已完成 | 已合 main |
| §2.3 B-ii PR-B 邮件 inject | 🟡 进行中 | PR #36（39 测试过 / base=main） |
| §2.3 B-ii PR-C/D（pcap / sample） | ⏳ 排队 | phase 12c 后续 |
| 沙箱管理（§14） | 🟡 设计中 | 需求阶段 |
| 用例契约 + ATT&CK + 杀伤链 + 标签 | ✅ 已完成 | 已合 main |
| ATT&CK 矩阵视图 | ✅ 已完成 | 已合 main |
| ChainedTimeline（编辑 + 运行） | ✅ 已完成 | 已合 main |

### 1.3 模块级 GAP 分布

| 模块 | ✅ | 🟡 | 🔴 |
| --- | --- | --- | --- |
| §3 边界防护验证（6） | 0 | 3 | 3 |
| §4 流量安全验证（4） | 0 | 2 | 2 |
| §5 应用与服务器安全验证（1） | 0 | 1 | 0 |
| §6 自定义验证（3） | 1 | 2 | 0 |
| §7 攻击编排（8） | 5 | 2 | 1 |
| §8 沙箱管理（3） | 0 | 3 | 0 |
| **合计** | **6** | **12** | **7** |

---

## 2. 按模块 GAP 分析

### 2.1 §3 边界防护验证

#### 3.1 网络边界防护覆盖度验证 + URL 对比

**🔴 未实现**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 覆盖度验证 + URL 级覆盖 + 两次对比 diff（提供功能截图） |
| Veriguard 现状 | 攻击编排可发起验证；用例库可分类。但 (URL × 策略) 覆盖矩阵、两次任务对比视图、策略名称作为独立维度均**不存在** |
| GAP | 1. URL 资产模型未独立（当前资产以 IP 为主）<br>2. 策略实体未独立（无 `policy` 表）<br>3. 覆盖矩阵数据模型 + 计算管线缺失<br>4. 两次对比 diff UI 缺失 |
| 补开发清单 | 1. 新增 `policy` 实体 + URL 资产支持<br>2. 新表 `boundary_coverage_baseline` / `boundary_coverage_run` / `boundary_coverage_result`<br>3. 覆盖度任务调度器：发起验证 → 反查 NxSOC → 写矩阵<br>4. 矩阵视图 UI + 对比页 UI<br>5. 截图素材准备 |

---

#### 3.2 安全设备策略有效性常态化监控

**🔴 未实现**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 配置基线 + 调度 + 日 / 小时趋势可视化 |
| Veriguard 现状 | Quartz 调度框架 ✅；但"常态化监控任务"作为独立场景概念**不存在** |
| GAP | 1. 监控任务的调度元模型缺失<br>2. 日 / 小时双视图趋势可视化 UI 缺失<br>3. 单策略下钻视图缺失 |
| 补开发清单 | 1. 新表 `boundary_monitoring_job`（cron + baseline + asset_set + device）<br>2. 趋势数据落 Elasticsearch `boundary-monitoring-history`<br>3. Recharts 时间序列 UI（按日 / 按小时切换）<br>4. 单策略下钻路径 |

---

#### 3.3 ★1 边界与流量设备稳定性

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 重复执行 + 一次稳定性结果 + 稳定性趋势图（需提供功能截图） |
| Veriguard 现状 | ✅ 节点重复执行配置（攻击编排 §2.4）；🔴 稳定性聚合 + 趋势图 |
| GAP | 1. 缺"稳定性指标计算"逻辑（命中率 = 命中次数 / N）<br>2. 缺稳定性趋势图视图<br>3. 缺稳定性数据快照模型 |
| 补开发清单 | 1. 在 `attack_chain_run` 基础上补 `stability_metric` 计算列<br>2. 新表 `stability_trend_snapshot`（每次任务一个点）<br>3. 稳定性趋势图 UI（任务点 + 按天聚合切换）<br>4. ★1 截图素材准备 |

---

#### 3.4 已知高危漏洞利用及绕过

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 模拟主流 web / 安全产品 / CMS / OS / DB 等高危漏洞利用，检验防护工具是否检出 |
| Veriguard 现状 | ✅ 用例契约模型；🟡 8 大软件类用例数据需扩充 |
| GAP | 1. 用例契约缺 `software_category` 字段<br>2. 8 大软件类高危漏洞 PoC 用例数据不足 |
| 补开发清单 | 1. 用例契约新增 `software_category` 枚举字段（8 类）<br>2. 用例库初始数据扩充：8 大软件类 ≥100 条 PoC<br>3. 用例库筛选 UI 补该维度 |

---

#### 3.5 WAF/IPS 12 类 ≥500 用例

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 12 类边界攻击 + 总用例数 ≥500 OR 攻击类型 ≥10 种 |
| Veriguard 现状 | ✅ 用例契约 + 12 类分类机制；🔴 用例数量盘点（很可能不足 500） |
| GAP | 1. 12 类全覆盖确认（盘点现有用例）<br>2. 用例数量缺口待研发盘点后定 |
| 补开发清单 | 1. **盘点任务**：清点现有用例按 12 类分布<br>2. 用例库扩充至 ≥500 条（重点补缺口类别）<br>3. 命名规范统一：`<attack_category>-<target_software>-<variant>` |

---

#### 3.6 ★2 动态攻击组合 + 聚类分级

**🔴 未实现**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 250 攻击类型 × 30 000 组合 + (设备, 资产) 聚类 + 分级（需提供功能截图） |
| Veriguard 现状 | 0 实现 |
| GAP | 1. 攻击组合生成器：250 × 120 绕过 ≈ 30 000<br>2. 绕过维度库（120 项）<br>3. 30 000 组合的限流并发执行调度<br>4. 聚类算法（按设备 + 资产维度）<br>5. 分级标准（4 级默认 + 客户可配置）<br>6. 下钻到 payload 级 UI |
| 补开发清单 | 1. 新模块 `attack_combination`<br>2. 新表：`bypass_dimension_template`、`attack_combination_template`、`attack_combination_run`、`attack_combination_result`<br>3. 绕过维度库初始数据（120 项）<br>4. 限流并发调度器（避免对设备过载）<br>5. 聚类计算 + 分级算法<br>6. 聚类视图 + 下钻 UI<br>7. ★2 截图素材准备 |
| 工作量评级 | **大**（独立子模块，开发 + 验证 ≥ 3 周） |

---

### 2.2 §4 流量安全验证

#### 4.1 网络边界流量覆盖度验证

**🔴 未实现**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 流量资产 × 策略覆盖矩阵（提供功能截图） |
| Veriguard 现状 | 与 §3.1 同：流量验证用例 ✅；覆盖矩阵 + 策略维度 🔴 |
| GAP | 与 §3.1 相同，但资产粒度为 IP；判定链路走 NTA/IDS → NxSOC |
| 补开发清单 | 1. 与 §3.1 共用数据模型（`coverage_type ∈ {boundary, traffic}` 区分）<br>2. 流量覆盖度页签 UI（4 态单元格）<br>3. 判定链路集成：攻击机 → NxSOC<br>4. 截图素材准备 |

---

#### 4.2 ★3 流量设备稳定性

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 流量设备稳定性趋势图（需提供功能截图） |
| Veriguard 现状 | 与 §3.3 同 |
| GAP | 与 §3.3 同，但被测设备为 IDS/IPS（蓝盾 BDIPS / 国基华电 GJsec-SIDS）/ 天眼探针 |
| 补开发清单 | 与 §3.3 共用稳定性引擎；区别仅在被测设备 |

---

#### 4.3 NTA/IDS 11 类 ≥300 用例

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 11 类攻击 + ≥300 用例（双满足）+ 11 类全部 IPv6 单栈可跑 |
| Veriguard 现状 | ✅ 流量验证用例契约；🟡 11 类盘点 + 数量；🔴 IPv6 单栈版本覆盖度未知 |
| GAP | 1. 11 类全覆盖盘点<br>2. 用例数量缺口<br>3. 每条用例的 IPv6 单栈版本可跑性验证 |
| 补开发清单 | 1. **盘点任务**：现有流量用例按 11 类分布<br>2. 用例扩充至 ≥300 条 × IPv6 双版本<br>3. 用例契约补 `network_protocol_family` 字段 |

---

#### 4.4 单用例多端口四元组

**🔴 未实现**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 单 pcap 用例支持多组 (srcIP, dstIP, srcPort, dstPort)，dstPort 不同 |
| Veriguard 现状 | 🟡 B-ii pcap inject 在 phase 12c 后续；多四元组声明 + 端口改写 🔴 |
| GAP | 1. pcap 用例契约多组四元组建模<br>2. pcap 回放层支持端口改写<br>3. 顺序 / 并发执行模式<br>4. 间隔配置 |
| 补开发清单 | 1. pcap 用例契约新增 `port_tuples: list[...]`<br>2. pcap 回放层（基于 tcpreplay / 自研）实现端口改写<br>3. 顺序模式：循环 + 间隔；并发模式：线程池<br>4. 单用例上限 64 组校验<br>5. 接进 §6.2 pcap_inject 类型 |

---

### 2.3 §5 应用与服务器安全验证

#### 5.1 HIDS 12 类 ≥300 用例 + 自有 Agent + 自动还原

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| 招标要求 | 12 类主机攻击 + ≥300 用例 + IPv6 适配 + 自动还原 |
| Veriguard 现状 | ✅ command_inject + binary_inject（B-i）；🔴 自有验证 Agent；🟡 12 类用例 + 网络通信类 IPv6 适配；🔴 自动还原机制 |
| GAP | 1. **平台自有轻量验证 Agent**（独立模块）<br>2. 12 类用例数据扩充<br>3. 网络通信类用例 IPv6 单栈适配<br>4. 用例执行后自动还原机制（删落盘 / 回滚注册表 / 杀进程 / 清持久化）<br>5. 靶机白名单机制（执行硬约束） |
| 补开发清单 | 1. 新模块 `veriguard-agent`（独立项目，可能跨多 PR）<br>2. Agent 与平台通信协议（TLS over IPv6）<br>3. 12 类主机用例数据扩充 ≥300 条<br>4. 用例契约新增 `rollback_steps` + `network_dependent` + `target_os` 字段<br>5. 还原引擎：解析 `rollback_steps` 在 Agent 端执行<br>6. 靶机白名单：`target_machine_whitelist` 表 + 执行前校验<br>7. 验证 Agent 干扰评估文档（提交甲方批准） |
| 工作量评级 | **大**（验证 Agent 是独立模块） |

---

### 2.4 §6 自定义验证

#### 6.1 自定义场景 + ATT&CK + 纵深防御 + 批量导入

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| 招标要求 | DAG 编排 + 执行顺序 + ATT&CK 划分 + 纵深防御维度划分 + 批量导入 |
| Veriguard 现状 | ✅ DAG 编排（攻击链路 §2.4）；✅ ATT&CK 矩阵视图；🔴 纵深防御 5 层视图；🟡 批量导入（单条 Excel 导入 ✅，"导入即场景"待补） |
| GAP | 1. 用例契约缺 `defense_layer` 字段<br>2. 纵深防御 5 层视图 UI 缺失<br>3. 批量导入"导入即场景"流程未串通 |
| 补开发清单 | 1. 用例契约新增 `defense_layer ∈ {boundary, traffic, application, host, data}` 枚举字段<br>2. 现有用例补 `defense_layer` 标注<br>3. 场景详情新增"纵深防御"tab，5 列 × N 行可视化<br>4. 批量导入：导入临时表 `case_import_batch` → 派生 attack_chain<br>5. 一次导入 → 一个场景的流程 UI |

---

#### 6.2 6 类自定义用例 inject_type

**🟡 部分满足**

| inject_type | 招标类型 | 当前状态 | GAP |
| --- | --- | --- | --- |
| `http_inject` | 构造 web 攻击包 | 🔴 未实现 | 新增 inject 类型 + 编辑器（method/URL/headers/body/cookies/query）+ HTTPS 容忍 |
| `pcap_inject` | 上传 pcap 流量包 | 🟡 B-ii 在 phase 12c 后续 | 完成 pcap 上传 + 多四元组 + 端口改写 |
| `sample_inject` | 上传样本文件 | 🟡 沙箱模块在做 | 与 §8 wangjuelong/cape 对接完成后承接 |
| `command_inject` | 配置执行命令 | ✅ B-i 完成 | - |
| `binary_inject` | 上传可执行 + 命令 | ✅ B-i 完成 | - |
| `email_inject` | 配置邮件 | 🟡 PR #36 进行中 | 完成 PR #36；测试 SMTP 对接（甲方提供服务器） |

**补开发清单**
1. 新 inject 类型 `http_inject` + 编辑器（最大工作量在此条）
2. 配合 PR #36 完成邮件 inject
3. 配合 B-ii 后续 PR 完成 pcap_inject
4. 配合沙箱模块完成 sample_inject 链路

---

#### 6.3 筛选生成场景 + 动态联动

**✅ 已满足**

| 项 | 内容 |
| --- | --- |
| Veriguard 现状 | B-iii 动态用例集联动 + follow-up 全完成 |
| 待确认细节 | 1. filter 6 维（属性 / 标签 / ATT&CK / 杀伤链 / 防御层 / 自定义字段）是否齐全<br>2. 「动态 filter 场景」与「手工枚举场景」两种共存的 UI 标识是否清晰<br>3. 用例库变更通知（UI badge）是否实现 |
| 微调项 | filter 维度中 `defense_layer` 需在 §6.1 实现后回头接入 filter |

---

### 2.5 §7 攻击编排

| 条 | 状态 | 摘要 |
| --- | --- | --- |
| 7.1 可视化路径图 | ✅ 已满足 | ChainedTimeline 编辑画布 |
| 7.2 节点延迟 / 重复 | ✅ 已满足 | 节点执行配置已具备 |
| 7.3 节点间执行逻辑 | ✅ 已满足 | 条件分支已具备；需确认 condition 枚举完整（on_blocked / on_passed / always） |
| 7.4 ★4 全局 + 节点参数 + 两种模式 | ✅ 已满足 | 基本具备 |
| 7.5 SOC 关联告警 + 6 维匹配 | 🟡 部分满足 | SOC 关联 ✅；6 维匹配条件 + 节点级覆盖 🔴 |
| 7.6 可视化运行画布 | ✅ 已满足 | ChainedTimeline 运行画布 |
| 7.7 时间轴 | ✅ 已满足 | ChainedTimeline 时间轴模式 |
| 7.8 三态结果 | 🟡 部分满足 | 节点级状态 ✅；链路级三态聚合 🔴 |

#### 7.4 ★4 补开发清单
- UI 文案统一：执行模式 radio 命名为「全程执行」/「拦截即停」
- 节点级参数覆盖全局参数的 UI 体验
- 截图素材准备

#### 7.5 补开发清单
- 新表 `soc_match_condition`（6 维：资产 IP / 时间窗 / 严重度 / 规则 ID / 规则类别 / 关键字）
- 任务级 SOC 匹配编辑器 UI
- 节点级 SOC 匹配覆盖 UI
- 蓝盾 NxSOC API 适配层补 6 维查询能力

#### 7.8 补开发清单
- `attack_chain_run` 新增 `chain_result ∈ {fully_effective, partially_failed, fully_failed}` 字段
- 三态计算引擎：聚合所有节点 result_state
- 任务列表 / 详情 / 仪表板新增三态徽标
- 三态作为筛选维度

---

### 2.6 §8 沙箱管理

#### 8.1 真实恶意样本执行

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| Veriguard 现状 | 沙箱模块在做 |
| GAP | 1. wangjuelong/cape 对接适配器<br>2. 8 类样本场景测试用例数据<br>3. 样本任务下发 + 报告轮询 + 回传 + 解析 |
| 补开发清单 | 1. 新模块 `veriguard-api/src/main/java/io/veriguard/integration/sandbox/cape/`<br>2. CAPE API 客户端封装（任务 / 状态 / 报告 / 网络策略 / Snapshot）<br>3. 报告可视化 UI（行为 / 网络回连 / 文件落地）<br>4. 8 类样本测试用例（需获取或构造） |

---

#### 8.2 沙箱平台 CRUD + 网络访问控制（**截图否则无效**）

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| Veriguard 现状 | 沙箱基础 CRUD 设计中 |
| GAP | 1. 沙箱平台 / 实例 CRUD<br>2. 网络访问控制 4 维：白 / 黑 / 协议 / 速率<br>3. 网络策略下发到 CAPE 的 VM 网络配置 |
| 补开发清单 | 1. 新表 `sandbox_platform` + `sandbox_network_policy`<br>2. CRUD UI（表格 + 增删改 + 详情）<br>3. 网络策略编辑器（4 维 tab）<br>4. CAPE API 适配（网络规则下发）<br>5. 截图素材准备（**否则视为无效**） |

---

#### 8.3 用例执行后自动还原

**🟡 部分满足**

| 项 | 内容 |
| --- | --- |
| Veriguard 现状 | 沙箱模块在做；自动还原机制设计中 |
| GAP | 1. 默认每条用例后还原；可切任务级<br>2. VM Snapshot 回滚集成<br>3. 还原失败处理（标 unavailable + 告警 + 重排） |
| 补开发清单 | 1. `sandbox_task.rollback_strategy ∈ {per_case, per_task}` + `rollback_status`<br>2. `sandbox_platform_instance.availability` 字段<br>3. CAPE Snapshot 回滚 API 集成<br>4. 还原失败告警 + 重排逻辑<br>5. 截图素材准备 |

---

## 3. 跨模块新增模块清单

下列是 GAP 分析中识别出的**全新模块** —— 不是改造现有代码，而是新增独立子项目 / 包：

| # | 新增模块 | 优先级 | 工作量评级 | 关联条款 |
| --- | --- | --- | --- | --- |
| 1 | `veriguard-agent`（平台自有验证 Agent，独立子项目） | 高 | 大（≥ 3 周） | §5.1 |
| 2 | `boundary_coverage` 子模块（边界覆盖度矩阵 + URL 资产 + 策略实体） | 高 | 中（≥ 2 周） | §3.1 / §4.1 |
| 3 | `attack_combination` 子模块（30 000 组合生成 + 聚类 + 分级） | 高 | 大（≥ 3 周） | §3.6 ★2 |
| 4 | `boundary_monitoring`（常态化监控调度） | 中 | 小（≥ 1 周） | §3.2 |
| 5 | `stability_engine`（稳定性计算与趋势） | 中 | 小（≥ 1 周） | §3.3 ★1 / §4.2 ★3 |
| 6 | `nxsoc_adapter`（蓝盾 NxSOC 接口适配，6 维查询） | 高 | 中（≥ 2 周；依赖 NxSOC 文档） | §3.1 / §4.1 / §5.1 / §7.5 |
| 7 | `cape_adapter`（wangjuelong/cape 沙箱适配） | 高 | 中（≥ 2 周；依赖 cape 项目对齐） | §8.1 / §8.2 / §8.3 |
| 8 | `http_inject` 类型（web 攻击包编辑器） | 中 | 小（≥ 1 周） | §6.2 |
| 9 | 用例库扩充（多模块共享） | 中 | 大（≥ 3 周，用例构造工作量集中） | §3.4 / §3.5 / §4.3 / §5.1 |

---

## 4. 工作量初评与排期建议

### 4.1 关键路径（按依赖排序）

```
Phase A（前置基建，2-3 周）
  ├─ nxsoc_adapter（依赖 NxSOC 文档 → 待澄清 #1）
  ├─ cape_adapter（依赖 wangjuelong/cape API → 项目对齐）
  └─ 用例契约字段扩充：software_category / defense_layer / network_protocol_family / target_os / rollback_steps

Phase B（用例库 + inject 类型完整化，3-4 周）
  ├─ http_inject 新增
  ├─ pcap_inject（B-ii 后续 PR）+ 多四元组
  ├─ email_inject 完成（PR #36）
  ├─ sample_inject 链路打通（依赖 cape_adapter）
  └─ 用例库扩充至 ≥1200 条（4 大类）

Phase C（核心 GAP 子模块，4-5 周；可与 B 并行）
  ├─ veriguard-agent（独立项目）+ 12 类主机用例
  ├─ boundary_coverage（覆盖矩阵 + URL 资产 + 策略实体）
  ├─ stability_engine（稳定性 + 趋势图）
  ├─ boundary_monitoring（常态化监控）
  └─ 三态结果聚合 + UI（§7.8）

Phase D（重型独立模块，3-4 周）
  └─ attack_combination（30 000 组合 + 聚类 + 分级）— ★2 必答项

Phase E（验收准备，1-2 周）
  ├─ 截图素材准备（招标明示截图清单）
  ├─ UAT 测试用例编写
  └─ 验收测试报告
```

### 4.2 关键风险点

| # | 风险 | 影响 | 缓解 |
| --- | --- | --- | --- |
| 1 | 蓝盾 NxSOC 接口文档迟迟未到 | Phase A 启动延期 | 项目启动第 1 周通过甲方索取 NxSOC API 文档；如无 API，启动 syslog 转 SIEM 网关方案 |
| 2 | wangjuelong/cape 项目 API 不稳定 | §8 沙箱集成节奏 | 优先和 cape 项目 owner 对齐 API 版本与认证机制 |
| 3 | 验证 Agent 安装授权周期长 | §5 主机验证条款验收延期 | 第 1 周提交 Agent 干扰评估文档；并行准备无 Agent 备选（SSH/WinRM） |
| 4 | 30 000 攻击组合并发对设备过载 | §3.6 ★2 任务可能跑挂客户设备 | 限流调度器 + 单次任务上限 + 客户认可的执行窗口（如非业务高峰） |
| 5 | 用例库扩充至 1200 条工作量大 | Phase B 延期 | 提前组织安全工程师攻击用例编写小组；复用社区 PoC（CVE 库、Metasploit、Atomic Red Team） |
| 6 | IPv6 单栈基础设施不就绪 | §4 / §5 验证目标不可达 | 项目启动第 1 周完成 IPv6 双栈连通性测试；不通畅项进网络变更流程 |
| 7 | 30 000 组合 / 1200 用例的截图素材准备 | 验收阶段时间紧 | Phase B 完成时即开始素材准备；截图工具脚本化 |

---

## 5. 待澄清项映射（与甲方对齐）

将 spec §7 的 8 个待澄清项与本 GAP 分析关联：

| 序号 | 待澄清 | 关联 GAP 条款 |
| --- | --- | --- |
| 1 | 蓝盾 NxSOC 对外接口规范 | §3.1 / §4.1 / §5.1 / §7.5 |
| 2 | 靶机部署细则（数量 / 位置 / 镜像 / snapshot） | §5.1 / §6.2 binary_inject |
| 3 | IPv6 单栈程度 | §1 平台接入 / §4 流量验证目标 |
| 4 | 测试邮箱服务器地址与凭据 | §6.2 email_inject |
| 5 | 集团 IPv6 地址管理平台对接接口 | 数据上报（spec §1.6.2 工作范围） |
| 6 | WAF / 防火墙 / IDS / HIDS 策略导出 | §3.1 / §4.1 "策略名称" |
| 7 | §3.6 攻击组合分级标准定制需求 | §3.6 ★2 验收边界 |
| 8 | 生产机 vs 靶机的最终边界 | §5 / §6.2 靶机白名单机制 |

---

## 6. 后续行动建议

1. **优先排期 Phase A 集成基建（NxSOC + CAPE 适配 + 用例契约扩字段）** —— 这些是所有 GAP 子模块的前置依赖
2. **第 1 周内开启与甲方 8 项待澄清对齐** —— 不阻塞研发，但需尽快落停
3. **用例库扩充工作并行启动** —— 1200 条用例编写工作量最大，越早启动越好
4. **截图素材准备前置到 Phase B 结束** —— 避免验收阶段集中赶工

GAP 状态会随研发推进动态变化，本文档版本 v1.0 截至 2026-05-12；建议每 Phase 结束更新一次。

---

*文档完*
