-- V12: IPv6 安全验证系统 ★2 攻击组合 PR D2 — 任务运行表 + 结果表
--
-- 招标条款：§3.6 动态攻击组合 30 000 组合执行（PR D2 编排层）
-- 本迁移在 V11（bypass_dimensions + attack_combination_templates）之上新增两张运行时表：
--   - attack_combination_runs      任务（含状态机 / 限流 / 并发 / 超时配置）
--   - attack_combination_results   单 (combination × asset) 结果行
--
-- 命名约定：snake_case + 复数表名；ID 用 VARCHAR(255)（与 V11 + Asset 一致）；
-- 枚举校验交 Java 层（lowercase Enum.name() 直驱 VARCHAR）。
--
-- 说明：combination_id 是「虚拟 ID」= base_attack_type + ":" + bypass_dimension_id；
-- 不引用 attack_combination_templates 表，因为组合是即时笛卡尔积，不必先建模板行。

BEGIN;

-- ============================================================
-- 1. 攻击组合任务
-- ============================================================
-- 状态机（lowercase）：
--   pending    -> running -> completed
--                  |  ^
--                  v  |
--                paused
--                  |
--                  v
--               cancelled
--                  |
--                  v
--                failed   （系统错误或超时）
CREATE TABLE attack_combination_runs (
    attack_combination_run_id                  VARCHAR(255) PRIMARY KEY,
    attack_combination_run_name                VARCHAR(255) NOT NULL,
    attack_combination_run_base_attack_types   JSONB        NOT NULL,
    attack_combination_run_bypass_dimension_ids JSONB       NOT NULL,
    attack_combination_run_asset_ids           JSONB        NOT NULL,
    attack_combination_run_status              VARCHAR(32)  NOT NULL DEFAULT 'pending',
    attack_combination_run_total_combinations  INTEGER      NOT NULL,
    attack_combination_run_total_results       INTEGER      NOT NULL,
    attack_combination_run_completed_count     INTEGER      NOT NULL DEFAULT 0,
    attack_combination_run_failed_count        INTEGER      NOT NULL DEFAULT 0,
    attack_combination_run_rate_limit_per_second INTEGER    NOT NULL DEFAULT 100,
    attack_combination_run_concurrency         INTEGER      NOT NULL DEFAULT 16,
    attack_combination_run_max_retries         INTEGER      NOT NULL DEFAULT 3,
    attack_combination_run_timeout_hours       INTEGER      NOT NULL DEFAULT 24,
    attack_combination_run_started_at          TIMESTAMP WITH TIME ZONE,
    attack_combination_run_completed_at        TIMESTAMP WITH TIME ZONE,
    attack_combination_run_expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    attack_combination_run_created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attack_combination_run_updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_attack_combination_runs_status_created
    ON attack_combination_runs (attack_combination_run_status, attack_combination_run_created_at DESC);

CREATE INDEX idx_attack_combination_runs_expires_active
    ON attack_combination_runs (attack_combination_run_expires_at)
    WHERE attack_combination_run_status IN ('running', 'paused');

-- ============================================================
-- 2. 攻击组合结果
-- ============================================================
-- 每条结果行 = 1 个 combination × 1 个 asset 的一次执行结果。
-- hit_state（lowercase）：pending / running / hit / miss / timeout / failed.
CREATE TABLE attack_combination_results (
    attack_combination_result_id              VARCHAR(255) PRIMARY KEY,
    attack_combination_result_run_id          VARCHAR(255) NOT NULL
        REFERENCES attack_combination_runs (attack_combination_run_id) ON DELETE CASCADE,
    attack_combination_result_combination_id  VARCHAR(255) NOT NULL,
    attack_combination_result_base_attack_type VARCHAR(64) NOT NULL,
    attack_combination_result_bypass_dimension_id VARCHAR(255) NOT NULL
        REFERENCES bypass_dimensions (bypass_dimension_id) ON DELETE RESTRICT,
    attack_combination_result_asset_id        VARCHAR(255) NOT NULL,
    attack_combination_result_hit_state       VARCHAR(32)  NOT NULL DEFAULT 'pending',
    attack_combination_result_retry_count     INTEGER      NOT NULL DEFAULT 0,
    attack_combination_result_payload_sample  TEXT,
    attack_combination_result_error_message   TEXT,
    attack_combination_result_executed_at     TIMESTAMP WITH TIME ZONE,
    attack_combination_result_created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attack_combination_result_updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attack_combination_result_run_combo_asset
        UNIQUE (attack_combination_result_run_id,
                attack_combination_result_combination_id,
                attack_combination_result_asset_id)
);

CREATE INDEX idx_attack_combination_results_run_state
    ON attack_combination_results (
        attack_combination_result_run_id,
        attack_combination_result_hit_state);

CREATE INDEX idx_attack_combination_results_run_asset
    ON attack_combination_results (
        attack_combination_result_run_id,
        attack_combination_result_asset_id);

COMMIT;
