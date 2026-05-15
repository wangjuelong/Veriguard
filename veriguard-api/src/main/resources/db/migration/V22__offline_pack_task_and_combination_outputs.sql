-- V22: C1-Platform-3 follow-up — 两件正交但同一 PR 的持久化补全
--
-- (1) AB-1: offline_pack_task 表
--     V21 把 offline_pack_result.task_id 留为 nullable + 在 import 时填 null
--     是因为 (pack_id, ordinal) → task_id 映射 V21 阶段刻意延后。本表承载
--     这层映射：export 时 OfflinePackExportService 写 N 行（每个 drained
--     task 一行，ordinal 与 .vpack 内 Task JSON array 的下标一致），import
--     时 OfflinePackImportService 读出来，把 offline_pack_result.task_id 填
--     正确的字符串。
--
--     不复用 offline_pack_result.task_id 单列承载 mapping 的原因：result
--     行可能在 export 时还不存在（Mode C 离线场景：export 与 import 时差
--     数小时～数天）；而 task_id mapping 在 export 时就要落库，因此必须
--     独立成行。
--
-- (2) AB-2: attack_combination_results 加 3 列
--     V12 的 attack_combination_results 表只有 hit_state / error_message /
--     payload_sample —— Mode A combination 跑完 dispatch 返回的 stdout /
--     stderr / exit_code 全被 HttpInjectExecutor 丢弃。招标 demo 需要 admin
--     回查每个组合的 agent 端原始输出；本迁移加 3 列承载。CombinationExecutor
--     trait 同步改返回 CombinationExecutionResult 包 hitState + 输出字段。

BEGIN;

-- ============================================================
-- (1) AB-1: offline_pack_task — (pack_id, ordinal) → task_id 映射
-- ============================================================
CREATE TABLE offline_pack_task (
    pack_id  UUID         NOT NULL,
    ordinal  INT          NOT NULL,
    task_id  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_offline_pack_task PRIMARY KEY (pack_id, ordinal),
    CONSTRAINT fk_offline_pack_task_audit
        FOREIGN KEY (pack_id) REFERENCES offline_pack_audit (pack_id) ON DELETE CASCADE,
    CONSTRAINT chk_offline_pack_task_ordinal_nonneg
        CHECK (ordinal >= 0)
);

-- 按 task_id 反查（"this task_id 出现在哪些 packs"）支持 cross-pack 诊断。
CREATE INDEX idx_offline_pack_task_task_id
    ON offline_pack_task (task_id);

-- ============================================================
-- (2) AB-2: attack_combination_results 补 stdout / stderr / exit_code
-- ============================================================
-- 列命名沿用 V12 的 attack_combination_result_* 前缀。三列均 nullable —
-- 旧行（V12-V21 期间已有数据）不 backfill；新 dispatch 写入。
ALTER TABLE attack_combination_results
    ADD COLUMN attack_combination_result_stdout    TEXT NULL;
ALTER TABLE attack_combination_results
    ADD COLUMN attack_combination_result_stderr    TEXT NULL;
ALTER TABLE attack_combination_results
    ADD COLUMN attack_combination_result_exit_code INT  NULL;

COMMIT;
