-- V13: IPv6 安全验证系统 ★2 攻击组合 PR D3 — 聚类结果表
--
-- 招标条款：§3.6 30 000 组合跑完后自动按 (设备, 资产) 聚类「未防护」（hit_state=miss）项.
-- 本迁移在 V12（attack_combination_runs + attack_combination_results）之上新增一张聚类表：
--   - attack_combination_clusters  单 (run × cluster_dim × cluster_key) 行
--
-- 命名约定：snake_case + 复数表名；ID 用 VARCHAR(255)（与 V11 / V12 一致）；
-- 枚举校验交 Java 层（lowercase Enum.name() 直驱 VARCHAR）.
--
-- 聚类对象：仅 result.hit_state = 'miss' 的未防护行（招标"得出不能防护的攻击组合"）.
-- hit / timeout / failed 不参与聚类，但 total_in_cluster 列会汇总 cluster 内所有 result 数以便 UI 展示.

BEGIN;

-- ============================================================
-- 攻击组合聚类结果
-- ============================================================
-- cluster_dim（lowercase）：
--   asset  —— GROUP BY result.asset_id；cluster_key = asset_id；cluster_label = asset_name
--   device —— GROUP BY 推导 device_key（Endpoint.hostname / asset_name fallback）
CREATE TABLE attack_combination_clusters (
    attack_combination_cluster_id                  VARCHAR(255) PRIMARY KEY,
    attack_combination_cluster_run_id              VARCHAR(255) NOT NULL
        REFERENCES attack_combination_runs (attack_combination_run_id) ON DELETE CASCADE,
    attack_combination_cluster_dim                 VARCHAR(16)  NOT NULL,
    attack_combination_cluster_key                 VARCHAR(255) NOT NULL,
    attack_combination_cluster_label               VARCHAR(512),
    attack_combination_cluster_miss_count          INTEGER      NOT NULL DEFAULT 0,
    attack_combination_cluster_total_in_cluster    INTEGER      NOT NULL DEFAULT 0,
    attack_combination_cluster_payload_samples     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    attack_combination_cluster_top_base_attack_types JSONB      NOT NULL DEFAULT '[]'::jsonb,
    attack_combination_cluster_top_bypass_dimensions JSONB      NOT NULL DEFAULT '[]'::jsonb,
    attack_combination_cluster_computed_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attack_combination_cluster_created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attack_combination_cluster_updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attack_combination_cluster_run_dim_key
        UNIQUE (attack_combination_cluster_run_id,
                attack_combination_cluster_dim,
                attack_combination_cluster_key)
);

CREATE INDEX idx_attack_combination_clusters_run_dim_miss
    ON attack_combination_clusters (
        attack_combination_cluster_run_id,
        attack_combination_cluster_dim,
        attack_combination_cluster_miss_count DESC);

COMMIT;
