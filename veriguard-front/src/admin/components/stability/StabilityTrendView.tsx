/* eslint-disable i18next/no-literal-string -- PR C5: 稳定性引擎首发与既有 VeriguardConsole
   一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Box,
  Button,
  ButtonGroup,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import Chart from 'react-apexcharts';

import {
  fetchStabilityTrend,
  fetchStabilityTrendDaily,
  type StabilityDailyAggregate,
  type StabilityTrendOutput,
} from '../../../actions/stability/stability-actions';
import { lineChartOptions } from '../../../utils/Charts';

type ViewMode = 'task' | 'day';

const HEIGHT = 360;

interface ChartPoint {
  x: string;
  y: number;
  meta: {
    snapshotId?: string;
    nodeId?: string | null;
    runId?: string | null;
    deviceId?: string | null;
    hitCount: number;
    totalCount: number;
    hitRate: number;
    capturedAt: string;
  };
}

const toPerTaskSeries = (items: StabilityTrendOutput[]): ChartPoint[] => items.map(item => ({
  x: item.snapshot_captured_at,
  y: Number(item.snapshot_hit_rate),
  meta: {
    snapshotId: item.snapshot_id,
    nodeId: item.snapshot_node_id,
    runId: item.snapshot_run_id,
    deviceId: item.snapshot_device_id,
    hitCount: item.snapshot_hit_count,
    totalCount: item.snapshot_total_count,
    hitRate: Number(item.snapshot_hit_rate),
    capturedAt: item.snapshot_captured_at,
  },
}));

const toPerDaySeries = (items: StabilityDailyAggregate[]): ChartPoint[] => items.map(item => ({
  x: item.day,
  y: Number(item.avg_hit_rate),
  meta: {
    hitCount: item.total_hit_count,
    totalCount: item.total_count_sum,
    hitRate: Number(item.avg_hit_rate),
    capturedAt: item.day,
  },
}));

const StabilityTrendView = () => {
  const theme = useTheme();
  const [mode, setMode] = useState<ViewMode>('task');
  const [deviceId, setDeviceId] = useState<string>('');
  const [from, setFrom] = useState<string>('');
  const [to, setTo] = useState<string>('');
  const [taskItems, setTaskItems] = useState<StabilityTrendOutput[]>([]);
  const [dailyItems, setDailyItems] = useState<StabilityDailyAggregate[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        if (mode === 'task') {
          const result = await fetchStabilityTrend({
            deviceId: deviceId || undefined,
            from: from ? new Date(from).toISOString() : undefined,
            to: to ? new Date(to).toISOString() : undefined,
            page: 0,
            size: 500,
          });
          if (!cancelled) setTaskItems(result.items);
        } else {
          const result = await fetchStabilityTrendDaily({
            deviceId: deviceId || undefined,
            from: from ? new Date(from).toISOString() : undefined,
            to: to ? new Date(to).toISOString() : undefined,
          });
          if (!cancelled) setDailyItems(result);
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '加载稳定性趋势失败');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [mode, deviceId, from, to]);

  const series = useMemo(() => {
    const data = mode === 'task' ? toPerTaskSeries(taskItems) : toPerDaySeries(dailyItems);
    return [
      {
        name: '命中率',
        data,
      },
    ];
  }, [mode, taskItems, dailyItems]);

  const options = useMemo(() => {
    const base = lineChartOptions({
      theme,
      isTimeSeries: true,
      xFormatter: (value: string) => new Date(Number(value)).toLocaleString(),
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
          const ratePct = (m.hitRate * 100).toFixed(2);
          const lines: string[] = [];
          lines.push(`<div style="padding:6px 8px;background:#fff;color:#000;font-size:12px;border:1px solid #ccc;">`);
          if (mode === 'task') {
            lines.push(`<div><b>任务节点：</b>${m.nodeId ?? '-'}</div>`);
            if (m.deviceId) lines.push(`<div><b>设备：</b>${m.deviceId}</div>`);
            lines.push(`<div><b>N（实际执行次数）：</b>${m.totalCount}</div>`);
            lines.push(`<div><b>命中次数：</b>${m.hitCount}</div>`);
            lines.push(`<div><b>命中率：</b>${ratePct}%</div>`);
            lines.push(`<div><b>时间：</b>${new Date(m.capturedAt).toLocaleString()}</div>`);
          } else {
            lines.push(`<div><b>日期：</b>${m.capturedAt}</div>`);
            lines.push(`<div><b>累计命中：</b>${m.hitCount} / ${m.totalCount}</div>`);
            lines.push(`<div><b>平均命中率：</b>${ratePct}%</div>`);
          }
          lines.push(`</div>`);
          return lines.join('');
        },
      },
    };
  }, [theme, mode]);

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4">稳定性趋势</Typography>
          <Typography variant="body2" color="text.secondary">
            边界与流量防护设备稳定性命中率（N × 重复执行，N=10 默认）
          </Typography>
        </Box>
        <ButtonGroup size="small" variant="outlined">
          <Button variant={mode === 'task' ? 'contained' : 'outlined'} onClick={() => setMode('task')}>按任务</Button>
          <Button variant={mode === 'day' ? 'contained' : 'outlined'} onClick={() => setMode('day')}>按天</Button>
        </ButtonGroup>
      </Stack>

      <Paper sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} alignItems="flex-end">
          <TextField
            label="设备 ID（可选）"
            size="small"
            value={deviceId}
            onChange={e => setDeviceId(e.target.value)}
            placeholder="asset-..."
            sx={{ minWidth: 200 }}
          />
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
          {(deviceId || from || to) && (
            <Button
              size="small"
              onClick={() => {
                setDeviceId('');
                setFrom('');
                setTo('');
              }}
            >
              清空过滤
            </Button>
          )}
        </Stack>
      </Paper>

      <Paper sx={{ p: 2 }}>
        {error && (
          <Typography color="error" variant="body2" sx={{ mb: 1 }}>
            {error}
          </Typography>
        )}
        {!error && series[0].data.length === 0 && !loading && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              textAlign: 'center',
              py: 4,
            }}
          >
            暂无数据。任务节点配置 N=2~100 次重复执行后，节点结算时会自动产出稳定性数据点。
          </Typography>
        )}
        {!error && series[0].data.length > 0 && (
          <Chart
            options={options}
            series={series}
            type="line"
            width="100%"
            height={HEIGHT}
          />
        )}
        {loading && (
          <Typography variant="caption" color="text.secondary">
            加载中…
          </Typography>
        )}
      </Paper>
    </Box>
  );
};

export default StabilityTrendView;
