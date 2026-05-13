/* eslint-disable i18next/no-literal-string -- IPv6 PR C4 边界监控 UI 与既有
   coverage / stability 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Box, Button, Chip, LinearProgress, MenuItem, Paper, Select, Stack, Switch, Table,
  TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  disableMonitoringJob,
  enableMonitoringJob,
  fetchMonitoringJobs,
  type MonitoringJobOutput,
} from '../../../actions/monitoring/monitoring-actions';
import MonitoringJobEditor from './MonitoringJobEditor';

const PAGE_SIZE = 20;

const ENABLED_FILTER_OPTIONS: ReadonlyArray<{
  value: string;
  label: string;
}> = [
  {
    value: 'all',
    label: '全部',
  },
  {
    value: 'true',
    label: '已启用',
  },
  {
    value: 'false',
    label: '已停用',
  },
];

const formatDate = (iso: string | null): string => {
  if (!iso) return '-';
  return new Date(iso).toLocaleString();
};

const MonitoringJobsList = () => {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState<MonitoringJobOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [enabledFilter, setEnabledFilter] = useState<string>('all');
  const [editorOpen, setEditorOpen] = useState<boolean>(false);

  const load = useMemo(() => async () => {
    setLoading(true);
    setError(null);
    try {
      const enabled = enabledFilter === 'all' ? undefined : enabledFilter === 'true';
      const result = await fetchMonitoringJobs({
        enabled,
        page: 0,
        size: PAGE_SIZE,
      });
      setJobs(result.content);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载监控任务列表失败');
    } finally {
      setLoading(false);
    }
  }, [enabledFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleToggle = async (job: MonitoringJobOutput) => {
    try {
      const updated = job.monitoring_job_enabled
        ? await disableMonitoringJob(job.monitoring_job_id)
        : await enableMonitoringJob(job.monitoring_job_id);
      setJobs(prev => prev.map(j => (j.monitoring_job_id === updated.monitoring_job_id ? updated : j)));
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '切换状态失败');
    }
  };

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4">策略有效性监控</Typography>
          <Typography variant="body2" color="text.secondary">
            §3.2 对安全设备策略进行常态化监控；按 cron 周期触发覆盖度评估，输出按天 / 按小时趋势分析
          </Typography>
        </Box>
        <Stack direction="row" spacing={2}>
          <Select
            size="small"
            value={enabledFilter}
            onChange={e => setEnabledFilter(e.target.value)}
            sx={{ minWidth: 140 }}
          >
            {ENABLED_FILTER_OPTIONS.map(o => (
              <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
            ))}
          </Select>
          <Button variant="contained" onClick={() => setEditorOpen(true)}>新建监控任务</Button>
        </Stack>
      </Stack>

      <Paper sx={{ p: 2 }}>
        {loading && <LinearProgress />}
        {error && (
          <Typography color="error" variant="body2" sx={{ mb: 1 }}>{error}</Typography>
        )}
        {!loading && jobs.length === 0 && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              textAlign: 'center',
              py: 4,
            }}
          >
            暂无监控任务。点击右上角「新建监控任务」绑定一份覆盖度基线 + cron 即可开始常态化监控。
          </Typography>
        )}
        {jobs.length > 0 && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>名称</TableCell>
                <TableCell>cron</TableCell>
                <TableCell>基线</TableCell>
                <TableCell>启用</TableCell>
                <TableCell>下次触发</TableCell>
                <TableCell>上次触发</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {jobs.map(j => (
                <TableRow
                  key={j.monitoring_job_id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/admin/monitoring/${j.monitoring_job_id}`)}
                >
                  <TableCell>
                    <Stack>
                      <Typography variant="body2">{j.monitoring_job_name}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {j.monitoring_job_id}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip size="small" label={j.monitoring_job_cron_expression} />
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {j.monitoring_job_baseline_id}
                    </Typography>
                  </TableCell>
                  <TableCell onClick={ev => ev.stopPropagation()}>
                    <Switch
                      checked={j.monitoring_job_enabled}
                      onChange={() => handleToggle(j)}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>{formatDate(j.monitoring_job_next_fire_at)}</TableCell>
                  <TableCell>{formatDate(j.monitoring_job_last_triggered_at)}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      onClick={(ev) => {
                        ev.stopPropagation();
                        navigate(`/admin/monitoring/${j.monitoring_job_id}`);
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

      <MonitoringJobEditor
        open={editorOpen}
        initial={null}
        onCancel={() => setEditorOpen(false)}
        onSaved={(job) => {
          setEditorOpen(false);
          navigate(`/admin/monitoring/${job.monitoring_job_id}`);
        }}
      />
    </Box>
  );
};

export default MonitoringJobsList;
