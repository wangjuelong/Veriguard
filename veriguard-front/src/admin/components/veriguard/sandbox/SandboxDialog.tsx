import {
  Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControl, FormControlLabel, InputLabel, MenuItem, Select, Stack, Switch,
  TextField, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import type {
  SandboxInput, SandboxNetworkPolicy, SandboxSampleType, SandboxStatus,
} from '../../../../actions/veriguard/veriguard-actions';
import NetworkRuleEditor from './NetworkRuleEditor';

const NETWORK_POLICIES: SandboxNetworkPolicy[] = ['DENY_ALL', 'ALLOWLIST', 'ISOLATED_LAB', 'CUSTOM'];
const SAMPLE_TYPES: SandboxSampleType[] = [
  'RANSOMWARE', 'MINER', 'WORM', 'MALICIOUS_DRIVER',
  'PRIVILEGE_ESCALATION', 'ACCOUNT_THEFT', 'PROXY_EXECUTION', 'SECURITY_COMPONENT_BYPASS',
];
const SAMPLE_TYPE_LABELS: Record<SandboxSampleType, string> = {
  RANSOMWARE: '勒索病毒样本执行',
  MINER: '挖矿病毒样本执行',
  WORM: '蠕虫病毒样本执行',
  MALICIOUS_DRIVER: '终端恶意驱动加载',
  PRIVILEGE_ESCALATION: '终端权限提升',
  ACCOUNT_THEFT: '终端系统账号窃取',
  PROXY_EXECUTION: '终端代理执行',
  SECURITY_COMPONENT_BYPASS: '终端安全组件对抗',
};
const STATUS_TYPES: SandboxStatus[] = ['ACTIVE', 'INACTIVE'];

type Props = {
  open: boolean;
  mode: 'create' | 'edit';
  initialValue: SandboxInput;
  onCancel: () => void;
  onSubmit: (value: SandboxInput) => void;
};

const SandboxDialog = ({ open, mode, initialValue, onCancel, onSubmit }: Props) => {
  const [form, setForm] = useState<SandboxInput>(initialValue);
  useEffect(() => { setForm(initialValue); }, [initialValue]);

  const formValid = useMemo(() => (
    form.sandbox_name.trim().length > 0
      && form.sandbox_supported_sample_types.length > 0
      && form.sandbox_auto_restore_enabled
  ), [form]);

  const updateField = <K extends keyof SandboxInput>(key: K, value: SandboxInput[K]) => {
    setForm(prev => ({ ...prev, [key]: value }));
  };
  const toggleSampleType = (st: SandboxSampleType) => {
    const exists = form.sandbox_supported_sample_types.includes(st);
    updateField('sandbox_supported_sample_types',
      exists ? form.sandbox_supported_sample_types.filter(s => s !== st)
             : [...form.sandbox_supported_sample_types, st]);
  };

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="md" fullWidth>
      <DialogTitle>{mode === 'create' ? '新建沙箱预设' : '编辑沙箱预设'}</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>
          网络访问控制策略与自动还原快照由沙箱平台主机管理员负责实际生效。本系统仅持久化策略并提供导出脚本，对快照配置缺失给出可视化告警；不参与运行时下发与执行。
        </Alert>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, pt: 1 }}>
          <TextField label="名称" value={form.sandbox_name}
            onChange={e => updateField('sandbox_name', e.target.value)} required />
          <FormControl>
            <InputLabel>网络策略</InputLabel>
            <Select label="网络策略" value={form.sandbox_network_policy}
              onChange={e => updateField('sandbox_network_policy', e.target.value as SandboxNetworkPolicy)}>
              {NETWORK_POLICIES.map(p => <MenuItem key={p} value={p}>{p}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField label="描述" value={form.sandbox_description ?? ''}
            onChange={e => updateField('sandbox_description', e.target.value)}
            multiline minRows={2} sx={{ gridColumn: '1 / -1' }} />
          <Box sx={{ gridColumn: '1 / -1' }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>网络访问控制规则（可选）</Typography>
            <NetworkRuleEditor value={form.sandbox_network_rules}
              onChange={v => updateField('sandbox_network_rules', v)} />
          </Box>
          <FormControlLabel
            control={(
              <Switch checked={form.sandbox_auto_restore_enabled}
                onChange={e => updateField('sandbox_auto_restore_enabled', e.target.checked)} />
            )}
            label="执行完成后自动还原" />
          <FormControl>
            <InputLabel>状态</InputLabel>
            <Select label="状态" value={form.sandbox_status}
              onChange={e => updateField('sandbox_status', e.target.value as SandboxStatus)}>
              {STATUS_TYPES.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </Select>
          </FormControl>
          <Box sx={{ gridColumn: '1 / -1' }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>样本类型</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {SAMPLE_TYPES.map(st => (
                <FormControlLabel key={st}
                  control={(
                    <Checkbox checked={form.sandbox_supported_sample_types.includes(st)}
                      onChange={() => toggleSampleType(st)} />
                  )}
                  label={SAMPLE_TYPE_LABELS[st]} />
              ))}
            </Stack>
          </Box>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>取消</Button>
        <Button variant="contained" disabled={!formValid} onClick={() => onSubmit(form)}>保存</Button>
      </DialogActions>
    </Dialog>
  );
};

export default SandboxDialog;
