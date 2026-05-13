-- V10: PR C5 稳定性引擎子模块（IPv6 安全验证系统 招标 §3.3 ★1 + §4.2 ★3）
--
-- 招标要求：边界 / 流量防护设备稳定性验证，"任务可配重复 N 次（默认 10，1<N≤100），
-- 命中率 = 命中次数 / N"，"稳定性趋势图：每个任务一个点，悬停展示 N + 命中率 + 命中明细".
--
-- 数据模型说明：
--   - 稳定性以 AttackChainNode 为最小聚合粒度，每个 repeat_count > 1 的节点在终态时产出一行 snapshot.
--   - run_id 取节点所属 attack_chain_run（atomic-testing 节点没有 run → 写 NULL）.
--   - device_id 取节点关联资产（多资产取首个，无资产 → NULL）；不强制 FK 约束，与 baseline_id 一致
--     使用 VARCHAR(255) 字符串列，便于未来对接外部设备元数据系统（招标 §3.3 边界设备 / §4.2 流量设备）.
--   - baseline_id 招标尚未引入"基线"概念，预留 NULL 字段以支持未来"基线漂移检测"扩展（招标 §3.3
--     验收要点未直接出现，但稳定性趋势 + 基线对比是后续可扩展方向）.
--   - hit_count 累计本次执行中 PREVENTION expectation = SUCCESS 的次数（即"被防御工具识别 / 拦截"次数）；
--     total_count 是节点实际跑过的迭代数（= currentIteration + 1，等同 repeat_count 或 STOP_ON_BLOCK
--     早停时小于 repeat_count）.
--   - hit_rate = hit_count / total_count，由 Java 层在写入时计算并落库，便于按 device_id 聚合查询.
--   - captured_at 用 TIMESTAMPTZ，与表内其它时间列一致.
--
-- 索引策略：
--   - (captured_at) 用于按时间范围 / 按天聚合查询.
--   - (run_id) 用于按 run 反查节点稳定性快照.
--   - (device_id, captured_at) 用于"按设备查趋势"（招标 §3.3 / §4.2 用例分类）.
--
-- 不加 tenant_id：本仓库的 attack_chain_* 系列实体未实现 tenant 隔离（PRD 单租户默认），与现有
-- attack_chain_runs / attack_chain_nodes 列约定保持一致.

CREATE TABLE stability_trend_snapshots (
    snapshot_id              VARCHAR(255) NOT NULL,
    snapshot_run_id          VARCHAR(255),
    snapshot_node_id         VARCHAR(255),
    snapshot_device_id       VARCHAR(255),
    snapshot_baseline_id     VARCHAR(255),
    snapshot_hit_count       INTEGER     NOT NULL,
    snapshot_total_count     INTEGER     NOT NULL,
    snapshot_hit_rate        NUMERIC(5, 4) NOT NULL,
    snapshot_captured_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    snapshot_created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    snapshot_updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT stability_trend_snapshots_pkey PRIMARY KEY (snapshot_id),
    CONSTRAINT stability_trend_snapshots_run_fkey
        FOREIGN KEY (snapshot_run_id) REFERENCES attack_chain_runs (run_id) ON DELETE SET NULL,
    CONSTRAINT stability_trend_snapshots_node_fkey
        FOREIGN KEY (snapshot_node_id) REFERENCES attack_chain_nodes (node_id) ON DELETE SET NULL,
    CONSTRAINT stability_trend_snapshots_hit_rate_range
        CHECK (snapshot_hit_rate >= 0 AND snapshot_hit_rate <= 1),
    CONSTRAINT stability_trend_snapshots_counts_valid
        CHECK (snapshot_hit_count >= 0 AND snapshot_total_count > 0 AND snapshot_hit_count <= snapshot_total_count)
);

CREATE INDEX idx_stability_trend_snapshots_captured_at
    ON stability_trend_snapshots (snapshot_captured_at);

CREATE INDEX idx_stability_trend_snapshots_run
    ON stability_trend_snapshots (snapshot_run_id)
    WHERE snapshot_run_id IS NOT NULL;

CREATE INDEX idx_stability_trend_snapshots_device_captured
    ON stability_trend_snapshots (snapshot_device_id, snapshot_captured_at)
    WHERE snapshot_device_id IS NOT NULL;
