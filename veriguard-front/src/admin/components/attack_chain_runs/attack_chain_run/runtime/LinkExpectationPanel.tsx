/* eslint-disable i18next/no-literal-string -- Phase 9 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import {
  CheckCircle as CheckCircleIcon,
  Close as CloseIcon,
  HelpOutline as HelpOutlineIcon,
  HourglassEmpty as HourglassEmptyIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemIcon, ListItemText, Stack, Typography } from '@mui/material';
import { type ReactElement } from 'react';

import { type LinkExpectationItem, type LinkExpectationStatus } from './attackChainRuntimeTypes';

interface Props { items: LinkExpectationItem[] }

const STATUS_STYLE: Record<LinkExpectationStatus, {
  label: string;
  color: 'success' | 'error' | 'warning' | 'default' | 'info';
  icon: ReactElement;
}> = {
  SUCCESS: {
    label: 'matched',
    color: 'success',
    icon: <CheckCircleIcon fontSize="small" color="success" />,
  },
  PARTIAL: {
    label: 'partial',
    color: 'warning',
    icon: <WarningIcon fontSize="small" color="warning" />,
  },
  FAILED: {
    label: 'no match',
    color: 'error',
    icon: <CloseIcon fontSize="small" color="error" />,
  },
  PENDING: {
    label: 'pending',
    color: 'info',
    icon: <HourglassEmptyIcon fontSize="small" color="info" />,
  },
  UNKNOWN: {
    label: 'unknown',
    color: 'default',
    icon: <HelpOutlineIcon fontSize="small" color="disabled" />,
  },
};

/**
 * 链路级 SOC correlation rules 侧边面板（spec §6.3.4）.
 *
 * 列出每条 AttackChainLinkExpectation 的：connector/规则名、状态图标、命中信息（如 incident#）、
 * 失败原因（如 "no match (window 2h)"）。空列表时显示提示 "本链路无 SOC correlation rule 配置"。
 */
const LinkExpectationPanel = ({ items }: Props) => {
  if (items.length === 0) {
    return (
      <Box data-testid="link-expectation-panel-empty" sx={{ p: 2 }}>
        <Typography variant="body2" color="text.secondary">
          本链路未配 SOC correlation rule（DETECTION 维度仅按节点级 expectation 计算）
        </Typography>
      </Box>
    );
  }
  return (
    <Box data-testid="link-expectation-panel">
      <Typography
        variant="subtitle2"
        sx={{
          p: 2,
          pb: 1,
        }}
      >
        链路级 SOC Correlation Rules
      </Typography>
      <List dense disablePadding>
        {items.map((item) => {
          const style = STATUS_STYLE[item.status];
          const secondaryText = (() => {
            switch (item.status) {
              case 'SUCCESS':
                return item.incidentRef
                  ? `命中 ${item.incidentRef}${item.score != null ? ` · 分数 ${item.score}/${item.expectedScore ?? '?'}` : ''}`
                  : '已命中';
              case 'PARTIAL':
                return item.score != null
                  ? `部分命中 · 分数 ${item.score}/${item.expectedScore ?? '?'}`
                  : '部分命中';
              case 'FAILED':
                return item.failureReason ?? '0 命中';
              case 'UNKNOWN':
                return item.unknownReason ?? '查询失败 / 已过期';
              case 'PENDING':
                return '等待 SOC 查询';
              default:
                return null;
            }
          })();
          return (
            <ListItem
              key={item.id}
              data-testid={`link-expectation-${item.status.toLowerCase()}`}
              sx={{
                py: 0.75,
                borderBottom: 1,
                borderColor: 'divider',
              }}
            >
              <ListItemIcon sx={{ minWidth: 32 }}>{style.icon}</ListItemIcon>
              <ListItemText
                primary={(
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip label={item.connectorId} size="small" variant="outlined" />
                    <Typography variant="body2">{item.ruleDisplayName}</Typography>
                  </Stack>
                )}
                secondary={(
                  <Typography variant="caption" color="text.secondary">
                    {style.label}
                    {secondaryText && ` · ${secondaryText}`}
                  </Typography>
                )}
              />
            </ListItem>
          );
        })}
      </List>
    </Box>
  );
};

export default LinkExpectationPanel;
