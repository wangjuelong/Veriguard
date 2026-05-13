-- V11: IPv6 安全验证系统 ★2 攻击组合 PR D1 — 绕过维度库 + 攻击组合模板
--
-- 招标条款：§3.6 动态攻击组合 250 类 × 30 000 组合 + 聚类 + 分级
-- 本迁移对应 PR D1（数据底座），新增两张表：
--   - bypass_dimensions              绕过维度库（120 项首批落地）
--   - attack_combination_templates   攻击组合模板（base_attack_type × bypass_dimension）
--
-- 后续 PR D2 将再加 attack_combination_runs / attack_combination_results 两张运行时表。
--
-- 命名约定：snake_case + 复数表名（与 injectors_contracts / smtp_profiles 一致）.
-- 枚举校验交 Java 层（Enum.name() 直驱 VARCHAR 列），DB 不加 CHECK 约束便于扩展.
-- 暂未引入 tenant_id —— 现有同类表（veriguard_sandboxes / smtp_profiles）均无 tenant_id；
-- 真正需要多租户时统一在跨表迁移中加入，避免本次单点污染.

BEGIN;

-- ============================================================
-- 1. 绕过维度库
-- ============================================================
-- 维度类别（category）lowercase 与 Java enum BypassDimensionCategory 直接对齐：
--   encoding     编码（base64 / url / hex / utf-7 / utf-8-bom 等）
--   chunking     分块（HTTP chunked / 段间长延迟）
--   casing       大小写混淆（mixed / random / unicode 大小写映射）
--   param_order  参数顺序扰乱
--   noise        噪声前后缀（空白 / tab / 控制字符 / 随机数据）
--   unicode      Unicode 变形（全角 / 同形异义 / 零宽）
--   comment      注释注入（SQL # / -- / /* */，XSS <!-- -->）
--   other        其他（Host header / smuggling / case-flip 等专有手段）
CREATE TABLE bypass_dimensions (
    bypass_dimension_id              VARCHAR(255) PRIMARY KEY,
    bypass_dimension_name            VARCHAR(128) NOT NULL,
    bypass_dimension_category        VARCHAR(32)  NOT NULL,
    bypass_dimension_description     TEXT,
    bypass_dimension_transform_type  VARCHAR(64)  NOT NULL,
    bypass_dimension_transform_config JSONB       NOT NULL DEFAULT '{}'::jsonb,
    bypass_dimension_created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bypass_dimension_updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_bypass_dimensions_name      ON bypass_dimensions (bypass_dimension_name);
CREATE INDEX        idx_bypass_dimensions_category  ON bypass_dimensions (bypass_dimension_category);

-- ============================================================
-- 2. 攻击组合模板
-- ============================================================
-- 每条模板 = 一种 base_attack_type × 一项 bypass_dimension 的笛卡尔积单元.
-- base_attack_type 字符串域来自 §3.5 attack_category 枚举（复用，不在此迁移建外键避免循环）.
-- combined_payload_template 是参数化模板字符串，由 PayloadTransform 在生成期填充实际 payload.
CREATE TABLE attack_combination_templates (
    attack_combination_template_id            VARCHAR(255) PRIMARY KEY,
    attack_combination_template_name          VARCHAR(128) NOT NULL,
    attack_combination_template_base_attack_type VARCHAR(64) NOT NULL,
    attack_combination_template_bypass_dimension_id VARCHAR(255) NOT NULL
        REFERENCES bypass_dimensions (bypass_dimension_id) ON DELETE CASCADE,
    attack_combination_template_combined_payload_template TEXT,
    attack_combination_template_description   TEXT,
    attack_combination_template_created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attack_combination_template_updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attack_combination_template_base_dim
        UNIQUE (attack_combination_template_base_attack_type, attack_combination_template_bypass_dimension_id)
);

CREATE INDEX idx_attack_combination_template_base_type
    ON attack_combination_templates (attack_combination_template_base_attack_type);
CREATE INDEX idx_attack_combination_template_dim
    ON attack_combination_templates (attack_combination_template_bypass_dimension_id);

COMMIT;
