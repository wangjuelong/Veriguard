-- V21: C1-Platform-3 — Mode C 离线包导入结果持久化
--
-- 背景：C1-Platform-2 落地 .vresults envelope 解密 (PR #57) + C1-Integration
-- 落地 OfflinePackImportService 用 production VresultsTaskResultParser 真解密
-- (PR #58) 后，import 仍只返回 count + 错误数组，**不持久化每条 result**。
-- 招标 demo 需要：admin 导入 .vresults 后能看每个任务的 agent 实跑输出
-- (stdout / stderr / exit_code / error_message) — 本表承载这层数据。
--
-- ## 与 Mode A 现状的关系
--
-- Mode A 走 `combination/executor/HttpInjectExecutor` → `dispatch + await`
-- 路径，agent 返回的 ReceivedResult 只读 `status` 字段做 HIT/MISS/TIMEOUT 三态
-- 分类写到 `attack_combination_run.hit_state`，**stdout/stderr/exit_code 一律丢
-- 弃**。Mode C 用例不同：operator 需要逐条人工 review agent 端原始输出。所以
-- 本表保留所有字段，**不**与 Mode A 路径合并。
--
-- ## 与 offline_pack_audit 的关系
--
-- offline_pack_audit (V20) 是"每个 pack 一行"的导出-导入审计行；本表是
-- "每个 result 一行"的明细行。pack_id 在两表都有，UI 可 JOIN 显示。
--
-- ## ALTER offline_pack_audit 同步动作
--
-- V20 把 offline_pack_audit.agent_id 设为 FK → agents(agent_id) ON DELETE
-- CASCADE。结果上游 OpenAEV 的 agents 表要求 Asset 关联 + privilege +
-- deploymentMode + executedByUser 等一堆字段（Asset 又有自己的强约束）。Mode C
-- agent 走 /api/agent/onboard/register 路径自己生成 agent_id，没有 Asset / 没
-- 在 agents 表 — 因此 V20 之后 OfflinePackExportService 的 audit 写入逻辑要先
-- agentRepository.findById 命中才写，否则 skip-with-WARN（见 PR #57 commit
-- c68ffefcf 的 scaffold-mode 分支注释）。
--
-- 把 agent_id 从 FK 降级为 opaque identifier，audit 总能写入；offline_pack_result
-- 的 agent_id 同步采用 opaque 约定。如未来真把 Mode C agent 写入 agents 表 (例
-- 如复用 OpenAEV asset 模型)，可在新 V 版本里加回 FK。

-- Step 1: 解除 offline_pack_audit.agent_id 的 FK 约束（保留 NOT NULL，仅去掉
-- 跨表引用），并为按 agent_id 查询保留覆盖索引。
ALTER TABLE offline_pack_audit
    DROP CONSTRAINT fk_pack_audit_agent;

-- Step 1b: 把 INET 列改成 TEXT — V20 用 INET 是为 IP 类型校验，但 Hibernate
-- JDBC 默认绑定 String → VARCHAR，对 INET 列触发"character varying ≠ inet"
-- 错误。Audit 只展示 IP，不做 CIDR 运算，TEXT 足够。改后 String<->TEXT 直通。
ALTER TABLE offline_pack_audit
    ALTER COLUMN exported_from_ip TYPE TEXT USING exported_from_ip::TEXT;
ALTER TABLE offline_pack_audit
    ALTER COLUMN imported_from_ip TYPE TEXT USING imported_from_ip::TEXT;

-- Step 2: 新建 offline_pack_result 表
CREATE TABLE offline_pack_result (
    pack_id UUID NOT NULL,
    ordinal INT NOT NULL,
    task_id VARCHAR(255) NULL,
    status VARCHAR(64) NOT NULL,
    exit_code INT NOT NULL,
    stdout TEXT NULL,
    stderr TEXT NULL,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    error_message TEXT NULL,
    agent_id VARCHAR(255) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_offline_pack_result PRIMARY KEY (pack_id, ordinal),
    CONSTRAINT fk_offline_pack_result_audit
        FOREIGN KEY (pack_id) REFERENCES offline_pack_audit (pack_id) ON DELETE CASCADE,
    CONSTRAINT chk_offline_pack_result_ordinal_nonneg
        CHECK (ordinal >= 0)
);

-- 索引：按 pack_id + ordinal 顺序读 (admin UI 渲染一个 pack 的所有 result)
-- 已由 PK 隐含；显式索引：按 agent_id + imported_at DESC，支持"最近从这个 agent
-- 来的 result"诊断查询。
CREATE INDEX idx_offline_pack_result_agent
    ON offline_pack_result (agent_id, imported_at DESC);
