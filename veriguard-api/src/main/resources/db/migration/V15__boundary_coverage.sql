-- V15: IPv6 安全验证系统 ★ 边界覆盖度子模块 PR C3 — 招标 §3.1（边界资产覆盖度验证）+ §4.1（流量边界覆盖度验证）。
--
-- 设计要点：
--   - 两个场景共享同一组表，由 coverage_baselines.coverage_type ∈ {boundary, traffic} 区分
--   - 主键 / 外键 沿用 VARCHAR(255)（与 V11/V12/V13/V14 一致）
--   - 4 态单元格：hit / miss / timeout / out_of_scope
--     · hit          —— 用例下发且 SOC 在时间窗内有匹配告警 → 该 (asset, policy) 单元格"有覆盖"
--     · miss         —— 用例下发但 SOC 在时间窗内无告警 → "无覆盖"（招标关注重点）
--     · timeout      —— 用例下发后超时未回 → 通信问题
--     · out_of_scope —— 策略类型不适用此资产（如 ICS 设备 vs WAF 策略） → N/A
--   - policies.device_type ∈ {waf, ips, ids, nta, hids}，对应蓝盾 NxSOC 中的告警来源类型
--
-- 表：
--   coverage_baselines    覆盖度基线（用例集合 + 资产组）
--   coverage_runs         一次覆盖度评估任务
--   coverage_results      4 态单元格（asset × policy）
--   policies              安全设备策略目录（WAF/IPS/IDS/NTA/HIDS 规则元数据）

BEGIN;

-- ============================================================
-- 1. policies —— 安全设备策略目录
-- ============================================================
CREATE TABLE policies (
    policy_id                   VARCHAR(255) PRIMARY KEY,
    policy_name                 VARCHAR(255) NOT NULL,
    policy_device_type          VARCHAR(16)  NOT NULL,
    policy_device_id            VARCHAR(255),
    policy_external_rule_id     VARCHAR(255),
    policy_description          TEXT,
    policy_created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    policy_updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_policies_device_type ON policies (policy_device_type);

COMMENT ON TABLE policies IS '安全设备策略目录 —— PR C3 边界覆盖度子模块';
COMMENT ON COLUMN policies.policy_device_type IS 'lowercase enum: waf / ips / ids / nta / hids';
COMMENT ON COLUMN policies.policy_external_rule_id IS '该策略对应蓝盾 NxSOC 中的 rule ID（用于反查告警匹配）';

-- ============================================================
-- 2. coverage_baselines —— 覆盖度基线
-- ============================================================
CREATE TABLE coverage_baselines (
    coverage_baseline_id              VARCHAR(255) PRIMARY KEY,
    coverage_baseline_name            VARCHAR(255) NOT NULL,
    coverage_baseline_coverage_type   VARCHAR(16)  NOT NULL,
    coverage_baseline_case_ids        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    coverage_baseline_asset_group_id  VARCHAR(255) NOT NULL,
    coverage_baseline_description     TEXT,
    coverage_baseline_soc_query_delay_seconds INTEGER NOT NULL DEFAULT 60,
    coverage_baseline_created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    coverage_baseline_updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_coverage_baseline_asset_group
        FOREIGN KEY (coverage_baseline_asset_group_id)
        REFERENCES asset_groups (asset_group_id)
);

CREATE INDEX idx_coverage_baselines_coverage_type
    ON coverage_baselines (coverage_baseline_coverage_type);

COMMENT ON TABLE coverage_baselines IS '覆盖度基线 —— PR C3 边界覆盖度子模块';
COMMENT ON COLUMN coverage_baselines.coverage_baseline_coverage_type IS 'lowercase enum: boundary (§3.1) / traffic (§4.1)';
COMMENT ON COLUMN coverage_baselines.coverage_baseline_soc_query_delay_seconds IS 'inject 完成后等待 SOC 消费 + 上送告警的窗口（秒，默认 60）';

-- ============================================================
-- 3. coverage_runs —— 覆盖度评估任务
-- ============================================================
CREATE TABLE coverage_runs (
    coverage_run_id                       VARCHAR(255) PRIMARY KEY,
    coverage_run_baseline_id              VARCHAR(255) NOT NULL,
    coverage_run_status                   VARCHAR(16)  NOT NULL DEFAULT 'pending',
    coverage_run_total_cells              INTEGER      NOT NULL DEFAULT 0,
    coverage_run_hit_count                INTEGER      NOT NULL DEFAULT 0,
    coverage_run_miss_count               INTEGER      NOT NULL DEFAULT 0,
    coverage_run_timeout_count            INTEGER      NOT NULL DEFAULT 0,
    coverage_run_out_of_scope_count       INTEGER      NOT NULL DEFAULT 0,
    coverage_run_started_at               TIMESTAMP WITH TIME ZONE,
    coverage_run_finished_at              TIMESTAMP WITH TIME ZONE,
    coverage_run_error_message            TEXT,
    coverage_run_created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    coverage_run_updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_coverage_run_baseline
        FOREIGN KEY (coverage_run_baseline_id)
        REFERENCES coverage_baselines (coverage_baseline_id)
);

CREATE INDEX idx_coverage_runs_status_started_at
    ON coverage_runs (coverage_run_status, coverage_run_started_at DESC);
CREATE INDEX idx_coverage_runs_baseline_id
    ON coverage_runs (coverage_run_baseline_id);

COMMENT ON TABLE coverage_runs IS '覆盖度评估任务 —— PR C3';
COMMENT ON COLUMN coverage_runs.coverage_run_status IS 'lowercase enum: pending / running / completed / failed / cancelled';

-- ============================================================
-- 4. coverage_results —— 4 态单元格（asset × policy）
-- ============================================================
CREATE TABLE coverage_results (
    coverage_result_id              VARCHAR(255) PRIMARY KEY,
    coverage_result_run_id          VARCHAR(255) NOT NULL,
    coverage_result_asset_id        VARCHAR(255) NOT NULL,
    coverage_result_policy_id       VARCHAR(255) NOT NULL,
    coverage_result_case_id         VARCHAR(255),
    coverage_result_hit_state       VARCHAR(16)  NOT NULL,
    coverage_result_alert_rule_id   VARCHAR(255),
    coverage_result_observed_at     TIMESTAMP WITH TIME ZONE,
    coverage_result_error_message   TEXT,
    coverage_result_created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    coverage_result_updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_coverage_result_run
        FOREIGN KEY (coverage_result_run_id)
        REFERENCES coverage_runs (coverage_run_id) ON DELETE CASCADE,
    CONSTRAINT uq_coverage_result_run_asset_policy
        UNIQUE (coverage_result_run_id, coverage_result_asset_id, coverage_result_policy_id)
);

CREATE INDEX idx_coverage_results_run_hit_state
    ON coverage_results (coverage_result_run_id, coverage_result_hit_state);
CREATE INDEX idx_coverage_results_asset
    ON coverage_results (coverage_result_asset_id);
CREATE INDEX idx_coverage_results_policy
    ON coverage_results (coverage_result_policy_id);

COMMENT ON TABLE coverage_results IS '4 态覆盖单元格 —— PR C3';
COMMENT ON COLUMN coverage_results.coverage_result_hit_state IS 'lowercase enum: hit / miss / timeout / out_of_scope';
COMMENT ON COLUMN coverage_results.coverage_result_alert_rule_id IS '仅 hit 态有：匹配到的 SOC 告警 rule_id';

COMMIT;
