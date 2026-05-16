# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

This is a Spring Boot + React monorepo.

- `pom.xml` — root Maven aggregator (`io.veriguard:veriguard-platform`, Java 21, Spring Boot 3.3.7).
  - `veriguard-model` — JPA entities, persistence, shared domain types.
  - `veriguard-framework` — **deprecated** cross-cutting framework module (see `.github/instructions/backend.instructions.md`); new code should not land here.
  - `veriguard-api` — Spring Boot application (`io.veriguard.App`); REST controllers live under `src/main/java/io/veriguard/rest/<feature>/`.
- `veriguard-front/` — Vite + React 19 + TypeScript SPA (Yarn 4 workspaces, package manager pinned via `packageManager`).
- `veriguard-dev/` — `docker-compose.yml` for backend dev infra (Postgres dev/test, Elasticsearch/OpenSearch, MinIO, RabbitMQ, Caldera, pgAdmin, Kibana) plus IntelliJ run configs. Required when running `mvn spring-boot:run` locally.
- `docker-compose.yml` (repo root) — quickstart stack used by the README; layered above `veriguard-dev/` for new contributors who want a single `docker compose up`.
- `docs/IPv6安全验证系统*.md` — IPv6 安全验证系统二开工作的权威文档系列：`IPv6安全验证系统.md` 是导航总览；`-技术方案.md` / `-研发拆解.md` / `-GAP分析.md` 是设计与落差分析；`-用例清单.md` / `-外部接口清单.md` / `-业务模块Agent与数据流.md` / `-靶机环境.md` 是工程清单；`-甲方待澄清清单.md` 是单一待澄清问题源。
- `docs/参考资料/` — Chinese-language reference notes; `Veriguard二开落地说明.md` is the source of truth for the current customization scope. 甲方原档（`招标文件正文.pdf`）与对接说明（`需要对接的内容.md`、`靶机环境.md`）一并放此目录。

### Sibling forks (Rust 2021, hosted on `wangjuelong/`)

The IPv6 安全验证系统 招标 §9.2 platform agent ships as two separate Rust binaries forked from `OpenAEV-Platform` (formerly OpenBAS-Platform, GitHub 301 redirect):

- `/Users/lamba/github/veriguard-agent` — agent client (← `OpenAEV-Platform/agent@531f9d120`, release 2.3.5). Talks to this Java backend over `/api/agent/*`. Adds Ed25519/X25519 crypto + Mode C offline pack + capabilities + bootstrap.
- `/Users/lamba/github/veriguard-implant` — payload executor (← `OpenAEV-Platform/implant@3b16615e9`, release 2.3.5). Dropped onto target by agent; 5 payload types (Command / Executable / FileDrop / DnsResolution / NetworkTraffic).

**Forks are decoupled** — no cherry-picks or rebase-from-upstream; baseline commit is retained only for LICENSE attribution. Detailed design in `docs/superpowers/specs/2026-05-14-veriguard-agent-implant-fork-c1-c2-design.md`; implementation plan in `docs/superpowers/plans/2026-05-14-veriguard-agent-implant-fork-c1-c2-plan.md`.

## Conventions Index

Authoritative coding standards live under `.github/instructions/`. When working in a given area, read the matching file first:

- `.github/instructions/backend.instructions.md` — Spring Boot service conventions
- `.github/instructions/database.instructions.md` — JPA + Flyway migration rules
- `.github/instructions/frontend.instructions.md` — React/Vite/TypeScript conventions
- `.github/instructions/security.instructions.md` — RBAC, input validation, secret handling
- `.github/instructions/performance.instructions.md` — query/render perf guidelines
- `.github/instructions/testing.instructions.md` — JUnit/vitest/Playwright expectations
- `.github/instructions/code-review.instructions.md` — what to flag in PR review

## Common Commands

### Backend (Maven, run from repo root)

```sh
mvn -pl veriguard-api -am compile -DskipTests   # compile API and its module deps
mvn -pl veriguard-api -am test                   # run all tests in a module
mvn -pl veriguard-api test -Dtest=ClassName#method   # single JUnit test
mvn -pl veriguard-api spring-boot:run            # run the API (after `cd veriguard-dev && docker compose up -d`)
mvn spotless:apply                               # format Java (configured at the root pom)
```

**Single-test gotcha**: `mvn -Dtest=ClassName#method test` silently reports "no tests" unless paired with `-Dsurefire.failIfNoSpecifiedTests=false`. Always include it for targeted runs.

**JDK 21 required**: ensure `JAVA_HOME` points to a JDK 21 install before any mvn command:

```sh
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

`co.elastic.clients:elasticsearch-java` may 502 from the configured mirror; retry or switch repositories — do not pin a stale version to work around it.

### Frontend (`cd veriguard-front`)

```sh
yarn install --immutable    # use corepack; Yarn version is pinned
yarn start                  # vite dev server
yarn build                  # production build
yarn check-ts               # tsc type check
yarn lint                   # eslint with --max-warnings 0
yarn test                   # vitest unit
yarn test:e2e               # playwright
yarn generate-types-from-api    # regenerate src/utils/api-types.d.ts from the running API's OpenAPI
```

Single test: `yarn vitest run path/to/file.test.ts -t "test name"` for unit, `yarn playwright test path/to/spec.ts` for e2e.

### Docs-only checks

```sh
git diff --check -- docs
rg -n 'TODO|FIXME|待补充' docs
```

## Dev Environment

- 如果仓库已提供开发环境（本仓库为 `veriguard-dev/docker-compose.yml` + 根 `docker-compose.yml`），**优先使用既有环境**——不自起平行栈，避免端口冲突与状态分裂。
- 仓库未提供开发环境时，**默认用 docker + docker-compose 自行搭建**；新增的 `docker-compose.yml`（以及必要的 `.env.example`、初始化脚本等）必须与代码一同提交到仓库，便于其他人 / 后续会话复用。
- 不要把"本机已装某服务"作为前提编进代码或文档；运行依赖必须由仓库内的开发环境承担。

## Architecture Notes

- The API is a single Spring Boot app exposing REST under `/api/...`. Controllers are organized by feature folder (`rest/<feature>/`). Custom Veriguard二开 endpoints live under `rest/security_validation/` (capability matrix, attack use cases, attack orchestration, sandbox CRUD); URI constants are declared on `SecurityValidationApi`.
- **Attack-chain orchestration is the largest active area** (PRD §2.4). Backend lives in `rest/attack_chain*/`, `attackchain/`, and `AttackChainNodesExecutionJob`; frontend lives in `veriguard-front/src/admin/components/attack_chains/` and `attack_chain_runs/`. Wire format uses snake_case `attack_chain_*` / `attack_chain_run_*` / `node_*` after Phase 12b-A.
- Persistence uses JPA against Postgres with Flyway migrations under `veriguard-api/src/main/resources/db/migration/`. `V1__Init.sql` is the baseline (generated from `pg_dump -s` in Phase 11), `V2__Drop_channel_challenge_article_email.sql` and `V3__attack_chain_module_init.sql` are already applied — **add new SQL migrations starting from `V4__...`**. Default tenant UUID for seed data is `2cffad3a-0001-4078-b0e2-ef74274022c3`.
- AuthZ uses an `@RBAC` AOP annotation referencing `io.veriguard.database.model.Action` / `ResourceType` — preserve those when adding endpoints.
- The frontend admin shell mounts at `/admin/...`; the Veriguard二开 console is at `/admin/veriguard` (`veriguard-front/src/admin/components/veriguard/VeriguardConsole.tsx`), wired through `admin/Index.tsx` and the left nav.
- Real traffic replay, HIDS collection, SOC queries, and sandbox virtualization drivers are **external adapters**. Do not fake their results in code — expose them as integration boundaries on the API/UI per the PRD.

## Repo Conventions

- Documentation is Chinese-first Markdown: one `#` title, then `##`/`###`; tables for structured requirements, with `要求内容` / `佐证要求` columns. Use `-` (not blank) for "not applicable". Add spaces around English tokens in Chinese text (e.g. `IPv6 安全验证`).
- Commit messages: Chinese, short, scoped (e.g. `执行：补充沙箱管理说明`, `修复：V4 迁移列别名`). The upstream `CONTRIBUTING.md` describes a `[<context>] <type>(<scope>): ...` Conventional Commits form for upstream PRs — follow the Chinese style for this fork's local commits, the upstream form only when contributing back.
- AI-authored commits must include a `Co-Authored-By` trailer for the agent identity.
- Keep edits scoped to what was requested. Don't refactor, reformat, or delete unrelated code — flag dead code instead of removing it. Only delete code that **your** change made unused.
- No fallback code. Fail visibly when required inputs / dependencies / assumptions are missing rather than silently degrading.
- Don't introduce new tooling or directory conventions without explicit need.
- For multi-step work, define verifiable goals and loop until each check passes; complex features should use `git worktree` + a feature branch and only ask for confirmation if blocked by safety, permissions, or missing info.
- If requirements are ambiguous, stop and ask before silently choosing an interpretation.
- **Never push or rebase `master`**: this fork keeps `master` aligned with upstream OpenBAS baseline. All二开 PRs base on `main`.
