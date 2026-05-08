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
