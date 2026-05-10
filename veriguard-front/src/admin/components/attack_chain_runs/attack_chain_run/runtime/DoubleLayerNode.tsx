/* eslint-disable i18next/no-literal-string -- Phase 9 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import { Repeat as RepeatIcon } from '@mui/icons-material';
import { Box, Stack, Tooltip, Typography } from '@mui/material';

import { type DoubleLayerNodeData, NODE_LAYER_STATUS_STYLE } from './attackChainRuntimeTypes';

interface Props {
  data: DoubleLayerNodeData;
  /** 点击节点触发；通常打开 NodeRunDetailDrawer */
  onClick?: () => void;
}

/**
 * 节点双层卡（spec §6.3.2）—— ReactFlow 节点 renderer：
 *
 * - 顶部色块：节点名 + 角色，背景按 PREVENTION 状态着色
 * - 底部色块：DETECTION 状态指示
 * - 角标：repeat_count > 1 时显示 ↻ current/total
 */
const DoubleLayerNode = ({ data, onClick }: Props) => {
  const preventionStyle = NODE_LAYER_STATUS_STYLE[data.preventionStatus];
  const detectionStyle = NODE_LAYER_STATUS_STYLE[data.detectionStatus];
  const showRepeatBadge = (data.repeatCount ?? 1) > 1;

  return (
    <Box
      onClick={onClick}
      data-testid="double-layer-node"
      sx={{
        width: 200,
        borderRadius: 1,
        overflow: 'hidden',
        border: 1,
        borderColor: 'divider',
        cursor: onClick ? 'pointer' : 'default',
        position: 'relative',
        boxShadow: 1,
      }}
    >
      <Box
        sx={{
          background: preventionStyle.background,
          color: preventionStyle.color,
          padding: '8px 12px',
          borderStyle: preventionStyle.borderStyle ?? 'solid',
          borderBottom: 1,
          borderBottomColor: 'rgba(255,255,255,0.2)',
        }}
      >
        <Tooltip title={`PREVENTION: ${preventionStyle.preventionLabel}`}>
          <Stack>
            <Typography
              variant="subtitle2"
              sx={{
                fontWeight: 600,
                lineHeight: 1.2,
              }}
            >
              {data.title}
            </Typography>
            {data.subtitle && (
              <Typography
                variant="caption"
                sx={{
                  opacity: 0.8,
                  lineHeight: 1.2,
                }}
              >
                {data.subtitle}
              </Typography>
            )}
          </Stack>
        </Tooltip>
      </Box>
      <Box
        sx={{
          background: detectionStyle.background,
          color: detectionStyle.color,
          padding: '4px 12px',
          fontSize: '0.75rem',
        }}
      >
        <Tooltip title={`DETECTION: ${detectionStyle.detectionLabel}`}>
          <Typography variant="caption" sx={{ fontWeight: 500 }}>
            DETECTION:
            {' '}
            {detectionStyle.detectionLabel}
          </Typography>
        </Tooltip>
      </Box>
      {showRepeatBadge && (
        <Tooltip title={`重复执行 ${data.currentIteration ?? 0}/${data.repeatCount}`}>
          <Box
            data-testid="repeat-badge"
            sx={{
              position: 'absolute',
              top: 4,
              right: 4,
              background: 'rgba(0,0,0,0.6)',
              color: '#ffffff',
              borderRadius: 4,
              padding: '0 6px',
              fontSize: '0.7rem',
              display: 'flex',
              alignItems: 'center',
              gap: 0.25,
            }}
          >
            <RepeatIcon sx={{ fontSize: 12 }} />
            {data.currentIteration ?? 0}
            /
            {data.repeatCount}
          </Box>
        </Tooltip>
      )}
    </Box>
  );
};

export default DoubleLayerNode;
