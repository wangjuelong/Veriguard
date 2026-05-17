# IPv6 安全验证系统 — 无损模拟攻击技术方案

| 项 | 内容 |
| --- | --- |
| **文档作用** | 安全防护验证系统（BAS）无损模拟攻击落地蓝图 |
| **整理日期** | 2026 年 5 月 |
| **受众** | 项目实施方、研发团队、甲方技术 owner、安全运营对接人 |
| **配套文档** | [`IPv6安全验证系统.md`](./IPv6安全验证系统.md) 总览 / [`-技术方案.md`](./IPv6安全验证系统-技术方案.md) 总体设计 / [`-研发拆解.md`](./IPv6安全验证系统-研发拆解.md) 工程拆解 |

---

## 文档导读

本方案融合两派业界思想，给出 Veriguard 二开（IPv6 安全验证系统）三大场景（边界 / 流量 / 主机）的无损模拟攻击完整落地路径：

- **无损派**（中文 BAS 主流）：真攻击，零伤害 —— 流量标记、探针热加载、不利用漏洞、灭活样本、沙箱隔离。代表：腾讯 TBAS、墨云 VackBAS、永信至诚。
- **强归因派**（国际 BAS 主流）：时空近似不可靠 —— per-inject UUID 嵌流量、artifact 指纹、SIEM 协作。代表：AttackIQ、SafeBreach、Mandiant Security Validation。

两派合并后，方案分七大模块：

| 模块 | 内容 |
| --- | --- |
| 一、设计原则 | 四条统一原则 |
| 二、核心技术栈 | 五大支柱（S1-S5） |
| 三、三场景方案 | 边界 / 流量 / 主机各自详细架构 |
| 四、归因决策表 | 三层归因（L1/L2/L3）+ confidence 分级 |
| 五、SIEM 协作机制 | 服务端归因（可选高阶） |
| 六、专用靶机部署 | 仿真接收端约定 |
| 七、差距与路线 | 当前实现 vs 目标方案 + 分期落地 |

---

# 一、设计原则

四条统一原则：

| 原则 | 含义 |
| --- | --- |
| **真攻击，零伤害** | 发出的报文 / 进程 / 文件在协议层、特征层、行为前段是真攻击；在效果层（漏洞利用、数据破坏、横向移动）被工程截停 |
| **强归因优先，时空兜底** | 每条 inject 必须带 per-execution UUID + artifact 指纹；当且仅当二者都失效时退化为时空近似，且标记 confidence |
| **默认安全（fail-safe）** | 任何不确定路径默认走"专用靶机"或"沙箱"，不打生产业务机 |
| **可审计** | 每条 inject 留 artifact bundle + 归因等级 + 决策依据，事后可复盘 |

---

# 二、核心技术栈（五大支柱）

| 支柱 | 解决的问题 | 技术手段 |
| --- | --- | --- |
| **S1 流量级无害化** | 不实际打到漏洞 | Packet Tagging + Detection-Trigger-Only payload + 灭活样本 |
| **S2 探针热加载** | 不长期驻留 / 不影响业务 | 空壳 implant + 任务期 module 下载 + 完成即卸载 |
| **S3 仿真接收端** | 不打到生产业务机 | 专用靶机（IP 段隔离）+ 协议级 echo / drop |
| **S4 隔离沙箱** | 高风险样本不扩散 | CAPEv2 沙箱 + 快照回滚 + 网络隔离 |
| **S5 强归因机制** | 区分 Veriguard 攻击 vs 真实攻击 | 三层归因 L1 标记 / L2 工件 / L3 时空 + confidence 分级 |

---

# 三、Veriguard 三场景方案

## 3.1 场景 A —— 边界安全防护验证

### 3.1.1 验证对象与攻击通道

- **验证对象**：边界 WAF / 边界防火墙 / 出口 IPS / DLP / 邮件网关
- **攻击通道**：HTTP/HTTPS Web 攻击包、钓鱼邮件、协议混淆

### 3.1.2 部署形态

```
            ┌────────────────────────────────────────┐
            │  Veriguard Platform (内网管理区)        │
            │  ↓ Mode A / Mode C                     │
            │  veriguard-agent (边界外公网 OR DMZ)    │
            │  → 发出带 L1 标记的 HTTP/HTTPS 请求    │
            └────────────────┬───────────────────────┘
                             │ 公网 / DMZ 经过
                             ▼
            ┌──────────────────────────────┐
            │  边界 WAF / FW / IPS  (SUT)  │ ← 验证目标
            └──────────────┬───────────────┘
                           │
                           ▼
            ┌──────────────────────────────┐
            │  专用靶机 nginx (生产同段)    │ ← 仿真接收端
            │  return 200 "VG-RECEIVED"    │
            └──────────────────────────────┘
                           │
                           ▼
            ┌──────────────────────────────┐
            │  SOC / SIEM (Elastic)         │ ← 归因查询源
            │  alert 自动带 bas_session_id  │
            └──────────────────────────────┘
```

### 3.1.3 L1 标记规范（强归因）

| 协议位 | 字段 | 示例 |
| --- | --- | --- |
| HTTP header | `X-Veriguard-Run-Id` | `c2a3f1...` (run UUID) |
| HTTP header | `X-Veriguard-Inject-Id` | `n7b91...` (per-inject UUID) |
| HTTP header | `X-Veriguard-Tenant` | `gfc-fugu-2026` |
| TLS SNI | `<inject-id>.bas.<tenant>.veriguard.local` | 走专用域名 |
| User-Agent 后缀 | `Veriguard/0.1.0 (drill;run=<runId>)` | 兼容旧 SIEM |

### 3.1.4 灭活 payload 示例

```http
# 原始攻击（真打）
POST /login HTTP/1.1
username=admin' OR '1'='1--&password=x

# 无损版本
POST /login HTTP/1.1
X-Veriguard-Run-Id: c2a3f1...
X-Veriguard-Inject-Id: n7b91...
User-Agent: Veriguard/0.1.0 (drill;run=c2a3f1)
username=admin' /* VG-PROBE n7b91 */ OR '1'='1-- VG-NEUTRALIZED&password=__vg_probe__
```

- 真攻击：`' OR '1'='1--` 会被 WAF 规则命中（SQL 注入特征保留）
- 但实际打到的是 **专用靶机 nginx**，nginx 不做数据库查询，直接 `return 200`
- 即使打错地址打到生产 web，参数里的 `VG-NEUTRALIZED` 在业务侧约定不解析

### 3.1.5 WAF 视角的检测流

检测规则该命中的全命中（特征齐全） → SIEM 出告警 → 告警 header 含 `X-Veriguard-Run-Id` → Veriguard 反查 ES 100% 强归因 ✅

### 3.1.6 当前已实现 / 待补

| 项 | 状态 |
| --- | --- |
| WebAttack injector 框架 `injectors/web_attack/` | ✅ 已实现 |
| 自动注入 L1 标记 header | ❌ 当前要 payload 作者手填 |
| 仿真接收端约定 | ❌ 当前能打到任何 URL |
| SOC connector 加 `x-veriguard-run-id` 字段查询 | ❌ 未实现 |

---

## 3.2 场景 B —— 流量安全防护验证

### 3.2.1 验证对象与攻击通道

- **验证对象**：NTA / 全流量分析 / 入侵检测系统 IDS / 网络威胁检测
- **攻击通道**：pcap 回放、多协议自定义流量（含 §4.4 多端口四元组）

### 3.2.2 部署形态

```
┌─────────────────────────────────┐
│  Veriguard Platform              │
│  pcap pool / 流量用例库          │
└──────────┬──────────────────────┘
           │ Mode A enqueue task
           ▼
┌─────────────────────────────────┐
│  veriguard-agent (注入点)        │
│  → 灭活 pcap 重放                │
│  → 多 src/dst port 四元组流量    │
│  → magic bytes 前缀注入          │
└──────────┬──────────────────────┘
           │ 镜像 / 旁路
           ▼
┌─────────────────────────────────┐
│  NTA / IDS / 全流量 (SUT)        │ ← 验证目标
└──────────┬──────────────────────┘
           │
           ▼
┌─────────────────────────────────┐
│  专用靶机 netcat listener        │ ← 仿真接收端
└─────────────────────────────────┘
```

### 3.2.3 pcap 灭活规范

| 操作 | 内容 |
| --- | --- |
| **保留** | 5-tuple 形态、IP/TCP/UDP header、TLS handshake、HTTP 请求行、DNS query name、payload 长度分布、TCP flags 序列 |
| **替换** | payload body 中的真实 exploit 字节 → 固定模式 `0xAA AA AA AA + 16B inject_id` |
| **校验和** | 重新计算（避免被 NTA 当损坏包丢弃） |

**示例**：CVE-2017-0144 EternalBlue pcap 灭活后，SMB 协商 + Trans2 Subcmd FE 特征完整保留，shellcode 段全替换为 `0xAA` + inject_id。NTA 规则按"SMB Trans2 Subcmd FE 异常长度"命中告警，但接收端是专用靶机 netcat，不会真崩。

### 3.2.4 L1 标记规范

| 流量层 | 标记位 | 说明 |
| --- | --- | --- |
| L2 以太 | 源 MAC 来自 BAS 专用 MAC 段（OUI 注册） | 网管设备级隔离 |
| L3 IP | 源 IP 来自 BAS 专用 IP 段（如 `10.255.0.0/24`） | SIEM 白名单 |
| L4 TCP/UDP | 源端口固定在 `49152-49200` | 5-tuple 强归因 |
| L7 payload | 前 8 字节 magic `0x564753AB 564753AB` ("VGS\xAB" ×2) + 16B inject_id | DPI 可识别 |
| §4.4 multi-tuple | `extra_tuples` 每条携带 inject_id 派生子标识 | 多包关联 |

### 3.2.5 强归因路径

1. NTA 告警上报 SOC → 含 5-tuple
2. Veriguard 查 ES：`source.ip IN bas_pool AND source.port IN [49152-49200] AND original_event.payload_hex LIKE '564753ab%'`
3. 命中即强归因，inject_id 从 payload offset 8-23 取出

### 3.2.6 当前已实现 / 待补

| 项 | 状态 |
| --- | --- |
| pcap_replay injector 框架 `injectors/pcap_replay/` | ✅ 已实现 |
| NetworkTraffic payload + multi-tuple wire（V24 + PR #70） | ✅ 已实现 |
| 灭活 pcap 库 | ❌ 需工具链 + 灭活后再签名 |
| implant NetworkTraffic 真消费 | ❌ `main.rs` 仍 `// Not implemented` |
| BAS 专用 IP/MAC/端口段约定 + agent 绑定 | ❌ 未约定 |

---

## 3.3 场景 C —— 主机安全防护验证

### 3.3.1 验证对象与攻击通道

- **验证对象**：HIDS / EDR / 终端威胁检测 / 主机防病毒
- **攻击通道**：本地 implant 执行命令 / 进程注入 / 文件落地 / DNS 解析 / 内存马 / 服务持久化

### 3.3.2 部署形态

```
靶机（部署 EDR / HIDS）：
┌─────────────────────────────────────────┐
│  veriguard-agent (常驻, 空壳 30KB)       │ ← 平时只有 transport+crypto
│       │                                  │
│       │ 收到任务后 ↓                     │
│       ▼                                  │
│  动态下载 implant module (签名验证)      │ ← S2 热加载
│       │                                  │
│       │ spawn ↓                          │
│       ▼                                  │
│  veriguard-implant (执行器, 完成即退)   │ ← 模拟攻击行为
│  ├─ exec 命令带 --vg-run=<id>           │ ← L1 标记
│  ├─ 落文件 sha256=hash(runId+injectId)  │ ← L2 工件
│  ├─ DNS 查 <inject-id>.probe.vg.local   │ ← L1+L2 标记
│  └─ pid 链上报 Director                  │ ← L2 工件
└─────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────┐
│  HIDS / EDR (SUT)                        │ ← 验证目标
│  → 上报告警含 process_name / sha256 /    │
│     cmdline / dns_query                  │
└─────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────┐
│  SOC / SIEM                              │
│  Veriguard 查询: cmdline LIKE '%--vg-run=<id>%'│
│              OR sha256 IN <预测集合>     │
│              OR dns_query LIKE '%.probe.vg.local%' │
└─────────────────────────────────────────┘
```

### 3.3.3 S2 热加载实现

```rust
// agent (常驻)
fn main_loop() {
    loop {
        let task = pull_task(); // 30s 轮询
        let module = download_module(&task.module_id, &task.signature)?;
        verify_signature(&module, &PLATFORM_PUBKEY)?;
        let result = execute_module(module, &task.payload);
        unload_module(); // 立即卸载
        report_result(result);
    }
}
```

### 3.3.4 L1 + L2 标记规范

| 行为类型 | L1 标记（流量内可识别） | L2 工件（artifact 指纹） |
| --- | --- | --- |
| 命令执行 | cmdline 末尾 `--vg-run=<runId> --vg-inject=<injectId>` | spawn pid 链上报 Director |
| 进程注入 | 注入的代码段开头 `0x564753AB` magic | parent pid + child pid + cmdline hash |
| 文件落地 | 文件内容前 16B `VG-PROBE\0` + inject_id | sha256 由 `H(runId‖injectId‖template)` 派生（确定性，可预测） |
| DNS 解析 | 查询名 `<inject-id>.probe.vg.<tenant>.local` | 查询时间戳 |
| 内存马 | URL 路径 `/vg-probe/<inject-id>` | 进程映像 hash |
| 服务持久化 | 服务名 `VeriguardProbe_<short-inject-id>` | service description 含 inject_id |

### 3.3.5 灭活规范

| 高危行为 | 灭活策略 |
| --- | --- |
| **勒索软件** | 执行到"枚举文件 + 申请 SeBackupPrivilege + 调用 CryptAcquireContext" 即停。**绝不调用 EncryptFile**。EDR 行为规则该命中的全命中 |
| **凭据窃取** | 跑到"打开 lsass.exe handle (PROCESS_VM_READ)" 即停。**绝不 ReadProcessMemory 真读 SAM** |
| **横向移动** | 跑到 "WinRM/SMB 协商完成" 即关闭连接。**绝不真投递 payload 到远端** |
| **进程注入** | 用 `VirtualAllocEx + WriteProcessMemory` 写入"灭活 shellcode"（只是 `MOV EAX, 0xDEADBEEF; RET`），不 `CreateRemoteThread` |

### 3.3.6 强归因决策（HIDS 告警 → Veriguard）

1. 优先匹配 `cmdline contains "--vg-run="` → 提取 `runId/injectId` → STRONG
2. 次匹配 `sha256` IN 预测集合 → STRONG（确定性派生让 Veriguard 提前算出可能的 sha256 集合）
3. 再匹配 `dns_query like "%.probe.vg.local"` → STRONG
4. 兜底时空 + ATT&CK technique 同 inject 节点 → MEDIUM

### 3.3.7 当前已实现 / 待补

| 项 | 状态 |
| --- | --- |
| implant 4 类 payload dispatcher（Command / Executable / FileDrop / DnsResolution） | ✅ 已实现 |
| 命令 / 文件 / DNS 自动注入 L1 标记 | ❌ 未实现 |
| implant artifact bundle 上报（spawn pid / sha256 / 查询时间戳） | ❌ 未实现 |
| 灭活模板库（高风险 payload 必须走灭活，非灭活直接拒绝执行） | ❌ 未实现 |
| S2 热加载（空壳 agent + module 模式） | ❌ 当前 agent 是单体 binary |

---

# 四、归因决策表（三场景共用）

| 命中条件 | 归因等级 | confidence | trace 标记 | UI 颜色 |
| --- | --- | --- | --- | --- |
| L1 标记命中（header / SNI / DNS / magic / cmdline） | **STRONG** | 1.00 | `attribution=STRONG, evidence=L1` | 绿 |
| L2 工件命中（sha256 / pid / 5-tuple in 预测集） | **STRONG** | 0.95 | `attribution=STRONG, evidence=L2` | 绿 |
| L1+L2 失效 + L3 时空 + ATT&CK technique 命中 | **MEDIUM** | 0.60 | `attribution=MEDIUM, evidence=L3+TTP` | 黄 |
| L1+L2 失效 + L3 时空 + 同 source IP 段 | **MEDIUM** | 0.50 | `attribution=MEDIUM, evidence=L3+IP` | 黄 |
| 仅 L3 时空 + 目标 IP 匹配 | **WEAK** | 0.30 | `attribution=WEAK, evidence=L3` | 橙 |
| 无任何命中 | **UNRELATED** | 0.00 | 不入 trace | - |

**实现位**：

- 数据模型：在 `DetectionMatch` / `NodeExpectationTrace` 加 `attribution_level` (enum) + `attribution_confidence` (BigDecimal) + `attribution_evidence` (jsonb 列表)
- 查询位：`ElasticSocConnector.queryNodeAlert` 改造为 4 步级联查询，从强到弱依次尝试

---

# 五、SIEM 协作机制（高阶可选）

```
[演练开始]
Veriguard Platform → SOC API:
  POST /api/sessions
  { "session_id": "<runId>",
    "started_at": "...",
    "expected_window_end": "...",
    "expected_source_ips": ["10.255.0.0/24"],
    "expected_techniques": ["T1059.001", "T1055", ...],
    "tenant": "gfc-fugu-2026" }

[演练中] SOC 自动给该时段从 bas_pool 源段进来的所有 alert 打标签:
  _source.bas.session_id = <runId>
  _source.bas.is_drill = true

[演练结束]
Veriguard Platform → SOC API:
  POST /api/sessions/<runId>/close
  { "ended_at": "...", "actual_inject_count": N }

[归因查询] 改用 SOC 服务端做的归因:
  GET /api/alerts?bas.session_id=<runId>&inject_id=<id>
```

**收益**：归因从 BAS 客户端做（受字段映射准确度限制）变为 SIEM 服务端做（甲方运营人员可信任）。

**前提**：需要甲方 SIEM 支持自定义索引字段（Elastic 配 ECS 自定义字段；Splunk 配 lookup table）。

---

# 六、专用靶机部署约定（仿真接收端）

| 场景 | 仿真接收端 | 配置 |
| --- | --- | --- |
| 边界 Web 攻击 | nginx + `return 200 'VG-RECEIVED inject=<id>'` | DMZ 段，独立子网，无业务数据库 |
| 邮件钓鱼 | postfix + 接收即丢 + 解析 inject_id 回执 Director | DMZ 段，专用 MX 域 |
| pcap 回放 | netcat listener / scapy responder | 流量验证段，绑定 BAS 专用 IP 段 |
| 主机攻击 | 业务隔离的 EDR 监控机 + 还原快照 | 终端验证段，**绝不**装真实业务软件 |
| 高风险样本 | CAPEv2 沙箱 (`192.168.1.6`) | 完全网络隔离 + 快照回滚 |

### 6.1 硬性约束

- inject 目标 IP 必须在 `veriguard.target.allowed_cidr` 白名单内
- 任何打到白名单外的 inject **直接拒绝执行**（agent 端 pre-flight check）
- 白名单由甲方提供初始清单，Veriguard 维护变更日志

---

# 七、差距矩阵与落地路线

## 7.1 Veriguard 当前差距矩阵

| 技术 | 业界成熟做法 | Veriguard 当前 | 差距 | 优先级 |
| --- | --- | --- | --- | --- |
| S1 流量标记 | 全通道自动注入 per-inject UUID | Web header 字段在但需手填，其他通道无 | 🔴 高 | P0 |
| S1 灭活 payload | 内置灭活模板库 | 无；payload 由用例作者自负责 | 🔴 高 | P0 |
| S2 热加载 | agent 空壳 + module 下载 | implant 是完整 binary | 🟡 中 | P2 |
| S3 仿真接收端 | 强约束，IP 白名单 | 无约束，能打任意 URL | 🔴 高 | P0 |
| S4 沙箱 | CAPEv2 + 快照回滚 | ✅ CAPEv2 接入（M2 完成） | 🟢 - | - |
| L1 强归因（查询位） | SIEM 查询带 `x-veriguard-run-id` | 查询只有时空 + 目标 IP | 🔴 高 | P0 |
| L2 工件归因 | sha256 / pid / 5-tuple 预测集 | implant 不上报 artifact bundle | 🟡 中 | P1 |
| L3 + confidence | 多层归因带分级 | 单层无 confidence | 🟡 中 | P1 |
| SIEM session 协作 | 大客户标配 | 未设计 | 🟡 中 | P2 |
| ATT&CK technique 兜底 | 标准能力 | `nodeContractTags` 已建模但 connector 没用 | 🟢 低 | P1 |
| 白名单防护 | 强约束 | 无 pre-flight check | 🔴 高 | P0 |

## 7.2 分期落地路线图

### Phase 1（2 周）—— P0 必交付（招标硬性归因要求）

**目标**：把"时空近似"升级为"L1 标记 + L3 兜底"，最小可用强归因。

- **平台侧**：
  - `WebAttackExecutor` / `EmailInjectService` / `PcapReplayDispatchService` / `CommandInjectDispatchService` 统一注入 `X-Veriguard-Run-Id` / `X-Veriguard-Inject-Id`
  - `ElasticSocConnector.queryNodeAlert` 改为 3 步级联：先查标记 → 失败查 ATT&CK → 失败查时空
  - `DetectionMatch` 加 `attribution_level` + `attribution_confidence` 字段（V26 迁移）
  - 加 `veriguard.target.allowed_cidr` 配置 + agent pre-flight check
- **agent 侧**：
  - implant 命令执行自动追加 `--vg-run=<id>` 末尾参数
  - DnsResolution 查询名自动改写成 `<inject-id>.probe.vg.<tenant>.local`
- **UI 侧**：
  - 归因 confidence 分色显示
  - inject 目标 IP 不在白名单时表单红框 + 提交按钮禁用

### Phase 2（4 周）—— P1 完善

- **灭活模板库**：建 `payload_template` 表，所有高风险 payload 必须有 `defang_template` 才能入库
- **artifact bundle 上报**：implant 执行完上报 spawn pid + 落地 sha256 + DNS 查询时间戳
- **sha256 预测集**：`FileDrop` payload 注册时算 `H(runId‖injectId‖template)`，写入 ES 待查表
- **ATT&CK technique 兜底**：`queryNodeAlert` 在 L3 上加一层 `signal.rule.threat.technique.id IN nodeContractTags` 查询，命中算 MEDIUM

### Phase 3（8 周）—— P2 优化

- **S2 热加载**：implant 拆分为空壳 agent + 动态模块
- **SIEM session API**：与甲方 SIEM 团队约定 `bas_session_id` 索引字段
- **演练时段广播**：UI 提供"演练模式"开关，开启时所有 inject 强制带标记，结束后自动生成归因报告

### Phase 4（持续）—— 运营

- 灭活模板库持续扩充（每个新 CVE 都要灭活变体）
- 归因准确率统计（false positive / false negative）周报
- 业界 BAS 案例（AttackIQ / SafeBreach 新 TTP）持续吸收

---

# 八、风险与边界条件

| 风险 | 后果 | 缓解 |
| --- | --- | --- |
| 灭活 payload 被攻击者反编译模仿 | 真攻击伪装成 Veriguard 演练，绕过 SIEM 告警 | 标记字段加 HMAC 签名（用 Ed25519 keypair 跨语言已有），SIEM 侧验签后才打 drill 标 |
| 强归因依赖 SIEM 字段映射准确 | 字段没传上去 → 归因失败 → 退到时空近似 | 演练前 connector health check 必查"我刚发的 inject 能不能查到自己的标记"，校验过才允许批量演练 |
| 沙箱沦陷 / 灭活失败 | 真有害行为扩散 | 沙箱网络物理隔离 + 网关白名单出口；implant 高风险 payload 加 `requires_sandbox=true` 标志，非沙箱环境直接拒绝 |
| BAS 专用 IP 段被运维误用做业务 | SIEM 自动给业务流量打 drill 标，造成漏报 | IP 段 DNS 注册 `veriguard-do-not-use.local`，定期扫描业务流量 |
| 演练并发 inject 太多压垮专用靶机 | 靶机不响应导致 inject 误判失败 | platform 限流（按 tenant + 接收端各自配额）+ 仿真接收端水平扩展 |
| 标记字段被甲方 SIEM 字段名 mapping 截断 | 强归因失效 | connector 实现按 SIEM 类型预设字段映射表（Elastic / Splunk / QRadar 各一份） |

---

# 九、最小可行 MVP（一周内可演示）

```
□ 1. ElasticSocConnector.queryNodeAlert 改：
     先查 must.term: original_event.http.request.headers.x-veriguard-run-id = runId
     失败回退现有 query
□ 2. WebAttackExecutor.execute() 改：
     headers.put("X-Veriguard-Run-Id", runId)
     headers.put("X-Veriguard-Inject-Id", injectId)
□ 3. NodeExpectationTrace 加 attribution_level / confidence 字段 (V26)
□ 4. UI AttackChainRun 详情页 alert 列表加 confidence 列 + 分色
□ 5. 写 e2e: 跑一次 web inject → 用 mock ES 返回带标记的 alert → 检查 trace=STRONG/1.0
```

**这 5 步合到一个 PR**：单文件改动可控，不动数据库结构以外的核心；演示效果 = 同样一次 SOC 查询，强归因路径多出"100% confidence"标签。这就足以向甲方证明"我们的归因不是猜的"。

---

# 十、参考资料

## 中文 BAS（无损派）

- CN-SEC 中文网：[入侵与攻击模拟（BAS）技术简介](https://cn-sec.com/archives/4396307.html)
- CN-SEC 中文网：[入侵与攻击模拟（BAS）：网络安全防御能力验证新利器](http://cn-sec.com/archives/4823004.html)
- 腾讯云开发者社区：[深度解析 BAS 模拟入侵攻击系统：工作原理与前置防御价值](https://cloud.tencent.com/developer/article/2607064)
- FreeBuf：[入侵与模拟攻击（BAS）—新兴的安全防护有效性验证评估技术](https://m.freebuf.com/articles/es/343865.html)
- 远瞻慧库：[攻击模拟 (BAS) 如何重塑 2025 年网络安全运营？腾讯安全专家深度解析](https://www.baogaobox.com/insights/250505000009874.html)
- 腾讯 TBAS、墨云科技 VackBAS

## 国际 BAS（强归因派）

- AttackIQ Security Optimization Platform
- SafeBreach Continuous Security Validation
- Cymulate Extended Security Posture Management
- Mandiant Security Validation（前 Verodin）
- Picus Security Continuous Validation
- MITRE CALDERA（开源参考实现）
- Red Canary Atomic Red Team（开源测试用例库）

## 标准框架

- MITRE ATT&CK v15
- Gartner 2022 安全运营技术成熟度曲线
- 等保 2.0 三级 / 四级要求

---

*— 完 —*
