-- V23: C1-Platform-5 (沙箱 M3) — sandbox_task 队列表
--
-- M2（V0 阶段，PR #62）落地了 CapeV2SandboxDriver：与 CAPEv2 REST API 完成
-- healthCheck / listMachines / submitSample / fetchTaskStatus 4 个 SPI。
-- 但 driver 是无状态调用层 —— 每次 submit 仅返回 capeTaskId；状态轮询、
-- 失败重试、审计回查均依赖调用方自管理。M3 在 driver 之上加一层任务队列：
--
--   1. REST POST /api/sandbox-tasks 收样本 → 立刻调 driver.submitSample → 把
--      (sandbox_task_id, cape_task_id, 当前状态) 一行落库（synchronous-submit
--      模型；CAPE 不可达时整次请求失败，符合 CLAUDE.md "no fallback code"）。
--   2. Quartz SandboxTaskPollingJob 每 30s 扫 active 行（QUEUED / RUNNING），
--      调 driver.fetchTaskStatus 刷新状态，COMPLETED / FAILED 写终态后不再轮询。
--   3. /api/sandbox-tasks/{id}/refresh 提供按需立即轮询入口，给 demo 用。
--
-- 关键设计：
-- - sample 二进制 *不* 入库：driver.submitSample 一次性 multipart 上传给 CAPE，
--   平台只保留 sha256 / 原始 filename / size 元数据用于审计与去重展示。
--   理由：单样本 50MB+ 普遍，落 jsonb / bytea 会膨胀且 Quartz poll 不需要内容。
-- - sandbox_task_sandbox_id 是 *可空* FK：M3 阶段允许任务不绑定具体 preset
--   （demo 时直接选 machine），后续 B4 sample_inject 链路才会强制要求。
-- - status CHECK 用平台中性词，对应 SandboxTaskStatus.Status 枚举；CAPE 原始
--   状态字符串单独留 raw_status 列方便排障（CAPE 2.5 有 "reported" / "running"
--   等多个变体，driver mapStatus 把它们规一为 5 个值）。

BEGIN;

CREATE TABLE veriguard_sandbox_tasks (
    -- VARCHAR(255) PK 对齐 veriguard_sandboxes / 其它 二开 表的 @Id String 约定。
    -- Hibernate 6 + @UuidGenerator 仍写 UUID 字符串，但 JDBC 绑定按 VARCHAR 走，
    -- 避免 "column is of type uuid but expression is character varying" 错。
    sandbox_task_id                VARCHAR(255) NOT NULL,
    sandbox_task_sandbox_id        VARCHAR(255) NULL,
    sandbox_task_sample_sha256     VARCHAR(64)  NOT NULL,
    sandbox_task_sample_filename   TEXT         NOT NULL,
    sandbox_task_sample_size_bytes BIGINT       NOT NULL,
    sandbox_task_sample_type       VARCHAR(64)  NULL,
    sandbox_task_target_machine    VARCHAR(255) NULL,
    sandbox_task_timeout_seconds   INT          NULL,
    sandbox_task_cape_task_id      BIGINT       NULL,
    sandbox_task_status            VARCHAR(32)  NOT NULL,
    sandbox_task_raw_status        VARCHAR(64)  NULL,
    sandbox_task_error_message     TEXT         NULL,
    sandbox_task_submitted_at      TIMESTAMP    NULL,
    sandbox_task_last_polled_at    TIMESTAMP    NULL,
    sandbox_task_completed_at      TIMESTAMP    NULL,
    sandbox_task_created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    sandbox_task_updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_sandbox_task PRIMARY KEY (sandbox_task_id),
    CONSTRAINT fk_sandbox_task_sandbox
        FOREIGN KEY (sandbox_task_sandbox_id)
        REFERENCES veriguard_sandboxes (veriguard_sandbox_id)
        ON DELETE SET NULL,
    CONSTRAINT chk_sandbox_task_status
        CHECK (sandbox_task_status IN ('QUEUED','RUNNING','COMPLETED','FAILED','UNKNOWN')),
    CONSTRAINT chk_sandbox_task_size_nonneg
        CHECK (sandbox_task_sample_size_bytes >= 0),
    CONSTRAINT chk_sandbox_task_timeout_pos
        CHECK (sandbox_task_timeout_seconds IS NULL OR sandbox_task_timeout_seconds > 0)
);

-- Quartz job 主路径：扫描 active 行。
CREATE INDEX idx_sandbox_task_status        ON veriguard_sandbox_tasks (sandbox_task_status);
-- 按 cape_task_id 反查支持运维侧 "CAPE 那边那条 task 是平台哪行" 诊断。
CREATE INDEX idx_sandbox_task_cape_task_id  ON veriguard_sandbox_tasks (sandbox_task_cape_task_id);
-- 按 sha256 反查支持「这个样本之前提交过吗」展示。
CREATE INDEX idx_sandbox_task_sha256        ON veriguard_sandbox_tasks (sandbox_task_sample_sha256);

COMMIT;
