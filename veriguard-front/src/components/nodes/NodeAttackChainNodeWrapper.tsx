import { Box } from '@mui/material';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo, useContext } from 'react';

import { type DoubleLayerNodeData } from '../../admin/components/attack_chain_runs/attack_chain_run/runtime/attackChainRuntimeTypes';
import DoubleLayerNode from '../../admin/components/attack_chain_runs/attack_chain_run/runtime/DoubleLayerNode';
import { RuntimeNodeContext } from '../../admin/components/attack_chain_runs/attack_chain_run/runtime/RuntimeNodeContext';
import NodeAttackChainNodeComponent, { type NodeAttackChainNode } from './NodeAttackChainNode';

const FALLBACK_DATA: DoubleLayerNodeData = {
  title: '',
  preventionStatus: 'PENDING',
  detectionStatus: 'PENDING',
};

/**
 * 单一 ReactFlow node 派发器（{@code components/nodes/index.ts} 注册到 nodeType "node"）.
 *
 * - 编辑器宿主（context=null） → 渲染 {@code NodeAttackChainNode}（旧编辑器节点 UI 不变）
 * - 运行画布宿主（context 注入） → 渲染 {@code DoubleLayerNode}（spec §6.3.2 双层卡）+ 自带
 *   ReactFlow Handle 锚点维持 edge 连接
 *
 * 不动 NodeAttackChainNode.tsx —— B3.5a 已注入的 NodeBadge / settings 上下文继续作用于编辑器路径，
 * 不影响运行画布路径.
 */
const NodeAttackChainNodeWrapper = (props: NodeProps<NodeAttackChainNode>) => {
  const runtimeContext = useContext(RuntimeNodeContext);
  if (!runtimeContext) {
    return <NodeAttackChainNodeComponent {...props} />;
  }
  const nodeId = props.data.node?.node_id ?? props.data.key;
  const runtime = runtimeContext.runtimeByNodeId.get(nodeId);
  const onClick = props.data.node
    ? () => props.data.onSelectedAttackChainNode(props.data.node)
    : undefined;

  return (
    <Box sx={{ position: 'relative' }} data-testid="runtime-node-wrapper">
      <DoubleLayerNode
        data={runtime ?? {
          ...FALLBACK_DATA,
          title: props.data.label,
        }}
        onClick={onClick}
      />
      {props.data.isTargeted && (
        <Handle
          type="target"
          id={`target-${props.data.key}`}
          position={Position.Left}
          isConnectable={false}
        />
      )}
      {props.data.isTargeting && (
        <Handle
          type="source"
          id={`source-${props.data.key}`}
          position={Position.Right}
          isConnectable={false}
        />
      )}
    </Box>
  );
};

export default memo(NodeAttackChainNodeWrapper);
