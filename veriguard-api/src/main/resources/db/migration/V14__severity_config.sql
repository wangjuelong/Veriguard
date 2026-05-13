-- V14: IPv6 安全验证系统 ★2 攻击组合 PR D4 — 分级算法配置 + cluster 分级字段
--
-- 招标条款：§3.6 "得出不能防护的攻击组合并按严重度分级"。
-- 默认 4 级：高 / 中 / 低 / 信息，可由客户在 UI 调整阈值与命名 / 颜色。
--
-- 设计决策（与 PR D1-D3 一致）：
--   - 现有 combination 模块未引入 tenant_id 列，整个 deploy 视为单租户；
--     severity_configs 落为「单行 singleton」表（应用层保证至多一行），
--     无 tenant_id 列，避免引入此 PR 范围外的 multi-tenant 基建。
--   - ID / 外键均沿用 VARCHAR(255)（与 V11/V12/V13 主键一致）。
--   - 阈值用 NUMERIC(5,2)；权重用 NUMERIC(4,3)（三者和必须 = 1.0）。
--   - 三档阈值定义为下界：score > critical_threshold → critical；
--                       score > high_threshold     → high；
--                       score > medium_threshold   → medium；
--                       否则                       → info。
--
-- 表：
--   severity_configs              单行配置（权重 + 阈值 + 标签 + 颜色）
-- 列扩展：
--   attack_combination_clusters   新增 severity_score / severity_level（D4 计算后回填）

BEGIN;

-- ============================================================
-- 1. 分级配置（singleton）
-- ============================================================
CREATE TABLE severity_configs (
    severity_config_id                  VARCHAR(255) PRIMARY KEY,
    severity_config_miss_count_weight   NUMERIC(4,3) NOT NULL DEFAULT 0.500,
    severity_config_attack_type_weight  NUMERIC(4,3) NOT NULL DEFAULT 0.300,
    severity_config_asset_sensitivity_weight NUMERIC(4,3) NOT NULL DEFAULT 0.200,
    severity_config_critical_threshold  NUMERIC(5,2) NOT NULL DEFAULT 70.00,
    severity_config_high_threshold      NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    severity_config_medium_threshold    NUMERIC(5,2) NOT NULL DEFAULT 10.00,
    severity_config_critical_label      VARCHAR(32)  NOT NULL DEFAULT '高',
    severity_config_high_label          VARCHAR(32)  NOT NULL DEFAULT '中',
    severity_config_medium_label        VARCHAR(32)  NOT NULL DEFAULT '低',
    severity_config_info_label          VARCHAR(32)  NOT NULL DEFAULT '信息',
    severity_config_critical_color      VARCHAR(16)  NOT NULL DEFAULT '#dc2626',
    severity_config_high_color          VARCHAR(16)  NOT NULL DEFAULT '#f97316',
    severity_config_medium_color        VARCHAR(16)  NOT NULL DEFAULT '#eab308',
    severity_config_info_color          VARCHAR(16)  NOT NULL DEFAULT '#3b82f6',
    severity_config_created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    severity_config_updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. cluster 分级字段
-- ============================================================
ALTER TABLE attack_combination_clusters
    ADD COLUMN attack_combination_cluster_severity_score NUMERIC(5,2),
    ADD COLUMN attack_combination_cluster_severity_level VARCHAR(16);

CREATE INDEX idx_attack_combination_clusters_run_severity
    ON attack_combination_clusters (
        attack_combination_cluster_run_id,
        attack_combination_cluster_severity_level);

COMMIT;
