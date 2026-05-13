/* eslint-disable i18next/no-literal-string -- IPv6 PR C4 边界监控 UI 与既有
   coverage / stability 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem,
  Stack, TextField, Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';

import {
  type CoverageBaselineOutput,
  fetchCoverageBaselines,
} from '../../../actions/coverage/coverage-actions';
import {
  createMonitoringJob,
  type MonitoringJobOutput,
  updateMonitoringJob,
} from '../../../actions/monitoring/monitoring-actions';

interface MonitoringJobEditorProps {
  open: boolean;
  initial?: MonitoringJobOutput | null;
  onCancel: () => void;
  onSaved: (job: MonitoringJobOutput) => void;
}

interface CronPreset {
  value: string;
  label: string;
}

// 招标 §3.2 最小粒度 1h —— 仅提供 ≥ 1h 的预设，避免引导用户填非法 cron
const CRON_PRESETS: ReadonlyArray<CronPreset> = [
  {
    value: '0 0 * * * ?',
    label: '每小时（整点触发）',
  },
  {
    value: '0 0 0/3 * * ?',
    label: '每 3 小时',
  },
  {
    value: '0 0 0/6 * * ?',
    label: '每 6 小时',
  },
  {
    value: '0 0 0 * * ?',
    label: '每天 00:00',
  },
  {
    value: '0 0 3 * * ?',
    label: '每天 03:00',
  },
  {
    value: '0 0 0 ? * MON',
    label: '每周一 00:00',
  },
];

const submitButtonLabel = (submitting: boolean, isEdit: boolean): string => {
  if (submitting) return '提交中…';
  return isEdit ? '保存' : '创建';
};

const MonitoringJobEditor = ({ open, initial, onCancel, onSaved }: MonitoringJobEditorProps) => {
  const [name, setName] = useState<string>('');
  const [baselineId, setBaselineId] = useState<string>('');
  const [cronExpression, setCronExpression] = useState<string>('0 0 * * * ?');
  const [enabled, setEnabled] = useState<boolean>(true);
  const [description, setDescription] = useState<string>('');
  const [baselines, setBaselines] = useState<CoverageBaselineOutput[]>([]);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const isEdit = Boolean(initial);

  useEffect(() => {
    if (!open) return;
    setError(null);
    if (initial) {
      setName(initial.monitoring_job_name);
      setBaselineId(initial.monitoring_job_baseline_id);
      setCronExpression(initial.monitoring_job_cron_expression);
      setEnabled(initial.monitoring_job_enabled);
      setDescription(initial.monitoring_job_description ?? '');
    } else {
      setName('');
      setBaselineId('');
      setCronExpression('0 0 * * * ?');
      setEnabled(true);
      setDescription('');
    }
    void (async () => {
      try {
        const result = await fetchCoverageBaselines({
          page: 0,
          size: 100,
        });
        setBaselines(result.content);
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : '加载基线列表失败');
      }
    })();
  }, [open, initial]);

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const payload = {
        name,
        baseline_id: baselineId,
        cron_expression: cronExpression,
        enabled,
        description: description || undefined,
      };
      const result = isEdit && initial
        ? await updateMonitoringJob(initial.monitoring_job_id, payload)
        : await createMonitoringJob(payload);
      onSaved(result);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '保存监控任务失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={onCancel} fullWidth maxWidth="sm">
      <DialogTitle>{isEdit ? '编辑监控任务' : '新建监控任务'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="任务名称"
            value={name}
            onChange={e => setName(e.target.value)}
            required
            fullWidth
          />
          <TextField
            select
            label="目标覆盖度基线"
            value={baselineId}
            onChange={e => setBaselineId(e.target.value)}
            required
            fullWidth
            helperText="监控任务触发时调用 PR C3 CoverageRunner.runAsync(baseline_id)"
          >
            {baselines.map(b => (
              <MenuItem key={b.coverage_baseline_id} value={b.coverage_baseline_id}>
                {b.coverage_baseline_name}
                {' ('}
                {b.coverage_baseline_coverage_type}
                )
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="cron 表达式（预设）"
            value={CRON_PRESETS.some(p => p.value === cronExpression) ? cronExpression : ''}
            onChange={e => setCronExpression(e.target.value)}
            fullWidth
            helperText="选择预设或在下方手动输入；招标 §3.2 要求最小粒度 1 小时"
          >
            <MenuItem value="">— 自定义 —</MenuItem>
            {CRON_PRESETS.map(p => (
              <MenuItem key={p.value} value={p.value}>{p.label}</MenuItem>
            ))}
          </TextField>
          <TextField
            label="cron 表达式"
            value={cronExpression}
            onChange={e => setCronExpression(e.target.value)}
            required
            fullWidth
            helperText="Quartz 6 字段格式，例如：0 0 * * * ?（每小时整点）"
          />
          <TextField
            select
            label="启用状态"
            value={enabled ? 'true' : 'false'}
            onChange={e => setEnabled(e.target.value === 'true')}
            fullWidth
          >
            <MenuItem value="true">启用</MenuItem>
            <MenuItem value="false">停用</MenuItem>
          </TextField>
          <TextField
            label="描述（可选）"
            value={description}
            onChange={e => setDescription(e.target.value)}
            multiline
            rows={2}
            fullWidth
          />
          <Typography variant="caption" color="text.secondary">
            后台 BoundaryMonitoringJob 每分钟 tick 一次，按 cron 决定是否触发该任务。
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={submitting}>取消</Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={submitting || !name || !baselineId || !cronExpression}
        >
          {submitButtonLabel(submitting, isEdit)}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MonitoringJobEditor;
