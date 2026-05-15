# Veriguard Agent + Implant Fork — C1 + C2 设计文档

| 项 | 值 |
| --- | --- |
| 状态 | Draft (brainstorm 完成，待 user review) |
| 日期 | 2026-05-14 |
| 招标条款 | §3.5 / §3.6 ★2 / §4.1 / §5.1 / §9.2 |
| 主仓 baseline | `wangjuelong/Veriguard` main = `6f0fd631d`（PR #54 merged） |
| 新建仓 | `wangjuelong/veriguard-agent`、`wangjuelong/veriguard-implant`（C1 创建） |
| 总工期 | C1 ≈ 4.3 周 + C2 ≈ 0.5 周 = **≈ 4.8 周** |
| 关联文档 | `docs/IPv6安全验证系统-研发拆解.md` §5 / §9.2；`docs/IPv6安全验证系统-甲方待澄清清单.md` |

---

## 0. 摘要

本文档定义 IPv6 安全验证系统中 **平台自有验证 Agent**（招标 §9.2 模块 `veriguard-agent`）的二次开发设计。范围包括：

- **C1**：从 OpenAEV-Platform（旧 OpenBAS-Platform 重定向）上游 fork 两个 **Rust** 项目（`agent` + `implant`，release 2.3.5 baseline），实现平台 ↔ 攻击机 ↔ 靶机的真实模拟攻击通道；让招标 §3 边界 / §4 流量 / §5 主机三场景的攻击模拟由"骨架 + mock"升级为"真打 + 真反查"。
- **C2**：完成 §5.1 12 类主机攻击的 ATT&CK ↔ Implant payload ↔ ART 数据集映射对照表，配套真靶机演练剧本与部署文档（含一行 curl 安装与 sneakernet 离线兜底）。

**核心交付物**：
1. 2 个新 Rust 仓 + 6 standard 二进制 (Linux/Win/macOS × x86_64/arm64) × 2 = 12 个产物
2. Veriguard 主仓 `veriguard-api` 内对接改造（去骨架 + 新 executor + 新 REST）
3. Flyway V19 + V20 迁移
4. `.vpack` / `.vresults` JSON envelope 加密文件格式（NaCl box + Ed25519）
5. 12 类主机攻击映射对照表 + 真靶机演练剧本

**显式 Out-of-Scope**：国产 OS 适配（攻击机 + 靶机均不做）；4 项安全加强延后；东西向 pcap 中继；implant 加 http_attack。

---

## 1. 背景与上下文

### 1.1 招标条款依赖

| 条款 | 内容 | 本设计交付的能力 |
| --- | --- | --- |
| §3.5 边界 WAF/IPS 12 类 ≥ 500 用例 | 12 类边界攻击 + ≥ 500 用例 | C1 让 1104 条 web_attack payload（B6+B8）真消费到边界 |
| §3.6 ★2 30k 组合 + 聚类 + 分级 | 30000 攻击组合真打 + 聚类 | C1 让 HttpInjectExecutor 从骨架升真接通，275 base × 120 dim 组合**实跑** |
| §4.1 流量边界覆盖度 | pcap 重放 + 4 态矩阵 | C1 让 PcapReplayExecutor 真接通，agent fork tcpreplay 真发包 |
| §5.1 主机 HIDS 12 类 ≥ 300 用例 | 12 类主机攻击 + ≥ 300 用例 | C1 让 1781 条 ART Command + 5 类 implant payload 真在靶机执行 |
| §9.2 平台自有验证 Agent | 模块 `veriguard-agent` 新增；TLS over IPv6；仅装靶机；与"云眼"并存 | 本文档全部 |

### 1.2 当前代码状态（main = `6f0fd631d`）

| 组件 | 状态 | 证据 |
| --- | --- | --- |
| `veriguard-agent` 客户端 | **不存在**（仅 .keep 占位）| `agents/veriguard-agent/{linux,macos,windows}/{arm64,x86_64}/.keep` |
| `veriguard-implant` | **不存在** | grep 全仓 0 命中 |
| `HttpInjectExecutor`（§3.6 ★2） | **骨架，抛 UnsupportedOperationException** | `HttpInjectExecutor.java:77` |
| `WebAttackExecutor`（attack chain）| **半骨架**：选 agent → 写 trace → 不真发 | `WebAttackExecutor.java:99` |
| `PcapReplayExecutor` | **半骨架**：同上 | `PcapReplayExecutor.java` |
| `CoverageRunner`（§3.1）| **mock SOC** 路径，不真发 inject | `CoverageRunner.java:47-48` |
| `NxSocAdapter` | **抛 UnsupportedOperationException**（待 PR A1 + 甲方 API 文档）| `NxSocAdapter.java` |
| `StubCombinationExecutor`（★2 兜底）| 按 0.35/0.55/0.10 概率掷骰子 | `StubCombinationExecutor.java:52-61` |
| 1104 条 web_attack payload（B6+B8）| 已落 dev DB | PR #54 merged |
| 1781 条 ART Command payload | 已落 dev DB | 历史 ART importer |

**结论**：用例数据已就位但**没有真实执行端**。C1+C2 是把整个平台从"数据集落库 + mock 跑通"升级到"真模拟攻击 + 真数据回填"的关键依赖。

### 1.3 设计目标

1. **真实模拟攻击**：去掉所有 throw UnsupportedOperationException / mock 路径，让 §3 / §4 / §5 三场景全部真实跑通
2. **复用上游 OpenBAS 设计**：fork 而非重写；**fork 一次性脱钩**——取 baseline commit 后两仓后续完全独立演化，不跟上游 patch / 不做 cherry-pick / 不强求协议兼容；agent / implant 双工件结构保留，加密协议替换为非对称模型
3. **极简部署 UX**：一行 curl 安装（Tailscale 风格）作为 Mode A 默认体验
4. **离线兜底**：Mode C sneakernet 协议覆盖 T4 air-gapped 拓扑
5. **不引入新依赖**：复用 dev compose；6 standard 二进制，不做国产 OS 编译

---

## 2. 整体架构

### 2.1 系统总览

```
                                    ┌─────────────────────────────────────────┐
                                    │       wangjuelong/Veriguard 主仓         │
                                    │              （Java 后端）              │
                                    │                                         │
                                    │  Scheduler / CoverageRunner             │
                                    │      ↓                                  │
                                    │  Dispatch Services:                     │
                                    │    ├─ WebAttackDispatchService          │
                                    │    ├─ PcapReplayDispatchService         │
                                    │    └─ CommandInjectExecutor (新)        │
                                    │      ↓                                  │
                                    │  AgentService.selectByCapability        │
                                    │      ↓                                  │
                                    │  Mode A poll queue  /  Mode C export   │
                                    │                                         │
                                    │  Keys: P_sign_priv / P_enc_priv (KMS)   │
                                    │  V19 migration: agents 表加密列         │
                                    └────────┬────────────────┬───────────────┘
                                             │                │
                                             │                │
                    Mode A (在线)             │                │       Mode C (离线)
                    ────────────             │                │       ────────────
                                             │                │
                                             ▼                ▼
                              ┌──────────────────────┐   ┌──────────────────┐
                              │  DMZ Forward Proxy   │   │  Operator USB    │
                              │  (nginx / HAProxy)   │   │  .vpack file     │
                              │  甲方部署 1 台       │   │  sneakernet 路径 │
                              │  双栈 IPv4 + IPv6    │   │                  │
                              └──────────┬───────────┘   └─────────┬────────┘
                                         │                         │
                                         ▼                         ▼
        ┌──────────────────────────────────────────────────────────────────────┐
        │  攻击机 / 内网协作主机                                                │
        │  (standard Linux/Win/macOS x86_64 or arm64)                          │
        │                                                                      │
        │  ┌────────────────────────────────────────────────────────────────┐  │
        │  │   wangjuelong/veriguard-agent  (Rust fork ← OpenAEV/agent)     │  │
        │  │   Mode 检测:                                                    │  │
        │  │     无 --offline-pack* flag → Mode A (HTTPS poll)              │  │
        │  │     --offline-pack <file>    → Mode C 单包                     │  │
        │  │     --offline-pack-dir <dir> → Mode C 目录扫描串行             │  │
        │  │                                                                │  │
        │  │   Capabilities:                                                 │  │
        │  │     ├─ http_attack       (§3 边界 - agent 内置 net/http 直发) │  │
        │  │     ├─ pcap_replay      (§4 流量 - fork tcpreplay)            │  │
        │  │     ├─ command_inject   (§5 主机 - drop implant 后启)         │  │
        │  │     └─ implant_drop     (§5 主机 - 通用 implant 落盘)         │  │
        │  │                                                                │  │
        │  │   Keys: ~/.veriguard-agent/keys/                                │  │
        │  │     A_sign_priv (Ed25519)  +  A_enc_priv (X25519)              │  │
        │  └────────────────────────────────────────────────────────────────┘  │
        │                              ↓ drop + exec (仅 §5)                  │
        │  ┌────────────────────────────────────────────────────────────────┐  │
        │  │   wangjuelong/veriguard-implant  (Rust fork ← OpenAEV/implant) │  │
        │  │   一次性短命 / 自清理                                          │  │
        │  │   Payloads: Command / Executable / FileDrop /                   │  │
        │  │             DnsResolution / NetworkTraffic                      │  │
        │  └────────────────────────────────────────────────────────────────┘  │
        └──────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼ 流量出口
        ┌────────────────────────────────┼─────────────────────────────────────┐
        ▼ § 3 北南 HTTP                  ▼ §4 流量 pcap          ▼ §5 主机检测
        WAF / IPS                       内部 / 边界 IDS         "云眼" HIDS
            │                                │                     │
            └────────────────┬───────────────┴─────────────────────┘
                             │ 告警上送
                             ▼
                      集团 SIEM → 蓝盾 NxSOC
                                  ▲
                                  │ queryAlerts (待 PR A1)
                                  Veriguard 平台
```

### 2.2 三场景执行路径

| 场景 | 执行体 | Agent 角色 | Implant 角色 | 流量出口 |
| --- | --- | --- | --- | --- |
| **§3 边界 WAF/IPS** | **Agent 自己**（内置 Rust `reqwest`）| 控制 + 执行双角色 | **不参与** | 攻击机 → 边界 WAF/IPS → 内网靶 URL |
| **§4 流量 pcap 回放** | **Agent fork `tcpreplay`** 子进程 | 控制 + 子进程管理 | **不参与** | Agent host eth → 内部 IDS → 同段靶 |
| **§5 主机 12 类** | **Implant 进程**（落盘 + 启动）| 仅"快递员"（drop + collect）| **核心执行体** | 主机内 syscalls → HIDS → SIEM → NxSOC |

**关键设计原则**：Agent = 控制面 + 部分执行能力（HTTP / pcap）；Implant = 主机内"被检测的对象本体"（5 类 payload 形态）。

### 2.3 双通信模式

#### Mode A（默认 / 在线）

- Agent 命令行**无** `--offline-pack*` flag 触发
- HTTPS poll-based（agent 主动 outbound 到平台）
- 支持 `HTTPS_PROXY` env / `--ca-bundle` flag
- 适用拓扑：T1 / T3 / T2 + DMZ bridge

#### Mode C（离线 / sneakernet 兜底）

- Agent 命令行**有** `--offline-pack <file>` 或 `--offline-pack-dir <dir>` 触发
- `.vpack` 文件经物理介质往返
- 支持单包 / 多包目录两种模式
- 适用拓扑：T4 air-gapped / T2 无 DMZ bridge

**4 种部署拓扑兼容矩阵**：

| 拓扑 | 平台位置 | 攻击机位置 | 攻击机 ↔ 平台 | 推荐模式 |
| --- | --- | --- | :---: | --- |
| T1 同段共置 | DMZ | DMZ | ✅ 双向通 | Mode A 直连 |
| T2 平台内网 + 攻击机 DMZ | 甲方内网 | DMZ | 通过 DMZ bridge | Mode A + nginx |
| T3 平台互联网 | 互联网 | DMZ | 攻击机 outbound | Mode A 直连 |
| T4 全隔离 | 甲方专网 | 互联网攻击段 | 不通 | **Mode C** sneakernet |

### 2.4 密钥与身份模型

**4 把私钥分布**：

```
平台（多 agent 共用）:
  P_sign_priv  (Ed25519) — 签所有出包
  P_enc_priv   (X25519)  — 解所有进包结果
  攻陷 = 全攻破，部署时优先 HSM/KMS；招标场景 chmod 0600 + 服务器加固

每个 Agent (独立):
  A_sign_priv  (Ed25519) — 签 results 出去
  A_enc_priv   (X25519)  — 解 pack 进来
  攻陷 = 仅这一台 agent 失陷
  落 ~/.veriguard-agent/keys/ chmod 0600
```

**公钥不必保密**，通过 install_pack / register 流程互相分发。

### 2.5 仓库与产物结构

```
github.com/wangjuelong/Veriguard           ← 主仓 (已存在)
github.com/wangjuelong/veriguard-agent     ← C1 fork 新建 (← OpenBAS-Platform/agent)
github.com/wangjuelong/veriguard-implant   ← C1 fork 新建 (← OpenBAS-Platform/implant)
```

**二进制分发链**：

```
veriguard-agent / veriguard-implant 各自 CI:
   GitHub Actions matrix build (Linux/Win/macOS × x86_64/arm64)
   → GitHub Release 6 个二进制 + SHA256SUMS
                ↓
   Veriguard 主仓 CI (mvn install 新增 step):
   下载最新 release → 校 SHA256 → 解压到:
                ↓
   veriguard-api/src/main/resources/agents/
       ├── veriguard-agent/
       │   ├── linux/{arm64,x86_64}/veriguard-agent      ← 替换 .keep
       │   ├── macos/{arm64,x86_64}/veriguard-agent      ← 替换 .keep
       │   └── windows/{arm64,x86_64}/veriguard-agent.exe← 替换 .keep
       └── veriguard-implant/                            ← 新增目录
           ├── linux/{arm64,x86_64}/veriguard-implant
           ├── macos/{arm64,x86_64}/veriguard-implant
           └── windows/{arm64,x86_64}/veriguard-implant.exe
                ↓
   打入 veriguard-api Spring Boot fat jar
                ↓
   Agent 运行时通过 GET /api/agent/implant/download/<os>/<arch> 下载 implant
```

### 2.6 Upstream 偏离矩阵

**Fork 策略声明**：两仓 fork 后**一次性脱钩**——baseline commit 仅作 LICENSE / attribution 归属保留（上游 Apache 2.0 协议要求），代码侧后续 100% 独立演化。不做 cherry-pick / 不跟上游 patch / 不强求协议兼容。

**Baseline commit**（C1 W1 fork 时锁定，记入两仓 README 顶部）：

```
veriguard-agent     ← OpenAEV-Platform/agent     @ 531f9d120a92f1af3ce78b0c37a356738584af18  (release 2.3.5)
veriguard-implant   ← OpenAEV-Platform/implant   @ 3b16615e95d0f9187328a73fbe26c5fd38e3b18a  (release 2.3.5)
```

**重要事实**：上游是 **Rust 2021 edition** 项目（非最初猜想的 Go）。已有相对成熟的 `api/{register_agent, manage_jobs}` + `process/{agent_job, keep_alive, agent_cleanup, agent_exec}` + `windows/service` 模块。本 fork 在此基础上**加密码学层 + Mode C + 4 capabilities + bootstrap + NDJSON pipe**，并替换原 api_key 鉴权为 Ed25519 签名 + X25519 加密模型。

#### veriguard-agent 改造矩阵（上游代码保留 ≈ 60-70%）

> 上游 Rust agent 已有 `api/{register_agent, manage_jobs}` + `process/{agent_job, keep_alive, agent_cleanup, agent_exec}` + `windows/service` + `config/{settings, execution_details}` + 完整 logging/panic 处理；本 fork **重用绝大部分骨架**，仅在认证 / 加密 / Mode C / capabilities 层做替换 + 扩展。

| 类别 | 改动 | 类型 |
| --- | --- | --- |
| 包名 | `openaev-agent` → `veriguard-agent`（Cargo.toml + 日志前缀 + service 名）| **改** |
| 认证模型 | api_key bearer (`api/register_agent.rs`) → Ed25519 + X25519 keypair onboarding | **改** |
| 加密层 | 新增 `crypto/` 模块（`ed25519-dalek` + `x25519-dalek` + `chacha20poly1305` 或 `dryoc`）| **加** |
| 通信模式 | 上游 `api/manage_jobs.rs` 只有 poll → 新增 Mode C `.vpack` 离线（单包 + 目录扫描）| **加** |
| 能力清单 | 上游 `process/agent_exec.rs` 主要执行 implant → 新增 `capabilities/` 注册中心 + `http_attack` agent 直发 + `pcap_replay` tcpreplay 子进程 + `command_inject` / `implant_drop` 区分 | **改 + 加** |
| Bootstrap | 上游无 → 新增 `--bootstrap` subcommand 配合一行 curl | **加** |
| Service 安装 | 上游已有 `windows/service.rs`；新增 `service/{systemd_linux,launchd_darwin}.rs` 跨平台 | **改 + 加** |
| 请求签名 | 新增 `X-Veriguard-Signature` header 协议（拦截器层挂接到 reqwest）| **加** |
| 状态库 | 新增 `state/sqlite.rs`（`rusqlite` bundled）+ `executed_packs.db` 黑名单 | **加** |
| Pack 解析 | 新增 `pack/{vpack,vresults,multi,blacklist}.rs` 模块（NaCl box JSON envelope）| **加** |
| Implant 管理 | 新增 `implant/{manager,pipe}.rs`（命名管道 + NDJSON 解析）| **加** |
| CI/CD | 替换为 wangjuelong 仓 release pipeline + 6 binary 矩阵（`actions/setup-rust` + `cargo zigbuild` 跨编译）| **改** |

#### veriguard-implant 改造矩阵（上游代码保留 ≈ 80%）

| 类别 | 改动 | 类型 |
| --- | --- | --- |
| Payload 执行核 | Command / Executable / FileDrop / DnsResolution / NetworkTraffic 5 类（含 `mailparse` 解析）| **保留上游** |
| 包名 | `openaev-implant` → `veriguard-implant`（Cargo.toml + binary name）| **改** |
| Agent 调用契约 | 已有 `clap` CLI；调整 flags：`--task-id` / `--payload-type` / `--payload-b64` / `--result-pipe` / `--timeout` / `--self-delete` | **改 / 加** |
| Result pipe 协议 | 上游可能用 stdout 上报；改为 NDJSON 写命名管道（见 §3.3.3）| **改** |
| 自删除 / cleanup | 跨平台一致化加固 | **改** |
| CI/CD | 替换为 wangjuelong/veriguard-implant release pipeline | **改** |
| **不做** | 国产 OS 平台代码 / TPM key handling / 自定义 §5.1 类 12 payload 子类 | — |

---

## 3. C1 详设

### 3.1 C1 范围

| 维度 | 内容 |
| --- | --- |
| 新增 Git 仓 | `wangjuelong/veriguard-agent` + `wangjuelong/veriguard-implant` |
| 主仓改动模块 | `veriguard-api/src/main/java/io/veriguard/{injectors,combination,rest,crypto,audit}` |
| Flyway 迁移 | V19（agents 表加密列）+ V20（offline_pack_audit）|
| Agent 能力 | `http_attack` / `pcap_replay` / `command_inject` / `implant_drop` |
| Implant payload | Command / Executable / FileDrop / DnsResolution / NetworkTraffic |
| 通信模式 | Mode A 在线（含一行 curl + forward proxy）+ Mode C 离线（单包 + 目录扫描）|
| 加密 | NaCl box (X25519 + ChaCha20-Poly1305) + Ed25519 签名；JSON envelope `.vpack` / `.vresults` |
| IPv6 兼容 | Happy Eyeballs（`reqwest` 经 `hyper` + `tokio` 自动支持）；URL 接受 IPv4 / IPv6 字面量 / DNS |
| 语言 | **Rust 2021 edition**（agent + implant）+ Java 21 (主仓改造) |
| 不做 | 国产 OS / Reverse tunnel / Implant 加 http_attack / 包 TTL / 4 项安全加强（见 §5）|

### 3.2 `veriguard-agent` 仓设计

#### 3.2.1 仓目录结构

```
wangjuelong/veriguard-agent/  (Rust 2021 edition; fork ← OpenAEV-Platform/agent@531f9d120)
├── Cargo.toml                                ← package "veriguard-agent" + deps
├── Cargo.lock
├── src/
│   ├── main.rs                               ← 入口；mod 声明 + clap subcommand 路由
│   ├── config/                               ← (上游已有) settings + execution_details
│   ├── common/                               ← (上游已有) error_model
│   ├── api/                                  ← (上游已有，本仓改造)
│   │   ├── register_agent.rs                 ← 改造：api_key → install_pack onboarding
│   │   ├── manage_jobs.rs                    ← 改造：加 X-Veriguard-Signature header
│   │   └── bootstrap.rs                      ← 新增：一行 curl bootstrap 入口
│   ├── crypto/                               ← 新增模块
│   │   ├── mod.rs
│   │   ├── ed25519.rs                        ← ed25519-dalek wrapper
│   │   ├── x25519_box.rs                     ← x25519-dalek + chacha20poly1305 (NaCl box)
│   │   ├── keys.rs                           ← 生成 / 持久 / 加载（chmod 0600）
│   │   └── cert_pin.rs                       ← TLS leaf cert SHA256 pin verifier
│   ├── capabilities/                         ← 新增模块
│   │   ├── mod.rs                            ← Capability trait + Registry
│   │   ├── http_attack.rs                    ← §3 - agent 内置 reqwest 直发
│   │   ├── pcap_replay.rs                    ← §4 - 启 tcpreplay 子进程
│   │   ├── command_inject.rs                 ← §5 - drop implant + run
│   │   └── implant_drop.rs                   ← §5 - 通用 implant 投放
│   ├── implant/                              ← 新增模块
│   │   ├── mod.rs
│   │   ├── manager.rs                        ← 二进制下载 / 启停 / pipe read
│   │   └── pipe.rs                           ← 命名管道跨平台抽象
│   ├── pack/                                 ← 新增模块
│   │   ├── mod.rs
│   │   ├── vpack.rs                          ← 解析 + 校签 + 解密 (9 步决策树)
│   │   ├── vresults.rs                       ← 签 + 加密 + 写盘
│   │   ├── blacklist.rs                      ← executed_packs SQLite 黑名单
│   │   └── multi.rs                          ← 目录 lex 排序串行
│   ├── onboard/                              ← 新增模块（上游 register_agent 重构进此）
│   │   ├── mod.rs
│   │   ├── install_pack.rs                   ← JSON 解析 + 必填校验
│   │   ├── init.rs                           ← 标准 onboarding 流程
│   │   ├── bootstrap.rs                      ← 一行 curl 用：token → install_pack
│   │   └── register.rs                       ← 在线 / 离线 .vregister 注册
│   ├── service/                              ← 跨平台 service 安装
│   │   ├── mod.rs
│   │   ├── systemd.rs                        ← Linux (#[cfg(target_os = "linux")])
│   │   ├── launchd.rs                        ← macOS
│   │   └── windows_svc.rs                    ← Windows (复用上游 windows/service.rs)
│   ├── process/                              ← (上游已有) agent_job + cleanup + keep_alive + agent_exec
│   ├── state/                                ← 新增
│   │   ├── mod.rs
│   │   └── sqlite.rs                         ← rusqlite (bundled) 持久层
│   └── windows/                              ← (上游已有) Windows-specific
├── tests/                                    ← 集成测试 (cargo test --test integration)
│   ├── crypto_interop.rs                     ← Go/Java 跨语言互测 fixture
│   ├── pack_security.rs                      ← T-1 ~ T-7 安全测
│   └── onboarding.rs
├── testdata/
│   ├── install_pack_valid.json
│   ├── pack_valid.vpack
│   ├── pack_signature_tampered.vpack
│   ├── pack_wrong_agent.vpack
│   └── pack_oversize.vpack
├── .github/workflows/release.yml             ← matrix build 6 binary（cargo zigbuild）
├── README.md                                 ← 头部含 fork baseline 声明
└── LICENSE                                   ← 继承上游 Apache 2.0
```

#### 3.2.2 CLI 接口契约

```bash
# Onboarding（一次性，首装）
veriguard-agent init \
  --install-pack <path/to/install-pack.json> \
  [--state-dir ~/.veriguard-agent] \
  [--register-online] \                      # 默认（Mode A 注册）
  [--register-offline-output <path>] \       # Mode C: 生成 .vregister 文件
  [--platform-cert-pin <sha256>]             # TLS 证书 pin

# 一行 curl 走的简化 init
veriguard-agent init \
  --bootstrap \
  --platform-url <url> \
  --onboard-token <token> \
  --platform-cert-pin <sha256> \
  [--state-dir ~/.veriguard-agent]

# Mode A 在线工作（默认）
veriguard-agent run \
  [--state-dir <path>] \
  [--platform-url <url>] \                   # 覆盖 install_pack 中的 URL
  [--poll-interval 5s] \
  [--ca-bundle <path>] \
  [--log-level info]

# Mode C 离线单包
veriguard-agent run \
  --offline-pack <path/to/day1.vpack> \
  --offline-results <path/to/day1.vresults>

# Mode C 离线多包目录
veriguard-agent run \
  --offline-pack-dir <path/to/batch/> \
  [--halt-on-task-error] \                   # 默认 false：单 task 失败继续
  [--pack-order <p1.vpack,p2.vpack,...>]    # 默认 lex 排序

# 辅助命令
veriguard-agent decrypt --pack <file>        # debug（需 agent 私钥）
veriguard-agent fingerprint                  # 输出 A_sign_pub 的 SHA256
veriguard-agent rotate-keys                  # 手动密钥轮换（自动轮换是 C3+）
veriguard-agent uninstall                    # 卸载 service + 删 state dir
veriguard-agent version
veriguard-agent capabilities                 # 列已 register capabilities
```

#### 3.2.3 Mode A 主循环伪码

```go
for {
    tasks := platform.Poll(agentID, capabilities)
    for _, t := range tasks {
        if !hasCapability(t.Capability) {
            platform.PostResult(t.TaskID, RejectedCapability)
            continue
        }
        result := capabilities[t.Capability].Execute(t)
        platform.PostResult(t.TaskID, result)
    }
    sleep(pollInterval)
}
```

#### 3.2.4 Mode C 目录扫描伪码

```go
packs := listAndSortLex(packDir)
for _, p := range packs {
    pack, err := vpack.LoadAndVerify(p, A_enc_priv, P_sign_pub, blacklist)
    if err != nil {
        log.Error("pack rejected", "file", p, "reason", err)
        return                                       // pack 链不可信 → 中断
    }
    results := []TaskResult{}
    for _, t := range pack.Tasks {
        r := capabilities[t.Capability].Execute(t)
        results = append(results, r)
        if r.Failed && haltOnTaskError {
            break
        }
    }
    blacklist.Add(pack.PackID)
    vresults.WriteSigned(p+".results", results, A_sign_priv, P_enc_pub)
}
```

### 3.3 `veriguard-implant` 仓设计

#### 3.3.1 仓目录结构

```
wangjuelong/veriguard-implant/  (Rust 2021 edition; fork ← OpenAEV-Platform/implant@3b16615e9)
├── Cargo.toml                                 ← package "veriguard-implant"
├── Cargo.lock
├── src/
│   ├── main.rs                                ← clap CLI 入口（上游已有 clap dep）
│   ├── payload/                               ← 5 类 payload（上游已有，本仓微调）
│   │   ├── mod.rs
│   │   ├── command.rs                         ← shell command 执行
│   │   ├── executable.rs                      ← drop + execve 二进制
│   │   ├── filedrop.rs                        ← 落盘文件 + 权限
│   │   ├── dns_resolution.rs                  ← DNS A/AAAA/MX 查询
│   │   └── network_traffic.rs                 ← 发自定义 TCP/UDP/ICMP
│   ├── result/                                ← 新增（替换上游 stdout 上报）
│   │   ├── mod.rs
│   │   ├── writer.rs                          ← NDJSON 命名管道 writer
│   │   └── chunked.rs                         ← stdout/stderr chunk 拆分
│   ├── cleanup/                               ← 新增
│   │   ├── mod.rs
│   │   └── self_delete.rs                     ← 跨平台自删（含 #[cfg] 分支）
│   └── platform/                              ← 跨平台抽象
│       ├── mod.rs
│       ├── linux.rs                           ← /proc, fork/exec, mkfifo
│       ├── windows.rs                         ← CreateProcessW, CreateNamedPipeW
│       └── darwin.rs
├── tests/integration.rs                       ← cargo test 集成
├── testdata/
│   ├── eicar.txt                              ← EICAR 标样
│   └── webshell_sample.php
├── .github/workflows/release.yml              ← matrix build 6 binary
├── README.md
└── LICENSE
```

#### 3.3.2 Agent → Implant 调用契约

```bash
veriguard-implant \
  --task-id T-001 \
  --payload-type Command \
  --payload-b64 <base64-encoded JSON of payload spec> \
  --result-pipe /tmp/veriguard-implant-pipe-T-001 \
  --timeout 60s \
  --self-delete
```

**Implant 退出码契约**：

| Code | 含义 |
| --- | --- |
| 0 | 任务正常完成 |
| 1 | 任务执行失败（shell exit != 0 等）|
| 2 | payload 解析失败 |
| 3 | 资源不足 / 文件权限 |
| 4 | 超时 |
| 99 | panic / unhandled error |

`stdout` / `stderr` / `exit_code` / `started_at` / `finished_at` 全部经 result_pipe 写回（JSON 一行，schema 见 §3.3.3）。

#### 3.3.3 Result Pipe 行格式契约（NDJSON）

Agent 与 Implant 之间通过 named pipe（Linux/macOS `mkfifo` / Windows `CreateNamedPipeW`）通信；Implant 按 **NDJSON**（newline-delimited JSON，每行一个独立 JSON 对象）格式写入；Agent 用 buffered reader 逐行解析。

**约定**：
- 一行 = 一个 event；UTF-8 编码；以 `\n`（0x0A）结尾；行内不含 `\n`
- Event 按时间顺序写入；最后一个 event 必为 `result_final`（标志任务终结，Agent 据此关 pipe）
- Implant 写完 `result_final` 后退出进程；Agent 读到 EOF 关 pipe
- Implant 进程异常退出（信号 / 崩溃 / OOM）→ Agent 检测 pipe EOF 且无 `result_final` → 标 `implant_crashed`

**Event 类型枚举**：

| event_type | 时机 | 字段 |
| --- | --- | --- |
| `started` | Implant 启动 + payload 解析完毕 | `task_id`, `started_at` (RFC3339) |
| `progress` | 可选；长任务中途上报心跳 | `task_id`, `at` (RFC3339), `stage` (string), `note` (string optional) |
| `stdout_chunk` | 可选；子进程 stdout 增量上报（如 ART Command 跑长输出）| `task_id`, `seq` (int), `data_b64` (string) |
| `stderr_chunk` | 同上，stderr 增量 | `task_id`, `seq` (int), `data_b64` (string) |
| `result_final` | **必有，且唯一**；任务终止 | `task_id`, `status`, `exit_code`, `started_at`, `finished_at`, `stdout_b64`, `stderr_b64`, `error_message` (optional), `response_digest` (optional, http 类任务用) |

**`result_final.status` 枚举**：
- `completed`（exit_code = 0 / 任务正常）
- `failed`（exit_code != 0）
- `timeout`（超过 `--timeout`，Implant 内部 SIGTERM → 5s 后 SIGKILL）
- `crashed`（Implant panic / unhandled error / signal 中断）

**示例 pipe 内容**（NDJSON 4 行，1 个 started + 2 个 stdout_chunk + 1 个 result_final）：

```
{"event_type":"started","task_id":"T-001","started_at":"2026-05-14T15:00:00Z"}
{"event_type":"stdout_chunk","task_id":"T-001","seq":0,"data_b64":"dGVzdF8x"}
{"event_type":"stdout_chunk","task_id":"T-001","seq":1,"data_b64":"dGVzdF8y"}
{"event_type":"result_final","task_id":"T-001","status":"completed","exit_code":0,"started_at":"2026-05-14T15:00:00Z","finished_at":"2026-05-14T15:00:02Z","stdout_b64":"dGVzdF8xdGVzdF8y","stderr_b64":""}
```

**字段尺寸约束**：
- 单行最大 64KB（防 implant 写炸 agent buffer）
- 单 chunk 的 `data_b64` ≤ 32KB（base64 后 ≈ 24KB 原始字节）
- 总 stdout/stderr 累积 ≤ 1MB（超出 `result_final.stdout_b64` truncate 并标记 `truncated: true`）

**为什么 NDJSON 而不是 length-prefixed binary**：
- 调试可读（`cat <pipe>` 可见）
- Rust `BufRead::lines()` / Go `bufio.Scanner` / Python `for line in fh` 等多语言 stdlib 直接逐行解析
- 单行损坏不污染后续 event（fail-soft）
- 加密 envelope 已在 §3.5 层处理；pipe 层无加密需求（同主机进程间通信）

### 3.4 Platform 侧改造矩阵

| 模块 | 操作 | 文件路径 |
| --- | --- | --- |
| `WebAttackDispatchService` | **修改**：去 TODO；写任务到 agent_task 队列 + 等回执 | `injectors/web_attack/service/WebAttackDispatchService.java` |
| `HttpInjectExecutor`（§3.6 ★2）| **修改**：去 `throw UnsupportedOperationException`，调 `WebAttackDispatchService` | `combination/executor/HttpInjectExecutor.java` |
| `CommandInjectExecutor`（新）| **新增**：1781 条 ART Command payload 真执行 | `injectors/command_inject/` 新目录 |
| `CommandInjectContract`（新）| `CAPABILITY_COMMAND_INJECT = "command_inject"` | 同上 |
| `CommandInjectDispatchService`（新）| 选 agent → 写任务 → 等回执（与 WebAttack 对称）| 同上 |
| `PcapReplayExecutor` | **修改**：去骨架，真接通 | `injectors/pcap_replay/PcapReplayExecutor.java` |
| `NodeExecutorService` | **修改**：注册 CommandInject NodeExecutor | 既有 service |
| `AgentOnboardApi`（新）| `POST /api/agent/onboard/{init,register,bootstrap}` | `rest/agent/AgentOnboardApi.java` |
| `AgentOfflinePackApi`（新）| `POST /api/agent/offline-pack/{export,import}` | `rest/agent/AgentOfflinePackApi.java` |
| `AgentTaskQueueApi`（扩）| `GET /api/agent/poll` + `POST /api/agent/task/<id>/result` | `rest/agent/AgentTaskQueueApi.java` |
| `AgentImplantDownloadApi`（新）| `GET /api/agent/implant/download/<os>/<arch>` | `rest/agent/AgentImplantDownloadApi.java` |
| `AgentInstallScriptApi`（新）| `GET /install/<token>` 生成一行 curl 用 bash/ps1 脚本 | `rest/agent/AgentInstallScriptApi.java` |
| `Ed25519SignatureService`（新）| `sign / verify` | `crypto/` 新包 |
| `X25519BoxService`（新）| `seal / open` | 同上 |
| `VpackSerializer / VresultsSerializer`（新）| JSON envelope 解析 + 签 + 加密 | 同上 |
| `OfflinePackAuditService`（新）| 审计表读写 + IP 记录 | `audit/` 新包 |
| `AgentService.selectByCapability` | **修改**：严校 capability 集合 | `service/AgentService.java` |

### 3.5 Wire 协议契约

#### 3.5.1 REST 端点签名

```
─── Onboarding ────────────────────────────────────────────
POST /api/agent/onboard/init                     (admin)
  body: { display_name, capabilities[], allowed_modes[] }
  resp: { agent_id, onboard_token, platform_ed25519_pub_b64,
          platform_x25519_pub_b64, platform_url,
          platform_cert_fingerprint_sha256, ttl_seconds: 86400 }

POST /api/agent/onboard/register                 (agent / 离线 import 共用)
  body: { agent_id, agent_ed25519_pub_b64, agent_x25519_pub_b64,
          capabilities[], onboard_token, registration_sig }
  resp: { status: "registered", agent_id }

POST /api/agent/onboard/bootstrap                (一行 curl 用)
  body: { onboard_token }
  resp: { install_pack: { ...完整 install_pack... } }

GET /install/<onboard_token>                     (一行 curl 入口)
  resp: text/x-shellscript — bash 脚本（auto-detect OS/arch）

GET /install/<onboard_token>/ps1                 (Windows PowerShell)
  resp: text/plain — PowerShell 脚本

GET /install/binary/<os>/<arch>/veriguard-agent[.exe]
  resp: octet-stream

─── Mode A 在线工作 ───────────────────────────────────────
GET /api/agent/poll?agent_id=X&capabilities=...   (agent)
  Header: X-Veriguard-Signature: Ed25519(body+timestamp, A_sign_priv)
  resp: { tasks: [{ task_id, capability, payload, expectations }, ...] }

POST /api/agent/task/<task_id>/result            (agent)
  Header: X-Veriguard-Signature: ...
  body: { status, stdout, stderr, exit_code, response_digest,
          started_at, finished_at }
  resp: { status: "accepted" }

GET /api/agent/implant/download/<os>/<arch>      (agent)
  resp: octet-stream + Header: X-SHA256: ...

─── Mode C 离线 ────────────────────────────────────────────
POST /api/agent/offline-pack/export              (admin)
  query: ?agent_id=X&max_tasks=N
  resp: application/json — .vpack 文件下载

POST /api/agent/offline-pack/import              (admin)
  body: multipart — 上传 .vresults
  resp: { pack_id, imported_count, rejected_count, errors[] }
```

#### 3.5.2 `.vpack` JSON envelope schema

```json
{
  "schema_version": "1.0",
  "format": "vpack",

  "metadata_plaintext": {
    "pack_id": "550e8400-e29b-41d4-a716-446655440000",
    "platform_id": "veriguard-prod-001",
    "agent_id": "8a7b9c1d-2e3f-4a5b-9c8d-1f2e3a4b5c6d",
    "issued_at": "2026-05-14T10:00:00Z",
    "task_count": 247,
    "schema_version_payload": "1.0",
    "exported_by": "operator-zhang"
  },

  "envelope_encrypted": {
    "scheme": "nacl-box",
    "kdf": "x25519",
    "cipher": "chacha20-poly1305",
    "sender_x25519_pub_b64": "<base64>",
    "nonce_b64": "<base64>",
    "ciphertext_b64": "<base64 of encrypted tasks JSON>"
  },

  "signature": {
    "scheme": "Ed25519",
    "signer_pub_b64": "<platform Ed25519 pub>",
    "sig_b64": "<sig over metadata + envelope>"
  }
}
```

**注意**：metadata 字段全部 opaque（UUID / 数字 / 时间戳 / 平台 ID），不含业务敏感数据。攻击 URL / 凭据 / 内网 IP 100% 在 `ciphertext_b64` 加密体内。

#### 3.5.3 `.vresults` schema

```json
{
  "schema_version": "1.0",
  "format": "vresults",
  "metadata_plaintext": {
    "pack_id": "550e8400-...",
    "agent_id": "8a7b9c1d-...",
    "executed_at": "2026-05-14T15:00:00Z",
    "result_count": 247
  },
  "envelope_encrypted": { "scheme": "nacl-box", ... },
  "signature": { "scheme": "Ed25519", "signer_pub_b64": "<agent A_sign_pub>", ... }
}
```

#### 3.5.4 Task / Result 数据结构（envelope 解密后）

```json
Task:
  { task_id: "T-001",
    injector_type: "web_attack" | "veriguard_implant" | "pcap_replay",
    capability:    "http_attack" | "command_inject" | "pcap_replay" | "implant_drop",
    payload:       <按 injector 不同的 JSON>,
    expectations:  ["PREVENTION", "DETECTION", "MANUAL", "VULNERABILITY"],
    target_asset:  { asset_id?, ip?, hostname? }
  }

Result:
  { task_id,
    status: "completed" | "failed" | "skipped" | "rejected_capability"
            | "implant_crashed" | "timeout",
    exit_code, stdout, stderr,
    response_digest: { status_code, body_head_b64, headers_summary },
    started_at, finished_at, error_message?
  }
```

#### 3.5.5 Agent 校验决策树（拒包顺序）

```
agent 加载 .vpack
  │
  ├─ 1. schema_version / format 校验          → 不符 → reject
  ├─ 2. Ed25519 signature 验签              → 失败 → reject (T-1/T-2 防伪造)
  ├─ 3. metadata.platform_id == 本机记录    → 不符 → reject (T-6)
  ├─ 4. metadata.agent_id == self.agent_id  → 不符 → reject (T-4)
  ├─ 5. pack_id ∉ executed_packs.db         → 已跑 → reject (T-3)
  ├─ 6. task_count <= 1000                  → 超量 → reject (T-7)
  ├─ 7. envelope_encrypted 解密              → 失败 → reject
  ├─ 8. 解密后 tasks 数 == metadata.task_count → 不符 → reject
  └─ 9. 逐 task 校 capability → 执行
```

**注**：去除 `valid_until` 字段（包不过期）；`pack_id` 唯一性 + 黑名单已独立解决重放问题。

### 3.6 Flyway 迁移设计

#### V19 — agents 表加密密钥列

```sql
-- veriguard-api/src/main/resources/db/migration/V19__agent_crypto_keys.sql

ALTER TABLE agents
  ADD COLUMN agent_sign_pubkey BYTEA NULL,
  ADD COLUMN agent_enc_pubkey  BYTEA NULL,
  ADD COLUMN agent_onboard_token TEXT NULL,
  ADD COLUMN agent_onboarded_at TIMESTAMPTZ NULL,
  ADD COLUMN agent_register_sig BYTEA NULL,
  ADD COLUMN agent_registered_from_ip INET NULL,    -- audit 保留项
  ADD COLUMN agent_created_by TEXT NULL;             -- audit 保留项

CREATE UNIQUE INDEX idx_agents_onboard_token
  ON agents(agent_onboard_token) WHERE agent_onboard_token IS NOT NULL;
```

#### V20 — offline_pack_audit 表

```sql
-- V20__offline_pack_audit.sql

CREATE TABLE offline_pack_audit (
  pack_id UUID PRIMARY KEY,
  agent_id UUID NOT NULL REFERENCES agents(agent_id),
  platform_id TEXT NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL,
  exported_by TEXT NOT NULL,
  exported_from_ip INET NULL,
  exported_ciphertext_sha256 BYTEA NOT NULL,
  task_count INT NOT NULL,
  imported_at TIMESTAMPTZ NULL,
  imported_by TEXT NULL,
  imported_from_ip INET NULL,
  result_count INT NULL,
  rejected_count INT NULL,
  CHECK (task_count >= 0 AND task_count <= 1000)
);

CREATE INDEX idx_pack_audit_agent ON offline_pack_audit(agent_id, issued_at DESC);
CREATE INDEX idx_pack_audit_imported ON offline_pack_audit(imported_at)
  WHERE imported_at IS NOT NULL;
```

> 下一可用 V 号 = V19（见 memory `project_veriguard_flyway_state`）；V19 + V20 在本 PR 内连续使用。

### 3.7 错误处理 / 失败隔离

| 错误类别 | Agent 侧 | Platform 侧 |
| --- | --- | --- |
| Pack 签名失败 | reject + log + 不进黑名单（防永久 deny 合法包）| import 拒收，audit 记录 |
| Pack 解密失败 | reject + log | 同上 |
| agent_id 不匹配 | reject | reject import |
| pack_id 重复 | skip pack + warn | reject re-import |
| Capability 不支持 | task status=`rejected_capability` | UI 显示原因 |
| 单 task 执行失败 | 写 failed result，继续下一 task | 入库 |
| Implant 崩溃 | kill + status=`implant_crashed` + stderr 上报 | 入库 + UI 红标 |
| Implant 超时 | SIGTERM → 5s 后 SIGKILL + status=`timeout` | 入库 |
| Mode A poll 超时 | exponential backoff (5s→10s→20s→...→max 5min) | N/A |
| SQLite locked | wait + retry up to 3 次 | N/A |
| Pack-dir 中某 pack 解析失败 | 默认中断；可配 `--continue-on-pack-error` | N/A |

**核心原则**：Task-level 错误隔离，pack-level 默认中断。

### 3.8 测试策略

#### 单元测试（Rust agent / implant + Java platform）

```
Crypto layer:
  ✅ Ed25519 sign+verify roundtrip
  ✅ X25519 box seal+open roundtrip
  ✅ 错 key 解密 → 拒
  ✅ 篡改 ciphertext → MAC 验失败 → 拒
  ✅ 篡改 metadata → 整体 signature 验失败 → 拒

Pack parser:
  ✅ 合法 .vpack 解析成功
  ✅ schema_version 不识别 → 拒
  ✅ agent_id 不匹配 → 拒
  ✅ pack_id 重复（黑名单命中）→ skip
  ✅ task_count > 1000 → 拒
  ✅ envelope ciphertext 与 metadata.task_count 不符 → 拒

Capability handlers (mock target):
  ✅ http_attack: GET/POST/PUT 各方法 + headers + body
  ✅ pcap_replay: tcpreplay 子进程启动 + exit code
  ✅ command_inject: implant drop + run + result 回传
  ✅ implant_drop: 二进制落盘 + chmod + cleanup
```

#### 集成测试场景

| ID | 场景 | 验证点 |
| --- | --- | --- |
| A1 | Mode A 端到端 | mock platform + agent (Mode A) → 1 个 http_attack task → 平台收 result + DB 入 inject_execution |
| A2 | Mode A 多 capability | 4 task (http_attack/pcap_replay/command_inject/implant_drop 各一) → 全 success |
| C1 | Mode C 单包 | platform export → .vpack 落盘 → agent --offline-pack → 跑 → .vresults → platform import → 入库 |
| C2 | Mode C 多包目录 | 3 个 .vpack 在目录 → lex 顺序串行 → 3 个 .vresults |
| C3 | 故障注入 | Pack 2 签名错 → 中断；Pack 1 已跑结果保留；Pack 3 未跑 |
| O1 | Onboarding 在线 | init → register → agent 可 poll |
| O2 | Onboarding 离线 | init → install_pack 拷贝 → agent --register-offline-output → .vregister → platform import → agent 可工作 |
| O3 | 一行 curl 安装 | UI 生成 token → curl 命令 → docker exec ubuntu-test → 5 分钟内 agent online |

#### 安全测试（T-1 ~ T-8 黑盒）

```
T-1 篡改:   改 .vpack ciphertext 1 字节 → MAC 失败 → 拒
T-2 伪造:   攻击者 Ed25519 签 pack → signer_pub 不在白名单 → 拒
T-3 重放:   跑过的 pack 再 import → blacklist 命中 → 拒
T-4 跨 agent: 用 Agent A 的 pack 给 Agent B → agent_id 不符 → 拒
T-5 伪造结果: 攻击者签 .vresults → A_sign_pub 不匹配 → 平台拒收
T-6 跨平台: 别 platform_id 的 pack → 拒
T-7 容量:   1001 任务 → metadata.task_count 触发上限 → 拒
T-8 越权:   pack 含 command_inject，agent 只有 http_attack → skip 该 task
```

#### 跨平台矩阵

```
CI Build matrix:
  Linux amd64 / Linux arm64 / Windows amd64 / Windows arm64 /
  macOS amd64 (Intel) / macOS arm64 (Apple Silicon)

Smoke test on each:
  veriguard-agent version
  veriguard-agent init --install-pack <sample>
  veriguard-implant --task-id T-test
```

### 3.9 C1 验收清单（DoD）

C1 PR 可 merge 当且仅当以下全 ✅：

- [ ] `wangjuelong/veriguard-agent` 仓建，CI 矩阵 build 6 binaries 通过
- [ ] `wangjuelong/veriguard-implant` 仓建，CI 矩阵 build 6 binaries 通过
- [ ] Veriguard 主仓 CI step：拉 release artifact → 校 SHA256 → 落到 `agents/{veriguard-agent,veriguard-implant}/{os}/{arch}/` 替换 .keep
- [ ] V19 + V20 Flyway 迁移 applied
- [ ] `WebAttackDispatchService` TODO 删除，真接通（1104 条 web_attack payload 真消费）
- [ ] `HttpInjectExecutor`（§3.6 ★2）真接通
- [ ] `CommandInjectExecutor` 新增，1781 条 ART Command payload 真执行通道
- [ ] `PcapReplayExecutor` 真接通
- [ ] `AgentOnboardApi` + `AgentOfflinePackApi` + `AgentTaskQueueApi` + `AgentImplantDownloadApi` + `AgentInstallScriptApi` 全部上线
- [ ] Crypto 模块单测覆盖 ≥ 95%
- [ ] 集成测 A1/A2/C1/C2/C3/O1/O2/O3 全过
- [ ] 安全测 T-1 ~ T-8 全过
- [ ] TLS cert fingerprint pin 写入 install_pack + agent 校验
- [ ] audit 表记 `created_by` / `registered_from_ip` / `imported_from_ip`
- [ ] 一行 curl 在 Ubuntu 22.04 / macOS / Windows PowerShell 三平台冒烟通过
- [ ] dev DB 端到端：一台 docker 攻击机 → 一行 curl 装 → 推 §3 + §5 各 1 用例 → 全跑通

---

## 4. C2 详设

### 4.1 C2 范围

| 维度 | 内容 |
| --- | --- |
| 核心交付物 | §5.1 12 类 → ATT&CK / Implant payload / ART 映射对照表 |
| 演练剧本 | 2 台标准靶机（Ubuntu 22.04 x86_64 + Windows 10/Server x86_64）跑通 §3 + §4 + §5 三场景闭环 |
| 部署文档 | 7 份文档（快速上手 / 拓扑选择 / Mode A proxy / Mode C / 一行 curl / 故障排查 / Future Work）|
| 不做 | 国产 OS 适配（保留 placeholder）；其他 Out-of-Scope 见 §5 |

### 4.2 §5.1 12 类 ↔ ATT&CK / Implant payload / ART / HIDS 检测维度

> **落地文件**：`docs/IPv6安全验证系统-§5主机12类映射对照表.md`

| # | §5.1 类别 | MITRE ATT&CK | Implant Payload | ART 代表用例 | HIDS 检测维度（云眼应抓的信号） |
| --- | --- | --- | --- | --- | --- |
| 1 | 反弹 shell | T1059.004 Unix Shell / T1059.001 PowerShell | `Command` | T1059.004 ART idx 1-15 | proc: bash/sh/powershell fork + 立即建出站 TCP 长连 |
| 2 | webshell 上传落盘 | T1505.003 Web Shell | `FileDrop` | T1505.003 webshell sample 集 | file: `*.php`/`*.jsp`/`*.aspx` 落到 web 根目录 |
| 3 | 命令执行 | T1059 Command and Scripting Interpreter | `Command` | ART 1781 条 Command（已落库）| proc: exec 链 + 异常父子关系（java → bash）|
| 4 | 隧道代理 | T1572 Protocol Tunneling | `Command` | `ssh -R 4444:localhost:22` / SOCKS5 chiseling | network: 非标端口长连 + 异常路由 |
| 5 | 内存注入 webshell | T1055 Process Injection | `Executable` | in-memory loader (Behinder / Godzilla 类) | proc: 父进程无 file backing 的 mmap RWX 段 |
| 6 | 主机内暴力破解 | T1110.001/.003 Brute Force | `Command` | `hydra` / `patator` / 内置 SSH bruteforce | proc + network: 短时高频认证请求 |
| 7 | RAT 远控木马执行 | T1219 Remote Access Software | `Executable` | meterpreter sample / Cobalt Strike beacon sample | proc: 已知 RAT 进程名 / 心跳协议指纹 |
| 8 | 系统提权 | T1068 Exploitation for PrivEsc | `Executable` | LinPEAS / WinPEAS / pwnkit (CVE-2021-4034) | proc: setuid 调用 / kernel sym 越权 |
| 9 | 网站篡改 | T1565.001 Stored Data Manipulation | `FileDrop` | 改 `/var/www/html/index.html` | file: webserver 静态文件被外部写入 |
| 10 | 病毒样本落盘 | T1105 Ingress Tool Transfer | `FileDrop` | EICAR 标样 + 自定义 SHA256 黑名单样本 | file: 文件 hash 命中威胁情报库 |
| 11 | 痕迹清理 | T1070.002/.003 Clear System Logs | `Command` | `rm /var/log/auth.log` / `wevtutil cl Security` | file: 日志文件 truncate / proc: 调用日志清理 API |
| 12 | 主机持久化 | T1543 Create or Modify System Process | `FileDrop` + `Command` | cron 注入 / systemd unit / `HKLM\...\Run` | file + config: 持久化锚点对象被创建 |

**对照表每行还携带 4 个字段**（spec 完整表里有）：
- `payload_external_id_range`（B*-XX-NNN 模式 / ART-NNN）
- `injector_type`（统一是 `veriguard_implant`）
- `capability`（`command_inject` / `implant_drop`）
- `expected_alert_pattern_hint`（招标演示时 NxSOC 应返回的 ruleId 例样）

**落地路径汇总**：

- Linux 全 12 类可跑（agent + implant on Ubuntu 22.04 x86_64）
- Windows 全 12 类可跑（agent + implant on Win 10 / Server 2022 x86_64）
- macOS 7 类可跑（4/5/8/9/10 类受 SIP / TCC 影响，演练剧本中跳过）

招标 §5 验收只看 Linux + Windows，macOS 是 nice-to-have。

### 4.3 真靶机演练剧本

> **落地文件**：`docs/IPv6安全验证系统-真靶机演练剧本.md`

#### 4.3.1 演练拓扑（C2 交付 docker-compose）

新增 `veriguard-dev/docker-compose.演练.yml`：

| 服务 | 容器镜像 | 作用 |
| --- | --- | --- |
| `nginx-bridge` | nginx:alpine | Mode A forward proxy 模拟（DMZ 跳板）|
| `mock-waf` | nginx:alpine + Lua 脚本 | 拦 SQLi/XSS/XXE 等 13 种 web 攻击模式 |
| `mock-ids` | suricata:latest 简化规则集 | 抓 pcap 重放流量 |
| `mock-nxsoc` | python:3.11-slim + Flask | 提供 `queryAlerts` 端点，按预设逻辑返 ruleId |
| `mock-siem` | logstash-mock | 把"云眼"日志聚合后转发 mock-nxsoc |
| `ubuntu-test` | ubuntu:22.04 + auditd | 攻击机 + 靶机双角色 |
| `win10-test` | mcr.microsoft.com/windows/server:ltsc2022 | Windows 靶机（LTSC Server 替代 Win10）|

#### 4.3.2 5 章节演练剧本

```
Chapter 1: 起环境（5 分钟）
  docker compose -f docker-compose.yml -f docker-compose.演练.yml up -d
  等 healthcheck 全绿
  UI: https://veriguard-api:8080/admin/veriguard

Chapter 2: 装 agent — Mode A 一行 curl（5 分钟）
  UI 新建 Agent "ubuntu-test-01" → copy curl 命令
  docker exec -it ubuntu-test bash
  paste curl → auto 装 + 注册 + service 起
  UI 上 ubuntu-test-01 显示 online
  win10-test 重复 PowerShell iwr

Chapter 3: §3 边界场景演练（15 分钟）
  UI 创建 CoverageRun（baseline = B8 sqli/xss 各 10 条共 20）
  跑 → 看 4 态矩阵
  预期 ~70% hit / ~30% miss
  截图存档（招标交付素材一）

Chapter 4: §5 主机 12 类演练（30 分钟）
  12 类 × 5 条 = 60 条用例
  每类预期 ≥ 95% hit
  截图存档（招标交付素材二）

Chapter 5: Mode C sneakernet 兜底演练（15 分钟）
  UI export 10 条 §3 任务 → day1.vpack
  docker cp 到 ubuntu-test
  agent --offline-pack day1.vpack
  docker cp day1.vresults 回
  UI import → 矩阵刷新
  截图存档（招标交付素材三 — Mode C 流程）
```

#### 4.3.3 演练完成标准

- [ ] 一行 curl 5 min 内装好 ubuntu-test agent
- [ ] §3 边界 20 条用例 4 态矩阵完整填充
- [ ] §5 主机 60 条用例 ≥ 95% 命中预期 ruleId
- [ ] Mode C sneakernet 10 条端到端跑通
- [ ] 3 份截图素材入档

### 4.4 部署文档骨架

| 文档 | 路径 | 受众 | 长度 |
| --- | --- | --- | --- |
| 1. 快速上手 | `docs/agent/QUICKSTART.md` | 操作员 / 招标演示 | 1-2 页 |
| 2. 网络拓扑选择指南 | `docs/agent/network-topology.md` | 甲方运维 | 3-5 页（T1-T4 4 种拓扑示意图）|
| 3. Mode A forward proxy 部署 | `docs/agent/deployment-mode-a-proxy.md` | 甲方运维 | 5-8 页（nginx config + cert openssl.cnf 样板）|
| 4. Mode C 离线 sneakernet 操作手册 | `docs/agent/deployment-mode-c-offline.md` | 甲方运维 | 4-6 页 |
| 5. 一行 curl 安装详解 | `docs/agent/install-curl-oneliner.md` | 招标演示 + 操作员 | 2-3 页 |
| 6. 故障排查 FAQ | `docs/agent/troubleshooting.md` | 运维 / 支持 | 6-10 页 |
| 7. Future Work / 国产 OS follow-up | `docs/agent/FUTURE-WORK.md` | 后续 PR 维护者 | 2-3 页 |

### 4.5 C2 验收清单（DoD）

- [ ] `docs/IPv6安全验证系统-§5主机12类映射对照表.md` 完成（12 类全填，每类含 4 字段）
- [ ] `docs/IPv6安全验证系统-真靶机演练剧本.md` 完成
- [ ] `veriguard-dev/docker-compose.演练.yml` 新增（7 个服务）
- [ ] docker compose up 后 5 章节演练全部跑通
- [ ] 3 份招标截图素材入档
- [ ] 7 份部署文档全部写完
- [ ] `FUTURE-WORK.md` 含 4 项 skip 项 + 国产 OS follow-up + 流量场景增强 3 大方向

---

## 5. Out-of-Scope / Future Work

本节记录 C1+C2 显式 **不做** 的工作项，留作后续 PR（C3+ / C4+）输入。

### 5.1 4 项安全加强（C3+ 候选）

#### 5.1.1 操作员 + agent 公钥指纹 UI 双对账

- **业务价值**：防 install_pack 投递路径篡改（S-2 第二层防御）
- **C1 暂未做原因**：与一行 curl UX 严重冲突；TLS pin 已防 80%；人眼对比 hex 易 cargo cult
- **C1 已留接口**：`veriguard-agent fingerprint` CLI 命令存在
- **何时启用**：高敏环境 / 甲方政策要求

#### 5.1.2 异常注册告警

- **业务价值**：防 token 泄露重放尝试（S-1 主动告警）
- **C1 暂未做原因**：招标 9 周交付期内基本不会触发；甲方告警通道能否消化未知
- **C1 已留数据源**：audit 表（offline_pack_audit + agents 表）已记 `created_by` / `registered_from_ip`
- **何时启用**：甲方有 SOC 集中告警通道时 / C3+

#### 5.1.3 TPM-bound 私钥

- **业务价值**：防 root 攻陷 agent host 后私钥外泄跨主机重放
- **C1 暂未做原因**：硬件依赖 + 3 平台 API 不通用 + 备份复杂度高 + 1 周工作量
- **缓解措施**：文件系统加密（LUKS / FileVault / BitLocker）+ chmod 0600 + 独立系统用户
- **何时启用**：招标新版本明确"硬件级密钥保护"硬指标

#### 5.1.4 密钥定期自动轮换

- **业务价值**：限制密钥泄露的爆炸窗口
- **C1 暂未做原因**：招标 9 周交付期内密钥本来就是新的；自动轮换边缘情况多
- **C1 已留接口**：`veriguard-agent rotate-keys` 手动 CLI
- **何时启用**：长期运行（>3 个月）部署 / 等保合规审计

### 5.2 国产 OS 适配（C3 候选）

#### 5.2.1 适配目标 OS

| OS | 架构 | 适配工作量 | 备注 |
| --- | --- | --- | --- |
| 飞腾 ARM64 | ARM64 | ≈ 0.5 天 | 与 standard arm64 二进制兼容 80% |
| 统信 UOS V20 (x86_64) | x86_64 | ≈ 1 天 | 基于 Debian，glibc 版本差异 + systemd unit 差异 |
| 麒麟 V10 (x86_64) | x86_64 | ≈ 1 天 | 基于 RHEL，需特殊编译 flag |
| 龙芯 LoongArch64 | LoongArch64 | ≈ 1 周 | Rust LoongArch target 自 1.71+ 标准；`cargo zigbuild --target loongarch64-unknown-linux-gnu` 跨编 + 单独 CI 矩阵 + 兼容性测 |

**合计 C3 工作量 ≈ 2-3 周**（独立 PR）

### 5.3 流量场景增强（C4 候选）

#### 5.3.1 东西向 pcap 中继

- 多 agent 协同（A 发 pcap，B 抓响应）
- 用例：§4 流量东西向 stretch goal

#### 5.3.2 Implant 加 http_attack capability

- 当 agent 不能直发（OPSEC 要求）时由 implant 替发
- 用例：§3 边界 + §5 主机联合验证（同时测攻击机 EDR）

---

## 6. 关键设计决策日志

| # | 决策 | 选项 | 决策 | 理由 |
| --- | --- | --- | --- | --- |
| D1 | C1/C2 切片方式 | 平台层+适配层 / Linux 全量 vs Win+macOS / 纯 fork vs 业务集成 | **平台层+适配层** | 切片自然；C1 横跨三场景；C2 单点冲刺国产 OS |
| D2 | 边界场景执行体 | Agent 自执行 / 走 implant | **Agent 自执行** | §3.5 验收只看边界设备；implant 中间层无价值 |
| D3 | 主机场景执行体 | Agent / Implant | **Implant** | implant 才是 HIDS 检测对象本体；agent 是控制面 |
| D4 | 通信模式 | Mode A 单 / Mode C 单 / A+C 双 | **A + C 双轨** | T1/T3 走 A，T4 走 C，T2 灵活；招标场景拓扑不确定 |
| D5 | 攻击机国产 OS 支持 | 做 / 不做 | **不做** | 攻击机 OS 乙方可控 |
| D6 | 靶机国产 OS 支持 | 做 / 不做 | **不做** | 简化 C1+C2 工期；留 C3+ |
| D7 | 加密协议 | AES-256-GCM / ChaCha20-Poly1305 + NaCl box | **NaCl box (X25519 + ChaCha20-Poly1305)** | 在国产 ARM 无 AES-NI 上更快；NaCl 标配 |
| D8 | 签名协议 | HMAC / Ed25519 | **Ed25519** | 非对称，平台失陷不影响 agent；agent 失陷只丢一台 |
| D9 | 包格式 | JSON envelope / 二进制紧凑 | **JSON envelope** | 可审计 / 调试友好；25-35% 大小开销可接受 |
| D10 | 包过期机制 | 包带 valid_until / 无 TTL | **无 TTL** | `pack_id` 唯一 + 黑名单已防重放；TTL 是多余防御 + sneakernet 时序不可控 |
| D11 | 多包模式 | 单包 / 单+多包目录 / 目录 only | **单包 + 多包目录** | 调试用单包，生产用目录；CLI 双模 |
| D12 | onboarding 流 | 上游 api_key / 完全统一非对称 / mTLS+独立 | **完全统一非对称** | 比上游强；Mode A/C 共用一套；招标差异化卖点 |
| D13 | 一行 curl 安装 | 做 / 不做 | **做** | Tailscale UX；招标演示加分；0.5 周代价 |
| D14 | TLS cert pin | 做 / 不做 | **做** | S-2 防御第一层；运维负担 0 |
| D15 | audit IP 记录 | 做 / 不做 | **做** | 溯源刚需；运维负担 0 |
| D16 | 公钥指纹双对账 | 做 / 不做 | **不做** | 破坏 curl UX；TLS pin 已防；C3+ 视需求 |
| D17 | 异常注册告警 | 做 / 不做 | **不做（C1）** | audit 表已落，pull 视角够；C3+ 推送 |
| D18 | TPM-bound 私钥 | 做 / 不做 | **不做** | 硬件依赖 + 3 平台 API 不通；C3+ 视需求 |
| D19 | 密钥自动轮换 | 做 / 不做 | **不做**（手动 CLI 保留）| 招标 9 周内密钥新鲜；边缘情况多；C3+ |

---

## 7. 风险与已知限制

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| 上游 implant 仓含敏感 RAT-like 代码引来误杀 | 🟡 中 | CI 在私有仓跑；release artifact 不进 public CI；甲方靶机白名单 |
| HSM/KMS 部署被甲方拒（platform 私钥落文件）| 🟡 中 | chmod 0600 + 独立系统用户 + 服务器 OS 加固；运维文档提示 |
| ChaCha20-Poly1305 / Ed25519 算法被破解 | 🟢 低（招标 3 年内）| 协议含 `scheme` 字段，可升级算法 |
| 一行 curl 安装被运维拒（curl + sudo bash 反模式）| 🟢 低 | 文档提示先 `curl ... | less` 审阅；UI 内嵌脚本预览 |
| sneakernet 周期长于 24h 后 token 过期 | ✅ 已消解 | 包不再有 TTL；onboard_token 24h 但一次性 |
| Mode A poll latency 影响 §3.6 ★2 30k 组合吞吐 | 🟡 中 | poll_interval 可配；platform 任务队列批量化 |
| 国产 OS 上 Rust binary 兼容性（musl / glibc / LoongArch ABI）| 🟢 低（C3 范围）| 飞腾 / 鲲鹏 ARM64 直接复用 standard arm64；LoongArch 需 `cargo zigbuild --target loongarch64-unknown-linux-gnu`；C3 跨编译时再细分 |
| 甲方网络策略禁 DMZ → 内网 inbound（T2 拓扑）| 🟢 低（已设计兜底）| Mode C sneakernet 保底；Forward proxy 需甲方协助部署 1 台 bridge |

> **不再列入风险**：上游 OpenBAS 后续版本变更——fork 已一次性脱钩（见 §1.3 / §2.6），后续上游演化与本仓无关。

---

## 8. 时间线与依赖

### 8.1 工期分解

| 模块 | 工作量 |
| --- | --- |
| **C1 基础工作**（fork + 编译 + 协议 + Spring 改）| ≈ 1.5 周 |
| **C1 安全 + 多包 + 离线**（HMAC/Ed25519 + 7 层防 + .vpack 解析 + 多包目录）| ≈ 1.2 周 |
| **C1 加密 envelope**（NaCl box + JSON envelope + Java/Go 双端）| ≈ 0.8 周 |
| **C1 一行 curl 安装**（bootstrap flag + script 模板 + service 安装器）| ≈ 0.5 周 |
| **C1 保留 2 项加强**（TLS pin + audit IP 列）| ≈ 0.2 周 |
| **C1 手动密钥轮换 CLI** | ≈ 0.05 周 |
| **C1 测试 + 调试缓冲** | ≈ 0.05 周 |
| **C1 总计** | **≈ 4.3 周** |
| **C2 总计**（12 类对照表 + 演练剧本 + 7 份文档）| **≈ 0.5 周** |
| **C1 + C2 合计** | **≈ 4.8 周** |

### 8.2 依赖与并行

- **不阻塞主线**：C1+C2 是独立 fork 项目，可与主仓 PR A1 / B9 / 截图素材准备并行
- **阻塞的下游**：A1（NxSocAdapter 真实联调）需要 C1 完成才能联调，因为只有 C1 后 §3 / §5 才有真流量打到 SOC
- **招标主时间盘**：5/18 启动 - 7/18 交付 共 9 周；C1+C2 ≈ 4.8 周 + A1 ≈ 1 周 + 截图素材 ≈ 1 周 + 验收缓冲 ≈ 2 周 = 8.8 周，刚好

### 8.3 关键里程碑

| 周 | 里程碑 |
| --- | --- |
| W1 | veriguard-agent 仓建 + 上游 fork + Linux x86_64 hello world 跑通 |
| W2 | 4 capabilities skeleton + crypto 模块 + V19 migration |
| W3 | Mode A 端到端（Scenario A1 / A2 通过）|
| W4 | Mode C 端到端（Scenario C1 / C2 / C3 通过）+ 一行 curl 三平台 |
| W5 | 安全测 T-1 ~ T-8 + 6 binaries CI release + C2 演练剧本跑通 |
| W6 | A1 NxSocAdapter 真实联调 + 截图素材准备 |
| W7 | 招标验收预演 + 国产 OS 兼容性 spot check（不正式适配，只看 bug）|
| W8 | 招标正式验收 |

### 8.4 并行执行策略

C1 工作可拆 3 个 Stream，**契约（CLI / REST / Pipe / Schema）spec 已全部定型**（§3.3.2 / §3.3.3 / §3.5.1 / §3.5.2 / §3.5.3 / §3.5.4），具备并行开发条件。

#### 三 Stream 依赖图

```
Stream A: veriguard-agent (Go) ─── 3.0 周（critical path）
  ╰ 依赖 B 的 CLI/Pipe 协议   (spec §3.3.2 / §3.3.3 已定)
  ╰ 依赖 C 的 REST 契约       (spec §3.5.1 已定)
                                                    
Stream B: veriguard-implant (Go) ─── 1.0 周
  ╰ 依赖 A 的 CLI 调用契约    (spec §3.3.2 已定)
                                                    
Stream C: veriguard-api (Java) ─── 2.0 周
  ╰ 依赖 A 的 REST 客户端行为 (spec §3.5.1 已定)
                                                    
Integration + bugfix ────────── 0.5 周（必须三流 alpha 后）
C2 doc + 演练 ──────────────────── 0.5 周（必须 C1 alpha 后）
```

#### Stream 任务清单

| Stream | 关键任务 | 工作量 | mock 顶替策略（独立开发期）|
| --- | --- | --- | --- |
| **A: agent** | crypto / onboarding / Mode A poll / Mode C pack / 4 capabilities / install scripts / CI | 3.0 周 | mock implant = echo 假回执 shell；mock platform = mockserver / Postman |
| **B: implant** | 5 payload 类 / pipe writer / self-delete / 跨平台 / CI | 1.0 周 | 直接 shell wrapper 跑测；无需 agent |
| **C: platform** | crypto services / WebAttack/PcapReplay/HttpInject 真接通 / CommandInjectExecutor 新增 / 8 REST endpoint / V19+V20 / audit | 2.0 周 | curl 单元测顶替 agent；mock agent 注册 |

#### 并行可行性矩阵

| 流间 | 能否并行 | 缓解 |
| --- | --- | --- |
| A ↔ B | ✅ 完全可并行 | CLI/Pipe 契约 spec §3.3 已定；A 端 mock implant，B 端 shell wrapper |
| A ↔ C | ✅ 完全可并行 | REST 契约 spec §3.5.1 已定；A 端 mockserver，C 端 curl 单元测 |
| B ↔ C | ✅ 完全可并行 | B 与 C 之间无直接依赖 |
| A+B+C → Integration | ❌ 必须等三方 alpha | 三方各 freeze 一个 alpha tag |
| Integration → C2 | ❌ 必须等 C1 alpha | C2 文档骨架可提前起草 |

#### 三档执行策略（按工程资源）

| 资源 | 推荐 | wall clock | 备注 |
| --- | --- | --- | --- |
| **1 人开发** | 串行：A → B → C → Int → C2 | ≈ **4.8 周** | 按 §8.3 周里程碑跑；最 likely 路径 |
| **2 人开发** | A 全程 / (B→C) 串接 | ≈ **4.0 周** | 节省 0.8 周；2 人 W3 合体做 Int + C2 |
| **3 人开发** | A / B+C2 docs 起草 / C 三流 | ≈ **4.0 周** | 与 2 人持平（A 是 critical path）；第 3 人介入 dev/staging 环境 + C2 演练预跑 |

**关键发现**：**Stream A (agent) 是 critical path** ≈ 3.0 周；无论多少人无法压缩 A，第 3 人收益递减。**2 人编制是最优投入**。

---

## 9. 引用 / 关联文档

- `docs/IPv6安全验证系统-研发拆解.md` §5 / §9.2 — 主机验证 / 平台自有 Agent
- `docs/IPv6安全验证系统-甲方待澄清清单.md` #2 / #9 — 靶机部署细则 / 平台-攻击机网络拓扑（新增）
- `docs/IPv6安全验证系统-外部接口清单.md` — 蓝盾 NxSOC / 集团 SIEM 集成边界
- `docs/IPv6安全验证系统-业务模块Agent与数据流.md` — Agent / Implant 数据流上下文
- `docs/superpowers/specs/2026-05-12-ipv6-security-validation-refinement-design.md` — 招标响应总体设计
- `docs/superpowers/specs/2026-05-12-veriguard-b-ii-design.md` — B-ii PR-C web_attack injector 前置工作
- `docs/superpowers/plans/2026-05-12-veriguard-b-ii-pr-c-web-attack-plan.md` — web_attack 落地详细 plan
- 上游 `OpenBAS-Platform/agent` — Veriguard Agent fork base
- 上游 `OpenBAS-Platform/implant` — Veriguard Implant fork base

---

*文档完*
