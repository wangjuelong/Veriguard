# Repository Guidelines

## Project Structure & Module Organization

- `docs/prd/` contains product requirements, for example `docs/prd/产品要求.md`.
- `docs/参考资料/` contains reference material, for example `docs/参考资料/无损模拟技术揭秘.md`.
- Use descriptive Chinese Markdown filenames for Chinese-facing documents.

## Build, Test, and Development Commands

- No package manager, build system, or automated test runner is configured.

```sh
find docs -name '*.md' -print
git diff --check -- docs AGENTS.md
rg -n 'TODO|FIXME|待补充' docs AGENTS.md
```

- Run `git diff --check` before submitting changes.

## Coding Style & Naming Conventions

- Write documentation in Markdown.
- Use one `#` title per document, then `##` and `###` for hierarchy.
- Prefer tables for structured requirements; use `要求内容` and `佐证要求` for product requirement tables.
- Add spaces around English abbreviations in Chinese text, for example `IPv6 安全验证`, `SOC 告警`, and `webshell 上传`.
- Avoid empty table cells unless the absence is meaningful; use `-` for “not required” or “not applicable”.

## Testing Guidelines

- There are no automated documentation tests.
- Review Markdown rendering for broken tables, heading order, unreadable long rows, valid paths, and consistent evidence requirements.

## Commit & Pull Request Guidelines

- Use Chinese commit messages. Prefer short, scoped messages, for example `文档：美化产品要求` or `需求：补充沙箱管理说明`.
- PRs should include purpose, changed paths, assumptions, and whether formatting-only edits preserved meaning.

## Complex Feature Workflow

- For complex features, prefer `git worktree` plus a new branch.
- Before implementation, state assumptions, success criteria, and detailed development plans; complex work usually requires multiple plans.
- Complete one plan at a time, then commit implementation with a Chinese message.
- After each implementation commit, update relevant docs and commit that change separately.
- Do not pause to ask for user confirmation before all development plans are complete, unless blocked by safety, permissions, or missing required information.
- After all plans are complete, merge the feature branch back into `main`, then remove unused worktrees and temporary branches.

## Agent-Specific Instructions

- Keep edits scoped to requested documents.
- If requirements are unclear or have multiple valid interpretations, stop and ask before choosing silently.
- Prefer the simplest complete solution; avoid speculative features, one-off abstractions, and unrequested configurability.
- Match existing style; avoid unrelated refactors, formatting churn, or cleanup. Mention unrelated dead code instead of deleting it.
- Remove only code or files made unused by your own changes.
- For multi-step work, define verifiable goals and loop until each check passes.
- When writing documents or development plans, preserve complete functionality and design; do not compromise scope unless the user explicitly narrows it.
- When coding, do not write fallback code. Handle real states explicitly and fail visibly when required inputs, dependencies, or assumptions are missing.
- Do not introduce tooling or new directory conventions unless explicitly required.
- AI-authored commits must include an appropriate `Co-Authored-By` trailer for the agent identity.
