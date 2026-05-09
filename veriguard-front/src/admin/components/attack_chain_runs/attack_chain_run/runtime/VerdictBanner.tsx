/* eslint-disable i18next/no-literal-string -- Phase 9 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import {
  Block as BlockIcon,
  CheckCircle as CheckCircleIcon,
  ExpandLess as ExpandLessIcon,
  ExpandMore as ExpandMoreIcon,
  HelpOutline as HelpOutlineIcon,
  HourglassEmpty as HourglassEmptyIcon,
  Visibility as VisibilityIcon,
} from '@mui/icons-material';
import { Box, Collapse, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import { type ReactElement, useState } from 'react';

import { type LinkVerdict, type VerdictBannerData } from './attackChainRuntimeTypes';

interface Props {
  data: VerdictBannerData;
  /** 点击 verdict 行触发；通常弹出 LinkExpectationPanel */
  onClickDetection?: () => void;
}

const VERDICT_STYLE: Record<LinkVerdict, {
  label: string;
  background: string;
  color: string;
  icon: ReactElement;
}> = {
  FULL_BLOCKED: {
    label: '完全防守',
    background: '#1b5e20',
    color: '#ffffff',
    icon: <CheckCircleIcon sx={{ fontSize: 20 }} />,
  },
  FULL_BREACH: {
    label: '完全失守',
    background: '#b71c1c',
    color: '#ffffff',
    icon: <BlockIcon sx={{ fontSize: 20 }} />,
  },
  PARTIAL: {
    label: '部分防守',
    background: '#e65100',
    color: '#ffffff',
    icon: <VisibilityIcon sx={{ fontSize: 20 }} />,
  },
  PENDING: {
    label: '等待结算',
    background: '#9e9e9e',
    color: '#ffffff',
    icon: <HourglassEmptyIcon sx={{ fontSize: 20 }} />,
  },
  N_A: {
    label: '不适用',
    background: '#eeeeee',
    color: '#616161',
    icon: <HelpOutlineIcon sx={{ fontSize: 20 }} />,
  },
};

/**
 * 链路 verdict 顶部横幅（spec §6.3.3）.
 *
 * - 终态：双行展示 PREVENTION / DETECTION verdict + stats 副文案，可折叠
 * - RUNNING：精简单行 "Running: 4/8 settled · 1 blocked · 2 detected"
 */
const VerdictBanner = ({ data, onClickDetection }: Props) => {
  const [expanded, setExpanded] = useState(true);

  if (data.running && data.runningStats) {
    const { settled, total, blocked, detected } = data.runningStats;
    return (
      <Box
        data-testid="verdict-banner-running"
        sx={{
          background: '#1976d2',
          color: '#ffffff',
          padding: '8px 16px',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <HourglassEmptyIcon sx={{ fontSize: 18 }} />
        <Typography variant="body2">
          运行中：
          {settled}
          /
          {total}
          {' '}
          已结算
          {' · '}
          {blocked}
          {' '}
          被拦下
          {' · '}
          {detected}
          {' '}
          被检测
        </Typography>
      </Box>
    );
  }

  const preventionStyle = VERDICT_STYLE[data.prevention];
  const detectionStyle = VERDICT_STYLE[data.detection];

  return (
    <Box data-testid="verdict-banner" sx={{ width: '100%' }}>
      <Box
        sx={{
          background: preventionStyle.background,
          color: preventionStyle.color,
          padding: '8px 16px',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <Tooltip title={preventionStyle.label}>{preventionStyle.icon}</Tooltip>
        <Typography
          variant="subtitle2"
          sx={{
            flex: 0,
            minWidth: 200,
          }}
        >
          PREVENTION:
          {' '}
          {preventionStyle.label}
        </Typography>
        {expanded && data.preventionStats && (
          <Typography variant="caption" sx={{ opacity: 0.9 }}>
            {data.preventionStats.blocked}
            {' / '}
            {data.preventionStats.total}
            {' '}
            个节点被拦下
            {data.stoppedAtNodeName && ` · 截停于 ${data.stoppedAtNodeName}`}
          </Typography>
        )}
        <Box sx={{ flex: 1 }} />
        <IconButton
          size="small"
          onClick={() => setExpanded(p => !p)}
          aria-label={expanded ? '折叠' : '展开'}
          sx={{ color: preventionStyle.color }}
        >
          {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
        </IconButton>
      </Box>
      <Collapse in={expanded} unmountOnExit>
        <Box
          onClick={onClickDetection}
          data-testid="verdict-banner-detection-row"
          sx={{
            background: detectionStyle.background,
            color: detectionStyle.color,
            padding: '8px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            cursor: onClickDetection ? 'pointer' : 'default',
          }}
        >
          <Tooltip title={detectionStyle.label}>{detectionStyle.icon}</Tooltip>
          <Typography
            variant="subtitle2"
            sx={{
              flex: 0,
              minWidth: 200,
            }}
          >
            DETECTION:
            {' '}
            {detectionStyle.label}
          </Typography>
          {data.detectionStats && (
            <Stack direction="row" spacing={1}>
              <Typography variant="caption" sx={{ opacity: 0.9 }}>
                {data.detectionStats.nodesDetected}
                {' / '}
                {data.detectionStats.nodeTotal}
                {' '}
                节点检出
              </Typography>
              <Typography variant="caption" sx={{ opacity: 0.9 }}>·</Typography>
              <Typography variant="caption" sx={{ opacity: 0.9 }}>
                {data.detectionStats.linksMatched}
                {' / '}
                {data.detectionStats.linkTotal}
                {' '}
                链路 SOC 匹配
              </Typography>
            </Stack>
          )}
        </Box>
      </Collapse>
    </Box>
  );
};

export default VerdictBanner;
