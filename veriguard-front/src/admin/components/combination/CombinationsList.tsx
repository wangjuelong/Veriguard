/* eslint-disable i18next/no-literal-string -- IPv6 §3.6 ★2 攻击组合 UI 首发与既有
   VeriguardConsole / StabilityTrendView 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Box, Button, Chip, LinearProgress, MenuItem, Paper, Select, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  type AttackCombinationRunOutput,
  type AttackCombinationRunStatus,
  fetchCombinationRuns,
} from '../../../actions/combination/combination-actions';
import CombinationTaskEditor from './CombinationTaskEditor';

const STATUS_OPTIONS: ReadonlyArray<{
  value: AttackCombinationRunStatus | '';
  label: string;
}> = [
  {
    value: '',
    label: '全部',
  },
  {
    value: 'pending',
    label: '待启动',
  },
  {
    value: 'running',
    label: '运行中',
  },
  {
    value: 'paused',
    label: '已暂停',
  },
  {
    value: 'completed',
    label: '已完成',
  },
  {
    value: 'cancelled',
    label: '已取消',
  },
  {
    value: 'failed',
    label: '已失败',
  },
];

const STATUS_COLOR: Record<AttackCombinationRunStatus, 'default' | 'info' | 'warning' | 'success' | 'error'> = {
  pending: 'default',
  running: 'info',
  paused: 'warning',
  completed: 'success',
  cancelled: 'default',
  failed: 'error',
};

const PAGE_SIZE = 20;

const formatDate = (iso: string | null): string => {
  if (!iso) {
    return '-';
  }
  return new Date(iso).toLocaleString();
};

const CombinationsList = () => {
  const navigate = useNavigate();
  const [runs, setRuns] = useState<AttackCombinationRunOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<AttackCombinationRunStatus | ''>('');
  const [editorOpen, setEditorOpen] = useState<boolean>(false);

  const load = useMemo(() => async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchCombinationRuns({
        status: statusFilter || undefined,
        page: 0,
        size: PAGE_SIZE,
      });
      setRuns(result.content);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载任务列表失败');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4">攻击组合任务</Typography>
          <Typography variant="body2" color="text.secondary">
            §3.6 ★2：30 000 攻击组合编排（基础攻击 × 绕过维度 × 资产），自动聚类与三因子分级。
          </Typography>
        </Box>
        <Stack direction="row" spacing={2}>
          <Select
            size="small"
            value={statusFilter}
            displayEmpty
            onChange={e => setStatusFilter((e.target.value as AttackCombinationRunStatus | '') || '')}
            sx={{ minWidth: 140 }}
          >
            {STATUS_OPTIONS.map(o => (
              <MenuItem key={o.value || 'all'} value={o.value}>{o.label}</MenuItem>
            ))}
          </Select>
          <Button variant="contained" onClick={() => setEditorOpen(true)}>新建任务</Button>
        </Stack>
      </Stack>

      <Paper sx={{ p: 2 }}>
        {loading && <LinearProgress />}
        {error && (
          <Typography color="error" variant="body2" sx={{ mb: 1 }}>{error}</Typography>
        )}
        {!loading && runs.length === 0 && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              textAlign: 'center',
              py: 4,
            }}
          >
            暂无任务。点击右上角「新建任务」开始第一次攻击组合扫描。
          </Typography>
        )}
        {runs.length > 0 && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>名称</TableCell>
                <TableCell>状态</TableCell>
                <TableCell align="right">总组合</TableCell>
                <TableCell align="right">总结果</TableCell>
                <TableCell align="right">进度</TableCell>
                <TableCell align="right">完成 / 失败</TableCell>
                <TableCell>启动时间</TableCell>
                <TableCell>完成时间</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {runs.map(run => (
                <TableRow
                  key={run.attack_combination_run_id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/admin/combinations/${run.attack_combination_run_id}`)}
                >
                  <TableCell>
                    <Stack>
                      <Typography variant="body2">{run.attack_combination_run_name}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {run.attack_combination_run_id}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={run.attack_combination_run_status}
                      color={STATUS_COLOR[run.attack_combination_run_status] ?? 'default'}
                    />
                  </TableCell>
                  <TableCell align="right">
                    {run.attack_combination_run_total_combinations.toLocaleString()}
                  </TableCell>
                  <TableCell align="right">
                    {run.attack_combination_run_total_results.toLocaleString()}
                  </TableCell>
                  <TableCell align="right">
                    {`${(run.attack_combination_run_progress_percent ?? 0).toFixed(1)}%`}
                  </TableCell>
                  <TableCell align="right">
                    {`${run.attack_combination_run_completed_count} / ${run.attack_combination_run_failed_count}`}
                  </TableCell>
                  <TableCell>{formatDate(run.attack_combination_run_started_at)}</TableCell>
                  <TableCell>{formatDate(run.attack_combination_run_completed_at)}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      onClick={(ev) => {
                        ev.stopPropagation();
                        navigate(`/admin/combinations/${run.attack_combination_run_id}`);
                      }}
                    >
                      详情
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      <CombinationTaskEditor
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        onCreated={(run) => {
          setEditorOpen(false);
          navigate(`/admin/combinations/${run.attack_combination_run_id}`);
        }}
      />
    </Box>
  );
};

export default CombinationsList;
