/* eslint-disable i18next/no-literal-string -- Phase 8: 二开攻击编排 UI 用硬编码中文，与
   VeriguardConsole / SandboxDialog 现有约定一致；未来 Phase 12 (i18n 清洗) 统一迁移到 react-intl。 */
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import {
  type AttackChainSettingsValue,
  type ExecutionMode,
  type ParameterSetOption,
  type SocCorrelationRuleRef,
} from './attackChainEditorTypes';

interface Props {
  open: boolean;
  initialValue: AttackChainSettingsValue;
  parameterSetOptions: ParameterSetOption[];
  /** 已知可选 connector ID 列表（来自 /api/soc_connectors）；空时禁用"+ 新增 SOC 规则"按钮. */
  availableConnectorIds: string[];
  onCancel: () => void;
  onSubmit: (value: AttackChainSettingsValue) => void;
}

const EXECUTION_MODE_OPTIONS: {
  value: ExecutionMode;
  label: string;
  hint: string;
}[] = [
  {
    value: 'STOP_ON_BLOCK',
    label: '截停模式',
    hint: '任一节点 PREVENTION 被防御工具拦下 → 整链路立即终止',
  },
  {
    value: 'CONTINUE',
    label: '完整执行',
    hint: '即使被拦也跑完所有节点；适合收集完整数据',
  },
];

const DEFAULT_NEW_RULE_WINDOW_SECONDS = 7200;

const AttackChainSettingsDrawer = ({
  open,
  initialValue,
  parameterSetOptions,
  availableConnectorIds,
  onCancel,
  onSubmit,
}: Props) => {
  const [form, setForm] = useState<AttackChainSettingsValue>(initialValue);

  useEffect(() => {
    setForm(initialValue);
  }, [initialValue]);

  const isFormValid = useMemo(() => {
    const allRulesComplete = form.soc_correlation_rules.every(
      r => r.connector_id.trim() && r.rule_id.trim() && r.display_name.trim() && r.match_window_seconds > 0,
    );
    return allRulesComplete;
  }, [form]);

  const updateField = <K extends keyof AttackChainSettingsValue>(
    key: K,
    value: AttackChainSettingsValue[K],
  ) => {
    setForm(prev => ({
      ...prev,
      [key]: value,
    }));
  };

  const updateRule = (index: number, patch: Partial<SocCorrelationRuleRef>) => {
    setForm(prev => ({
      ...prev,
      soc_correlation_rules: prev.soc_correlation_rules.map((r, i) =>
        i === index
          ? {
              ...r,
              ...patch,
            }
          : r,
      ),
    }));
  };

  const addRule = () => {
    if (availableConnectorIds.length === 0) {
      return;
    }
    setForm(prev => ({
      ...prev,
      soc_correlation_rules: [
        ...prev.soc_correlation_rules,
        {
          connector_id: availableConnectorIds[0],
          rule_id: '',
          display_name: '',
          match_window_seconds: DEFAULT_NEW_RULE_WINDOW_SECONDS,
        },
      ],
    }));
  };

  const removeRule = (index: number) => {
    setForm(prev => ({
      ...prev,
      soc_correlation_rules: prev.soc_correlation_rules.filter((_, i) => i !== index),
    }));
  };

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="md" fullWidth>
      <DialogTitle>链路级设置</DialogTitle>
      <DialogContent>
        <Stack spacing={3} sx={{ pt: 1 }}>
          <Box>
            <Typography variant="subtitle2" gutterBottom>执行模式</Typography>
            <ToggleButtonGroup
              exclusive
              value={form.execution_mode}
              onChange={(_, v) => v && updateField('execution_mode', v)}
              size="small"
            >
              {EXECUTION_MODE_OPTIONS.map(opt => (
                <ToggleButton key={opt.value} value={opt.value}>
                  {opt.label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            <FormHelperText>
              {EXECUTION_MODE_OPTIONS.find(o => o.value === form.execution_mode)?.hint}
            </FormHelperText>
          </Box>

          <FormControl fullWidth>
            <InputLabel id="default-param-set-label">默认参数集</InputLabel>
            <Select
              labelId="default-param-set-label"
              label="默认参数集"
              value={form.validation_parameter_set_id ?? ''}
              onChange={e =>
                updateField(
                  'validation_parameter_set_id',
                  e.target.value === '' ? null : (e.target.value as string),
                )}
            >
              <MenuItem value="">— 不指定（节点可自行覆盖）—</MenuItem>
              {parameterSetOptions.map(opt => (
                <MenuItem key={opt.id} value={opt.id}>
                  {opt.name}
                  {opt.is_template ? ' (模板)' : ''}
                </MenuItem>
              ))}
            </Select>
            <FormHelperText>链路启动时把分数 / 期望值快照到节点 expectation</FormHelperText>
          </FormControl>

          <Box>
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
              <Typography variant="subtitle2">链路级 SOC 相关规则</Typography>
              <Button
                size="small"
                startIcon={<AddIcon />}
                onClick={addRule}
                disabled={availableConnectorIds.length === 0}
              >
                新增规则
              </Button>
            </Stack>
            {availableConnectorIds.length === 0 && (
              <FormHelperText sx={{ color: 'warning.main' }}>
                未注册任何 SOC connector；请先在 集成 → SOC 连接器 中启用对应实现
              </FormHelperText>
            )}
            {form.soc_correlation_rules.length === 0 && availableConnectorIds.length > 0 && (
              <Typography variant="body2" color="text.secondary">
                无链路级 correlation 规则。所有 DETECTION 维度仅按节点级 expectation 计算。
              </Typography>
            )}
            <Stack spacing={1.5}>
              {form.soc_correlation_rules.map((rule, idx) => (
                <Box
                  key={`rule-${idx}`}
                  sx={{
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                    p: 1.5,
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr 1fr 120px 40px',
                    gap: 1,
                    alignItems: 'center',
                  }}
                >
                  <FormControl size="small">
                    <InputLabel id={`connector-${idx}`}>Connector</InputLabel>
                    <Select
                      labelId={`connector-${idx}`}
                      label="Connector"
                      value={rule.connector_id}
                      onChange={e => updateRule(idx, { connector_id: e.target.value as string })}
                    >
                      {availableConnectorIds.map(id => (
                        <MenuItem key={id} value={id}>
                          {id}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField
                    size="small"
                    label="规则 ID"
                    value={rule.rule_id}
                    onChange={e => updateRule(idx, { rule_id: e.target.value })}
                  />
                  <TextField
                    size="small"
                    label="显示名"
                    value={rule.display_name}
                    onChange={e => updateRule(idx, { display_name: e.target.value })}
                  />
                  <TextField
                    size="small"
                    label="窗口（秒）"
                    type="number"
                    value={rule.match_window_seconds}
                    onChange={e =>
                      updateRule(idx, { match_window_seconds: Math.max(1, Number.parseInt(e.target.value || '0', 10)) })}
                  />
                  <IconButton
                    size="small"
                    onClick={() => removeRule(idx)}
                    aria-label={`移除规则 ${idx + 1}`}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Box>
              ))}
            </Stack>
            {form.soc_correlation_rules.length > 0 && (
              <Stack
                direction="row"
                spacing={0.5}
                sx={{
                  mt: 1,
                  flexWrap: 'wrap',
                }}
              >
                {form.soc_correlation_rules.map((r, i) => (
                  <Chip
                    key={`chip-${i}`}
                    label={`${r.connector_id} / ${r.display_name || r.rule_id}`}
                    size="small"
                    variant="outlined"
                  />
                ))}
              </Stack>
            )}
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>取消</Button>
        <Button variant="contained" onClick={() => onSubmit(form)} disabled={!isFormValid}>
          保存
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default AttackChainSettingsDrawer;
