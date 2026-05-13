-- V16: IPv6 安全验证系统 边界策略有效性常态化监控 PR C4 — 招标 §3.2
--
-- 设计要点：
--   - 监控本身不重新实现 baseline → inject → SOC 4 态链路；它是 Quartz 周期触发器，
--     按 cron 调用 PR C3 CoverageRunner.runAsync(baselineId)，并把每次 run 元数据
--     落到 monitoring_run_history（含命中计数 + 关联 coverage_run_id）
--   - 不引入 Elasticsearch 索引：监控历史是结构化时间序列，PG 表足以支撑趋势查询
--   - cron 最小粒度 1 小时（招标"按天 / 按小时"要求的下界），由 Java 端 CronValidator 强制
--   - history.status 三态：triggered / completed / failed
--     · triggered  —— Quartz 触发后 + 异步 coverage_run 启动；待回填 4 计数
--     · completed  —— 关联的 coverage_run 终结（completed/failed/cancelled），4 计数已回填
--     · failed     —— 触发本身失败（baseline 已删 / runner 抛异常）；不会有关联 coverage_run_id
--
-- 表：
--   monitoring_jobs         监控任务 = baseline_id + cron + enabled
--   monitoring_run_history  每次 cron 触发的执行历史 + 计数

BEGIN;

-- ============================================================
-- 1. monitoring_jobs —— 监控任务
-- ============================================================
CREATE TABLE monitoring_jobs (
    monitoring_job_id              VARCHAR(255) PRIMARY KEY,
    monitoring_job_name            VARCHAR(255) NOT NULL,
    monitoring_job_baseline_id     VARCHAR(255) NOT NULL,
    monitoring_job_cron_expression VARCHAR(64)  NOT NULL,
    monitoring_job_enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    monitoring_job_description     TEXT,
    monitoring_job_last_triggered_at TIMESTAMP WITH TIME ZONE,
    monitoring_job_created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    monitoring_job_updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_monitoring_job_baseline
        FOREIGN KEY (monitoring_job_baseline_id)
        REFERENCES coverage_baselines (coverage_baseline_id)
);

CREATE INDEX idx_monitoring_jobs_enabled_created
    ON monitoring_jobs (monitoring_job_enabled, monitoring_job_created_at DESC);
CREATE INDEX idx_monitoring_jobs_baseline_id
    ON monitoring_jobs (monitoring_job_baseline_id);

COMMENT ON TABLE monitoring_jobs IS '边界策略监控任务 —— PR C4 招标 §3.2 常态化监控';
COMMENT ON COLUMN monitoring_jobs.monitoring_job_cron_expression IS 'Quartz 6 字段 cron 表达式；最小触发粒度 1 小时（CronValidator 强制）';
COMMENT ON COLUMN monitoring_jobs.monitoring_job_last_triggered_at IS '最近一次 Quartz 触发该 job 的时间（仅观测用，不参与调度判定）';

-- ============================================================
-- 2. monitoring_run_history —— 每次 cron 触发的历史记录
-- ============================================================
CREATE TABLE monitoring_run_history (
    monitoring_run_history_id                  VARCHAR(255) PRIMARY KEY,
    monitoring_run_history_job_id              VARCHAR(255) NOT NULL,
    monitoring_run_history_coverage_run_id     VARCHAR(255),
    monitoring_run_history_scheduled_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    monitoring_run_history_finished_at         TIMESTAMP WITH TIME ZONE,
    monitoring_run_history_status              VARCHAR(16)  NOT NULL DEFAULT 'triggered',
    monitoring_run_history_hit_count           INTEGER,
    monitoring_run_history_miss_count          INTEGER,
    monitoring_run_history_timeout_count       INTEGER,
    monitoring_run_history_out_of_scope_count  INTEGER,
    monitoring_run_history_total_count         INTEGER,
    monitoring_run_history_error_message       TEXT,
    monitoring_run_history_created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    monitoring_run_history_updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_monitoring_run_history_job
        FOREIGN KEY (monitoring_run_history_job_id)
        REFERENCES monitoring_jobs (monitoring_job_id) ON DELETE CASCADE,
    CONSTRAINT fk_monitoring_run_history_coverage_run
        FOREIGN KEY (monitoring_run_history_coverage_run_id)
        REFERENCES coverage_runs (coverage_run_id) ON DELETE SET NULL
);

CREATE INDEX idx_monitoring_run_history_job_scheduled
    ON monitoring_run_history (monitoring_run_history_job_id,
                               monitoring_run_history_scheduled_at DESC);
CREATE INDEX idx_monitoring_run_history_status
    ON monitoring_run_history (monitoring_run_history_status);
CREATE INDEX idx_monitoring_run_history_coverage_run
    ON monitoring_run_history (monitoring_run_history_coverage_run_id);

COMMENT ON TABLE monitoring_run_history IS '监控任务执行历史 —— PR C4；每条 = 一次 cron 触发的元数据 + 4 态计数';
COMMENT ON COLUMN monitoring_run_history.monitoring_run_history_status IS 'lowercase enum: triggered / completed / failed';
COMMENT ON COLUMN monitoring_run_history.monitoring_run_history_coverage_run_id IS '关联的 coverage_run.id（异步触发后填）；仅 status=triggered/completed 有值';

COMMIT;
