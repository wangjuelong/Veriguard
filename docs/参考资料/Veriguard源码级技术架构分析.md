# Veriguard 源码级技术架构分析

## 说明

- 目标仓库：`https://github.com/Veriguard-Platform/veriguard`
- 分析粒度：源码级静态分析
- 本次分析对应源码提交：`dd437fb4f09b39641caa4290e121f193cc03b148`
- 说明：该项目已从 OpenBAS 演进并更名为 Veriguard，源码中保留了大量 `openbas.*` 到 `veriguard.*` 的兼容配置

## 一、仓库整体结构

从源码看，Veriguard 采用“前后端分离 + Java 模块化单体 + 多运行时依赖”的架构。

### 1.1 顶层目录

- `veriguard-api/`：Spring Boot 主应用，包含 REST API、调度、执行、集成实现、认证授权、实时流
- `veriguard-framework/`：共享配置、队列、资产相关基础能力、集成基类依赖
- `veriguard-model/`：JPA 实体、Repository、搜索引擎抽象、对象存储/搜索驱动、Schema 元数据
- `veriguard-front/`：前端应用，React + TypeScript + Vite
- `veriguard-dev/`：开发环境编排，含 PostgreSQL、RabbitMQ、MinIO、Elastic/OpenSearch、Caldera 等

### 1.2 后端模块关系

根 `pom.xml` 显示后端是标准 Maven 多模块工程：

- `veriguard-model`
- `veriguard-framework`
- `veriguard-api`

依赖方向也很清晰：

- `api -> framework -> model`

这说明它不是微服务拆分，而是边界明确的模块化单体。

## 二、系统整体技术框架

### 2.1 后端基础技术栈

从 `pom.xml` 与各子模块 `pom.xml` 可直接确认：

- Java 21
- Spring Boot 3.3.7
- Spring Web / WebFlux
- Spring Data JPA + Hibernate 6
- Spring Security
- Flyway
- Quartz
- Caffeine Cache
- OpenTelemetry / OTLP
- RabbitMQ Java Client
- Elasticsearch Java Client / OpenSearch Java Client
- MinIO Java SDK

### 2.2 前端基础技术栈

从 `veriguard-front/package.json` 和 `src/` 可以确认：

- React 19
- TypeScript 5.9
- Vite 8
- Material UI 7
- React Router 7
- Redux + redux-thunk
- React Hook Form
- react-intl
- Vitest + Playwright
- SSE 客户端：`@microsoft/fetch-event-source`
- 局部状态：`zustand`

### 2.3 运行时依赖

`veriguard-api/src/main/resources/application.properties` 和 `veriguard-dev/docker-compose.yml` 说明 Veriguard 运行至少依赖：

- PostgreSQL：主事务数据
- RabbitMQ：异步消息与执行分发
- MinIO / S3：对象存储
- Elasticsearch 或 OpenSearch：检索、聚合、报表、仪表盘

开发环境还内置：

- Caldera
- pgAdmin
- Kibana
- XTM Composer

## 三、源码级模块划分

### 3.1 数据模型层：`veriguard-model`

这是 Veriguard 的核心领域模型层。仅 `database/model` 目录就有 **146 个实体类**，说明平台建模非常完整，不是“几张任务表 + 几个脚本执行器”的轻量产品。

主要实体类型包括：

- 场景与演练：`Scenario`、`Exercise`、`Inject`、`Injection`
- 目标与资产：`Asset`、`Endpoint`、`Agent`、`AssetGroup`
- 安全验证：`InjectExpectation`、`Finding`、`Vulnerability`、`SecurityCoverage`
- 运营协同：`Team`、`Role`、`User`、`NotificationRule`
- 集成生态：`ConnectorInstance`、`CatalogConnector`、`InjectorContract`
- 分析展示：`Widget`、`CustomDashboard`、`Report`

这层还包含三类关键基础设施代码：

- JPA / Repository
- 搜索引擎模型与索引服务
- 对象存储和搜索驱动

### 3.2 共享基础层：`veriguard-framework`

这一层的职责不是业务编排，而是给 API 模块提供可复用基础设施。

重点包括：

- `config/VeriguardConfig.java`
- `config/RabbitmqConfig.java`
- `config/QueueConfig.java`
- `asset/QueueService.java`
- `model/ExecutionProcess.java`

其中最关键的是 `VeriguardConfig.java`：它几乎把平台所有核心配置都统一到一个配置对象里，并且通过：

- `${openbas.xxx:${veriguard.xxx:...}}`

实现了 OpenBAS 到 Veriguard 的平滑兼容。

### 3.3 应用编排层：`veriguard-api`

这是主业务应用层，也是平台真正的“控制平面”。

从目录数量看，`rest/` 下有 **52 个一级业务模块目录**，已经足够说明平台覆盖的业务面非常广。

主要模块包括：

- `rest/scenario`
- `rest/exercise`
- `rest/inject`
- `rest/payload`
- `rest/asset`
- `rest/asset_group`
- `rest/dashboard`
- `rest/report`
- `rest/stream`
- `rest/settings`
- `rest/connector_instance`
- `rest/injector_contract`
- `rest/finding`
- `rest/vulnerability`

这些 REST 模块背后由 `service/`、`scheduler/`、`integration/`、`execution/` 等包共同支撑。

## 四、前端源码架构

### 4.1 前端入口与路由结构

关键入口文件：

- `src/index.tsx`
- `src/app.tsx`
- `src/root.tsx`

其中：

- `app.tsx` 负责 Redux Provider 和 BrowserRouter
- `root.tsx` 负责登录态分流、权限上下文、国际化、主题、企业版开关、系统 Banner

前端路由按三类视图拆分：

- `src/public/`：未登录或公开页面
- `src/private/`：玩家/参与者视图
- `src/admin/`：管理员/运营控制台

这种结构说明 Veriguard 从前端层就区分了：

- 平台运营者
- 演练参与者
- 公共访问入口

### 4.2 前端状态管理

`src/store.ts` 显示主状态管理仍以 Redux 为核心：

- `createStore`
- `redux-thunk`
- `reducers/Root.ts`
- `actions/*`

同时辅以：

- `useHelper()` 做 Immutable.js 到 JS 的映射与选择器封装
- `zustand` 处理部分局部组件态

这不是新一代 RTK 风格，而是偏“成熟平台型前端”的传统 Redux 分层。

### 4.3 前端业务模块组织方式

前端源码将平台能力切分得非常细：

- `actions/*`：按资源分片的请求与状态更新
- `admin/components/*`：管理后台页面与业务组件
- `public/components/*`：公开视图
- `utils/permissions/*`：权限模型
- `utils/endpoints/*`：接口映射

这说明前端并不是“一个页面调用一组 API”的轻量结构，而是资源驱动、权限驱动的复杂后台应用。

## 五、后端核心技术机制

### 5.1 启动与基础配置机制

主启动类：`veriguard-api/src/main/java/io/veriguard/App.java`

核心特点：

- Spring Boot 主应用
- 启动前先执行 Flyway migration name 校验
- 启动后通过 `@PostConstruct` 初始化平台实例 ID
- 平台实例 ID 写入数据库 `Setting`

这意味着：

- Veriguard 把“平台实例身份”当成核心元数据管理
- 启动阶段不只是拉起服务，还会做平台状态初始化

## 5.2 安全认证与授权模型

### 认证链路

关键文件：

- `config/AppSecurityConfig.java`
- `security/TokenAuthenticationFilter.java`
- `config/security/OpenSamlConfig.java`

源码确认支持：

- 本地认证
- OAuth2 / OpenID
- SAML2
- Cookie Token
- Bearer Token

`TokenAuthenticationFilter` 的设计很实用：

- 优先从 `Authorization` 头取 token
- 若不存在则退回 `veriguard_token` Cookie
- Bearer Token 既兼容 JWT，也兼容明文 token

### 权限控制

关键文件：

- `aop/RBAC.java`
- `aop/RBACAspect.java`
- `service/PermissionService`

它不是只靠 Spring Security URL 规则，而是在业务方法上加 `@RBAC` 注解，然后由切面在调用前做：

- 当前用户解析
- SpEL 解析资源 ID
- 基于资源类型和动作的权限判断

这是一种“平台级资源授权模型”，比简单角色判断更细。

## 5.3 搜索引擎抽象与索引机制

### 引擎抽象

关键文件：

- `service/EngineComponent.java`
- `config/EngineConfig.java`
- `driver/ElasticDriver.java`
- `driver/OpenSearchDriver.java`
- `service/ElasticService.java`
- `service/OpenSearchService.java`

`EngineComponent` 通过配置决定实例化：

- `ElasticService`
- `OpenSearchService`

这说明 Veriguard 在源码级做了搜索引擎可替换抽象，而不是把 ES API 写死在业务中。

### 可索引模型

关键文件：

- `annotation/Indexable.java`
- `annotation/EsQueryable.java`
- `engine/model/*`

业务实体不会直接拿 JPA 实体做搜索，而是映射到专门的 `Es*` 模型，例如：

- `EsScenario`
- `EsSimulation`
- `EsInject`
- `EsFinding`

这种做法的价值在于：

- 搜索文档结构可以独立于数据库范式
- 聚合、路径分析、全局搜索不被 ORM 结构绑定

### 索引同步机制

关键文件：

- `database/audit/ModelBaseListener.java`
- `database/audit/BaseEvent.java`
- `database/audit/IndexEvent.java`
- `scheduler/jobs/EngineSyncExecutionJob.java`

核心机制是：

1. JPA 实体增删改时由 `ModelBaseListener` 发布事件
2. 删除时额外发布 `IndexEvent`
3. Quartz Job `EngineSyncExecutionJob` 周期性并行处理所有 `EsModel`
4. `IndexingStatus` 跟踪各类型的最后索引状态

这是一套明确的“数据库 -> 事件 -> 搜索索引”同步链。

## 5.4 调度系统

关键文件：

- `scheduler/PlatformJobDefinitions.java`
- `scheduler/jobs/*`

源码中可直接看到 **9 个 Quartz Job**，例如：

- `InjectsExecutionJob`
- `ComchecksExecutionJob`
- `ScenarioExecutionJob`
- `EngineSyncExecutionJob`
- `ManagerIntegrationsSyncJob`
- `SecurityCoverageJob`
- `OpenCTIConnectorRegisterPingJob`
- `ExecutionTracesBatchRequeueJob`

这说明 Veriguard 的运行模型是典型的“调度驱动型平台”，不是同步请求驱动型系统。

## 5.5 场景到演练的生成机制

关键文件：

- `scheduler/jobs/ScenarioExecutionJob.java`
- `service/scenario/ScenarioRecurrenceService`
- `service/ScenarioToExerciseService`

从源码看：

- Scenario 可以配置 recurrence
- Quartz 周期性扫描“该到点执行”的 Scenario
- 到点后自动生成 Exercise
- 过期 recurrence 会自动清理

也就是说，Scenario 更像模板或作战计划，Exercise 才是实际运行实例。

## 5.6 注入执行主链路

这一部分是 Veriguard 最关键的源码链路之一。

### 主入口

关键文件：

- `scheduler/jobs/InjectsExecutionJob.java`
- `executors/Executor.java`

`InjectsExecutionJob` 负责：

- 自动启动 Exercise
- 自动关闭 Exercise
- 处理 pending inject
- 检查依赖条件
- 最终触发 inject 执行

### 依赖条件

源码中 `InjectsExecutionJob` 明确使用：

- `InjectDependency`
- `InjectDependencyConditions`
- SpEL 表达式解析

这说明 inject 之间可以存在条件依赖，不是简单串行时间线。

### 执行分发

`executors/Executor.java` 是实际的调度分发器：

- 先拿到 `InjectorContract`
- 判断是否需要 executor
- 初始化 `InjectStatus`
- 如果是 external injector，则将 DTO 发到 RabbitMQ
- 如果是 internal injector，则通过 Integration Manager 找到对应实现并直接执行

也就是说，Veriguard 的执行路径天然分成两类：

- **外部执行**：消息投递给外部消费方
- **内部执行**：平台内集成实例直接处理

## 5.7 Executor 上下文与终端执行

关键文件：

- `execution/ExecutionExecutorService.java`
- `execution/ExecutionContextService.java`

### 执行上下文

`ExecutionContextService` 会在执行时注入：

- 当前用户
- 团队
- Exercise / Scenario 变量
- 动态生成的玩家 URL、挑战 URL、记分板 URL、Lessons URL

这说明“执行内容”不是死脚本，而是带业务上下文的动态构造结果。

### Agent / Asset 分类执行

`ExecutionExecutorService` 会对目标进行分类：

- 无 agent 的资产
- inactive agent
- 无 executor 的 agent
- CrowdStrike agent
- SentinelOne agent
- Tanium agent
- Cortex agent
- 其他常规 agent

然后分别调用：

- 批量 executor 子流程
- 单 agent executor 子流程
- 失败时写入 `ExecutionTrace`

这体现出 Veriguard 对“不同终端执行通道”的抽象已经深入到调度器级别，而不是 UI 层概念。

## 5.8 RabbitMQ 队列与批处理机制

关键文件：

- `framework/asset/QueueService.java`
- `rest/helper/queue/BatchQueueService.java`

其中：

- `QueueService` 负责基础发布与 exchange / queue / routing key 组织
- `BatchQueueService` 进一步实现生产者、消费者、worker、QoS、缓冲、重连、批处理

这说明 RabbitMQ 在 Veriguard 中不是点缀，而是核心执行总线。

## 5.9 实时事件流机制

关键文件：

- `rest/stream/StreamApi.java`
- `config/ThreadPoolTaskSchedulerConfig.java`

实现方式：

- 数据库实体变化触发 `BaseEvent`
- `StreamApi` 用 `@TransactionalEventListener` 监听
- 按用户权限过滤后，通过 SSE 向 `/api/stream` 推送
- `streamExecutor` 单独线程池负责流式事件处理

源码还特别做了：

- 定时 ping
- 队列溢出时丢弃最旧事件

说明作者明确考虑了前端长连接的背压与浏览器承载问题。

## 5.10 集成框架：Veriguard 的核心壁垒之一

关键文件：

- `integration/Integration.java`
- `integration/IntegrationFactory.java`
- `integration/Manager.java`
- `integration/ManagerFactory.java`

这是全平台最值得重点分析的部分。

### 运行方式

1. 每种集成都有一个 `IntegrationFactory`
2. Factory 负责：
   - catalog 初始化
   - 配置迁移
   - 发现数据库中的关联 `ConnectorInstance`
   - spawn 对应 Integration
3. `Manager` 维护所有运行中的 Integration 实例
4. `Manager.monitorIntegrations()` 会持续刷新、启动、停止、重启这些实例

### 状态切换机制

`Integration` 基类里处理了：

- 启动
- 停止
- 配置 hash 变化后的重启
- requested status 到 current status 的同步

这意味着 Veriguard 的“连接器实例”不是静态配置，而是平台内部可管理的运行单元。

### 已实现的集成类型

源码中可直接看到：

- Executors：
  - Caldera
  - CrowdStrike
  - SentinelOne
  - Tanium
  - Palo Alto Cortex
  - Veriguard
- Injectors：
  - Email
  - Channel
  - Manual
  - Challenge
  - OpenCTI
  - OVH
  - Veriguard

这证明 Veriguard 的产品形态是“平台 + 集成生态”，不是单体功能应用。

## 5.11 健康检查与运行治理

关键文件：

- `service/HealthCheckService.java`

健康检查会显式验证：

- Database
- RabbitMQ
- MinIO / File storage

它不是只看 Spring Boot 自身存活，而是直接探测关键运行依赖是否可用。

## 5.12 AOP 基础设施

关键文件：

- `aop/RBACAspect.java`
- `aop/lock/LockAspect.java`
- `aop/LoggingAspect.java`

其中最值得注意的是 `LockAspect`：

- 基于 Guava `Striped<Lock>`
- 支持按资源类型分条带锁
- 用 SpEL 解析锁 key
- 在事务之前执行

源码注释明确提到它是为“高并发场景，例如同一 inject 触发 10000+ implants”做的资源竞争控制。

这说明 Veriguard 在设计时已经考虑过大规模并发执行。

## 六、功能模块与源码目录的对应关系

下面是一个更适合后续对标的映射。

| 功能域 | 关键源码目录 |
| --- | --- |
| 场景管理 | `rest/scenario`、`database/model/Scenario.java` |
| 演练实例 | `rest/exercise`、`database/model/Exercise.java` |
| 注入与载荷 | `rest/inject`、`rest/payload`、`database/model/Inject.java` |
| 原子测试 | `rest/atomic_testing` |
| 资产与终端 | `rest/asset`、`database/model/Asset.java`、`Agent.java`、`Endpoint.java` |
| 连接器与执行器 | `integration/*`、`connector_instance/*` |
| 结果与发现 | `rest/finding`、`rest/vulnerability`、`database/model/Finding.java` |
| 仪表盘与报表 | `rest/dashboard`、`rest/report`、`database/model/Widget.java` |
| 实时流 | `rest/stream` |
| 权限与用户 | `security/*`、`aop/RBAC*`、`rest/user`、`rest/role` |
| 设置与治理 | `rest/settings`、`rest/organization`、`rest/tag_rule` |

## 七、关键源码结论

### 7.1 它不是简单 BAS，而是完整控制平面

从实体规模、REST 模块数量、调度数量、集成框架复杂度来看，Veriguard 不是“攻击脚本管理平台”，而是一个完整的：

- 建模平台
- 编排平台
- 执行控制平面
- 分析与治理平台

### 7.2 它的技术路线是“模块化单体 + 强异步 + 强集成”

源码最核心的三条主线是：

- **模块化单体**：业务内聚，便于快速迭代复杂产品
- **异步执行总线**：RabbitMQ + Quartz + 线程池 + 事件流
- **集成驱动能力扩展**：Factory / Manager / ConnectorInstance

### 7.3 它的底层壁垒不在某个单点功能

真正难复制的不是某个 inject 页面，而是以下组合：

- 关系库 + 搜索引擎双模数据架构
- 实体事件到索引同步
- Scenario -> Exercise -> Inject -> ExecutionTrace 的执行链
- Integration Factory / Manager 的运行时实例管理
- Agent / Executor / External Injector 的多路径执行模型
- SSE 实时推送与 RBAC 过滤

## 八、建议的后续分析方向

如果要继续深入，优先级建议如下：

1. 单独拆解 `Scenario / Exercise / Inject / ExecutionTrace` 四类核心实体关系  
2. 单独分析 `Integration` 体系，梳理如何新增一个自定义 Executor / Injector  
3. 单独分析 `Agent + ExecutorContextService + ExecutionTrace` 的终端执行链  
4. 将 Veriguard 与当前仓库目标产品做能力矩阵对标

## 九、关键源码路径

- 根模块：`/tmp/veriguard/pom.xml`
- 主应用：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/App.java`
- 安全配置：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/config/AppSecurityConfig.java`
- RBAC 切面：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/aop/RBACAspect.java`
- 锁切面：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/aop/lock/LockAspect.java`
- 调度定义：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/scheduler/PlatformJobDefinitions.java`
- 场景调度：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/scheduler/jobs/ScenarioExecutionJob.java`
- 注入调度：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/scheduler/jobs/InjectsExecutionJob.java`
- 执行分发：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/executors/Executor.java`
- Executor 上下文：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/execution/ExecutionExecutorService.java`
- 搜索引擎工厂：`/tmp/veriguard/veriguard-model/src/main/java/io/veriguard/service/EngineComponent.java`
- 索引监听：`/tmp/veriguard/veriguard-model/src/main/java/io/veriguard/database/audit/ModelBaseListener.java`
- 集成管理：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/integration/Manager.java`
- 集成工厂：`/tmp/veriguard/veriguard-api/src/main/java/io/veriguard/integration/IntegrationFactory.java`
- 队列服务：`/tmp/veriguard/veriguard-framework/src/main/java/io/veriguard/asset/QueueService.java`
- 前端根入口：`/tmp/veriguard/veriguard-front/src/root.tsx`
- 前端管理路由：`/tmp/veriguard/veriguard-front/src/admin/Index.tsx`
- 前端状态：`/tmp/veriguard/veriguard-front/src/store.ts`
