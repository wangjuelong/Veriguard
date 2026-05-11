-- V5: Phase 12c-Biii 攻击链路动态用例集
--
-- 加 attack_chains.dynamic_filter JSONB 列（与 asset_groups.asset_group_dynamic_filter 同模式）.
-- 默认 {"mode":"and","filters":[]} 表示空 filter，与现有 chain 行为完全兼容（无动态 contracts）.
-- 由 Filters.FilterGroup Java 对象 Jackson 序列化驱动；columnDefinition jsonb 与 Hibernate JsonType 配合.

ALTER TABLE attack_chains
    ADD COLUMN dynamic_filter JSONB NOT NULL
    DEFAULT '{"mode":"and","filters":[]}'::jsonb;
