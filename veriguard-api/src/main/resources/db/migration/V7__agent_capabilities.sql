-- V7: B-ii PR-A Agent Capability 机制
--
-- 加 agents.agent_capabilities JSONB 列，承载 Agent 声明的能力标签列表（字符串数组）.
-- 默认空数组 '[]' 表示该 Agent 未声明任何 capability，与现有 Agent 行为完全兼容.
-- 由 List<String> Jackson 序列化驱动；columnDefinition jsonb 与 Hibernate JsonType 配合.

ALTER TABLE agents
    ADD COLUMN agent_capabilities JSONB NOT NULL
    DEFAULT '[]'::jsonb;
