/* eslint-disable i18next/no-literal-string -- IPv6 PR C4 边界监控 UI 与既有
   coverage / stability 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Alert, Box, Button, ButtonGroup, CircularProgress, Paper, Stack, Tab, Tabs,
  TextField, Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import Chart from 'react-apexcharts';
import { useNavigate, useParams } from 'react-router';

import {
  deleteMonitoringJob,
  fetchMonitoringJob,
  fetchMonitoringTrend,
  type MonitoringAggregation,
  type MonitoringJobOutput,
  type MonitoringTrendBucket,
  triggerMonitoringJobNow,
} from '../../../actions/monitoring/monitoring-actions';
import { lineChartOptions } from '../../../utils/Charts';
import MonitoringHistoryTable from './MonitoringHistoryTable';
import MonitoringJobEditor from './MonitoringJobEditor';

const HEIGHT = 360;

type TabKey = 'trend' | 'history';

interface ChartPoint {
  x: string;
  y: number;
  meta: MonitoringTrendBucket;
}

const toSeriesData = (buckets: MonitoringTrendBucket[]): ChartPoint[] => buckets.map(b => ({
  x: b.bucket,
  y: Number(b.avg_hit_rate),
  meta: b,
}));

const defaultRange = (aggregation: MonitoringAggregation): {
  from: string;
  to: string;
} => {
  const now = new Date();
  const offsetDays = aggregation === 'hour' ? 7 : 30;
  const from = new Date(now.getTime() - offsetDays * 24 * 3600 * 1000);
  const fmt = (d: Date) => d.toISOString().slice(0, 16);
  return {
    from: fmt(from),
    to: fmt(now),
  };
};

const MonitoringTrendView = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const theme = useTheme();
  const [job, setJob] = useState<MonitoringJobOutput | null>(null);
  const [tab, setTab] = useState<TabKey>('trend');
  const [aggregation, setAggregation] = useState<MonitoringAggregation>('day');
  const initialRange = useMemo(() => defaultRange('day'), []);
  const [from, setFrom] = useState<string>(initialRange.from);
  const [to, setTo] = useState<string>(initialRange.to);
  const [buckets, setBuckets] = useState<MonitoringTrendBucket[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState<boolean>(false);
  const [triggering, setTriggering] = useState<boolean>(false);
  const [reloadKey, setReloadKey] = useState<number>(0);

  useEffect(() => {
    if (!id) return;
    void (async () => {
      try {
        const result = await fetchMonitoringJob(id);
        setJob(result);
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : '加载监控任务失败');
      }
    })();
  }, [id, reloadKey]);

  useEffect(() => {
    if (!id || tab !== 'trend') {
      return () => {};
    }
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchMonitoringTrend(id, {
          aggregation,
          from: new Date(from).toISOString(),
          to: new Date(to).toISOString(),
        });
        if (!cancelled) setBuckets(result.buckets);
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '加载趋势数据失败');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [id, tab, aggregation, from, to, reloadKey]);

  const series = useMemo(() => [{
    name: '命中率',
    data: toSeriesData(buckets),
  }], [buckets]);

  const options = useMemo(() => {
    const base = lineChartOptions({
      theme,
      isTimeSeries: false,
      xFormatter: (value: string) => value,
    });
    return {
      ...base,
      yaxis: {
        ...base.yaxis,
        min: 0,
        max: 1,
        labels: { formatter: (val: number) => `${(val * 100).toFixed(1)}%` },
      },
      tooltip: {
        ...base.tooltip,
        custom: ({ seriesIndex, dataPointIndex, w }: {
          seriesIndex: number;
          dataPointIndex: number;
          w: { config: { series: Array<{ data: ChartPoint[] }> } };
        }) => {
          const point = w.config.series[seriesIndex]?.data[dataPointIndex];
          if (!point) return '';
          const m = point.meta;
          const lines: string[] = [];
          lines.push(`<div style="padding:6px 8px;background:#fff;color:#000;font-size:12px;border:1px solid #ccc;">`);
          lines.push(`<div><b>桶：</b>${m.bucket}</div>`);
          lines.push(`<div><b>平均命中率：</b>${(Number(m.avg_hit_rate) * 100).toFixed(2)}%</div>`);
          lines.push(`<div><b>命中：</b>${m.hit_sum} / ${m.total_sum}</div>`);
          lines.push(`<div><b>未命中：</b>${m.miss_sum}</div>`);
          lines.push(`<div><b>超时：</b>${m.timeout_sum}</div>`);
          lines.push(`<div><b>不适用：</b>${m.out_of_scope_sum}</div>`);
          lines.push(`<div><b>本桶 run 数：</b>${m.run_count}</div>`);
          lines.push(`</div>`);
          return lines.join('');
        },
      },
    };
  }, [theme]);

  const handleTriggerNow = async () => {
    if (!id) return;
    setTriggering(true);
    setError(null);
    try {
      await triggerMonitoringJobNow(id);
      setReloadKey(prev => prev + 1);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '立即触发失败');
    } finally {
      setTriggering(false);
    }
  };

  const handleDelete = async () => {
    if (!id) return;
    if (!window.confirm('确认删除该监控任务？关联历史会一并级联删除。')) return;
    try {
      await deleteMonitoringJob(id);
      navigate('/admin/monitoring');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '删除失败');
    }
  };

  if (!id) {
    return <Alert severity="error">缺少 job id</Alert>;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
        <Box>
          <Typography variant="h4">{job?.monitoring_job_name ?? '加载中…'}</Typography>
          <Typography variant="body2" color="text.secondary">
            cron：
            {job?.monitoring_job_cron_expression ?? '-'}
            {' | '}
            基线：
            {job?.monitoring_job_baseline_id ?? '-'}
            {' | '}
            下次触发：
            {job?.monitoring_job_next_fire_at
              ? new Date(job.monitoring_job_next_fire_at).toLocaleString()
              : '-'}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button onClick={() => setEditorOpen(true)} variant="outlined" size="small">编辑</Button>
          <Button
            onClick={handleTriggerNow}
            variant="contained"
            size="small"
            disabled={triggering}
          >
            {triggering ? '触发中…' : '立即触发'}
          </Button>
          <Button onClick={handleDelete} color="error" size="small">删除</Button>
        </Stack>
      </Stack>

      {error && <Alert severity="error">{error}</Alert>}

      <Tabs value={tab} onChange={(_, v) => setTab(v as TabKey)}>
        <Tab value="trend" label="趋势分析" />
        <Tab value="history" label="历史记录" />
      </Tabs>

      {tab === 'trend' && (
        <>
          <Paper sx={{ p: 2 }}>
            <Stack direction="row" spacing={2} alignItems="flex-end">
              <ButtonGroup size="small" variant="outlined">
                <Button
                  variant={aggregation === 'day' ? 'contained' : 'outlined'}
                  onClick={() => setAggregation('day')}
                >
                  按天
                </Button>
                <Button
                  variant={aggregation === 'hour' ? 'contained' : 'outlined'}
                  onClick={() => setAggregation('hour')}
                >
                  按小时
                </Button>
              </ButtonGroup>
              <TextField
                label="起始时间"
                type="datetime-local"
                size="small"
                value={from}
                onChange={e => setFrom(e.target.value)}
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                label="结束时间"
                type="datetime-local"
                size="small"
                value={to}
                onChange={e => setTo(e.target.value)}
                InputLabelProps={{ shrink: true }}
              />
            </Stack>
          </Paper>
          <Paper sx={{ p: 2 }}>
            {loading && <CircularProgress size={24} />}
            {!loading && buckets.length === 0 && (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{
                  textAlign: 'center',
                  py: 4,
                }}
              >
                所选窗口内无完成的监控运行。Quartz 调度后或点击「立即触发」即可生成数据点。
              </Typography>
            )}
            {!loading && buckets.length > 0 && (
              <Chart
                options={options}
                series={series}
                type="line"
                width="100%"
                height={HEIGHT}
              />
            )}
          </Paper>
        </>
      )}

      {tab === 'history' && <MonitoringHistoryTable jobId={id} />}

      <MonitoringJobEditor
        open={editorOpen}
        initial={job}
        onCancel={() => setEditorOpen(false)}
        onSaved={(updated) => {
          setEditorOpen(false);
          setJob(updated);
          setReloadKey(prev => prev + 1);
        }}
      />
    </Box>
  );
};

export default MonitoringTrendView;
