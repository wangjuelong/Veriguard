-- V19: C1 Veriguard Agent fork — agents 表加密密钥列
--
-- 背景：招标 §9.2 要求平台自有验证 Agent；wangjuelong/veriguard-agent (Rust 2021) fork
-- 与本仓 Java 21 Spring Boot 平台通过 Ed25519 (签名) + X25519 (密钥协商) + ChaCha20-Poly1305
-- (内容加密) 通信。`.vpack` 下行 + `.vresults` 上行均为 NaCl-box JSON envelope。
--
-- 本迁移给现有 agents 表追加 7 列承载：
--   - 双公钥 (sign / enc) 落库以便平台验签 + 加密回包
--   - onboard_token 一次性入网令牌（unique partial index 仅在 NOT NULL 时生效）
--   - 注册时序 + 注册签名 + 注册来源 IP / 创建者 (audit 保留项)
--
-- 类型决策：
--   - 公钥用 BYTEA (32 字节定长，平台侧 Java byte[])，不用 base64 TEXT —
--     base64 持久化既浪费空间也增加误用风险（Java 端记得不要把 base64 当 byte[]）
--   - onboard_token 用 TEXT，长度由应用层定 (建议 64+ hex chars，UUID 也 OK)
--   - onboarded_at 用 TIMESTAMPTZ 与 agent_created_at 列风格一致
--   - register_sig 用 BYTEA (Ed25519 签名固定 64 字节)
--   - registered_from_ip 用 INET (Postgres 原生 IPv4/IPv6 统一)
--
-- 既有 agents 行兼容性：所有新列 NULLABLE，未通过 C1 onboarding 入网的旧 Agent 行
-- 这些列全 NULL 不影响既有 inject 执行路径 (Caldera / B-ii agent 流程)。
--
-- 唯一索引 idx_agents_onboard_token 用 partial WHERE NOT NULL 避免多个 NULL 行
-- 冲突（PostgreSQL 唯一索引把 NULL 视为各不相同，但 partial 写法更清晰地表达意图）。

ALTER TABLE agents
    ADD COLUMN agent_sign_pubkey BYTEA NULL;

ALTER TABLE agents
    ADD COLUMN agent_enc_pubkey BYTEA NULL;

ALTER TABLE agents
    ADD COLUMN agent_onboard_token TEXT NULL;

ALTER TABLE agents
    ADD COLUMN agent_onboarded_at TIMESTAMPTZ NULL;

ALTER TABLE agents
    ADD COLUMN agent_register_sig BYTEA NULL;

ALTER TABLE agents
    ADD COLUMN agent_registered_from_ip INET NULL;

ALTER TABLE agents
    ADD COLUMN agent_created_by TEXT NULL;

CREATE UNIQUE INDEX idx_agents_onboard_token
    ON agents (agent_onboard_token)
    WHERE agent_onboard_token IS NOT NULL;
