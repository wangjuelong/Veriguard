import { CropFree, UnfoldLess, UnfoldMore } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  type Connection,
  ConnectionLineType,
  type ConnectionState,
  ControlButton,
  Controls,
  type Edge,
  MarkerType,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Viewport,
  type XYPosition,
} from '@xyflow/react';
import moment from 'moment-timezone';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AssetGroupsHelper } from '../actions/asset_groups/assetgroup-helper';
import { type EndpointHelper } from '../actions/assets/asset-helper';
import { type AttackChainNodeOutputType, type AttackChainNodeStore } from '../actions/attack_chain_nodes/AttackChainNode';
import { type AttackChainNodeHelper } from '../actions/attack_chain_nodes/node-helper';
import { type AttackChainRunsHelper } from '../actions/attack_chain_runs/attack_chain_run-helper';
import { type AttackChainsHelper } from '../actions/attack_chains/attack_chain-helper';
import { type TeamsHelper } from '../actions/teams/team-helper';
import { AttackChainEdgeConditionContext } from '../admin/components/attack_chains/attack_chain/editor/AttackChainEdgeConditionContext';
import { AttackChainNodeTestContext, PermissionsContext } from '../admin/components/common/Context';
import { useHelper } from '../store';
import { type AttackChainEdge, type AttackChainNode } from '../utils/api-types';
import handle from '../utils/period/Period';
import ChainingUtils from './common/chaining/ChainingUtils';
import CustomTimelineBackground from './CustomTimelineBackground';
import CustomTimelinePanel from './CustomTimelinePanel';
import { useFormatter } from './i18n';
import nodeTypes from './nodes';
import { type NodeAttackChainNode } from './nodes/NodeAttackChainNode';
import NodePhantom from './nodes/NodePhantom';

const useStyles = makeStyles()(() => ({
  container: {
    marginTop: 30,
    paddingRight: 40,
  },
  rotatedIcon: { transform: 'rotate(90deg)' },
  newBox: {
    position: 'relative',
    zIndex: 4,
    pointerEvents: 'none',
    cursor: 'none',
  },
}));

interface Props {
  nodes: AttackChainNodeOutputType[];
  onSelectedAttackChainNode(node?: AttackChainNodeOutputType): void;
  onTimelineClick(duration: number): void;
  onUpdateAttackChainNode: (data: AttackChainNode[]) => void;
  onCreate: (result: {
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }) => void;
  onUpdate: (result: {
    result: string;
    entities: { nodes: Record<string, AttackChainNodeStore> };
  }) => void;
  onDelete: (result: string) => void;
}

const ChainedTimelineFlow: FunctionComponent<Props> = ({
  nodes,
  onSelectedAttackChainNode,
  onTimelineClick,
  onUpdateAttackChainNode,
  onCreate,
  onUpdate,
  onDelete,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);
  const [flowNodes, setFlowNodes, onFlowNodesChange] = useNodesState<NodeAttackChainNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [draggingOnGoing, setDraggingOnGoing] = useState<boolean>(false);
  const [viewportData, setViewportData] = useState<Viewport>();
  const [minutesPerGapIndex, setMinutesPerGapIndex] = useState<number>(0);
  const [currentUpdatedNode, setCurrentUpdatedNode] = useState<NodeAttackChainNode | null>(null);
  const [currentMousePosition, setCurrentMousePosition] = useState<XYPosition>({
    x: 0,
    y: 0,
  });
  const [newNodeCursorVisibility, setNewNodeCursorVisibility] = useState<'visible' | 'hidden'>('hidden');
  const [newNodeCursorClickable, setNewNodeCursorClickable] = useState<boolean>(true);
  const [currentMouseTime, setCurrentMouseTime] = useState<string>('');
  const [connectOnGoing, setConnectOnGoing] = useState<boolean>(false);

  const reactFlow = useReactFlow();

  const { contextId } = useContext(AttackChainNodeTestContext);
  const edgeConditionContext = useContext(AttackChainEdgeConditionContext);

  // Phase 12b-B3.5b：编辑器树里点 edge → 打开 ConditionEdgePopover；运行画布树里 context = null，整段 no-op。
  const handleEdgeClick = (event: ReactMouseEvent<Element>, edge: Edge) => {
    if (!edgeConditionContext) return;
    if (!permissions.canManage) return;
    edgeConditionContext.openEdgeCondition(edge.id, event.currentTarget as HTMLElement);
  };

  const { injectsMap, teams, assets, assetGroups, attack_chain, attack_chain_run }
    = useHelper((helper: AttackChainRunsHelper & AttackChainNodeHelper & TeamsHelper & EndpointHelper & AssetGroupsHelper & AttackChainsHelper) => ({
      injectsMap: helper.getAttackChainNodesMap(),
      teams: helper.getTeamsMap(),
      assets: helper.getEndpointsMap(),
      assetGroups: helper.getAssetGroupMaps(),
      attack_chain: helper.getAttackChain(contextId),
      attack_chain_run: helper.getAttackChainRun(contextId),
    }));

  const { t } = useFormatter();

  const proOptions = {
    account: 'paid-pro',
    hideAttribution: true,
  };
  const defaultEdgeOptions = {
    type: ConnectionLineType.Bezier,
    markerEnd: {
      type: MarkerType.ArrowClosed,
      width: 30,
      height: 30,
    },
  };

  const minutesPerGapAllowed = [
    5, // 5 minutes per gap
    20, // 20 so that each vertical lines indicates 1h
    20 * 12, // 20*12 so that each vertical line indicates half a day
    20 * 24, // 20*24 so that each vertical line indicates a full day
  ];
  const gapSize = 125;
  const newNodeSize = 50;
  const nodeHeightClearance = 220;
  const nodeWidthClearance = 350;

  let startDate: string | undefined;

  // If we have a attack_chain, we find the startdate using the cron info
  if (attack_chain !== undefined) {
    const cronObject = handle(attack_chain.attack_chain_recurrence);
    startDate = attack_chain?.attack_chain_recurrence_start ? attack_chain?.attack_chain_recurrence_start : attack_chain_run?.attack_chain_run_start_date;
    if (startDate !== undefined) {
      startDate = cronObject !== null
        ? moment(startDate).utc().hour(cronObject.getRecurrenceTime().hour || 0).minute(cronObject.getRecurrenceTime().minute || 0)
            .format()
        : moment(startDate).utc().format();
    }
  } else if (attack_chain_run !== undefined) {
    // Otherwise, we're in a attack_chain_run and we use the start_date
    startDate = attack_chain_run.attack_chain_run_start_date !== null ? attack_chain_run.attack_chain_run_start_date : undefined;
  }

  /**
   * Convert reactflow coordinates to time
   * @param position the reactflow coordinates
   */
  const convertCoordinatesToTime = (position: XYPosition) => {
    return Math.round(((position.x) / (gapSize / minutesPerGapAllowed[minutesPerGapIndex])) * 60);
  };

  /**
   * Move item from an index to another one
   * @param array the array to update
   * @param to the target index
   * @param from the origin index
   */
  const moveItem = (array: NodeAttackChainNode[], to: number, from: number) => {
    const item = array[from];
    array.splice(from, 1);
    array.splice(to, 0, item);
    return array;
  };

  /**
   * Calculate a bounding box for an index
   * @param currentNode the node to calculate the bounding box for
   * @param nodesAvailable the nodes
   */
  const calculateBoundingBox = (currentNode: NodeAttackChainNode, nodesAvailable: NodeAttackChainNode[]) => {
    if (currentNode.data.node?.node_depends_on) {
      const nodesId = currentNode.data.node?.node_depends_on.map(value => value.dependency_relationship?.node_parent_id);
      const dependencies = nodesAvailable.filter(dependencyNode => nodesId.includes(dependencyNode.id));
      const minX = Math.min(currentNode.position.x, ...dependencies.map(value => value.data.boundingBox!.topLeft.x));
      const minY = Math.min(currentNode.position.y, ...dependencies.map(value => value.data.boundingBox!.topLeft.y));
      const maxX = Math.max(currentNode.position.x + nodeWidthClearance, ...dependencies.map(value => value.data.boundingBox!.bottomRight.x));
      const maxY = Math.max(currentNode.position.y + nodeHeightClearance, ...dependencies.map(value => value.data.boundingBox!.bottomRight.y));
      return {
        topLeft: {
          x: minX,
          y: minY,
        },
        bottomRight: {
          x: maxX,
          y: maxY,
        },
      };
    }
    return {
      topLeft: currentNode.position,
      bottomRight: {
        x: currentNode.position.x + nodeWidthClearance,
        y: currentNode.position.y + nodeHeightClearance,
      },
    };
  };

  /**
   * Calculate nodes position when dragging stopped
   * @param nodeAttackChainNodes the list of nodes
   */
  const calculateAttackChainNodePosition = (nodeAttackChainNodes: NodeAttackChainNode[]) => {
    let reorganizedAttackChainNodes = nodeAttackChainNodes;

    nodeAttackChainNodes.forEach((node, i) => {
      let childNodes = reorganizedAttackChainNodes.slice(i).filter(nextNode => nextNode.id !== node.id
        && nextNode.data.node?.node_depends_on !== undefined
        && nextNode.data.node?.node_depends_on !== null
        && nextNode.data.node!.node_depends_on
          .find(dependsOn => dependsOn.dependency_relationship?.node_parent_id === node.id) !== undefined);

      childNodes = childNodes.sort((a, b) => a.data.node!.node_depends_duration - b.data.node!.node_depends_duration);

      childNodes.forEach((childNode, j) => {
        reorganizedAttackChainNodes = moveItem(reorganizedAttackChainNodes, i + j + 1, reorganizedAttackChainNodes.indexOf(childNode, i));
      });
    });

    reorganizedAttackChainNodes.forEach((nodeAttackChainNode, index) => {
      const nodeAttackChainNodePosition = nodeAttackChainNode.position;
      const nodeAttackChainNodeData = nodeAttackChainNode.data;

      const previousNodes = reorganizedAttackChainNodes.slice(0, index)
        .filter(previousNode => previousNode.data.boundingBox !== undefined
          && nodeAttackChainNodeData.boundingBox !== undefined
          && nodeAttackChainNodeData.boundingBox?.topLeft.x >= previousNode.data.boundingBox.topLeft.x
          && nodeAttackChainNodeData.boundingBox?.topLeft.x < previousNode.data.boundingBox.bottomRight.x);

      const arrayOfY = previousNodes
        .map(previousNode => (previousNode.data.boundingBox?.bottomRight.y ? previousNode.data.boundingBox?.bottomRight.y : 0));
      const maxY = Math.max(0, ...arrayOfY);

      nodeAttackChainNodePosition.y = 0;
      let rowFound = false;
      for (let row = 1; row <= (maxY / nodeHeightClearance) + 1; row += 1) {
        if (!arrayOfY.includes(row * nodeHeightClearance)) {
          nodeAttackChainNodePosition.y = (row - 1) * nodeHeightClearance;
          rowFound = true;
          break;
        }
      }

      if (!rowFound) {
        nodeAttackChainNodePosition.y = previousNodes.length === 0 ? 0 : maxY;
      }
      if (nodeAttackChainNode.data.node?.node_depends_on) {
        const nodesId = nodeAttackChainNode.data.node?.node_depends_on.map(value => value.dependency_relationship?.node_parent_id);
        const dependencies = reorganizedAttackChainNodes.filter(dependencyNode => nodesId.includes(dependencyNode.id));
        const minY = dependencies.length > 0 ? Math.min(...dependencies.map(value => value.data.boundingBox!.topLeft.y)) : 0;

        nodeAttackChainNodePosition.y = nodeAttackChainNodePosition.y < minY ? minY : nodeAttackChainNodePosition.y;
      }

      nodeAttackChainNodeData.fixedY = nodeAttackChainNodePosition.y;
      nodeAttackChainNodeData.boundingBox = calculateBoundingBox(nodeAttackChainNode, reorganizedAttackChainNodes);
      reorganizedAttackChainNodes[index] = nodeAttackChainNode;
    });
  };

  const updateEdges = () => {
    const newEdges = nodes.filter(node => node.node_depends_on !== null && node.node_depends_on !== undefined)
      .flatMap((node) => {
        const results = [];
        if (node.node_depends_on !== undefined) {
          for (let i = 0; i < node.node_depends_on.length; i += 1) {
            if (node.node_depends_on[i].dependency_relationship?.node_children_id === node.node_id) {
              results.push({
                id: `${node.node_depends_on[i].dependency_relationship?.node_parent_id}->${node.node_depends_on[i].dependency_relationship?.node_children_id}`,
                target: `${node.node_depends_on[i].dependency_relationship?.node_children_id}`,
                targetHandle: `target-${node.node_depends_on[i].dependency_relationship?.node_children_id}`,
                source: `${node.node_depends_on[i].dependency_relationship?.node_parent_id}`,
                sourceHandle: `source-${node.node_depends_on[i].dependency_relationship?.node_parent_id}`,
                label: ChainingUtils.fromAttackChainNodeDependencyToLabel(node.node_depends_on[i]),
                labelShowBg: false,
                labelStyle: {
                  fill: theme.palette.text?.primary,
                  fontSize: 14,
                },
              });
            }
          }
        }
        return results;
      });

    setEdges(newEdges);
  };

  /**
   * Update all nodes
   */
  const updateNodes = () => {
    if (nodes.length > 0) {
      const injectsNodes = nodes
        .sort((a, b) => a.node_depends_duration - b.node_depends_duration)
        .map((node: AttackChainNodeOutputType) => ({
          id: `${node.node_id}`,
          type: 'node',
          data: {
            key: node.node_id,
            label: node.node_title,
            color: 'green',
            background:
                theme.palette.mode === 'dark'
                  ? '#09101e'
                  : '#e5e5e5',
            isTargeted: nodes.find(anyAttackChainNode => anyAttackChainNode.node_id === node.node_id) !== undefined,
            isTargeting: node.node_depends_on !== undefined,
            node,
            fixedY: 0,
            startDate,
            onSelectedAttackChainNode,
            boundingBox: {
              topLeft: {
                x: (node.node_depends_duration / 60) * (gapSize / minutesPerGapAllowed[minutesPerGapIndex]),
                y: 0,
              },
              bottomRight: {
                x: (node.node_depends_duration / 60) * (gapSize / minutesPerGapAllowed[minutesPerGapIndex]) + nodeWidthClearance,
                y: nodeHeightClearance,
              },
            },
            targets: node.node_assets!.map(asset => assets[asset]?.asset_name)
              .concat(node.node_asset_groups!.map(assetGroup => assetGroups[assetGroup]?.asset_group_name))
              .concat(node.node_teams!.map(team => teams[team]?.team_name)),
            contextId,
            onCreate,
            onUpdate,
            onDelete,
          },
          position: {
            x: (node.node_depends_duration / 60) * (gapSize / minutesPerGapAllowed[minutesPerGapIndex]),
            y: 0,
          },
        }));

      if (currentUpdatedNode !== null) {
        injectsNodes.find(node => node.id === currentUpdatedNode.id)!.position.x = currentUpdatedNode.position.x;
      }

      setCurrentUpdatedNode(null);
      setDraggingOnGoing(false);
      calculateAttackChainNodePosition(injectsNodes);
      setFlowNodes(injectsNodes);
      updateEdges();
    }
  };

  useEffect(() => {
    updateNodes();
  }, [nodes, minutesPerGapIndex]);

  /**
   * Actions to hide the new node 'button'
   */
  const hideNewNode = () => {
    if (!connectOnGoing) {
      setNewNodeCursorVisibility('hidden');
      setNewNodeCursorClickable(false);
    }
  };

  /**
   * Actions to show the new node 'button'
   */
  const showNewNode = () => {
    if (!connectOnGoing) {
      setNewNodeCursorVisibility('visible');
      setNewNodeCursorClickable(true);
    }
  };

  /**
   * Take care of updates when the node drag is starting
   * @param _event the mouse event (unused for now)
   * @param node the node to update
   */
  const nodeDragStop = (_event: ReactMouseEvent, node: NodeAttackChainNode) => {
    const injectFromMap = injectsMap[node.id];
    if (injectFromMap !== undefined) {
      const updatedNode = {
        ...injectFromMap,
        node_injector_contract: injectFromMap.node_injector_contract.injector_contract_id,
        node_id: node.id,
        node_depends_duration: convertCoordinatesToTime(node.position),
        node_depends_on: injectFromMap.node_depends_on !== null
          ? injectFromMap.node_depends_on
          : null,
      };
      onUpdateAttackChainNode([updatedNode]);
      setCurrentUpdatedNode(updatedNode);
      setDraggingOnGoing(false);
    }
  };

  /**
   * Small function to do some stuff when dragging is starting
   */
  const nodeDragStart = () => {
    const nodesList = flowNodes.filter(currentNode => currentNode.type !== 'phantom');
    setFlowNodes(nodesList);
  };

  /**
   * Small function to do some stuff when connect is starting
   */
  const connectStart = () => {
    setConnectOnGoing(true);
    hideNewNode();
  };

  /**
   * Small function to do some stuff when connect ends
   */
  const connectEnd = () => {
    setTimeout(() => {
      setConnectOnGoing(false);
      showNewNode();
    }, 100);
  };

  const connect = (connection: Connection) => {
    const node = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === connection.target);
    const injectParent = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === connection.source);
    if (node !== undefined && injectParent !== undefined && node.node_depends_duration > injectParent.node_depends_duration) {
      // 新建边时不挂条件 —— 评估器对 null condition 返回 true（"父节点执行完成即可"），
      // 用户后续点 edge 打开 ConditionEdgePopover 再加 PREVENTION/DETECTION/MANUAL 条件。
      const newDependsOn: AttackChainEdge = {
        dependency_relationship: {
          node_children_id: node.node_id,
          node_parent_id: injectParent.node_id,
        },
      };

      const injectToUpdate = {
        ...injectsMap[node.node_id],
        node_injector_contract: node.node_injector_contract.injector_contract_id,
        node_id: node.node_id,
        node_depends_on: [newDependsOn],
      };
      onUpdateAttackChainNode([injectToUpdate]);
    }
  };

  /**
   * Actions to do during node drag, especially keeping it horizontal
   * @param _event the mouse event
   * @param node the node that is being dragged
   */
  const nodeDrag = (_event: ReactMouseEvent, node: NodeAttackChainNode) => {
    setDraggingOnGoing(true);
    const { position } = node;
    const { data } = node;
    const dependsOn = flowNodes.find(currentNode => (data.node?.node_depends_on !== null
      && data.node?.node_depends_on!.find(value => value.dependency_relationship?.node_parent_id === currentNode.id)));
    const dependsTo = flowNodes
      .filter(currentNode => (currentNode.data.node?.node_depends_on !== undefined
        && currentNode.data.node?.node_depends_on !== null
        && currentNode.data.node?.node_depends_on.find(value => value.dependency_relationship?.node_parent_id === node.id) !== undefined))
      .sort((a, b) => a.data.node!.node_depends_duration - b.data.node!.node_depends_duration)[0];
    const aSecond = gapSize / (minutesPerGapAllowed[minutesPerGapIndex] * 60);
    if (dependsOn?.position && position.x <= dependsOn?.position.x) {
      position.x = dependsOn.position.x + aSecond;
    }

    if (dependsTo?.position && position.x >= dependsTo?.position.x) {
      position.x = dependsTo.position.x - aSecond;
    }

    if (node.data.fixedY !== undefined) {
      position.y = node.data.fixedY;
      if (data.node) data.node.node_depends_duration = convertCoordinatesToTime(node.position);
    }
  };

  /**
   * Actions when clicking the new node 'button'
   * @param event
   */
  const onNewNodeClick = (event: ReactMouseEvent) => {
    if (newNodeCursorClickable) {
      const position = reactFlow.screenToFlowPosition({
        x: event.clientX - (newNodeSize / 2),
        y: event.clientY,
      });

      const totalSeconds = (position.x / gapSize) * minutesPerGapAllowed[minutesPerGapIndex] * 60;
      onTimelineClick(totalSeconds);
    }
  };

  /**
   * Actions to do when the mouse move
   * @param eventMove the mouse event
   */
  const onMouseMove = (eventMove: ReactMouseEvent) => {
    if (!draggingOnGoing) {
      const position = reactFlow.screenToFlowPosition({
        x: eventMove.clientX,
        y: eventMove.clientY,
      }, { snapToGrid: false });
      const sidePosition = reactFlow.screenToFlowPosition({
        x: eventMove.clientX - (newNodeSize / 2),
        y: eventMove.clientY,
      }, { snapToGrid: false });

      const viewPort = reactFlow.getViewport();
      setCurrentMousePosition({
        x: ((position.x * reactFlow.getZoom()) + viewPort.x - (newNodeSize / 2)),
        y: ((position.y * reactFlow.getZoom()) + viewPort.y - (newNodeSize / 2)),
      });

      if (startDate === undefined) {
        const momentOfTime = moment.utc(
          moment.duration(convertCoordinatesToTime(
            {
              x: sidePosition.x > 0 ? sidePosition.x : 0,
              y: sidePosition.y,
            },
          ), 's').asMilliseconds(),
        );

        setCurrentMouseTime(`${momentOfTime.dayOfYear() - 1} d, ${momentOfTime.hour()} h, ${momentOfTime.minute()} m`);
      } else {
        const momentOfTime = moment.utc(startDate)
          .add(-new Date().getTimezoneOffset() / 60, 'h')
          .add(convertCoordinatesToTime({
            x: sidePosition.x > 0 ? sidePosition.x : 0,
            y: sidePosition.y,
          }), 's');

        setCurrentMouseTime(momentOfTime.format('MMMM Do, YYYY - h:mmA'));
      }
    }
  };

  /**
   * Taking care of the panning of the timeline
   * @param _event the mouse event
   * @param viewport the updated viewport
   */
  const panTimeline = (_event: MouseEvent | TouchEvent | null, viewport: Viewport) => {
    setViewportData(viewport);
  };

  /**
   * Updating the time between each gap
   * @param incrementIndex increment or decrement the index to get the current minutesPerGap
   */
  const updateMinutesPerGap = (incrementIndex: number) => {
    const nodesList = flowNodes.filter(currentNode => currentNode.type !== 'phantom');
    setFlowNodes(nodesList);
    setDraggingOnGoing(true);
    setMinutesPerGapIndex(minutesPerGapIndex + incrementIndex);
    setDraggingOnGoing(false);
  };

  const onReconnectEnd = (event: ReactMouseEvent, edge: Edge, handleType: 'source' | 'target', connectionState: Omit<ConnectionState, 'inProgress'>) => {
    if (!connectionState.isValid) {
      const node = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === edge.target);
      if (node !== undefined) {
        const injectToUpdate = {
          ...injectsMap[node.node_id],
          node_injector_contract: node.node_injector_contract.injector_contract_id,
          node_id: node.node_id,
          node_depends_on: undefined,
        };
        onUpdateAttackChainNode([injectToUpdate]);
      }
    } else if (handleType === 'source') {
      const updates = [];
      const injectToRemove = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === edge.target);
      const injectToUpdate = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === connectionState.toNode?.id);

      const parent = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === connectionState.fromNode?.id);

      if (parent !== undefined
        && injectToUpdate !== undefined
        && injectToRemove !== undefined
        && parent.node_depends_duration < injectToUpdate.node_depends_duration) {
        const injectToRemoveEdge = {
          ...injectsMap[injectToRemove.node_id],
          node_injector_contract: injectToRemove.node_injector_contract.injector_contract_id,
          node_id: injectToRemove.node_id,
          node_depends_on: undefined,
        };
        updates.push(injectToRemoveEdge);
        const newDependsOn: AttackChainEdge = {
          dependency_relationship: {
            node_children_id: injectToUpdate.node_id,
            node_parent_id: edge.source,
          },
        };
        const injectToUpdateEdge = {
          ...injectsMap[injectToUpdate.node_id],
          node_injector_contract: injectToUpdate.node_injector_contract.injector_contract_id,
          node_id: injectToUpdate.node_id,
          node_depends_on: [newDependsOn],
        };
        updates.push(injectToUpdateEdge);
        onUpdateAttackChainNode(updates);
      }
    } else {
      const node = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === edge.target);
      const parent = nodes.find(currentAttackChainNode => currentAttackChainNode.node_id === connectionState.toNode?.id);
      if (node !== undefined && parent !== undefined && parent.node_depends_duration < node.node_depends_duration) {
        const newDependsOn: AttackChainEdge = {
          dependency_relationship: {
            node_children_id: node.node_id,
            node_parent_id: connectionState.toNode?.id,
          },
        };
        const injectToUpdate = {
          ...injectsMap[node.node_id],
          node_injector_contract: node.node_injector_contract.injector_contract_id,
          node_id: node.node_id,
          node_depends_on: [newDependsOn],
        };
        onUpdateAttackChainNode([injectToUpdate]);
      }
    }
    updateNodes();
  };
  return (
    <>
      {nodes.length > 0 ? (
        <div
          className={`${classes.container} chainedTimeline`}
          style={{
            width: '100%',
            height: 'calc(100vh - 400px)',
          }}
        >
          <ReactFlow
            colorMode={theme.palette.mode}
            nodes={flowNodes}
            edges={edges}
            onNodesChange={onFlowNodesChange}
            onEdgesChange={onEdgesChange}
            nodeTypes={nodeTypes}
            nodesDraggable={permissions.canManage}
            nodesConnectable={permissions.canManage}
            nodesFocusable={false}
            elementsSelectable={permissions.canManage}
            onNodeDrag={nodeDrag}
            onNodeDragStop={nodeDragStop}
            onNodeDragStart={nodeDragStart}
            onNodeMouseEnter={hideNewNode}
            onNodeMouseLeave={showNewNode}
            onConnectStart={connectStart}
            onConnectEnd={connectEnd}
            onConnect={connect}
            onEdgeMouseEnter={hideNewNode}
            onEdgeMouseLeave={showNewNode}
            onEdgeClick={handleEdgeClick}
            defaultEdgeOptions={defaultEdgeOptions}
            connectionLineType={ConnectionLineType.SmoothStep}
            onMouseMove={onMouseMove}
            onMove={panTimeline}
            proOptions={proOptions}
            translateExtent={[[-60, -50], [Infinity, Infinity]]}
            nodeExtent={[[0, 0], [Infinity, Infinity]]}
            defaultViewport={{
              x: 60,
              y: 50,
              zoom: 0.75,
            }}
            minZoom={0.3}
            onClick={onNewNodeClick}
            onMouseEnter={showNewNode}
            onMouseLeave={hideNewNode}
            onReconnect={() => {}}
            // @ts-expect-error for some reason, the signature here is not well defined
            onReconnectEnd={onReconnectEnd}
            edgesReconnectable={true}
          >
            <div
              id="newBox"
              className={!connectOnGoing ? classes.newBox : ''}
              style={{
                top: currentMousePosition.y,
                left: currentMousePosition.x,
                visibility: newNodeCursorVisibility,
              }}
            >
              <NodePhantom
                time={currentMouseTime}
                newNodeSize={newNodeSize}
              />
            </div>
            <div
              onMouseEnter={hideNewNode}
              onMouseLeave={showNewNode}
            >
              <Controls
                showFitView={false}
                showZoom={false}
                showInteractive={false}
                orientation="horizontal"
              >
                <Tooltip title={t('Fit view')}>
                  <div>
                    <ControlButton
                      onClick={() => reactFlow.fitView({ duration: 500 })}
                    >
                      <CropFree />
                    </ControlButton>
                  </div>
                </Tooltip>
                <Tooltip title={t('Increase time interval')}>
                  <div>
                    <ControlButton
                      disabled={minutesPerGapAllowed.length - 1 === minutesPerGapIndex}
                      onClick={() => updateMinutesPerGap(1)}
                    >
                      <UnfoldLess className={classes.rotatedIcon} />
                    </ControlButton>
                  </div>
                </Tooltip>
                <Tooltip title={t('Reduce time interval')}>
                  <div>
                    <ControlButton
                      disabled={minutesPerGapIndex === 0}
                      onClick={() => updateMinutesPerGap(-1)}
                    >
                      <UnfoldMore className={classes.rotatedIcon} />
                    </ControlButton>
                  </div>
                </Tooltip>
              </Controls>
            </div>
            <CustomTimelineBackground
              gap={gapSize}
              minutesPerGap={minutesPerGapAllowed[minutesPerGapIndex]}
            />
            <CustomTimelinePanel
              gap={gapSize}
              minutesPerGap={minutesPerGapAllowed[minutesPerGapIndex]}
              viewportData={viewportData}
              startDate={startDate}
            />

            <MiniMap
              pannable={true}
              onMouseEnter={hideNewNode}
              onMouseLeave={showNewNode}
              ariaLabel={null}
            />
          </ReactFlow>
        </div>
      ) : null}
    </>
  );
};

const ChainedTimeline: FunctionComponent<Props> = (props) => {
  return (
    <ReactFlowProvider>
      <ChainedTimelineFlow {...props} />
    </ReactFlowProvider>
  );
};

export default ChainedTimeline;
