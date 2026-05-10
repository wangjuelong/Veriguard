import { Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, type OnConnect, Position, type XYPosition } from '@xyflow/react';
import moment from 'moment';
import { memo, type MouseEvent, useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackChainNodeOutputType, type AttackChainNodeStore } from '../../actions/attack_chain_nodes/AttackChainNode';
import { computeNodeBadgeState } from '../../admin/components/attack_chains/attack_chain/editor/attackChainNodeEditAdapters';
import { AttackChainNodeSettingsContext } from '../../admin/components/attack_chains/attack_chain/editor/AttackChainNodeSettingsContext';
import NodeBadge from '../../admin/components/attack_chains/attack_chain/editor/NodeBadge';
import AttackChainNodeIcon from '../../admin/components/common/attack_chain_nodes/AttackChainNodeIcon';
import AttackChainNodePopover from '../../admin/components/common/attack_chain_nodes/AttackChainNodePopover';
import { isNotEmptyField } from '../../utils/utils';
import { useFormatter } from '../i18n';

const useStyles = makeStyles()(theme => ({
  node: {
    position: 'relative',
    border:
      theme.palette.mode === 'dark'
        ? '1px solid rgba(255, 255, 255, 0.12)'
        : '1px solid rgba(0, 0, 0, 0.12)',
    borderRadius: 4,
    width: 250,
    minHeight: '100px',
    height: 'auto',
    padding: '8px 5px 5px 5px',
    zIndex: 10,
  },
  icon: {
    textAlign: 'left',
    margin: '10px 0 0 5px',
  },
  popover: {
    textAlign: 'right',
    width: 'md',
  },
  triggerTime: {
    textAlign: 'right',
    margin: '10px 0 0 5px',
    position: 'absolute',
    top: 10,
    right: 10,
    color: theme.palette.text.secondary,
  },
  badgeSlot: {
    position: 'absolute',
    top: 6,
    left: 40,
    zIndex: 11,
    cursor: 'pointer',
  },
  label: {
    margin: '0 0 0 5px',
    textAlign: 'left',
    fontSize: 15,
    whiteSpace: 'normal',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    display: '-webkit-box',
    WebkitLineClamp: 2,
    WebkitBoxOrient: 'vertical',
    height: 40,
    color: theme.palette.text.primary,
  },
  targets: {
    display: 'flex',
    justifyContent: 'center',
    flexDirection: 'column',
    margin: '0 0 0 5px',
    textAlign: 'left',
    fontSize: 12,
    whiteSpace: 'normal',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  footer: {
    display: 'flex',
    justifyContent: 'space-between',
    p: 1,
    m: 1,
  },
}));

export type NodeAttackChainNode = Node<{
  background?: string;
  color?: string;
  key: string;
  label: string;
  description?: string;
  isTargeted?: boolean;
  isTargeting?: boolean;
  onConnectAttackChainNodes?: OnConnect;
  node?: AttackChainNodeOutputType;
  fixedY?: number;
  startDate?: string;
  targets: string[];
  boundingBox?: {
    topLeft: XYPosition;
    bottomRight: XYPosition;
  };
  onSelectedAttackChainNode(node?: AttackChainNodeOutputType): void;
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

>;

/**
 * The node used to represent an node
 * @param data the props used
 * @constructor
 */
const NodeAttackChainNodeComponent = ({ data }: NodeProps<NodeAttackChainNode>) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const { ft, fld } = useFormatter();
  const settingsContext = useContext(AttackChainNodeSettingsContext);

  /**
   * Converts the duration in second to a string representing the relative time
   * @param durationInSeconds the duration in seconds
   */
  const convertToRelativeTime = (durationInSeconds: number) => {
    const date = moment.utc(moment.duration(0, 'd').add(durationInSeconds, 's').asMilliseconds());
    return `${date.dayOfYear() - 1} d, ${date.hour()} h, ${date.minute()} m`;
  };

  /**
   * Converts the duration in second and a start date into an absolute time
   * @param startDate the start date
   * @param durationInSeconds the duration in seconds
   */
  const convertToAbsoluteTime = (startDate: string, durationInSeconds: number) => {
    const date = moment.utc(startDate)
      .add(durationInSeconds, 's')
      .toDate();

    return `${fld(date)} - ${ft(date)}`;
  };

  /**
   * Actions on click on the node
   */
  const onClick = () => {
    if (data.node) data.onSelectedAttackChainNode(data.node);
  };

  /**
   * Prevent click to avoid double actions to be raised
   * @param event the event to prevent
   */
  const preventClick = (event: MouseEvent) => {
    event.stopPropagation();
  };

  /**
   * Actions if a node is selected (clicked)
   */
  const selectedAttackChainNode = () => {
    if (data.node) data.onSelectedAttackChainNode(data.node);
  };

  const dimNode = !data.node?.node_enabled;

  let borderLeftColor = theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.7)' : 'rgba(0, 0, 0, 0.7)';
  if (!data.node?.node_enabled) {
    borderLeftColor = theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.3)' : 'rgba(0, 0, 0, 0.3)';
  }

  return (
    <div
      className={classes.node}
      style={{
        backgroundColor: data.background,
        color: 'white',
        borderLeftColor,
      }}
      onClick={onClick}
    >
      <div className={classes.icon} style={{ opacity: dimNode ? '0.3' : '1' }}>
        <AttackChainNodeIcon
          isPayload={isNotEmptyField(data.node?.node_injector_contract?.injector_contract_payload)}
          type={
            data.node?.node_injector_contract?.injector_contract_payload
              ? data.node?.node_injector_contract?.injector_contract_payload?.payload_collector_type
              || data.node?.node_injector_contract?.injector_contract_payload?.payload_type
              : data.node?.node_type
          }
        />
      </div>
      {settingsContext && data.node && (
        <div
          className={classes.badgeSlot}
          onClick={preventClick}
          role="presentation"
        >
          <NodeBadge
            state={computeNodeBadgeState(data.node)}
            onClick={() => settingsContext.openNodeSettings(data.node!)}
          />
        </div>
      )}
      {data.startDate !== undefined ? (
        <div
          className={classes.triggerTime}
          style={{ opacity: dimNode ? '0.3' : '1' }}
        >
          {convertToAbsoluteTime(data.startDate, data.node!.node_depends_duration)}
        </div>

      ) : (
        <div
          className={classes.triggerTime}
          style={{ opacity: dimNode ? '0.3' : '1' }}
        >
          {convertToRelativeTime(data.node!.node_depends_duration)}
        </div>

      )}
      <Tooltip title={data.label} style={{ opacity: dimNode ? '0.3' : '1' }}>
        <div className={classes.label}>{data.label}</div>
      </Tooltip>
      <div className={classes.footer}>
        <Tooltip title={`${data.targets.slice(0, 3).join(', ')}`}>
          <div className={classes.targets} style={{ opacity: dimNode ? '0.3' : '1' }}>
            <span>{`${data.targets.slice(0, 3).join(', ')}${data.targets.length > 3 ? ', ...' : ''}`}</span>
          </div>
        </Tooltip>
        <div className={classes.popover}>
          <span onClick={preventClick}>
            <AttackChainNodePopover
              node={data.node!}
              setSelectedAttackChainNodeId={selectedAttackChainNode}
              canBeTested={data.node?.node_testable}
              onDelete={data.onDelete}
              onUpdate={data.onUpdate}
              onCreate={data.onCreate}
            />
          </span>
        </div>

      </div>
      {(data.isTargeted ? (
        <Handle
          type="target"
          id={`target-${data.key}`}
          position={Position.Left}
          isConnectable={true}
          onConnect={data.onConnectAttackChainNodes}
        />
      ) : null)}
      {(data.isTargeting ? (
        <Handle
          type="source"
          id={`source-${data.key}`}
          position={Position.Right}
          isConnectable={true}
          onConnect={data.onConnectAttackChainNodes}
        />
      ) : null)}
    </div>
  );
};

export default memo(NodeAttackChainNodeComponent);
