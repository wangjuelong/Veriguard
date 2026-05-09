/* eslint-disable i18next/no-literal-string -- Phase 10 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Lock as LockIcon,
} from '@mui/icons-material';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import {
  type SocCorrelationRuleRef,
  type ValidationParameterSetValue,
} from './validationParameterSetTypes';

interface Props {
  open: boolean;
  /** 'create' 新建 / 'edit' 编辑 / 'duplicate' 模板复制后改 */
  mode: 'create' | 'edit' | 'duplicate';
  initialValue: ValidationParameterSetValue;
  /** 已知可选 connector ID（来自 /api/soc_connectors） */
  availableConnectorIds: string[];
  onCancel: () => void;
  onSubmit: (value: ValidationParameterSetValue) => void;
}

const DEFAULT_NEW_RULE_WINDOW_SECONDS = 7200;

/**
 * ParameterSet 创建 / 编辑对话框（spec §6.3.5）.
 *
 * - 普通参数集：所有字段可改
 * - 模板参数集（is_template = true）：所有字段只读，按钮变为 "复制并修改" → 调用方应在
 *   onSubmit 收到 mode='duplicate' 时把 is_template=false 落库为新条目（spec §6.3.5
 *   "is_template = true 锁图标，编辑只能复制后修改"）
 */
const ParameterSetEditDialog = ({
  open,
  mode,
  initialValue,
  availableConnectorIds,
  onCancel,
  onSubmit,
}: Props) => {
  const [form, setForm] = useState<ValidationParameterSetValue>(initialValue);

  useEffect(() => {
    setForm(initialValue);
  }, [initialValue]);

  const isTemplateLocked = mode === 'edit' && initialValue.is_template;

  const isFormValid = useMemo(() => {
    if (form.name.trim().length === 0) {
      return false;
    }
    if (
      form.prevention_expected_score < 0
      || form.prevention_expiration_seconds <= 0
      || form.detection_expected_score < 0
      || form.detection_expiration_seconds <= 0
    ) {
      return false;
    }
    return form.soc_correlation_rules.every(
      r => r.connector_id.trim()
        && r.rule_id.trim()
        && r.display_name.trim()
        && r.match_window_seconds > 0,
    );
  }, [form]);

  const updateField = <K extends keyof ValidationParameterSetValue>(
    key: K,
    value: ValidationParameterSetValue[K],
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

  const removeTarget = (index: number) => {
    setForm(prev => ({
      ...prev,
      default_targets: prev.default_targets.filter((_, i) => i !== index),
    }));
  };

  const removeTag = (index: number) => {
    setForm(prev => ({
      ...prev,
      tags: prev.tags.filter((_, i) => i !== index),
    }));
  };

  const handleSubmit = () => {
    if (mode === 'duplicate') {
      // 复制模板：强制 is_template=false 落为普通参数集
      onSubmit({
        ...form,
        is_template: false,
      });
      return;
    }
    onSubmit(form);
  };

  const titleText = (() => {
    switch (mode) {
      case 'create':
        return '新建参数集';
      case 'edit':
        return isTemplateLocked ? '查看模板参数集' : '编辑参数集';
      case 'duplicate':
        return '复制模板并修改';
      default:
        return '参数集';
    }
  })();

  const submitText = mode === 'duplicate' ? '复制并保存' : '保存';

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="md" fullWidth>
      <DialogTitle>
        <Stack direction="row" spacing={1} alignItems="center">
          {isTemplateLocked && (
            <LockIcon
              fontSize="small"
              color="action"
              data-testid="param-set-template-lock"
            />
          )}
          <span>{titleText}</span>
        </Stack>
      </DialogTitle>
      <DialogContent>
        {isTemplateLocked && (
          <Alert severity="info" sx={{ mb: 2 }}>
            模板参数集只读。如需修改，请用「复制并修改」创建一个普通参数集。
          </Alert>
        )}
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          <TextField
            label="名称"
            value={form.name}
            onChange={e => updateField('name', e.target.value)}
            error={form.name.trim().length === 0}
            helperText={form.name.trim().length === 0 ? '名称必填' : ''}
            disabled={isTemplateLocked}
            fullWidth
          />
          <TextField
            label="描述"
            value={form.description ?? ''}
            onChange={e => updateField('description', e.target.value)}
            disabled={isTemplateLocked}
            multiline
            minRows={2}
            fullWidth
          />

          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary">PREVENTION</Typography>
          </Divider>
          <Stack direction="row" spacing={2}>
            <TextField
              label="期望分数"
              type="number"
              value={form.prevention_expected_score}
              inputProps={{
                min: 0,
                step: 1,
              }}
              onChange={e =>
                updateField(
                  'prevention_expected_score',
                  Math.max(0, Number.parseInt(e.target.value || '0', 10)),
                )}
              disabled={isTemplateLocked}
              sx={{ flex: 1 }}
            />
            <TextField
              label="超时（秒）"
              type="number"
              value={form.prevention_expiration_seconds}
              inputProps={{
                min: 1,
                step: 1,
              }}
              onChange={e =>
                updateField(
                  'prevention_expiration_seconds',
                  Math.max(1, Number.parseInt(e.target.value || '1', 10)),
                )}
              disabled={isTemplateLocked}
              sx={{ flex: 1 }}
            />
          </Stack>

          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary">DETECTION</Typography>
          </Divider>
          <Stack direction="row" spacing={2}>
            <TextField
              label="期望分数"
              type="number"
              value={form.detection_expected_score}
              inputProps={{
                min: 0,
                step: 1,
              }}
              onChange={e =>
                updateField(
                  'detection_expected_score',
                  Math.max(0, Number.parseInt(e.target.value || '0', 10)),
                )}
              disabled={isTemplateLocked}
              sx={{ flex: 1 }}
            />
            <TextField
              label="超时（秒）"
              type="number"
              value={form.detection_expiration_seconds}
              inputProps={{
                min: 1,
                step: 1,
              }}
              onChange={e =>
                updateField(
                  'detection_expiration_seconds',
                  Math.max(1, Number.parseInt(e.target.value || '1', 10)),
                )}
              disabled={isTemplateLocked}
              sx={{ flex: 1 }}
            />
          </Stack>

          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary">默认验证目标</Typography>
          </Divider>
          {form.default_targets.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              未设置默认目标。链路启动时使用节点上配置的 assets。
            </Typography>
          ) : (
            <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
              {form.default_targets.map((target, idx) => (
                <Chip
                  key={`${target.kind}-${target.id}`}
                  data-testid={`param-set-target-${idx}`}
                  label={`${target.kind}: ${target.display_name ?? target.id}`}
                  size="small"
                  onDelete={isTemplateLocked ? undefined : () => removeTarget(idx)}
                />
              ))}
            </Stack>
          )}

          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary">默认 SOC Correlation Rules</Typography>
          </Divider>
          <Stack spacing={1.5}>
            {form.soc_correlation_rules.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                未设置默认 SOC 规则。
              </Typography>
            )}
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
                <FormControl size="small" disabled={isTemplateLocked}>
                  <InputLabel id={`ps-connector-${idx}`}>Connector</InputLabel>
                  <Select
                    labelId={`ps-connector-${idx}`}
                    label="Connector"
                    value={rule.connector_id}
                    onChange={e => updateRule(idx, { connector_id: e.target.value as string })}
                  >
                    {availableConnectorIds.map(id => (
                      <MenuItem key={id} value={id}>{id}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField
                  size="small"
                  label="规则 ID"
                  value={rule.rule_id}
                  onChange={e => updateRule(idx, { rule_id: e.target.value })}
                  disabled={isTemplateLocked}
                />
                <TextField
                  size="small"
                  label="显示名"
                  value={rule.display_name}
                  onChange={e => updateRule(idx, { display_name: e.target.value })}
                  disabled={isTemplateLocked}
                />
                <TextField
                  size="small"
                  label="窗口（秒）"
                  type="number"
                  value={rule.match_window_seconds}
                  onChange={e =>
                    updateRule(idx, { match_window_seconds: Math.max(1, Number.parseInt(e.target.value || '0', 10)) })}
                  disabled={isTemplateLocked}
                />
                <IconButton
                  size="small"
                  onClick={() => removeRule(idx)}
                  disabled={isTemplateLocked}
                  aria-label={`移除规则 ${idx + 1}`}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Box>
            ))}
            <Button
              size="small"
              startIcon={<AddIcon />}
              onClick={addRule}
              disabled={isTemplateLocked || availableConnectorIds.length === 0}
            >
              新增规则
            </Button>
          </Stack>

          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary">标签</Typography>
          </Divider>
          {form.tags.length === 0 ? (
            <Typography variant="body2" color="text.secondary">无标签。</Typography>
          ) : (
            <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
              {form.tags.map((tag, idx) => (
                <Chip
                  key={tag.id}
                  data-testid={`param-set-tag-${idx}`}
                  label={tag.name}
                  size="small"
                  onDelete={isTemplateLocked ? undefined : () => removeTag(idx)}
                />
              ))}
            </Stack>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>取消</Button>
        {!isTemplateLocked && (
          <Button
            variant="contained"
            disabled={!isFormValid}
            onClick={handleSubmit}
          >
            {submitText}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default ParameterSetEditDialog;
