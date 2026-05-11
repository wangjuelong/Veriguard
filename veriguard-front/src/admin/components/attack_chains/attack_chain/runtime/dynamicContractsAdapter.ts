import { type NodeContract } from '../../../../../utils/api-types';

/**
 * 动态节点 view-model（ChainedTimeline 用，与 ReactFlow node 对齐结构）.
 */
export interface DynamicNodeViewModel {
  /** ReactFlow node id；前缀 'dynamic-' 避免与 UUID 节点冲突. */
  id: string;
  /** 节点显示文本（contract label fallback 到 contract_id）. */
  label: string;
  /** ReactFlow position（动态节点固定 y，x 按 index 递增）. */
  position: {
    x: number;
    y: number;
  };
  /** 标记位：NodeAttackChainNodeWrapper 用此切 dashed border 视觉. */
  isDynamic: true;
  /** 原始 contract 引用（节点详情面板 / hover tooltip 用）. */
  contract: NodeContract;
}

const DYNAMIC_NODE_Y = 600;
const DYNAMIC_NODE_X_STEP = 250;

/**
 * 后端 wire format `attack_chain_dynamic_contracts: NodeContract[]` →
 * ReactFlow 动态节点 view-model 列表.
 */
export const toDynamicNodeViewModels = (
  contracts: readonly NodeContract[] | null | undefined,
): DynamicNodeViewModel[] => {
  if (!contracts) return [];
  return contracts.map((c, index) => {
    const label = (c.injector_contract_labels?.['en'] ?? c.injector_contract_id) as string;
    return {
      id: `dynamic-${c.injector_contract_id}`,
      label,
      position: {
        x: index * DYNAMIC_NODE_X_STEP,
        y: DYNAMIC_NODE_Y,
      },
      isDynamic: true as const,
      contract: c,
    };
  });
};
