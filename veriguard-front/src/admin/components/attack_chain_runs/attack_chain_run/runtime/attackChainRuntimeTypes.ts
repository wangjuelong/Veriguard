/**
 * Phase 9 运行结果画布共享类型（PRD §2.4 / spec §6.3.2-§6.3.4）.
 *
 * 与后端 V3 schema 字段对齐（LinkVerdict / NodeState / LinkExpectationStatus），但暂未通过
 * yarn generate-types-from-api 同步到 api-types.d.ts —— 给本 Phase 9 UI 提供独立类型表面。
 */

export type LinkVerdict = 'FULL_BREACH' | 'FULL_BLOCKED' | 'PARTIAL' | 'PENDING' | 'N_A';

/**
 * 节点 PREVENTION / DETECTION 状态指示（对应 spec §6.3.2 双层卡的两个色块）.
 *
 * - {@code SUCCESS / DETECTED} —— 该维度结算为 SUCCESS（防守方胜）
 * - {@code FAILED / NOT_DETECTED} —— 该维度结算为 FAILED（攻击方胜）
 * - {@code PARTIAL} —— 部分命中（节点级混合）
 * - {@code PENDING} —— 还未结算
 * - {@code SKIPPED} —— 节点被 stop-on-block 截停或上游条件不满足
 * - {@code N_A} —— 该节点未配该维度 expectation（不参与计数）
 */
export type NodeLayerStatus
  = | 'SUCCESS'
    | 'FAILED'
    | 'PARTIAL'
    | 'PENDING'
    | 'SKIPPED'
    | 'N_A';

export interface DoubleLayerNodeData {
  /** 节点显示名 */
  title: string;
  /** 节点角色 / contract type，作为副标题 */
  subtitle?: string;
  /** PREVENTION 维度状态（顶部色块） */
  preventionStatus: NodeLayerStatus;
  /** DETECTION 维度状态（底部色块） */
  detectionStatus: NodeLayerStatus;
  /** 当前迭代号（0-based）—— spec §6.3.2 重复执行进度 */
  currentIteration?: number;
  /** repeat_count；> 1 时角标显示 ↻ x current/total */
  repeatCount?: number;
}

/** 链路级 verdict 结果（与后端 LinkVerdict 对齐）. */
export interface VerdictBannerData {
  prevention: LinkVerdict;
  detection: LinkVerdict;
  /** 已结算节点数 / 总节点数 —— 用于 PREVENTION 副文案 "X of Y nodes blocked" */
  preventionStats?: {
    blocked: number;
    total: number;
  };
  /** 节点级 + 链路级合计 —— 用于 DETECTION 副文案 */
  detectionStats?: {
    nodesDetected: number;
    nodeTotal: number;
    linksMatched: number;
    linkTotal: number;
  };
  /** stop_on_block 触发节点名，非空时显示 "stopped on block at X" */
  stoppedAtNodeName?: string;
  /** RUNNING 状态时启用精简单行模式（spec §6.3.3） */
  running?: boolean;
  /** RUNNING 时显示的进度统计 */
  runningStats?: {
    settled: number;
    total: number;
    blocked: number;
    detected: number;
  };
}

/** 链路级 SOC correlation rule 状态（对应后端 LinkExpectationStatus）. */
export type LinkExpectationStatus = 'PENDING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'UNKNOWN';

export interface LinkExpectationItem {
  id: string;
  connectorId: string;
  ruleDisplayName: string;
  status: LinkExpectationStatus;
  /** SUCCESS 状态下展示 "incident #...". */
  incidentRef?: string;
  /** FAILED 时展示 "no match (window 2h)" 等说明 */
  failureReason?: string;
  /** UNKNOWN 时展示 "soc query failed" 等说明 */
  unknownReason?: string;
  /** SUCCESS 时累加分数 */
  score?: number;
  expectedScore?: number;
}
