/* eslint-disable i18next/no-literal-string -- Phase 8 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import {
  type AttackChainNodeEditValue,
  type ParameterSetOption,
} from './attackChainEditorTypes';

interface Props {
  open: boolean;
  initialValue: AttackChainNodeEditValue;
  parameterSetOptions: ParameterSetOption[];
  onCancel: () => void;
  onSubmit: (value: AttackChainNodeEditValue) => void;
}

const NodeEditDrawer = ({
  open,
  initialValue,
  parameterSetOptions,
  onCancel,
  onSubmit,
}: Props) => {
  const [form, setForm] = useState<AttackChainNodeEditValue>(initialValue);

  useEffect(() => {
    setForm(initialValue);
  }, [initialValue]);

  const isFormValid = useMemo(() => (
    form.node_title.trim().length > 0
    && form.repeat_count >= 1
    && form.repeat_interval_seconds >= 0
  ), [form]);

  const updateField = <K extends keyof AttackChainNodeEditValue>(
    key: K,
    value: AttackChainNodeEditValue[K],
  ) => {
    setForm(prev => ({
      ...prev,
      [key]: value,
    }));
  };

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="sm" fullWidth>
      <DialogTitle>节点设置</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="节点名称"
            value={form.node_title}
            onChange={e => updateField('node_title', e.target.value)}
            error={form.node_title.trim().length === 0}
            helperText={form.node_title.trim().length === 0 ? '名称必填' : ''}
            fullWidth
          />
          <TextField
            label="节点描述"
            value={form.node_description ?? ''}
            onChange={e => updateField('node_description', e.target.value)}
            multiline
            minRows={2}
            fullWidth
          />
          <FormControl fullWidth>
            <InputLabel id="node-param-set-label">参数集覆盖</InputLabel>
            <Select
              labelId="node-param-set-label"
              label="参数集覆盖"
              value={form.validation_parameter_set_id ?? ''}
              onChange={e =>
                updateField(
                  'validation_parameter_set_id',
                  e.target.value === '' ? null : (e.target.value as string),
                )}
            >
              <MenuItem value="">— 跟随链路默认 —</MenuItem>
              {parameterSetOptions.map(opt => (
                <MenuItem key={opt.id} value={opt.id}>
                  {opt.name}
                  {opt.is_template ? ' (模板)' : ''}
                </MenuItem>
              ))}
            </Select>
            <FormHelperText>仅本节点用本参数集；空 = 用链路默认</FormHelperText>
          </FormControl>
          <Stack direction="row" spacing={2}>
            <TextField
              label="重复次数"
              type="number"
              value={form.repeat_count}
              inputProps={{
                min: 1,
                step: 1,
              }}
              onChange={e =>
                updateField('repeat_count', Math.max(1, Number.parseInt(e.target.value || '1', 10)))}
              sx={{ flex: 1 }}
              helperText="≥ 1；> 1 时角标显示 ↻ x N"
            />
            <TextField
              label="重复间隔（秒）"
              type="number"
              value={form.repeat_interval_seconds}
              inputProps={{
                min: 0,
                step: 1,
              }}
              onChange={e =>
                updateField(
                  'repeat_interval_seconds',
                  Math.max(0, Number.parseInt(e.target.value || '0', 10)),
                )}
              sx={{ flex: 1 }}
              helperText="0 = 立即下次"
            />
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>取消</Button>
        <Button variant="contained" disabled={!isFormValid} onClick={() => onSubmit(form)}>
          保存
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default NodeEditDrawer;
