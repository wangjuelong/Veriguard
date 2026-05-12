# IPv6 安全验证系统 — 研发拆解

> 来源：`docs/superpowers/specs/2026-05-12-ipv6-security-validation-refinement-design.md`
> 日期：2026-05-12
> 受众：Veriguard 研发团队
> 用法：按模块挨条对应到研发任务点；每条要求拆解为「验收指标 / 数据模型 / UI 要点 / 集成边界 / 边界异常 / 依赖前置」六维。GAP 与补开发清单见 [`IPv6安全验证系统-GAP分析.md`](IPv6安全验证系统-GAP分析.md)。

---

## 1. 总览

本文档覆盖招标 §2.3.1.2 IPv6 安全验证系统全部 25 条功能要求的研发拆解，分布如下：

| 模块 | 条数 | 含星条款 |
| --- | --- | --- |
| §3 边界防护验证 | 6 | ★1（3.3）、★2（3.6） |
| §4 流量安全验证 | 4 | ★3（4.2） |
| §5 应用与服务器安全验证 | 1 | - |
| §6 自定义验证 | 3 | - |
| §7 攻击编排 | 8 | ★4（7.4） |
| §8 沙箱管理 | 3 | - |

跨模块前置（详见 §9）：用例库扩充、平台自有验证 Agent、蓝盾 NxSOC SOC 对接适配器、wangjuelong/cape 沙箱对接适配器、IPv6 单栈基础设施。

---

## 2. 集成边界速查（甲方既有系统）

| 角色 | 具体系统 | 平台对接方向 |
| --- | --- | --- |
| 主对接 SOC | **蓝盾 NxSOC（日志审计与分析平台）** | 平台 → NxSOC 反查告警；按 (资产 IP, 时间窗) 拉 `ruleId` / `ruleName` |
| 流量 IDS/IPS | 国基华电 GJsec-SIDS、蓝盾 BDIPS | 不直接对接；事件经其上送 NxSOC |
| 流量分析平台（备选 SOC） | 奇安信天眼分析平台 | 备选，首期不强制 |
| 应用 WAF | 深信服 WAF | 不直接对接；告警经 NxSOC |
| 主机 HIDS | 集团统建"云眼" | 不替换；平台**自有轻量验证 Agent** 与其并存 |
| 沙箱后端 | **`wangjuelong/cape`**（CAPE Sandbox 派生） | 异步任务下发 + 报告轮询 |
| 测试邮箱 | 甲方提供测试 SMTP 服务器 | `email_inject` 真实发送 |

---

## 3. §3 边界防护验证 — 详细拆解

### 3.1 网络边界防护覆盖度验证

**验收指标**
- 资产覆盖矩阵可一屏展示 ≥100 个 URL 资产 × N 个策略；3 态单元格（✅ / ❌ / —）
- 一次任务结束后，UI 可下钻到单 URL 的具体策略命中列表
- 两次任务对比页：新增资产数、变化资产数、URL 级 diff 列表
- **截图佐证**：覆盖矩阵、URL 详情、两次对比 diff

**数据模型要点**
- 新表 `boundary_coverage_baseline`（基线用例集，user_selected 用例 ID 列表）
- 新表 `boundary_coverage_run`（一次覆盖度运行：baseline_id + asset_set + 启动时间）
- 新表 `boundary_coverage_result`（行级：run_id × asset_id × policy_id × hit_state）
- `policy` 实体（策略名称）作为独立维度，**手工录入** + 阶段二可扩展从设备 API 拉取

**UI 要点**
- 覆盖度任务创建页：选基线用例 + 选资产组 + 配 SOC 关联
- 矩阵视图：行=URL 资产 / 列=策略 / 单元格点击 → 详情侧栏
- 对比页：选两个 `boundary_coverage_run` → 生成 diff

**集成边界**
- 与 §3.5 平台预置 WAF 攻击用例库共享 inject 池
- 与 §7 攻击编排共享 SOC 关联（NxSOC）查询管线

**边界 / 异常场景**
- SOC 超时未返回：单元格按"⏱ 超时未知"标识（不等同未覆盖）
- 资产不在本次任务范围：单元格"—"
- 同一资产被多条策略命中：单元格点击展开命中策略列表

**依赖前置**
- §6.1 用例库筛选能力
- 蓝盾 NxSOC 接口可按 (asset_ip, time_window) 反查告警

---

### 3.2 安全设备策略有效性常态化监控

**验收指标**
- 监控任务可调度到小时级（每天 / 每小时跑同一基线）
- 趋势可视化：日 / 小时双视图；展示"被拦截 / 检出占比"曲线
- 可下钻到具体策略的单策略趋势

**数据模型要点**
- 新表 `boundary_monitoring_job`（Quartz 调度任务，含 cron + baseline_id + asset_set + device_id）
- 时序结果落到 Elasticsearch 索引 `boundary-monitoring-history`（按月分片）

**UI 要点**
- 监控配置页：cron 表达式 + 基线 + 资产 + 设备 / 策略
- 趋势图：Recharts 时间序列；切换"设备整体 / 单策略"

**集成边界**
- 同 3.1

**边界 / 异常**
- cron 表达式校验：最小粒度 1h
- 监控任务失败 N 次后自动降频通知

**依赖前置**
- §3.1 资产覆盖矩阵（监控任务复用同一计算管线）

---

### 3.3 ★1 边界与流量设备稳定性验证（招标方标星）

**验收指标**
- 任务可配置"重复 N 次"，**N 用户可输，默认 10**
- 稳定性结果 = 命中率 = 命中次数 / N（0–1）
- 多次任务在同一基线下形成稳定性趋势图
- **截图佐证**：重复 N 次结果详情、稳定性趋势图

**数据模型要点**
- 复用 `attack_chain_run` 既有 repeat_count 字段；新增 `stability_metric` 字段（计算列）
- 新表 `stability_trend_snapshot`（每次任务一个点：device_id + baseline_id + run_id + hit_rate + timestamp）

**UI 要点**
- 攻击链路 / 任务配置中可见"稳定性模式"开关
- 稳定性趋势图：每点一个任务，悬停展示 N、命中率、命中明细
- 切换按天聚合

**集成边界**
- §4.2 流量稳定性复用同一机制（仅设备维度变化）

**边界 / 异常**
- N=1 时不计算稳定性（视为普通验证）
- N>100 拒绝（界面限制 + 后端校验）

**依赖前置**
- §7 攻击编排节点重复执行能力（✅ §2.4 已具备）

---

### 3.4 已知高危漏洞利用及绕过防护有效性验证

**验收指标**
- 平台内置高危漏洞 PoC 用例库，按软件类别分类
- 软件分类至少覆盖：web 服务组件、安全产品、应用程序、国产商业软件、CMS、网络产品、操作系统、数据库 8 类
- 不强制对接 CVE / 威胁情报源

**数据模型要点**
- 用例契约新增字段 `software_category ∈ {web_component, security_product, application, domestic_commercial, cms, network_device, os, database}`
- 用例契约可关联 CVE ID（可选）

**UI 要点**
- 用例库筛选维度补 `software_category`
- 用例详情页展示关联 CVE

**集成边界**
- §3.5 WAF/IPS 攻击效用用例与本条同源（部分用例两边都标）

**依赖前置**
- 用例库初始数据：覆盖 8 大软件类别的高危漏洞 PoC ≥100 条（首期门槛）

---

### 3.5 边界 WAF/IPS 攻击效用 12 类 ≥500 用例

**验收指标**
- 12 类攻击 100% 覆盖：命令执行 / 目录遍历 / 弱口令登录 / 暴力破解 / SQL 注入 / SSRF / CSRF / XSS / XXE / SSTI / 畸形表单上传绕过 / 大包上传绕过
- **总用例数 ≥500（双满足承诺）**
- 每条用例标注 `attack_category` + 软件目标 + IPv6 适配版本

**数据模型要点**
- 用例契约 `attack_category` 字段枚举 12 类
- 用例命名规范：`<attack_category>-<target_software>-<variant>`

**UI 要点**
- 用例库筛选 / 矩阵视图按 `attack_category` 维度

**集成边界**
- 与 §3.6 攻击组合生成共享同一用例池

**依赖前置**
- 12 类 × 用例库扩充至少 500 条（首期门槛）

---

### 3.6 ★2 动态攻击组合 250 类 × 30 000 组合 + 聚类分级（招标方标星）

**验收指标**
- 平台支持选择 ≥250 种攻击类型作为生成基础
- 动态生成 ≥30 000 个攻击组合（数学上 250 × 120 绕过维度 ≈ 30 000）
- 跑完后自动按 (设备, 资产) 聚类未防护项
- 未防护项自动分级（平台预置默认分级 + 客户可配置）
- 聚类输出**可下钻到 payload 级**
- **截图佐证**：组合生成、聚类分析、分级展示

**数据模型要点**
- 新表 `attack_combination_template`（绕过维度库：编码 / 分块 / 大小写 / 参数顺序 / 噪声前后缀等 120 维）
- 新表 `attack_combination_run`（任务：base_types[250] × bypass_dims[120] = combinations）
- 新表 `attack_combination_result`（行级：combination_id × asset_id × hit_state × cluster_id × severity_level）

**UI 要点**
- 任务创建：勾选 base_types + 绕过维度（默认全选 120 维）
- 聚类视图：树形或矩阵；下钻到 payload 级别
- 分级 UI：默认 4 级（高 / 中 / 低 / 信息）；客户可配置阈值与命名

**集成边界**
- 与 §3.5 用例池打通

**边界 / 异常**
- 30 000 组合并发执行：需限流 + 队列调度，避免对设备过载
- 单次任务超时控制：默认 24h，可配

**依赖前置**
- §3.5 用例池 ≥250 类
- 绕过维度库 120 项首批落地

---

## 4. §4 流量安全验证 — 详细拆解

### 4.1 网络边界流量覆盖度验证

**验收指标**
- 流量覆盖矩阵：行=IP 资产 / 列=策略 / **4 态单元格**（✅ / ❌ 未覆盖 / ⏱ 超时未知 / —）
- 可下钻：单 IP × 单策略 的命中告警明细
- **截图佐证**：覆盖矩阵

**数据模型要点**
- 复用 §3.1 `coverage_*` 数据模型，区分 `coverage_type ∈ {boundary, traffic}`
- 资产实体为 IP 级（IPv4 + IPv6 双栈）；通过资产组 / 标签筛选

**UI 要点**
- 与 §3.1 独立的"流量覆盖度"页签；UI 风格统一
- 4 态单元格图例

**集成边界**
- 判定链路（**严格**）：
  ```
  平台 → 攻击机（运维管理区 / 广域网 PE 旁挂）
    → 目标 IPv6 资产 → 国基华电 GJsec-SIDS / 蓝盾 BDIPS / 奇安信天眼探针
    → 蓝盾 NxSOC → 平台按 (asset_ip, time_window) 查告警 → 提取 ruleId
    → 写入覆盖矩阵
  ```

**边界 / 异常**
- SOC 超时未返回（默认窗口 5min，可配）→ 单元格 ⏱
- 同一资产被多条策略命中 → 单元格点击展开

**依赖前置**
- 攻击机部署
- 蓝盾 NxSOC 对外接口（待澄清，见 spec §7）

---

### 4.2 ★3 流量安全设备稳定性（招标方标星）

**验收指标**
- 与 §3.3 同机制；区别仅在被测设备为 IDS/IPS（蓝盾 BDIPS / 国基华电 GJsec-SIDS）/ 天眼探针
- 默认 N=10；命中率 0–1
- 趋势图按任务一个点；可切换按天聚合
- **截图佐证**：稳定性趋势图

**数据模型 / UI / 异常**：与 §3.3 同

**依赖前置**
- 同 §4.1

---

### 4.3 NTA/IDS 攻击效用 11 类 ≥300 用例

**验收指标**
- 11 类：暴力破解 / 反弹 shell / 内存注入 webshell / 隐秘隧道 / 恶意域名解析 / webshell 命令执行 / 高危漏洞利用 / 远控木马执行 / 权限绕过 / 未授权访问 / 信息泄露
- **总用例数 ≥300（双满足承诺）**
- 11 类**全部 IPv6 单栈可跑**；IPv4 版保留作回归

**数据模型要点**
- 用例契约同 §3.5；`attack_category` 枚举本节 11 类
- IPv6 支持通过 `network_protocol_family` 字段标注

**UI 要点**
- 用例库可按 `attack_category` 筛选；标签过滤 IPv6 单栈版

**集成边界**
- 用例形态：主以 `pcap_inject`；少数用 `command_inject` / `binary_inject`（反弹 shell、远控木马）

**依赖前置**
- 用例库扩充至 300 条 × IPv6 双版本
- §6.2 inject_type 6 类完整可用

---

### 4.4 单用例多端口四元组

**验收指标**
- 单条流量验证用例（pcap 用例）支持 ≥2 组 (srcIP, dstIP, srcPort, dstPort) 声明
- 用户可选**顺序 / 并发**两种执行模式（默认顺序）
- 顺序模式下间隔可配（默认 1s，最小 0.1s）
- 平台支持回放时**自动改写端口**，无需准备 N 个 pcap

**数据模型要点**
- pcap 用例契约新增字段 `port_tuples: list[{src_ip, dst_ip, src_port, dst_port}]`
- 单用例最多 64 组（可配置上限）
- 同协议族（v4 / v6 不混）：用例 schema 校验

**UI 要点**
- pcap 用例编辑器："端口元组" 表格，行级增删
- 执行模式开关：顺序 / 并发；间隔输入框

**集成边界**
- pcap 回放层（B-ii pcap inject）需补"端口改写"能力

**边界 / 异常**
- srcPort 留空 → 系统随机生成
- dstPort 重复 → 校验拒绝（要求每组 dstPort 必须不同）

**依赖前置**
- B-ii pcap inject 完成（PR 节奏对齐 phase 12c 后续）

---

## 5. §5 应用与服务器安全验证 — 详细拆解

### 5.1 HIDS 主机攻击效用 12 类 ≥300 用例

**验收指标**
- 12 类：反弹 shell / webshell 上传落盘 / 命令执行 / 隧道代理 / 内存注入 webshell / 暴力破解 / 远控木马执行 / 系统提权 / 网站篡改 / 病毒样本落盘 / 痕迹清理 / 主机持久化
- **总用例数 ≥300（双满足承诺）**
- 网络通信类用例（反弹 shell / 远控 / 隧道 / 暴力破解）100% IPv6 单栈可跑
- 纯本地操作类用例（提权 / 痕迹清理 / 持久化）IPv6 无关
- 每条用例标注 `target_os ∈ {linux, windows, both}`
- **用例执行后自动还原**主机（删落盘 / 杀进程 / 回滚注册表 / 清持久化）

**数据模型要点**
- 用例契约 `attack_category`（12 类枚举）+ `target_os` + `network_dependent`（bool）
- 还原步骤声明：`rollback_steps: list[step]`（每 step 描述还原动作）

**UI 要点**
- 用例库筛选维度：`attack_category` + `target_os` + `network_dependent`
- 用例编辑器：还原步骤可视化编辑

**集成边界**
- 平台**自有轻量验证 Agent** ↔ 主机：通过 TLS over IPv6 通信
- HIDS 检出判定 ← 蓝盾 NxSOC 反查告警

**边界 / 异常（**硬约束**）**
- **仅在甲方靶机（1:1 仿真主机）上执行**；不在生产主机上跑
- 靶机白名单：用例执行前校验目标是否在 `target_machine_whitelist`，否则拒绝
- 还原失败：标该靶机 `unavailable` + 告警通知运维 + 暂停后续用例

**依赖前置**
- 平台验证 Agent 开发（独立模块）
- 甲方批准 Agent 安装到靶机
- 靶机部署完成（数量、位置、镜像参考 spec §7 待澄清项 #2）

---

## 6. §6 自定义验证 — 详细拆解

### 6.1 自定义场景

**验收指标**
- 用户可创建自定义场景，编排画布支持 DAG
- 节点可拖拽 / 增删 / 连线；执行顺序由 DAG 决定
- 场景详情双 tab：**ATT&CK 矩阵视图** / **纵深防御 5 层视图**
- 5 层：网络边界 / 流量检测 / 应用层 / 主机端点 / 数据层
- 用例可按 ATT&CK Tactic + Technique 自动落入矩阵格
- 用例可按防御层自动落入对应层

**数据模型要点**
- 复用 Veriguard `attack_chain` + `attack_chain_node` + `attack_chain_edge`（✅ §2.4 已具备）
- 用例契约新增字段 `defense_layer ∈ {boundary, traffic, application, host, data}`
- 矩阵视图复用 §7.3.12 ATT&CK 矩阵视图组件

**UI 要点**
- 场景详情页顶部 tab：「ATT&CK 矩阵」/「纵深防御」 / 「时间轴」
- 纵深防御视图：5 列 × N 行（用例）；空缺层显示"⚠ 此层无用例"

**集成边界**
- 复用 ChainedTimeline 编辑画布

**边界 / 异常**
- 用例无 `defense_layer` 标注 → 落入"未分类"区，UI 提示补足

**依赖前置**
- 用例契约 `defense_layer` 字段 + 现有用例数据补充
- ATT&CK 矩阵视图已具备（✅）

---

### 6.1.X 批量导入

**验收指标**
- 支持 Excel 导入（首选）+ JSON 导入（可选）
- Excel 模板含：用例标题、描述、attack_category、attack_pattern_id、defense_layer、target_os、tags、inject_type 等
- 一次导入 → 一个场景（含全部用例作为节点）
- 已存在用例按 hash 幂等（同名同内容跳过）

**数据模型要点**
- 导入临时表 `case_import_batch`（batch_id + uploaded_by + status）
- 关联 `attack_chain`：每个 batch 默认派生一个 chain

**UI 要点**
- 用例库 → "批量导入"按钮 → 上传 Excel → 预览（行级 valid/invalid）→ 确认导入

**边界 / 异常**
- Excel 必填列缺失 → 行级标红 + 报错
- 重复（同 hash）→ 行级标"跳过"

---

### 6.2 6 类自定义用例 inject_type

逐 inject_type 拆解：

#### 6.2.1 `http_inject` —— 构造 web 攻击包（**GAP**）

**验收指标**
- 编辑器支持：method（GET/POST/PUT/DELETE/PATCH）+ URL（IPv6 字面量地址支持）+ headers + body（form-data / JSON / XML / raw）+ cookies + query params
- 自签证书容忍
- HTTPS 支持

**数据模型**
- 新 inject_type：`http_inject`
- payload 结构：`{method, url, headers, body, cookies, query, tls_verify}`

**UI 要点**
- 类似 Postman 的简化编辑器，请求构造完成后可"测试发送一次"

**边界 / 异常**
- 仅 HTTP / HTTPS；不含 WebSocket / gRPC（后续扩展）
- IPv6 字面量地址需用 `[::1]:port` 格式

---

#### 6.2.2 `pcap_inject` —— 上传 pcap 流量包（B-ii 进行中）

**验收指标**
- 上传 .pcap / .pcapng 文件 + 多组四元组声明（与 §4.4 承接）
- 回放时端口改写

**数据模型**
- payload：`{pcap_file_id, port_tuples: list[...], replay_mode: sequential|concurrent, interval_ms}`

---

#### 6.2.3 `sample_inject` —— 上传样本文件（沙箱承接）

**验收指标**
- 上传 .exe / .dll / .bin / .py 等样本
- **强制沙箱执行**：与 §8 wangjuelong/cape 对接
- 执行完成后自动还原

**数据模型**
- payload：`{sample_file_id, sandbox_profile_id, expected_behaviors: list[...]}`

**集成边界**
- 沙箱执行结果（行为日志、网络回连、文件落地）→ 平台展现

---

#### 6.2.4 `command_inject` —— 配置执行命令（✅ B-i 已完成）

**验收指标**
- 多行命令字符串；shell 类型可选（sh / bash / cmd / powershell）
- 标 `target_os`
- 输出 stdout / stderr / exit_code

**数据模型** 已具备

---

#### 6.2.5 `binary_inject` —— 上传可执行 + 配置命令（✅ B-i 已完成）

**验收指标**
- 上传二进制 + 部署路径 + 启动命令
- 验证 Agent 先下载部署到目标主机，再执行命令
- 执行完后自动清理

**数据模型** 已具备

---

#### 6.2.6 `email_inject` —— 配置邮件（PR #36 进行中）

**验收指标**
- 模拟钓鱼邮件：发件人 / 收件人 / 主题 / 正文 / 附件
- 通过**真实 SMTP**（甲方提供测试邮箱服务器）发送
- 可选 IMAP / POP3 下行验证（接收端收到与否）

**数据模型**
- payload：`{smtp_server_id, from, to, subject, body, attachments: list[...]}`
- 新表 `smtp_server_config`（SMTP 服务器对接配置）

**集成边界**
- 甲方测试 SMTP 服务器（spec §7 待澄清 #4）

**依赖前置**
- PR #36 邮件 inject 完成

---

### 6.3 筛选生成场景 + 动态联动

**验收指标**
- 筛选 6 维：属性（type / target_os）+ 标签 + ATT&CK Tactic/Technique + 杀伤链阶段 + 防御层（5 档）+ 自定义字段
- 场景可保存为「动态 filter 场景」或「手工枚举场景」
- 动态 filter 场景：任务启动时实时 evaluate filter；不在创建时 snapshot
- 已运行任务**不回溯**（保留启动时刻快照）
- 用例库变更 → 场景列表 UI 显示"X 条新用例进入场景 Y"
- filter 评估窗口：任务启动时一次取值；执行中途用例库变化不影响当次任务

**数据模型要点**
- `attack_chain` 增加 `scenario_type ∈ {dynamic, snapshot}`
- 动态场景：存 `filter_definition: jsonb`（filter DSL）
- 快照场景：存 `case_ids: list[uuid]`

**UI 要点**
- 场景创建：「场景类型」radio：动态 filter / 手工枚举
- 动态 filter 编辑器：6 维下拉 + AND / OR / NOT 逻辑组合
- 用例库变更通知：UI badge

**集成边界**
- 复用 §6.1 attack_chain 模型

**边界 / 异常**
- filter 无匹配用例时 → 任务启动时报错"场景为空"
- 用例库批量更新触发大量场景通知 → 通知聚合（同一场景 N 条更新合并为 1 条）

**依赖前置**
- ✅ B-iii 动态 filter 已完成（含 follow-up）

---

## 7. §7 攻击编排 — 详细拆解

### 7.1 可视化路径图配置

**验收指标**
- ChainedTimeline 画布（ReactFlow）支持：节点拖拽 / 节点 drawer 配置用例 / 边连接
- 节点详情侧栏：用例选择、执行参数、期望
- ✅ 已实现

---

### 7.2 节点延迟 / 重复 / 间隔（截图）

**验收指标**
- 节点配置：「延迟执行」开关 + 时间（秒，可输入秒 / 分 / 时）
- 「重复执行」开关 + 次数（默认 1，上限 100）+ 间隔（默认 1s，最小 1s）
- **截图佐证**：节点配置界面

**数据模型** 已具备

**UI 要点**
- 节点 drawer："执行模式"折叠面板

---

### 7.3 节点间执行逻辑（基于上一节点拦截结果）

**验收指标**
- 节点出边支持 2 种类型：**on-blocked** / **on-passed**
- 节点状态判定（统一定义）：从蓝盾 NxSOC 收到匹配该节点的告警 → 被拦；超时未收到 → 未拦
- 链路执行调度器按出边类型路由

**数据模型要点**
- `attack_chain_edge` 增加字段 `condition ∈ {on_blocked, on_passed, always}`
- ✅ §2.4 应已具备；需确认 condition 枚举完整

**UI 要点**
- 节点出边连接时弹出"路由条件"选择
- 边样式：on-blocked 红色 / on-passed 绿色 / always 灰色

---

### 7.4 ★4 全局 + 节点级验证参数 / 两种执行模式（截图）

**验收指标**
- 任务级全局参数：目标资产、时间窗、SOC 对接配置、超时
- 节点级参数可覆盖全局（节点级优先级 > 全局）
- 任务执行模式 2 种：
  - **「全程执行」**：无论节点是否被拦都继续推进（验证多层防御广度）
  - **「拦截即停」**：被拦截后链路终止（更贴近真实 APT 行为）
- **截图佐证**：全局 / 节点参数 UI、两种执行模式选择

**数据模型要点**
- `attack_chain_run` 增加字段 `execution_mode ∈ {continue_all, stop_on_blocked}`
- `attack_chain_node` 增加 `local_params: jsonb`（覆盖全局参数的部分）

**UI 要点**
- 任务创建页：「执行模式」radio
- 节点 drawer："覆盖全局参数"折叠面板

---

### 7.5 SOC 关联告警 + 匹配条件

**验收指标**
- 攻击编排任务可配 SOC 关联条件
- 匹配条件 6 维：资产 IP / 时间窗 / 告警严重度 / 规则 ID / 规则类别 / 告警关键字
- 6 维支持 AND 组合
- 全局配置 + 节点级可覆盖

**数据模型要点**
- 新表 `soc_match_condition`（match_id + chain_run_id + 6 维字段）
- 节点级覆盖：`attack_chain_node.local_params.soc_match`

**UI 要点**
- 任务级 SOC 匹配编辑器（6 个字段 + AND 逻辑）
- 节点级 SOC 匹配（折叠面板）

**集成边界**
- 蓝盾 NxSOC API：查询告警 → 应用 6 维匹配 → 返回命中告警列表

---

### 7.6 可视化运行画布 + 节点拦截 / 检测结果

**验收指标**
- 运行画布与编辑画布共用 ChainedTimeline
- 节点状态：未执行 / 执行中 / 已被拦 / 未被拦 / 超时
- 每节点显示对应 SOC 告警数与拦截判定
- ✅ §2.4 已实现

---

### 7.7 时间轴展示

**验收指标**
- 时间轴视图：横轴时间，纵轴节点
- 每节点的执行起止时间块
- 拦截 / 检测结果以颜色标识
- ✅ ChainedTimeline 已实现

---

### 7.8 三态验证结果（防御视角）

**验收指标**
- 链路级三态：
  - **全链路有效**：每节点都被拦 / 检测到（防御 100%）
  - **全链路失效**：每节点都未被拦（防御 0%）
  - **部分失效**：中间状态
- 任务列表 / 任务详情顶部展示三态徽标
- 三态切片在仪表板中作为视图维度

**数据模型要点**
- `attack_chain_run` 增加字段 `chain_result ∈ {fully_effective, partially_failed, fully_failed}`
- 计算逻辑：聚合所有节点 `node.result_state`；全拦=有效 / 全未拦=失效 / 其他=部分失效

**UI 要点**
- 状态徽标三色：绿（有效）/ 黄（部分失效）/ 红（失效）
- 任务列表筛选维度增加三态

**集成边界**
- 与 §7.3 节点状态共用判定逻辑

---

## 8. §8 沙箱管理 — 详细拆解

### 8.1 真实恶意样本执行

**验收指标**
- 支持 8 类样本场景：勒索 / 挖矿 / 蠕虫 / 终端恶意驱动 / 提权 / 账号窃取 / 代理执行 / 安全组件对抗
- 通过 `wangjuelong/cape` 异步任务下发
- 执行结果（行为日志、网络回连、文件落地）回传平台
- 报告可下载

**数据模型要点**
- 新表 `sandbox_task`（task_id + sample_file_id + sandbox_instance_id + status + cape_task_id）
- 新表 `sandbox_report`（task_id + behaviors[] + network[] + file_drops[] + raw_report_url）

**UI 要点**
- 沙箱任务列表 / 详情
- 报告可视化：行为分析、网络回连、文件落地

**集成边界**
- `wangjuelong/cape` 适配层：REST 任务下发 + 轮询；调用接口待 cape 项目 API 文档对齐

**边界 / 异常**
- 任务下发失败：自动重试 3 次
- 报告解析失败：标 `report_parse_error`，附原始报告 URL

**依赖前置**
- `wangjuelong/cape` 项目 API 文档与认证机制

---

### 8.2 沙箱平台 CRUD + 网络访问控制（**截图否则无效**）

**验收指标**
- 沙箱平台 / 沙箱实例 CRUD：新建 / 编辑 / 删除 / 列表 / 详情
- 实例配置含：name / cape_endpoint / api_key / 默认 timeout / 网络访问控制策略
- 网络访问控制 4 维：
  - 白名单（允许出站 IP / 域名）
  - 黑名单（禁止出站 IP / 域名）
  - 协议限制（HTTP / HTTPS / DNS / 自定义协议）
  - 速率限制（出站带宽上限 Mbps）
- **截图佐证**：CRUD 全套界面 + 网络策略配置界面

**数据模型要点**
- 新表 `sandbox_platform`（platform_id + name + cape_endpoint + api_key_encrypted）
- 新表 `sandbox_network_policy`（platform_id + whitelist[] + blacklist[] + protocols[] + rate_limit_mbps）

**UI 要点**
- 沙箱平台管理页：表格列表 + 增删改 + 详情侧栏
- 网络策略编辑器：4 个 tab 维度独立编辑

**集成边界**
- 网络策略下发到 `wangjuelong/cape` 通过对应 API 设置 VM 网络规则

---

### 8.3 用例执行后自动还原沙箱环境

**验收指标**
- 默认**每条用例结束后**还原
- 可切换"任务结束整体还原"
- 还原方式：CAPE 原生 VM Snapshot 回滚
- 还原失败 → 标该实例 `unavailable` + 告警 + 任务自动重排
- **截图佐证**：还原流程截图

**数据模型要点**
- `sandbox_task` 增加字段 `rollback_strategy ∈ {per_case, per_task}` + `rollback_status`
- `sandbox_platform_instance` 增加 `availability ∈ {available, unavailable, draining}`

**UI 要点**
- 沙箱实例状态徽标（绿 / 黄 / 红）
- 还原失败告警列表

**集成边界**
- `wangjuelong/cape` 的 snapshot 回滚 API

---

## 9. 跨模块依赖

### 9.1 用例库扩充清单

| 用例类别 | 招标条款 | 目标数量 | IPv6 适配 |
| --- | --- | --- | --- |
| 高危漏洞 PoC（8 软件类） | §3.4 | ≥100 | 视情况 |
| WAF/IPS 12 类（边界） | §3.5 | ≥500（双满足） | 是 |
| NTA/IDS 11 类（流量） | §4.3 | ≥300（双满足） | **必须 100%** |
| HIDS 12 类（主机） | §5.1 | ≥300（双满足） | 网络通信类**必须** |
| **合计** | | **≥1200** | |

### 9.2 平台自有验证 Agent

- 模块：`veriguard-agent`（新增）
- 通信：TLS over IPv6
- 平台 ↔ Agent：任务下发 + 心跳 + 结果回传
- 部署：与甲方云眼并存；需提交干扰评估并获甲方批准
- 仅安装到甲方靶机；生产主机不安装

### 9.3 蓝盾 NxSOC 对接适配器

- 模块：`veriguard-api/src/main/java/io/veriguard/integration/soc/nxsoc/`
- 接口能力（待 NxSOC 文档确认）：
  - `queryAlerts(asset_ip, time_window, filters)` → 告警列表
  - 字段：`ruleId` / `ruleName` / `severity` / `category` / `keywords`
- 备选 SOC：奇安信天眼分析平台（首期不强制）

### 9.4 wangjuelong/cape 沙箱对接适配器

- 模块：`veriguard-api/src/main/java/io/veriguard/integration/sandbox/cape/`
- 接口能力（待 cape 项目 API 对齐）：
  - 任务下发 + 状态轮询 + 报告获取
  - 网络策略下发
  - Snapshot 回滚

### 9.5 IPv6 单栈基础设施

- 平台 Web 控制台：IPv6 双栈监听
- 平台 ↔ 外部组件全链路 IPv6
- 攻击机：双栈接入广域网 PE / 园区网 / 数据中心
- 验证 Agent：IPv6 字面量地址支持

---

## 10. 截图清单（招标明示项）

研发交付时**必须**配套提供下列截图：

| 模块 | 截图条款 | 招标原文 |
| --- | --- | --- |
| §3.1 | 资产覆盖矩阵、URL 覆盖详情、两次对比 diff | 提供功能截图 |
| §3.3 ★1 | 重复 N 次结果详情、稳定性趋势图 | 需提供功能截图 |
| §3.6 ★2 | 30 000 组合生成、聚类分析、未防护项分级 | 需提供功能截图 |
| §4.1 | 流量覆盖矩阵、策略覆盖详情 | 提供功能截图 |
| §4.2 ★3 | 流量设备稳定性趋势图 | 需提供功能截图 |
| §7.2 | 节点延迟 / 重复配置界面 | 提供功能截图 |
| §7.4 ★4 | 全局 vs 节点级参数、两种执行模式 | 需提供功能截图 |
| §8.2 | 沙箱 CRUD 全套 + 网络访问控制 | **提供功能截图，否则视为无效** |
| §8.3 | 用例执行后沙箱自动还原 | 提供功能截图 |

---

*文档完*
