# Veriguard 沙箱二开设计稿

- 文档日期：2026-04-26
- 范围：在 Veriguard 二开框架内完善"沙箱"模块，对接开源沙箱产品 CAPEv2，覆盖 PRD §2.5 全部要点
- 关联 PRD：`docs/prd/产品要求.md`
- 关联落地说明：`docs/参考资料/Veriguard二开落地说明.md`

## 1. 目标与范围

### 1.1 目标

PRD §2.5 沙箱管理要求：

| 要求内容 | 验收 |
| --- | --- |
| 支持沙箱平台 CRUD | 提供功能截图 |
| 支持配置网络访问控制策略 | 提供功能截图 |
| 用例执行完成后自动还原沙箱环境 | 提供功能截图 |
| 支持使用沙箱执行真实恶意样本（8 类样本） | — |

本期通过对接 CAPEv2 主控满足上述要求。`docs/参考资料/Veriguard二开落地说明.md` 已经定下"虚拟化 / 容器沙箱驱动"作为外部集成边界，本设计在该边界内完成驱动适配与控制台完善。

### 1.2 本期范围

- 控制台 UX 完善：多规则编辑、CIDR / 端口校验、列表筛选、删除二次确认、导出 iptables / routing.conf 脚本。
- 引入 `SandboxDriver` 抽象 + `CapeV2SandboxDriver` 唯一实现。
- 沙箱平台健康检查与分析机同步（只读视图）。
- 沙箱样本提交（multipart）与任务状态轮询。
- 凭据全部走配置文件，不进 DB / 不进 UI。
- 中文 UI 措辞统一为"沙箱"，不出现 "CAPEv2"。

### 1.3 显式非目标

- ❌ 网络规则运行时下发（IaC 或 host-side agent 落 iptables）；本期仅持久化 + 导出脚本，运维手工应用。
- ❌ 多 CAPEv2 主控（cluster_key map）；本期假定单主控。
- ❌ CAPEv2 报告 / 截图 / pcap 镜像或嵌入；本期不展示任何报告链接。
- ❌ 后台定时任务（同步 / 轮询）；同步与轮询都由用户在 UI 显式触发或前端轮询。
- ❌ 凭据 DB 持久化与加密层；凭据只能从环境变量读到。
- ❌ 自动重试任何失败请求（401、502、超时一律返回错误）。

## 2. 关键决定（含历史选项）

| 决定 | 选项 | 理由 |
| --- | --- | --- |
| 沙箱产品 | CAPEv2 | 用户指定；GitHub `kevoreilly/CAPEv2`，Cuckoo 演进版，社区与文档活跃 |
| 部署形态 | 单 CAPEv2 主控 | PRD 未提及多安全域 / 多集群；KISS；将来可扩多集群 |
| 凭据形式 | API Token + 环境变量 | DRF authtoken 永不过期；token 直配比用户名密码省一跳认证；凭据脱离 UI / DB |
| Provider 字段 | UI 不暴露，后端固定 KVM，迁移时删列 | CAPEv2 推荐 Linux host + KVM machinery；KVM 覆盖 Windows / Linux / macOS / Android guest |
| 网络规则下发 | 元数据 + 导出脚本 | CAPEv2 无运行时 API；运行时下发在后续期 |
| 自动还原 | 表单强制 + 分析机 snapshot 可视化 | CAPEv2 自动还原由 machinery + hypervisor 完成；控制台只观测与强制 |
| 沙箱记录语义 | "用例预设"（一组规则 + 样本 + 网络策略） | 单主控形态下不再代表 CAPEv2 实例；运维按业务划分预设 |
| 网络规则数量 | 可为空 | PRD 仅要求"支持配置"，不要求每个预设必填 |

## 3. 架构总览

```
┌────────────────────────────────────────────────────────────────────┐
│ veriguard-front (React)                                            │
│   admin/components/veriguard/                                      │
│     VeriguardConsole.tsx        — 沙箱 Tab 拆出，渲染下面两个      │
│     sandbox/                                                       │
│       CapePlatformPanel.tsx     — M2 全局平台状态面板              │
│       MachineTable.tsx          — M2 分析机只读子表                │
│       SandboxList.tsx           — M1 列表 + 筛选 + 行操作菜单      │
│       SandboxDialog.tsx         — M1 新建 / 编辑表单               │
│       NetworkRuleEditor.tsx     — M1 多规则增删改                  │
│       NetworkRuleExportButtons  — M1 导出 iptables / routing.conf  │
│       DeleteConfirmDialog       — M1 通用二次确认                  │
│       SubmitSampleDialog.tsx    — M3 提交样本                      │
│       SubmissionHistoryDrawer   — M3 提交历史                      │
│       SubmissionStatusBadge     — M3 状态 chip                     │
│       hooks/{useSandboxes, usePlatformStatus, useSubmissionPolling}│
│   actions/veriguard/veriguard-actions.ts — 同步扩展                │
└────────────────────────────────────────────────────────────────────┘
                                │ REST + multipart
                                ▼
┌────────────────────────────────────────────────────────────────────┐
│ veriguard-api (Spring Boot)                                        │
│   rest/security_validation/                                        │
│     SecurityValidationApi.java   — 现有，矩阵 / 目录 / 编排只读    │
│     SandboxApi.java              — 新增，沙箱端点全部归此          │
│     SandboxService.java          — 沙箱 CRUD + 校验                │
│     SandboxScriptExporter.java   — M1 iptables / routing.conf 导出 │
│     SandboxMachineService.java   — M2 分析机同步与查询             │
│     SandboxSubmissionService.java — M3 提交与状态                  │
│   integration/sandbox/                                             │
│     SandboxDriver.java           — interface                       │
│     SandboxDriverRegistry.java   — 单驱动工厂                      │
│     NotImplementedSandboxDriver  — M1 占位                         │
│     SandboxIntegrationException  — 统一异常                        │
│     capev2/                                                        │
│       CapeV2ConnectionProperties — @ConfigurationProperties        │
│       CapeV2HttpClient           — RestClient 包装                 │
│       CapeV2SandboxDriver        — M2 / M3 实装                    │
│       CapeV2ResponseMapper       — JSON → DTO                      │
│   migration/                                                       │
│     V4_73__Extend_veriguard_sandbox.java       (M1)                │
│     V4_75__Add_veriguard_cape_machines.java    (M2)                │
│     V4_76__Add_veriguard_sandbox_submissions.java (M3)             │
└────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────┐
│ External（不在本仓库范围）                                         │
│   CAPEv2 主控（REST：/apiv2/api-token-auth、/tasks/*、/machines/*）│
│   CAPEv2 主机管理员（手工应用 iptables / routing.conf 脚本）        │
│   Hypervisor（KVM machinery 自动 snapshot revert）                  │
└────────────────────────────────────────────────────────────────────┘
```

关键决定：

- **包命名 `integration/sandbox/`** 与现有 `io.veriguard.integration` 同级，明确"外部集成边界"。
- **`SecurityValidationService` 拆分**：原 248 行的 service 同时承载 PRD 矩阵 / 攻击目录 / 编排 / 沙箱 CRUD 四件事。本期把沙箱业务全部迁出，原 service 仅保留只读元数据查询。
- **`SandboxDriver` 单驱动设计**：本期所有沙箱预设统一走 `CapeV2SandboxDriver`，`SandboxDriverRegistry` 退化为常量返回；保留接口为未来兼容其他沙箱产品。
- **凭据全部走配置**：`CapeV2ConnectionProperties` 启动时校验环境变量必填，缺失即应用启动失败（`@PostConstruct` 抛 `IllegalStateException`），无加密层、无缓存表。
- **UI 措辞**：用户面向字符串只出现"沙箱"，不出现 "CAPEv2"。后端日志、内部异常、源代码注释保留 CAPEv2 命名。

## 4. 数据模型

### 4.1 现存表（V4_72，不动）

`veriguard_sandboxes` 已经存在；本期通过迁移收紧字段。

### 4.2 M1 — `V4_73__Extend_veriguard_sandbox.java`

```sql
-- 删除不再使用的列
ALTER TABLE veriguard_sandboxes
  DROP COLUMN veriguard_sandbox_endpoint,
  DROP COLUMN veriguard_sandbox_provider_type;

-- 名称唯一约束
ALTER TABLE veriguard_sandboxes
  ADD CONSTRAINT uk_veriguard_sandboxes_name
  UNIQUE (veriguard_sandbox_name);
```

迁移幂等：在演示库为空、生产库为空的前提下安全；如果存在记录，删除列会一并删除值，需事先备份。文档段落明示。

### 4.3 M2 — `V4_75__Add_veriguard_cape_machines.java`

```sql
CREATE TABLE veriguard_cape_machines (
  veriguard_cape_machine_name           varchar(255) PRIMARY KEY,
  veriguard_cape_machine_label          varchar(255),
  veriguard_cape_machine_platform       varchar(64),
  veriguard_cape_machine_snapshot       varchar(255),
  veriguard_cape_machine_status         varchar(32)  NOT NULL,
  veriguard_cape_machine_last_synced_at timestamptz  NOT NULL
);

CREATE INDEX idx_veriguard_cape_machines_missing_snapshot
  ON veriguard_cape_machines (veriguard_cape_machine_name)
  WHERE veriguard_cape_machine_snapshot IS NULL;
```

- 单主控形态下不带 `sandbox_id` 外键，分析机是平台级资源。
- 同步策略：事务内 `DELETE *; INSERT ALL;` —— CAPEv2 是真值源。
- 部分索引专门加速"未配快照"告警查询。

> **注**：原 §2 设计中的 `V4_74` token 缓存表因凭据改走配置而取消。

### 4.4 M3 — `V4_76__Add_veriguard_sandbox_submissions.java`

```sql
CREATE TABLE veriguard_sandbox_submissions (
  veriguard_sandbox_submission_id                  varchar(255) PRIMARY KEY,
  veriguard_sandbox_submission_sandbox_id          varchar(255) NOT NULL
    REFERENCES veriguard_sandboxes(veriguard_sandbox_id) ON DELETE CASCADE,
  veriguard_sandbox_submission_sample_type         varchar(64)  NOT NULL,
  veriguard_sandbox_submission_cape_task_id        bigint       NOT NULL,
  veriguard_sandbox_submission_status              varchar(32)  NOT NULL,
  veriguard_sandbox_submission_original_filename   varchar(512) NOT NULL,
  veriguard_sandbox_submission_sample_sha256       varchar(64)  NOT NULL,
  veriguard_sandbox_submission_target_machine_name varchar(255),
  veriguard_sandbox_submission_submitted_by        varchar(255) NOT NULL,
  veriguard_sandbox_submission_submitted_at        timestamptz  NOT NULL DEFAULT now(),
  veriguard_sandbox_submission_last_status_at      timestamptz  NOT NULL DEFAULT now(),
  veriguard_sandbox_submission_error_message       text
);

CREATE INDEX idx_veriguard_sandbox_submissions_history
  ON veriguard_sandbox_submissions
     (veriguard_sandbox_submission_sandbox_id,
      veriguard_sandbox_submission_submitted_at DESC);

CREATE INDEX idx_veriguard_sandbox_submissions_active
  ON veriguard_sandbox_submissions (veriguard_sandbox_submission_status)
  WHERE veriguard_sandbox_submission_status IN ('QUEUED', 'RUNNING');
```

- `status` 枚举：`QUEUED / RUNNING / COMPLETED / FAILED / UNKNOWN`。`UNKNOWN` 专门给"CAPEv2 返回我们未枚举的状态字符串"；不静默吞掉，记 `error_message='unmapped: <raw>'`，UI 黄 chip。
- `sample_type` 复用 `VeriguardSandbox.SampleType` 枚举。
- `sample_sha256` 用于"同样本重复提交"的友好提示，不强制阻止。
- 历史索引服务 UI 详情；活跃部分索引为后续后台对账作业（本期不实现）预留。

### 4.5 不做的数据模型变更

- ❌ 不为网络规则建关系表；继续用 `veriguard_sandboxes.veriguard_sandbox_network_rules` jsonb 列，只是 UI 改造。
- ❌ 不为 token 建缓存表（凭据走配置）。
- ❌ 不在 `submissions` 加 `report_url` / `report_blob`（不镜像报告）。
- ❌ 不为多主控建 cluster 表。

## 5. 后端组件

### 5.1 驱动层（`io.veriguard.integration.sandbox`）

```java
public interface SandboxDriver {
    void healthCheck();
    List<MachineSnapshot> listMachines();
    SubmissionResult submitSample(SampleSubmissionRequest request);
    TaskStatus fetchTaskStatus(long capeTaskId);
}
```

- DTO（`MachineSnapshot` / `SubmissionResult` / `TaskStatus`）均为 immutable record。
- `SandboxIntegrationException extends RuntimeException`：所有驱动方法统一抛出，`reasonCode` 取 §8.2 表中的 502/504 一行。
- `SandboxDriverRegistry`：本期返回常量 `CapeV2SandboxDriver`；接口预留以备未来。

### 5.2 CAPEv2 驱动要点

```
io.veriguard.integration.sandbox.capev2
  CapeV2ConnectionProperties      @ConfigurationProperties("veriguard.sandbox.cape")
  CapeV2HttpClient                Spring RestClient 薄封装
  CapeV2SandboxDriver             @Component
  CapeV2ResponseMapper            JSON → MachineSnapshot / TaskStatus
```

- HTTP 客户端：Spring `RestClient`（Spring Boot 3.3.7 自带）；连接超时 5s、读超时 30s、提交样本读超时 60s（按配置覆盖）。
- 认证：固定 `Authorization: Token ${api-token}`。401 → 直接 `SandboxIntegrationException("authentication_failed")`，不重试。
- 同步分析机：`GET /machines/list` → `CapeV2ResponseMapper` → `SandboxMachineService` 在事务内 `DELETE WHERE 1=1; INSERT ALL;` 并写 `last_synced_at`。
- 健康检查：`GET /cuckoo/status`（CAPEv2 最廉价 ping 端点）。
- 提交样本：`POST /tasks/create/file` multipart：`file`、可选 `machine`、可选 `timeout`、固定 `tags=veriguard,sample_type=<…>`。返回 `{task_id}` → `SandboxSubmissionService` 落 `veriguard_sandbox_submissions`。
- 状态映射：

| CAPEv2 字符串 | 我方枚举 |
| --- | --- |
| `pending`、`queued` | `QUEUED` |
| `running`、`processing`、`analysis` | `RUNNING` |
| `reported`、`completed` | `COMPLETED` |
| `failed_*`（前缀） | `FAILED` |
| 其他 | `UNKNOWN`（伴随 `WARN` 日志，`error_message` 记原始字符串） |

- 配置启动校验：`@PostConstruct` 中验证 `endpoint` / `api-token` 必填，否则 `IllegalStateException`。

### 5.3 服务层拆分

| Service | 职责 | 状态 |
| --- | --- | --- |
| `SecurityValidationService` | PRD 矩阵 + 攻击目录 + 编排 schema（只读） | 现存，沙箱方法迁出 |
| `SandboxService` | 沙箱预设 CRUD + 校验 | M1 新增 |
| `SandboxScriptExporter` | iptables / routing.conf 脚本 | M1 新增 |
| `SandboxMachineService` | `/machines/list` 同步与查询 | M2 新增 |
| `SandboxSubmissionService` | 提交、状态查询、历史 | M3 新增 |

每文件 < 800 行；`SecurityValidationService` 拆分后 ~150 行。

### 5.4 REST API 清单

控制器拆 `SandboxApi`，**对外 URI 路径完全不变**，前端 actions 字符串常量保留。

| Milestone | Method | URI | 说明 |
| --- | --- | --- | --- |
| 现存 | `GET / POST / GET{id} / PUT{id} / DELETE{id}` | `/api/sandboxes[/...]` | CRUD；M1 仅扩展 DTO |
| **M1** | `GET` | `/api/sandboxes/{id}/network-rules/exports/iptables` | text/plain，iptables 脚本 |
| **M1** | `GET` | `/api/sandboxes/{id}/network-rules/exports/routing-conf` | text/plain，routing.conf 片段 |
| **M2** | `POST` | `/api/sandbox-platform/health-check` | 触发连通性检查 |
| **M2** | `POST` | `/api/sandbox-platform/machines/sync` | 同步分析机 |
| **M2** | `GET` | `/api/sandbox-platform/machines` | 读取已同步分析机视图 |
| **M3** | `POST` | `/api/sandboxes/{id}/submissions` | multipart，提交样本 |
| **M3** | `GET` | `/api/sandboxes/{id}/submissions` | 历史，分页 + 按 status 筛选 |
| **M3** | `GET` | `/api/sandboxes/{id}/submissions/{submissionId}` | 单条，前端轮询 |

RBAC：读 `Action.READ`、写/同步/导出 `Action.WRITE`、删除 `Action.DELETE`，全部 `ResourceType.PLATFORM_SETTING`。

### 5.5 DTO 调整

- `SandboxInput` 删除 `sandbox_endpoint`、`sandbox_provider_type`、`sandbox_username`、`sandbox_password`。
- `SandboxInput.network_rules` 标注：允许空列表（不允许 `null`）。
- `SandboxOutput` 同步删除上述字段。
- 新增 `MachineOutput`（M2）：`name / label / platform / snapshot / status / last_synced_at`。
- 新增 `SubmissionOutput`（M3）：`submission_id / sample_type / cape_task_id / status / status_label / target_machine_name / original_filename / sample_sha256 / submitted_by / submitted_at / last_status_at / error_message`。

**Bean 校验注解策略**：

- 保留 `@NotBlank` / `@NotNull` / `@Valid` 做 null 安全与基本格式校验。
- **去除** `@AssertTrue`（`autoRestoreEnabled`）、`@NotEmpty`（`supportedSampleTypes`、`networkRules`）。
- 业务规则全部集中在 `SandboxService.validate`（M1 起），抛 `InputValidationException` 携带 §8.2 表的 `reason_code`，避免 Spring 默认 bean validation 错误体与我方 `error_code/reason_code` 契约不一致。

### 5.6 配置项契约

```yaml
veriguard:
  sandbox:
    cape:
      endpoint: ${VERIGUARD_SANDBOX_CAPE_ENDPOINT}
      api-token: ${VERIGUARD_SANDBOX_CAPE_API_TOKEN}
      connect-timeout: 5s
      read-timeout: 30s
      sample-submit-timeout: 60s
spring:
  servlet:
    multipart:
      max-file-size: 64MB
      max-request-size: 64MB
```

- `endpoint` / `api-token` 缺失或为空 → 应用启动失败（M2 启用此校验；M1 不强校验，避免 M1 部署即依赖 CAPEv2）。
- token 在 CAPEv2 主机用 `python manage.py drf_create_token <username>` 一次性生成；轮换时同样命令 `--r` 重置后改 env 滚动重启 Veriguard。

### 5.7 事务边界

- `submitSample` 同步语义：HTTP 调用 → CAPEv2 返回 task_id → 落 submission 行 → 返回。极少数情况 CAPEv2 接受 task 但落库失败，记 `WARN sandbox.submission.orphan_cape_task=<id>`，运维人工到 CAPEv2 删 task；不做自动补偿。
- `machines/sync` 整体一个事务：调 CAPEv2 → 反序列化 → DELETE+INSERT；任一步失败回滚，UI 维持上一次同步快照。

## 6. 前端组件

### 6.1 页面布局（沙箱 Tab，M3 完工形态）

```
┌─ 沙箱平台 ─────────────────────────────────────────────┐
│ ● 已连接   上次同步 2 分钟前                            │
│ 分析机 5 台 │ ⚠ 1 台未配快照                            │
│ [测试连接]  [同步分析机]                                │
└────────────────────────────────────────────────────────┘
┌─ 沙箱预设 ─────────────────────────────────────────────┐
│ [+ 新建预设]  状态 ▼  样本类型 ▼              页 1 / N │
│ ┌────────┬──────┬────────┬────────┬───────┬────────┐ │
│ │ 名称   │ 规则 │ 样本数 │ 自动还原 │ 状态  │ 操作   │ │
│ │ 勒索    │ 3    │ 4      │ ✓       │ 启用  │ ⋮      │ │
│ │ 挖矿    │ 0    │ 2      │ ✓       │ 启用  │ ⋮      │ │
│ └────────┴──────┴────────┴────────┴───────┴────────┘ │
└────────────────────────────────────────────────────────┘
```

行操作菜单 `⋮`：编辑 / 删除 / 导出 iptables / 导出 routing.conf / 提交样本 / 查看提交历史。

### 6.2 多规则编辑器（M1）

- props：`value: SandboxNetworkRule[]`、`onChange`、`disabled`。
- 一行一条规则：方向（select）/ 动作（select）/ 协议（select：TCP / UDP / ICMP / ALL）/ CIDR（text）/ 端口（text）/ 删除按钮。
- 末尾"+ 添加规则"按钮。**允许 0 条**：列表为空时空态文案"尚未配置规则。沙箱平台主机将沿用默认网络策略。"。
- 协议为 ICMP 时端口字段 disabled 且强制写 `none`。
- 字段校验：
  - `cidr`：IPv4 / IPv6 / `0.0.0.0/0`，简单 regex + 段位范围；不引网络库。
  - `ports`：`all` / 单端口 1–65535 / 范围 `1-65535` / 列表 `80,443,8080-8090`。

### 6.3 平台状态面板（M2）

- `usePlatformStatus` 首次加载调 `GET /api/sandbox-platform/machines`：
  ```ts
  { healthy: boolean, last_synced_at: string | null, machines: MachineOutput[] }
  ```
- "测试连接" → `POST /api/sandbox-platform/health-check`，loading 期 disabled，结果用 Snackbar 显示成功 / 失败 + reason_code。
- "同步分析机" → `POST /api/sandbox-platform/machines/sync`；成功后刷新机器表。
- `healthy` 字段语义：后端用一个进程内 `AtomicReference<HealthSnapshot>`（不持久化）记录最近一次任意 CAPEv2 调用的成功 / 失败状态；`GET /api/sandbox-platform/machines` 在响应里同时返回该值。应用启动后首次调用前 `healthy = null`，前端渲染为"未知"。
- 机器表列：`name / label / platform / snapshot / status / last_synced_at`。`snapshot` 列：有值绿 chip 显示快照名；空值红 chip"未配置"，悬停 tooltip：
  > 未配置自动还原快照，样本执行后该分析机不会回到干净态。请在沙箱平台主机修改 `<machinery>.conf` 并重启服务。
- 顶部告警条：存在 `snapshot == null` 时显示 `⚠ N 台未配快照`，点击滚到机器表。

### 6.4 提交样本对话框（M3）

- 字段：
  - 样本类型 select：选项 = 当前预设的 `supported_sample_types`，label 用 `sampleTypeLabels`。
  - 样本文件 input[type=file]：单文件，前端校验 ≤ 64MB。
  - 目标分析机 select：来自全局机器缓存 + `自动选择` 默认项。
  - 超时（秒）：可选，空 = CAPEv2 默认。
- 提交：multipart `POST /api/sandboxes/{id}/submissions`；成功 → 关闭对话框、Snackbar、自动打开提交历史抽屉。
- 失败：错误体 `reason_code` 映射中文文案（`sandbox/i18n.ts`）。

### 6.5 提交历史抽屉（M3）

- 展示该预设最近 N 条提交（默认 50，分页）。
- 列：时间 / 样本类型 / 文件名 / 目标机 / 状态 chip / cape_task_id / 错误信息。
- 状态 chip 颜色：QUEUED 灰、RUNNING 蓝、COMPLETED 绿、FAILED 红、UNKNOWN 黄（tooltip 显示原始字符串）。
- `useSubmissionPolling`：抽屉打开时收集所有 `QUEUED|RUNNING` 项；每 5s 并行调单条状态接口；进入终态从轮询集合移除；抽屉关闭 / 组件 unmount cleanup。

### 6.6 Action 层扩展（`veriguard-actions.ts`）

```ts
// M1 改动
- 删除 SandboxProviderType 类型 + sandbox_provider_type 字段
- 删除 endpoint / username / password 字段
+ fetchVeriguardSandboxes(params?: { status?; sample_type?; page?; limit? })
+ exportSandboxIptables(id): Promise<string>
+ exportSandboxRoutingConf(id): Promise<string>

// M2 新增
+ fetchCapePlatformMachines(): Promise<{ healthy; last_synced_at; machines }>
+ triggerCapePlatformHealthCheck()
+ triggerCapePlatformMachineSync()

// M3 新增
+ submitSandboxSample(sandboxId, formData): Promise<SubmissionOutput>
+ fetchSandboxSubmissions(sandboxId, params)
+ fetchSandboxSubmission(sandboxId, submissionId)
```

### 6.7 状态管理

- 不引入新依赖（无 react-hook-form / final-form / MSW），沿用现有 `useState + useMemo + 手写校验` 模式（与项目现有 `VeriguardConsole.tsx` 一致），符合 AGENTS.md "不引入新工具或目录约定"。
- 表单状态拆到对应组件本地，避免单文件超 800 行。
- HTTP 调用沿用 `utils/Action.ts` 中现有的 `simpleCall` / `simplePostCall` / `simplePutCall` / `simpleDelCall`；multipart 调用复用项目已有的上传函数（writing-plans 阶段确认具体函数名）。

### 6.8 文案与 i18n

- 项目已有 `auto-translation:all` 与 `i18n-checker`；所有新字符串走 i18n 框架，硬编码中文将被 lint 失败。
- 文案集中在 `sandbox/i18n.ts`，键名前缀 `sandbox.*`。
- "CAPEv2" 字样仅出现在错误码对应文案的一句中（"请联系沙箱平台主机管理员检查 CAPEv2 配置..."）；用户主路径不出现。

## 7. 数据流（端到端）

### 7.1 M1 — 导出网络规则脚本

```
Operator         Frontend            SandboxApi           SandboxService     SandboxScriptExporter
   │ 行操作 → 导出  │                    │                      │                    │
   ├──────────────►│ GET /api/sandboxes/{id}/network-rules/exports/iptables          │
   │                ├───────────────────►│ findById(id)          │                   │
   │                │                    ├─────────────────────►│                    │
   │                │                    │ rules                │                    │
   │                │                    │◄─────────────────────┤                    │
   │                │                    │ render(rules)        │                    │
   │                │                    ├─────────────────────────────────────────►│
   │                │                    │ #!/bin/sh ...        │                    │
   │                │ 200 text/plain     │◄─────────────────────────────────────────┤
   │                │ Content-Disposition: attachment                                │
   │ 浏览器下载      │◄───────────────────┤                                            │
   │ scp 到 CAPEv2 主机手工执行（外部）                                               │
```

边界：脚本生成完毕**控制台不与 CAPEv2 主机通信**；运维拿文件去执行。规则为空时输出仅含头部注释的脚本：`# 沙箱预设「<name>」未配置网络访问控制规则。`，HTTP 仍 200。

### 7.2 M1 — CRUD（精简后）

```
Operator → Frontend：表单校验（name/rules-allow-empty/sample_types/auto_restore=true/status）
Frontend → SandboxApi：POST /api/sandboxes
SandboxApi → SandboxService.validateAndSave
SandboxService → JPA save（providerType 强制 KVM）
返回 SandboxOutput（无 endpoint/cred）
Frontend：列表刷新
```

校验规则：
- `auto_restore_enabled = false` → `InputValidationException("sandbox_auto_restore_required")`
- `supported_sample_types` 空 → `InputValidationException("sandbox_supported_sample_types_empty")`
- `network_rules` 允许空（明确放宽）
- `name` 重复（DB unique 约束触发）→ `409`

### 7.3 M2 — 同步分析机

```
Operator → Frontend：点"同步分析机"
Frontend → SandboxApi：POST /api/sandbox-platform/machines/sync
SandboxApi → SandboxMachineService.sync
SandboxMachineService → CapeV2SandboxDriver.listMachines()
CapeV2SandboxDriver → CAPEv2: GET /machines/list (Authorization: Token …)
CAPEv2 → 200 JSON
CapeV2ResponseMapper → MachineSnapshot[]
SandboxMachineService TX：DELETE FROM veriguard_cape_machines; INSERT ALL；healthy=true（内存）
返回 { healthy:true, last_synced_at, machines:[…] }
Frontend：表刷新；红 chip / 绿 chip 重绘
```

错误路径：连接失败 / 5xx / 超时 → `SandboxIntegrationException` → 502/504 + Snackbar；DB 维持上次快照（事务回滚）；`healthy=false`。401 → `authentication_failed`。

### 7.4 M2 — 健康检查

```
Frontend → POST /api/sandbox-platform/health-check
SandboxApi → CapeV2SandboxDriver.healthCheck()
CapeV2SandboxDriver → CAPEv2: GET /cuckoo/status
返回 { healthy: true, reason: null }（或失败时 { healthy:false, reason_code:… }）
```

### 7.5 M3 — 提交样本

```
Operator：选样本/选文件/提交
Frontend：multipart POST /api/sandboxes/{id}/submissions
SandboxApi → SandboxSubmissionService.submit(req)
  - 校验 preset.status=ACTIVE / sample_type ∈ supported / file 非空 / size ≤ 64MB
  - 计算 SHA-256
  - CapeV2SandboxDriver.submitSample(req)
    - CAPEv2: POST /tasks/create/file (multipart, Authorization: Token)
    - 200 { task_id }
  - INSERT submissions (status=QUEUED)
返回 SubmissionOutput
Frontend：dialog 关闭；history drawer 自动打开；轮询入活跃集合
```

边界声明：
- 样本在 guest VM 内执行 = CAPEv2 内部行为，不在我方代码路径。
- 网络隔离 = CAPEv2 主机已按 M1 导出脚本配好的 iptables / routing.conf 在生效。
- 自动还原 = task 完成后 CAPEv2 machinery + hypervisor 自己 revert。

### 7.6 M3 — 任务状态轮询

```
Frontend (drawer 打开)：useSubmissionPolling 收集 QUEUED|RUNNING；每 5s 触发
GET /api/sandboxes/{id}/submissions/{submissionId}
SandboxSubmissionService.refresh：
  - 已是终态 → 直接返回
  - 否则 CapeV2SandboxDriver.fetchTaskStatus(cape_task_id)
    - CAPEv2: GET /tasks/view/{id}
  - 映射到我方枚举；状态变化时 UPDATE submissions
返回 SubmissionOutput
Frontend：chip 颜色更新；终态时移出活跃集合；unmount cleanup
```

特性：
- 终态后停止轮询。
- 仅状态变化时 UPDATE，避免噪音。
- 多个活跃 submission 并行轮询；不互相阻塞。
- CAPEv2 不可达 → chip 旁⚠图标 + tooltip；继续轮询（暂态错误能自愈）。
- `UNKNOWN` → 黄 chip + 原始字符串 tooltip；继续轮询。

### 7.7 集成边界总览

| 能力 | 我方 | 外部（CAPEv2 / 主机 / hypervisor） |
| --- | --- | --- |
| 沙箱预设 CRUD | ✓ | — |
| 网络规则录入与导出 | ✓ | 主机管理员手工 / IaC 应用 |
| 沙箱平台健康检查 | ✓ ping 端点 | CAPEv2 自身 |
| 分析机列表同步与可视化 | ✓ /machines/list 落地只读视图 | CAPEv2 |
| 自动还原快照配置 | ✓ 表单强制 + 状态可视化 | CAPEv2 machinery + hypervisor |
| 真实样本在 guest 内执行 | — | CAPEv2 + guest VM |
| 网络隔离运行时生效 | — | 主机 iptables / routing.conf |
| 任务状态轮询展示 | ✓ | CAPEv2 |
| CAPEv2 报告 / 截图 / pcap | — 本期不做 | CAPEv2 |
| 报告外链或嵌入 | — 本期不做 | CAPEv2 web UI |

## 8. 错误处理与边界声明

### 8.1 异常层级

```
RuntimeException
├── InputValidationException             (现有，扩展 reason_code)
└── SandboxIntegrationException          (新增，io.veriguard.integration.sandbox)
    └── reasonCode: enum (+ 可选 capeStatusCode、capeBody snippet 仅日志)

ElementNotFoundException                 (现有)
```

约束：驱动层只抛 `SandboxIntegrationException`；服务层只抛 `InputValidationException` / `ElementNotFoundException`；REST 层不抛业务异常。HTTP 映射统一由 `RestControllerAdvice` 处理。

### 8.2 错误码 / reason_code 表

| HTTP | error_code | reason_code | 出现位置 | 中文文案 |
| --- | --- | --- | --- | --- |
| 400 | `input_validation_failed` | `sandbox_name_blank` | preset 表单 | 沙箱名称必填 |
| 400 | `input_validation_failed` | `sandbox_name_duplicated` | preset 创建 | 沙箱名称已存在，请换一个 |
| 400 | `input_validation_failed` | `sandbox_auto_restore_required` | preset 表单 | 必须开启执行完成后自动还原 |
| 400 | `input_validation_failed` | `sandbox_network_rule_cidr_invalid` | preset 表单 | CIDR 格式无效 |
| 400 | `input_validation_failed` | `sandbox_network_rule_ports_invalid` | preset 表单 | 端口表达式无效（接受：`all`、单端口、范围、列表） |
| 400 | `input_validation_failed` | `sandbox_supported_sample_types_empty` | preset 表单 | 至少需要一种支持的样本类型 |
| 400 | `input_validation_failed` | `sandbox_status_invalid` | preset 表单 | 状态值无效 |
| 400 | `input_validation_failed` | `sample_file_missing` | 提交样本 | 请选择样本文件 |
| 400 | `input_validation_failed` | `sample_file_too_large` | 提交样本 | 样本文件超过 64 MB |
| 400 | `input_validation_failed` | `sample_type_not_supported_by_preset` | 提交样本 | 当前沙箱预设不支持该样本类型 |
| 400 | `input_validation_failed` | `sandbox_inactive` | 提交样本 | 沙箱预设处于停用状态，无法提交 |
| 400 | `input_validation_failed` | `target_machine_unknown` | 提交样本 | 选择的分析机不存在或已被移除，请重新同步 |
| 404 | `element_not_found` | `sandbox_preset_not_found` | 任意 | 沙箱预设不存在或已被删除 |
| 404 | `element_not_found` | `sandbox_submission_not_found` | 状态轮询 | 提交记录不存在 |
| 502 | `sandbox_integration_failed` | `connection_failed` | 健康/同步/提交/轮询 | 无法连接沙箱平台，请检查网络与 endpoint 配置 |
| 502 | `sandbox_integration_failed` | `authentication_failed` | 同上 | 沙箱平台身份认证失败，请联系运维更新 API token |
| 502 | `sandbox_integration_failed` | `remote_error` | 同上 | 沙箱平台返回错误：{capeStatusCode}（详见服务端日志） |
| 502 | `sandbox_integration_failed` | `protocol_mismatch` | 同上 | 沙箱平台响应格式不符，请联系运维确认 CAPEv2 版本 |
| 504 | `sandbox_integration_failed` | `timeout` | 同上 | 沙箱平台响应超时，请稍后重试或检查负载 |
| 500 | `internal_error` | `unmapped_status` | 状态轮询 | （UI 用黄 chip + tooltip "未知状态：{raw}"，不走通用 toast） |

> `sandbox_network_rules_empty` 已移除（规则可为空）。

响应体格式（适配项目现有 `ValidationErrorBag` envelope，由 `RestBehavior` 统一发出）：

```json
{
  "code": 502,
  "message": "SANDBOX_INTEGRATION_FAILED",
  "errors": {
    "children": {
      "authentication_failed": {
        "errors": [{ "message": "Sandbox authentication failed" }]
      }
    }
  }
}
```

约定：

- `errors.children` 的 **key** 即 `reason_code`（snake_case），与 §8.2 表中"reason_code"列一致。
- `InputValidationException(field, message)`：`field` 直接传 reason_code，`message` 走英文短文。
- `SandboxIntegrationException(reasonCode, message)`：handler 用 `reasonCode.name().toLowerCase()` 作 key，bag.message 固定为 `SANDBOX_INTEGRATION_FAILED` 字符串。
- 前端按 `Object.keys(data.errors.children)[0]` 拿到 reason_code，查 `sandbox/i18n.ts` 渲染中文文案。
- `bag.code` 为 HTTP 状态码（与响应 status 一致），便于客户端基于一份契约判断。

> 这样保持与项目其它领域（auth / inject / channel 等）的错误响应一致，避免引入第二份 envelope 设计。

### 8.3 HTTP 映射规则（`RestControllerAdvice`）

| 异常 | HTTP | 备注 |
| --- | --- | --- |
| `InputValidationException` | 400 | 现有 `RestBehavior` handler；新增 reason_code 沿用 |
| `DataIntegrityViolationException` (唯一约束) | 400 + `sandbox_name_duplicated` | 在 `SandboxService.persist` 内主动捕获并转 `InputValidationException`，避免侵入 `RestBehavior` |
| `ElementNotFoundException` | 404 | 现有 |
| `SandboxIntegrationException` `reasonCode=timeout` | 504 | 新增 handler 在 `RestBehavior` |
| `SandboxIntegrationException` 其他 | 502 | 新增 handler 在 `RestBehavior` |
| `MaxUploadSizeExceededException` | 400 + `sample_file_too_large` | M3 加，新增 handler |
| 其他 | 500 + `internal_error` | 现有兜底 |

> 注：spec 早期版本的 409 conflict 改成 400 + `sandbox_name_duplicated`（沿用 `InputValidationException` 通道，前端按 reason_code 显示中文）。

### 8.4 日志策略

- 驱动层：`INFO` 调用开始/完成 + 耗时；`WARN` `SandboxIntegrationException`；`ERROR` 不可映射的远端响应（带原始 body 摘要 200 字节）。
- 服务层：`WARN` `InputValidationException`（不打栈，用户错误）；`ERROR` 仅 `unmapped_status`、`orphan_cape_task`。
- 成功的状态轮询：`DEBUG`，避免淹没。
- 结构化字段：`sandbox_id`、`submission_id`、`cape_task_id`、`reason_code` 进 MDC。
- 不记敏感信息：API token / endpoint / 用户名 → `***redacted***`。

### 8.5 边界声明文本（UI 原文）

**沙箱预设详情 / 编辑表单顶部横幅**（M1 起）
> ⓘ 网络访问控制策略与自动还原快照由沙箱平台主机管理员负责实际生效。本系统仅持久化策略、提供 iptables / routing.conf 导出脚本，并对快照配置缺失给出可视化告警；不参与运行时下发与执行。

**分析机表头部告警条**（M2 起，存在 `snapshot=null` 时）
> ⚠ 检测到 N 台分析机未配置自动还原快照。请在沙箱平台主机的 `<machinery>.conf` 中为对应分析机设置 `snapshot=` 字段并重启沙箱服务。

**分析机行 snapshot tooltip**（红 chip 悬停）
> 未配置自动还原快照，样本执行后该分析机不会回到干净态。请在沙箱平台主机修改 `<machinery>.conf` 并重启服务。

**提交样本对话框底部**（M3 起）
> ⓘ 样本将提交至沙箱平台执行，执行过程、网络隔离、还原均由沙箱平台与 hypervisor 完成。本系统仅记录提交流水与状态。

**导出脚本下载页提示**（M1 起）
> ⓘ 此脚本需运维在沙箱平台主机上以 root 执行。脚本仅声明规则，不会自动重置已有 iptables 链；请确认与现有规则不冲突。

### 8.6 安全要点

- API token 仅通过 `Authorization: Token …` Header 发；不进 URL、不进日志。
- multipart：`spring.servlet.multipart.max-file-size=64MB`、`max-request-size=64MB`；超限由 Spring 抛 `MaxUploadSizeExceededException`。
- 上传文件不落本地磁盘（除 Spring 临时目录），完成后立即关闭；`submissions` 表只存元数据 + sha256，不存样本本体。
- `@RBAC` 全覆盖。导出脚本用 `Action.WRITE`（脚本承载规则原文，按写权限严格审计）。
- 响应体不回传内部异常 stack trace。

## 9. 测试策略

### 9.1 总览

| 层 | 工具 | M1 | M2 | M3 |
| --- | --- | --- | --- | --- |
| 后端单元 | JUnit 5 + Mockito + AssertJ | Service 校验 / ScriptExporter / NotImpl 驱动 | CapeV2 驱动（MockRestServiceServer）/ 状态映射 | SubmissionService / 轮询去重 / 终态判断 |
| 后端集成 | `@SpringBootTest` + Testcontainers PG + WireMock | CRUD + 唯一约束 + 导出 HTTP | machines/sync 整事务 + healthCheck + 401 | 提交+轮询全链路 |
| 前端单元 | vitest + @testing-library/react | NetworkRuleEditor / CIDR-Port utils / SandboxDialog 校验 | CapePlatformPanel 各状态 / usePlatformStatus | SubmitSampleDialog / SubmissionStatusBadge / useSubmissionPolling |
| 端到端 | Playwright | 新建/编辑/删除/导出 | 测试连接 / 同步 / 红 chip | 提交 → drawer → 轮询到终态 |

金字塔目标：单元 ≥ 70% / 集成 ≈ 25% / E2E ≈ 5%（按用例数量）。所有 milestone 遵循 TDD（红 → 绿 → 重构）。

### 9.2 后端单元测试要点

**`SandboxServiceTest`（M1）**
- `validate_rejects_auto_restore_disabled`
- `validate_rejects_empty_sample_types`
- `validate_accepts_empty_network_rules`（修订点覆盖）
- `create_persists_kvm_provider_type_regardless_of_input`
- `update_preserves_existing_id_and_timestamps`
- `delete_cascades_submissions`（M3 增）
- `name_uniqueness_violation_translates_to_409`

**`SandboxScriptExporterTest`（M1）**
- `to_iptables_renders_each_rule_in_correct_order`（INGRESS 在前 / EGRESS 在后）
- `to_iptables_with_empty_rules_returns_header_only_script`（修订点覆盖）
- `to_iptables_handles_icmp_with_no_ports`
- `to_routing_conf_emits_one_route_per_rule`
- `to_iptables_quotes_sandbox_name_in_filename_safely`（防 shell 注入）

**`CapeV2SandboxDriverTest`（M2，`MockRestServiceServer`）**
- `list_machines_parses_canonical_response`
- `list_machines_throws_authentication_failed_on_401`
- `list_machines_throws_timeout_on_socket_timeout`
- `list_machines_throws_protocol_mismatch_on_malformed_json`
- `health_check_calls_cuckoo_status`
- `submit_sample_constructs_multipart_with_required_fields`
- `submit_sample_propagates_optional_machine_and_timeout`
- `fetch_task_status_maps_each_known_string`（参数化）
- `fetch_task_status_returns_unknown_with_raw_string_for_unmapped`
- `authorization_header_is_token_scheme_not_bearer`

**`SandboxMachineServiceTest`（M2）**
- `sync_replaces_all_machines_in_single_transaction`
- `sync_rolls_back_when_driver_throws`
- `sync_updates_last_synced_at`
- `find_missing_snapshot_uses_partial_index_query`

**`SandboxSubmissionServiceTest`（M3）**
- `submit_rejects_inactive_preset`
- `submit_rejects_unsupported_sample_type`
- `submit_rejects_oversize_file`
- `submit_persists_sha256_and_metadata_only_no_blob`
- `submit_logs_orphan_when_db_insert_fails_after_cape_accept`
- `refresh_skips_remote_call_for_terminal_state`
- `refresh_only_updates_db_when_status_changes`
- `refresh_marks_unknown_status_with_raw_string`

### 9.3 后端集成测试

- 用项目已有的 Testcontainers 模式（writing-plans 阶段确认；缺失则首期补）。
- `WireMockExtension`（JUnit 5）启动 stub；fixture JSON 在 `src/test/resources/cape-fixtures/`：
  - `machines-list.success.json` / `.three-of-which-one-missing-snapshot.json`
  - `tasks-create-file.success.json`
  - `tasks-view.queued.json` / `.running.json` / `.completed.json` / `.failed.json` / `.unknown-state.json`
  - `cuckoo-status.success.json`
- 关键场景：
  - **M1**：CRUD + 导出 HTTP（断言 Content-Type、Content-Disposition、文本含规则）；同名 preset → 409。
  - **M2**：CAPEv2 stub 返 3 台机器（1 台 `snapshot=null`） → POST sync → GET machines 返 3 行 + `missing_snapshot_count=1`；CAPEv2 401 → 502 + `authentication_failed`。
  - **M3**：完整 happy path（POST submission → stub create/file 返 task_id=42 → DB QUEUED → GET submission/{id} → stub view/42 返 running → DB RUNNING → 再次 GET stub 返 reported → DB COMPLETED）；UNKNOWN 状态写入并保留 `error_message="unmapped: <raw>"`。

### 9.4 前端单元测试

- vitest + @testing-library/react；不引 MSW；action 层用 `vi.mock` 桩。
- `NetworkRuleEditor.test.tsx`：空态渲染 / 添加 / 删除最后一条 / ICMP 时端口 disabled / CIDR 校验错误。
- `cidr-port-validators.test.ts`：8–10 用例，含 IPv4 / IPv6 / `0.0.0.0/0` / `all` / 范围 / 列表 / 错误。
- `SandboxDialog.test.tsx`：必填校验、保存按钮 disable 条件、**网络规则为空时不阻止保存**（修订点覆盖）。
- `CapePlatformPanel.test.tsx`：loading / healthy / unhealthy / 上次同步相对时间 / 红 chip 数。
- `usePlatformStatus.test.tsx`：action 调用 / toast / 刷新。
- `SubmitSampleDialog.test.tsx`：sample type 下拉只显 preset 支持 / 文件超 64MB 阻止 / 分析机来自全局缓存 / 失败 reason_code 文案。
- `useSubmissionPolling.test.tsx`：5s 轮询 / 终态停止 / unmount 清理（`vi.useFakeTimers()`）。
- `SubmissionStatusBadge.test.tsx`：每 status 颜色 + 中文文案。

### 9.5 E2E（Playwright）

- 沿用项目 `playwright.config.ts`；新增 `tests_e2e/admin/veriguard/sandbox/`。
- CAPEv2 桩：用 Playwright `route` 拦截 `/api/sandbox-platform/*` 与 `/api/sandboxes/{id}/submissions/*`，不引 WireMock 容器。
- 主要场景：
  - M1：登录 → 沙箱管理 → 新建预设（不填规则）→ 编辑加 2 条 → 导出 iptables（断言 download 事件）→ 删除（断言二次确认）。
  - M2：平台面板首屏（mock 健康 + 3 机器）→ 测试连接 → 同步分析机 → 第 3 行红 chip。
  - M3：某预设上点提交样本 → 上传 / 选样本 / 自动选机器 → 提交 → drawer 自动打开 → 第一次轮询返 RUNNING → 第二次返 COMPLETED → chip 变绿。
- E2E 单 worker（沿用项目 `--workers=1 --retries=2`）。

### 9.6 覆盖率门槛

| 范围 | 门槛 | 工具 |
| --- | --- | --- |
| `io.veriguard.integration.sandbox.**` | 90% lines / 85% branches | JaCoCo |
| `io.veriguard.rest.security_validation.**`（新/改） | 85% lines | JaCoCo |
| 其他改动文件 | 80% lines（项目基线） | JaCoCo |
| `admin/components/veriguard/sandbox/**` | 85% lines | vitest --coverage |
| `actions/veriguard/veriguard-actions.ts` | 80% lines | vitest |

低于门槛 → CI fail，无豁免。

### 9.7 CI 与本地验证

- 本地预提交：`mvn -pl veriguard-api -am test`；`yarn check-ts && yarn lint && yarn test`；milestone 收尾跑 `yarn test:e2e`。
- CI：编译 → 单元 → 集成 → JaCoCo 阈值；前端 lint / check-ts / vitest --coverage / playwright（独立 job）。
- 文档：`Veriguard二开落地说明.md` 中"检查记录"按 milestone 增三行。

### 9.8 不做的事

- ❌ 对真实 CAPEv2 实例的契约测试（CAPEv2 升级由 fixture 替换 + 集成测试发现）。
- ❌ 引 MSW / Mirage（保持依赖面）。
- ❌ Pact / Spring Cloud Contract（CAPEv2 非我方掌控）。
- ❌ 性能 / 压测（M3 非高吞吐场景，另立 spec）。
- ❌ E2E 真下载脚本验证 shell 可执行性（脚本由 `SandboxScriptExporterTest` 单测覆盖）。

## 10. Milestone 切分与验收

每期完整可交付：单独通过编译 / 测试 / lint，PRD 截图能拍到，独立 commit。顺序 M1 → M2 → M3，每期收尾仓库可发布。

### 10.1 M1 — 控制台 UX 完善 + 边界声明 + 抽象驱动

**交付物**

- 迁移 `V4_73__Extend_veriguard_sandbox.java`（删 endpoint / provider_type，加 name 唯一索引）。
- `SandboxApi` 拆出；现 5 端点 URI 不变；新增 2 个导出端点。
- `SandboxService` 拆出；校验按 §8 修订。
- `SandboxScriptExporter`（Java text block 模板）。
- `SandboxDriver` + `NotImplementedSandboxDriver` + `SandboxDriverRegistry`。
- `SandboxIntegrationException` + `RestControllerAdvice` 新映射。
- `application.yml` 加 `veriguard.sandbox.cape.*` 占位；M1 不强校验。
- 前端：`SandboxList` / `SandboxDialog` / `NetworkRuleEditor` / `NetworkRuleExportButtons` / `DeleteConfirmDialog`，从 `VeriguardConsole.tsx` 拆出。
- i18n 同步 `yarn extract-translation`。
- 测试：§9.2 / §9.4 中 M1 全部；E2E 主流程。

**PRD §2.5 验收点**

- ✅ 沙箱平台 CRUD（截图：列表 + 编辑对话框 + 删除二次确认）。
- ✅ 网络访问控制策略配置（截图：多规则编辑器 + iptables 导出下载 + 审计日志）。
- ✅ 自动还原（部分，仅表单校验）。

**验证命令**

```sh
mvn -pl veriguard-api -am test
mvn -pl veriguard-api jacoco:report
cd veriguard-front
yarn check-ts && yarn lint && yarn test --coverage
yarn test:e2e -g "sandbox.m1"
git diff --check -- docs AGENTS.md
rg -n 'TODO|FIXME|待补充' docs/参考资料/Veriguard二开落地说明.md
```

**Commit / 文档**

1. 实现：`需求：完善沙箱预设控制台与导出脚本`
2. 文档：`文档：同步沙箱预设 M1 落地说明`（更新落地路径 / 接口清单 / 检查记录）

### 10.2 M2 — CAPEv2 驱动接入 + 分析机同步

**交付物**

- 迁移 `V4_75__Add_veriguard_cape_machines.java`。
- `CapeV2ConnectionProperties`（启动校验 endpoint + api-token，缺失启动失败）。
- `CapeV2HttpClient`（RestClient 包装，超时按配置）。
- `CapeV2SandboxDriver` 实装 `healthCheck` / `listMachines`；`submitSample` / `fetchTaskStatus` 抛 `UnsupportedOperationException`，签名稳定。
- `SandboxMachineService` + `MachineOutput` + 三个 `/api/sandbox-platform/*` 端点。
- 内存级 healthy 标志（最近一次成功/失败更新）。
- 前端：`CapePlatformPanel` + `MachineTable` + `usePlatformStatus`。
- 部署文档：`veriguard-dev/.env.example` 加变量；二开落地说明加"如何在 CAPEv2 主机生成 token"段落（用 `drf_create_token`）。
- 测试：§9.2 / §9.3 / §9.4 / §9.5 中 M2 全部。

**PRD §2.5 验收点**

- ✅ 自动还原（完整：分析机表 + 红 chip + 横幅告警）。
- ✅ 真实样本执行（部分：连通性 + 机器列表准备就绪）。

**新增风险**

- M2 完成后部署强依赖 CAPEv2 endpoint + api-token；缺失即启动失败。文档显式提示运维这一变化。实际起 CAPEv2 不在本期实现工作量内。

**Commit / 文档**

1. 实现：`需求：接入沙箱平台健康检查与分析机同步`
2. 文档：`文档：同步沙箱预设 M2 落地说明`

### 10.3 M3 — 沙箱样本提交 + 任务状态轮询

**交付物**

- 迁移 `V4_76__Add_veriguard_sandbox_submissions.java`。
- `CapeV2SandboxDriver.submitSample` / `fetchTaskStatus` 实装；状态映射写在 `CapeV2ResponseMapper`，与 §8.2 一致。
- `SandboxSubmissionService` + 三个 REST 端点。
- `MaxUploadSizeExceededException` handler；`spring.servlet.multipart.max-file-size=64MB` 显式声明。
- 前端：`SubmitSampleDialog` + `SubmissionHistoryDrawer` + `SubmissionStatusBadge` + `useSubmissionPolling`。
- 测试：§9.2 / §9.3 / §9.4 / §9.5 中 M3 全部。

**PRD §2.5 验收点**

- ✅ 真实样本执行（完整：提交对话框 + 历史表 + 状态从 QUEUED → RUNNING → COMPLETED 截图）。
- ✅ 8 类样本类型在下拉中可见。

**Commit / 文档**

1. 实现：`需求：实现沙箱样本提交与任务状态轮询`
2. 文档：`文档：同步沙箱预设 M3 落地说明`

### 10.4 横切关注

- **无 fallback** 自检：每期收尾跑 `rg -n 'try.*catch.*\{\s*\}|catch.*Exception.*log\.warn|return.*null.*//.*fallback' veriguard-api/src` 不命中。
- **审计日志**：CRUD / 导出 / 提交 / 同步事件都进项目已有审计切面。
- **i18n**：每期 PR 含 `yarn extract-translation` 后产物；缺失 → lint 失败。
- **AGENTS.md "代码 + 文档分别 commit"**：每期两个 commit。
- **分支策略**：`worktrees/sandbox-m1` / `-m2` / `-m3`，每期合回 main 后开下期 worktree。

### 10.5 完成定义（DoD，每期适用）

- [ ] 全部新增 / 修改文件 < 800 行；新增函数 < 50 行；嵌套 ≤ 4 层。
- [ ] JaCoCo 覆盖率门槛达标。
- [ ] `mvn spotless:apply` 无差异；`yarn lint --max-warnings 0` 通过。
- [ ] 文档与代码同期 commit；二开落地说明的接口清单 / 检查记录与代码一致。
- [ ] AGENTS.md "fail visibly" 自检：fallback / 静默 catch / 默认值兜底零命中。
- [ ] PRD 截图按 milestone 已拍并附入 PR / 文档目录。
- [ ] 全部 commit 含 `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>` trailer。

## 11. 历史决策与撤销点

| 时间点 | 决策 | 状态 |
| --- | --- | --- |
| 起始 | 范围拟定为 A+B+C 三块 | 收敛为 A+B（C 在后续期） |
| Q1 | 范围在 A、B、A+B、全部之间选择 | A+B（含提交样本按钮） |
| Q4 | 凭据形式：用户名密码 vs API token | 收敛为 token |
| Q5 | 沙箱粒度：主控 vs 分析机 | 主控 → 后修订为"用例预设" |
| Q6 | 单 vs 多 CAPEv2 主控 | 单主控 |
| Q7 | 凭据置 DB 或配置 | 配置文件 |
| 修订 | 网络规则必填 → 可空 | 可空 |

## 12. 后续期（不在本 spec）

- 网络规则运行时下发（host-side agent / IaC 集成）。
- 多 CAPEv2 主控（cluster_key map）。
- CAPEv2 报告 / 截图 / pcap 镜像或外链。
- 沙箱与攻击编排 / 用例执行链的端到端集成。
- 后台对账作业（孤儿 task 清理 / 状态自愈轮询）。
- 沙箱预设的导入 / 导出 / 版本管理。

## 13. 参考

- CAPEv2 项目：[github.com/kevoreilly/CAPEv2](https://github.com/kevoreilly/CAPEv2)
- CAPEv2 REST API：[capev2.readthedocs.io/en/latest/usage/api.html](https://capev2.readthedocs.io/en/latest/usage/api.html)
- DRF authtoken 永不过期参考：[Medium — DRF Token Authentication with Expires In](https://medium.com/@yerkebulan199/django-rest-framework-drf-token-authentication-with-expires-in-a05c1d2b7e05)
- 仓库：`docs/prd/产品要求.md`、`docs/参考资料/Veriguard二开落地说明.md`、`AGENTS.md`、`CLAUDE.md`
