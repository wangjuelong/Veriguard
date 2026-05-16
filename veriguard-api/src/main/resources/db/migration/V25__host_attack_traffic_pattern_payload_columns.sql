-- V25: IPv6 安全验证系统 P1.1 + P1.2 — HostAttack / TrafficPattern PayloadType 持久化列
--
-- 背景：PR #65 (HIDS P1.1) 交付 12 类 × ~26 = 310 条 HostAttack 数据集 + importer，
-- PR #66 (NTA P1.2.a) 交付 11 类 × 30 = 330 条 TrafficPattern 数据集 + importer。
-- 两 importer 当前 dry-run only — payload_type = "HostAttack" / "TrafficPattern"
-- 未在 PayloadType 枚举里，POST /api/payloads/upsert 会被 PayloadUpsertInput 校验拒 400.
--
-- 本迁移给 payloads 表追加 10 列（5 HIDS + 5 NTA）承载两子类专有字段，与
-- HostAttackPayload / TrafficPatternPayload 实体的 @JsonProperty / @Column 一一对应。
-- SINGLE_TABLE 继承策略下其它子类列保持 NULL.
--
-- 字段命名：仿 V18 (WebAttack payload_web_*)，前缀 payload_hids_* / payload_nta_*.
--
-- JSONB 承载：
--   payload_hids_expected_artifacts   List<String>  — 期望落地的 artifact 路径/特征列表
--
-- TEXT 承载：
--   payload_hids_command_template      含 {{C2_HOST}} 占位符的命令模板
--   payload_hids_artifact_path         单个 artifact 路径
--   payload_nta_signature              Suricata/Snort 规则文本
--   payload_nta_pcap_path              pcap 文件相对路径
--   payload_nta_detection_hint         人工检测提示
--
-- 枚举/范围校验交由 Java DTO 层（@NotNull）；DB 不加 CHECK 约束.

BEGIN;

-- HostAttack (HIDS P1.1)
ALTER TABLE payloads
    ADD COLUMN payload_hids_category VARCHAR(64);

ALTER TABLE payloads
    ADD COLUMN payload_hids_execution_mode VARCHAR(32);

ALTER TABLE payloads
    ADD COLUMN payload_hids_command_template TEXT;

ALTER TABLE payloads
    ADD COLUMN payload_hids_artifact_path TEXT;

ALTER TABLE payloads
    ADD COLUMN payload_hids_expected_artifacts JSONB;

-- TrafficPattern (NTA P1.2)
ALTER TABLE payloads
    ADD COLUMN payload_nta_category VARCHAR(64);

ALTER TABLE payloads
    ADD COLUMN payload_nta_protocol VARCHAR(16);

ALTER TABLE payloads
    ADD COLUMN payload_nta_signature TEXT;

ALTER TABLE payloads
    ADD COLUMN payload_nta_pcap_path TEXT;

ALTER TABLE payloads
    ADD COLUMN payload_nta_detection_hint TEXT;

COMMIT;
