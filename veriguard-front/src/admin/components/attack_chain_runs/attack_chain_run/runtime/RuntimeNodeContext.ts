import { createContext } from 'react';

import { type DoubleLayerNodeData } from './attackChainRuntimeTypes';

/**
 * 运行画布注入上下文 —— 用 B3.5a/B3.5b 同款 context-injection 模式让共享的 ChainedTimeline +
 * NodeAttackChainNode 文件无侵入地切换"编辑器节点 vs 双层运行节点"渲染。
 *
 * - 编辑器宿主（{@code AttackChainAttackChainNodes.tsx}）不 wrap Provider → 值保持 null →
 *   {@code NodeAttackChainNodeWrapper} useContext null 时降级到旧 {@code NodeAttackChainNode}。
 * - 运行画布宿主（{@code AttackChainRunAttackChainNodes.tsx}）wrap Provider 注入 nodeId →
 *   双层 view-model 的 lookup map → wrapper 切到 {@code DoubleLayerNode} 渲染。
 *
 * shape: lookup-by-nodeId 而不是 list，避免每个节点 O(N) 扫；wrapper 拿到 nodeId 直接 get。
 */
export interface RuntimeNodeContextValue { runtimeByNodeId: ReadonlyMap<string, DoubleLayerNodeData> }

export const RuntimeNodeContext = createContext<RuntimeNodeContextValue | null>(null);
