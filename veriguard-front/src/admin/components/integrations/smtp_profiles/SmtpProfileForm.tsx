/* eslint-disable i18next/no-literal-string -- B-ii PR-B 二开 UI 硬编码中文，follow-up 统一 i18n 清洗。 */
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import type { SmtpProfile, SmtpProfileInput } from '../../../../utils/api-types.d';

interface Props {
  open: boolean;
  mode: 'create' | 'edit';
  initial?: SmtpProfile;
  onCancel: () => void;
  onSubmit: (input: SmtpProfileInput) => void;
}

const DEFAULT_FORM: SmtpProfileInput = {
  smtp_profile_name: '',
  smtp_profile_host: '',
  smtp_profile_port: 587,
  smtp_profile_auth_type: 'none',
  smtp_profile_username: '',
  smtp_profile_password: '',
  smtp_profile_tls_mode: 'starttls',
  smtp_profile_default_from: '',
  smtp_profile_default_reply_to: '',
};

const toInput = (profile: SmtpProfile): SmtpProfileInput => ({
  smtp_profile_name: profile.smtp_profile_name,
  smtp_profile_host: profile.smtp_profile_host,
  smtp_profile_port: profile.smtp_profile_port,
  smtp_profile_auth_type: profile.smtp_profile_auth_type,
  smtp_profile_username: profile.smtp_profile_username ?? '',
  smtp_profile_password: profile.smtp_profile_password ?? '',
  smtp_profile_tls_mode: profile.smtp_profile_tls_mode,
  smtp_profile_default_from: profile.smtp_profile_default_from,
  smtp_profile_default_reply_to: profile.smtp_profile_default_reply_to ?? '',
});

const SmtpProfileForm = ({ open, mode, initial, onCancel, onSubmit }: Props) => {
  const [form, setForm] = useState<SmtpProfileInput>(
    initial ? toInput(initial) : { ...DEFAULT_FORM },
  );

  useEffect(() => {
    setForm(initial ? toInput(initial) : { ...DEFAULT_FORM });
  }, [initial, open]);

  const updateField = <K extends keyof SmtpProfileInput>(
    key: K,
    value: SmtpProfileInput[K],
  ) => {
    setForm(prev => ({ ...prev, [key]: value }));
  };

  const isValid = useMemo(() => {
    if (!form.smtp_profile_name.trim()) return false;
    if (!form.smtp_profile_host.trim()) return false;
    if (!form.smtp_profile_port || form.smtp_profile_port < 1) return false;
    if (!form.smtp_profile_default_from.trim()) return false;
    if (
      form.smtp_profile_auth_type === 'password'
      && !form.smtp_profile_username?.trim()
    ) {
      return false;
    }
    return true;
  }, [form]);

  const handleSubmit = () => {
    const payload: SmtpProfileInput = {
      ...form,
      smtp_profile_username:
        form.smtp_profile_auth_type === 'none'
          ? undefined
          : form.smtp_profile_username || undefined,
      smtp_profile_password:
        form.smtp_profile_auth_type === 'none'
          ? undefined
          : form.smtp_profile_password || undefined,
      smtp_profile_default_reply_to:
        form.smtp_profile_default_reply_to?.trim() || undefined,
    };
    onSubmit(payload);
  };

  const titleText = mode === 'create' ? '新建 SMTP 配置' : '编辑 SMTP 配置';
  const requiresAuth = form.smtp_profile_auth_type === 'password';

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="sm" fullWidth>
      <DialogTitle>{titleText}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          <TextField
            label="名称"
            value={form.smtp_profile_name}
            onChange={e => updateField('smtp_profile_name', e.target.value)}
            error={!form.smtp_profile_name.trim()}
            helperText={!form.smtp_profile_name.trim() ? '名称必填' : ''}
            fullWidth
          />
          <Stack direction="row" spacing={2}>
            <TextField
              label="主机 (Host)"
              value={form.smtp_profile_host}
              onChange={e => updateField('smtp_profile_host', e.target.value)}
              error={!form.smtp_profile_host.trim()}
              helperText={!form.smtp_profile_host.trim() ? 'Host 必填' : ''}
              sx={{ flex: 3 }}
            />
            <TextField
              label="端口 (Port)"
              type="number"
              value={form.smtp_profile_port}
              inputProps={{ min: 1, max: 65535, step: 1 }}
              onChange={e =>
                updateField(
                  'smtp_profile_port',
                  Math.max(1, Number.parseInt(e.target.value || '1', 10)),
                )}
              sx={{ flex: 1 }}
            />
          </Stack>
          <Stack direction="row" spacing={2}>
            <FormControl sx={{ flex: 1 }}>
              <InputLabel id="auth-type-label">认证方式</InputLabel>
              <Select
                labelId="auth-type-label"
                label="认证方式"
                value={form.smtp_profile_auth_type}
                onChange={e =>
                  updateField(
                    'smtp_profile_auth_type',
                    e.target.value as SmtpProfileInput['smtp_profile_auth_type'],
                  )}
              >
                <MenuItem value="none">无认证</MenuItem>
                <MenuItem value="password">用户名 / 密码</MenuItem>
              </Select>
            </FormControl>
            <FormControl sx={{ flex: 1 }}>
              <InputLabel id="tls-mode-label">TLS 模式</InputLabel>
              <Select
                labelId="tls-mode-label"
                label="TLS 模式"
                value={form.smtp_profile_tls_mode}
                onChange={e =>
                  updateField(
                    'smtp_profile_tls_mode',
                    e.target.value as SmtpProfileInput['smtp_profile_tls_mode'],
                  )}
              >
                <MenuItem value="none">无加密</MenuItem>
                <MenuItem value="starttls">STARTTLS</MenuItem>
                <MenuItem value="tls">TLS/SSL</MenuItem>
              </Select>
            </FormControl>
          </Stack>
          {requiresAuth && (
            <Stack direction="row" spacing={2}>
              <TextField
                label="用户名"
                value={form.smtp_profile_username ?? ''}
                onChange={e => updateField('smtp_profile_username', e.target.value)}
                error={!form.smtp_profile_username?.trim()}
                helperText={!form.smtp_profile_username?.trim() ? '用户名必填' : ''}
                sx={{ flex: 1 }}
              />
              <TextField
                label="密码"
                type="password"
                value={form.smtp_profile_password ?? ''}
                onChange={e => updateField('smtp_profile_password', e.target.value)}
                sx={{ flex: 1 }}
              />
            </Stack>
          )}
          <TextField
            label="发件人 (From)"
            value={form.smtp_profile_default_from}
            onChange={e => updateField('smtp_profile_default_from', e.target.value)}
            error={!form.smtp_profile_default_from.trim()}
            helperText={!form.smtp_profile_default_from.trim() ? '发件人必填' : ''}
            fullWidth
          />
          <TextField
            label="回复地址 (Reply-To)（可选）"
            value={form.smtp_profile_default_reply_to ?? ''}
            onChange={e => updateField('smtp_profile_default_reply_to', e.target.value)}
            fullWidth
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>取消</Button>
        <Button variant="contained" disabled={!isValid} onClick={handleSubmit}>
          保存
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SmtpProfileForm;
