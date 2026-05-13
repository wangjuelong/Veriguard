/* eslint-disable i18next/no-literal-string -- IPv6 PR C3 边界覆盖度 UI */
import { Alert, Box, Button, Chip, LinearProgress, Paper, Stack, Tab, Tabs, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import {
  type CoverageBaselineOutput, type CoverageRunOutput, type CoverageRunStatus,
  fetchCoverageBaseline, fetchCoverageRun, fetchCoverageRuns, triggerCoverageRun,
} from '../../../actions/coverage/coverage-actions';
import CoverageDiffView from './CoverageDiffView';
import CoverageMatrixView from './CoverageMatrixView';

const POLL_INTERVAL_MS = 5_000;

const STATUS_COLOR: Record<CoverageRunStatus, 'default' | 'info' | 'success' | 'error'> = {
  pending: 'default',
  running: 'info',
  completed: 'success',
  failed: 'error',
  cancelled: 'default',
};

type TabKey = 'overview' | 'matrix' | 'diff';

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

const CoverageRunCanvas = () => {
  const navigate = useNavigate();
  const { id: baselineId } = useParams<{ id: string }>();
  const [baseline, setBaseline] = useState<CoverageBaselineOutput | null>(null);
  const [runs, setRuns] = useState<CoverageRunOutput[]>([]);
  const [activeRun, setActiveRun] = useState<CoverageRunOutput | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabKey>('overview');
  const [triggering, setTriggering] = useState<boolean>(false);

  const loadBaseline = useMemo(() => async () => {
    if (!baselineId) {
      return;
    }
    try {
      const b = await fetchCoverageBaseline(baselineId);
      setBaseline(b);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载基线失败');
    }
  }, [baselineId]);

  const loadRuns = useMemo(() => async () => {
    if (!baselineId) {
      return;
    }
    try {
      const list = await fetchCoverageRuns({
        baseline_id: baselineId,
        page: 0,
        size: 50,
      });
      setRuns(list.content);
      if (list.content.length > 0) {
        // pick first non-finalized or latest
        const running = list.content.find(r => r.coverage_run_status === 'running' || r.coverage_run_status === 'pending');
        const target = running ?? list.content[0];
        setActiveRun(target);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载 run 列表失败');
    }
  }, [baselineId]);

  useEffect(() => {
    void loadBaseline();
    void loadRuns();
  }, [loadBaseline, loadRuns]);

  // poll active run if pending/running
  useEffect(() => {
    if (!activeRun) {
      return undefined;
    }
    if (activeRun.coverage_run_status !== 'pending' && activeRun.coverage_run_status !== 'running') {
      return undefined;
    }
    const handle = setInterval(async () => {
      try {
        const fresh = await fetchCoverageRun(activeRun.coverage_run_id);
        setActiveRun(fresh);
      } catch {
        // swallow polling error
      }
    }, POLL_INTERVAL_MS);
    return () => clearInterval(handle);
  }, [activeRun]);

  const handleTrigger = async () => {
    if (!baselineId) {
      return;
    }
    setTriggering(true);
    setError(null);
    try {
      await triggerCoverageRun(baselineId);
      // reload run list
      await loadRuns();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '触发评估失败');
    } finally {
      setTriggering(false);
    }
  };

  if (!baselineId) {
    return <Alert severity="error">缺少 baseline id</Alert>;
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
          <Typography variant="h4">{baseline?.coverage_baseline_name ?? '加载中…'}</Typography>
          <Typography variant="body2" color="text.secondary">
            类型：
            {baseline?.coverage_baseline_coverage_type === 'boundary' ? '边界资产覆盖度（§3.1）' : '流量边界覆盖度（§4.1）'}
            {baseline ? `；用例 ${baseline.coverage_baseline_case_ids.length} 个；资产组 ${baseline.coverage_baseline_asset_group_id}` : ''}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button onClick={() => navigate('/admin/coverage')}>返回列表</Button>
          <Button variant="contained" onClick={handleTrigger} disabled={triggering}>
            {triggering ? '正在触发…' : '触发评估'}
          </Button>
        </Stack>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}

      {activeRun && (
        <Paper sx={{ p: 2 }}>
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="body2">当前 run：</Typography>
            <Typography variant="caption" color="text.secondary">{activeRun.coverage_run_id}</Typography>
            <Chip
              size="small"
              label={activeRun.coverage_run_status}
              color={STATUS_COLOR[activeRun.coverage_run_status]}
            />
          </Stack>
          <Stack direction="row" spacing={3} sx={{ mb: 1 }}>
            <Stat label="进度" value={`${activeRun.coverage_run_progress_percent.toFixed(1)}%`} />
            <Stat label="单元格" value={String(activeRun.coverage_run_total_cells)} />
            <Stat label="hit" value={String(activeRun.coverage_run_hit_count)} />
            <Stat label="miss" value={String(activeRun.coverage_run_miss_count)} />
            <Stat label="timeout" value={String(activeRun.coverage_run_timeout_count)} />
            <Stat label="out_of_scope" value={String(activeRun.coverage_run_out_of_scope_count)} />
            <Stat label="开始" value={formatDate(activeRun.coverage_run_started_at)} />
            <Stat label="完成" value={formatDate(activeRun.coverage_run_finished_at)} />
          </Stack>
          {(activeRun.coverage_run_status === 'pending' || activeRun.coverage_run_status === 'running')
            && <LinearProgress variant="determinate" value={activeRun.coverage_run_progress_percent} />}
        </Paper>
      )}

      <Paper sx={{ p: 0 }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v as TabKey)}>
          <Tab value="overview" label="概览" />
          <Tab value="matrix" label="覆盖矩阵" disabled={!activeRun || activeRun.coverage_run_status === 'pending'} />
          <Tab value="diff" label="对比" disabled={!activeRun || runs.length < 2} />
        </Tabs>
        <Box sx={{ p: 2 }}>
          {tab === 'overview' && (
            <Stack spacing={1}>
              <Typography variant="body2" color="text.secondary">
                历史 run（共
                {' '}
                {runs.length}
                {' '}
                条）：
              </Typography>
              {runs.map(r => (
                <Box
                  key={r.coverage_run_id}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2,
                    cursor: 'pointer',
                    py: 0.5,
                    borderBottom: '1px dashed',
                    borderColor: 'divider',
                  }}
                  onClick={() => setActiveRun(r)}
                >
                  <Chip size="small" label={r.coverage_run_status} color={STATUS_COLOR[r.coverage_run_status]} />
                  <Typography variant="caption">{r.coverage_run_id}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    创建
                    {' '}
                    {formatDate(r.coverage_run_created_at)}
                    ；hit
                    {' '}
                    {r.coverage_run_hit_count}
                    {' '}
                    / miss
                    {' '}
                    {r.coverage_run_miss_count}
                  </Typography>
                </Box>
              ))}
            </Stack>
          )}
          {tab === 'matrix' && activeRun && <CoverageMatrixView runId={activeRun.coverage_run_id} />}
          {tab === 'diff' && activeRun && <CoverageDiffView runId={activeRun.coverage_run_id} />}
        </Box>
      </Paper>
    </Box>
  );
};

export default CoverageRunCanvas;
