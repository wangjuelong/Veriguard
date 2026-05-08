/* eslint-disable i18next/no-literal-string -- spec §6.7: M1 sandbox UI uses
   hardcoded Chinese to match existing VeriguardConsole.tsx pattern; future
   M-x will migrate to react-intl when sandbox UI stabilizes. */
import { AddOutlined, MoreVertOutlined } from '@mui/icons-material';
import {
  Button, Chip, FormControl, IconButton, InputLabel, Menu, MenuItem,
  Paper, Select, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import {
  createVeriguardSandbox, deleteVeriguardSandbox, exportSandboxIptables,
  exportSandboxRoutingConf, fetchVeriguardSandboxes, type SandboxInput, type SandboxOutput, type SandboxSampleType, type SandboxStatus,
  updateVeriguardSandbox,
} from '../../../../actions/veriguard/veriguard-actions';
import DeleteConfirmDialog from './DeleteConfirmDialog';
import SandboxDialog from './SandboxDialog';

const STATUS_TYPES: SandboxStatus[] = ['ACTIVE', 'INACTIVE'];
const SAMPLE_TYPES: SandboxSampleType[] = [
  'RANSOMWARE', 'MINER', 'WORM', 'MALICIOUS_DRIVER',
  'PRIVILEGE_ESCALATION', 'ACCOUNT_THEFT', 'PROXY_EXECUTION', 'SECURITY_COMPONENT_BYPASS',
];

const downloadText = (filename: string, content: string) => {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
};

const emptyInput: SandboxInput = {
  sandbox_name: '',
  sandbox_description: '',
  sandbox_network_policy: 'DENY_ALL',
  sandbox_network_rules: [],
  sandbox_auto_restore_enabled: true,
  sandbox_supported_sample_types: ['RANSOMWARE'],
  sandbox_status: 'ACTIVE',
};

const SandboxList = () => {
  const [sandboxes, setSandboxes] = useState<SandboxOutput[]>([]);
  const [statusFilter, setStatusFilter] = useState<SandboxStatus | 'ALL'>('ALL');
  const [sampleFilter, setSampleFilter] = useState<SandboxSampleType | 'ALL'>('ALL');
  const [dialogState, setDialogState] = useState<{
    open: boolean;
    mode: 'create' | 'edit';
    id?: string;
    value: SandboxInput;
  }>({
    open: false,
    mode: 'create',
    value: emptyInput,
  });
  const [deleteState, setDeleteState] = useState<{
    open: boolean;
    id?: string;
    name?: string;
  }>({ open: false });
  const [menuAnchor, setMenuAnchor] = useState<{
    el: HTMLElement;
    sandbox: SandboxOutput;
  } | null>(null);

  const reload = async () => setSandboxes(await fetchVeriguardSandboxes());
  useEffect(() => {
    void reload();
  }, []);

  const filtered = useMemo(() => sandboxes.filter(s => (
    (statusFilter === 'ALL' || s.sandbox_status === statusFilter)
    && (sampleFilter === 'ALL' || s.sandbox_supported_sample_types.includes(sampleFilter))
  )), [sandboxes, statusFilter, sampleFilter]);

  const onSubmit = async (value: SandboxInput) => {
    if (dialogState.mode === 'edit' && dialogState.id) {
      await updateVeriguardSandbox(dialogState.id, value);
    } else {
      await createVeriguardSandbox(value);
    }
    setDialogState(s => ({
      ...s,
      open: false,
    }));
    await reload();
  };

  const onDelete = async () => {
    if (deleteState.id) {
      await deleteVeriguardSandbox(deleteState.id);
      setDeleteState({ open: false });
      await reload();
    }
  };

  const onExportIptables = async (id: string) => {
    const { filename, content } = await exportSandboxIptables(id);
    downloadText(filename, content);
  };
  const onExportRoutingConf = async (id: string) => {
    const { filename, content } = await exportSandboxRoutingConf(id);
    downloadText(filename, content);
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6">沙箱预设</Typography>
        <Stack direction="row" gap={1}>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>状态</InputLabel>
            <Select
              label="状态"
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value as SandboxStatus | 'ALL')}
            >
              <MenuItem value="ALL">全部</MenuItem>
              {STATUS_TYPES.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel>样本类型</InputLabel>
            <Select
              label="样本类型"
              value={sampleFilter}
              onChange={e => setSampleFilter(e.target.value as SandboxSampleType | 'ALL')}
            >
              <MenuItem value="ALL">全部</MenuItem>
              {SAMPLE_TYPES.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
            </Select>
          </FormControl>
          <Button
            startIcon={<AddOutlined />}
            variant="contained"
            onClick={() => setDialogState({
              open: true,
              mode: 'create',
              value: emptyInput,
            })}
          >
            新建预设
          </Button>
        </Stack>
      </Stack>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>名称</TableCell>
            <TableCell>规则数</TableCell>
            <TableCell>样本数</TableCell>
            <TableCell>自动还原</TableCell>
            <TableCell>状态</TableCell>
            <TableCell align="right">操作</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {filtered.map(s => (
            <TableRow key={s.sandbox_id}>
              <TableCell>{s.sandbox_name}</TableCell>
              <TableCell>{s.sandbox_network_rules.length}</TableCell>
              <TableCell>{s.sandbox_supported_sample_types.length}</TableCell>
              <TableCell>
                <Chip
                  size="small"
                  color={s.sandbox_auto_restore_enabled ? 'success' : 'error'}
                  label={s.sandbox_auto_restore_enabled ? '已开启' : '未开启'}
                />
              </TableCell>
              <TableCell>{s.sandbox_status}</TableCell>
              <TableCell align="right">
                <IconButton
                  aria-label={`沙箱「${s.sandbox_name}」操作`}
                  onClick={e => setMenuAnchor({
                    el: e.currentTarget,
                    sandbox: s,
                  })}
                >
                  <MoreVertOutlined />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Menu open={!!menuAnchor} anchorEl={menuAnchor?.el} onClose={() => setMenuAnchor(null)}>
        {menuAnchor && [
          <MenuItem
            key="edit"
            onClick={() => {
              const s = menuAnchor.sandbox;
              setDialogState({
                open: true,
                mode: 'edit',
                id: s.sandbox_id,
                value: {
                  sandbox_name: s.sandbox_name,
                  sandbox_description: s.sandbox_description ?? '',
                  sandbox_network_policy: s.sandbox_network_policy,
                  sandbox_network_rules: s.sandbox_network_rules,
                  sandbox_auto_restore_enabled: s.sandbox_auto_restore_enabled,
                  sandbox_supported_sample_types: s.sandbox_supported_sample_types,
                  sandbox_status: s.sandbox_status,
                },
              });
              setMenuAnchor(null);
            }}
          >
            编辑
          </MenuItem>,
          <MenuItem
            key="iptables"
            onClick={() => {
              void onExportIptables(menuAnchor.sandbox.sandbox_id);
              setMenuAnchor(null);
            }}
          >
            导出 iptables 脚本
          </MenuItem>,
          <MenuItem
            key="routing"
            onClick={() => {
              void onExportRoutingConf(menuAnchor.sandbox.sandbox_id);
              setMenuAnchor(null);
            }}
          >
            导出 routing.conf 片段
          </MenuItem>,
          <MenuItem
            key="delete"
            onClick={() => {
              setDeleteState({
                open: true,
                id: menuAnchor.sandbox.sandbox_id,
                name: menuAnchor.sandbox.sandbox_name,
              });
              setMenuAnchor(null);
            }}
          >
            删除
          </MenuItem>,
        ]}
      </Menu>

      <SandboxDialog
        open={dialogState.open}
        mode={dialogState.mode}
        initialValue={dialogState.value}
        onCancel={() => setDialogState(s => ({
          ...s,
          open: false,
        }))}
        onSubmit={onSubmit}
      />
      <DeleteConfirmDialog
        open={deleteState.open}
        title="删除沙箱预设"
        message={`确定删除沙箱预设「${deleteState.name ?? ''}」？此操作不可恢复。`}
        onCancel={() => setDeleteState({ open: false })}
        onConfirm={onDelete}
      />
    </Paper>
  );
};

export default SandboxList;
