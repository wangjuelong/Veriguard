# IPv6 安全验证系统 — 业务模块 Agent 与数据流

> 来源：[`IPv6安全验证系统.md`](./IPv6安全验证系统.md)（招标文件条款汇编）+ [`IPv6安全验证系统-靶机环境.md`](./IPv6安全验证系统-靶机环境.md)
> 整理日期：2026 年 5 月
> 范围：除沙箱以外的 4 个业务模块（边界防护 / 流量安全 / 主机安全 / 自定义验证）
> 用途：作为靶机部署、Agent 选型、SOC 联动方案设计的基线

---

## 1. 概览：4 业务模块 ↔ Agent 类型

| 业务模块 | 攻击源 Agent | Agent capability | 被攻击端 | 检测设备 | Veriguard 实现状态 |
|---|---|---|---|---|---|
| 边界防护验证 | **协作主机 Agent** | `http_attack` | 客户 Web 应用 / API | WAF / IPS | PR-C ✅ |
| 流量安全验证 | **协作主机 Agent**（或流量回放设备）| `pcap_replay` | 网络流量任意 IP/端口 | NTA / IDS | PR-D ✅ |
| 主机安全验证 | **OpenAEV Agent**（直驱）或 **第三方 EDR Agent**（借力客户已有部署）| `command_exec` / `file_drop` | 主机自身 | HIDS / EDR | 上游 ✅（capability 见 PR-A）|
| 自定义验证（6 类）| 视用例类型而定（见下文）| 见下文细表 | 视用例 | 视用例 | PR-B/C/D ✅，其它走上游 |

---

## 2. 边界防护验证数据流

**Agent**：协作主机 Agent（Linux 主机，部署在能访问目标 Web 应用的网段；声明 `http_attack` capability）。

### 2.1 数据流图

```
┌─────────┐  ①编辑 web_attack inject     ┌─────────────────┐
│  用户   │ ──────────────────────────►│ Veriguard 后端  │
└─────────┘   (method/url/headers/body)  │ WebAttackExecutor│
                                          └─────────┬────────┘
                                                    │ ②按 capability="http_attack"
                                                    │   selectAgentsForCapability
                                                    ▼
                                          ┌──────────────────┐
                                          │ 协作主机 Agent   │
                                          │ (curl/reqwest)   │
                                          └─────────┬────────┘
                                                    │ ③HTTP/HTTPS 请求
                                                    ▼
┌──────────────────┐  ④流量经过        ┌──────────────────┐
│  WAF / IPS       │ ◄───────────────  │  客户 Web 应用   │
│  (边界设备)      │                   │  (业务系统)      │
└──────┬───────────┘                   └─────────┬────────┘
       │ ⑤产生告警 / 拦截记录                    │ ⑥HTTP 响应
       ▼                                          │
┌──────────────────┐                              │
│  SIEM            │                              │
└──────┬───────────┘                              │
       │ ⑧SIEM 关联查询                          │
       │                                          │
       ▼                                          ▼
┌──────────────────────────────────────────────────────┐
│ Veriguard 后端：⑦收 Agent 回传响应 → ⑧查 SIEM 告警  │
│ → ⑨计算验证结果（拦截维度 + 检测维度）              │
└──────────────────────────────────────────────────────┘
```

### 2.2 数据流关键步骤

1. 用户编辑 inject（method / url / headers / body / 期望状态码 / 期望响应正文匹配）
2. 平台后端按 capability 选协作主机 Agent
3. Agent 模板替换 + 发起 HTTP / HTTPS 请求
4. 流量经过 WAF / IPS
5. 检测设备产生告警 / 拦截记录 → SIEM
6. Web 应用返回响应（如未被拦截）
7. Agent 上报响应（状态码 / 头 / 体 / 耗时）到平台
8. 平台查 SIEM 关联告警
9. 计算验证结果

### 2.3 验证维度

| 维度 | 评估方式 |
|---|---|
| 拦截 | 响应状态码 / 错误页特征 / 连接重置 / 超时 |
| 检测 | SIEM 关联告警匹配（WAF / IPS）|

---

## 3. 流量安全验证数据流

**Agent**：协作主机 Agent（Linux 主机，部署在能将流量灌到目标网段的位置；声明 `pcap_replay` capability；本地需 root 或 `CAP_NET_RAW + CAP_NET_ADMIN` 跑 tcpreplay）。

**备选执行体**：客户既有流量回放设备（外部硬件，通过接口适配层接入）。

### 3.1 数据流图

```
┌─────────┐  ①上传 pcap → MinIO          ┌──────────────────┐
│  用户   │ ──────────────────────────►│  Veriguard MinIO │
└────┬────┘                              └──────────────────┘
     │ ②编辑 pcap_replay inject
     │   (pcap_file_id/interface/mode/rate)
     ▼
┌──────────────────┐    ③按 capability="pcap_replay"
│ Veriguard 后端   │    selectAgentsForCapability
│ PcapReplayExecutor│ ───────────────┐
└──────────────────┘                  ▼
                            ┌──────────────────┐
                            │ 协作主机 Agent   │
                            │ (tcpreplay)      │
                            └────────┬─────────┘
                                     │ ④从 MinIO 下载 pcap
                                     │ ⑤tcpreplay -i eth0 [params]
                                     ▼
                            ┌──────────────────┐
                            │ 客户业务网络     │ (网卡注入流量)
                            └────────┬─────────┘
                                     │ ⑥流量经过
                                     ▼
                            ┌──────────────────┐
                            │  NTA / IDS       │
                            │  (流量检测设备)   │
                            └────────┬─────────┘
                                     │ ⑦产生告警
                                     ▼
                            ┌──────────────────┐
                            │  SIEM            │
                            └────────┬─────────┘
                                     │ ⑨平台查询关联告警
                                     ▼
┌─────────────────────────────────────────────────────┐
│ Veriguard 后端：                                     │
│ ⑧收 Agent 回传（包数/时长/错误）                    │
│ ⑨查 SIEM 告警 → ⑩计算验证结果                     │
└─────────────────────────────────────────────────────┘
```

### 3.2 数据流关键步骤

1. 用户上传 pcap 文件到 MinIO（独立子项目，本期 PR 仅 pcap_file_id 引用）
2. 用户编辑 inject（pcap_file_id / interface / mode / rate）
3. 平台按 capability 选 Agent
4. Agent 从 MinIO 下载 pcap
5. Agent 调 tcpreplay 在指定网卡注入流量
6. 流量经过 NTA / IDS
7. 检测设备告警 → SIEM
8. Agent 清理临时 pcap 文件 + 上报回放结果（包数 / 时长 / 错误）
9. 平台查 SIEM 关联告警
10. 计算验证结果

### 3.3 验证维度

| 维度 | 评估方式 |
|---|---|
| 检测 | SIEM 关联告警匹配（NTA / IDS）|
| 拦截 | 取决于检测设备是否旁路监听（旁路只看不拦；inline 模式可观察阻断）|

### 3.4 招标特殊约束

招标边界条款 L44 强制：**同一用例需支持包含多个端口不同的四元组**。当前 `NetworkTraffic` schema 是单组四元组，实施期需扩展为多元组 List（已在用例清单缺口表标注）。

---

## 4. 主机安全验证数据流

主机侧有 **2 种 Agent 路径**：直驱 OpenAEV Agent / 借第三方 EDR。

### 4.1 路径 A：OpenAEV Agent 直驱（推荐）

**Agent**：OpenAEV Agent（Rust 常驻进程，装在被验证主机上；声明 `command_exec` / `file_drop` 等 capability；部署形态 service.admin / service.standard / session.admin / session.standard 4 种角色）。

```
┌─────────┐  ①编辑 inject（Command/Executable/FileDrop payload）
│  用户   │ ─────────────────────►┌──────────────────┐
└─────────┘                       │ Veriguard 后端   │
                                  │ (NodeExecutor)   │
                                  └────────┬─────────┘
                                           │ ②选目标 asset + Agent
                                           │   (按主机 IP + capability)
                                           ▼
                                  ┌──────────────────┐
                                  │ OpenAEV Agent    │  ← 装在主机上
                                  │ (常驻 poll 平台) │
                                  └────────┬─────────┘
                                           │ ③在主机执行命令
                                           │   (powershell/bash/sh/cmd
                                           │    或落盘 + 执行二进制)
                                           ▼
                                  ┌──────────────────┐
                                  │  被验证主机      │  ← 产生主机事件
                                  │  (文件/进程/网络) │   (文件写入 / 进程创建 /
                                  └────────┬─────────┘    网络连接)
                                           │
                                           │ ④HIDS 监控主机事件
                                           ▼
                                  ┌──────────────────┐
                                  │  HIDS / EDR      │
                                  │  (被验证设备)    │
                                  └────────┬─────────┘
                                           │ ⑤产生告警
                                           ▼
                                  ┌──────────────────┐
                                  │  SIEM            │
                                  └────────┬─────────┘
                                           │ ⑦SIEM 关联查询
                                           ▼
┌──────────────────────────────────────────────────────┐
│ Veriguard 后端：⑥收 Agent 命令输出 → ⑦查 SIEM 告警  │
│ → ⑧计算验证结果（拦截维度 + 检测维度）              │
└──────────────────────────────────────────────────────┘
```

#### 路径 A 关键步骤

1. 用户编辑主机攻击 inject（如反弹 shell / 提权 / 病毒落盘）
2. 平台按主机 asset + Agent capability 选定目标 + Agent
3. Agent 在主机执行命令（攻击直接发生在被验证主机自身）
4. HIDS / EDR 监控主机事件
5. 检测设备告警 → SIEM
6. Agent 上报命令输出 / 退出码
7. 平台查 SIEM 关联告警
8. 计算验证结果

### 4.2 路径 B：借第三方 EDR Agent（客户已部署）

**Agent**：客户既有的 Caldera / Tanium / CrowdStrike / SentinelOne / Cortex XDR Agent — **平台不装自有 Agent**，通过厂商 API 转发命令。

```
┌─────────┐  ①编辑 inject + 指定 executor (e.g. tanium)
│  用户   │ ─────────────────────►┌──────────────────┐
└─────────┘                       │ Veriguard 后端   │
                                  └────────┬─────────┘
                                           │ ②调厂商 API
                                           │   (Tanium / CrowdStrike RTR /
                                           │    SentinelOne Remote Script)
                                           ▼
                                  ┌──────────────────┐
                                  │ 客户 EDR 平台    │
                                  └────────┬─────────┘
                                           │ ③EDR 平台下发到 EDR Agent
                                           ▼
                                  ┌──────────────────┐
                                  │ EDR Agent        │  ← 客户已部署
                                  │ (Tanium/Falcon等)│
                                  └────────┬─────────┘
                                           │ ④主机执行命令
                                           ▼
                                  ┌──────────────────┐
                                  │  被验证主机      │
                                  └────────┬─────────┘
                                           │ ⑤产生主机事件
                                           │   (HIDS 监控)
                                           ▼
                                       后续同路径 A
                                       （SIEM → 平台计算结果）
```

### 4.3 路径对比

| 维度 | 路径 A（OpenAEV Agent 直驱）| 路径 B（第三方 EDR 借力）|
|---|---|---|
| Agent 部署 | 需在主机装 OpenAEV Agent（6 平台 × 2 架构 = 12 二进制） | 无需装新 Agent |
| 厂商依赖 | 仅依赖 OpenAEV 开源 | 依赖厂商 API access + remote-script 权限 |
| 通信加密 | 平台自控（TLS）| 走厂商 SaaS / 私部 API |
| 适合场景 | 客户无成熟 EDR / 自主可控诉求 | 客户已部署 EDR 且 API 可用 |

---

## 5. 自定义验证 5 类用例数据流（排除沙箱）

招标自定义验证条款列了 6 类用例，排除"上传样本文件 → 沙箱"后剩 5 类，对应 Agent 需求：

| 自定义用例 | Agent | capability | 数据流要点 |
|---|---|---|---|
| 构造 web 攻击包 | 协作主机 Agent | `http_attack` | 与边界防护章节一致 |
| 上传 pcap 流量包 | 协作主机 Agent | `pcap_replay` | 与流量安全章节一致 |
| 配置执行的命令 | OpenAEV Agent | `command_exec` | 主机路径 A 简化版（无 EDR 借力，直驱 Agent 跑 shell 命令）|
| 上传可执行文件 + 命令 | OpenAEV Agent | `file_drop` + `command_exec` | 多一步 file_drop 落盘后执行 |
| 配置邮件形式 | **无 Agent** | — | 平台 SMTP 直发（PR-B 完成的路径）|

### 5.1 邮件用例的特殊数据流（无 Agent）

邮件类用例**不经任何 Agent**，平台后端的 `MailInjector` 直接通过 SmtpProfile 拨号发邮件：

```
┌─────────┐  ①编辑邮件 inject + 选 SMTP profile
│  用户   │ ──────────────────────────────►┌──────────────────┐
└─────────┘   (主题/正文/附件/收件人 teams)│ Veriguard 后端   │
                                            │ EmailExecutor    │
                                            └────────┬─────────┘
                                                     │ ②按 SmtpProfile 拨号
                                                     │   (jakarta.mail Session)
                                                     ▼
                                            ┌──────────────────┐
                                            │ SMTP 服务器      │  ← 客户邮件服务器
                                            │ (中转)           │     或外部 SMTP
                                            └────────┬─────────┘
                                                     │ ③投递到收件人邮箱
                                                     ▼
                                            ┌──────────────────┐
                                            │ 收件人邮箱       │  ← 客户终端用户
                                            └──────────────────┘
                                                     │
                                                     │ ④（人工 / 自动）触发
                                                     │   钓鱼链接 / 附件
                                                     ▼
                                                  下游攻击
                                                  （网络钓鱼防御 / 邮件安全网关验证）
```

**特点**：邮件类是**唯一不需要 Agent 的 inject 类型**，攻击源就是平台自身的 SMTP 客户端。验证目标是邮件安全网关 / 终端用户防钓鱼意识。

### 5.2 文件 / 命令类用例的数据流

文件 + 命令类 = 主机侧路径 A 的子集，复用同一套 OpenAEV Agent + 同样数据流（见前述主机安全验证章节）。

---

## 6. 综合矩阵：业务模块 × Agent × 数据流要素

| 业务模块 | Agent 类型 | 部署位置 | capability | 数据载体 | 检测设备 | 验证维度 |
|---|---|---|---|---|---|---|
| 边界防护 | 协作主机 Agent | 客户网内合适位置 | `http_attack` | HTTP 请求 | WAF / IPS | 拦截 + 检测 |
| 流量安全 | 协作主机 Agent | 流量可注入的网卡位置 | `pcap_replay` | pcap 文件 | NTA / IDS | 检测 + 拦截（inline 时）|
| 主机安全 (A) | OpenAEV Agent | 被验证主机自身 | `command_exec` / `file_drop` | 命令 / 文件 / 二进制 | HIDS / EDR | 拦截 + 检测 |
| 主机安全 (B) | 第三方 EDR Agent | 客户 EDR 已部署 | —（走 EDR API）| remote-script | HIDS / EDR | 拦截 + 检测 |
| 自定义 #1 web 攻击包 | 同边界 | 同边界 | `http_attack` | — | — | — |
| 自定义 #2 pcap | 同流量 | 同流量 | `pcap_replay` | — | — | — |
| 自定义 #4 命令 | 同主机 (A) | 同主机 (A) | `command_exec` | — | — | — |
| 自定义 #5 可执行 + 命令 | 同主机 (A) | 同主机 (A) | `file_drop` + `command_exec` | — | — | — |
| 自定义 #6 邮件 | **无** | 平台后端 | — | SMTP 协议 | 邮件安全网关 | 用户行为 / 拦截 |

---

## 7. 关键观察

1. **5 个业务流的 Agent 实际只有 3 类**（去重后）：
   - **协作主机 Agent**（`http_attack` + `pcap_replay` capability，独立 Linux 主机，主动攻击源）
   - **OpenAEV Agent**（`command_exec` + `file_drop` capability，装在被验证主机自身）
   - **第三方 EDR Agent**（不属于 Veriguard 资产，借力客户既有部署）

2. **边界防护 / 流量安全 / 自定义 web 包 / 自定义 pcap 包共用协作主机 Agent** —— 只是 capability 不同（`http_attack` vs `pcap_replay`）。

3. **主机安全 / 自定义命令 / 自定义可执行+命令共用 OpenAEV Agent** —— capability 不同（`command_exec` vs `file_drop + command_exec`）。

4. **邮件 inject 是 5 类自定义里唯一不需 Agent 的** —— 平台自身 SMTP 直发。

5. **本期已实现派发逻辑**（PR-A/B/C/D 全部 merge），但**协作主机 Agent + OpenAEV Agent 的客户端代码**是独立 follow-up 子项目（fork OpenAEV-Platform/agent 加 capability 声明）。客户端代码落地前，PR-C / PR-D 的 dispatch trace 只是"找到 Agent 并记录派发"，真实 HTTP / tcpreplay 执行待 Agent 项目完成。

---

## 8. 实施期 follow-up 清单

| 工作项 | 紧迫度 | 归属 | 说明 |
|---|---|---|---|
| 协作主机 Agent 客户端项目 | ⭐⭐⭐⭐⭐ | 独立子项目 | fork OpenAEV-Platform/agent，加 `http_attack` + `pcap_replay` capability 声明，包装 curl / tcpreplay 子进程 |
| OpenAEV Agent 国产 OS / 信创架构二进制 | ⭐⭐⭐⭐ | 独立子项目 | 麒麟 / UOS / 鲲鹏 / 飞腾 编译产物 |
| 真实 HTTP / tcpreplay 执行 + 结果回填 endpoint | ⭐⭐⭐⭐ | 协作主机 Agent 客户端项目落地后 | 平台需加 `POST /api/inject/{id}/web-attack-result` 与 `POST /api/inject/{id}/pcap-replay-result` |
| 响应特征自动判定 | ⭐⭐⭐ | 同上 | 状态码 / body regex / 超时 / 连接重置 自动评估 |
| 请求序列模式 | ⭐⭐ | 边界防护增强 | 多步骤 chain，引用上一步响应字段（CSRF token 等）|
| 多端口不同四元组 schema 扩展 | ⭐⭐⭐⭐ | 流量安全 schema | `NetworkTraffic` 当前单组四元组，招标强制多组 |
| SIEM 关联告警查询模块 | ⭐⭐⭐⭐ | SOC 对接子项目（蓝盾 NxSOC）| 验证结果计算的最后一步依赖 |
| pcap 文件 MinIO 上传 + 元数据解析 | ⭐⭐⭐ | 独立子项目 | 包数 / 时长 / SHA-256 去重 |

---

## 9. 一句话总结

除沙箱外的 4 个业务模块（边界防护 / 流量安全 / 主机安全 / 自定义验证）共用 **3 类 Agent**：协作主机 Agent（攻击源 / 边界 + 流量）、OpenAEV Agent（攻击源 = 被攻击端 / 主机）、第三方 EDR Agent（主机借力）。数据流统一遵循"用户编辑 → 平台后端按 capability 选 Agent → Agent 在目标网络发起攻击 → 安全设备产生告警 → SIEM 中转 → 平台查询关联 → 计算验证结果"七步式时序。邮件类用例是唯一不需 Agent 的特例，由平台 SMTP 客户端直接送达。
