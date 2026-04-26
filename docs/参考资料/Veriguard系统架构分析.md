# Veriguard（原 OpenBAS）系统架构分析

## 说明

- 分析对象：`https://github.com/Veriguard-Platform/veriguard`
- 分析时间：2026-04-24
- 分析方法：基于官方 GitHub 仓库、官方文档的静态代码与配置阅读
- 说明：OpenBAS 已在 2.0 阶段重命名为 Veriguard；代码中仍保留 `openbas.*` 到 `veriguard.*` 的兼容配置路径

## 一、系统整体技术框架

### 1.1 产品定位

Veriguard 本质上不是单点 BAS 工具，而是一个更完整的 AEV（Adversarial Exposure Validation）平台。它既支持传统 BAS/攻击模拟，也支持场景编排、人员/流程演练、验证结果分析、外部安全平台联动，以及基于 OpenCTI 的威胁情报驱动能力。

从官方文档和代码目录看，它覆盖了“场景设计 -> 执行编排 -> 资产/人员目标 -> 结果采集 -> 期望校验 -> 分析报表 -> 外部联动”的完整闭环。

### 1.2 总体架构分层

从仓库结构看，Veriguard 采用典型的前后端分离 + 后端分层 + 外部依赖协同架构：

- 前端：`veriguard-front`
  - React 19 + TypeScript + Vite
  - Material UI + React Router + Redux + React Hook Form
- 后端：Java 21 + Spring Boot 3.3.x，多模块 Maven 工程
  - `veriguard-model`：数据模型、JPA、搜索引擎抽象、对象存储/搜索驱动
  - `veriguard-framework`：共享配置、队列、资产与集成基础能力
  - `veriguard-api`：REST API、认证授权、调度、执行、集成实现、实时流、报表、管理能力
- 外部运行依赖：
  - PostgreSQL：主业务数据库
  - MinIO / S3：对象存储
  - RabbitMQ：异步消息与执行分发
  - Elasticsearch / OpenSearch：检索、聚合分析、仪表盘
  - 可选外部执行/联动平台：Caldera、CrowdStrike、Tanium、SentinelOne、Palo Alto Cortex、OpenCTI 等

### 1.3 一个更准确的理解方式

Veriguard 更像“安全演练与暴露验证控制平面”，而不是单纯的 payload 执行器。它本身负责：

- 建模：场景、注入、资产、组织、角色、目标、期望、发现
- 编排：模拟、调度、依赖、链路、通知、报告
- 协调：调用 injector / executor / collector / agent
- 分析：搜索、聚合、仪表盘、结果校验、实时事件流

真正落地到终端、邮件、外部安全产品、仿真平台的动作，很多是通过集成层转交给其他组件完成的。

## 二、功能模块拆解

以下模块划分，是根据官方文档导航、前端 `actions` 目录、后端 `rest` 与 `database/model` 目录综合整理的。

### 2.1 场景与演练设计模块

- Scenario / Simulation / Exercise
- Inject / Payload / Variable / Channel / Document / Challenge / Lessons
- Team / Player / Communication / Objective

这一层解决的是“要模拟什么、谁参与、如何投递、如何收集反馈、如何复盘”的问题。

### 2.2 安全验证与暴露评估模块

- Atomic Testing
- Expectations / InjectExpectation / Validation
- Findings / Vulnerability / Mitigation / Detection Remediation
- Security Control Validation / Security Coverage

这一层解决的是“验证是否命中预期、哪些控制生效、哪些暴露路径仍然存在”。

### 2.3 资产与目标模块

- Asset / Endpoint / Agent / AssetGroup / SecurityPlatform / Domain
- EndpointTarget / TeamTarget / AgentTarget / PlayerTarget

这一层负责把验证对象从“抽象场景”落到“实际资产、实际组织对象、实际终端或安全平台”。

### 2.4 集成生态模块

- Injectors
- Executors
- Collectors
- ConnectorInstance / CatalogConnector / InjectorContract
- XTM Hub / XTM Composer
- OpenCTI integration

这是 Veriguard 最关键的可扩展层。Veriguard 自身不试图把所有执行能力硬编码在平台内，而是通过 Integration Factory / Manager 机制，统一纳管不同类型的外部能力。

### 2.5 运营分析与管理模块

- Dashboard / CustomDashboard / Widget
- Report / Export / Import
- NotificationRule
- Users / Roles / Grants / RBAC
- Settings / Organizations / Tags / Policies

这一层负责平台治理、可视化、报表和团队协同。

## 三、底层核心实现技术

下面区分“代码直接可证实”和“结合代码推断”两类结论。

### 3.1 后端核心骨架

**代码直接可证实**

- Veriguard 是一个 Maven 多模块 Spring Boot 单体应用，不是微服务拆分。
- 主应用在 `veriguard-api` 中启动，但 `model`、`framework`、`api` 被清晰拆层。
- Spring 同时启用了异步、定时调度、事务管理、缓存。

**分析判断**

- 它是“模块化单体”路线：部署简单，跨模块调用成本低，便于快速叠加大量安全产品集成。
- 对这种产品形态而言，这比一开始拆成微服务更合理。

### 3.2 数据存储：关系库 + 对象存储 + 搜索引擎三分层

**代码直接可证实**

- PostgreSQL 承载业务主数据，Spring Data JPA/Hibernate 负责 ORM。
- Flyway 管理数据库迁移。
- MinIO / S3 负责文件与对象内容。
- 平台支持 Elasticsearch 或 OpenSearch 二选一，且通过 `EngineComponent` 做引擎切换。

**分析判断**

- 这是很典型的“事务数据在关系库、检索分析在搜索引擎、附件在对象存储”的企业级平台设计。
- 这使它既能保持业务一致性，又能支持搜索、仪表盘、统计、攻击路径等复杂分析能力。

### 3.3 搜索索引同步机制

**代码直接可证实**

- 实体可通过 `@Indexable` 标注为可检索对象。
- JPA 实体监听器 `ModelBaseListener` 在增删改时发布 `BaseEvent` / `IndexEvent`。
- Quartz Job `EngineSyncExecutionJob` 会并行批量同步模型到 Elastic/OpenSearch。
- `IndexingStatus` 用来跟踪各类实体的索引状态。

**分析判断**

- Veriguard 不是“请求时现查数据库做报表”，而是显式维护一套搜索分析索引。
- 这种实现更适合大规模筛选、聚合、趋势图、attack path、全局搜索，但代价是要处理索引一致性与重建问题。

### 3.4 异步执行与消息队列

**代码直接可证实**

- RabbitMQ 是平台关键依赖，不只是可选组件。
- `QueueService` 用于向不同 inject / executor 路由键发布消息。
- `BatchQueueService` 内部实现了 publisher / consumer / worker 分工、QoS、批量处理、重连和缓冲队列。
- 配置中允许为不同队列设置 `publisher-number`、`consumer-number`、`worker-number`、`max-size` 等参数。

**分析判断**

- Veriguard 把大量“执行、状态回传、轨迹处理”做成异步流水线，而不是同步 API 调用。
- 这对高并发注入、批量终端操作、执行轨迹处理更稳，也更适合跨产品联动。

### 3.5 调度与执行编排

**代码直接可证实**

- 平台使用 Quartz。
- 已实现多个独立 Job，例如：场景执行、注入执行、通信检查、OpenCTI 同步、引擎同步、集成同步、安全覆盖等。
- 任务线程池与流式事件线程池单独配置。

**分析判断**

- Veriguard 的核心不是“立即执行一个攻击动作”，而是“长期持续地调度和编排大量验证任务”。
- 所以它更接近“安全演练编排平台”，而非简单的 attack runner。

### 3.6 实时事件流

**代码直接可证实**

- 平台暴露 `/api/stream`，使用 `text/event-stream` 和 Reactor `Flux` 推送事件。
- `StreamApi` 监听数据库事件后，根据权限过滤，再通过 SSE 推送到前端。
- 前端依赖 `@microsoft/fetch-event-source`。

**分析判断**

- Veriguard 的前端是“准实时控制台”，不是纯静态后台。
- 这对演练监控、状态刷新、结果回传、协同观察很关键。

### 3.7 认证授权与安全模型

**代码直接可证实**

- 支持本地认证、OAuth2 / OpenID、SAML2；配置中也保留 Kerberos 开关。
- `TokenAuthenticationFilter` 同时支持 Cookie 与 Bearer Token。
- Spring Security + 自定义用户映射 + 平台内 RBAC 共存。

**分析判断**

- Veriguard 是“企业内网平台”定位，因此它优先适配企业 SSO 与角色治理，而不是做互联网产品式认证。

### 3.8 集成扩展机制

**代码直接可证实**

- 集成层以 `Integration` / `IntegrationFactory` / `Manager` 为主骨架。
- 连接器实例存储在数据库中，运行时由 Manager 监控、启动、停止、刷新。
- 已实现多类 executor / injector 工厂，如 Caldera、CrowdStrike、Tanium、SentinelOne、PaloAlto Cortex、Email、OVH、OpenCTI、Veriguard 自身等。

**分析判断**

- 这是 Veriguard 最有价值的底层设计之一。
- 它把“平台能力”与“具体安全产品实现”解耦，后续扩展新产品，主要增量在 connector factory 与 client 层，而不是重写业务主线。

### 3.9 Agent / Executor 模式

**代码直接可证实**

- 官方文档明确写到 Veriguard Agent 使用 Rust 开发。
- Agent 的职责是注册资产、拉取作业/脚本，并把执行信息转给 implant；Agent 自身不直接在资产上执行动作。
- 文档明确强调这种设计是为了尽量对杀软保持中性，并提升模拟执行成功率。

**分析判断**

- Veriguard 在终端侧采用了“平台控制 + agent 编排 + implant/外部执行器执行”的分层模型。
- 这种模型比“平台直接 SSH/WinRM/脚本执行”更适合做多环境、多权限、多终端类型的长期验证。

### 3.10 AI 与可观测性

**代码直接可证实**

- `application.properties` 中存在 `ai.enabled`、模型、endpoint、token 等配置。
- `/api/ai/*` 提供流式 AI 能力，例如摘要、解释、改写、生成消息与主题。
- Telemetry 文档表明平台会通过 OTLP over HTTPS 上报 OpenTelemetry JSON 格式的匿名统计数据。
- 代码依赖中还包含 Pyroscope agent 与 OpenTelemetry exporter。

**分析判断**

- AI 在当前版本里更像“增强层”，不是平台主执行路径。
- 可观测性部分已经预埋得较完整，说明产品在向更成熟的平台运营方向走。

## 四、一个简化的数据流视角

可以把 Veriguard 运行机制理解为：

1. 前端创建场景、注入、目标、期望、执行计划
2. 后端写入 PostgreSQL
3. JPA 监听器发布实体事件
4. Quartz / EngineService 批量同步到 Elastic / OpenSearch
5. 前端通过 SSE 获取实时变化
6. Veriguard 通过 RabbitMQ、集成工厂、Agent、Executor、Collector 与外部环境交互
7. 执行结果、轨迹、校验结果再回写平台，形成报表、发现和安全覆盖视图

## 五、对 Veriguard 的总体判断

- **强项**：架构完整，定位清晰，工程化程度高，扩展点成熟，明显不是“脚本集合”式 BAS 工具。
- **核心壁垒**：多产品集成框架、搜索分析引擎抽象、事件流与调度体系、Agent/Executor 分层。
- **技术路线**：模块化单体 + 强集成平台，而不是微服务化拆散。
- **适用场景**：持续化 BAS / AEV、紫队演练、威胁情报驱动场景验证、跨终端/跨安全产品联动验证。

## 六、参考来源

- 官方仓库：<https://github.com/Veriguard-Platform/veriguard>
- 官方 README：<https://github.com/Veriguard-Platform/veriguard/blob/master/README.md>
- 根 `pom.xml`：<https://github.com/Veriguard-Platform/veriguard/blob/master/pom.xml>
- 后端配置：<https://github.com/Veriguard-Platform/veriguard/blob/master/veriguard-api/src/main/resources/application.properties>
- 前端依赖：<https://github.com/Veriguard-Platform/veriguard/blob/master/veriguard-front/package.json>
- Veriguard 配置兼容层：<https://github.com/Veriguard-Platform/veriguard/blob/master/veriguard-framework/src/main/java/io/veriguard/config/VeriguardConfig.java>
- 官方安装文档：<https://docs.veriguard.io/latest/deployment/installation/>
- 官方认证文档：<https://docs.veriguard.io/latest/deployment/authentication/>
- 官方 Agent 文档：<https://docs.veriguard.io/latest/usage/veriguard-agent/>
- 官方 Telemetry 文档：<https://docs.veriguard.io/latest/reference/deployment/telemetry/>
- 官方重命名迁移说明：<https://docs.veriguard.io/latest/deployment/breaking-changes/2.0.0-veriguard-renaming/>
