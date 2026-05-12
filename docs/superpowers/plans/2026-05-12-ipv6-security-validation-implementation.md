# IPv6 安全验证系统 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Veriguard 现有 §2.4 攻击编排 + §2.3 B-i / B-iii 完成的基础上，补齐 7 项 🔴 未实现 + 12 项 🟡 部分满足，使 IPv6 安全验证系统满足招标 §2.3.1.2 全部 25 条要求并具备可演示、可验收能力。

**Architecture:** 按集成基建 → inject 类型 → 用例库 → 核心子模块 → 重型 ★2 模块 → 验收准备 五阶段推进；19 GAP 项落到约 20 个 PR；每个 PR 独立可合并到 main，互相之间显式依赖前置。所有数据模型扩展沿用 Veriguard 既有 Flyway 迁移机制；所有新增 inject 类型沿用既有用例契约 schema 扩展点。

**Tech Stack:** Spring Boot 3.3.7 / Java 21 / JPA + Flyway / PostgreSQL / Elasticsearch / RabbitMQ / Quartz / React 19 + Vite + ReactFlow；新增对接：蓝盾 NxSOC API / wangjuelong/cape REST / veriguard-agent（独立子项目，TLS over IPv6）。

**来源 spec：** [`docs/superpowers/specs/2026-05-12-ipv6-security-validation-refinement-design.md`](../specs/2026-05-12-ipv6-security-validation-refinement-design.md)
**来源 GAP 分析：** [`docs/IPv6安全验证系统-GAP分析.md`](../../IPv6安全验证系统-GAP分析.md)
**研发拆解参考：** [`docs/IPv6安全验证系统-研发拆解.md`](../../IPv6安全验证系统-研发拆解.md)

---

## 项目总览

### 五阶段排期

| Phase | 名称 | PR 数 | 估时 | 关键产出 |
| --- | --- | --- | --- | --- |
| A | 集成基建 | 3 | 2–3 周 | NxSOC 适配 / CAPE 适配 / 用例契约扩字段 |
| B | Inject 类型完整化 + 用例库扩充 | 5 | 3–4 周 | 6 类 inject_type 全可用 + 用例库 ≥1200 |
| C | 核心 GAP 子模块 | 6 | 4–5 周（与 B 并行） | Agent / 覆盖度 / 监控 / 稳定性 / 三态 |
| D | ★2 攻击组合（重型独立） | 5 | 3–4 周 | 30 000 组合 + 聚类 + 分级 |
| E | 验收准备 | 3 | 1–2 周 | 截图素材 / UAT 用例集 / 验收报告 |
| **合计** | | **~22 PR** | **13–16 周**（叠加并行约 9–11 周关键路径） | |

### GAP 项目映射

| GAP 条款 | 状态 | 对应 PR |
| --- | --- | --- |
| §3.1 边界覆盖度 | 🔴 | C3 |
| §3.2 常态化监控 | 🔴 | C4 |
| §3.3 ★1 边界稳定性 | 🟡 | C5 |
| §3.4 漏洞利用 | 🟡 | A3 + B5 |
| §3.5 WAF 12 类 ≥500 | 🟡 | A3 + B5 |
| §3.6 ★2 攻击组合 | 🔴 | D1–D5 |
| §4.1 流量覆盖度 | 🔴 | C3 |
| §4.2 ★3 流量稳定性 | 🟡 | C5 |
| §4.3 NTA 11 类 ≥300 | 🟡 | A3 + B5 |
| §4.4 多端口四元组 | 🔴 | B3 |
| §5.1 HIDS 12 类 + Agent + 还原 | 🟡 | C1 + C2 + A3 + B5 |
| §6.1 场景 + ATT&CK + 防御层 + 导入 | 🟡 | A3 + C6（防御层 UI 部分）|
| §6.2.1 http_inject | 🔴 | B1 |
| §6.2.2 pcap_inject | 🟡 | B3 |
| §6.2.3 sample_inject | 🟡 | B4 |
| §6.2.6 email_inject | 🟡 | B2 |
| §6.3 动态联动 | ✅ | 无需 PR（已完成）|
| §7.5 SOC 6 维匹配 | 🟡 | A1 |
| §7.8 三态结果 | 🟡 | C6 |
| §8.1 真实样本 | 🟡 | A2 + B4 |
| §8.2 沙箱 CRUD + 网络策略 | 🟡 | A2 |
| §8.3 自动还原 | 🟡 | A2 |

### 关键路径与并行机会

```
Phase A (2-3 周, 串行入口)
   └─→ Phase B (3-4 周) ╮
        └─→ Phase C (4-5 周) ╮（C 可在 A 后启动，与 B 并行）
              └─→ Phase D (3-4 周)
                    └─→ Phase E (1-2 周)
```

并行可压缩至 ~9-11 周关键路径（Phase A → C/D 并发 → E）。

---

## Phase A：集成基建（2–3 周，串行入口）

### PR A1：蓝盾 NxSOC 适配器

**Goal:** 提供平台与蓝盾 NxSOC 的标准化对接层，支持 6 维告警匹配查询，被后续 §3.1 / §4.1 / §5.1 / §7.5 全部复用。

**前置：** spec §7 待澄清项 #1（NxSOC 接口文档）必须在第 1 周拿到。

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/integration/soc/nxsoc/NxSocClient.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/soc/nxsoc/NxSocConfig.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/soc/nxsoc/dto/AlertQueryRequest.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/soc/nxsoc/dto/AlertQueryResponse.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/soc/SocAdapter.java`（抽象接口，便于未来对接天眼分析平台）
- Create: `veriguard-api/src/test/java/io/veriguard/integration/soc/nxsoc/NxSocClientTest.java`
- Modify: `veriguard-api/src/main/resources/application.yml`（添加 `nxsoc.endpoint` / `nxsoc.api-key` 配置项）

**Tasks:**

- [ ] **A1-1** 定义 `SocAdapter` 接口：`List<Alert> queryAlerts(QueryCriteria criteria)`；`QueryCriteria` 含 6 维（资产 IP / 时间窗 / 严重度 / 规则 ID / 规则类别 / 关键字）
- [ ] **A1-2** 实现 `NxSocClient`：基于 OkHttp / Spring RestTemplate，含重试 + 超时 + Auth header
- [ ] **A1-3** 单测：mock NxSOC server（WireMock），覆盖 6 维查询、超时、404、500
- [ ] **A1-4** 集成测试：基于 testcontainers 或 mock server，验证完整调用链路
- [ ] **A1-5** 配置文档：在 `docs/IPv6安全验证系统-研发拆解.md` §9.3 NxSOC 适配器小节补 API 文档链接
- [ ] **A1-6** 提交 PR base=main

**AC:**
- 6 维查询用例全部通过
- 超时容错：默认 30s，可配
- API key 加密存储（DB 字段 `api_key_encrypted`，使用 Spring Vault 或 JCE）

**估时：** 1 周（含与甲方对齐 API 细节 2-3 天）

**风险：** NxSOC 文档延期 → 启用 syslog 备选方案（不阻塞 A2 / A3）

---

### PR A2：wangjuelong/cape 沙箱适配器

**Goal:** 提供平台与 wangjuelong/cape 项目的对接层，支持任务下发 / 状态轮询 / 报告获取 / 网络策略下发 / Snapshot 回滚。

**前置：** 与 wangjuelong/cape 项目 owner 对齐 API 版本（同一作者协调容易）。

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/cape/CapeClient.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/cape/CapeConfig.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/cape/dto/SandboxTaskRequest.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/cape/dto/SandboxReport.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/cape/dto/NetworkPolicy.java`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/sandbox/SandboxAdapter.java`（抽象接口）
- Create: `veriguard-api/src/test/java/io/veriguard/integration/sandbox/cape/CapeClientTest.java`

**Tasks:**

- [ ] **A2-1** 定义 `SandboxAdapter` 接口：`submitTask` / `pollStatus` / `getReport` / `setNetworkPolicy` / `rollbackSnapshot`
- [ ] **A2-2** 实现 `CapeClient` REST 客户端
- [ ] **A2-3** 单测：mock CAPE server，覆盖任务下发、轮询、报告解析、网络策略下发、snapshot 回滚
- [ ] **A2-4** 错误处理：任务下发失败自动重试 3 次；report 解析失败保留原始 URL
- [ ] **A2-5** 提交 PR base=main

**AC:**
- 5 个核心 API 全部覆盖测试
- 与 wangjuelong/cape 完成一次端到端的样本提交 → 报告获取联调

**估时：** 1 周

**风险：** cape API 不稳定 → 用 fixture 数据继续推进 B 阶段

---

### PR A3：用例契约字段扩充

**Goal:** 用例契约（`node_contract`）数据模型补齐 §3 / §4 / §5 / §6 各场景所需的元数据字段：`software_category` / `defense_layer` / `network_protocol_family` / `target_os` / `network_dependent` / `rollback_steps`。

**前置：** 无（独立可推进）

**Files:**
- Create: `veriguard-api/src/main/resources/db/migration/V4__node_contract_field_extension.sql`
- Modify: `veriguard-model/src/main/java/io/veriguard/database/model/NodeContract.java`（新字段）
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/node_contract/NodeContractDto.java`（DTO 字段映射）
- Modify: `veriguard-front/src/utils/api-types.d.ts`（前端类型重新生成）
- Modify: `veriguard-front/src/admin/components/node_contracts/NodeContractEditor.tsx`（编辑器新字段）
- Modify: `veriguard-front/src/admin/components/node_contracts/NodeContractFilter.tsx`（filter 维度补齐）
- Create: `veriguard-api/src/test/java/io/veriguard/rest/node_contract/NodeContractFieldExtensionTest.java`

**Tasks:**

- [ ] **A3-1** Flyway 迁移 V4：添加列
  - `software_category VARCHAR(64)` (枚举值：web_component, security_product, application, domestic_commercial, cms, network_device, os, database, NULL)
  - `defense_layer VARCHAR(32)` (枚举：boundary, traffic, application, host, data, NULL)
  - `network_protocol_family VARCHAR(16)` (枚举：ipv4, ipv6, both)
  - `target_os VARCHAR(16)` (枚举：linux, windows, both, none)
  - `network_dependent BOOLEAN DEFAULT false`
  - `rollback_steps JSONB`
- [ ] **A3-2** JPA 实体 `NodeContract` 加字段
- [ ] **A3-3** DTO 与序列化测试（含枚举值校验）
- [ ] **A3-4** 前端 `yarn generate-types-from-api` 重新生成类型
- [ ] **A3-5** 用例编辑器 UI：添加 6 个字段的输入控件（下拉、开关、JSONB 编辑器）
- [ ] **A3-6** 用例 filter UI：6 维筛选条件补齐
- [ ] **A3-7** 单测 + 集成测试
- [ ] **A3-8** 提交 PR base=main

**AC:**
- 现有 ~Veriguard 用例数据在迁移后全部字段为 NULL（向后兼容）
- 用例编辑器可设置 / 修改新字段
- 用例 filter 支持 6 维筛选

**估时：** 1 周

**风险：** 现有用例数量大，批量回填字段需另起任务（PR B5 中包含）

---

## Phase B：Inject 类型完整化 + 用例库扩充（3–4 周）

### PR B1：http_inject 新增

**Goal:** 新增 `http_inject` 类型，支持用户自定义 HTTP / HTTPS 攻击包，编辑器类似 Postman 简化版。

**前置：** A3（用例契约扩展）

**Files:**
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/inject/HttpInjectPayload.java`
- Create: `veriguard-api/src/main/java/io/veriguard/inject/HttpInjectExecutor.java`
- Create: `veriguard-api/src/main/java/io/veriguard/inject/HttpInjectValidator.java`
- Create: `veriguard-front/src/admin/components/injects/HttpInjectEditor.tsx`
- Create: `veriguard-front/src/admin/components/injects/HttpInjectRunner.tsx`（运行画布渲染）
- Create: `veriguard-api/src/test/java/io/veriguard/inject/HttpInjectExecutorTest.java`
- Modify: `veriguard-api/src/main/resources/db/migration/V5__http_inject_type.sql`
- Modify: `veriguard-api/src/main/java/io/veriguard/inject/InjectTypeRegistry.java`（注册新类型）

**Tasks:**

- [ ] **B1-1** Flyway V5：在 `inject_type` 枚举中添加 `http_inject`
- [ ] **B1-2** `HttpInjectPayload` 数据结构：`{method, url, headers, body, body_type, cookies, query, tls_verify}`
- [ ] **B1-3** `HttpInjectExecutor` 实现：基于 OkHttp，支持 IPv6 字面量地址 `[::1]:port`，自签证书容忍
- [ ] **B1-4** 单测：mock HTTP server，覆盖 GET / POST / PUT / DELETE / PATCH × form-data / JSON / XML / raw
- [ ] **B1-5** 编辑器 UI：类 Postman 五 tab（Params / Headers / Body / Cookies / Auth）
- [ ] **B1-6** "测试发送一次"按钮：调后端 `/api/inject/http/test-once` 显示响应预览
- [ ] **B1-7** 运行画布渲染：节点上显示 method + URL host
- [ ] **B1-8** 提交 PR base=main

**AC:**
- IPv6 字面量地址（如 `https://[2001:db8::1]:443/api`）正确解析
- 自签证书可选信任开关生效
- 编辑器与运行画布双端联调通过

**估时：** 4-5 天

---

### PR B2：B-ii PR-B 邮件 inject 完成（基于 PR #36）

**Goal:** 完成现有 PR #36 邮件 inject，对接甲方测试 SMTP 服务器。

**前置：** PR #36 当前 39 测试通过；A3 字段扩展

**Files:**
- Continue from PR #36（已 base=main）
- Modify: `veriguard-api/src/main/resources/db/migration/V6__smtp_server_config.sql`
- Create: `veriguard-api/src/main/java/io/veriguard/integration/smtp/SmtpServerConfig.java`
- Modify: `veriguard-front/src/admin/components/injects/EmailInjectEditor.tsx`

**Tasks:**

- [ ] **B2-1** 新表 `smtp_server_config`（id + name + host + port + username + password_encrypted + tls_mode）
- [ ] **B2-2** SMTP 服务器 CRUD 后端 + UI
- [ ] **B2-3** 邮件 inject 编辑器关联 SMTP 服务器配置
- [ ] **B2-4** 集成测试：本地 mailpit / mailcatcher → 真实邮件发送
- [ ] **B2-5** 与甲方约定测试 SMTP 服务器对接联调
- [ ] **B2-6** PR #36 增补 commit + 合并到 main

**AC:**
- 与甲方测试邮箱服务器联调通过
- IPv6 SMTP 服务器地址（如有）支持

**估时：** 3-4 天

**风险：** spec §7 待澄清项 #4（测试邮箱服务器）需第 1 周拿到

---

### PR B3：pcap_inject 完成 + 多端口四元组

**Goal:** 完成 B-ii PR-C pcap inject，并补"单 pcap + 多端口四元组"声明能力。

**前置：** A3

**Files:**
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/inject/PcapInjectPayload.java`
- Create: `veriguard-api/src/main/java/io/veriguard/inject/PcapInjectExecutor.java`
- Create: `veriguard-api/src/main/java/io/veriguard/inject/PcapReplayer.java`（基于 tcpreplay 或自研）
- Create: `veriguard-front/src/admin/components/injects/PcapInjectEditor.tsx`
- Modify: `veriguard-api/src/main/resources/db/migration/V7__pcap_inject_type.sql`

**Tasks:**

- [ ] **B3-1** Flyway V7：`inject_type` 添加 `pcap_inject`
- [ ] **B3-2** `PcapInjectPayload`：`{pcap_file_id, port_tuples: list[...], replay_mode: sequential|concurrent, interval_ms}`
- [ ] **B3-3** 端口元组上限校验（≤64）+ dstPort 必须不同校验
- [ ] **B3-4** `PcapReplayer`：包装 tcpreplay 调用，支持端口改写参数
- [ ] **B3-5** 顺序回放：循环 + 间隔；并发回放：线程池
- [ ] **B3-6** 编辑器 UI：pcap 上传 + 四元组表格（行级增删）+ 执行模式 radio
- [ ] **B3-7** 单测 + 集成测试（含一个真实 pcap fixture）
- [ ] **B3-8** 提交 PR base=main

**AC:**
- 单 pcap 文件 + 多组四元组可正确回放
- 端口改写功能验证（用 tcpdump 抓回放包确认端口正确）
- 顺序 / 并发模式都通过测试

**估时：** 1 周

---

### PR B4：sample_inject 链路（依赖 A2）

**Goal:** 完成样本上传 inject 类型，与 wangjuelong/cape 沙箱对接，支持沙箱报告展示。

**前置：** A2（CAPE 适配器）

**Files:**
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/inject/SampleInjectPayload.java`
- Create: `veriguard-api/src/main/java/io/veriguard/inject/SampleInjectExecutor.java`
- Create: `veriguard-front/src/admin/components/injects/SampleInjectEditor.tsx`
- Create: `veriguard-front/src/admin/components/sandbox/SandboxReportViewer.tsx`
- Modify: `veriguard-api/src/main/resources/db/migration/V8__sample_inject_type.sql`

**Tasks:**

- [ ] **B4-1** Flyway V8：`inject_type` 添加 `sample_inject`
- [ ] **B4-2** `SampleInjectPayload`：`{sample_file_id, sandbox_profile_id, expected_behaviors: list[...]}`
- [ ] **B4-3** `SampleInjectExecutor`：调用 `SandboxAdapter` 提交任务 + 轮询 + 报告解析
- [ ] **B4-4** 编辑器 UI：样本上传 + 沙箱实例选择 + 预期行为标注
- [ ] **B4-5** 报告查看器 UI：行为日志 / 网络回连 / 文件落地三 tab
- [ ] **B4-6** 单测 + 集成测试（用 CAPE mock）
- [ ] **B4-7** 提交 PR base=main

**AC:**
- 上传 .exe / .dll 样本，沙箱执行后报告可展示
- 8 类样本场景的预期行为标注与实际行为对比

**估时：** 1 周

---

### PR B5：用例库扩充至 ≥1200 条

**Goal:** 用例库初始数据扩充，满足 §3.4 / §3.5 / §4.3 / §5.1 总量与分类要求。

**前置：** A3（字段扩展）+ B1–B4（inject 类型完整）

**Files:**
- Create: `veriguard-api/src/main/resources/data/node_contracts/boundary_waf_500.json`（边界 WAF 12 类 ≥500 条）
- Create: `veriguard-api/src/main/resources/data/node_contracts/traffic_nta_300.json`（流量 NTA 11 类 ≥300 条 × IPv6 双版本）
- Create: `veriguard-api/src/main/resources/data/node_contracts/host_hids_300.json`（主机 HIDS 12 类 ≥300 条）
- Create: `veriguard-api/src/main/resources/data/node_contracts/vuln_poc_100.json`（漏洞 PoC 8 类 ≥100 条）
- Create: `veriguard-api/src/main/java/io/veriguard/data/InitialContractsLoader.java`

**Tasks:**

- [ ] **B5-1** 组建用例编写小组（建议 2 名安全工程师 + 1 名 Veriguard 研发）
- [ ] **B5-2** 边界 WAF 12 类 ≥500 条用例编写（命令执行 / 目录遍历 / 弱口令 / 暴力破解 / SQL 注入 / SSRF / CSRF / XSS / XXE / SSTI / 畸形上传 / 大包上传）—— 可参考 PayloadsAllTheThings、PortSwigger 课程
- [ ] **B5-3** 流量 NTA 11 类 ≥300 条 × IPv6 单栈双版本 —— 部分用例从社区 Snort / Suricata 规则反演
- [ ] **B5-4** 主机 HIDS 12 类 ≥300 条 —— 参考 Atomic Red Team / MITRE ATT&CK Evaluations
- [ ] **B5-5** 漏洞 PoC 8 类 ≥100 条 —— 从 ExploitDB / CVE 库挑选
- [ ] **B5-6** 用例命名规范统一：`<attack_category>-<target_software>-<variant>`
- [ ] **B5-7** 每条用例标注 `defense_layer` + `software_category` + `target_os` + `network_dependent` + `network_protocol_family` + ATT&CK Tactic/Technique + 杀伤链阶段 + 标签
- [ ] **B5-8** `InitialContractsLoader` 启动时幂等导入
- [ ] **B5-9** 用例可执行性自动化测试（每条用例至少跑一次干跑确认 schema 正确）
- [ ] **B5-10** 提交 PR base=main

**AC:**
- 用例库总数 ≥1200
- 4 大场景类别都满足"双满足"承诺
- 全部用例可在 IPv6 单栈环境执行（网络通信类）

**估时：** 3-4 周（用例编写工作量集中）

**风险：** 用例编写人力 / 时间 → 第 1 周组建小组；并行启动；社区资源 + 自动化生成相结合

---

## Phase C：核心 GAP 子模块（4–5 周，可与 B 并行）

### PR C1：veriguard-agent 项目骨架

**Goal:** 建立平台自有轻量验证 Agent 独立子项目，含通信协议、心跳、任务下发框架。

**前置：** A3（用例契约扩展，含 `rollback_steps`）

**Files:**
- Create: `veriguard-agent/` （新子项目）
  - `pom.xml`（Java 21 + GraalVM Native Image 候选）
  - `src/main/java/io/veriguard/agent/AgentMain.java`
  - `src/main/java/io/veriguard/agent/CommunicationClient.java`（TLS over IPv6）
  - `src/main/java/io/veriguard/agent/HeartbeatScheduler.java`
  - `src/main/java/io/veriguard/agent/TaskRunner.java`
  - `src/main/java/io/veriguard/agent/RollbackEngine.java`（解析 rollback_steps）
- Modify: `pom.xml`（根 aggregator 加 veriguard-agent module）
- Create: `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentController.java`（接收 Agent 注册 / 心跳 / 任务结果回传）
- Create: `veriguard-api/src/main/resources/db/migration/V9__agent_registry.sql`

**Tasks:**

- [ ] **C1-1** 新建 veriguard-agent 子项目 + 根 pom 注册
- [ ] **C1-2** Agent 与平台通信协议设计：gRPC over TLS 或 HTTPS（建议 HTTPS 简化）
- [ ] **C1-3** Agent 启动 → 注册到平台 → 心跳保活
- [ ] **C1-4** 平台侧：`agent_registry` 表 + 注册 / 心跳 API
- [ ] **C1-5** 任务下发协议：平台 → Agent 推送任务，Agent 执行后回传
- [ ] **C1-6** `RollbackEngine`：解析 `rollback_steps` JSONB（删文件 / 杀进程 / 回滚注册表 / 清持久化）
- [ ] **C1-7** 单测：Agent 单元测试 + 通信协议测试
- [ ] **C1-8** Agent 干扰评估文档（提交甲方批准的素材）
- [ ] **C1-9** 提交 PR base=main

**AC:**
- Agent 可独立打包（jar / native）
- Agent 在 Linux + Windows 双 OS 启动通过
- 与平台心跳 / 任务下发 / 结果回传链路联调通过

**估时：** 1.5-2 周

**风险：** Agent 部署到甲方靶机需获批 → C1-8 文档优先

---

### PR C2：veriguard-agent 主机用例执行能力 + 靶机白名单

**Goal:** Agent 支持执行 12 类主机攻击用例，并强制"仅靶机执行"硬约束。

**前置：** C1（Agent 骨架）

**Files:**
- Modify: `veriguard-agent/src/main/java/io/veriguard/agent/TaskRunner.java`
- Create: `veriguard-agent/src/main/java/io/veriguard/agent/ScriptExecutor.java`（sh / bash / cmd / powershell）
- Create: `veriguard-agent/src/main/java/io/veriguard/agent/BinaryDeployer.java`
- Create: `veriguard-api/src/main/java/io/veriguard/rest/target_machine/TargetMachineWhitelistController.java`
- Modify: `veriguard-api/src/main/resources/db/migration/V10__target_machine_whitelist.sql`

**Tasks:**

- [ ] **C2-1** 新表 `target_machine_whitelist`（host_id + agent_id + machine_type ∈ {target, production} + approved_by + approved_at）
- [ ] **C2-2** Agent 任务下发前校验：平台 API `/api/inject/validate-target` 检查目标主机是否在白名单
- [ ] **C2-3** Agent 端 `ScriptExecutor`：sh / bash / cmd / powershell 多 shell 支持
- [ ] **C2-4** Agent 端 `BinaryDeployer`：下载 → 部署到指定路径 → 执行 → 清理
- [ ] **C2-5** 回传输出：stdout / stderr / exit_code 完整捕获
- [ ] **C2-6** 集成测试：在 docker 容器内模拟靶机，跑 12 类用例覆盖
- [ ] **C2-7** 还原引擎集成：每条用例执行后自动 rollback
- [ ] **C2-8** 提交 PR base=main

**AC:**
- 12 类主机用例在靶机上全部可执行
- 不在白名单的主机调用执行 API 时返回 403 + 审计日志
- 还原失败时 Agent 标自身 unavailable + 告警

**估时：** 1.5 周

---

### PR C3：boundary_coverage 子模块（§3.1 + §4.1 复用）

**Goal:** 实现网络边界 + 流量 双场景的"资产 × 策略"覆盖矩阵，含两次结果对比。

**前置：** A1（NxSOC）+ A3（字段扩展）

**Files:**
- Create: `veriguard-api/src/main/resources/db/migration/V11__boundary_coverage.sql`
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/coverage/CoverageBaseline.java`
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/coverage/CoverageRun.java`
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/coverage/CoverageResult.java`
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/coverage/Policy.java`
- Create: `veriguard-api/src/main/java/io/veriguard/coverage/CoverageRunner.java`
- Create: `veriguard-api/src/main/java/io/veriguard/coverage/CoverageDiffCalculator.java`
- Create: `veriguard-front/src/admin/components/coverage/CoverageMatrixView.tsx`
- Create: `veriguard-front/src/admin/components/coverage/CoverageDiffView.tsx`

**Tasks:**

- [ ] **C3-1** Flyway V11：
  - `coverage_baseline`（id + name + coverage_type ∈ {boundary, traffic} + case_ids[] + asset_group_id）
  - `coverage_run`（id + baseline_id + started_at + finished_at + status）
  - `coverage_result`（id + run_id + asset_id + policy_id + hit_state ∈ {hit, miss, timeout, out_of_scope}）
  - `policy`（id + name + device_type ∈ {waf, ips, ids, nta, hids} + device_id）
- [ ] **C3-2** JPA 实体 + Repository
- [ ] **C3-3** `CoverageRunner` 后端流程：
  - 取 baseline → 取 asset_group → 按用例 × 资产笛卡尔积发起验证
  - 通过现有攻击编排接口 / 直接 inject 执行
  - 任务结束后调 `SocAdapter.queryAlerts` → 提取 ruleId → 写入 `coverage_result`
- [ ] **C3-4** 4 态单元格逻辑：hit / miss / timeout / out_of_scope
- [ ] **C3-5** `CoverageDiffCalculator`：两次 run 对比生成 diff（新增 / 减少 / 变化）
- [ ] **C3-6** REST API：CRUD baseline + 启动 run + 查询 matrix + 对比 diff
- [ ] **C3-7** 前端矩阵 UI：行=资产 / 列=策略 / 4 色单元格 + 点击下钻
- [ ] **C3-8** 前端对比 UI：选两 run → diff 表
- [ ] **C3-9** 截图素材（招标明示）
- [ ] **C3-10** 提交 PR base=main

**AC:**
- 边界覆盖度（§3.1） + 流量覆盖度（§4.1）共用同一数据模型，UI 风格统一
- 矩阵可展示 ≥100 个资产 × N 个策略，性能良好
- 两次对比 diff 准确

**估时：** 2 周

---

### PR C4：boundary_monitoring 子模块（§3.2）

**Goal:** 实现安全设备策略有效性常态化监控，含 Quartz 调度、日 / 小时趋势可视化。

**前置：** C3（复用 coverage_run）

**Files:**
- Create: `veriguard-api/src/main/resources/db/migration/V12__boundary_monitoring.sql`
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/monitoring/MonitoringJob.java`
- Create: `veriguard-api/src/main/java/io/veriguard/monitoring/MonitoringScheduler.java`
- Create: `veriguard-api/src/main/java/io/veriguard/scheduler/jobs/BoundaryMonitoringJob.java`
- Create: `veriguard-front/src/admin/components/monitoring/MonitoringTrendView.tsx`

**Tasks:**

- [ ] **C4-1** Flyway V12：`monitoring_job`（id + name + cron + baseline_id + asset_set + device_set + enabled）
- [ ] **C4-2** Elasticsearch 索引 `boundary-monitoring-history`（按月分片）
- [ ] **C4-3** `BoundaryMonitoringJob`：Quartz 任务，按 cron 触发 baseline 跑一次，结果写入 ES
- [ ] **C4-4** REST API：监控任务 CRUD + 趋势查询
- [ ] **C4-5** 前端配置 UI：cron 编辑器 + baseline / asset / device 选择
- [ ] **C4-6** 前端趋势 UI：Recharts 时间序列，按日 / 按小时切换；单策略下钻
- [ ] **C4-7** cron 校验：最小粒度 1h
- [ ] **C4-8** 提交 PR base=main

**AC:**
- 监控任务可调度且持久化
- 日 / 小时趋势图 UI 流畅
- 单策略下钻路径清晰

**估时：** 1 周

---

### PR C5：stability_engine 子模块（★1 §3.3 + ★3 §4.2）

**Goal:** 实现"重复 N 次 + 命中率 + 趋势"稳定性计算引擎，被边界、流量两场景复用。

**前置：** C3（复用 coverage_run）

**Files:**
- Create: `veriguard-api/src/main/resources/db/migration/V13__stability_trend.sql`
- Create: `veriguard-api/src/main/java/io/veriguard/stability/StabilityCalculator.java`
- Create: `veriguard-api/src/main/java/io/veriguard/stability/StabilityRepository.java`
- Create: `veriguard-front/src/admin/components/stability/StabilityTrendView.tsx`

**Tasks:**

- [ ] **C5-1** Flyway V13：`stability_trend_snapshot`（id + device_id + baseline_id + run_id + hit_rate + n + timestamp）
- [ ] **C5-2** 攻击链路 / 任务配置加"稳定性模式"开关（复用现有 repeat_count 字段）
- [ ] **C5-3** `StabilityCalculator`：任务结束后聚合 N 次结果 → 命中率（命中次数 / N）→ 写入 snapshot
- [ ] **C5-4** REST API：查询稳定性趋势（按设备 / 按 baseline）
- [ ] **C5-5** 前端趋势 UI：每点一个任务，悬停展示明细；按天聚合切换
- [ ] **C5-6** N 上限校验：1 < N ≤ 100
- [ ] **C5-7** ★1 + ★3 截图素材
- [ ] **C5-8** 提交 PR base=main

**AC:**
- N=10 默认值生效
- 趋势图按任务 / 按天 两视图切换流畅
- 与 §3.3（边界）/ §4.2（流量）两个场景都验证通过

**估时：** 1 周

---

### PR C6：三态结果聚合 + 防御层视图 + 7.5 SOC 6 维匹配

**Goal:** 实现链路级三态结果（§7.8）+ 自定义场景的纵深防御 5 层视图（§6.1）+ SOC 6 维匹配（§7.5）。

**前置：** A1（NxSOC）+ A3（defense_layer 字段）

**Files:**
- Create: `veriguard-api/src/main/resources/db/migration/V14__chain_result_and_soc_match.sql`
- Modify: `veriguard-model/src/main/java/io/veriguard/database/model/attack_chain/AttackChainRun.java`
- Create: `veriguard-api/src/main/java/io/veriguard/chain/ChainResultAggregator.java`
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/soc/SocMatchCondition.java`
- Create: `veriguard-api/src/main/java/io/veriguard/chain/SocMatchEvaluator.java`
- Modify: `veriguard-front/src/admin/components/attack_chains/AttackChainDetail.tsx`（加防御层 tab）
- Create: `veriguard-front/src/admin/components/attack_chains/DefenseLayerView.tsx`
- Modify: `veriguard-front/src/admin/components/attack_chain_runs/AttackChainRunDetail.tsx`（加三态徽标）

**Tasks:**

- [ ] **C6-1** Flyway V14：
  - `attack_chain_run` 加 `chain_result ∈ {fully_effective, partially_failed, fully_failed, in_progress}`
  - 新表 `soc_match_condition`（chain_run_id + 6 维字段：asset_ip / time_window / severity / rule_id / rule_category / keywords）
- [ ] **C6-2** `ChainResultAggregator`：聚合所有节点 result_state → 计算 chain_result（防御视角）
- [ ] **C6-3** 任务列表 / 详情 UI 加三态徽标（绿 / 黄 / 红）+ 筛选维度
- [ ] **C6-4** `SocMatchEvaluator`：基于 6 维匹配条件 + `SocAdapter` 查询告警
- [ ] **C6-5** 任务级 SOC 匹配编辑器 UI（6 字段 + AND 逻辑）
- [ ] **C6-6** 节点级 SOC 匹配覆盖 UI（折叠面板）
- [ ] **C6-7** 自定义场景详情页加"纵深防御"tab（5 列 × N 行可视化）
- [ ] **C6-8** ★4 截图素材
- [ ] **C6-9** 提交 PR base=main

**AC:**
- 三态徽标在任务列表 / 详情 / 仪表板正确显示
- 三态可作为筛选维度
- 防御层 tab 与 ATT&CK 矩阵 tab 并列流畅切换
- SOC 6 维匹配在攻击链路与各覆盖度场景中复用

**估时：** 1.5 周

---

## Phase D：★2 攻击组合（3–4 周，重型独立模块）

### PR D1：绕过维度库 + 攻击组合模板

**Goal:** 建立 120 项绕过维度库 + 攻击组合模板模型。

**前置：** A3 + B5（用例库 ≥250 类基础）

**Files:**
- Create: `veriguard-api/src/main/resources/db/migration/V15__attack_combination_init.sql`
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/combination/BypassDimension.java`
- Create: `veriguard-model/src/main/java/io/veriguard/database/model/combination/AttackCombinationTemplate.java`
- Create: `veriguard-api/src/main/resources/data/bypass_dimensions/120_dimensions.json`

**Tasks:**

- [ ] **D1-1** Flyway V15：
  - `bypass_dimension`（id + name + category + description + payload_transform）
  - `attack_combination_template`（id + base_attack_type + bypass_dim_id + combined_payload_template）
- [ ] **D1-2** 绕过维度 120 项初始数据（编码 / 分块 / 大小写 / 参数顺序 / 噪声 / Unicode / Comment 等多类）
- [ ] **D1-3** payload_transform 实现：根据 dim 类型对 base payload 应用转换
- [ ] **D1-4** REST API：维度查询 + 模板生成（base_types × dims = combinations）
- [ ] **D1-5** 单测：每种维度 transform 正确
- [ ] **D1-6** 提交 PR base=main

**AC:**
- 120 项维度数据可用
- 250 × 120 ≈ 30 000 组合数学上可生成

**估时：** 1 周

---

### PR D2：攻击组合生成器 + 限流调度器

**Goal:** 生成 30 000 个组合任务，按限流策略并发执行，避免对设备过载。

**前置：** D1

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/combination/AttackCombinationGenerator.java`
- Create: `veriguard-api/src/main/java/io/veriguard/combination/CombinationScheduler.java`（限流 + 队列）
- Create: `veriguard-api/src/main/resources/db/migration/V16__attack_combination_run.sql`

**Tasks:**

- [ ] **D2-1** Flyway V16：
  - `attack_combination_run`（id + name + base_attack_types[] + bypass_dims[] + asset_set + status + total_combinations + completed_count）
  - `attack_combination_result`（id + run_id + combination_id + asset_id + hit_state + executed_at）
- [ ] **D2-2** `AttackCombinationGenerator`：base × dim 笛卡尔积，生成 combination instances
- [ ] **D2-3** `CombinationScheduler`：基于 RabbitMQ 队列 + 限流（默认 100 req/s 可配）
- [ ] **D2-4** 单次任务超时：默认 24h，可配
- [ ] **D2-5** 失败重试机制：单 combination 失败重试 3 次后标 failed
- [ ] **D2-6** REST API：任务创建 + 状态查询 + 暂停 / 恢复 / 取消
- [ ] **D2-7** 集成测试：3 base × 5 dim = 15 组合的小规模联调
- [ ] **D2-8** 提交 PR base=main

**AC:**
- 30 000 组合任务可在合理时间内（如 8h）完成
- 限流参数生效，不对设备造成过载
- 失败 combination 不阻塞整体任务

**估时：** 1.5 周

**风险：** 30 000 组合并发对甲方设备的影响 → 限流默认值保守 + 与甲方协商执行窗口

---

### PR D3：聚类算法（设备 + 资产视角）

**Goal:** 攻击组合任务完成后，自动按 (设备, 资产) 维度对未防护项分组。

**前置：** D2

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/combination/ClusterAnalyzer.java`
- Create: `veriguard-api/src/main/resources/db/migration/V17__attack_combination_cluster.sql`

**Tasks:**

- [ ] **D3-1** Flyway V17：`attack_combination_cluster`（id + run_id + cluster_key + cluster_dim ∈ {device, asset} + size + payload_examples[]）
- [ ] **D3-2** `ClusterAnalyzer`：
  - 设备视角：未防护组合按 device_id 分组
  - 资产视角：未防护组合按 asset_id 分组
  - 每个 cluster 保留前 N 个 payload 样例
- [ ] **D3-3** 聚类计算可异步触发：任务结束自动 + 手动重算 API
- [ ] **D3-4** REST API：cluster 查询 + payload 下钻
- [ ] **D3-5** 单测：固定 fixture 验证聚类逻辑
- [ ] **D3-6** 提交 PR base=main

**AC:**
- 聚类结果可下钻到 payload 级
- 设备 + 资产双视角切换流畅

**估时：** 4-5 天

---

### PR D4：分级算法 + 客户可配置

**Goal:** 对未防护项自动分级（默认 4 级 + 客户可配置阈值）。

**前置：** D3

**Files:**
- Create: `veriguard-api/src/main/java/io/veriguard/combination/SeverityClassifier.java`
- Create: `veriguard-api/src/main/resources/db/migration/V18__severity_config.sql`
- Modify: `veriguard-front/src/admin/components/combination/SeverityConfigEditor.tsx`

**Tasks:**

- [ ] **D4-1** Flyway V18：`severity_config`（id + tenant_id + level_name + threshold_min + threshold_max + color）
- [ ] **D4-2** 默认 4 级：高（>70）/ 中（40-70）/ 低（10-40）/ 信息（<10）；评分基于"未防护组合数 + 攻击类型严重度 + 资产敏感度"
- [ ] **D4-3** `SeverityClassifier`：对每个 cluster 计算 severity_score → 落到分级
- [ ] **D4-4** 客户可配置阈值与命名的 UI 编辑器
- [ ] **D4-5** 单测 + 集成测试
- [ ] **D4-6** 提交 PR base=main

**AC:**
- 默认 4 级分级算法生效
- 客户可调整阈值与级别命名

**估时：** 4-5 天

---

### PR D5：聚类视图 + 下钻 UI + ★2 截图

**Goal:** 完整的攻击组合 UI：任务创建、运行画布、聚类视图、分级展示、下钻到 payload。

**前置：** D2 + D3 + D4

**Files:**
- Create: `veriguard-front/src/admin/components/combination/CombinationTaskEditor.tsx`
- Create: `veriguard-front/src/admin/components/combination/CombinationRunCanvas.tsx`
- Create: `veriguard-front/src/admin/components/combination/ClusterTreeView.tsx`
- Create: `veriguard-front/src/admin/components/combination/PayloadDrilldown.tsx`

**Tasks:**

- [ ] **D5-1** 任务创建 UI：勾选 base_types（≥250 选项）+ 绕过维度（默认全选 120 维）+ 资产组
- [ ] **D5-2** 运行画布：实时进度条 + 已完成 / 总数 + ETA
- [ ] **D5-3** 聚类树视图：设备 / 资产两 tab；每 cluster 展示 size + severity badge
- [ ] **D5-4** Payload 下钻：点击 cluster → 展开 payload 列表 → 单 payload 详情（含响应 + 是否被拦截）
- [ ] **D5-5** 分级配置入口
- [ ] **D5-6** ★2 截图素材准备
- [ ] **D5-7** 提交 PR base=main

**AC:**
- 30 000 组合任务全流程 UI 可用
- 截图覆盖招标 §3.6 要求

**估时：** 1.5 周

---

## Phase E：验收准备（1–2 周）

### PR E1：截图素材集中准备

**Goal:** 按招标 9 项明示截图要求 + 内部建议截图，集中产出高质量素材，归档到 `docs/screenshots/`。

**前置：** Phase B/C/D 主体功能完成

**Files:**
- Create: `docs/screenshots/`（新目录）
- Create: `docs/screenshots/INDEX.md`（截图与招标条款对应表）

**Tasks:**

- [ ] **E1-1** 列截图清单（参考研发拆解 §10）
- [ ] **E1-2** 准备演示数据集（典型场景 / 用例 / 任务结果）
- [ ] **E1-3** 批量截图（人工 + Playwright 自动化辅助）
- [ ] **E1-4** 截图标注与编号
- [ ] **E1-5** `INDEX.md` 索引文档
- [ ] **E1-6** 提交 PR base=main

**AC:**
- 9 项明示截图（含 ★1-★4 + §8.2 "否则视为无效"）全部到位
- 截图清晰、内容真实、与招标原文对得上

**估时：** 1 周

---

### PR E2：UAT 测试用例集

**Goal:** 编写《验收测试用例集》，覆盖 25 条招标要求逐条对应的测试用例。

**前置：** 主体功能完成

**Files:**
- Create: `docs/uat/uat-test-cases.md`
- Create: `docs/uat/test-data-setup.md`

**Tasks:**

- [ ] **E2-1** 按 25 条招标要求编写 25 组 UAT 测试用例（每组含前置 / 步骤 / 预期 / 实际）
- [ ] **E2-2** 测试数据初始化脚本（用例库 / 资产 / 沙箱 / 监控任务）
- [ ] **E2-3** 内部 dry-run UAT（实施方先跑一遍）
- [ ] **E2-4** 与甲方约定联合 UAT 时间
- [ ] **E2-5** 提交 PR base=main

**AC:**
- 25 条要求 1:1 对应测试用例
- 测试数据可一键初始化

**估时：** 3-4 天

---

### PR E3：验收文档输出

**Goal:** 产出招标 §2.3.6 项目交付文档中本系统相关的 8 份 Word 文档。

**前置：** 全部 PR 完成

**Files:**
- Create: `docs/deliverables/01-现状分析报告.md` → 转 Word
- Create: `docs/deliverables/02-IPv6安全验证功能设计.md` → 转 Word（基于 docs/IPv6安全验证系统-技术方案.md 扩写）
- Create: `docs/deliverables/03-IPv6安全策略配置规范.md` → 转 Word
- Create: `docs/deliverables/04-攻击特征库更新机制.md` → 转 Word
- Create: `docs/deliverables/05-IPv6安全验证体系实施方案.md` → 转 Word
- Create: `docs/deliverables/06-IPv6安全验证平台及智能体操作手册.md` → 转 Word
- Create: `docs/deliverables/07-IPv6安全验证体系建设差距分析报告.md` → 转 Word（基于 GAP 分析）
- Create: `docs/deliverables/08-IPv6安全验证体系构建成效报告.md` → 转 Word

**Tasks:**

- [ ] **E3-1** 准备 8 份 Markdown 文档框架
- [ ] **E3-2** 复用现有 spec / 技术方案 / GAP 分析 / 研发拆解的内容填充
- [ ] **E3-3** 使用 pandoc / 内部工具转 Word
- [ ] **E3-4** 双语校对（中文为主，英文术语规范）
- [ ] **E3-5** 提交 PR base=main

**AC:**
- 8 份 Word 文档齐备
- 内容与平台实现保持一致

**估时：** 1 周

---

## 关键依赖与风险汇总

### 外部依赖（项目启动第 1 周必须落停）

| # | 依赖项 | 影响范围 | 责任方 |
| --- | --- | --- | --- |
| 1 | 蓝盾 NxSOC API 文档 | A1 / C3 / C4 / C6 全部 | 甲方 |
| 2 | wangjuelong/cape API 稳定版 | A2 / B4 | 自有项目协调 |
| 3 | 测试 SMTP 服务器 | B2 | 甲方 |
| 4 | 靶机部署 + Agent 安装授权 | C1 / C2 | 甲方 |
| 5 | IPv6 双栈基础设施就绪 | 全部 | 甲方 |

### 风险点（与 GAP §4.2 一致）

| 风险 | 缓解 |
| --- | --- |
| NxSOC 文档延期 | 启用 syslog 备选；先用 mock 推进 |
| CAPE API 不稳定 | fixture 数据继续推进 B 阶段 |
| Agent 授权延期 | C1-8 干扰评估文档优先；备选无 Agent 模式 |
| 30 000 组合对设备过载 | 限流保守默认 + 与甲方协商执行窗口 |
| 用例库扩充 1200 条工作量 | 提前组队 + 社区资源 + 自动化生成 |
| IPv6 单栈不就绪 | 第 1 周连通性测试，不通畅项进网络变更 |
| 截图素材准备时间紧 | Phase B 完成时即开始；批量自动化辅助 |

---

## 自审记录

**Spec 覆盖检查：**
- ✅ §3 边界防护（6 条）→ A3 / B5 / C3 / C4 / C5 / D1-D5
- ✅ §4 流量安全（4 条）→ A3 / B3 / B5 / C3 / C5
- ✅ §5 主机安全（1 条）→ A3 / B5 / C1 / C2
- ✅ §6 自定义验证（3 条）→ A3 / B1-B4 / C6（防御层 UI）；B-iii ✅ 无需 PR
- ✅ §7 攻击编排（8 条）→ ✅ 大部已实现 + A1 / C6 补缺
- ✅ §8 沙箱管理（3 条）→ A2 / B4

**Placeholder 扫描：** 无 TBD / TODO；所有 Task 含明确 AC 与 Files。

**类型一致性：** 数据模型字段（`software_category` / `defense_layer` / `network_protocol_family` / `target_os` / `network_dependent` / `rollback_steps`）在 A3 定义后，B1–D5 各 PR 引用保持一致。

**截图清单：** A3 / B5 / C3 / C5 / C6 / D5 / E1 全部产出招标明示的 9 处截图。

---

*Plan complete.*
