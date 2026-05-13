/* eslint-disable i18next/no-literal-string -- IPv6 PR C3 边界覆盖度 UI 与既有
   CombinationsList / StabilityTrendView 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem,
  Select, Stack, TextField, Typography,
} from '@mui/material';
import { useState } from 'react';

import {
  type CoverageBaselineOutput,
  type CoverageType,
  createCoverageBaseline,
} from '../../../actions/coverage/coverage-actions';

interface CoverageBaselineEditorProps {
  open: boolean;
  onCancel: () => void;
  onCreated: (baseline: CoverageBaselineOutput) => void;
}

const COVERAGE_TYPES: ReadonlyArray<{
  value: CoverageType;
  label: string;
}> = [
  {
    value: 'boundary',
    label: '边界资产覆盖度（§3.1）',
  },
  {
    value: 'traffic',
    label: '流量边界覆盖度（§4.1）',
  },
];

const CoverageBaselineEditor = ({ open, onCancel, onCreated }: CoverageBaselineEditorProps) => {
  const [name, setName] = useState<string>('');
  const [coverageType, setCoverageType] = useState<CoverageType>('boundary');
  const [caseIdsRaw, setCaseIdsRaw] = useState<string>('');
  const [assetGroupId, setAssetGroupId] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [socDelay, setSocDelay] = useState<number>(60);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setName('');
    setCoverageType('boundary');
    setCaseIdsRaw('');
    setAssetGroupId('');
    setDescription('');
    setSocDelay(60);
    setError(null);
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const caseIds = caseIdsRaw
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0);
      const created = await createCoverageBaseline({
        name,
        coverage_type: coverageType,
        case_ids: caseIds,
        asset_group_id: assetGroupId,
        description,
        soc_query_delay_seconds: socDelay,
      });
      reset();
      onCreated(created);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '创建基线失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={onCancel} fullWidth maxWidth="sm">
      <DialogTitle>新建覆盖度基线</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="基线名称"
            value={name}
            onChange={e => setName(e.target.value)}
            required
            fullWidth
          />
          <Select
            value={coverageType}
            onChange={e => setCoverageType(e.target.value as CoverageType)}
            fullWidth
          >
            {COVERAGE_TYPES.map(t => (
              <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>
            ))}
          </Select>
          <TextField
            label="资产组 ID"
            value={assetGroupId}
            onChange={e => setAssetGroupId(e.target.value)}
            required
            fullWidth
            helperText="基线所覆盖的资产组（可在「资产管理」获取 ID）"
          />
          <TextField
            label="用例 ID 列表（逗号分隔）"
            value={caseIdsRaw}
            onChange={e => setCaseIdsRaw(e.target.value)}
            fullWidth
            helperText="例如：case-A, case-B, case-C"
          />
          <TextField
            label="SOC 等待时间（秒）"
            type="number"
            value={socDelay}
            onChange={e => setSocDelay(Number(e.target.value))}
            fullWidth
            inputProps={{
              min: 0,
              max: 600,
            }}
            helperText="inject 完成后等待 SOC 上送告警的窗口（默认 60s）"
          />
          <TextField
            label="描述（可选）"
            value={description}
            onChange={e => setDescription(e.target.value)}
            multiline
            rows={2}
            fullWidth
          />
          <Typography variant="caption" color="text.secondary">
            提交后会立即生成基线，可在详情页点击「触发评估」启动一次覆盖度计算。
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={submitting}>取消</Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={submitting || !name || !assetGroupId}
        >
          {submitting ? '提交中…' : '创建'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default CoverageBaselineEditor;
