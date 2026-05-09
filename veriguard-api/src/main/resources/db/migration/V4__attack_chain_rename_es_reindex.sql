-- V4: Phase 12b-A1 后端 JSON 字段全栈改名后置 migration
--
-- 1. 强制 ES 重建索引：旧 indexing_status_type ('scenario'/'simulation'/'inject')
--    与新 @Indexable(index=...) 值 ('attack_chains'/'attack_chain_runs'/'nodes') 不匹配，
--    清掉旧记录后下次启动 EsCollector 会全量 reindex。
--
-- 2. 重写 widgets.widget_config 内的 base_entity filter 值：
--    旧 widget 配置里 {"key":"base_entity","values":["inject"]} 类用法对应
--    WidgetUtils.getColumnsFromBaseEntityName 的旧 case；switch 已改名后旧值会落到 default
--    返回 List.of("id")，dashboard 列表展示为单列断裂。
--    text 替换基于 jsonb canonical 形式（无空格），仅 base_entity filter 命中。

BEGIN;

-- 1. ES reindex
--    旧 type 字符串 ('scenario'/'simulation'/'inject')
--    含一次失败的过渡 type (如果某 instance 跑过早期 singular 版本: 'attack_chain'/'attack_chain_run'/'node')
--    下次启动 EsCollector 会按当前 @Indexable.index 复数值 ('attack_chains'/'attack_chain_runs'/'nodes') 重建。
DELETE FROM indexing_status
WHERE indexing_status_type IN (
  'scenario', 'simulation', 'inject',
  'attack_chain', 'attack_chain_run', 'node'
);

-- 2. widget_config base_entity rename
UPDATE widgets
SET widget_config = REPLACE(
    REPLACE(
      REPLACE(
        widget_config::text,
        '"key":"base_entity","values":["inject"]',
        '"key":"base_entity","values":["node"]'
      ),
      '"key":"base_entity","values":["scenario"]',
      '"key":"base_entity","values":["attack_chain"]'
    ),
    '"key":"base_entity","values":["simulation"]',
    '"key":"base_entity","values":["attack_chain_run"]'
)::jsonb
WHERE widget_config::text LIKE '%"base_entity"%';

COMMIT;
