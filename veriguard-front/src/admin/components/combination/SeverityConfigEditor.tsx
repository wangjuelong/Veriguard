/* eslint-disable i18next/no-literal-string -- IPv6 §3.6 ★2 攻击组合 UI 首发与既有
   VeriguardConsole / StabilityTrendView 一致使用硬编码中文；后续统一迁移 react-intl. */
import { Alert, Box, Button, LinearProgress, Paper, Stack, TextField, Typography } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import {
  fetchSeverityConfig,
  recomputeSeverity,
  type SeverityConfigOutput,
  type SeverityConfigUpdateRequest,
  updateSeverityConfig,
} from '../../../actions/combination/combination-actions';

const WEIGHT_SUM_TOLERANCE = 0.001;

type Props = { runId?: string };

type FormState = {
  miss_count_weight: number;
  attack_type_weight: number;
  asset_sensitivity_weight: number;
  critical_threshold: number;
  high_threshold: number;
  medium_threshold: number;
  critical_label: string;
  high_label: string;
  medium_label: string;
  info_label: string;
  critical_color: string;
  high_color: string;
  medium_color: string;
  info_color: string;
};

const toForm = (cfg: SeverityConfigOutput): FormState => ({
  miss_count_weight: Number(cfg.severity_config_miss_count_weight),
  attack_type_weight: Number(cfg.severity_config_attack_type_weight),
  asset_sensitivity_weight: Number(cfg.severity_config_asset_sensitivity_weight),
  critical_threshold: Number(cfg.severity_config_critical_threshold),
  high_threshold: Number(cfg.severity_config_high_threshold),
  medium_threshold: Number(cfg.severity_config_medium_threshold),
  critical_label: cfg.severity_config_critical_label,
  high_label: cfg.severity_config_high_label,
  medium_label: cfg.severity_config_medium_label,
  info_label: cfg.severity_config_info_label,
  critical_color: cfg.severity_config_critical_color,
  high_color: cfg.severity_config_high_color,
  medium_color: cfg.severity_config_medium_color,
  info_color: cfg.severity_config_info_color,
});

const SeverityConfigEditor = ({ runId }: Props) => {
  const [form, setForm] = useState<FormState | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const [recomputing, setRecomputing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const cfg = await fetchSeverityConfig();
        if (!cancelled) {
          setForm(toForm(cfg));
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '加载分级配置失败');
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
  }, []);

  const weightSum = useMemo(() => {
    if (!form) {
      return 0;
    }
    return form.miss_count_weight + form.attack_type_weight + form.asset_sensitivity_weight;
  }, [form]);

  const weightsValid = Math.abs(weightSum - 1) <= WEIGHT_SUM_TOLERANCE;

  const thresholdsValid = useMemo(() => {
    if (!form) {
      return false;
    }
    return form.critical_threshold > form.high_threshold
      && form.high_threshold > form.medium_threshold
      && form.medium_threshold > 0;
  }, [form]);

  const isValid = weightsValid && thresholdsValid;

  const updateField = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm(prev => (prev
      ? {
          ...prev,
          [key]: value,
        }
      : prev));
  };

  const handleSave = async () => {
    if (!form || !isValid) {
      return;
    }
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const req: SeverityConfigUpdateRequest = { ...form };
      const cfg = await updateSeverityConfig(req);
      setForm(toForm(cfg));
      setNotice('保存成功');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleRecompute = async () => {
    if (!runId) {
      return;
    }
    setRecomputing(true);
    setError(null);
    setNotice(null);
    try {
      const result = await recomputeSeverity(runId);
      setNotice(`重算已触发：${result.severity_recompute_status} (job=${result.severity_recompute_job_id})`);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '触发重算失败');
    } finally {
      setRecomputing(false);
    }
  };

  if (loading) {
    return <LinearProgress />;
  }
  if (!form) {
    return <Alert severity="error">{error ?? '配置加载失败'}</Alert>;
  }

  return (
    <Stack spacing={2}>
      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" gutterBottom>
          权重（三项之和必须 = 1.0 ±
          {WEIGHT_SUM_TOLERANCE}
          ）
        </Typography>
        <Stack direction="row" spacing={2}>
          <TextField
            label="miss 数量权重"
            type="number"
            value={form.miss_count_weight}
            onChange={e => updateField('miss_count_weight', Number(e.target.value))}
            inputProps={{
              step: 0.05,
              min: 0,
              max: 1,
            }}
          />
          <TextField
            label="攻击类型权重"
            type="number"
            value={form.attack_type_weight}
            onChange={e => updateField('attack_type_weight', Number(e.target.value))}
            inputProps={{
              step: 0.05,
              min: 0,
              max: 1,
            }}
          />
          <TextField
            label="资产敏感度权重"
            type="number"
            value={form.asset_sensitivity_weight}
            onChange={e => updateField('asset_sensitivity_weight', Number(e.target.value))}
            inputProps={{
              step: 0.05,
              min: 0,
              max: 1,
            }}
          />
        </Stack>
        <Typography
          variant="caption"
          color={weightsValid ? 'text.secondary' : 'error'}
          sx={{
            mt: 1,
            display: 'block',
          }}
        >
          权重和：
          {weightSum.toFixed(3)}
          {weightsValid ? ' ✓' : ` ✗ (期望 1.000 ± ${WEIGHT_SUM_TOLERANCE})`}
        </Typography>
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" gutterBottom>阈值（必须严格递减：critical &gt; high &gt; medium &gt; 0）</Typography>
        <Stack direction="row" spacing={2}>
          <TextField
            label="critical 阈值"
            type="number"
            value={form.critical_threshold}
            onChange={e => updateField('critical_threshold', Number(e.target.value))}
            inputProps={{
              step: 0.01,
              min: 0,
              max: 1,
            }}
          />
          <TextField
            label="high 阈值"
            type="number"
            value={form.high_threshold}
            onChange={e => updateField('high_threshold', Number(e.target.value))}
            inputProps={{
              step: 0.01,
              min: 0,
              max: 1,
            }}
          />
          <TextField
            label="medium 阈值"
            type="number"
            value={form.medium_threshold}
            onChange={e => updateField('medium_threshold', Number(e.target.value))}
            inputProps={{
              step: 0.01,
              min: 0,
              max: 1,
            }}
          />
        </Stack>
        {!thresholdsValid && (
          <Typography
            variant="caption"
            color="error"
            sx={{
              mt: 1,
              display: 'block',
            }}
          >
            阈值未严格递减。
          </Typography>
        )}
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" gutterBottom>标签与颜色</Typography>
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
          gap: 2,
        }}
        >
          {(['critical', 'high', 'medium', 'info'] as const).map((level) => {
            const labelKey = `${level}_label` as keyof FormState;
            const colorKey = `${level}_color` as keyof FormState;
            return (
              <Box key={level}>
                <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase' }}>
                  {level}
                </Typography>
                <TextField
                  label="标签"
                  size="small"
                  fullWidth
                  value={form[labelKey] as string}
                  onChange={e => updateField(labelKey, e.target.value as never)}
                  sx={{ mt: 0.5 }}
                />
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
                  <input
                    type="color"
                    value={form[colorKey] as string}
                    onChange={e => updateField(colorKey, e.target.value as never)}
                    style={{
                      width: 40,
                      height: 32,
                    }}
                  />
                  <TextField
                    label="颜色"
                    size="small"
                    fullWidth
                    value={form[colorKey] as string}
                    onChange={e => updateField(colorKey, e.target.value as never)}
                  />
                </Stack>
              </Box>
            );
          })}
        </Box>
      </Paper>

      {error && <Alert severity="error">{error}</Alert>}
      {notice && <Alert severity="success">{notice}</Alert>}

      <Stack direction="row" spacing={2}>
        <Button
          variant="contained"
          onClick={() => { void handleSave(); }}
          disabled={!isValid || saving}
        >
          {saving ? '保存中…' : '保存配置'}
        </Button>
        {runId && (
          <Button
            variant="outlined"
            onClick={() => { void handleRecompute(); }}
            disabled={recomputing}
          >
            {recomputing ? '正在触发…' : '重新评分当前任务'}
          </Button>
        )}
      </Stack>
    </Stack>
  );
};

export default SeverityConfigEditor;
