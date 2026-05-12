-- V8: B-ii PR-B Mail Inject — SMTP Profile 表
--
-- SMTP profile 由运维人员在平台界面配置，承载发送邮件 inject 所需的 SMTP 服务器信息.
-- Email inject 执行时通过 smtp_profile_id 选择具体 profile.
-- 密码字段以加密形式存储（由 Veriguard 现有加密层处理，本表只存字符串列）.

CREATE TABLE smtp_profiles (
    smtp_profile_id          VARCHAR(255) PRIMARY KEY,
    smtp_profile_name        VARCHAR(255) NOT NULL,
    smtp_profile_host        VARCHAR(255) NOT NULL,
    smtp_profile_port        INTEGER NOT NULL,
    smtp_profile_auth_type   VARCHAR(32) NOT NULL DEFAULT 'NONE',
    smtp_profile_username    VARCHAR(255),
    smtp_profile_password    TEXT,
    smtp_profile_tls_mode    VARCHAR(32) NOT NULL DEFAULT 'STARTTLS',
    smtp_profile_default_from VARCHAR(255) NOT NULL,
    smtp_profile_default_reply_to VARCHAR(255),
    smtp_profile_created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    smtp_profile_updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_smtp_profiles_name ON smtp_profiles (smtp_profile_name);
