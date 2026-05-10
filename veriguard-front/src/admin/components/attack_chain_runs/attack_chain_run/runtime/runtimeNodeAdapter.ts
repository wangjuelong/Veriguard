import { type AttackChainNodeExpectation } from '../../../../../utils/api-types';
import { type DoubleLayerNodeData, type NodeLayerStatus } from './attackChainRuntimeTypes';

type ExpectationStatus = NonNullable<AttackChainNodeExpectation['node_expectation_status']>;
type NodeState = 'PENDING' | 'SCHEDULED' | 'RUNNING' | 'SETTLED' | 'SKIPPED' | 'FAILED';

/**
 * 节点 view-model 计算的最小输入. 不直接接 wire-format type（{@code AttackChainNode} 与
 * {@code AttackChainNodeOutputType} 字段集不一致），由调用方挑出需要的字段，让 adapter 保持纯函数.
 */
export interface RuntimeNodeInput {
  nodeId: string;
  title: string;
  /** 节点角色 / contract id，作为副标题. */
  subtitle?: string;
  /** 节点级总状态机；缺失时按 expectations 推断双层. */
  nodeState?: NodeState;
  /** node.repeat_count；> 1 时双层卡显示 ↻ 角标. */
  repeatCount?: number;
  currentIteration?: number;
}

/**
 * 节点级 PREVENTION / DETECTION 双层状态推断（spec §6.3.2）.
 *
 * 优先级：node_node_state === 'SKIPPED' → 双层均 SKIPPED（stop-on-block 截停）；
 * 否则按节点 expectations 同维度（PREVENTION / DETECTION）的 status 集合聚合：
 *
 * - 该维度无任何 expectation → N_A（不计入 banner 分母）
 * - 全 SUCCESS → SUCCESS
 * - 全 FAILED → FAILED
 * - 全 PENDING / UNKNOWN → PENDING（未结算）
 * - 其他混合（如部分 SUCCESS + 部分 FAILED） → PARTIAL
 */
const aggregate = (
  expectations: AttackChainNodeExpectation[],
  type: 'PREVENTION' | 'DETECTION',
): NodeLayerStatus => {
  const filtered = expectations.filter(e => e.node_expectation_type === type);
  if (filtered.length === 0) {
    return 'N_A';
  }
  const statuses: ExpectationStatus[] = filtered
    .map(e => e.node_expectation_status)
    .filter((s): s is ExpectationStatus => s !== undefined);
  if (statuses.length === 0) {
    return 'PENDING';
  }
  const allEqual = (target: ExpectationStatus) => statuses.every(s => s === target);
  if (allEqual('SUCCESS')) return 'SUCCESS';
  if (allEqual('FAILED')) return 'FAILED';
  if (statuses.every(s => s === 'PENDING' || s === 'UNKNOWN')) return 'PENDING';
  return 'PARTIAL';
};

/**
 * 单节点构造 view-model. expectations 仅传与本节点相关的列表（调用方筛过）.
 */
export const computeRuntimeNodeData = (
  node: RuntimeNodeInput,
  expectations: AttackChainNodeExpectation[],
): DoubleLayerNodeData => {
  const skipFallback: NodeLayerStatus | null = node.nodeState === 'SKIPPED' ? 'SKIPPED' : null;
  const preventionStatus = skipFallback ?? aggregate(expectations, 'PREVENTION');
  const detectionStatus = skipFallback ?? aggregate(expectations, 'DETECTION');
  return {
    title: node.title,
    subtitle: node.subtitle,
    preventionStatus,
    detectionStatus,
    repeatCount: node.repeatCount,
    currentIteration: node.currentIteration,
  };
};

/**
 * 把 run 当前节点列表 + 节点级 expectation 列表聚合成 nodeId → view-model 的 lookup map。
 *
 * 时间复杂度：O(N + E)（先按 nodeId 索引 expectations，再 O(1) 取）.
 */
export const buildRuntimeNodeMap = (
  nodes: readonly RuntimeNodeInput[],
  expectations: readonly AttackChainNodeExpectation[],
): ReadonlyMap<string, DoubleLayerNodeData> => {
  const expectationsByNodeId = new Map<string, AttackChainNodeExpectation[]>();
  for (const expectation of expectations) {
    const nodeId = expectation.node_expectation_node;
    if (!nodeId) {
      continue;
    }
    const list = expectationsByNodeId.get(nodeId);
    if (list) {
      list.push(expectation);
    } else {
      expectationsByNodeId.set(nodeId, [expectation]);
    }
  }
  const result = new Map<string, DoubleLayerNodeData>();
  for (const node of nodes) {
    const nodeExpectations = expectationsByNodeId.get(node.nodeId) ?? [];
    result.set(node.nodeId, computeRuntimeNodeData(node, nodeExpectations));
  }
  return result;
};
