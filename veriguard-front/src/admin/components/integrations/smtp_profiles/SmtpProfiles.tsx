/* eslint-disable i18next/no-literal-string -- B-ii PR-B 二开 UI 硬编码中文，follow-up 统一 i18n 清洗。 */
import { Add as AddIcon, Delete as DeleteIcon, Edit as EditIcon } from '@mui/icons-material';
import {
  Box,
  Button,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { useCallback, useEffect, useState } from 'react';

import {
  createSmtpProfile,
  deleteSmtpProfile,
  fetchSmtpProfiles,
  updateSmtpProfile,
} from '../../../../actions/smtp_profiles/smtp_profile-action';
import type { SmtpProfile, SmtpProfileInput } from '../../../../utils/api-types.d';
import SmtpProfileForm from './SmtpProfileForm';

const AUTH_TYPE_LABEL: Record<SmtpProfile['smtp_profile_auth_type'], string> = {
  none: '无认证',
  password: '用户名/密码',
};

const TLS_MODE_LABEL: Record<SmtpProfile['smtp_profile_tls_mode'], string> = {
  none: '无加密',
  starttls: 'STARTTLS',
  tls: 'TLS/SSL',
};

const SmtpProfiles = () => {
  const [profiles, setProfiles] = useState<SmtpProfile[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<'create' | 'edit'>('create');
  const [editTarget, setEditTarget] = useState<SmtpProfile | undefined>(undefined);

  const loadProfiles = useCallback(async () => {
    const response = await fetchSmtpProfiles();
    setProfiles(response.data ?? []);
  }, []);

  useEffect(() => {
    loadProfiles();
  }, [loadProfiles]);

  const handleOpenCreate = () => {
    setFormMode('create');
    setEditTarget(undefined);
    setFormOpen(true);
  };

  const handleOpenEdit = (profile: SmtpProfile) => {
    setFormMode('edit');
    setEditTarget(profile);
    setFormOpen(true);
  };

  const handleCancel = () => {
    setFormOpen(false);
    setEditTarget(undefined);
  };

  const handleSubmit = async (input: SmtpProfileInput) => {
    if (formMode === 'create') {
      await createSmtpProfile(input);
    } else if (editTarget) {
      await updateSmtpProfile(editTarget.smtp_profile_id, input);
    }
    setFormOpen(false);
    setEditTarget(undefined);
    await loadProfiles();
  };

  const handleDelete = async (profile: SmtpProfile) => {
    await deleteSmtpProfile(profile.smtp_profile_id);
    await loadProfiles();
  };

  return (
    <Box>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: 2,
        }}
      >
        <Typography variant="h4">SMTP 配置</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenCreate}>
          新建
        </Button>
      </Box>

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>名称</TableCell>
              <TableCell>主机</TableCell>
              <TableCell>端口</TableCell>
              <TableCell>认证方式</TableCell>
              <TableCell>TLS 模式</TableCell>
              <TableCell>发件人</TableCell>
              <TableCell align="right">操作</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {profiles.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                    暂无 SMTP 配置，点击「新建」添加。
                  </Typography>
                </TableCell>
              </TableRow>
            )}
            {profiles.map(profile => (
              <TableRow key={profile.smtp_profile_id} hover>
                <TableCell>{profile.smtp_profile_name}</TableCell>
                <TableCell>{profile.smtp_profile_host}</TableCell>
                <TableCell>{profile.smtp_profile_port}</TableCell>
                <TableCell>{AUTH_TYPE_LABEL[profile.smtp_profile_auth_type]}</TableCell>
                <TableCell>{TLS_MODE_LABEL[profile.smtp_profile_tls_mode]}</TableCell>
                <TableCell>{profile.smtp_profile_default_from}</TableCell>
                <TableCell align="right">
                  <Tooltip title="编辑">
                    <IconButton
                      size="small"
                      onClick={() => handleOpenEdit(profile)}
                      aria-label={`编辑 ${profile.smtp_profile_name}`}
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="删除">
                    <IconButton
                      size="small"
                      onClick={() => handleDelete(profile)}
                      aria-label={`删除 ${profile.smtp_profile_name}`}
                    >
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <SmtpProfileForm
        open={formOpen}
        mode={formMode}
        initial={editTarget}
        onCancel={handleCancel}
        onSubmit={handleSubmit}
      />
    </Box>
  );
};

export default SmtpProfiles;
