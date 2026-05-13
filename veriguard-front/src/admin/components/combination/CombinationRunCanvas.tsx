/* eslint-disable i18next/no-literal-string -- IPv6 §3.6 ★2 攻击组合 UI 首发与既有
   VeriguardConsole / StabilityTrendView 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Alert, Box, Button, Chip, LinearProgress, Paper, Stack, Tab, Table, TableBody,
  TableCell, TableHead, TableRow, Tabs, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import {
  type AttackCombinationHitState,
  type AttackCombinationResultOutput, type AttackCombinationRunOutput,
  type AttackCombinationRunStatus,
  cancelCombinationRun, fetchCombinationRun, fetchRunResults, pauseCombinationRun,
  recomputeClusters, resumeCombinationRun,
} from '../../../actions/combination/combination-actions';
import ClusterTreeView from './ClusterTreeView';
import SeverityConfigEditor from './SeverityConfigEditor';

const POLL_INTERVAL_MS = 5_000;
const RESULTS_PAGE_SIZE = 25;

const STATUS_COLOR: Record<AttackCombinationRunStatus, 'default' | 'info' | 'warning' | 'success' | 'error'> = {
  pending: 'default',
  running: 'info',
  paused: 'warning',
  completed: 'success',
  cancelled: 'default',
  failed: 'error',
};

const HIT_STATE_COLOR: Record<AttackCombinationHitState, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  pending: 'default',
  running: 'info',
  hit: 'success',
  miss: 'warning',
  timeout: 'warning',
  failed: 'error',
};

const HIT_STATE_ORDER: AttackCombinationHitState[] = [
  'hit', 'miss', 'timeout', 'failed', 'pending', 'running',
];

const formatDate = (iso: string | null): string => {
  if (!iso) {
    return '-';
  }
  return new Date(iso).toLocaleString();
};

type TabKey = 'overview' | 'cluster_asset' | 'cluster_device' | 'results' | 'severity';

type OverviewProps = {
  run: AttackCombinationRunOutput;
  onRecomputeClusters: () => void;
  canRecompute: boolean;
  recomputing: boolean;
};

const OverviewTab = ({ run, onRecomputeClusters, canRecompute, recomputing }: OverviewProps) => (
  <Stack spacing={2}>
    <Typography variant="body2" color="text.secondary">
      任务完成时会自动触发聚类与分级评分。若已完成但需要按更新后的配置重新评分，请在「分级配置」页保存后回到此处重算。
    </Typography>
    <Box>
      <Button
        variant="outlined"
        onClick={() => onRecomputeClusters()}
        disabled={!canRecompute || recomputing}
      >
        {recomputing ? '正在触发…' : '重算聚类'}
      </Button>
      {!canRecompute && (
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ ml: 2 }}
        >
          仅在任务状态为 completed 时可手动重算聚类。
        </Typography>
      )}
    </Box>
    <Box>
      <Typography variant="subtitle2" gutterBottom>命中状态分布</Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>状态</TableCell>
            <TableCell align="right">数量</TableCell>
            <TableCell align="right">占比</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {HIT_STATE_ORDER.map((s) => {
            const count = Number(run.attack_combination_run_hit_state_counts?.[s] ?? 0);
            const total = run.attack_combination_run_total_results || 1;
            return (
              <TableRow key={s}>
                <TableCell>
                  <Chip
                    size="small"
                    label={s}
                    color={HIT_STATE_COLOR[s]}
                  />
                </TableCell>
                <TableCell align="right">{count.toLocaleString()}</TableCell>
                <TableCell align="right">{`${((count / total) * 100).toFixed(2)}%`}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </Box>
  </Stack>
);

const ResultsTab = ({ runId }: { runId: string }) => {
  const [results, setResults] = useState<AttackCombinationResultOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchRunResults(runId, {
          page: 0,
          size: RESULTS_PAGE_SIZE,
        });
        if (!cancelled) {
          setResults(data.content);
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '加载结果失败');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [runId]);

  if (loading) {
    return <LinearProgress />;
  }
  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }
  if (results.length === 0) {
    return (
      <Typography
        variant="body2"
        color="text.secondary"
        sx={{
          textAlign: 'center',
          py: 4,
        }}
      >
        暂无结果。任务运行中时此处会陆续填充；分页/全量浏览建议进入聚类视图后下钻。
      </Typography>
    );
  }
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>基础攻击</TableCell>
          <TableCell>绕过维度</TableCell>
          <TableCell>资产</TableCell>
          <TableCell>命中</TableCell>
          <TableCell align="right">重试</TableCell>
          <TableCell>执行时间</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {results.map(r => (
          <TableRow key={r.attack_combination_result_id}>
            <TableCell>{r.attack_combination_result_base_attack_type}</TableCell>
            <TableCell><code>{r.attack_combination_result_bypass_dimension_id}</code></TableCell>
            <TableCell><code>{r.attack_combination_result_asset_id}</code></TableCell>
            <TableCell>
              <Chip
                size="small"
                label={r.attack_combination_result_hit_state}
                color={HIT_STATE_COLOR[r.attack_combination_result_hit_state] ?? 'default'}
              />
            </TableCell>
            <TableCell align="right">{r.attack_combination_result_retry_count}</TableCell>
            <TableCell>{formatDate(r.attack_combination_result_executed_at)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
};

const CombinationRunCanvas = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [run, setRun] = useState<AttackCombinationRunOutput | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [tab, setTab] = useState<TabKey>('overview');
  const [recomputingClusters, setRecomputingClusters] = useState<boolean>(false);
  const [actionInflight, setActionInflight] = useState<boolean>(false);
  const [notice, setNotice] = useState<string | null>(null);

  const refresh = useMemo(() => async () => {
    if (!id) {
      return;
    }
    try {
      const fresh = await fetchCombinationRun(id);
      setRun(fresh);
      setError(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载任务失败');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!run || run.attack_combination_run_status !== 'running') {
      return undefined;
    }
    const timer = window.setInterval(() => {
      void refresh();
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [run, refresh]);

  if (loading) {
    return <LinearProgress />;
  }
  if (error || !run) {
    return <Alert severity="error">{error ?? '任务未找到'}</Alert>;
  }

  const status = run.attack_combination_run_status;
  const canPause = status === 'running';
  const canResume = status === 'paused';
  const canCancel = status === 'running' || status === 'paused' || status === 'pending';
  const canRecomputeClusters = status === 'completed';

  const handlePause = async () => {
    if (!id) {
      return;
    }
    setActionInflight(true);
    try {
      await pauseCombinationRun(id);
      await refresh();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '暂停失败');
    } finally {
      setActionInflight(false);
    }
  };
  const handleResume = async () => {
    if (!id) {
      return;
    }
    setActionInflight(true);
    try {
      await resumeCombinationRun(id);
      await refresh();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '恢复失败');
    } finally {
      setActionInflight(false);
    }
  };
  const handleCancel = async () => {
    if (!id) {
      return;
    }
    setActionInflight(true);
    try {
      await cancelCombinationRun(id);
      await refresh();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '取消失败');
    } finally {
      setActionInflight(false);
    }
  };
  const handleRecomputeClusters = async () => {
    if (!id) {
      return;
    }
    setRecomputingClusters(true);
    setNotice(null);
    try {
      const result = await recomputeClusters(id);
      setNotice(`聚类重算已触发：${result.cluster_recompute_status} (job=${result.cluster_recompute_job_id})`);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '触发重算失败');
    } finally {
      setRecomputingClusters(false);
    }
  };

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
        <Box>
          <Stack direction="row" alignItems="center" spacing={2}>
            <Button size="small" onClick={() => navigate('/admin/combinations')}>← 返回列表</Button>
            <Typography variant="h5">{run.attack_combination_run_name}</Typography>
            <Chip
              size="small"
              label={status}
              color={STATUS_COLOR[status] ?? 'default'}
            />
          </Stack>
          <Typography variant="caption" color="text.secondary">{run.attack_combination_run_id}</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button size="small" onClick={() => { void refresh(); }} disabled={actionInflight}>刷新</Button>
          <Button size="small" onClick={() => { void handlePause(); }} disabled={!canPause || actionInflight}>暂停</Button>
          <Button size="small" onClick={() => { void handleResume(); }} disabled={!canResume || actionInflight}>恢复</Button>
          <Button size="small" color="error" onClick={() => { void handleCancel(); }} disabled={!canCancel || actionInflight}>取消</Button>
        </Stack>
      </Stack>

      <Paper sx={{ p: 2 }}>
        <Stack spacing={1}>
          <Box>
            <Stack direction="row" justifyContent="space-between">
              <Typography variant="body2">
                进度：
                {' '}
                {run.attack_combination_run_completed_count.toLocaleString()}
                {' / '}
                {run.attack_combination_run_total_results.toLocaleString()}
                {'  ('}
                {(run.attack_combination_run_progress_percent ?? 0).toFixed(1)}
                %)
              </Typography>
              <Typography variant="caption" color="text.secondary">
                超时阈值：
                {run.attack_combination_run_timeout_hours}
                {' 小时 | 速率：'}
                {run.attack_combination_run_rate_limit_per_second}
                {' req/s | 并发：'}
                {run.attack_combination_run_concurrency}
                {' | 重试：'}
                {run.attack_combination_run_max_retries}
              </Typography>
            </Stack>
            <LinearProgress
              variant="determinate"
              value={Math.min(100, run.attack_combination_run_progress_percent ?? 0)}
              sx={{
                mt: 0.5,
                height: 8,
                borderRadius: 1,
              }}
            />
          </Box>

          <Stack
            direction="row"
            spacing={3}
            sx={{
              mt: 1,
              flexWrap: 'wrap',
            }}
          >
            <Typography variant="body2">
              基础攻击：
              {' '}
              {run.attack_combination_run_base_attack_types.join(' / ') || '-'}
            </Typography>
            <Typography variant="body2">
              维度数：
              {' '}
              {run.attack_combination_run_bypass_dimension_ids.length}
            </Typography>
            <Typography variant="body2">
              资产数：
              {' '}
              {run.attack_combination_run_asset_ids.length}
            </Typography>
            <Typography variant="body2">
              组合：
              {' '}
              {run.attack_combination_run_total_combinations.toLocaleString()}
            </Typography>
            <Typography variant="body2">
              结果：
              {' '}
              {run.attack_combination_run_total_results.toLocaleString()}
            </Typography>
            <Typography variant="body2">
              启动：
              {' '}
              {formatDate(run.attack_combination_run_started_at)}
            </Typography>
            <Typography variant="body2">
              完成：
              {' '}
              {formatDate(run.attack_combination_run_completed_at)}
            </Typography>
            <Typography variant="body2">
              到期：
              {' '}
              {formatDate(run.attack_combination_run_expires_at)}
            </Typography>
          </Stack>

          <Stack
            direction="row"
            spacing={1}
            sx={{
              mt: 1,
              flexWrap: 'wrap',
              gap: 1,
            }}
          >
            {HIT_STATE_ORDER.map((s) => {
              const count = run.attack_combination_run_hit_state_counts?.[s] ?? 0;
              return (
                <Chip
                  key={s}
                  size="small"
                  label={`${s}: ${Number(count).toLocaleString()}`}
                  color={HIT_STATE_COLOR[s]}
                />
              );
            })}
          </Stack>
        </Stack>
      </Paper>

      {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}

      <Paper>
        <Tabs
          value={tab}
          onChange={(_, v: TabKey) => setTab(v)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab value="overview" label="概览" />
          <Tab value="cluster_asset" label="聚类（资产视角）" />
          <Tab value="cluster_device" label="聚类（设备视角）" />
          <Tab value="results" label="结果列表" />
          <Tab value="severity" label="分级配置" />
        </Tabs>
        <Box sx={{ p: 2 }}>
          {tab === 'overview' && (
            <OverviewTab run={run} onRecomputeClusters={handleRecomputeClusters} canRecompute={canRecomputeClusters} recomputing={recomputingClusters} />
          )}
          {tab === 'cluster_asset' && (
            <ClusterTreeView runId={run.attack_combination_run_id} dim="asset" />
          )}
          {tab === 'cluster_device' && (
            <ClusterTreeView runId={run.attack_combination_run_id} dim="device" />
          )}
          {tab === 'results' && (
            <ResultsTab runId={run.attack_combination_run_id} />
          )}
          {tab === 'severity' && (
            <SeverityConfigEditor runId={run.attack_combination_run_id} />
          )}
        </Box>
      </Paper>
    </Box>
  );
};

export default CombinationRunCanvas;
