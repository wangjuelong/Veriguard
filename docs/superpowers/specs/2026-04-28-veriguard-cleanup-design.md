# Veriguard 二开冗余裁剪设计

> 状态：设计完成，待 review。下一步交由 superpowers:writing-plans 生成实施计划。
> 日期：2026-04-28

## 一、Understanding Summary

- **做什么**：把 Veriguard（fork 自 OpenBAS）裁剪成只服务 PRD 五大模块（流量验证 / 主机验证 / 自定义验证 / 攻击编排 / 沙箱）的二开版本，移除原项目的非 PRD 功能、数据库历史包袱与 Filigran/OpenBAS 品牌归属。
- **为什么**：满足 PRD 中"二开"目标，让仓库代码、UI、文档、DB schema 都收敛到二开真正需要的范围；后续维护负担最小化。
- **谁用**：单人维护；私有部署；不对外分发。
- **核心约束**：
  - 沙箱 M1 已落地能力（`rest/security_validation/`、`integration/sandbox/`、`VeriguardSandbox`、V4_72/V4_73 migration、前端 `admin/components/veriguard/sandbox/`）**全程不动**。
  - 认证主路径（admin token + JWT 登录）不能断。
  - 每个 phase 完成做 (a) 编译 + compose 起来 + (b) 沙箱 CRUD smoke + (c) 受影响模块 test，硬 gate；Phase 14 final 增加 (d) 完整 e2e。
  - 仓库私有，不对外分发——LICENSE 可删。
- **明确非目标**：
  - 不补任何新功能。
  - 不重写底层（Spring Security / RBAC / Flyway / RabbitMQ / MinIO / ES）。
  - 不实现 PRD 中"待外部适配器"内容（流量回放 / HIDS / SOC / 沙箱驱动等）。
  - 不为已有 OpenBAS 老库提供平滑升级路径。
  - 不做 metrics/telemetry 替代方案。
  - 不在本次范围内重建 CI 工作流。

## 二、Assumptions

- **A1** 当前 docker compose 部署是空 DB 状态，可以接受 Flyway baseline 重置。
- **A2** 仓库永远不对外分发，删 LICENSE 没法律风险。
- **A3** 接受单分支 `chore/cleanup` 14 个 commit 一次合（不分多个 PR）。
- **A4** 每个 phase 验证 (c) 后端 test 套件因为删测试反而变快，不会反向破坏。
- **A5** 被砍模块的 Flyway migration 文件可以连同 Phase 11 重置一起删，期间不需要"幽灵表"留作兼容。
- **A6** 保留的 EDR executors（caldera/crowdstrike/paloaltocortex/sentinelone/tanium）虽然依赖 EE License，Phase 8 砍 EE 后**只剥离 license 校验那几行**让 executors 仍然可用。
- **A7** Email Injector 删除后，SmtpService 用作 admin 通知基础设施保留，PlatformSettingsService 中的 SMTP 配置入口保留。
- **A8** starter pack 内置 zip 砍掉后，`starterpack/scenarios/` 目录留空，import 框架（`V20260101_Starter_pack`、`ImportService`）保留。
- **A9** 保留 zh + en 两个 i18n 文件，UI 提供语言切换。
- **A10** 项目仓库中文化已基本完成，只需在裁剪过程中同步删除 EE/XTM/AI/OVH/OpenCTI 相关 i18n key。
- **A11** 场景 / 模拟组 / 自动测试 / 资产组 / 人员（Users/Teams/Players）/ 载荷 全部 100% 保留，所有 CRUD / 列表 / 详情 / 关联关系都在。

## 三、Decision Log

| # | 决策 | 选项 | 理由 |
|---|---|---|---|
| D1 | 裁剪尺度 | B（砍模块 + 抹品牌） | 重度（C）超出当前需求 |
| D2 | 后端模块清单 | 1-8 砍；9/11/12/15 留；10 删 injector 留 SMTP；13/14 砍 zip 留框架 | 沿 PRD 五大模块边界 |
| D3 | 前端裁剪尺度 | C（彻底删 + Experience/getting_started 整块下线） | 仓库最干净 |
| D4 | 数据库 | C（删历史 migration + 重置 Flyway baseline） | 全新部署，无需平滑升级 |
| D5 | i18n | zh + en | PRD 中文，保留 en 备演示 |
| D6 | LICENSE / 品牌 | 完全独立（删 LICENSE / CONTRIBUTING / SECURITY，不标注 OpenBAS 二开） | 私有不分发 |
| D7 | 阶段化 | C（按模块 14 phase 细粒度） | review 容易，回滚便宜 |
| D8 | 验证基准 | (a)+(b)+(c) 每 phase；(d) Phase 14 | 节奏可控 |
| D9 | 回滚锚 | 打 `pre-cleanup` tag | 便宜安全 |
| D10 | 模块顺序 | InjectAssistant→XTM→OpenCTI→OVH→Channel/Challenge→Email→Telemetry→EE→starter pack→i18n→Flyway→品牌→pom→Final | 依赖反图，从叶到根 |
| D11 | 测试随删 | 删模块同时删对应 unit/integration test | 减包袱 |
| D12 | pom.xml 依赖 | Phase 13 统一砍 | 一次到位 |
| D13 | 分支策略 | ii（单分支 `chore/cleanup`，14 commits 一次合） | 集中管理 |
| D14 | Flyway 重置方案 | A（pg_dump -s 导出当前 schema 成 V1__Init.sql） | 仓库最干净；新部署 only |
| D15 | EE 剥离方案 | A（引用方全删 EE 分支，永远走 community 路径） | 仓库无遗留代码 |
| D16 | 认证链拆 OpenCTI | A（删 JwtExtractor，错误类型替换为通用类型） | 干净，对应 Q10.3 |
| D17 | 验证流水线 | A（hard gate=编译+compose+sandbox smoke+受影响模块 test；Phase 14 加 e2e） | 节奏可控，"尽可能跑全"语义满足 |
| D18 | Phase 12 抹除范围 | 删 LICENSE/CONTRIBUTING/SECURITY/AGENTS/copilot-instructions/.github/workflows；logo 用 SVG 文字占位；其余 ~50 处字符串替换 | 一次性扫干净 |
| D19 | Commit 规范 | 中文 `裁剪：<动作>`，附 `Co-Authored-By` | 沿用 CLAUDE.md 风格 |

## 四、Final Design

### 4.1 总体架构

```
Phase 0  打 tag pre-cleanup                    (回滚锚)
Phase 1  裁剪 InjectAssistant                  (孤儿)
Phase 2  裁剪 XTM Hub                          (低耦合，含前端 Experience tab + Banner + GettingStarted 关联段)
Phase 3  裁剪 OpenCTI                          (含拆 Token 链)
Phase 4  裁剪 OVH Injector
Phase 5  裁剪 Channel + Challenge Injector     (一组)
Phase 6  裁剪 Email Injector                   (保 SmtpService)
Phase 7  裁剪 Telemetry                        (深嵌入)
Phase 8  裁剪 EE / License                     (最大头，剥 5 EDR executor 中 license 校验)
Phase 9  清空 starter pack zip + xtmhub-scenarios test resources
Phase 10 i18n 收缩到 zh+en + 清理残留 key
Phase 11 Flyway baseline 重置至 V1__Init.sql
Phase 12 品牌抹除（README 重写、删归属文件、SVG 占位 logo、邮件模板、字符串替换）
Phase 13 pom.xml 依赖清理
Phase 14 Final 全量验证 + 修复 patch
```

### 4.2 各 Phase 触达组件

#### Phase 1 — InjectAssistant
- **后端删**：`rest/inject/form/InjectAssistantInput.java`、`rest/inject/service/InjectAssistantService.java`、`rest/inject/InjectAssistantApi.java`（如有）
- **前端删**：`actions/AskAI.js`、`utils/ai/ResponseDialog.tsx`、`common/form/TextFieldAskAI.tsx`，以及业务组件中所有 `<TextFieldAskAI>` 引用替换为 `<TextField>`
- **测试删**：对应 `*Assistant*Test.java`
- **i18n**：标记相关 key 待 Phase 10 清理

#### Phase 2 — XTM Hub
- **后端删**：`xtmhub/`（整目录，含 `XtmHubApi`、`XtmHubService`、`XtmHubClient`、`XtmHubEmailService`、`XtmHubConnectivityService`、`xtmhub/collector/`、`xtmhub/config/`）；`api/xtmhub/XtmHubApi.java`；`PlatformSettingsService` 中 XtmHub 字段
- **前端删**：`actions/xtmhub/`、`utils/hooks/useXtmHubUserPlatformToken.ts`、`utils/hooks/useXtmHubDownloadDocument.ts`、`utils/xtm-hub-client.ts`、`public/components/trialbanners/StartTrialBanner.tsx`、`public/components/trialbanners/LicenseBanner.tsx`、`admin/components/getting_started/`（D3=C 整目录下线）、`admin/components/settings/experience/xtm_hub/`、`admin/components/settings/experience/Experience.tsx`、`admin/components/settings/experience/EnterpriseEditionSettings.tsx`；`root.tsx`、`useAuth.ts`、`Theme.ts` 中相关 import；`admin/components/common/ImportFromHubButton.tsx`
- **dev 环境**：`veriguard-dev/docker-compose.yml` 中 `xtm-composer` 服务段
- **测试删**：`Xtm*Test.java`
- **菜单/路由删**：去掉 Experience、GettingStarted 入口

#### Phase 3 — OpenCTI
- **后端删**：`opencti/`（整目录）、`injectors/opencti/`、`integration/impl/injectors/opencti/`、`scheduler/jobs/OpenCTIConnectorRegisterPingJob.java`、`scheduler/jobs/SecurityCoverageJob.java`、`api/stix_process/StixApi.java`、`service/stix/SecurityCoverageService.java`、`service/stix/StixService.java`、`img/icon-opencti.png`
- **拆 Token 链**：删 `security/token/JwtExtractor.java`；`security/token/ExtractorBase.java` 把 `import io.veriguard.opencti.errors.ConnectorError` 替换为本地 `AuthenticationException`；`security/TokenAuthenticationFilter.java` 同步替换
- **PlatformSettingsService**：删 OpenCTI 字段
- **前端删**：`opencti` 引用（已扫确认前端无活跃组件，仅 `api-types.d.ts` 有遗留类型，Phase 14 重生成时自然消失）
- **测试删**：`OpenCTI*Test.java`、`Stix*Test.java`、`SecurityCoverage*Test.java`

#### Phase 4 — OVH
- **后端删**：`injectors/ovh/`、`integration/impl/injectors/ovh/`、`integration/migration/OvhInjectorConfigurationMigration.java`
- **解耦**：`rest/injector_contract/InjectorContractService.java` 中 OVH 注入器注册段、`rest/inject/output/InjectOutput.java` 中 OVH 引用
- **测试删**：`Ovh*Test.java`

#### Phase 5 — Channel + Challenge
- **后端删**：`integration/impl/injectors/channel/`、`integration/impl/injectors/challenge/`、`injectors/channel/`、`injectors/challenge/`
- **测试删**：相关 `*Test.java`
- **注意**：这俩 injector 复用 EmailInjectorIntegration，必须在 Phase 6 之前删，否则 Phase 6 删 Email 时会触动 Channel/Challenge

#### Phase 6 — Email Injector（保 SmtpService）
- **后端删**：`integration/impl/injectors/email/EmailInjectorIntegration.java`、`EmailInjectorIntegrationFactory.java`、`injectors/email/EmailInjector.java`（如有）、`injectors/email/service/EmailService.java`（业务执行层）
- **保留**：`injectors/email/service/SmtpService.java`（admin 通知基础设施）、`scheduler/jobs/ComchecksExecutionJob.java`（重写不再调 EmailInjector，改走 SmtpService 通知层）、`service/MailingService.java`、`integration/Manager.java` 中 Email injector 注册段、`rest/inject/service/InjectService.java`、`PlatformSettingsService` 中 SMTP 配置
- **测试删**：`EmailInjector*Test.java`，保留 `Smtp*Test.java`、`Mailing*Test.java`

#### Phase 7 — Telemetry
- **后端删**：`telemetry/`（整目录，含 `OpenTelemetryConfig`、`PyroscopeConfig`、`PyroscopePropertiesConfig`、`CustomMetricReader`、`metric_collectors/*`）、`config/AppPyroscopeConfig.java`
- **去打点**：8 处核心 service / job：`importer/V1_DataImporter.java`、`rest/custom_dashboard/WidgetService.java`、`scheduler/jobs/InjectsExecutionJob.java`、`service/AtomicTestingService.java`、`service/scenario/ScenarioService.java`、`rest/exercise/service/ExerciseService.java`、`executors/Executor.java` —— 删掉 metric.increment / counter.add 等调用行
- **dev 环境**：`veriguard-dev/docker-compose.yml` 中 pyroscope/otlp 注释段；删 `veriguard-dev/otlp-config.yaml`
- **测试删**：`Telemetry*Test.java`、`Metric*Test.java`

#### Phase 8 — EE / License
- **后端删**：`ee/`（`Ee.java`、`License.java`、`LicenseTypeEnum.java`、`Pem.java`）、`utils/LicenseUtils.java`、`config/cache/LicenseCacheManager.java`、`rest/exception/LicenseRestrictionException.java`
- **去 EE 分支**（25+ 引用点）：每处把 `if (LicenseUtils.isEEEnabled()) { ... } else { ... }` 简化为只保留 community 分支；删 `LicenseRestrictionException` throw 点；具体清单含：
  - `rest/settings/response/PlatformSettings.java`
  - `rest/vulnerability/service/VulnerabilityService.java`
  - `rest/helper/RestBehavior.java`（核心 base class，必须仔细 review）
  - `service/detection_remediation/DetectionRemediationAIService.java`（含 AI 逻辑，多半整文件删）
  - `rest/inject/service/InjectService.java`
  - `utils/mapper/PayloadMapper.java`、`VulnerabilityMapper.java`、`CveMapper.java`、`ExecutorMapper.java`
  - `service/scenario/ScenarioService.java`
  - `rest/exercise/service/ExerciseService.java`
  - `service/PlatformSettingsService.java`
  - `service/connectors/ConnectorOrchestrationService.java`
  - 4 个 EDR Executor service：`paloaltocortex/`、`crowdstrike/`、`sentinelone/`、`tanium/`（剥 license 校验保留执行逻辑）
  - 4 个 Executor Integration Factory（同上）
- **前端删**：`components/EnterpriseEditionContext.ts`、`components/EnterpriseEditionProvider.tsx`、`utils/hooks/useEnterpriseEdition.ts`、`admin/components/common/entreprise_edition/`（整目录）、`admin/components/settings/experience/EnterpriseEditionSettings.tsx`（已在 Phase 2 删）
- **去 EE 装饰**：业务组件中 `<EEChip>`、`<EETooltip>`、`<EnterpriseEditionButton>`、`<EnterpriseEditionAgreementDialog>` 全删；触达 `findings/FindingDetail.tsx`、`agents/Agents.tsx`、`agents/ExecutorDocumentationLink.tsx`、`settings/Parameters.tsx`、`settings/vulnerabilities/TabLabelWithEE.tsx`、`settings/vulnerabilities/VulnerabilityDetail.tsx`、`payloads/Loader.tsx`、`payloads/form/DetectionRemediationUseAriane.tsx`、`payloads/form/DetectionRemediationInfo.tsx`、`common/form/TextFieldAskAI.tsx`（已在 Phase 1 删）
- **风险**：community 分支可能在某些场景返回空/默认；Phase 8 review 时确认不破 PRD 五大模块

#### Phase 9 — Starter pack zip + xtmhub-scenarios test resources
- **删**：`veriguard-api/src/main/resources/starterpack/scenarios/scenario-Akira_Ransomware_Crisis.zip`、`scenario-CVE_TTPs_validation.zip`、`scenario-EASM.zip`
- **删**：`veriguard-api/src/test/resources/xtmhub-scenarios/`（整目录）
- **保留**：`V20260101_Starter_pack.java`、`ImportService.java` 框架代码（A8 假设）；启动时无 zip 可导，`Failed to import` ERROR 日志会消失

#### Phase 10 — i18n 收缩
- **删**：`veriguard-front/src/utils/lang/de.json`、`es.json`、`fr.json`、`it.json`、`ja.json`、`ko.json`、`ru.json`（7 个）
- **保留 + 清理**：`zh.json`、`en.json` —— 扫一遍 key，把对应 EE/XTM Hub/AI Assistant/OpenCTI/OVH/Filigran/Telemetry 的 key 全删
- **i18n loader**：把语言列表常量从 9 改成 2

#### Phase 11 — Flyway baseline 重置
- **导出当前 schema**：`docker compose exec veriguard-pgsql pg_dump -s -U veriguard veriguard > V1__Init.sql`，整理后放入 `veriguard-api/src/main/resources/db/migration/V1__Init.sql`
- **删旧 Java migrations**：`veriguard-api/src/main/java/io/veriguard/migration/V*.java` 全部删除（约 60+ 个）
- **保留**：Flyway 的 application.properties 配置；如有 `db/migration/V*.sql` 也保留
- **沙箱迁移融入 V1**：V4_72/V4_73 的 schema 已经在 pg_dump 输出里，自然包含
- **风险**：要确认 V1__Init.sql 在干净 PG 上能完整建出 schema；Phase 11 验证 (a) 时强制 `docker compose down -v` 清空 volume 重启验证

#### Phase 12 — 品牌抹除
- **删除**：`LICENSE`、`CONTRIBUTING.md`、`SECURITY.md`、`AGENTS.md`、`.github/copilot-instructions.md`、`.github/workflows/`（整目录）、`.github/img/logo_filigran.png`、`veriguard-front/src/static/images/logo_filigran_*.png`（4 张）
- **重写**：`README.md`（纯二开项目介绍，不提 OpenBAS）、邮件模板（`generic_template_en.html`、`notification_template_scenario_difference_en.html`）logo URL + 页脚
- **SVG 占位 logo**：用纯文字 SVG 替换 `logo_dark.png` / `logo_light.png` / `logo_text_dark.png` / `logo_text_light.png`（如果当前图片已经显示 Veriguard 文字也可保留——Phase 12 review 时定）
- **代码字符串替换**：约 50 处剩余 "Filigran" 字样：
  - 配置：`AppConfig.java`、`VeriguardConfig.java`、`VeriguardAdminConfig.java`、`RabbitmqConfig.java`、`ExpectationPropertiesConfig.java`、`application.properties`
  - 服务：`EndpointService.java`、`HealthCheckApi.java`、`InitAdminCommandLineRunner.java`、`BatchQueueService.java`、`ExecutorHelper.java`
  - 模型：`SettingKeys.java`、`ExternalServiceDependency.java` 残留 openaev 兼容
  - 前端：`Login.tsx`、`LeftBar.tsx`、`package.json` (name/description)、`index.html` (title)、`Theme.ts`/`ThemeLight.ts`/`ThemeDark.ts`
- **保留**：`veriguard-dev/docker-compose.yml`（D6 决定）

#### Phase 13 — pom.xml 依赖清理
- **砍**（根 `pom.xml` + `veriguard-api/pom.xml`）：
  - `io.opentelemetry:*`（Telemetry 依赖）
  - `io.pyroscope:agent`（Pyroscope agent）
  - OpenCTI / STIX 客户端 jar（如 `org.opencti.*` 或 `com.filigran.opencti`）
  - OVH SDK（`com.ovh:*` 如有）
  - XTM Hub 相关 SDK（如有）
  - 其他被 Phase 1-8 移除模块独占的依赖
- **保留**：Spring Boot、Hibernate、Flyway、RabbitMQ、MinIO、Elasticsearch、Caldera 相关、Spotless 工具链
- **验证**：`mvn -pl veriguard-api -am dependency:tree` 确认无遗留

#### Phase 14 — Final 验证
- (a) 全栈编译：`mvn install -DskipTests -Pdev` + `cd veriguard-front && yarn install && yarn build`
- (a) 镜像 build：`DOCKER_BUILDKIT=0 docker build -t veriguard-app:dev .`
- (a) 干净启动：`docker compose down -v && docker compose up -d`
- (b) Smoke：登录 + 沙箱 CRUD 主路径
- (c) 后端全量 test：`mvn -pl veriguard-api test`
- (c) 前端 unit test：`yarn test`
- (d) e2e：`yarn playwright test`，重点 sandbox m1 spec 通过
- 重生成 OpenAPI 类型：`yarn generate-types-from-api`，提交 `veriguard-front/src/utils/api-types.d.ts` 增量
- 任何遗留问题落到额外修复 commit

### 4.3 验证流水线（每 Phase）

| Gate | 命令 | 通过条件 |
|---|---|---|
| (a-1) 后端编译 | `mvn -pl veriguard-api -am compile -DskipTests` | exit 0 |
| (a-2) 前端编译 | `cd veriguard-front && yarn check-ts && yarn lint` | exit 0 |
| (a-3) 镜像启动 | `docker compose up -d --build` | 全部容器 healthy |
| (b) Sandbox smoke | curl 登录 + GET/POST/DELETE `/api/sandboxes` | 200/201/204 |
| (c) 受影响模块 test | `mvn -pl veriguard-api test -Dtest='*<module>*,*Sandbox*'` | 通过 |

每 Phase 全部通过才能 commit。失败回滚到上一个 commit，调整后重试。

### 4.4 回滚策略

- Phase 0 打 `pre-cleanup` tag，保留永久。
- 任何 phase 失败：`git reset --hard HEAD~1`（如已 commit）或 `git restore .`（未 commit）回到上一稳定点。
- Phase 14 全失败：`git reset --hard pre-cleanup` 整个回滚。
- 合入 main 后：`pre-cleanup` tag 仍可作为长期参考。

### 4.5 风险与已知边缘情况

| 风险 | Phase | 缓解 |
|---|---|---|
| Flyway baseline 在干净 PG 上重建失败 | 11 | Phase 11 验证强制 `down -v` 重启；如失败回滚，重新 `pg_dump -s` 整理 |
| EE 剥离破坏 community 默认行为 | 8 | Phase 8 review 每处 `if isEEEnabled` 的 else 分支语义；smoke 包含全部沙箱主路径 |
| 删 OpenCTI 触动认证主链 | 3 | 提前确认 admin/JWT 登录走 `PlainTokenExtractor`；Phase 3 smoke 含 `/api/login` |
| 删 EmailInjector 影响通知 | 6 | 保留 SmtpService、MailingService；Phase 6 验证含一次"忘密邮件触发" smoke |
| api-types.d.ts 累积过期 | 1-13 | Phase 14 重生成统一更新 |
| pom.xml 砍依赖意外破坏保留模块 | 13 | Phase 13 验证 (a)+(b)+(c) 全跑；逐个删验证 |
| Telemetry 打点删除遗漏 | 7 | grep `metric.|counter.|histogram.|opentelemetry|pyroscope` 全仓扫净 |

### 4.6 Commit 规范

```
裁剪：<模块> <动作摘要>

<2-4 行可选 body>

Co-Authored-By: Claude <noreply@anthropic.com>
```

14 phase 对应的标准 commit message 见 §设计 §6（已锁定，详细列表略）。

### 4.7 分支模型

```
main
 │
 ├── (tag pre-cleanup)
 │
 └── chore/cleanup
      ├── commit Phase 1
      ├── commit Phase 2
      ├── ...
      ├── commit Phase 13
      └── commit Phase 14 (修复 patch，如有)

合并：chore/cleanup → main（PR 形式或本地 fast-forward）
合并后：删 chore/cleanup 分支，保留 pre-cleanup tag
```

## 五、未尽事宜（写 plan 阶段确认）

- 每个 Phase 受影响模块 test 的具体 `-Dtest=` 模式
- pg_dump 导出的 V1__Init.sql 整理细节（去 timestamp、去 owner、统一格式化）
- 各 phase 在 EditMode 下的 git commit 命令编排（保证每个 commit 自洽）
- Phase 14 final commit message 命名（`修复：Final 验证清理` 或合并到 13 之前的 commit？）

## 六、下一步

写 implementation plan：`docs/superpowers/plans/2026-04-28-veriguard-cleanup.md`，把上述 14 phase 拆成 TDD 风格的可执行步骤。
