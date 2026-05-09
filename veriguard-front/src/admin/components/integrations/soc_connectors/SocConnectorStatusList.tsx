/* eslint-disable i18next/no-literal-string -- Phase 11 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import {
  Block as BlockIcon,
  CheckCircle as CheckCircleIcon,
  ErrorOutline as ErrorIcon,
  Refresh as RefreshIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';
import {
  Box,
  Chip,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { type ReactElement } from 'react';

import {
  type SocConnectorHealthStatus,
  type SocConnectorStatusItem,
} from './socConnectorTypes';

interface Props {
  items: SocConnectorStatusItem[];
  /** 触发对单个 connector 的 checkHealth() 重新拉取；undefined 时不渲染按钮 */
  onRefreshOne?: (connectorId: string) => void;
  /** 触发全量刷新；undefined 时不渲染顶部刷新按钮 */
  onRefreshAll?: () => void;
}

const STATUS_STYLE: Record<SocConnectorHealthStatus, {
  label: string;
  color: 'success' | 'warning' | 'error' | 'default';
  icon: ReactElement;
}> = {
  HEALTHY: {
    label: '健康',
    color: 'success',
    icon: <CheckCircleIcon fontSize="small" color="success" />,
  },
  DEGRADED: {
    label: '降级',
    color: 'warning',
    icon: <WarningIcon fontSize="small" color="warning" />,
  },
  UNHEALTHY: {
    label: '不可用',
    color: 'error',
    icon: <ErrorIcon fontSize="small" color="error" />,
  },
  DISABLED: {
    label: '已禁用',
    color: 'default',
    icon: <BlockIcon fontSize="small" color="disabled" />,
  },
};

const formatLastChecked = (iso?: string): string => {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return date.toLocaleString();
};

/**
 * SOC Connector 状态列表（spec §6.3.6 简单表格）.
 *
 * 列：Connector ID / Display Name / Status / Available Rules / Last Health Check / Actions.
 * 空列表时显示运维提示。
 */
const SocConnectorStatusList = ({ items, onRefreshOne, onRefreshAll }: Props) => {
  if (items.length === 0) {
    return (
      <Paper variant="outlined" sx={{ p: 3 }} data-testid="soc-status-list-empty">
        <Stack spacing={1} alignItems="center">
          <BlockIcon color="disabled" />
          <Typography variant="body2" color="text.secondary">
            未注册任何 SOC connector。请在 application.properties 中启用具体实现（如
            {' '}
            <code>veriguard.soc.elastic.enabled=true</code>
            ），或检查 ops 是否已注入凭证环境变量。
          </Typography>
        </Stack>
      </Paper>
    );
  }
  return (
    <Box data-testid="soc-status-list">
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
        <Typography variant="subtitle1" sx={{ flex: 1 }}>SOC Connectors</Typography>
        {onRefreshAll && (
          <Tooltip title="全部重新检查健康">
            <IconButton
              size="small"
              onClick={onRefreshAll}
              data-testid="soc-status-refresh-all"
              aria-label="刷新所有 SOC connector 健康状态"
            >
              <RefreshIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Connector</TableCell>
              <TableCell>显示名</TableCell>
              <TableCell>状态</TableCell>
              <TableCell align="right">可用规则</TableCell>
              <TableCell>上次检查</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {items.map((item) => {
              const style = STATUS_STYLE[item.status];
              return (
                <TableRow
                  key={item.connectorId}
                  data-testid={`soc-status-row-${item.connectorId}`}
                  hover
                >
                  <TableCell>
                    <Chip label={item.connectorId} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell>{item.displayName}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Tooltip title={item.message ?? style.label}>{style.icon}</Tooltip>
                      <Typography variant="body2">{style.label}</Typography>
                      {item.message && item.status !== 'HEALTHY' && (
                        <Typography variant="caption" color="text.secondary">
                          ·
                          {' '}
                          {item.message}
                        </Typography>
                      )}
                    </Stack>
                  </TableCell>
                  <TableCell align="right">
                    {item.availableRuleCount === undefined ? '—' : item.availableRuleCount}
                  </TableCell>
                  <TableCell>{formatLastChecked(item.lastCheckedAt)}</TableCell>
                  <TableCell align="right">
                    {onRefreshOne && (
                      <Tooltip title="重新检查">
                        <IconButton
                          size="small"
                          onClick={() => onRefreshOne(item.connectorId)}
                          data-testid={`soc-status-refresh-${item.connectorId}`}
                          aria-label={`刷新 ${item.connectorId} 健康状态`}
                        >
                          <RefreshIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

export default SocConnectorStatusList;
