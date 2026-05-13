-- V18: IPv6 安全验证系统 边界 B6 — WebAttack PayloadType 持久化列
--
-- 背景：PR B6 (#52) 已交付 5 类 × 31 = 155 条 web 攻击数据集 + importer 框架，
-- 但 PayloadType 枚举 (COMMAND/EXECUTABLE/FILE_DROP/DNS_RESOLUTION/NETWORK_TRAFFIC)
-- 未包含 WebAttack 子类型，importer 真跑会被 PayloadUpsertInput 校验拒 400。
--
-- 本迁移给 payloads 表追加 9 列承载 web 攻击载荷专有字段，与
-- io.veriguard.injectors.web_attack.model.WebAttackContent (inject 形态) 字段对齐，
-- 保持 wire 格式一致 (snake_case)。SINGLE_TABLE 继承策略下其它子类列保持 NULL.
--
-- 字段命名：与 NetworkTraffic 的 network_traffic_* / DnsResolution 的 dns_resolution_*
-- 风格保持一致，前缀 payload_web_request_* / payload_web_expected_*.
--
-- JSONB 承载：
--   payload_web_request_headers          List<{name, value}>
--   payload_web_expected_status_codes    List<Integer>
--
-- TEXT 承载：
--   payload_web_request_cookies          裸 Cookie header 字符串 ("a=1; b=2")
--     与 WebAttackContent.cookies (String) 同形态，与 B6 importer 数据集
--     "csrf_token=cookie_token_xyz" 直传不需转换.
--
-- 枚举校验交由 Java DTO 层；DB 不加 CHECK 约束.

ALTER TABLE payloads
    ADD COLUMN payload_web_request_method VARCHAR(16);

ALTER TABLE payloads
    ADD COLUMN payload_web_request_url TEXT;

ALTER TABLE payloads
    ADD COLUMN payload_web_request_body TEXT;

ALTER TABLE payloads
    ADD COLUMN payload_web_request_body_type VARCHAR(64);

ALTER TABLE payloads
    ADD COLUMN payload_web_request_timeout_seconds INT;

ALTER TABLE payloads
    ADD COLUMN payload_web_request_cookies TEXT;

ALTER TABLE payloads
    ADD COLUMN payload_web_request_headers JSONB;

ALTER TABLE payloads
    ADD COLUMN payload_web_expected_status_codes JSONB;

ALTER TABLE payloads
    ADD COLUMN payload_web_expected_body_regex TEXT;
