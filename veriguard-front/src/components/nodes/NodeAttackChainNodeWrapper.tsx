/* eslint-disable i18next/no-literal-string -- Phase 12c-Biii 二开 UI 中文文案，未来 i18n 清洗。 */
import { Box, Stack, Typography } from '@mui/material';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo, useContext } from 'react';

import { type DoubleLayerNodeData, NODE_LAYER_STATUS_STYLE } from '../../admin/components/attack_chain_runs/attack_chain_run/runtime/attackChainRuntimeTypes';
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

  if (props.data.isDynamic) {
    const nodeId = props.data.node?.node_id ?? props.data.key;
    const runtimeData = runtimeContext?.runtimeByNodeId.get(nodeId);
    const layerStatus = runtimeData?.preventionStatus ?? null;
    const verdictBackground = layerStatus
      ? NODE_LAYER_STATUS_STYLE[layerStatus].background
      : 'transparent';
    const verdictColor = layerStatus
      ? NODE_LAYER_STATUS_STYLE[layerStatus].color
      : '#bb86fc';
    return (
      <Box
        sx={{
          position: 'relative',
          width: 200,
          padding: '8px 12px',
          background: verdictBackground,
          border: '2px dashed',
          borderColor: '#5e35b1',
          borderRadius: 1,
          color: verdictColor,
          cursor: 'default',
        }}
        data-testid="dynamic-node"
      >
        <Box
          sx={{
            position: 'absolute',
            top: -6,
            right: -6,
            background: '#5e35b1',
            color: '#ffffff',
            borderRadius: '50%',
            width: 18,
            height: 18,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 11,
          }}
        >
          ↻
        </Box>
        <Stack>
          <Typography
            variant="subtitle2"
            sx={{
              fontWeight: 600,
              lineHeight: 1.2,
            }}
          >
            {props.data.label}
          </Typography>
          <Typography
            variant="caption"
            sx={{
              opacity: 0.7,
              lineHeight: 1.2,
            }}
          >
            动态 (filter)
          </Typography>
        </Stack>
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
  }

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
