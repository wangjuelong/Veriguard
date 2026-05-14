-- V20: C1 Veriguard Agent fork — offline_pack_audit 表
--
-- 背景：Mode C 离线工作模式下，平台通过 .vpack 下发任务包到运营人员 U 盘，运营线下
-- 在 Agent 所在隔离网执行后，把 .vresults 拷回平台导入。本表对每次 export / import
-- 留 audit trail，满足招标场景下的"谁、何时、何源 IP、什么内容摘要"要求。
--
-- 关键约束：
--   - pack_id 用 UUID 主键 (`.vpack`.metadata.pack_id 同形态)；UUID 唯一即可防重放
--     （Agent 侧也维护 executed_packs.db 黑名单 — 见 spec §3.5.5）
--   - agent_id 用 VARCHAR(255) 引用 agents.agent_id (与 V1 既有列类型对齐；spec 文档
--     §3.6 原写 UUID 是想当然，实际 agents.agent_id 在 V1__Init.sql 是 varchar 255)
--   - exported_ciphertext_sha256 BYTEA 32 字节 — envelope.ciphertext 的完整 SHA-256，
--     允许导入时核对内容一致性（防中间篡改 / 防误重新导入修改过的包）
--   - task_count CHECK 上限 1000 — spec §3.5.5 step 6 决策树同源；超量包拒发拒收
--   - issued_at NOT NULL，imported_at NULLABLE（导出后未必导入）
--   - exported_from_ip / imported_from_ip 用 INET，允许 IPv4 + IPv6
--
-- 索引设计：
--   - idx_pack_audit_agent (agent_id, issued_at DESC) 支持"按 Agent 查最近导出"
--     的 UI 查询模式
--   - idx_pack_audit_imported (imported_at) WHERE imported_at IS NOT NULL —
--     partial index，"查未导入的 / 已导入的"两种诊断场景都可用

CREATE TABLE offline_pack_audit (
    pack_id UUID PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL,
    platform_id TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    exported_by TEXT NOT NULL,
    exported_from_ip INET NULL,
    exported_ciphertext_sha256 BYTEA NOT NULL,
    task_count INT NOT NULL,
    imported_at TIMESTAMPTZ NULL,
    imported_by TEXT NULL,
    imported_from_ip INET NULL,
    result_count INT NULL,
    rejected_count INT NULL,
    CONSTRAINT fk_pack_audit_agent
        FOREIGN KEY (agent_id) REFERENCES agents (agent_id) ON DELETE CASCADE,
    CONSTRAINT chk_pack_audit_task_count
        CHECK (task_count >= 0 AND task_count <= 1000)
);

CREATE INDEX idx_pack_audit_agent
    ON offline_pack_audit (agent_id, issued_at DESC);

CREATE INDEX idx_pack_audit_imported
    ON offline_pack_audit (imported_at)
    WHERE imported_at IS NOT NULL;
