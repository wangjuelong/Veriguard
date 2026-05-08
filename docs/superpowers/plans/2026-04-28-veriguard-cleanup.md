# Veriguard 二开冗余裁剪 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Veriguard 仓库裁剪成只服务 PRD 五大模块的二开版本，移除 OpenCTI/XtmHub/EE/Telemetry/AI Assistant/OVH/Channel/Challenge/Email Injector 等非 PRD 模块；i18n 收缩到 zh+en；Flyway baseline 重置；品牌资产抹除；同时全程保活沙箱 M1 与认证主路径。

**Architecture:** 单分支 `chore/cleanup` × 14 个独立 commit；每个 phase 一个 commit + (a) 编译/(b) 沙箱 smoke/(c) 受影响模块 test 三层 hard gate；Phase 14 final 加 (d) e2e。模块顺序按依赖反图从叶到根：`InjectAssistant → XTM → OpenCTI → OVH → Channel/Challenge → Email → Telemetry → EE → starter pack → i18n → Flyway → 品牌 → pom → Final`。

**Tech Stack:** Spring Boot 3.3.7 / Java 21 / Maven / Flyway / PostgreSQL 17 / React 19 / TypeScript / Vite / Yarn 4 / Docker Compose / Playwright

**Spec:** `docs/superpowers/specs/2026-04-28-veriguard-cleanup-design.md`

---

## File Structure 决策摘要

裁剪不创建新业务文件；新增的只有：
- `veriguard-api/src/main/resources/db/migration/V1__Init.sql` — Phase 11 新生成的 Flyway baseline，由 `pg_dump -s` 导出整理
- 占位 SVG logos（`veriguard-front/src/static/images/logo_dark.svg` 等）— Phase 12

主要操作是**删除文件**和**修改引用**：
- 后端整目录删除：`opencti/`、`xtmhub/`、`telemetry/`、`ee/`、`injectors/opencti/`、`injectors/ovh/`、`injectors/channel/`、`injectors/challenge/`、`migration/V*.java`（272 个文件）
- 前端整目录/文件删除：`actions/xtmhub/`、`utils/xtm-hub-client.ts`、`utils/hooks/useXtm*.ts`、`utils/ai/`、`actions/AskAI.js`、`admin/components/common/form/TextFieldAskAI.tsx`、`admin/components/common/entreprise_edition/`、`components/EnterpriseEdition*`、`utils/hooks/useEnterpriseEdition.ts`、`admin/components/getting_started/`、`admin/components/settings/experience/`、`public/components/trialbanners/`、`utils/lang/{de,es,fr,it,ja,ko,ru}.json`
- 顶层删除：`LICENSE`、`CONTRIBUTING.md`、`SECURITY.md`、`AGENTS.md`、`.github/copilot-instructions.md`、`.github/workflows/`、`.github/img/logo_filigran.png`、`veriguard-front/src/static/images/logo_filigran_*.png`

---

## Task 0: Phase 0 — 打 pre-cleanup tag + 拉 chore/cleanup 分支

**Files:** （无文件改动，仅 git 操作）

- [ ] **Step 1: 确认当前分支干净**

```bash
cd /Users/lamba/github/Veriguard
git status --porcelain
```

Expected: 输出为空，工作树干净。如有未提交改动先 stash 或处理。

- [ ] **Step 2: 切到 main 并拉到最新**

```bash
git checkout main
git pull --ff-only
```

Expected: `Already up to date.` 或 fast-forward。

- [ ] **Step 3: 打 pre-cleanup tag**

```bash
git tag pre-cleanup
git tag -l pre-cleanup
```

Expected: 输出 `pre-cleanup`。

- [ ] **Step 4: 拉 chore/cleanup 分支**

```bash
git checkout -b chore/cleanup
git branch --show-current
```

Expected: 输出 `chore/cleanup`。

- [ ] **Step 5: 不产生 commit，进入 Task 1**

确认分支位于 `chore/cleanup`，HEAD 与 main 重合。

---

## Task 1: Phase 1 — 移除 InjectAssistant AI 助手模块

**Files:**
- Delete: `veriguard-api/src/main/java/io/veriguard/rest/inject/form/InjectAssistantInput.java`
- Delete: `veriguard-api/src/main/java/io/veriguard/rest/inject/service/InjectAssistantService.java`
- Delete: `veriguard-front/src/actions/AskAI.js`
- Delete: `veriguard-front/src/utils/ai/ResponseDialog.tsx`
- Delete: `veriguard-front/src/utils/ai/`（整目录）
- Delete: `veriguard-front/src/admin/components/common/form/TextFieldAskAI.tsx`
- Modify: `veriguard-front/src/components/fields/OldTextField.jsx`、`SimpleRichTextField.jsx`、`TextField.jsx`、`OldRichTextField.jsx`、`MarkDownFieldController.tsx`、`OldMarkDownField.tsx`、`RichTextField.tsx` —— 删除 `<TextFieldAskAI>` 引用
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/inject/InjectApi.java` 等可能引用 `InjectAssistantService` 的 controller — 搜后处理

- [ ] **Step 1: 列出 InjectAssistant 当前测试覆盖**

```bash
cd /Users/lamba/github/Veriguard
rg -l 'InjectAssistant' veriguard-api/src/test/ veriguard-front/src/ 2>/dev/null
```

Expected: 列出所有相关测试文件路径。这些测试将连同模块一并删除。

- [ ] **Step 2: 找出 InjectAssistantService 的引用方**

```bash
rg -l 'InjectAssistantService\|InjectAssistantInput\|@PostMapping.*assistant' veriguard-api/src/main/java/ 2>/dev/null
```

Expected: 列出引用文件，可能含 InjectApi.java 中一个 `@PostMapping("/assistant")` 端点。

- [ ] **Step 3: 删除后端两个文件 + 引用方端点**

```bash
rm veriguard-api/src/main/java/io/veriguard/rest/inject/form/InjectAssistantInput.java
rm veriguard-api/src/main/java/io/veriguard/rest/inject/service/InjectAssistantService.java
```

如 InjectApi.java 中有 `requestAssistant`/`generateInject` 等端点引用 InjectAssistantService，用 Edit 工具删除该方法和相关 import。

- [ ] **Step 4: 删除 InjectAssistant 的后端测试**

```bash
find veriguard-api/src/test -name '*InjectAssistant*' -delete
```

如有其他 unit/integration test 引用 InjectAssistant 的方法，用 Edit 工具去掉对应 test 方法。

- [ ] **Step 5: 删除前端 AskAI 相关文件**

```bash
rm veriguard-front/src/actions/AskAI.js
rm veriguard-front/src/admin/components/common/form/TextFieldAskAI.tsx
rm -rf veriguard-front/src/utils/ai
```

- [ ] **Step 6: 修改前端 fields 组件，移除 TextFieldAskAI 引用**

对于以下每个文件，用 Edit 工具删除 `import TextFieldAskAI` 行和 JSX 中 `<TextFieldAskAI ... />` 标签：

```
veriguard-front/src/components/fields/OldTextField.jsx
veriguard-front/src/components/fields/SimpleRichTextField.jsx
veriguard-front/src/components/fields/TextField.jsx
veriguard-front/src/components/fields/OldRichTextField.jsx
veriguard-front/src/components/fields/MarkDownFieldController.tsx
veriguard-front/src/components/fields/OldMarkDownField.tsx
veriguard-front/src/components/fields/RichTextField.tsx
```

- [ ] **Step 7: 验证后端编译**

```bash
cd /Users/lamba/github/Veriguard
mvn -pl veriguard-api -am compile -DskipTests -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 8: 验证前端 ts/lint 通过**

```bash
cd /Users/lamba/github/Veriguard/veriguard-front
yarn check-ts
yarn lint
```

Expected: 0 error。

- [ ] **Step 9: 启动全栈，检查健康**

```bash
cd /Users/lamba/github/Veriguard
docker compose up -d --build
docker compose ps
```

Expected: 所有 5 个服务 `healthy` / `Up`。

- [ ] **Step 10: 沙箱主路径 smoke**

```bash
TOKEN=$(curl -s -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' | jq -r '.user_id')
echo "logged in user: $TOKEN"

curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"

CREATED=$(curl -sb /tmp/vg-cookies.txt -X POST http://localhost:8080/api/sandboxes \
  -H 'Content-Type: application/json' \
  -d '{"name":"smoke-phase1","networkRules":[],"sampleType":"WINDOWS_PE","autoRevert":true}' \
  -w "\nPOST=%{http_code}\n")
echo "$CREATED"
ID=$(echo "$CREATED" | head -1 | jq -r '.id')

curl -sb /tmp/vg-cookies.txt -X DELETE "http://localhost:8080/api/sandboxes/$ID" -w "\nDELETE=%{http_code}\n"
```

Expected: GET=200, POST=201, DELETE=204。

- [ ] **Step 11: 跑受影响模块 test**

```bash
cd /Users/lamba/github/Veriguard
mvn -pl veriguard-api test -Dtest='*Inject*Test,*Sandbox*Test' -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：移除 InjectAssistant AI 助手模块

- 删 InjectAssistantInput.java / InjectAssistantService.java 及引用端点
- 删前端 actions/AskAI.js、utils/ai/、TextFieldAskAI 组件
- 移除 7 个 fields 组件中的 AskAI 按钮引用

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Phase 2 — 移除 XTM Hub 集成

**Files:**
- Delete (后端): `veriguard-api/src/main/java/io/veriguard/xtmhub/`（整目录）、`veriguard-api/src/main/java/io/veriguard/api/xtmhub/`
- Delete (前端): `veriguard-front/src/actions/xtmhub/`、`veriguard-front/src/utils/hooks/useXtmHubUserPlatformToken.ts`、`useXtmHubDownloadDocument.ts`、`utils/xtm-hub-client.ts`、`public/components/trialbanners/`、`admin/components/getting_started/`、`admin/components/settings/experience/`、`admin/components/common/ImportFromHubButton.tsx`
- Modify: `veriguard-api/src/main/java/io/veriguard/service/PlatformSettingsService.java` —— 删 XtmHub 字段
- Modify: `veriguard-front/src/root.tsx`、`utils/hooks/useAuth.ts`、`components/Theme.ts`、`ThemeLight.ts`、`ThemeDark.ts` —— 删 XtmHub import 与初始化
- Modify: `veriguard-dev/docker-compose.yml` —— 删 xtm-composer 服务段
- Modify: `veriguard-front/src/admin/Index.tsx`、`admin/components/nav/LeftBar.tsx` —— 删 Experience / GettingStarted 路由与菜单项
- Modify: `veriguard-api/src/main/resources/application.properties` —— 删 `veriguard.xtm.hub.url=...` 行

- [ ] **Step 1: 列出 XtmHub 当前测试覆盖**

```bash
rg -l 'XtmHub\|xtm.?hub\|xtm_hub' veriguard-api/src/test/ veriguard-front/src/ 2>/dev/null
```

- [ ] **Step 2: 删除 XtmHub 后端整目录**

```bash
rm -rf veriguard-api/src/main/java/io/veriguard/xtmhub/
rm -rf veriguard-api/src/main/java/io/veriguard/api/xtmhub/
rm -rf veriguard-api/src/test/java/io/veriguard/api/xtmhub/ 2>/dev/null
find veriguard-api/src/test -name 'XtmHub*Test.java' -delete
find veriguard-api/src/test -name 'WithMockXtmHubConfig*' -delete
```

> 注意：不要用 `find ... -path '*Xtm*' -delete` 这类通配符。`XtmComposer` 是另一个仍保留的子系统（管理 connector 实例），其测试 `XtmComposerApiTest.java` 必须保留。仅匹配 `XtmHub*` 名称才安全。

- [ ] **Step 3: PlatformSettingsService 中去 XtmHub 字段**

```bash
grep -n 'xtmhub\|XtmHub' veriguard-api/src/main/java/io/veriguard/service/PlatformSettingsService.java
```

用 Edit 工具删除：
- `import io.veriguard.xtmhub.*` 行
- `@Value("${veriguard.xtm.hub.url:...}")` 字段
- 相关 getter/setter
- 任何向输出 DTO 注入 XTM Hub 信息的代码段

- [ ] **Step 4: application.properties 删 XTM Hub 行**

```bash
sed -i.bak '/veriguard\.xtm\.hub/d' veriguard-api/src/main/resources/application.properties
rm veriguard-api/src/main/resources/application.properties.bak
```

- [ ] **Step 5: 删除前端 XtmHub 相关文件**

```bash
rm -rf veriguard-front/src/actions/xtmhub
rm veriguard-front/src/utils/hooks/useXtmHubUserPlatformToken.ts
rm veriguard-front/src/utils/hooks/useXtmHubDownloadDocument.ts
rm veriguard-front/src/utils/xtm-hub-client.ts
rm -rf veriguard-front/src/public/components/trialbanners
rm -rf veriguard-front/src/admin/components/getting_started
rm -rf veriguard-front/src/admin/components/settings/experience
rm veriguard-front/src/admin/components/common/ImportFromHubButton.tsx
```

- [ ] **Step 6: 修改 root.tsx / useAuth.ts / Theme*.ts 去 XtmHub 引用**

对每个文件用 Edit 工具：
- `veriguard-front/src/root.tsx` — 删 XtmHub Provider/import
- `veriguard-front/src/utils/hooks/useAuth.ts` — 删 XtmHub 字段读取
- `veriguard-front/src/components/Theme.ts`、`ThemeLight.ts`、`ThemeDark.ts` — 删 XtmHub 颜色/品牌段

- [ ] **Step 7: 删除 Experience / GettingStarted 菜单与路由**

修改 `veriguard-front/src/admin/Index.tsx`：删除 `<Route path="/experience" ...>` 与 `<Route path="/getting-started" ...>`。

修改 `veriguard-front/src/admin/components/nav/LeftBar.tsx`：删除"Experience"、"Getting started" 菜单项。

- [ ] **Step 8: dev compose 删 xtm-composer**

修改 `veriguard-dev/docker-compose.yml`，删除 `xtm-composer` 服务整段（约 15-20 行，含 image/environment/volumes）。

- [ ] **Step 9: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

Expected: BUILD SUCCESS。如失败，按报错位置补漏 import / 删残留引用。

- [ ] **Step 10: 验证前端编译**

```bash
cd veriguard-front && yarn check-ts && yarn lint && cd ..
```

Expected: 0 error。

- [ ] **Step 11: 启动全栈 + 沙箱 smoke**

```bash
docker compose up -d --build
sleep 30
TOKEN=$(curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' | jq -r '.user_id')
[ "$TOKEN" != "null" ] && echo LOGIN_OK || echo LOGIN_FAIL
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: LOGIN_OK 与 GET=200。

- [ ] **Step 12: 跑受影响模块 test**

```bash
mvn -pl veriguard-api test -Dtest='*Settings*Test,*Sandbox*Test,*Auth*Test' -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：移除 XTM Hub 集成

- 删后端 xtmhub/ 整目录及 PlatformSettingsService 中 XtmHub 字段
- 删前端 actions/xtmhub、utils/xtm-hub-client、useXtmHub* hooks
- 删 trialbanners、getting_started、settings/experience、ImportFromHubButton
- 删左侧菜单 Experience 与 Getting Started 入口
- 删 dev docker-compose 中 xtm-composer 服务

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Phase 3 — 移除 OpenCTI 集成与认证链拆解

**Files:**
- Delete (后端整目录): `veriguard-api/src/main/java/io/veriguard/opencti/`、`integration/impl/injectors/opencti/`、`injectors/opencti/`
- Delete: `scheduler/jobs/OpenCTIConnectorRegisterPingJob.java`、`scheduler/jobs/SecurityCoverageJob.java`、`api/stix_process/StixApi.java`、`service/stix/SecurityCoverageService.java`、`service/stix/StixService.java`、`security/token/JwtExtractor.java`
- Delete: `veriguard-api/src/main/resources/img/icon-opencti.png`
- Modify: `veriguard-api/src/main/java/io/veriguard/security/token/ExtractorBase.java` —— 替换 `import io.veriguard.opencti.errors.ConnectorError` 为本地 `AuthenticationException`
- Modify: `veriguard-api/src/main/java/io/veriguard/security/TokenAuthenticationFilter.java` —— 同上
- Modify: `veriguard-api/src/main/java/io/veriguard/service/PlatformSettingsService.java` —— 删 OpenCTI 字段

- [ ] **Step 1: 列出 OpenCTI 测试覆盖**

```bash
rg -l 'OpenCTI\|opencti\|StixService\|SecurityCoverage' veriguard-api/src/test/ 2>/dev/null
```

- [ ] **Step 2: 检查 ExtractorBase / TokenAuthenticationFilter 中具体 OpenCTI 引用行**

```bash
grep -n 'opencti\|ConnectorError' veriguard-api/src/main/java/io/veriguard/security/token/ExtractorBase.java veriguard-api/src/main/java/io/veriguard/security/TokenAuthenticationFilter.java
```

- [ ] **Step 3: 创建本地 AuthenticationException 类**

```bash
mkdir -p veriguard-api/src/main/java/io/veriguard/security/exception
```

新建文件 `veriguard-api/src/main/java/io/veriguard/security/exception/AuthenticationException.java`：

```java
package io.veriguard.security.exception;

public class AuthenticationException extends RuntimeException {
  public AuthenticationException(String message) {
    super(message);
  }

  public AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

- [ ] **Step 4: 修改 ExtractorBase / TokenAuthenticationFilter 替换异常类型**

用 Edit 工具：
- `ExtractorBase.java`：删 `import io.veriguard.opencti.errors.ConnectorError;`，加 `import io.veriguard.security.exception.AuthenticationException;`，把方法签名/抛出位置的 `ConnectorError` 改成 `AuthenticationException`
- `TokenAuthenticationFilter.java`：同上

- [ ] **Step 5: 删除 JwtExtractor**

```bash
rm veriguard-api/src/main/java/io/veriguard/security/token/JwtExtractor.java
```

如 `TokenAuthenticationFilter.java` 中显式引用 `JwtExtractor`，用 Edit 工具删除该 import 与构造逻辑（保留 `PlainTokenExtractor` 即可）。

- [ ] **Step 6: 删除 OpenCTI 后端整目录**

```bash
rm -rf veriguard-api/src/main/java/io/veriguard/opencti
rm -rf veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/opencti
rm -rf veriguard-api/src/main/java/io/veriguard/injectors/opencti
rm veriguard-api/src/main/java/io/veriguard/scheduler/jobs/OpenCTIConnectorRegisterPingJob.java
rm veriguard-api/src/main/java/io/veriguard/scheduler/jobs/SecurityCoverageJob.java
rm veriguard-api/src/main/java/io/veriguard/api/stix_process/StixApi.java
rm -rf veriguard-api/src/main/java/io/veriguard/service/stix
rm veriguard-api/src/main/resources/img/icon-opencti.png
find veriguard-api/src/test -name '*OpenCTI*' -delete
find veriguard-api/src/test -name '*Stix*' -delete
find veriguard-api/src/test -name '*SecurityCoverage*' -delete
```

- [ ] **Step 7: PlatformSettingsService 删 OpenCTI 字段**

```bash
grep -n 'opencti\|OpenCTI' veriguard-api/src/main/java/io/veriguard/service/PlatformSettingsService.java
```

用 Edit 删除 OpenCTI 相关 `@Value` / 字段 / getter。

- [ ] **Step 8: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

Expected: BUILD SUCCESS。常见报错：某 service/controller 还引用 `OpenCTIConnectorService` 或 `StixService` —— 用 grep 查找并删除引用。

- [ ] **Step 9: 启动全栈 + 登录 smoke（验证认证主链未断）**

```bash
docker compose up -d --build
sleep 30
LOGIN=$(curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nHTTP=%{http_code}\n")
echo "$LOGIN" | tail -1
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: HTTP=200 (login)、GET=200 (sandboxes)。如认证 5xx，回滚 ExtractorBase / TokenAuthenticationFilter 修改。

- [ ] **Step 10: 跑认证 + 沙箱 + Stix 受影响模块 test**

```bash
mvn -pl veriguard-api test -Dtest='*Auth*Test,*Token*Test,*Sandbox*Test,*PlatformSettings*Test' -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：移除 OpenCTI 集成与认证链 OpenCTI 分支

- 删 opencti/、injectors/opencti/、stix_process/、service/stix/
- 删 OpenCTIConnectorRegisterPingJob、SecurityCoverageJob
- 删 JwtExtractor（OpenCTI 专用 JWT 校验器）
- 新增本地 AuthenticationException 替换 OpenCTI ConnectorError
- PlatformSettingsService 移除 OpenCTI 字段

认证主路径（admin token + JWT login via PlainTokenExtractor）保活。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Phase 4 — 移除 OVH 短信注入器

**Files:**
- Delete (整目录): `veriguard-api/src/main/java/io/veriguard/injectors/ovh/`、`veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/ovh/`
- Delete: `veriguard-api/src/main/java/io/veriguard/integration/migration/OvhInjectorConfigurationMigration.java`
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/injector_contract/InjectorContractService.java` —— 删 OVH contract 注册段
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/inject/output/InjectOutput.java` —— 删 OVH 字段（如有）
- Modify: `veriguard-api/src/main/java/io/veriguard/integration/Manager.java` —— 删 OVH injector 注册方法

- [ ] **Step 1: 找 OVH 测试覆盖**

```bash
rg -l 'Ovh\|OVH\|ovh_sms' veriguard-api/src/test/ 2>/dev/null
```

- [ ] **Step 2: 删除 OVH 后端整目录**

```bash
rm -rf veriguard-api/src/main/java/io/veriguard/injectors/ovh
rm -rf veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/ovh
rm veriguard-api/src/main/java/io/veriguard/integration/migration/OvhInjectorConfigurationMigration.java
find veriguard-api/src/test -name '*Ovh*' -delete
```

- [ ] **Step 3: 找出 InjectorContractService 中 OVH 注册段**

```bash
grep -n 'ovh\|Ovh\|OVH' veriguard-api/src/main/java/io/veriguard/rest/injector_contract/InjectorContractService.java
```

- [ ] **Step 4: 修改 InjectorContractService 删 OVH 注册**

用 Edit 工具删除 import 行与 contract 注册块（通常是 `register(...)` 调用一行）。

- [ ] **Step 5: Manager.java 删 OVH 注入器请求方法**

```bash
grep -n 'ovh\|Ovh' veriguard-api/src/main/java/io/veriguard/integration/Manager.java
```

用 Edit 删除 `requestOvhInjector()` 等方法。

- [ ] **Step 6: InjectOutput 删 OVH 字段**

```bash
grep -n 'ovh\|Ovh' veriguard-api/src/main/java/io/veriguard/rest/inject/output/InjectOutput.java
```

如有 OVH 字段（DTO 输出含 SMS 信息），用 Edit 删除。

- [ ] **Step 7: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 8: 启动全栈 + 沙箱 smoke**

```bash
docker compose up -d --build
sleep 30
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: LOGIN=200、GET=200。

- [ ] **Step 9: 跑受影响测试**

```bash
mvn -pl veriguard-api test -Dtest='*Inject*Test,*Sandbox*Test,*Manager*Test' -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：移除 OVH 短信注入器

- 删 injectors/ovh/ 与 integration/impl/injectors/ovh/
- 删 OvhInjectorConfigurationMigration
- 解耦 InjectorContractService、Manager、InjectOutput 中 OVH 注册引用

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Phase 5 — 移除 Channel + Challenge 注入器

**Files:**
- Delete: `veriguard-api/src/main/java/io/veriguard/injectors/channel/`、`injectors/challenge/`、`integration/impl/injectors/channel/`、`integration/impl/injectors/challenge/`
- Modify: `veriguard-api/src/main/java/io/veriguard/integration/Manager.java` —— 删 Channel/Challenge injector 注册
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/injector_contract/InjectorContractService.java` —— 删对应 contract 注册
- Modify: `veriguard-front/src/admin/Index.tsx`、`admin/components/nav/LeftBar.tsx` —— 如有 Channel/Challenge 路由/菜单，一并删

- [ ] **Step 1: 列出测试覆盖**

```bash
rg -l 'ChannelInjector\|ChallengeInjector\|injectors\.channel\|injectors\.challenge' veriguard-api/src/test/ veriguard-front/src/ 2>/dev/null
```

- [ ] **Step 2: 删后端整目录**

```bash
rm -rf veriguard-api/src/main/java/io/veriguard/injectors/channel
rm -rf veriguard-api/src/main/java/io/veriguard/injectors/challenge
rm -rf veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/channel
rm -rf veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/challenge
find veriguard-api/src/test -path '*channel*' -delete
find veriguard-api/src/test -path '*challenge*' -delete
find veriguard-api/src/test -name '*Channel*Injector*Test.java' -delete
find veriguard-api/src/test -name '*Challenge*Injector*Test.java' -delete
```

- [ ] **Step 3: Manager.java 删 Channel/Challenge 注入器请求方法**

```bash
grep -n 'channel\|Channel\|challenge\|Challenge' veriguard-api/src/main/java/io/veriguard/integration/Manager.java
```

用 Edit 删除 `requestChannelInjector`、`requestChallengeInjector` 方法及相关 import。

- [ ] **Step 4: InjectorContractService 删对应 contract 注册**

用 Edit 删除 Channel / Challenge contract 注册行。

- [ ] **Step 5: 前端检查 Channel / Challenge 引用**

```bash
rg -l 'channel.?inject\|challenge.?inject\|ChannelContract\|ChallengeContract' veriguard-front/src 2>/dev/null
```

如前端有相关组件、菜单、页面，逐个删除（这些 injector 仅是 inject 类型之一，前端通常通过统一 inject 表单渲染，应不需要专门删）。

- [ ] **Step 6: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

- [ ] **Step 7: 启动全栈 + sandbox smoke**

```bash
docker compose up -d --build
sleep 30
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: LOGIN=200, GET=200。

- [ ] **Step 8: 跑受影响测试**

```bash
mvn -pl veriguard-api test -Dtest='*Inject*Test,*Sandbox*Test' -q
```

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：移除 Channel 与 Challenge 注入器

- 删 injectors/channel、injectors/challenge 及对应 integration/impl
- 解耦 Manager.java 与 InjectorContractService 中注册引用

注：Channel/Challenge 复用 EmailInjectorIntegration，必须先于 Phase 6 移除。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Phase 6 — 移除 Email Injector，保留 SmtpService

**Files:**
- Delete: `veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailInjectorIntegration.java`、`EmailInjectorIntegrationFactory.java`
- Delete: `veriguard-api/src/main/java/io/veriguard/injectors/email/EmailContract.java`、`EmailExecutor.java`（如存在）
- Delete: `veriguard-api/src/main/java/io/veriguard/injectors/email/service/EmailService.java`（业务执行层）
- **保留**: `veriguard-api/src/main/java/io/veriguard/injectors/email/service/SmtpService.java`（admin 通知基础设施）
- Modify: `veriguard-api/src/main/java/io/veriguard/integration/Manager.java` —— 删 `requestEmailInjector()` 方法
- Modify: `veriguard-api/src/main/java/io/veriguard/scheduler/jobs/ComchecksExecutionJob.java` —— 重写不再调 EmailInjector，改走 SmtpService 或通知层
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/injector_contract/InjectorContractService.java` —— 删 Email contract 注册
- Modify: `veriguard-api/src/main/java/io/veriguard/rest/inject/service/InjectService.java`、`service/MailingService.java` —— 检查不再走 EmailInjectorIntegration

- [ ] **Step 1: 列出测试覆盖与依赖**

```bash
rg -l 'EmailInjector\|EmailExecutor\|EmailContract' veriguard-api/src/main/ veriguard-api/src/test/ 2>/dev/null
rg -l 'SmtpService' veriguard-api/src/main/ veriguard-api/src/test/ 2>/dev/null
```

- [ ] **Step 2: 删 EmailInjector 但保留 SmtpService**

```bash
rm veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailInjectorIntegration.java
rm veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email/EmailInjectorIntegrationFactory.java
rmdir veriguard-api/src/main/java/io/veriguard/integration/impl/injectors/email 2>/dev/null || true
rm -f veriguard-api/src/main/java/io/veriguard/injectors/email/EmailContract.java
rm -f veriguard-api/src/main/java/io/veriguard/injectors/email/EmailExecutor.java
rm -f veriguard-api/src/main/java/io/veriguard/injectors/email/service/EmailService.java
find veriguard-api/src/test -name '*EmailInjector*Test.java' -delete
find veriguard-api/src/test -name '*EmailContract*Test.java' -delete
```

确认 SmtpService 仍在：

```bash
ls veriguard-api/src/main/java/io/veriguard/injectors/email/service/SmtpService.java
```

Expected: 文件存在。

- [ ] **Step 3: Manager.java 删 requestEmailInjector**

```bash
grep -n 'EmailInjector\|requestEmail' veriguard-api/src/main/java/io/veriguard/integration/Manager.java
```

用 Edit 删除该方法及相关 import（位置约在 line 70 附近）。

- [ ] **Step 4: ComchecksExecutionJob 改走 SmtpService**

```bash
grep -n 'EmailInjector\|EmailService\|SmtpService' veriguard-api/src/main/java/io/veriguard/scheduler/jobs/ComchecksExecutionJob.java
```

如该 job 通过 EmailInjectorIntegration 发送通信检查邮件，改成直接调用 `smtpService.send(...)`。具体改动取决于现有签名，原则：
- 删 `import io.veriguard.integration.impl.injectors.email.EmailInjectorIntegration`
- 添加 `import io.veriguard.injectors.email.service.SmtpService`
- 替换调用：`emailInjector.execute(...)` → `smtpService.send(toEmail, subject, body)`

- [ ] **Step 5: InjectService / MailingService / InjectorContractService 验证**

```bash
grep -n 'EmailInjector\|EmailContract' veriguard-api/src/main/java/io/veriguard/rest/inject/service/InjectService.java veriguard-api/src/main/java/io/veriguard/service/MailingService.java veriguard-api/src/main/java/io/veriguard/rest/injector_contract/InjectorContractService.java
```

用 Edit 删除残留 import 和 contract 注册行。

- [ ] **Step 6: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

Expected: BUILD SUCCESS。SmtpService 必须仍可被 InjectService / PlatformSettingsService / MailingService 引用。

- [ ] **Step 7: 启动全栈 + sandbox smoke + 邮件通知 smoke**

```bash
docker compose up -d --build
sleep 30
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"

# Smtp 通知能力验证（不真正发邮件，确认 endpoint 存在不报错）
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/settings/platform -w "\nSETTINGS=%{http_code}\n" -o /dev/null
```

Expected: LOGIN=200, GET=200, SETTINGS=200。

- [ ] **Step 8: 跑受影响测试**

```bash
mvn -pl veriguard-api test -Dtest='*Smtp*Test,*Mailing*Test,*Comcheck*Test,*Sandbox*Test' -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：移除 Email 注入器，保留 SmtpService 通知能力

- 删 EmailInjectorIntegration、EmailInjectorIntegrationFactory、EmailContract、EmailService
- 保留 SmtpService 作为 admin/通知基础设施
- ComchecksExecutionJob 改走 SmtpService 发邮件
- Manager.java 删除 requestEmailInjector 方法

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Phase 7 — 移除 Telemetry 上报

**Files:**
- Delete (整目录): `veriguard-api/src/main/java/io/veriguard/telemetry/`
- Delete: `veriguard-api/src/main/java/io/veriguard/config/AppPyroscopeConfig.java`
- Delete: `veriguard-dev/otlp-config.yaml`
- Modify (去打点): `veriguard-api/src/main/java/io/veriguard/importer/V1_DataImporter.java`、`rest/custom_dashboard/WidgetService.java`、`scheduler/jobs/InjectsExecutionJob.java`、`service/AtomicTestingService.java`、`service/scenario/ScenarioService.java`、`rest/exercise/service/ExerciseService.java`、`executors/Executor.java`
- Modify: `veriguard-dev/docker-compose.yml` —— 删 pyroscope/otlp 注释段

- [ ] **Step 1: 列出测试覆盖**

```bash
rg -l 'telemetry\|Telemetry\|MetricCollector\|OpenTelemetry\|Pyroscope' veriguard-api/src/test/ 2>/dev/null
```

- [ ] **Step 2: 删除 telemetry 整目录**

```bash
rm -rf veriguard-api/src/main/java/io/veriguard/telemetry
rm veriguard-api/src/main/java/io/veriguard/config/AppPyroscopeConfig.java
rm veriguard-dev/otlp-config.yaml
find veriguard-api/src/test -path '*telemetry*' -delete
find veriguard-api/src/test -name '*Metric*Test.java' -delete
find veriguard-api/src/test -name '*OpenTelemetry*Test.java' -delete
find veriguard-api/src/test -name '*Pyroscope*Test.java' -delete
```

- [ ] **Step 3: 全仓扫描 telemetry 调用并清理**

```bash
rg -n 'metricRegistry\|MetricRegistry\|ActionMetricCollector\|GlobalMetricCollector\|UserMetricCollector\|AgentMetricCollector\|telemetry\.' veriguard-api/src/main/java/
```

对每个匹配位置（约 10 处），用 Edit 工具删除：
- `import io.veriguard.telemetry.*`
- 类字段 `private final MetricRegistry metricRegistry;`
- 构造器中注入参数（同步删除调用方）
- 方法体中 `metricRegistry.increment(...)`、`actionMetricCollector.record(...)` 等调用行

具体文件：
1. `importer/V1_DataImporter.java`
2. `rest/custom_dashboard/WidgetService.java`
3. `scheduler/jobs/InjectsExecutionJob.java`
4. `service/AtomicTestingService.java`
5. `service/scenario/ScenarioService.java`
6. `rest/exercise/service/ExerciseService.java`
7. `executors/Executor.java`

- [ ] **Step 4: dev compose 删 pyroscope/otlp 段**

修改 `veriguard-dev/docker-compose.yml`，删除注释掉的 `veriguard-pyroscope:` 与 `veriguard-telemetry-otlp:` 服务段（约 15 行）。

- [ ] **Step 5: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

Expected: BUILD SUCCESS。如有遗漏调用，按报错位置补漏。

- [ ] **Step 6: 启动全栈 + sandbox smoke**

```bash
docker compose up -d --build
sleep 30
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: LOGIN=200, GET=200。

- [ ] **Step 7: 跑受影响测试**

```bash
mvn -pl veriguard-api test -Dtest='*Atomic*Test,*Scenario*Test,*Exercise*Test,*Widget*Test,*Sandbox*Test' -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：移除 Telemetry 上报与各处打点

- 删 telemetry/ 整目录 + AppPyroscopeConfig
- 8 处核心 service / job 移除 metric.increment / counter 等打点调用
- 删 dev otlp-config.yaml 与 pyroscope/otlp docker-compose 段

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Phase 8 — 移除 EE / License 校验

**Files:**
- Delete (整目录): `veriguard-api/src/main/java/io/veriguard/ee/`
- Delete: `utils/LicenseUtils.java`、`config/cache/LicenseCacheManager.java`、`rest/exception/LicenseRestrictionException.java`
- Delete (前端): `veriguard-front/src/components/EnterpriseEditionContext.ts`、`EnterpriseEditionProvider.tsx`、`utils/hooks/useEnterpriseEdition.ts`、`admin/components/common/entreprise_edition/`（整目录）
- Modify (去 EE 分支): `rest/helper/RestBehavior.java`、`rest/settings/response/PlatformSettings.java`、`rest/vulnerability/service/VulnerabilityService.java`、`service/detection_remediation/DetectionRemediationAIService.java`、`rest/inject/service/InjectService.java`、`utils/mapper/PayloadMapper.java`、`VulnerabilityMapper.java`、`CveMapper.java`、`ExecutorMapper.java`、`service/scenario/ScenarioService.java`、`rest/exercise/service/ExerciseService.java`、`service/PlatformSettingsService.java`、`service/connectors/ConnectorOrchestrationService.java`
- Modify (剥 EDR license 校验): `executors/paloaltocortex/service/PaloAltoCortexExecutorContextService.java`、`executors/crowdstrike/service/CrowdStrikeExecutorContextService.java`、`executors/sentinelone/service/SentinelOneExecutorContextService.java`、`executors/tanium/service/TaniumExecutorContextService.java`、4 个对应的 IntegrationFactory
- Modify (前端去 EE 装饰): `findings/FindingDetail.tsx`、`agents/Agents.tsx`、`agents/ExecutorDocumentationLink.tsx`、`settings/Parameters.tsx`、`settings/vulnerabilities/TabLabelWithEE.tsx`、`settings/vulnerabilities/VulnerabilityDetail.tsx`、`payloads/Loader.tsx`、`payloads/form/DetectionRemediationUseAriane.tsx`、`payloads/form/DetectionRemediationInfo.tsx`、`agents/InstructionSelector.tsx`

- [ ] **Step 1: 列出 EE 测试覆盖**

```bash
rg -l 'LicenseUtils\|LicenseRestriction\|isEEEnabled\|EnterpriseEdition' veriguard-api/src/test/ veriguard-front/src/ 2>/dev/null
```

- [ ] **Step 2: 全仓扫描 EE 引用点**

```bash
rg -n 'LicenseUtils\.\|isEEEnabled\|LicenseRestrictionException' veriguard-api/src/main/java/ > /tmp/ee-refs.txt
wc -l /tmp/ee-refs.txt
cat /tmp/ee-refs.txt | head -30
```

记录所有引用点，作为 Step 4 修改清单。

- [ ] **Step 3: RestBehavior 删 LicenseRestrictionException 处理器**

读取 `veriguard-api/src/main/java/io/veriguard/rest/helper/RestBehavior.java` line 75-82，用 Edit 工具删除：

```java
@ExceptionHandler(LicenseRestrictionException.class)
public ValidationErrorBag handleLicenseError(LicenseRestrictionException ex) {
  // ... handler body
}
```

以及顶部 `import io.veriguard.rest.exception.LicenseRestrictionException;`。

- [ ] **Step 4: 逐个修改 EE 引用点**

按 /tmp/ee-refs.txt 中每行位置，用 Edit 工具按以下规则改：

规则 A — `if (LicenseUtils.isEEEnabled()) { eeBranch } else { communityBranch }`：保留 communityBranch 部分，删 if 包裹和 eeBranch。
规则 B — `if (LicenseUtils.isEEEnabled()) { eeBranch }`：整段删（默认行为是无操作 / community fallback）。
规则 C — `throw new LicenseRestrictionException(...)`：替换为合适的业务处理（多数情况删整行；如果该方法的契约就是"EE 才允许"，则保留方法但删 LicenseRestriction 那行，业务行为变成始终允许 community 操作）。
规则 D — `import io.veriguard.utils.LicenseUtils;` / `import io.veriguard.ee.*;`：删除。

具体文件清单（按 spec §4.2 Phase 8）：
1. `rest/settings/response/PlatformSettings.java`
2. `rest/vulnerability/service/VulnerabilityService.java`
3. `service/detection_remediation/DetectionRemediationAIService.java` —— 该 service 的 `enrichRemediation()` 方法整体走 AI 路径，删后改成 noop / 返回 input；如该 service 仅服务 EE，整文件删
4. `rest/inject/service/InjectService.java`
5. `utils/mapper/PayloadMapper.java`、`VulnerabilityMapper.java`、`CveMapper.java`、`ExecutorMapper.java`
6. `service/scenario/ScenarioService.java`
7. `rest/exercise/service/ExerciseService.java`
8. `service/PlatformSettingsService.java`
9. `service/connectors/ConnectorOrchestrationService.java`
10. 4 个 EDR Executor service：`paloaltocortex/`、`crowdstrike/`、`sentinelone/`、`tanium/` 下的 `*ExecutorContextService.java`
11. 4 个 EDR Executor `*ExecutorIntegrationFactory.java`、`*ExecutorIntegration.java`

- [ ] **Step 5: 删 EE 后端核心目录与文件**

```bash
rm -rf veriguard-api/src/main/java/io/veriguard/ee
rm veriguard-api/src/main/java/io/veriguard/utils/LicenseUtils.java
rm veriguard-api/src/main/java/io/veriguard/config/cache/LicenseCacheManager.java
rm veriguard-api/src/main/java/io/veriguard/rest/exception/LicenseRestrictionException.java
find veriguard-api/src/test -name '*License*Test.java' -delete
find veriguard-api/src/test -name '*EnterpriseEdition*Test.java' -delete
find veriguard-api/src/test -path '*ee/*Test*' -delete
```

- [ ] **Step 6: 删 EE 前端核心目录与文件**

```bash
rm veriguard-front/src/components/EnterpriseEditionContext.ts
rm veriguard-front/src/components/EnterpriseEditionProvider.tsx
rm veriguard-front/src/utils/hooks/useEnterpriseEdition.ts
rm -rf veriguard-front/src/admin/components/common/entreprise_edition
```

- [ ] **Step 7: 修改前端业务组件，删 EE 装饰**

对每个文件，用 Edit 工具删除：
- `import EEChip / EETooltip / EnterpriseEditionButton / EnterpriseEditionAgreementDialog`
- JSX 中 `<EEChip ... />`、`<EETooltip>...</EETooltip>`、`<EnterpriseEditionButton>` 等标签

文件清单：
1. `veriguard-front/src/admin/components/findings/FindingDetail.tsx`
2. `veriguard-front/src/admin/components/agents/Agents.tsx`
3. `veriguard-front/src/admin/components/agents/ExecutorDocumentationLink.tsx`
4. `veriguard-front/src/admin/components/agents/InstructionSelector.tsx`
5. `veriguard-front/src/admin/components/settings/Parameters.tsx`
6. `veriguard-front/src/admin/components/settings/vulnerabilities/TabLabelWithEE.tsx` —— 整文件删（这就是 EE 标签）；`Vulnerabilities.tsx` 中引用换成 `<TabLabel>` 即可
7. `veriguard-front/src/admin/components/settings/vulnerabilities/VulnerabilityDetail.tsx`
8. `veriguard-front/src/admin/components/payloads/Loader.tsx`
9. `veriguard-front/src/admin/components/payloads/form/DetectionRemediationUseAriane.tsx`
10. `veriguard-front/src/admin/components/payloads/form/DetectionRemediationInfo.tsx`

- [ ] **Step 8: root.tsx 删 EnterpriseEditionProvider 包裹**

```bash
grep -n 'EnterpriseEdition' veriguard-front/src/root.tsx
```

用 Edit 删除 import 与 `<EnterpriseEditionProvider>...</EnterpriseEditionProvider>` 包裹（保留内部组件结构）。

- [ ] **Step 9: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

Expected: BUILD SUCCESS。EE 引用最广，预计需要多轮迭代修复。

- [ ] **Step 10: 验证前端编译**

```bash
cd veriguard-front && yarn check-ts && yarn lint && cd ..
```

Expected: 0 error。

- [ ] **Step 11: 启动全栈 + sandbox smoke**

```bash
docker compose up -d --build
sleep 30
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"

# 创建/删除沙箱
CREATED=$(curl -sb /tmp/vg-cookies.txt -X POST http://localhost:8080/api/sandboxes \
  -H 'Content-Type: application/json' \
  -d '{"name":"smoke-phase8","networkRules":[],"sampleType":"WINDOWS_PE","autoRevert":true}' \
  -w "\nPOST=%{http_code}\n")
echo "$CREATED"
ID=$(echo "$CREATED" | head -1 | jq -r '.id')
curl -sb /tmp/vg-cookies.txt -X DELETE "http://localhost:8080/api/sandboxes/$ID" -w "\nDELETE=%{http_code}\n"
```

Expected: LOGIN=200, GET=200, POST=201, DELETE=204。

- [ ] **Step 12: 跑受影响测试**

```bash
mvn -pl veriguard-api test -Dtest='*RestBehavior*Test,*PlatformSettings*Test,*Vulnerability*Test,*Payload*Test,*Executor*Test,*Sandbox*Test,*Mapper*Test,*Scenario*Test,*Exercise*Test' -q
```

Expected: BUILD SUCCESS。如某 mapper test 期望 EE 增强字段，应一并删。

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：移除 EE/License 校验，保留 EDR Executors 业务逻辑

- 删 ee/、utils/LicenseUtils、config/cache/LicenseCacheManager、LicenseRestrictionException
- 25+ 引用点全删 EE 分支，仅保留 community 路径
- RestBehavior 删 LicenseRestrictionException 处理器
- 4 个 EDR Executor (caldera/crowdstrike/paloaltocortex/sentinelone/tanium) 剥离 license 校验保留执行逻辑
- 前端删 EnterpriseEditionContext/Provider、useEnterpriseEdition、entreprise_edition/
- 业务组件删 EEChip/EETooltip/EnterpriseEditionButton 装饰

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Phase 9 — 清空 starter pack zip + xtmhub-scenarios 测试资源

**Files:**
- Delete: `veriguard-api/src/main/resources/starterpack/scenarios/scenario-Akira_Ransomware_Crisis.zip`、`scenario-CVE_TTPs_validation.zip`、`scenario-EASM.zip`
- Delete: `veriguard-api/src/test/resources/xtmhub-scenarios/`（整目录）
- **保留**: `veriguard-api/src/main/java/io/veriguard/datapack/packs/V20260101_Starter_pack.java`（导入框架）

- [ ] **Step 1: 删 starter pack zip**

```bash
rm veriguard-api/src/main/resources/starterpack/scenarios/scenario-Akira_Ransomware_Crisis.zip
rm veriguard-api/src/main/resources/starterpack/scenarios/scenario-CVE_TTPs_validation.zip
rm veriguard-api/src/main/resources/starterpack/scenarios/scenario-EASM.zip
ls veriguard-api/src/main/resources/starterpack/scenarios/
```

Expected: 目录为空（仅留目录本身）。

- [ ] **Step 2: 删 xtmhub-scenarios 测试资源**

```bash
rm -rf veriguard-api/src/test/resources/xtmhub-scenarios
```

- [ ] **Step 3: 检查 V20260101_Starter_pack 在没有 zip 时行为**

读取 `veriguard-api/src/main/java/io/veriguard/datapack/packs/V20260101_Starter_pack.java`，确认其 `apply()` 或类似方法在 scenarios 目录为空时是 graceful skip（不抛异常）。如果当前实现遇到空目录会异常，用 Edit 加一个文件存在性检查。

通常该类用 `getResourceAsStream` 读 zip，无 zip 时 `null` 返回 → 跳过。如代码已经这么处理，无需改动。

- [ ] **Step 4: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

- [ ] **Step 5: 启动全栈 + 检查启动日志无 starter pack import 失败**

```bash
docker compose up -d --build
sleep 60
docker logs veriguard-app 2>&1 | grep -i 'starter\|import' | tail -10
```

Expected: 没有 `Failed to import StarterPack` 错误（对比裁剪前会有 3 条）。

- [ ] **Step 6: 沙箱 smoke**

```bash
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: LOGIN=200, GET=200。

- [ ] **Step 7: 跑受影响测试**

```bash
mvn -pl veriguard-api test -Dtest='*StarterPack*Test,*Import*Test,*Scenario*Test,*Sandbox*Test' -q
```

如有 test 显式依赖 xtmhub-scenarios fixture，删除该 test。

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：清空 starter pack 内置场景与 xtmhub-scenarios 测试资源

- 删 3 个 starter pack zip：Akira / CVE / EASM
- 删 src/test/resources/xtmhub-scenarios/ 目录
- 保留 V20260101_Starter_pack 与 ImportService 导入框架

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Phase 10 — i18n 收缩到 zh + en + 清理残留 key

**Files:**
- Delete: `veriguard-front/src/utils/lang/de.json`、`es.json`、`fr.json`、`it.json`、`ja.json`、`ko.json`、`ru.json`
- Modify: `veriguard-front/src/utils/lang/zh.json`、`en.json` —— 删除已下线模块对应的 key
- Modify: i18n loader 文件（通常是 `veriguard-front/src/components/AppIntlProvider.tsx` 或类似）—— 把语言列表常量从 9 改成 2

- [ ] **Step 1: 找出 i18n loader / 语言常量**

```bash
rg -l 'de\.json\|fr\.json\|languageList\|supportedLocales' veriguard-front/src 2>/dev/null
```

记录返回的文件路径（多半是 `AppIntlProvider.tsx` 或 `utils/lang/index.ts` 或 `utils/Localization.ts`）。

- [ ] **Step 2: 删除 7 个语言文件**

```bash
cd veriguard-front/src/utils/lang
rm de.json es.json fr.json it.json ja.json ko.json ru.json
ls
cd /Users/lamba/github/Veriguard
```

Expected: lang/ 目录只剩 `en.json`、`zh.json`。

- [ ] **Step 3: 修改 i18n loader，把语言列表改成 zh + en**

用 Edit 工具找到语言常量数组（如 `['en', 'fr', 'de', ...]`），替换成 `['en', 'zh']`。同时如有 `import deLocale from './lang/de.json'` 等，删除这些 import 与对应的 case 分支。

- [ ] **Step 4: 清理 zh.json / en.json 中已删模块的 key**

被砍模块的 i18n key 通常含字符串前缀。grep 查找：

```bash
grep -E '"(opencti|xtmhub|xtm_hub|enterprise|EE_|license|ee_|telemetry|ai_assistant|ovh|filigran|trial|getting_started|experience)' veriguard-front/src/utils/lang/zh.json | head -30
grep -E '"(opencti|xtmhub|xtm_hub|enterprise|EE_|license|ee_|telemetry|ai_assistant|ovh|filigran|trial|getting_started|experience)' veriguard-front/src/utils/lang/en.json | head -30
```

对每个匹配到的 key，用 Edit 工具或脚本删除该行（注意保持 JSON 合法 —— 删行同时处理前后逗号）。

推荐用 jq 批量清理：

```bash
for f in veriguard-front/src/utils/lang/{zh,en}.json; do
  jq '
    with_entries(
      select(
        (.key | test("opencti|xtmhub|xtm_hub|enterprise_edition|^EE_|^ee_|license|telemetry|ai_assistant|ask_ai|^ovh|filigran|trial|getting_started|experience"; "i")) | not
      )
    )
  ' "$f" > "$f.new" && mv "$f.new" "$f"
done
```

执行后人工抽查保留的 key 是否完整覆盖业务文案。

- [ ] **Step 5: 验证前端编译**

```bash
cd veriguard-front && yarn check-ts && yarn lint && cd ..
```

Expected: 0 error。如某组件引用了已删除的 i18n key，会报"missing translation"warning，不阻塞编译；后续 e2e 时可视化验证。

- [ ] **Step 6: 启动全栈 + sandbox smoke + 切换语言验证**

```bash
docker compose up -d --build
sleep 30
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: LOGIN=200, GET=200。

- [ ] **Step 7: 前端 vitest**

```bash
cd veriguard-front && yarn test --run && cd ..
```

Expected: 通过。

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：i18n 收缩到 zh+en，清理残留模块文案 key

- 删 de/es/fr/it/ja/ko/ru 7 个语言文件
- i18n loader 语言列表改为 ['en', 'zh']
- 用 jq 清理 zh.json/en.json 中 opencti/xtmhub/EE/AI/ovh/telemetry/filigran/trial/getting_started/experience 相关 key

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Phase 11 — Flyway baseline 重置至 V1__Init.sql

**Files:**
- Create: `veriguard-api/src/main/resources/db/migration/V1__Init.sql`（pg_dump -s 导出整理）
- Delete: `veriguard-api/src/main/java/io/veriguard/migration/V*.java`（272 个文件全部）

- [ ] **Step 1: 确认当前 schema 已是干净状态**

```bash
docker compose up -d
sleep 30
docker compose exec veriguard-pgsql pg_dump -s -U veriguard veriguard > /tmp/v1-schema-raw.sql
wc -l /tmp/v1-schema-raw.sql
```

Expected: 输出 schema SQL，约几百到几千行。

- [ ] **Step 2: 整理 schema SQL（去 owner / timestamp / SET 语句）**

```bash
sed -e '/^SET /d' \
    -e '/^SELECT pg_catalog\.set_config/d' \
    -e '/^-- Dumped/d' \
    -e '/^-- Started/d' \
    -e '/^-- PostgreSQL database dump/d' \
    -e '/OWNER TO/s/OWNER TO [^;]*/OWNER TO veriguard/g' \
    /tmp/v1-schema-raw.sql > /tmp/v1-schema-cleaned.sql
head -20 /tmp/v1-schema-cleaned.sql
wc -l /tmp/v1-schema-cleaned.sql
```

确认头部没有 timestamp / SET / OWNER 杂质，正文是 CREATE TABLE / CREATE INDEX 等 DDL。

- [ ] **Step 3: 写入 V1__Init.sql**

```bash
mkdir -p veriguard-api/src/main/resources/db/migration
cat > veriguard-api/src/main/resources/db/migration/V1__Init.sql <<'HEADER'
-- Veriguard schema baseline (二开裁剪后第一次 baseline)
-- 由 pg_dump -s 在 Phase 11 导出，整合所有沙箱 M1 之前的历史 migrations。
-- 后续 schema 变更从 V2 开始。

HEADER
cat /tmp/v1-schema-cleaned.sql >> veriguard-api/src/main/resources/db/migration/V1__Init.sql
wc -l veriguard-api/src/main/resources/db/migration/V1__Init.sql
```

- [ ] **Step 4: 删除全部 Java migrations**

```bash
ls veriguard-api/src/main/java/io/veriguard/migration/V*.java | wc -l
rm veriguard-api/src/main/java/io/veriguard/migration/V*.java
ls veriguard-api/src/main/java/io/veriguard/migration/
```

Expected: 第一条命令返回 272，rm 后 ls 输出空（或仅剩非 V*.java 文件，如 base classes）。

如 migration 目录有非 V*.java（如 `BaseJavaMigration.java` helper），保留。

- [ ] **Step 5: Flyway 配置确认**

```bash
grep -n 'flyway' veriguard-api/src/main/resources/application.properties
```

Expected: 含 `spring.flyway.locations=classpath:db/migration,classpath:io/veriguard/migration` 或类似。如包含 `classpath:io/veriguard/migration`，可改成只 `classpath:db/migration`（因为 Java migrations 已删）。

如果 `application-prod.properties` 也有相关行，同步修改。

- [ ] **Step 6: 干净启动验证 V1__Init.sql 在空 PG 上能完整建表**

```bash
docker compose down -v
docker compose up -d --build
sleep 60
docker logs veriguard-app 2>&1 | grep -iE 'flyway|migration|V1__init' | head -20
docker logs veriguard-app 2>&1 | grep -iE 'BUILD FAILED|ERROR|Failed' | head -20
```

Expected: Flyway 成功执行 V1__Init，无 ERROR；表结构完整。

- [ ] **Step 7: 沙箱 smoke（含创建 / 删除）**

```bash
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null

CREATED=$(curl -sb /tmp/vg-cookies.txt -X POST http://localhost:8080/api/sandboxes \
  -H 'Content-Type: application/json' \
  -d '{"name":"smoke-phase11","networkRules":[],"sampleType":"WINDOWS_PE","autoRevert":true}' \
  -w "\nPOST=%{http_code}\n")
echo "$CREATED"
ID=$(echo "$CREATED" | head -1 | jq -r '.id')
curl -sb /tmp/vg-cookies.txt -X DELETE "http://localhost:8080/api/sandboxes/$ID" -w "\nDELETE=%{http_code}\n"
```

Expected: LOGIN=200, POST=201, DELETE=204。

- [ ] **Step 8: 跑全部受影响 test**

```bash
mvn -pl veriguard-api test -Dtest='*Migration*Test,*Sandbox*Test,*Flyway*Test' -q
```

如有 test 直接引用某个 V2_* 类（理论上裁剪后不应有），全删。

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：Flyway baseline 重置至 V1__Init.sql

- 通过 pg_dump -s 导出当前裁剪后 schema，整理后落到 V1__Init.sql
- 删除全部 272 个历史 V*.java migration 文件
- Flyway location 收敛到 db/migration

警告：本次 baseline 重置仅适用于全新部署，已有 OpenBAS 老库无法平滑升级。

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Phase 12 — 品牌资产抹除

**Files:**
- Delete: `LICENSE`、`CONTRIBUTING.md`、`SECURITY.md`、`AGENTS.md`、`.github/copilot-instructions.md`、`.github/workflows/`（整目录）、`.github/img/logo_filigran.png`、`veriguard-front/src/static/images/logo_filigran_dark.png`、`logo_filigran_light.png`、`logo_filigran_text_dark.png`、`logo_filigran_text_light.png`
- Create (SVG 占位 logo): `veriguard-front/src/static/images/logo_dark.svg`、`logo_light.svg`、`logo_text_dark.svg`、`logo_text_light.svg`
- Modify: `README.md` — 重写为纯二开项目介绍
- Modify: 邮件模板 `veriguard-api/src/main/resources/email/generic_template_en.html`、`notification_template_scenario_difference_en.html` — logo URL + 页脚替换
- Modify (字符串替换 ~50 处): 配置 / 服务 / 模型 / 资源 / 前端组件中的 "Filigran" 字样

- [ ] **Step 1: 顶层删除文件**

```bash
rm LICENSE CONTRIBUTING.md SECURITY.md AGENTS.md
rm .github/copilot-instructions.md
rm -rf .github/workflows
rm .github/img/logo_filigran.png
rm veriguard-front/src/static/images/logo_filigran_dark.png \
   veriguard-front/src/static/images/logo_filigran_light.png \
   veriguard-front/src/static/images/logo_filigran_text_dark.png \
   veriguard-front/src/static/images/logo_filigran_text_light.png
```

- [ ] **Step 2: 创建 SVG 占位 logo**

新建文件 `veriguard-front/src/static/images/logo_dark.svg`：

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 60" width="200" height="60">
  <text x="10" y="40" font-family="sans-serif" font-size="28" font-weight="700" fill="#ffffff">Veriguard</text>
</svg>
```

新建 `logo_light.svg`（fill 改为 `#1f2937`）：

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 60" width="200" height="60">
  <text x="10" y="40" font-family="sans-serif" font-size="28" font-weight="700" fill="#1f2937">Veriguard</text>
</svg>
```

新建 `logo_text_dark.svg` 和 `logo_text_light.svg`（同上，但 viewBox 缩为 `0 0 200 50`，font-size 调整到 24）。

如原 png 文件被代码以 `.png` 扩展名引用，需要把所有引用同步改成 `.svg`：

```bash
rg -l 'logo_dark\.png\|logo_light\.png\|logo_text_dark\.png\|logo_text_light\.png' veriguard-front/src 2>/dev/null
```

对每个匹配文件用 Edit 工具替换 `.png` → `.svg`。

- [ ] **Step 3: 重写 README.md**

用 Write 工具替换 `README.md` 内容为：

```markdown
# Veriguard

面向 PRD 五大模块的安全验证平台二开实现：流量安全验证、应用与服务器安全验证、自定义验证、攻击编排、沙箱管理。

## 快速开始

```bash
docker compose up -d
```

服务起来后访问 `http://localhost:8080`，默认账号 `admin@veriguard.io` / `Veriguard2026`。

## 项目结构

- `veriguard-api/` — Spring Boot REST API（Java 21 / Spring Boot 3.3.7）
- `veriguard-model/` — JPA 实体与持久化层
- `veriguard-framework/` — 横切关注点（安全、配置、消息）
- `veriguard-front/` — React 19 + TypeScript SPA（Vite + Yarn 4）
- `veriguard-dev/` — IntelliJ run configs 与开发期 docker compose
- `docs/` — 产品需求与设计文档

## 开发

详见 `CLAUDE.md`。
```

- [ ] **Step 4: 重写邮件模板**

读取 `veriguard-api/src/main/resources/email/generic_template_en.html`，找到 logo `<img src="...">` 与页脚的 Filigran 引用，用 Edit 替换：
- logo src 替换为新 SVG URL（如使用 base64 内嵌或本地 `/static/logo_text_light.svg`）
- 页脚的 `© Filigran` 等改成 `© Veriguard`
- 邮件结尾的 `https://filigran.io` 等链接删除或替换为占位

同样处理 `notification_template_scenario_difference_en.html`。

- [ ] **Step 5: 字符串替换**

对每个文件执行 grep 找到 Filigran/filigran 字样，用 Edit 工具针对具体行替换或删除。

文件清单（spec §4.2 Phase 12）：

```
veriguard-api/src/main/java/io/veriguard/config/AppConfig.java
veriguard-framework/src/main/java/io/veriguard/config/VeriguardConfig.java
veriguard-framework/src/main/java/io/veriguard/config/VeriguardAdminConfig.java
veriguard-framework/src/main/java/io/veriguard/config/RabbitmqConfig.java
veriguard-framework/src/main/java/io/veriguard/expectation/ExpectationPropertiesConfig.java
veriguard-api/src/main/resources/application.properties
veriguard-api/src/main/java/io/veriguard/service/EndpointService.java
veriguard-api/src/main/java/io/veriguard/rest/health_check/HealthCheckApi.java
veriguard-api/src/main/java/io/veriguard/runner/InitAdminCommandLineRunner.java
veriguard-api/src/main/java/io/veriguard/rest/helper/queue/BatchQueueService.java
veriguard-api/src/main/java/io/veriguard/executors/ExecutorHelper.java
veriguard-model/src/main/java/io/veriguard/database/model/SettingKeys.java
veriguard-model/src/main/java/io/veriguard/healthcheck/enums/ExternalServiceDependency.java
veriguard-front/package.json
veriguard-front/src/public/components/login/Login.tsx
veriguard-front/src/admin/components/nav/LeftBar.tsx
veriguard-front/index.html
veriguard-front/src/components/Theme.ts
veriguard-front/src/components/ThemeLight.ts
veriguard-front/src/components/ThemeDark.ts
```

替换原则：
- "Filigran" → 删除（在签名/版权上下文）或替换为 "Veriguard"（在产品名上下文）
- "filigran.io" / "filigran.com" → 删除整段链接或替换为占位 `https://example.invalid`
- "OpenBAS" → 替换为 "Veriguard"（如代码注释提到 OpenBAS）

- [ ] **Step 6: package.json 同步**

修改 `veriguard-front/package.json`：
- `"name": "veriguard-front"` —— 已是正确，无变化
- `"description"` —— 改成中文项目描述
- 删 `"author"` 中 Filigran 字样

- [ ] **Step 7: index.html 标题**

```bash
grep -n 'title' veriguard-front/index.html
```

用 Edit 把 `<title>` 改为简洁名称如 `<title>Veriguard</title>`。

- [ ] **Step 8: 全仓 grep 残留 Filigran**

```bash
rg -i 'filigran\|openbas' --max-count 1 . 2>/dev/null | grep -vE 'node_modules|target|/builder/|yarn.lock|package-lock|.git/' | head -20
```

如还有残留，用 Edit 工具逐个删除/替换。

- [ ] **Step 9: 验证后端编译**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
```

- [ ] **Step 10: 验证前端编译**

```bash
cd veriguard-front && yarn check-ts && yarn lint && cd ..
```

- [ ] **Step 11: 启动全栈 + 浏览器肉眼检查 logo / 标题**

```bash
docker compose up -d --build
sleep 30
curl -s http://localhost:8080/ | grep -E '<title>|logo'
```

- [ ] **Step 12: 沙箱 smoke**

```bash
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: LOGIN=200, GET=200。

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：抹除 Filigran/OpenBAS 品牌资产与归属文件

- 删 LICENSE/CONTRIBUTING/SECURITY/AGENTS/.github/workflows/copilot-instructions
- 替换 logo 为 SVG 文字占位
- 重写 README 为纯二开项目介绍
- 重写邮件模板 logo 与页脚
- 替换 ~20 个源码文件中的 Filigran 字样

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Phase 13 — pom.xml 依赖清理

**Files:**
- Modify: 根 `pom.xml`、`veriguard-api/pom.xml`、`veriguard-framework/pom.xml`、`veriguard-model/pom.xml`

- [ ] **Step 1: 列出当前 pom 依赖**

```bash
mvn -pl veriguard-api -am dependency:tree -DoutputType=text -DoutputFile=/tmp/deptree.txt -q
wc -l /tmp/deptree.txt
grep -iE 'opentelemetry\|pyroscope\|opencti\|stix\|filigran\|ovh\|xtm\|openai' /tmp/deptree.txt | head -30
```

记录所有匹配项，按 groupId / artifactId 整理。

- [ ] **Step 2: 在 pom.xml 中删除已下线模块依赖**

按以下规则修改 `pom.xml` 与各子模块 `pom.xml`：

砍掉的依赖（按 groupId 模式）：
- `io.opentelemetry:*`（含 telemetry-api、telemetry-sdk、telemetry-exporter-* 等）
- `io.pyroscope:agent`、`io.pyroscope:pyroscope-otel`
- `org.glowroot.*`（如有）
- `com.openai:*` / `com.theokanning.openai-gpt3-java:*`（AI Assistant 用）
- `com.ovh:*`（OVH SMS SDK）
- `com.filigran:*`（如 filigran 私有 SDK）
- 其他被 Phase 1-8 移除模块独占的依赖

对每个匹配的 `<dependency>` 块用 Edit 工具删除，保持 `<dependencies>` 容器完整。

如 `<dependencyManagement>` 中也声明了版本，对应删除。

- [ ] **Step 3: 删除被砍模块的 BOM / parent**

如 OpenTelemetry 用 BOM `<dependency><groupId>io.opentelemetry.instrumentation</groupId>...</dependency>` 在 `<dependencyManagement>`，一并删除。

- [ ] **Step 4: 验证 mvn 解析**

```bash
mvn -pl veriguard-api -am dependency:resolve -q
```

Expected: BUILD SUCCESS。如某依赖被保留模块隐式依赖，会从 transitive 中拉到，`compile` 仍可过。

- [ ] **Step 5: 验证后端完整编译 + 测试**

```bash
mvn -pl veriguard-api -am compile -DskipTests -q
mvn -pl veriguard-api test -Dtest='*Sandbox*Test,*Auth*Test,*PlatformSettings*Test' -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 6: docker build 验证镜像构建**

```bash
docker compose down -v
docker compose up -d --build
sleep 60
docker compose ps
docker logs veriguard-app 2>&1 | tail -20
```

Expected: 全部 healthy，启动日志无 `ClassNotFoundException` / `NoClassDefFoundError`。

- [ ] **Step 7: 沙箱 smoke**

```bash
curl -sb /tmp/vg-cookies.txt -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null
curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nGET=%{http_code}\n"
```

Expected: LOGIN=200, GET=200。

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
裁剪：清理 pom.xml 中已下线模块依赖

- 删除 OpenTelemetry、Pyroscope、OpenCTI、OVH、OpenAI、XtmHub 等独占依赖
- dependencyManagement 中对应版本声明同步删除

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Phase 14 — Final 全量验证

**Files:** 不预期改动，如发现问题则补丁 commit。

- [ ] **Step 1: 全栈编译**

```bash
mvn install -DskipTests -Pdev -q
cd veriguard-front && yarn install --immutable && yarn build && cd ..
```

Expected: BUILD SUCCESS, vite build 成功，`dist/` 输出完整。

- [ ] **Step 2: 镜像构建（多阶段）**

```bash
DOCKER_BUILDKIT=0 docker build -t veriguard-app:dev -f Dockerfile .
docker images veriguard-app
```

Expected: 镜像构建成功。

- [ ] **Step 3: 干净启动**

```bash
docker compose down -v
docker compose up -d --build
sleep 60
docker compose ps
docker logs veriguard-app 2>&1 | grep -iE 'started|error|fail' | tail -20
```

Expected: 5 个容器全 healthy；启动日志无 ERROR。

- [ ] **Step 4: 沙箱主路径完整 smoke**

```bash
curl -c /tmp/vg-cookies.txt -X POST http://localhost:8080/api/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin@veriguard.io","password":"Veriguard2026"}' -w "\nLOGIN=%{http_code}\n" -o /dev/null

curl -sb /tmp/vg-cookies.txt http://localhost:8080/api/sandboxes -w "\nLIST=%{http_code}\n"

CREATED=$(curl -sb /tmp/vg-cookies.txt -X POST http://localhost:8080/api/sandboxes \
  -H 'Content-Type: application/json' \
  -d '{"name":"final-smoke","networkRules":[{"cidr":"10.0.0.0/24","ports":[80,443],"action":"ALLOW"}],"sampleType":"WINDOWS_PE","autoRevert":true}' \
  -w "\nCREATE=%{http_code}\n")
echo "$CREATED"
ID=$(echo "$CREATED" | head -1 | jq -r '.id')

curl -sb /tmp/vg-cookies.txt "http://localhost:8080/api/sandboxes/$ID" -w "\nDETAIL=%{http_code}\n"

curl -sb /tmp/vg-cookies.txt -X PUT "http://localhost:8080/api/sandboxes/$ID" \
  -H 'Content-Type: application/json' \
  -d '{"name":"final-smoke-renamed","networkRules":[],"sampleType":"WINDOWS_PE","autoRevert":false}' \
  -w "\nUPDATE=%{http_code}\n" -o /dev/null

curl -sb /tmp/vg-cookies.txt -X DELETE "http://localhost:8080/api/sandboxes/$ID" -w "\nDELETE=%{http_code}\n"
```

Expected: LOGIN=200, LIST=200, CREATE=201, DETAIL=200, UPDATE=200, DELETE=204。

- [ ] **Step 5: 后端全量 test**

```bash
mvn -pl veriguard-api test -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 6: 前端 unit test**

```bash
cd veriguard-front && yarn test --run && cd ..
```

Expected: 通过。

- [ ] **Step 7: 重生成 OpenAPI 类型**

```bash
cd veriguard-front && yarn generate-types-from-api && cd ..
git diff veriguard-front/src/utils/api-types.d.ts | head -50
```

Expected: 类型差量主要是删除（OpenCTI/XtmHub/EE 相关 type 移除）。

- [ ] **Step 8: e2e（沙箱重点）**

```bash
cd veriguard-front && yarn playwright test tests_e2e/tests/admin/veriguard/sandbox/m1.spec.ts && cd ..
```

Expected: 沙箱 m1 spec 通过。

如能跑全 e2e：

```bash
cd veriguard-front && yarn playwright test && cd ..
```

如有 spec 因模块下线而失败（如 EE / Experience / GettingStarted 相关），删除该 spec。

- [ ] **Step 9: 提交 final patch（如有）**

```bash
git add -A
git status
```

如有改动（最常见的是 api-types.d.ts 重生成 + e2e spec 删除），commit：

```bash
git commit -m "$(cat <<'EOF'
裁剪：Final 全量验证后修复

- 重生成 OpenAPI 类型（api-types.d.ts）
- 删除已下线模块对应 e2e spec
- 修复其他全量验证发现的小问题

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

如无改动，跳过此 commit。

- [ ] **Step 10: 合回 main**

```bash
git checkout main
git merge --no-ff chore/cleanup -m "$(cat <<'EOF'
合并：二开冗余裁剪

裁剪 14 phase 完成：移除非 PRD 模块、Flyway baseline 重置、品牌抹除。
回滚锚 tag: pre-cleanup
执行计划: docs/superpowers/plans/2026-04-28-veriguard-cleanup.md
设计 spec: docs/superpowers/specs/2026-04-28-veriguard-cleanup-design.md

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
git log --oneline -20
```

- [ ] **Step 11: 删除 chore/cleanup 分支**

```bash
git branch -d chore/cleanup
git tag -l pre-cleanup
```

Expected: 分支删除成功，pre-cleanup tag 仍存在作为永久回滚锚。

- [ ] **Step 12: 任务完成**

最终状态：
- main 分支包含全部 14 phase + 合并 commit
- pre-cleanup tag 标记裁剪起点
- 仓库代码、UI、docs、DB schema 收敛到二开真正需要的范围
- 沙箱 M1 全部能力保活
- 全栈干净启动 + 主路径 smoke + 测试套件 + e2e 全过

---

## Self-Review

### 1. Spec 覆盖检查

对照 spec §四 14 phase：

| Phase | 在 plan 中？ | 任务 |
|---|---|---|
| 0 打 tag | ✅ | Task 0 |
| 1 InjectAssistant | ✅ | Task 1 |
| 2 XTM Hub | ✅ | Task 2 |
| 3 OpenCTI + 认证拆解 | ✅ | Task 3 |
| 4 OVH | ✅ | Task 4 |
| 5 Channel + Challenge | ✅ | Task 5 |
| 6 Email Injector | ✅ | Task 6 |
| 7 Telemetry | ✅ | Task 7 |
| 8 EE / License | ✅ | Task 8 |
| 9 starter pack zip + xtmhub-scenarios | ✅ | Task 9 |
| 10 i18n | ✅ | Task 10 |
| 11 Flyway baseline | ✅ | Task 11 |
| 12 品牌抹除 | ✅ | Task 12 |
| 13 pom.xml | ✅ | Task 13 |
| 14 Final | ✅ | Task 14 |

### 2. Placeholder 扫描

无 TBD / TODO / "后续补充" 等占位。Step 6 / Step 4 in Phase 8 用了"按 grep 结果逐个修改"——这是必要的开放式步骤（引用点过多无法预先穷举），但每条规则（A/B/C/D）已具体化。

### 3. Type 一致性

- 所有 sandbox API 路径统一用 `/api/sandboxes`，请求体字段 `name`/`networkRules`/`sampleType`/`autoRevert` 在 Phase 1/8/11/14 完全一致。
- `pre-cleanup` tag 在 Task 0 创建，Task 14 引用，名称一致。
- `chore/cleanup` 分支命名贯穿全部 task。
- `Veriguard2026` admin 密码全部 task 一致。
- `AuthenticationException` 在 Task 3 创建，后续 task 不再涉及。

无类型/命名漂移。
