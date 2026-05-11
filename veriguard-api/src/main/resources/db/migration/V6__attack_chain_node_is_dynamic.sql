-- V6: Phase 12c-Biii 动态节点持久化标记（A4 design fix 2026-05-11）
--
-- 动态节点（由 attack_chains.dynamic_filter 派生）run 启动时 save 到表，
-- run 结束 cleanup 删除. is_dynamic=true 区分手动 vs 动态.
-- 部分索引仅覆盖 is_dynamic=true 行（绝大多数行 false，提高 cleanup 删除效率）.

ALTER TABLE attack_chain_nodes
    ADD COLUMN is_dynamic BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_attack_chain_nodes_dynamic
    ON attack_chain_nodes (node_attack_chain_id)
    WHERE is_dynamic = TRUE;
