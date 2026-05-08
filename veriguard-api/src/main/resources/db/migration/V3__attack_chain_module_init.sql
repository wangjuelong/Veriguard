-- V3: Attack Chain module init (PRD §2.4 Phase 1 — schema 大清洗)
-- TRUNCATE 全部 scenarios 数据 + DROP 演练遗留邮件字段
-- + RENAME 表/列 对齐 Phase 0 Java 改名
-- + ADD 新字段（execution_mode/repeat_*/verdict_*/validation_parameter_set_id/soc_correlation_rules）
-- + CREATE 新表（validation_parameter_sets / attack_chain_link_expectations / link_expectation_traces）
-- + 种子 3 个 ParameterSet 模板
--
-- 注意：V2 已被 Phase 11.5 占用（drop channel/challenge/article/email），所以本次用 V3。
-- Phase 1 只动模板/运行时核心 6 张表 + 与之直接关联的 m2m 表/反向 FK 列；lessons 等遗留表保留。

BEGIN;

-- ============================================================
-- 1. 大清洗：清空所有 scenarios 相关数据
-- ============================================================
-- TRUNCATE CASCADE 会自动级联清掉 exercises / injects / 关联表 / lessons_categories / objectives / pauses / logs / variables 等
TRUNCATE TABLE scenarios CASCADE;
-- 单独清掉 atomic-testing 与孤立 injects（没有 scenario 父）
TRUNCATE TABLE injects CASCADE;
-- exercises 也要单独清（reports_exercises 关联）
TRUNCATE TABLE exercises CASCADE;

-- ============================================================
-- 2. DROP 邮件 / 演练遗留字段
-- ============================================================
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_message_header;
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_message_footer;
ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_mail_from;

ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_message_header;
ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_message_footer;
ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_mail_from;

DROP TABLE IF EXISTS scenario_mails_reply_to CASCADE;
DROP TABLE IF EXISTS exercise_mails_reply_to CASCADE;

-- ============================================================
-- 3. RENAME 表（顺序：先 m2m / 子表，再父表）
-- ============================================================
ALTER TABLE injects_dependencies        RENAME TO attack_chain_edges;
ALTER TABLE injects_expectations_traces RENAME TO node_expectation_traces;
ALTER TABLE injects_expectations        RENAME TO attack_chain_node_expectations;
ALTER TABLE injects_documents           RENAME TO attack_chain_nodes_documents;
ALTER TABLE injects_tags                RENAME TO attack_chain_nodes_tags;
ALTER TABLE injects_teams               RENAME TO attack_chain_nodes_teams;
ALTER TABLE injects_assets              RENAME TO attack_chain_nodes_assets;
ALTER TABLE injects_asset_groups        RENAME TO attack_chain_nodes_asset_groups;

ALTER TABLE scenarios_documents         RENAME TO attack_chains_documents;
ALTER TABLE scenarios_tags              RENAME TO attack_chains_tags;
ALTER TABLE scenarios_teams             RENAME TO attack_chains_teams;
ALTER TABLE scenarios_teams_users       RENAME TO attack_chains_teams_users;
ALTER TABLE scenarios_exercises         RENAME TO attack_chains_runs;

ALTER TABLE exercises_documents         RENAME TO attack_chain_runs_documents;
ALTER TABLE exercises_tags              RENAME TO attack_chain_runs_tags;
ALTER TABLE exercises_teams             RENAME TO attack_chain_runs_teams;
ALTER TABLE exercises_teams_users       RENAME TO attack_chain_runs_teams_users;

ALTER TABLE injects                     RENAME TO attack_chain_nodes;
ALTER TABLE exercises                   RENAME TO attack_chain_runs;
ALTER TABLE scenarios                   RENAME TO attack_chains;

-- ============================================================
-- 4. RENAME 列（基于 V1__Init.sql 的实际列）
-- ============================================================

-- attack_chains（原 scenarios）
ALTER TABLE attack_chains RENAME COLUMN scenario_id                  TO attack_chain_id;
ALTER TABLE attack_chains RENAME COLUMN scenario_name                TO attack_chain_name;
ALTER TABLE attack_chains RENAME COLUMN scenario_description         TO attack_chain_description;
ALTER TABLE attack_chains RENAME COLUMN scenario_subtitle            TO attack_chain_subtitle;
ALTER TABLE attack_chains RENAME COLUMN scenario_created_at          TO attack_chain_created_at;
ALTER TABLE attack_chains RENAME COLUMN scenario_updated_at          TO attack_chain_updated_at;
ALTER TABLE attack_chains RENAME COLUMN scenario_recurrence          TO attack_chain_recurrence;
ALTER TABLE attack_chains RENAME COLUMN scenario_recurrence_start    TO attack_chain_recurrence_start;
ALTER TABLE attack_chains RENAME COLUMN scenario_recurrence_end      TO attack_chain_recurrence_end;
ALTER TABLE attack_chains RENAME COLUMN scenario_category            TO attack_chain_category;
ALTER TABLE attack_chains RENAME COLUMN scenario_severity            TO attack_chain_severity;
ALTER TABLE attack_chains RENAME COLUMN scenario_main_focus          TO attack_chain_main_focus;
ALTER TABLE attack_chains RENAME COLUMN scenario_external_reference  TO attack_chain_external_reference;
ALTER TABLE attack_chains RENAME COLUMN scenario_external_url        TO attack_chain_external_url;
ALTER TABLE attack_chains RENAME COLUMN scenario_lessons_anonymized  TO attack_chain_lessons_anonymized;
ALTER TABLE attack_chains RENAME COLUMN scenario_custom_dashboard    TO attack_chain_custom_dashboard;
ALTER TABLE attack_chains RENAME COLUMN scenario_dependencies        TO attack_chain_dependencies;
ALTER TABLE attack_chains RENAME COLUMN scenario_type_affinity       TO attack_chain_type_affinity;

-- attack_chain_nodes（原 injects）
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_id                   TO node_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_user                 TO node_user;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_title                TO node_title;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_description          TO node_description;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_content              TO node_content;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_all_teams            TO node_all_teams;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_enabled              TO node_enabled;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_depends_duration     TO node_depends_duration;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_depends_from_another TO node_depends_from_another;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_exercise             TO node_attack_chain_run_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_scenario             TO node_attack_chain_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_created_at           TO node_created_at;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_updated_at           TO node_updated_at;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_country              TO node_country;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_city                 TO node_city;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_injector_contract    TO node_contract_id;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_assets               TO node_assets_legacy;
ALTER TABLE attack_chain_nodes RENAME COLUMN injects_asset_groups        TO node_asset_groups_legacy;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_trigger_now_date     TO node_trigger_now_date;
ALTER TABLE attack_chain_nodes RENAME COLUMN inject_collect_status       TO node_collect_status;

-- attack_chain_edges（原 injects_dependencies）
ALTER TABLE attack_chain_edges RENAME COLUMN inject_parent_id     TO parent_node_id;
ALTER TABLE attack_chain_edges RENAME COLUMN inject_children_id   TO child_node_id;
ALTER TABLE attack_chain_edges RENAME COLUMN dependency_condition  TO edge_condition;
ALTER TABLE attack_chain_edges RENAME COLUMN dependency_created_at TO edge_created_at;
ALTER TABLE attack_chain_edges RENAME COLUMN dependency_updated_at TO edge_updated_at;

-- attack_chain_runs（原 exercises）
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_id                TO run_id;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_name              TO run_name;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_subtitle          TO run_subtitle;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_description       TO run_description;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_status            TO run_status;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_start_date        TO run_start_date;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_end_date          TO run_end_date;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_created_at        TO run_created_at;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_updated_at        TO run_updated_at;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_pause_date        TO run_pause_date;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_launch_order      TO run_launch_order;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_category          TO run_category;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_severity          TO run_severity;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_main_focus        TO run_main_focus;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_logo_dark         TO run_logo_dark;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_logo_light        TO run_logo_light;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_lessons_anonymized TO run_lessons_anonymized;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_custom_dashboard  TO run_custom_dashboard;
ALTER TABLE attack_chain_runs RENAME COLUMN exercise_security_coverage TO run_security_coverage;

-- attack_chain_node_expectations（原 injects_expectations）
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_id             TO node_expectation_id;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_created_at     TO node_expectation_created_at;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_updated_at     TO node_expectation_updated_at;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_type           TO node_expectation_type;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_score          TO node_expectation_score;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_expected_score TO node_expectation_expected_score;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_name           TO node_expectation_name;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_description    TO node_expectation_description;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_group          TO node_expectation_group;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_results        TO node_expectation_results;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expectation_signatures     TO node_expectation_signatures;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_expiration_time            TO node_expectation_expiration_time;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN exercise_id                       TO run_id;
ALTER TABLE attack_chain_node_expectations RENAME COLUMN inject_id                         TO node_id;

-- node_expectation_traces（原 injects_expectations_traces）
ALTER TABLE node_expectation_traces RENAME COLUMN inject_expectation_trace_id          TO trace_id;
ALTER TABLE node_expectation_traces RENAME COLUMN inject_expectation_trace_expectation TO trace_expectation_id;
ALTER TABLE node_expectation_traces RENAME COLUMN inject_expectation_trace_source_id   TO trace_source_id;
ALTER TABLE node_expectation_traces RENAME COLUMN inject_expectation_trace_alert_name  TO trace_alert_name;
ALTER TABLE node_expectation_traces RENAME COLUMN inject_expectation_trace_alert_link  TO trace_alert_link;
ALTER TABLE node_expectation_traces RENAME COLUMN inject_expectation_trace_date        TO trace_date;
ALTER TABLE node_expectation_traces RENAME COLUMN inject_expectation_trace_created_at  TO trace_created_at;
ALTER TABLE node_expectation_traces RENAME COLUMN inject_expectation_trace_updated_at  TO trace_updated_at;

-- m2m FK 列改名
ALTER TABLE attack_chain_nodes_documents     RENAME COLUMN inject_id   TO node_id;
ALTER TABLE attack_chain_nodes_tags          RENAME COLUMN inject_id   TO node_id;
ALTER TABLE attack_chain_nodes_teams         RENAME COLUMN inject_id   TO node_id;
ALTER TABLE attack_chain_nodes_assets        RENAME COLUMN inject_id   TO node_id;
ALTER TABLE attack_chain_nodes_asset_groups  RENAME COLUMN inject_id   TO node_id;

ALTER TABLE attack_chains_documents     RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_tags          RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_teams         RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_teams_users   RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_runs          RENAME COLUMN scenario_id TO attack_chain_id;
ALTER TABLE attack_chains_runs          RENAME COLUMN exercise_id TO run_id;

ALTER TABLE attack_chain_runs_documents     RENAME COLUMN exercise_id TO run_id;
ALTER TABLE attack_chain_runs_tags          RENAME COLUMN exercise_id TO run_id;
ALTER TABLE attack_chain_runs_teams         RENAME COLUMN exercise_id TO run_id;
ALTER TABLE attack_chain_runs_teams_users   RENAME COLUMN exercise_id TO run_id;

-- ============================================================
-- 5. ADD 新字段
-- ============================================================
ALTER TABLE attack_chains ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'STOP_ON_BLOCK';
ALTER TABLE attack_chains ADD COLUMN validation_parameter_set_id UUID;
ALTER TABLE attack_chains ADD COLUMN soc_correlation_rules JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE attack_chain_nodes ADD COLUMN repeat_count INT NOT NULL DEFAULT 1
    CHECK (repeat_count >= 1);
ALTER TABLE attack_chain_nodes ADD COLUMN repeat_interval_seconds BIGINT NOT NULL DEFAULT 0
    CHECK (repeat_interval_seconds >= 0);
ALTER TABLE attack_chain_nodes ADD COLUMN validation_parameter_set_id UUID;
ALTER TABLE attack_chain_nodes ADD COLUMN node_state VARCHAR(32);
ALTER TABLE attack_chain_nodes ADD COLUMN current_iteration INT NOT NULL DEFAULT 0
    CHECK (current_iteration >= 0);

ALTER TABLE attack_chain_runs ADD COLUMN verdict_prevention VARCHAR(32);
ALTER TABLE attack_chain_runs ADD COLUMN verdict_detection VARCHAR(32);
ALTER TABLE attack_chain_runs ADD COLUMN verdict_computed_at TIMESTAMPTZ;

-- attack_chain_edges：替代复合主键为单列 UUID + 唯一约束
ALTER TABLE attack_chain_edges ADD COLUMN edge_id UUID;
UPDATE attack_chain_edges SET edge_id = gen_random_uuid() WHERE edge_id IS NULL;
ALTER TABLE attack_chain_edges ALTER COLUMN edge_id SET NOT NULL;
ALTER TABLE attack_chain_edges DROP CONSTRAINT IF EXISTS injects_dependencies_pkey;
ALTER TABLE attack_chain_edges ADD PRIMARY KEY (edge_id);
ALTER TABLE attack_chain_edges ADD CONSTRAINT uq_edge_parent_child UNIQUE (parent_node_id, child_node_id);

-- ============================================================
-- 6. CREATE 新表
-- ============================================================
CREATE TABLE validation_parameter_sets (
    parameter_set_id UUID PRIMARY KEY,
    name TEXT UNIQUE NOT NULL,
    description TEXT,
    is_template BOOLEAN NOT NULL DEFAULT FALSE,
    default_targets JSONB NOT NULL DEFAULT '[]'::jsonb,
    prevention_expected_score INT NOT NULL DEFAULT 100
        CHECK (prevention_expected_score BETWEEN 0 AND 100),
    prevention_expiration_seconds INT NOT NULL DEFAULT 1800
        CHECK (prevention_expiration_seconds > 0),
    detection_expected_score INT NOT NULL DEFAULT 100
        CHECK (detection_expected_score BETWEEN 0 AND 100),
    detection_expiration_seconds INT NOT NULL DEFAULT 1800
        CHECK (detection_expiration_seconds > 0),
    soc_correlation_rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE validation_parameter_set_tags (
    parameter_set_id UUID NOT NULL
        REFERENCES validation_parameter_sets(parameter_set_id) ON DELETE CASCADE,
    tag_id VARCHAR(255) NOT NULL REFERENCES tags(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (parameter_set_id, tag_id)
);

CREATE TABLE attack_chain_link_expectations (
    link_expectation_id UUID PRIMARY KEY,
    attack_chain_run_id VARCHAR(255) NOT NULL
        REFERENCES attack_chain_runs(run_id) ON DELETE CASCADE,
    soc_rule_ref JSONB NOT NULL,
    score INT NOT NULL DEFAULT 0,
    expected_score INT NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expiration_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE link_expectation_traces (
    trace_id UUID PRIMARY KEY,
    link_expectation_id UUID NOT NULL
        REFERENCES attack_chain_link_expectations(link_expectation_id) ON DELETE CASCADE,
    incident_id TEXT,
    correlation_rule_name TEXT,
    triggered_at TIMESTAMPTZ NOT NULL,
    score_delta INT NOT NULL,
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 7. FK + CHECK 约束
-- ============================================================
ALTER TABLE attack_chains ADD CONSTRAINT fk_chain_param_set
    FOREIGN KEY (validation_parameter_set_id)
    REFERENCES validation_parameter_sets(parameter_set_id) ON DELETE RESTRICT;

ALTER TABLE attack_chain_nodes ADD CONSTRAINT fk_node_param_set
    FOREIGN KEY (validation_parameter_set_id)
    REFERENCES validation_parameter_sets(parameter_set_id) ON DELETE RESTRICT;

-- 模板与运行时互斥：node 必须有且仅有一个所有者
-- 注：TRUNCATE 已经清空 injects/scenarios/exercises，此约束对将来插入的所有节点生效。
-- atomic-testing（两边都为 null）已不再支持，所有节点必须挂在 chain 或 run 上。
ALTER TABLE attack_chain_nodes ADD CONSTRAINT chk_node_owner
    CHECK ((node_attack_chain_id IS NULL) <> (node_attack_chain_run_id IS NULL));

-- ============================================================
-- 8. 种子：3 个 ValidationParameterSet 模板
-- ============================================================
INSERT INTO validation_parameter_sets (
    parameter_set_id, name, description, is_template,
    prevention_expected_score, prevention_expiration_seconds,
    detection_expected_score, detection_expiration_seconds
) VALUES
    (gen_random_uuid(), '严格', '生产环境严格验证：100% 防御要求 + 30 分钟超时', true, 100, 1800, 100, 1800),
    (gen_random_uuid(), '宽松', '日常巡检：80% 防御要求 + 15 分钟超时', true, 80, 900, 80, 900),
    (gen_random_uuid(), '快速演练', '红队演练：50% 防御要求 + 5 分钟超时', true, 50, 300, 50, 300);

-- ============================================================
-- 9. 索引
-- ============================================================
CREATE INDEX idx_node_attack_chain ON attack_chain_nodes (node_attack_chain_id)
    WHERE node_attack_chain_id IS NOT NULL;
CREATE INDEX idx_node_attack_chain_run ON attack_chain_nodes (node_attack_chain_run_id)
    WHERE node_attack_chain_run_id IS NOT NULL;
CREATE INDEX idx_edge_parent ON attack_chain_edges (parent_node_id);
CREATE INDEX idx_edge_child ON attack_chain_edges (child_node_id);
CREATE INDEX idx_link_expectation_run ON attack_chain_link_expectations (attack_chain_run_id);
CREATE INDEX idx_param_set_template ON validation_parameter_sets (is_template)
    WHERE is_template = true;

COMMIT;
