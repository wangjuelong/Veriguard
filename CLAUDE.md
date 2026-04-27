# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

This is a Spring Boot + React monorepo, **not** a docs-only project. `AGENTS.md` predates the code drop and only covers `docs/`; treat it as authoritative for documentation work but rely on this file for the wider codebase.

- `pom.xml` — root Maven aggregator (`io.veriguard:veriguard-platform`, Java 21, Spring Boot 3.3.7).
  - `veriguard-model` — JPA entities, persistence, shared domain types.
  - `veriguard-framework` — cross-cutting framework code (security, infra helpers).
  - `veriguard-api` — Spring Boot application (`io.veriguard.App`); REST controllers live under `src/main/java/io/veriguard/rest/<feature>/`.
- `veriguard-front/` — Vite + React 19 + TypeScript SPA (Yarn 4 workspaces, package manager pinned via `packageManager`).
- `veriguard-dev/` — `docker-compose.yml`, IntelliJ run configs, and infra (Postgres dev/test, Elasticsearch/OpenSearch, MinIO, RabbitMQ, Caldera, pgAdmin, Kibana). Required for backend dev.
- `docs/prd/产品要求.md` — the PRD that drives the 二开 (secondary-development) work.
- `docs/参考资料/` — Chinese-language reference notes; `Veriguard二开落地说明.md` is the source of truth for the current customization scope.

## Common Commands

### Backend (Maven, run from repo root)

```sh
mvn -pl veriguard-api -am compile -DskipTests   # compile API and its module deps
mvn -pl veriguard-api -am test                   # run tests for a module
mvn -pl veriguard-api spring-boot:run            # run the API (after `cd veriguard-dev && docker compose up -d`)
mvn spotless:apply                               # format Java (configured at the root pom)
```

`co.elastic.clients:elasticsearch-java` may 502 from the configured mirror; retry or switch repositories — do not pin a stale version to work around it.

### Frontend (`cd veriguard-front`)

```sh
yarn install --immutable    # use corepack; Yarn version is pinned
yarn start                  # vite dev server
yarn build                  # production build
yarn check-ts               # tsc type check
yarn lint                   # eslint with --max-warnings 0
yarn test                   # vitest
yarn test:e2e               # playwright
yarn generate-types-from-api    # regenerate src/utils/api-types.d.ts from the running API's OpenAPI
```

Single test: `yarn vitest run path/to/file.test.ts -t "test name"` for unit, `yarn playwright test path/to/spec.ts` for e2e.

### Docs-only checks (from `AGENTS.md`)

```sh
git diff --check -- docs AGENTS.md
rg -n 'TODO|FIXME|待补充' docs AGENTS.md
```

## Dev Environment

- 如果仓库已提供开发环境（本仓库为 `veriguard-dev/docker-compose.yml` + 相关运行配置），**优先使用既有环境**——不自起平行栈，避免端口冲突与状态分裂。
- 仓库未提供开发环境时，**默认用 docker + docker-compose 自行搭建**；新增的 `docker-compose.yml`（以及必要的 `.env.example`、初始化脚本等）必须与代码一同提交到仓库，便于其他人 / 后续会话复用。
- 不要把"本机已装某服务"作为前提编进代码或文档；运行依赖必须由仓库内的开发环境承担。

## Architecture Notes

- The API is a single Spring Boot app exposing REST under `/api/...`. Controllers are organized by feature folder (`rest/<feature>/`). Custom Veriguard二开 endpoints live under `rest/security_validation/` (capability matrix, attack use cases, attack orchestration, sandbox CRUD); URI constants are declared on `SecurityValidationApi`.
- Persistence uses JPA against Postgres with Flyway migrations under `veriguard-api/src/main/java/io/veriguard/migration/` (e.g. `V4_72__Add_veriguard_sandbox.java`). Add a new migration rather than editing existing ones.
- AuthZ uses an `@RBAC` AOP annotation referencing `io.veriguard.database.model.Action` / `ResourceType` — preserve those when adding endpoints.
- The frontend admin shell mounts at `/admin/...`; the Veriguard二开 console is at `/admin/veriguard` (`veriguard-front/src/admin/components/veriguard/VeriguardConsole.tsx`), wired through `admin/Index.tsx` and the left nav.
- Real traffic replay, HIDS collection, SOC queries, and sandbox virtualization drivers are **external adapters**. Do not fake their results in code — expose them as integration boundaries on the API/UI per the PRD.

## Repo Conventions (from `AGENTS.md`)

- Documentation is Chinese-first Markdown: one `#` title, then `##`/`###`; tables for structured requirements, with `要求内容` / `佐证要求` columns. Use `-` (not blank) for "not applicable". Add spaces around English tokens in Chinese text (e.g. `IPv6 安全验证`).
- Commit messages: Chinese, short, scoped (e.g. `文档：美化产品要求`, `需求：补充沙箱管理说明`). The upstream `CONTRIBUTING.md` describes a `[<context>] <type>(<scope>): ...` Conventional Commits form for upstream PRs — follow the Chinese style for this fork's local commits, the upstream form only when contributing back.
- AI-authored commits must include a `Co-Authored-By` trailer for the agent identity.
- Keep edits scoped to what was requested. Don't refactor, reformat, or delete unrelated code — flag dead code instead of removing it. Only delete code that **your** change made unused.
- No fallback code. Fail visibly when required inputs / dependencies / assumptions are missing rather than silently degrading.
- Don't introduce new tooling or directory conventions without explicit need.
- For multi-step work, define verifiable goals and loop until each check passes; complex features should use `git worktree` + a feature branch and only ask for confirmation if blocked by safety, permissions, or missing info.
- If requirements are ambiguous, stop and ask before silently choosing an interpretation.
