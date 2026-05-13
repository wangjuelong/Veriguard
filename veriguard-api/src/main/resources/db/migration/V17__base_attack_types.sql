-- V17: IPv6 安全验证系统 ★2 攻击组合 PR B5 — 基础攻击类型库（≥ 250 类）
--
-- 招标条款：§3.6 "支持选择至少 250 种攻击类型，动态生成至少 30000 个攻击组合".
-- D2 阶段 BaseAttackPayloadCatalog / D4 BaseAttackTypeSeverityCatalog 内置 ~12 类，
-- 不满足 250 类门槛；本 PR 把基础攻击类型扩到 ≥ 250 + 落库管理 + 加载到内存缓存.
--
-- 命名约定：snake_case + 复数表名（与 V11 bypass_dimensions 一致）.
-- 枚举校验交 Java 层（Enum.name() 直驱 VARCHAR 列），DB 不加 CHECK 约束便于扩展.
-- 不引入 tenant_id —— 与 V11/V14 同样视为单租户（与 bypass_dimensions / severity_configs 一致）.
--
-- 类别（lowercase enum，9 类）：
--   web_injection         Web 注入（sql / xss / xxe / ssti / oscommand 等）
--   web_business          Web 业务漏洞（csrf / open_redirect / upload / 路径穿越 / jwt 等）
--   credential            凭证攻击（爆破 / 喷洒 / kerberoast / pass-the-hash 等）
--   network_protocol      网络协议攻击（dns 隧道 / arp 欺骗 / smb relay 等）
--   c2                    C2 / 远控（reverse shell / cobalt strike / sliver 等）
--   persistence           主机持久化（systemd / cron / registry / 计划任务 等）
--   privilege_escalation  提权（sudo 滥用 / suid / uac bypass / 内核 exp 等）
--   discovery             信息探测（端口扫描 / smb 枚举 / wmi / ldap 枚举 等）
--   exploit_cve           高危 CVE 利用（log4shell / spring4shell / fastjson rce 等）
--
-- target_layer（lowercase enum，4 类）：
--   boundary     边界（防火墙 / WAF / 反代）
--   traffic      流量层（IDS / NDR / 流量探针）
--   host         主机层（HIDS / EDR / agent）
--   application  应用层（业务系统 / 中间件）

BEGIN;

CREATE TABLE base_attack_types (
    base_attack_type_id                 VARCHAR(255) PRIMARY KEY,
    base_attack_type_name               VARCHAR(128) NOT NULL,
    base_attack_type_category           VARCHAR(64)  NOT NULL,
    base_attack_type_display_label      VARCHAR(255) NOT NULL,
    base_attack_type_description        TEXT,
    base_attack_type_severity_score     INT          NOT NULL,
    base_attack_type_default_payload    TEXT,
    base_attack_type_attack_pattern_id  VARCHAR(255),
    base_attack_type_target_layer       VARCHAR(32)  NOT NULL,
    base_attack_type_created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    base_attack_type_updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_base_attack_types_name           ON base_attack_types (base_attack_type_name);
CREATE INDEX        idx_base_attack_types_category       ON base_attack_types (base_attack_type_category);
CREATE INDEX        idx_base_attack_types_target_layer   ON base_attack_types (base_attack_type_target_layer);
CREATE INDEX        idx_base_attack_types_severity_desc  ON base_attack_types (base_attack_type_severity_score DESC);

COMMIT;
