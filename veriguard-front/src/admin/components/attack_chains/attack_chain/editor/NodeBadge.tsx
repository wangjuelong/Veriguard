/* eslint-disable i18next/no-literal-string -- Phase 8 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import { Repeat as RepeatIcon, Settings as SettingsIcon } from '@mui/icons-material';
import { Badge, Chip, Tooltip } from '@mui/material';

import { type NodeBadgeState } from './attackChainEditorTypes';

interface Props {
  state: NodeBadgeState;
  /** 点击 ⚙ 触发；用于打开 NodeEditDrawer */
  onClick?: () => void;
}

/**
 * 节点右上角 ⚙ 角标（spec §6.3.1）：
 *
 * - {@code OK} —— 灰 ⚙
 * - {@code OVERRIDE} —— 蓝 ⚙ + tooltip "已覆盖默认参数集"
 * - {@code REPEAT} —— ⚙ 旁加 ↻ x N
 * - {@code INCOMPLETE} —— 红 ⚙ + tooltip "未配置：reason"
 */
const NodeBadge = ({ state, onClick }: Props) => {
  switch (state.kind) {
    case 'OK':
      return (
        <Tooltip title="节点配置完整">
          <SettingsIcon
            fontSize="small"
            color="action"
            onClick={onClick}
            sx={{ cursor: onClick ? 'pointer' : 'default' }}
            data-testid="node-badge-ok"
          />
        </Tooltip>
      );
    case 'OVERRIDE':
      return (
        <Tooltip title="节点已覆盖默认参数集">
          <SettingsIcon
            fontSize="small"
            color="primary"
            onClick={onClick}
            sx={{ cursor: onClick ? 'pointer' : 'default' }}
            data-testid="node-badge-override"
          />
        </Tooltip>
      );
    case 'REPEAT':
      return (
        <Tooltip title={`重复执行 ${state.count} 次`}>
          <Badge
            color="secondary"
            badgeContent={(
              <span style={{ fontSize: 10 }}>
                <RepeatIcon style={{ fontSize: 10 }} />
                {' '}
                ×
                {state.count}
              </span>
            )}
            data-testid="node-badge-repeat"
          >
            <SettingsIcon
              fontSize="small"
              color="action"
              onClick={onClick}
              sx={{ cursor: onClick ? 'pointer' : 'default' }}
            />
          </Badge>
        </Tooltip>
      );
    case 'INCOMPLETE':
      return (
        <Tooltip title={`未配置：${state.reason}`}>
          <Chip
            size="small"
            color="error"
            icon={<SettingsIcon fontSize="small" />}
            label="未配置"
            onClick={onClick}
            data-testid="node-badge-incomplete"
            sx={{ cursor: onClick ? 'pointer' : 'default' }}
          />
        </Tooltip>
      );
    default:
      return null;
  }
};

export default NodeBadge;
