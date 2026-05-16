-- V24: P1.2.b 多端口四元组 schema —— IPv6 安全验证系统招标 §4 流量安全验证.
--
-- 招标条款（§4）："支持同一个流量安全验证用例中，包含多个端口不同的四元组"
--
-- 现状：payloads 表的 NetworkTraffic 子类（@DiscriminatorValue("NetworkTraffic")）
--      只支持单一 (ip_src, ip_dst, port_src, port_dst, protocol) 五元组（V1 init
--      就已定义）。一条 NetworkTraffic payload 只能描述一对四元组。
--
-- 本迁移加 1 列 `network_traffic_extra_tuples JSONB NULL`，承载额外四元组列表。
-- 单条 payload 的 *主* 四元组继续由现有 5 列承载（向后兼容，不动它们）；
-- *额外* 四元组（同协议或跨协议、同 src_ip 不同 port、跨网段等）以 JSON 列表追加。
--
-- JSON 结构示例：
--   [
--     {
--       "network_traffic_ip_src": "2001:db8::1",
--       "network_traffic_ip_dst": "2001:db8::2",
--       "network_traffic_port_src": 32768,
--       "network_traffic_port_dst": 443,
--       "network_traffic_protocol": "TCP"
--     },
--     ...
--   ]
--
-- 字段名直接复用现有 5 列的 snake_case 命名，与 NetworkTrafficTuple record 的
-- @JsonProperty 一一对应。读侧通过 NetworkTraffic.allTuples() 统一拿 primary +
-- extras 列表。
--
-- 不加 CHECK 约束（JSON 内枚举/范围校验交 Java 层 @NotBlank / @NotNull）。
-- 列可为 NULL —— 旧 payloads 无 extras，新 payloads 可选填。

BEGIN;

ALTER TABLE payloads
    ADD COLUMN network_traffic_extra_tuples JSONB NULL;

COMMIT;
