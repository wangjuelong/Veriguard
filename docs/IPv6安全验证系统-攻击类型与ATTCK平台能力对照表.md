# IPv6 安全验证系统 — 攻击类型与 ATT&CK / 平台能力对照表

> 招标锚点：§3.5（12 类边界攻击效用矩阵）+ 用例数量门槛（边界 ≥ 500 / 流量 ≥ 300 / 主机 ≥ 300）
> 整理日期：2026 年 5 月
> 用途：交付资料 — 三模块 35 具名攻击类型 → ATT&CK Technique + 平台 executor + Agent capability + 用例数据集的端到端对照

## 0. 总览

| 模块 | 招标攻击类型门槛 | 招标用例数门槛 | 已具名攻击类型 | 已落地用例数 | 主要 ATT&CK 战术 |
| --- | --- | --- | --- | --- | --- |
| 边界防护（WAF / IPS）| ≥ 10 | ≥ 500 | 12 | 1 104（PR B6 155 + PR B8 949）| TA0001 / TA0002 / TA0006 / TA0009 |
| 流量安全（NTA / IDS）| ≥ 10 | ≥ 300 | 11 | 0 → 315 目标（PR B9 待）| TA0008 / TA0010 / TA0011 |
| 主机安全（HIDS / EDR）| ≥ 10 | ≥ 300 | 12 | 0 → 310 目标（用例库内容建设阶段）| TA0002 / TA0003 / TA0004 / TA0005 / TA0011 |

> 动态攻击组合（≥ 250 类 × ≥ 30 000 组合）由组合引擎生成；沙箱真实样本（≥ 8 类）单列。本表仅覆盖三模块**具名**攻击类型。

## 1. 边界防护 12 类 — 已闭环

执行链路：`SecurityValidationApi` → `WebAttackDispatchService` → `HttpInjectExecutor` → `/api/agent/task-queue/poll` → veriguard-agent `http_attack` capability → HTTP request 直发目标 → 结果回填 → 三态判定（PREVENTION / DETECTION / ATTACK SUCCESS）。

| # | 攻击类型 | ATT&CK Technique | 用例数据集 | 实际条数 | 配比建议 | 缺口 | Nuclei 来源 tag |
| --- | --- | --- | --- | ---: | ---: | --- | --- |
| 1 | 命令执行 | T1059 Command and Scripting Interpreter | `boundary_b8/rce.json` | 140 | ≥ 40 | — | `rce` |
| 2 | 目录遍历 | T1083 File and Directory Discovery | `boundary_b8/lfi.json` | 140 | ≥ 30 | — | `lfi` |
| 3 | 弱口令登录 | T1110.001 Password Guessing | `boundary_b8/default_login.json` | 140 | ≥ 30 | — | `default-login` |
| 4 | 暴力破解 | T1110 Brute Force | `boundary_b6/brute_force.json` | 31 | ≥ 40 | -9 | — (curated) |
| 5 | SQL 注入 | T1190 Exploit Public-Facing App | `boundary_b8/sqli.json` | 140 | ≥ 60 | — | `sqli` |
| 6 | 服务器端请求伪造（SSRF）| T1190 / T1200 | `boundary_b8/ssrf.json` | 140 | ≥ 40 | — | `ssrf` |
| 7 | 跨站请求伪造（CSRF）| T1659 Content Injection | `boundary_b6/csrf.json` | 31 | ≥ 30 | — | — (curated) |
| 8 | 跨站脚本（XSS）| T1059.007 JavaScript | `boundary_b8/xss.json` | 140 | ≥ 50 | — | `xss` |
| 9 | XML 外部实体（XXE）| T1190 / T1213 | `boundary_b6/xxe.json` | 31 | ≥ 30 | — | — (curated) |
| 10 | 服务器端模板注入（SSTI）| T1190 / T1059 | `boundary_b6/ssti.json` | 31 | ≥ 30 | — | — (curated) |
| 11 | 畸形表单上传绕过 | T1505.003 Web Shell / T1190 | `boundary_b8/file_upload.json` | 109 | ≥ 60 | — | `file-upload` |
| 12 | 大包上传绕过 | T1499 Endpoint DoS / T1190 | `boundary_b6/oversized_upload.json` | 31 | ≥ 60 | -29 | — (curated) |

**边界总量：1 104 条（招标硬门槛 ≥ 500，已达成 2.2 倍）。**

**配比缺口（2 类）：**
- 第 4 类 暴力破解：现 31 条，建议 ≥ 40，差 9 条 → 计划在用例库内容建设阶段补 9–20 条 curated payload。
- 第 12 类 大包上传绕过：现 31 条，建议 ≥ 60，差 29 条 → 同上，因纯 curated（无 nuclei tag）补量需手写攻击包。

> 配比建议为单类内部均衡参考，**不影响硬门槛达成**。

### 1.1 边界执行链路

```
攻击编排画布（前端：veriguard-front /admin/veriguard）
  ↓ 用户拖入 WebAttackPayload + 选目标
SecurityValidationApi POST /api/attack_chain_runs/{id}/start
  ↓
WebAttackDispatchService.dispatch()
  ├─ AgentService.selectByCapability("http_attack") → 选 Agent
  └─ 构造 task → AgentTaskQueueApi.poll 出队
veriguard-agent (Rust)
  └─ capabilities::HttpAttackCapability::execute() → HTTP 请求 → status/body
AgentTaskQueueApi POST /api/agent/task/{taskId}/result
  ├─ Ed25519 签名校验（platform pub）
  └─ 落库 + 推送 HttpInjectExecutor.handleResult
HttpInjectExecutor → 三态判定（基于 status 码 / WAF 拦截特征）
```

## 2. 流量安全 11 类

执行链路：`PcapReplayDispatchService` → `PcapReplayExecutor` → Agent `pcap_replay` / `dnsresolution` / `networktraffic` capability → 发送 `.pcap` / `.pcapng` 样本到镜像口 / 目标网段 → NTA/IDS 侧采集判定。

| # | 攻击类型 | ATT&CK Technique | Agent Capability | 载体类型 | 配比建议 |
| --- | --- | --- | --- | --- | ---: |
| 1 | 暴力破解 | T1110 Brute Force | pcap_replay | TCP/UDP 登录尝试 pcap | ≥ 30 |
| 2 | 反弹 shell | T1059 Command and Scripting Interpreter | pcap_replay | egress TCP shell pcap | ≥ 30 |
| 3 | 内存注入 webshell | T1620 Reflective Code Loading | pcap_replay | HTTP attack pcap | ≥ 25 |
| 4 | 隐秘隧道 | T1572 Protocol Tunneling | networktraffic | DNS / ICMP / HTTP 隧道 pcap | ≥ 30 |
| 5 | 恶意域名解析 | T1071.004 DNS / T1568 Dynamic Resolution | dnsresolution | DNS query pcap | ≥ 25 |
| 6 | webshell 命令执行 | T1505.003 Server Software Component: Web Shell | pcap_replay | HTTP webshell pcap | ≥ 25 |
| 7 | 高危漏洞利用 | T1190 Exploit Public-Facing App | pcap_replay | CVE 利用 pcap | ≥ 40 |
| 8 | 远控木马执行 | T1219 Remote Access Software | networktraffic | C2 心跳 / RAT 流量 pcap | ≥ 30 |
| 9 | 权限绕过 | T1548 Abuse Elevation Control Mechanism | pcap_replay | HTTP 越权 pcap | ≥ 25 |
| 10 | 未授权访问 | T1078 Valid Accounts | pcap_replay | HTTP 未授权 pcap | ≥ 25 |
| 11 | 信息泄露 | T1213 Data from Information Repositories | pcap_replay | HTTP 数据泄露 pcap | ≥ 30 |

**流量目标总量：≥ 315 条（招标门槛 ≥ 300）。**

> 招标 §流量验证额外约束：同一个流量验证用例需支持包含**多个端口不同的四元组**（即一个用例可以涵盖多个 `src_ip:src_port` → `dst_ip:dst_port` 流量样本）。`PcapReplayExecutor` 已支持多 4-元组 pcap 内嵌。

### 2.1 流量数据集落地状态

| 项 | 状态 | 备注 |
| --- | --- | --- |
| `PcapReplayExecutor` 真接通（Java）| 已完成 | PR #57 |
| Agent `pcap_replay` capability（Rust）| 已完成 | veriguard-agent#2 |
| Agent `dnsresolution` capability | 计划 | C1-Integration / veriguard-implant Mode C |
| Agent `networktraffic` capability | 计划 | 同上 |
| PR B9 pcap 数据集导入器 | 未启动 | 1–2 周；批量灌入 11 类 ≥ 315 条 pcap 样本 |

## 3. 主机安全 12 类

执行链路：`CommandInjectDispatchService` → `CommandInjectExecutor` → Agent `command_inject` capability → drop `veriguard-implant` 子进程 → 5 类 payload（Command / Executable / FileDrop / DnsResolution / NetworkTraffic）执行 → NDJSON 结果回管道 → Agent 上报。

| # | 攻击类型 | ATT&CK Technique | Implant Payload Type | 配比建议 |
| --- | --- | --- | --- | ---: |
| 1 | 反弹 shell | T1059 Bash / Python / PowerShell / Netcat | Command | ≥ 30 |
| 2 | webshell 上传落盘 | T1505.003 Server Software Component: Web Shell | FileDrop + Command | ≥ 25 |
| 3 | 命令执行 | T1059 Command and Scripting Interpreter | Command | ≥ 25 |
| 4 | 隧道代理 | T1572 Protocol Tunneling（SSH / SOCKS / reGeorg）| Executable | ≥ 25 |
| 5 | 内存注入 webshell | T1620 Reflective Code Loading（Java / .NET / PHP）| Executable | ≥ 25 |
| 6 | 暴力破解 | T1110 Brute Force（系统 / DB / 应用账号）| Command（loop）| ≥ 25 |
| 7 | 远控木马执行 | T1219 Remote Access Software | Executable + FileDrop | ≥ 30 |
| 8 | 系统提权 | T1068 Privilege Escalation（Linux / Windows）| Executable | ≥ 30 |
| 9 | 网站篡改 | T1565 Data Manipulation / T1491 Defacement | FileDrop | ≥ 20 |
| 10 | 病毒样本落盘 | T1105 Ingress Tool Transfer | FileDrop | ≥ 25 |
| 11 | 痕迹清理 | T1070 Indicator Removal | Command | ≥ 20 |
| 12 | 主机持久化 | T1053 Scheduled Task / T1543 Service / T1547 启动项 / T1546 Registry / cron | Command + FileDrop | ≥ 30 |

**主机目标总量：≥ 310 条（招标门槛 ≥ 300）。**

### 3.1 主机执行链路

```
攻击编排画布
  ↓ 用户拖入 CommandInjectPayload + 选 Host 资产
SecurityValidationApi POST /api/attack_chain_runs/{id}/start
  ↓
CommandInjectDispatchService.dispatch()
  ├─ AgentService.selectByCapability("command") → 选已上线 Agent
  └─ 构造 task → AgentTaskQueueApi.poll 出队
veriguard-agent (Rust)
  └─ capabilities::CommandInjectCapability::execute()
       ├─ implant_manager.spawn() → 启动 veriguard-implant 子进程
       └─ 命名管道（FIFO / Win Named Pipe）传 payload + 收 NDJSON
veriguard-implant (Rust)
  └─ payload::{command|executable|filedrop|dnsresolution|networktraffic}::run()
       → NDJSON 结果写回管道
veriguard-agent 读管道 → 累计 results → 上报
AgentTaskQueueApi POST /api/agent/task/{taskId}/result
  ├─ Ed25519 签名校验（Agent pub）
  └─ 落库 + 推送 CommandInjectExecutor.handleResult
CommandInjectExecutor → 三态判定（基于 HIDS/EDR 反馈 + Agent 自检）
```

## 4. ATT&CK 战术总覆盖（35 具名类）

| ATT&CK Tactic | 覆盖（边界 # / 流量 # / 主机 #）| 类目数 |
| --- | --- | ---: |
| TA0001 Initial Access | 边界 1, 5, 6, 11 | 4 |
| TA0002 Execution | 边界 1, 8, 10；主机 1, 3, 6, 11 | 7 |
| TA0003 Persistence | 主机 12 | 1 |
| TA0004 Privilege Escalation | 主机 8 | 1 |
| TA0005 Defense Evasion | 流量 9；主机 11 | 2 |
| TA0006 Credential Access | 边界 3, 4 | 2 |
| TA0007 Discovery | 边界 2 | 1 |
| TA0008 Lateral Movement | 流量 2, 4, 6；主机 4 | 4 |
| TA0009 Collection | 边界 9, 11；流量 11 | 3 |
| TA0010 Exfiltration | 流量 4, 11 | 2 |
| TA0011 Command and Control | 流量 4, 5, 8；主机 1, 4, 7 | 6 |
| TA0040 Impact | 边界 12；主机 9 | 2 |

> 35 具名类 × 多 Tactic（部分类目跨 Tactic）覆盖 Enterprise ATT&CK 12 个核心战术；未覆盖的为 TA0042 Resource Development / TA0043 Reconnaissance（属于侦察前置阶段，不在本系统验证范围内）。

## 5. 平台代码 / Agent capability 索引

### 5.1 Java 侧（`wangjuelong/Veriguard`）

| 资产 | 路径 | 落地 |
| --- | --- | --- |
| `HttpInjectExecutor` | `veriguard-api/src/main/java/io/veriguard/combination/executor/HttpInjectExecutor.java` | PR #57 |
| `PcapReplayExecutor`（组合）| `veriguard-api/src/main/java/io/veriguard/combination/executor/PcapReplayExecutor.java` | PR #57 |
| `CommandInjectExecutor` | `veriguard-api/src/main/java/io/veriguard/injectors/command_inject/CommandInjectExecutor.java` | PR #57 |
| `WebAttackDispatchService` | `veriguard-api/src/main/java/io/veriguard/injectors/web_attack/service/WebAttackDispatchService.java` | PR #57 |
| `PcapReplayDispatchService` | `veriguard-api/src/main/java/io/veriguard/injectors/pcap_replay/service/PcapReplayDispatchService.java` | PR #57 |
| `CommandInjectDispatchService` | `veriguard-api/src/main/java/io/veriguard/injectors/command_inject/service/CommandInjectDispatchService.java` | PR #57 |
| `AgentTaskQueueApi` | `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentTaskQueueApi.java` | PR #56 / #57 |
| `AgentOnboardApi` | `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentOnboardApi.java` | PR #56 |
| `AgentInstallScriptApi` | `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentInstallScriptApi.java` | PR #57 |
| `AgentOfflinePackApi`（Mode C）| `veriguard-api/src/main/java/io/veriguard/rest/agent/AgentOfflinePackApi.java` | PR #56 / #57 |
| `VpackSerializer` | `veriguard-api/src/main/java/io/veriguard/crypto/VpackSerializer.java` | PR #56 |
| `VresultsSerializer` | `veriguard-api/src/main/java/io/veriguard/crypto/VresultsSerializer.java` | PR #56 |
| `X25519BoxService` | `veriguard-api/src/main/java/io/veriguard/crypto/X25519BoxService.java` | PR #56 |
| `Ed25519SignatureService` | `veriguard-api/src/main/java/io/veriguard/crypto/Ed25519SignatureService.java` | PR #56 |
| `OfflinePackAuditService` | `veriguard-api/src/main/java/io/veriguard/audit/OfflinePackAuditService.java` | PR #57 |

### 5.2 Rust Agent 侧（`wangjuelong/veriguard-agent` ← OpenAEV-Platform/agent@531f9d120）

| 资产 | 路径 | 落地 |
| --- | --- | --- |
| `crypto::x25519_box`（ECDH + ChaCha20-Poly1305 12B nonce IETF）| `src/crypto/x25519_box.rs` | veriguard-agent#1 |
| `crypto::ed25519`（RFC 8032 pure）| `src/crypto/ed25519.rs` | veriguard-agent#1 |
| `crypto::keys`（0o600 安全文件持久化）| `src/crypto/keys.rs` | veriguard-agent#1 |
| `state::sqlite`（executed_packs 黑名单）| `src/state/sqlite.rs` | veriguard-agent#1 |
| `onboard::install_pack`（offline 模式 7 字段 JSON）| `src/onboard/install_pack.rs` | veriguard-agent#1 |
| `onboard::bootstrap`（online 模式获 install pack）| `src/onboard/bootstrap.rs` | veriguard-agent#2 |
| `transport::sign`（8 字段 canonical signing）| `src/transport/sign.rs` | veriguard-agent#2 |
| `capabilities::HttpAttackCapability` | `src/capabilities/http_attack.rs` | veriguard-agent#2 |
| `capabilities::PcapReplayCapability` | `src/capabilities/pcap_replay.rs` | veriguard-agent#2 |
| `capabilities::CommandInjectCapability` | `src/capabilities/command_inject.rs` | veriguard-agent#2 |
| `capabilities::ImplantDropCapability` | `src/capabilities/implant_drop.rs` | veriguard-agent#2 |
| `implant::manager`（subprocess + FIFO）| `src/implant/manager.rs` | veriguard-agent#2 |
| `pack`（.vpack 解析 + Mode C 多包扫描）| `src/pack/`（计划）| C1-Agent-3 待启动 |
| Service Install（systemd / launchd / Windows）| `src/install/`（计划）| C1-Agent-3 待启动 |
| GitHub Actions CI release matrix（6 binary）| `.github/workflows/release.yml`（计划）| C1-Agent-3 待启动 |

### 5.3 Rust Implant 侧（`wangjuelong/veriguard-implant` ← OpenAEV-Platform/implant@3b16615e9）

| 资产 | 状态 |
| --- | --- |
| CLI parser + NDJSON 结果写回管道 | veriguard-implant#1 alpha 待 merge |
| `payload::command` | 同上 |
| `payload::executable` / `payload::filedrop` | 同上 |
| `payload::dns_resolution` / `payload::network_traffic` | 同上 |
| Self-delete（结束后自删二进制）| 同上 |

## 6. 验收门槛对照

| 校验项 | 招标门槛 | 当前 | 状态 |
| --- | ---: | --- | --- |
| 边界 攻击类型数 | ≥ 10 | 12 | 达成 |
| 边界 用例总量 | ≥ 500 | 1 104 | 达成（2.2 倍）|
| 流量 攻击类型数 | ≥ 10 | 11 | 达成 |
| 流量 用例总量 | ≥ 300 | 0 → 315 目标 | PR B9 灌入待 |
| 主机 攻击类型数 | ≥ 10 | 12 | 达成 |
| 主机 用例总量 | ≥ 300 | 0 → 310 目标 | 用例库内容建设阶段 |
| 自定义 用例类型数 | ≥ 6 | 6 | 达成（PR B-ii 4 PR 全完成）|
| 沙箱 用例类型数 | ≥ 8 | 8 | 达成（样本待内容建设）|
| 动态组合 攻击类型 | ≥ 250 | — | 组合引擎待规划 |
| 动态组合 数量 | ≥ 30 000 | — | 组合引擎待规划 |

## 7. 招标交付截图占位

C1-Integration alpha 完工后回填以下截图作为响应附件：

- 边界 12 类用例库列表（含 1 104 条入库截图）
- 攻击编排画布 + 12 类节点拖入演示
- 一次完整 RCE 用例运行端到端时序（前端 → Dispatch → Agent → Result）
- 三态结果（PREVENTION / DETECTION / ATTACK SUCCESS）的判定 UI 截图
- ATT&CK Technique 覆盖热力图（35 类 × 12 Tactics）
- 边界 12 类 × 主流 WAF/IPS 厂商效用矩阵
- 流量 11 类 pcap 回放运行截图（PR B9 完成后）
- 主机 12 类 Agent + Implant 调度截图（C1-Integration 完成后）
- 沙箱 8 类样本执行 + 自动还原截图

## 8. 引用

- 招标 §3.5（12 类边界攻击效用矩阵）+ §流量验证 + §主机安全验证 + §自定义验证 + §沙箱管理 条款
- 边界数据集导入器：`datasets/importer/import_boundary_b6.py`、`datasets/importer/import_boundary_b8.py`、`datasets/importer/nuclei_to_boundary_b8.py`
- Agent fork 设计：`docs/superpowers/specs/2026-05-14-veriguard-agent-implant-fork-c1-c2-design.md`
- Agent fork 实施计划：`docs/superpowers/plans/2026-05-14-veriguard-agent-implant-fork-c1-c2-plan.md`
- 平台代码仓：`wangjuelong/Veriguard`（Spring Boot + React）、`wangjuelong/veriguard-agent`（Rust）、`wangjuelong/veriguard-implant`（Rust）
