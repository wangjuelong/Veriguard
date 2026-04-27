# Veriguard 二开落地说明

## 一、目标范围

本次二开目标是将 Veriguard 改造成面向 `docs/prd/产品要求.md` 的 Veriguard 控制平面，优先补齐 PRD 可验收的管理界面、能力目录、策略模型和沙箱管理闭环。

真实流量回放、HIDS 告警采集、SOC 查询、虚拟化沙箱驱动属于外部执行与采集适配器，不在本次代码中伪造执行结果；系统通过接口和界面显式列出这些集成边界。

## 二、落地路径

| 类型 | 路径 | 说明 |
| --- | --- | --- |
| 后端 API | `veriguard-api/src/main/java/io/veriguard/rest/security_validation/` | 能力矩阵、用例目录、攻击编排策略、沙箱预设管理接口；M1 拆出 `SandboxApi` / `SandboxService` / `SandboxScriptExporter` |
| 后端驱动 | `veriguard-api/src/main/java/io/veriguard/integration/sandbox/` | M1 新增 `SandboxDriver` 接口、`NotImplementedSandboxDriver` 占位、`SandboxIntegrationException`、4 个 DTO records；为 M2 CAPEv2 接入预留 |
| 后端模型 | `veriguard-model/src/main/java/io/veriguard/database/model/VeriguardSandbox.java` | 沙箱预设实体（网络策略、样本类型、自动还原开关）；M1 收窄移除 `endpoint`/`providerType`，凭据改走配置 |
| 数据迁移 | `veriguard-api/src/main/java/io/veriguard/migration/V4_72__Add_veriguard_sandbox.java` | 新增 `veriguard_sandboxes` 表与查询索引 |
| 数据迁移 | `veriguard-api/src/main/java/io/veriguard/migration/V4_73__Extend_veriguard_sandbox.java` | M1 新增：删除 `endpoint`/`provider_type` 列、增加 `uk_veriguard_sandboxes_name` 唯一约束 |
| 前端入口 | `veriguard-front/src/admin/components/veriguard/VeriguardConsole.tsx` | Veriguard 管理控制台；M1 沙箱 Tab 由 `SandboxList` 接管 |
| 前端组件 | `veriguard-front/src/admin/components/veriguard/sandbox/` | M1 新增：`SandboxList`、`SandboxDialog`、`NetworkRuleEditor`、`DeleteConfirmDialog`、`utils/cidr-port-validators` |
| 前端路由 | `veriguard-front/src/admin/Index.tsx` | 新增 `/admin/veriguard` 路由 |
| 前端导航 | `veriguard-front/src/admin/components/nav/LeftBar.tsx` | 左侧菜单新增 Veriguard 入口 |

## 三、PRD 映射

| PRD 模块 | 已落地内容 | 仍需外部适配 |
| --- | --- | --- |
| 2.1 流量安全验证 | 流量攻击类型目录、300+ 用例模板容量、多四元组模板能力、边界覆盖度与稳定性指标控制项 | 流量回放引擎、NTA / IDS 事件采集 |
| 2.2 应用与服务器安全验证 | 主机攻击类型目录、HIDS 验证分类、Atomic testing / Payload / Agent 映射控制项 | HIDS 告警采集与厂商适配 |
| 2.3 自定义验证 | 6 类自定义用例类型、批量导入入口、ATT&CK 与纵深防御维度、动态筛选场景模型 | PCAP 解析器、Web 攻击包构造执行器 |
| 2.4 攻击编排 | 节点延迟、重复、间隔、条件执行、SOC 规则匹配字段、链路级结果状态 | SOC 查询适配器 |
| 2.5 沙箱管理 | 沙箱平台新建、编辑、删除、网络访问控制策略、恶意样本类型、自动还原强制校验 | 虚拟化 / 容器沙箱驱动 |

## 四、接口清单

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/capabilities/matrix` | 查询 PRD 能力矩阵 |
| `GET` | `/api/attack-use-cases/catalog` | 查询攻击类型、自定义用例类型和生成模板 |
| `GET` | `/api/attack-orchestration/schema` | 查询攻击编排节点策略、执行模式、SOC 匹配字段和链路结果 |
| `GET` | `/api/sandboxes` | 查询沙箱平台列表 |
| `POST` | `/api/sandboxes` | 新建沙箱平台 |
| `GET` | `/api/sandboxes/{sandboxId}` | 查询单个沙箱平台 |
| `PUT` | `/api/sandboxes/{sandboxId}` | 更新沙箱平台 |
| `DELETE` | `/api/sandboxes/{sandboxId}` | 删除沙箱平台 |
| `GET` | `/api/sandboxes/{sandboxId}/network-rules/exports/iptables` | M1 新增：导出沙箱网络规则的 iptables 脚本（`text/plain`，含 `Content-Disposition`） |
| `GET` | `/api/sandboxes/{sandboxId}/network-rules/exports/routing-conf` | M1 新增：导出沙箱网络规则的 CAPEv2 routing.conf 片段 |

## 五、验收要点

| 验收项 | 验收方式 |
| --- | --- |
| 管理台入口 | 登录管理端后访问 `/admin/veriguard`，左侧菜单显示 Veriguard |
| 能力矩阵 | “能力矩阵”页展示 2.1 到 2.5 五个 PRD 模块、控制项和适配器边界 |
| 用例目录 | “用例目录”页展示流量与主机攻击类型，模板总量不低于 300，攻击类型不少于 10 种 |
| 攻击编排 | “攻击编排”页展示节点策略字段、执行模式、SOC 匹配字段和链路结果 |
| 沙箱管理 | “沙箱管理”页支持新建、编辑、删除沙箱平台，并配置网络访问控制策略 |
| 自动还原 | 沙箱表单关闭“执行完成后自动还原”时不能保存，后端也会拒绝该输入 |

## 六、检查记录

| 命令 | 结果 |
| --- | --- |
| `git diff --check -- docs AGENTS.md veriguard-api veriguard-model veriguard-front` | 通过 |
| `corepack yarn install --immutable` | 通过，存在仓库已有 peer dependency warning |
| `corepack yarn check-ts` | 通过（M1 后仅剩 `AtomicTesting.tsx` 等无关模块的预存错误） |
| `mvn -pl veriguard-api -am compile -DskipTests` | 当前环境 Maven 依赖解析遇到 `co.elastic.clients:elasticsearch-java:8.19.14` 镜像 `502 Bad Gateway`，未进入源码编译 |
| `mvn -pl veriguard-api -am test -Dtest='NotImplementedSandboxDriverTest,SandboxIntegrationExceptionMappingTest,SandboxServiceTest,SandboxScriptExporterTest' -Dsurefire.failIfNoSpecifiedTests=false` | M1 后端单元测试 4+2+5+8=19 PASS |
| `cd veriguard-front && yarn vitest run src/admin/components/veriguard/sandbox` | M1 前端组件 + 工具单元测试 5+5+19=29 PASS |
| `mvn -pl veriguard-api -am test -Dtest=SandboxApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false` | M1 集成测试编译通过；运行时验证待 dev stack 启动后回归（依赖 V4_73 迁移与 Postgres 实例） |
