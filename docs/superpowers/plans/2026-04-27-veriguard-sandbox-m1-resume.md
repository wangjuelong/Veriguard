# Veriguard 沙箱 M1 续跑指南

**目的**：在新会话里接着推进 `docs/superpowers/plans/2026-04-27-veriguard-sandbox-m1.md` 剩余任务（Task 9 起）。本文档记录到当前会话结束时的精确状态、风险与下一步动作。

**会话切断时间**：2026-04-27（写于 `feature/sandbox-m1` HEAD = `c38b59d62`）

---

## 1. 当前状态快照

### 仓库结构

- 主仓库：`/Users/lamba/github/Veriguard`，分支 `main`，HEAD `8216b1e2a`（spec + plan 已 commit）。
- 工作 worktree：`/Users/lamba/github/Veriguard/worktrees/sandbox-m1`，分支 `feature/sandbox-m1`，HEAD `c38b59d62`。
- 后续所有命令默认在 worktree 路径下执行。

### 已完成的 Plan Tasks

| Plan Task | 状态 | 关键 Commit |
| --- | --- | --- |
| Task 0（worktree 创建） | ✅ | n/a |
| Task 1（驱动接口骨架） | ✅ | `b3653546f` + `e3c53e091`（@Primary 修复） |
| Task 2（异常 HTTP 映射） | ✅ | `1c2cb50ad` + `c7b8fb2cc`（log cause 修复） |
| Tasks 3–7（实体收窄 + DTO + Service/Api 拆分） | ✅ | `1e894fa53` + `c38b59d62`（log cause 修复） |
| Task 8 | ⏭️ 计划中标记为无操作 | — |
| Task 9–22 | ⏳ 未开始 | — |
| Task 18 | ⏭️ 计划中已合并入 Task 17 行操作菜单，无独立组件 | — |

### 测试通过情况（按 commit `c38b59d62`）

```bash
cd /Users/lamba/github/Veriguard/worktrees/sandbox-m1
mvn -pl veriguard-api -am test -Dtest=NotImplementedSandboxDriverTest -Dsurefire.failIfNoSpecifiedTests=false  # 4/4 PASS
mvn -pl veriguard-api -am test -Dtest=SandboxIntegrationExceptionMappingTest -Dsurefire.failIfNoSpecifiedTests=false  # 2/2 PASS
mvn -pl veriguard-api -am test -Dtest=SandboxServiceTest -Dsurefire.failIfNoSpecifiedTests=false  # 5/5 PASS
mvn -pl veriguard-api -am compile  # BUILD SUCCESS
```

`SandboxApiIntegrationTest` 编译通过但 **未跑**（依赖 Postgres 5433 + V4_73 迁移，两者在 Task 9 之后才到位）。

---

## 2. 还没完成的 Task 列表（按推进顺序）

| # | Task | 估算 dispatch 数 | 关键依赖 |
| --- | --- | --- | --- |
| 9 | Flyway 迁移 V4_73（删 endpoint/provider 列、加 name 唯一索引） | implementer + spec + code = 3 | 本机 Postgres 跑起来才能验证 |
| 10 | `SandboxScriptExporter`（iptables / routing.conf 生成） + 单元测试 | 3 | 无 |
| 11 | 导出 REST 端点（`GET /api/sandboxes/{id}/network-rules/exports/{iptables\|routing-conf}`）+ 集成测试 | 3 | Task 9 V4_73 已应用、Postgres up |
| 12 | 前端 `veriguard-actions.ts` 收窄 + `exportSandboxIptables` / `exportSandboxRoutingConf` | 3 | 无 |
| 13 | `cidr-port-validators.ts` + vitest | 3 | 无 |
| 14 | `DeleteConfirmDialog.tsx` | 2（小到不必两段评审） | 无 |
| 15 | `NetworkRuleEditor.tsx` + vitest | 3 | MUI v7 + react-intl 测试上下文（**已知风险**，见 §3） |
| 16 | `SandboxDialog.tsx` + vitest | 3 | Task 14 + 15 |
| 17 | `SandboxList.tsx` + 行操作菜单 + 删除二次确认 + 导出按钮 | 3 | Task 14 + 16 + 12 |
| 18 | （已合并到 17，跳过） | 0 | — |
| 19 | `VeriguardConsole.tsx` 接入沙箱 Tab + 整体类型检查 | 3 | Task 17 |
| 20 | Playwright E2E `sandbox.m1` | 3 | 全栈（Postgres + Spring Boot + Vite）up |
| 21 | 同步 `docs/参考资料/Veriguard二开落地说明.md` | 1（直接编辑 + commit，不用代理） | 无 |
| 22 | spotless / lint / 全测 / JaCoCo / 截图清单 / 合并回 main | 串行多步 | **Task #26（spotless 工具链）必须先解决** |

---

## 3. 已知风险与未解项

### Task #26 — 预存的 spotless / google-java-format 不兼容

`mvn -pl veriguard-api spotless:apply` 在 `src/test/java/io/veriguard/database/model/SimulationTest.java` 上爆：

```
google-java-format(java.lang.NoSuchMethodError) 'java.util.Queue com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()'
```

这是 `google-java-format` 与 JDK 21 内部 API 的不兼容，与本次沙箱二开无关，但 **Task 22 终态自检会被它阻塞**。

**修复路径**（任选其一，新会话尽早决策）：
1. 升级 spotless-maven-plugin / google-java-format 版本到 JDK 21 兼容的发布。
2. 给 spotless 配置 `<excludes><exclude>**/SimulationTest.java</exclude></excludes>` 临时绕过，并在 issue tracker 留 ticket。
3. 把 spotless 切换到不依赖 JDK 内部 API 的 formatter（palantir-java-format）。
4. 退一步：Task 22 验证步骤跳过 `mvn spotless:apply`，仅在新增/修改文件上手工对照 `RestBehavior.java` 的格式。

### Postgres / dev stack 启动

Task 9、11、20 都需要 `veriguard-dev/docker-compose.yml` 起的服务。最小集合：

```bash
cd /Users/lamba/github/Veriguard/veriguard-dev
docker compose up -d veriguard-pgsql veriguard-test-pgsql rabbitmq veriguard-minio
```

新会话开局先确认 `docker compose ps` 看见这些容器 `Up`，再跑 Task 9。

### MUI v7 + react-intl 测试上下文

前端组件 vitest 跑起来时，MUI 主题与 `react-intl` 的 `<IntlProvider>` 都不会被自动注入。Task 15 / 16 的测试可能在 `screen.getByText('删除规则')` 这类断言上失败，因为 `react-intl` 包了 `<FormattedMessage>` 后渲染会变成消息 ID 字符串而不是中文。

**应对**：plan 里的测试 fixture 用的就是项目现有 `VeriguardConsole.tsx` 的硬编码中文模式（不走 i18n 框架），所以 vitest 不需要 `<IntlProvider>`。但需要注意：
- 新组件应该 **沿用现有硬编码中文模式**，不要引入 `<FormattedMessage>` —— spec §6.7 已经明示"不引入新依赖、沿用受控组件 + 手写校验"。
- 如果走到需要 `<ThemeProvider>` 包裹的测试场景，复用项目里既有的 vitest 测试找 setup 范例（`grep -rn "ThemeProvider" veriguard-front/src --include='*.test.tsx'`）。

### `SandboxApiIntegrationTest` 未实际跑过

只在编译层面验证。新会话 Task 9 完成后第一件事应该是：

```bash
cd /Users/lamba/github/Veriguard/worktrees/sandbox-m1
mvn -pl veriguard-api -am test -Dtest=SandboxApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

四个 case：
1. `create_returns_201_and_persists` — 应通过。
2. `create_with_disabled_auto_restore_returns_400_with_reason_code` — 应通过。
3. `create_with_duplicate_name_returns_400_duplicated` — **依赖 V4_73 的 `uk_veriguard_sandboxes_name` 唯一约束**，没有 V4_73 这条会跑红。
4. `list_returns_persisted_sandbox` — 应通过。

如果 (3) 红，说明 Task 9 没生效；先排查 Flyway。

---

## 4. 新会话推荐启动流程

1. **加载 spec + plan + 本 resume 文档**（fresh session 的最小上下文）：
   ```
   spec: docs/superpowers/specs/2026-04-26-veriguard-sandbox-design.md
   plan: docs/superpowers/plans/2026-04-27-veriguard-sandbox-m1.md
   resume: docs/superpowers/plans/2026-04-27-veriguard-sandbox-m1-resume.md
   ```

2. **进入 worktree、确认状态**：
   ```bash
   cd /Users/lamba/github/Veriguard/worktrees/sandbox-m1
   git log --oneline -8
   git status
   ```
   预期 HEAD = `c38b59d62`、tree clean。

3. **起 dev stack（如未起）**：
   ```bash
   cd /Users/lamba/github/Veriguard/veriguard-dev
   docker compose up -d veriguard-pgsql veriguard-test-pgsql rabbitmq veriguard-minio
   docker compose ps
   ```

4. **跑回归测试，确认底座绿**：
   ```bash
   cd /Users/lamba/github/Veriguard/worktrees/sandbox-m1
   mvn -pl veriguard-api -am test -Dtest='NotImplementedSandboxDriverTest,SandboxIntegrationExceptionMappingTest,SandboxServiceTest' -Dsurefire.failIfNoSpecifiedTests=false
   ```
   预期 4 + 2 + 5 = 11 PASS。

5. **唤起 subagent-driven-development 续跑**：
   - 让 Claude Code 读这份 resume 文档 + plan 的 Task 9 节。
   - 使用 plan §"Task 9" 的 verbatim 步骤生成第一个 implementer prompt。
   - 推进顺序：9 → 10 → 11 → 12 → 13 → 14 → 15 → 16 → 17 → 19 → 21 → 22。
   - Task 20（E2E）建议放到所有别的都 PASS 之后，且确认 Task #26 spotless 已修。

---

## 5. 之前评审循环里反复出现的模式（节省下次时间）

1. **`mvn ... -Dtest=X`**：项目是多模块 reactor，跑指定测试时**必须**带 `-Dsurefire.failIfNoSpecifiedTests=false`，否则 `veriguard-model` / `veriguard-framework` 模块没匹配的测试就报错退出。
2. **`InputValidationException(field, message)`**：`field` 位置就是 `reason_code`（snake_case 字符串），由 `RestBehavior` 渲染到 `errors.children.<field>`。所有新 reason_code 必须在 spec §8.2 表里有对应一行。
3. **`@SpringBootTest` 切片 vs `@WebMvcTest`**：项目大部分集成测试 `extends IntegrationTest`（abstract class）。`@WebMvcTest` 只在 Task 2 用过且需要：
   - `@AutoConfigureMockMvc(addFilters = false)` 绕 Spring Security
   - 内嵌 `static class TestConfig { @SpringBootConfiguration @EnableAutoConfiguration }` 防止扫到 `io.veriguard.App`
   - `@Import(static-nested-controller)` 注册测试 controller
4. **`@WithMockUser`**：项目自定义注解，参数是 `isAdmin = true`，**不是** `authorities = {"ROLE_ADMIN"}`。
5. **`catch (... ex) { throw new ...(without cause) }`** 是该仓库现存的反模式之一；review 几乎必定要求加 `log.warn(..., ex)` 后再 throw。下次写实现时**直接预先 log**，省一次评审循环。
6. **`SANDBOXES_URI` 类常量**：现在归 `SandboxApi.java`（Task 7 重构里相对了）。若新代码要复用，从那里 import。
7. **每个 commit message** 必须包含 `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>` 行（AGENTS.md "AI-authored commits" 要求）。

---

## 6. 6 commit 摘要（在新会话如果要 squash 或挑选可参考）

```
c38b59d62 fix(sandbox): log DataIntegrityViolation cause before translating to duplicate-name
1e894fa53 refactor(sandbox): extract SandboxApi/SandboxService and slim SandboxInput
c7b8fb2cc fix(sandbox): log cause chain on SandboxIntegrationException and tidy test imports
1c2cb50ad feat(sandbox): map SandboxIntegrationException to 502/504 with reason_code
e3c53e091 fix(sandbox): mark NotImplementedSandboxDriver @Primary for M2 bean disambiguation
b3653546f feat(sandbox): scaffold sandbox driver interface and not-implemented driver
```

---

## 7. 不在本 resume 范围

- M2 / M3 的 plan 还**没写**（M1 plan 里 §10.2 / §10.3 是设计稿层面的描述，不是可执行计划）。M1 收尾后下一步先 brainstorm → writing-plans 出 M2 plan。
- Task 21（落地说明文档同步）会在最终把 M1 经验、新接口路径、检查记录写进 `docs/参考资料/Veriguard二开落地说明.md`。本 resume 不替代 Task 21。
