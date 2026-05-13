/* eslint-disable i18next/no-literal-string -- IPv6 PR C4 边界监控 UI 与既有
   coverage / stability 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Chip, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  fetchMonitoringHistory,
  type MonitoringRunHistoryOutput,
  type MonitoringRunStatus,
} from '../../../actions/monitoring/monitoring-actions';

interface MonitoringHistoryTableProps { jobId: string }

const STATUS_OPTIONS: ReadonlyArray<{
  value: MonitoringRunStatus | '';
  label: string;
  color: 'default' | 'success' | 'warning' | 'error';
}> = [
  {
    value: '',
    label: '全部',
    color: 'default',
  },
  {
    value: 'triggered',
    label: '触发中',
    color: 'warning',
  },
  {
    value: 'completed',
    label: '已完成',
    color: 'success',
  },
  {
    value: 'failed',
    label: '失败',
    color: 'error',
  },
];

const STATUS_COLOR: Record<MonitoringRunStatus, 'default' | 'success' | 'warning' | 'error'> = {
  triggered: 'warning',
  completed: 'success',
  failed: 'error',
};

const formatDate = (iso: string | null): string => {
  if (!iso) return '-';
  return new Date(iso).toLocaleString();
};

const formatHitRate = (rate: number | null): string => {
  if (rate === null || rate === undefined) return '-';
  return `${(rate * 100).toFixed(2)}%`;
};

const MonitoringHistoryTable = ({ jobId }: MonitoringHistoryTableProps) => {
  const navigate = useNavigate();
  const [items, setItems] = useState<MonitoringRunHistoryOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<MonitoringRunStatus | ''>('');

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchMonitoringHistory(jobId, {
          status: statusFilter || undefined,
          page: 0,
          size: 100,
        });
        if (!cancelled) setItems(result.content);
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '加载历史记录失败');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [jobId, statusFilter]);

  return (
    <Paper sx={{ p: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6">执行历史</Typography>
        <TextField
          select
          size="small"
          label="状态"
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value as MonitoringRunStatus | '')}
          sx={{ minWidth: 140 }}
        >
          {STATUS_OPTIONS.map(o => (
            <MenuItem key={o.value || 'all'} value={o.value}>{o.label}</MenuItem>
          ))}
        </TextField>
      </Stack>

      {error && (
        <Typography color="error" variant="body2" sx={{ mb: 1 }}>{error}</Typography>
      )}
      {!loading && items.length === 0 && (
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{
            textAlign: 'center',
            py: 4,
          }}
        >
          暂无历史记录。Quartz BoundaryMonitoringJob 触发后会自动生成历史；也可点击「立即触发」手动测试。
        </Typography>
      )}
      {items.length > 0 && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>调度时间</TableCell>
              <TableCell>完成时间</TableCell>
              <TableCell>状态</TableCell>
              <TableCell align="right">命中</TableCell>
              <TableCell align="right">未命中</TableCell>
              <TableCell align="right">超时</TableCell>
              <TableCell align="right">不适用</TableCell>
              <TableCell align="right">总数</TableCell>
              <TableCell align="right">命中率</TableCell>
              <TableCell>关联 run</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {items.map(h => (
              <TableRow key={h.monitoring_run_history_id} hover>
                <TableCell>{formatDate(h.monitoring_run_history_scheduled_at)}</TableCell>
                <TableCell>{formatDate(h.monitoring_run_history_finished_at)}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={h.monitoring_run_history_status}
                    color={STATUS_COLOR[h.monitoring_run_history_status]}
                  />
                </TableCell>
                <TableCell align="right">{h.monitoring_run_history_hit_count ?? '-'}</TableCell>
                <TableCell align="right">{h.monitoring_run_history_miss_count ?? '-'}</TableCell>
                <TableCell align="right">{h.monitoring_run_history_timeout_count ?? '-'}</TableCell>
                <TableCell align="right">{h.monitoring_run_history_out_of_scope_count ?? '-'}</TableCell>
                <TableCell align="right">{h.monitoring_run_history_total_count ?? '-'}</TableCell>
                <TableCell align="right">{formatHitRate(h.monitoring_run_history_hit_rate)}</TableCell>
                <TableCell>
                  {h.monitoring_run_history_coverage_run_id
                    ? (
                        <Typography
                          variant="caption"
                          sx={{
                            cursor: 'pointer',
                            textDecoration: 'underline',
                          }}
                          onClick={() =>
                            navigate(
                              `/admin/coverage/baselines/${h.monitoring_run_history_coverage_run_id}`,
                            )}
                        >
                          {h.monitoring_run_history_coverage_run_id.slice(0, 8)}
                        </Typography>
                      )
                    : '-'}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Paper>
  );
};

export default MonitoringHistoryTable;
