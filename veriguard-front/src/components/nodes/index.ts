import { type NodeTypes } from '@xyflow/react';

import NodeAttackChainNodeWrapperExport from './NodeAttackChainNodeWrapper';

// "node" key 既是编辑器又是运行画布；wrapper 内部 useContext(RuntimeNodeContext) 派发实际渲染.
const nodeTypes: NodeTypes = { node: NodeAttackChainNodeWrapperExport };

export default nodeTypes;
