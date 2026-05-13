/* eslint-disable i18next/no-literal-string -- IPv6 PR C3 follow-up: coverage run detail */
import {
  Alert, Box, Button, Chip, LinearProgress, Link, MenuItem, Paper, Stack, Tab, Table,
  TableBody, TableCell, TableHead, TablePagination, TableRow, Tabs, TextField, Typography,
} from '@mui/material';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import {
  type CoverageBaselineOutput,
  type CoverageCellOutput,
  type CoverageHitState,
  type CoverageRunOutput,
  type CoverageRunStatus,
  fetchCoverageBaseline,
  fetchCoverageMatrix,
  fetchCoverageRun,
  fetchCoverageRuns,
} from '../../../actions/coverage/coverage-actions';
import CellDetailDrawer from './CellDetailDrawer';
import CoverageDiffView from './CoverageDiffView';
import CoverageMatrixView from './CoverageMatrixView';

const HIT_STATE_COLOR: Record<CoverageHitState, 'success' | 'error' | 'warning' | 'default'> = {
  hit: 'success',
  miss: 'error',
  timeout: 'warning',
  out_of_scope: 'default',
};

const HIT_STATE_OPTIONS: {
  value: CoverageHitState | '';
  label: string;
}[] = [
  {
    value: '',
    label: '全部',
  },
  {
    value: 'hit',
    label: '有覆盖',
  },
  {
    value: 'miss',
    label: '无覆盖',
  },
  {
    value: 'timeout',
    label: '超时',
  },
  {
    value: 'out_of_scope',
    label: '不适用',
  },
];

const POLL_INTERVAL_MS = 5_000;

const STATUS_COLOR: Record<CoverageRunStatus, 'default' | 'info' | 'success' | 'error'> = {
  pending: 'default',
  running: 'info',
  completed: 'success',
  failed: 'error',
  cancelled: 'default',
};

type TabKey = 'matrix' | 'cells' | 'diff';

const formatDate = (iso: string | null): string => {
  if (!iso) {
    return '-';
  }
  return new Date(iso).toLocaleString();
};

const Stat = ({ label, value }: {
  label: string;
  value: string;
}) => (
  <Box>
    <Typography variant="caption" color="text.secondary">{label}</Typography>
    <Typography variant="body2">{value}</Typography>
  </Box>
);

const CellsListPanel = ({ runId }: { runId: string }) => {
  const [cells, setCells] = useState<CoverageCellOutput[]>([]);
  const [total, setTotal] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState<number>(0);
  const [size, setSize] = useState<number>(50);
  const [hitState, setHitState] = useState<CoverageHitState | ''>('');
  const [selected, setSelected] = useState<CoverageCellOutput | null>(null);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError(null);
    fetchCoverageMatrix(runId, {
      hit_state: hitState === '' ? undefined : hitState,
      page,
      size,
    })
      .then((p) => {
        if (mounted) {
          setCells(p.content);
          setTotal(p.total_elements);
        }
      })
      .catch((e: unknown) => {
        if (mounted) {
          setError(e instanceof Error ? e.message : '加载单元格失败');
        }
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [runId, hitState, page, size]);

  return (
    <Stack spacing={1}>
      <TextField
        select
        size="small"
        label="按状态过滤"
        value={hitState}
        onChange={(e) => {
          setHitState(e.target.value as CoverageHitState | '');
          setPage(0);
        }}
        sx={{ maxWidth: 200 }}
      >
        {HIT_STATE_OPTIONS.map(opt => (
          <MenuItem key={opt.value || 'all'} value={opt.value}>{opt.label}</MenuItem>
        ))}
      </TextField>
      {loading && <LinearProgress />}
      {error && <Alert severity="error">{error}</Alert>}
      <Paper variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>资产</TableCell>
              <TableCell>策略</TableCell>
              <TableCell>用例</TableCell>
              <TableCell>命中状态</TableCell>
              <TableCell>告警规则</TableCell>
              <TableCell>观测时间</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {cells.map(c => (
              <TableRow
                key={c.coverage_result_id}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => setSelected(c)}
              >
                <TableCell sx={{
                  fontFamily: 'monospace',
                  fontSize: '0.75rem',
                }}
                >
                  {c.coverage_result_asset_id.slice(0, 12)}
                  …
                </TableCell>
                <TableCell sx={{
                  fontFamily: 'monospace',
                  fontSize: '0.75rem',
                }}
                >
                  {c.coverage_result_policy_id.slice(0, 12)}
                  …
                </TableCell>
                <TableCell sx={{
                  fontFamily: 'monospace',
                  fontSize: '0.75rem',
                }}
                >
                  {c.coverage_result_case_id ? `${c.coverage_result_case_id.slice(0, 12)}…` : '-'}
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={c.coverage_result_hit_state}
                    color={HIT_STATE_COLOR[c.coverage_result_hit_state]}
                  />
                </TableCell>
                <TableCell sx={{
                  fontFamily: 'monospace',
                  fontSize: '0.75rem',
                }}
                >
                  {c.coverage_result_alert_rule_id ?? '-'}
                </TableCell>
                <TableCell>{c.coverage_result_observed_at ?? '-'}</TableCell>
              </TableRow>
            ))}
            {!loading && cells.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    无符合条件的单元格。
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={total}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          rowsPerPage={size}
          onRowsPerPageChange={(e) => {
            setSize(parseInt(e.target.value, 10));
            setPage(0);
          }}
          rowsPerPageOptions={[20, 50, 100, 200]}
        />
      </Paper>
      <CellDetailDrawer cell={selected} onClose={() => setSelected(null)} />
    </Stack>
  );
};

const CoverageRunDetailView = () => {
  const navigate = useNavigate();
  const { id: runId } = useParams<{ id: string }>();
  const [run, setRun] = useState<CoverageRunOutput | null>(null);
  const [baseline, setBaseline] = useState<CoverageBaselineOutput | null>(null);
  const [siblingRuns, setSiblingRuns] = useState<CoverageRunOutput[]>([]);
  const [tab, setTab] = useState<TabKey>('matrix');
  const [error, setError] = useState<string | null>(null);

  const loadRun = useCallback(async () => {
    if (!runId) {
      return;
    }
    try {
      const fresh = await fetchCoverageRun(runId);
      setRun(fresh);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载 run 详情失败');
    }
  }, [runId]);

  useEffect(() => {
    void loadRun();
  }, [loadRun]);

  // load baseline + sibling runs once we know baseline_id
  useEffect(() => {
    if (!run) {
      return;
    }
    const baselineId = run.coverage_run_baseline_id;
    void (async () => {
      try {
        const [b, list] = await Promise.all([
          fetchCoverageBaseline(baselineId),
          fetchCoverageRuns({
            baseline_id: baselineId,
            page: 0,
            size: 50,
          }),
        ]);
        setBaseline(b);
        setSiblingRuns(list.content);
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : '加载基线 / 历史 run 失败');
      }
    })();
  }, [run]);

  // poll while pending/running
  useEffect(() => {
    if (!run) {
      return undefined;
    }
    if (run.coverage_run_status !== 'pending' && run.coverage_run_status !== 'running') {
      return undefined;
    }
    const handle = setInterval(() => {
      void loadRun();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(handle);
  }, [run, loadRun]);

  if (!runId) {
    return <Alert severity="error">缺少 run id</Alert>;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4">覆盖度评估详情</Typography>
          <Typography variant="body2" color="text.secondary">
            run id：
            {runId}
            {baseline && (
              <>
                {' ；基线：'}
                <Link
                  component="button"
                  variant="body2"
                  onClick={() => navigate(
                    `/admin/coverage/baselines/${baseline.coverage_baseline_id}`,
                  )}
                >
                  {baseline.coverage_baseline_name}
                </Link>
              </>
            )}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button onClick={() => navigate('/admin/coverage')}>返回列表</Button>
          {baseline && (
            <Button
              variant="outlined"
              onClick={() => navigate(
                `/admin/coverage/baselines/${baseline.coverage_baseline_id}`,
              )}
            >
              查看该基线全部 run
            </Button>
          )}
        </Stack>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}

      {run && (
        <Paper sx={{ p: 2 }}>
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1 }}>
            <Chip
              size="small"
              label={run.coverage_run_status}
              color={STATUS_COLOR[run.coverage_run_status]}
            />
            <Typography variant="caption" color="text.secondary">
              进度
              {' '}
              {run.coverage_run_progress_percent.toFixed(1)}
              %
            </Typography>
          </Stack>
          <Stack direction="row" spacing={3} sx={{ mb: 1 }}>
            <Stat label="单元格" value={String(run.coverage_run_total_cells)} />
            <Stat label="hit" value={String(run.coverage_run_hit_count)} />
            <Stat label="miss" value={String(run.coverage_run_miss_count)} />
            <Stat label="timeout" value={String(run.coverage_run_timeout_count)} />
            <Stat label="out_of_scope" value={String(run.coverage_run_out_of_scope_count)} />
            <Stat label="开始" value={formatDate(run.coverage_run_started_at)} />
            <Stat label="完成" value={formatDate(run.coverage_run_finished_at)} />
          </Stack>
          {(run.coverage_run_status === 'pending' || run.coverage_run_status === 'running')
            && <LinearProgress variant="determinate" value={run.coverage_run_progress_percent} />}
          {run.coverage_run_error_message && (
            <Alert severity="error" sx={{ mt: 1 }}>
              {run.coverage_run_error_message}
            </Alert>
          )}
        </Paper>
      )}

      <Paper sx={{ p: 0 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v as TabKey)}>
          <Tab
            value="matrix"
            label="矩阵视图"
            disabled={!run || run.coverage_run_status === 'pending'}
          />
          <Tab
            value="cells"
            label="单元格列表"
            disabled={!run || run.coverage_run_status === 'pending'}
          />
          <Tab
            value="diff"
            label="对比另一 run"
            disabled={!run || siblingRuns.length < 2}
          />
        </Tabs>
        <Box sx={{ p: 2 }}>
          {tab === 'matrix' && run && <CoverageMatrixView runId={run.coverage_run_id} />}
          {tab === 'cells' && run && <CellsListPanel runId={run.coverage_run_id} />}
          {tab === 'diff' && run && <CoverageDiffView runId={run.coverage_run_id} />}
        </Box>
      </Paper>
    </Box>
  );
};

export default CoverageRunDetailView;
