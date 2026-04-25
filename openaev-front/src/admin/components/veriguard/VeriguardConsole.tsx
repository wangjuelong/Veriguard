import {
  AddOutlined,
  DeleteOutline,
  EditOutlined,
  RefreshOutlined,
} from '@mui/icons-material';
import {
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  type SelectChangeEvent,
  Stack,
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';

import {
  createVeriguardSandbox,
  deleteVeriguardSandbox,
  fetchVeriguardAttackCatalog,
  fetchVeriguardCapabilityMatrix,
  fetchVeriguardOrchestrationSchema,
  fetchVeriguardSandboxes,
  type AttackCatalogOutput,
  type CapabilityMatrixOutput,
  type OrchestrationSchemaOutput,
  type SandboxInput,
  type SandboxNetworkRule,
  type SandboxNetworkPolicy,
  type SandboxOutput,
  type SandboxProviderType,
  type SandboxRuleAction,
  type SandboxRuleDirection,
  type SandboxSampleType,
  type SandboxStatus,
  updateVeriguardSandbox,
} from '../../../actions/veriguard/veriguard-actions';
import Loader from '../../../components/Loader';

type TabValue = 'matrix' | 'catalog' | 'orchestration' | 'sandboxes';

const PROVIDER_TYPES: SandboxProviderType[] = ['VMWARE', 'OPENSTACK', 'KVM', 'KUBERNETES', 'CUSTOM'];
const NETWORK_POLICIES: SandboxNetworkPolicy[] = ['DENY_ALL', 'ALLOWLIST', 'ISOLATED_LAB', 'CUSTOM'];
const SAMPLE_TYPES: SandboxSampleType[] = [
  'RANSOMWARE',
  'MINER',
  'WORM',
  'MALICIOUS_DRIVER',
  'PRIVILEGE_ESCALATION',
  'ACCOUNT_THEFT',
  'PROXY_EXECUTION',
  'SECURITY_COMPONENT_BYPASS',
];
const STATUS_TYPES: SandboxStatus[] = ['ACTIVE', 'INACTIVE'];
const RULE_DIRECTIONS: SandboxRuleDirection[] = ['INGRESS', 'EGRESS'];
const RULE_ACTIONS: SandboxRuleAction[] = ['ALLOW', 'DENY'];

const sampleTypeLabels: Record<SandboxSampleType, string> = {
  RANSOMWARE: '勒索病毒样本执行',
  MINER: '挖矿病毒样本执行',
  WORM: '蠕虫病毒样本执行',
  MALICIOUS_DRIVER: '终端恶意驱动加载',
  PRIVILEGE_ESCALATION: '终端权限提升',
  ACCOUNT_THEFT: '终端系统账号窃取',
  PROXY_EXECUTION: '终端代理执行',
  SECURITY_COMPONENT_BYPASS: '终端安全组件对抗',
};

const defaultSandboxInput: SandboxInput = {
  sandbox_name: '',
  sandbox_description: '',
  sandbox_provider_type: 'CUSTOM',
  sandbox_endpoint: '',
  sandbox_network_policy: 'DENY_ALL',
  sandbox_network_rules: [{
    rule_direction: 'EGRESS',
    rule_action: 'DENY',
    rule_protocol: 'TCP',
    rule_cidr: '0.0.0.0/0',
    rule_ports: 'all',
  }],
  sandbox_auto_restore_enabled: true,
  sandbox_supported_sample_types: ['RANSOMWARE'],
  sandbox_status: 'ACTIVE',
};

const enumLabel = (value: string) => value.replaceAll('_', ' ');

const compactList = (items: string[]) => items.join(' / ');

const Section = ({ children }: { children: ReactNode }) => (
  <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
    {children}
  </Paper>
);

const VeriguardConsole = () => {
  const [tab, setTab] = useState<TabValue>('matrix');
  const [loading, setLoading] = useState(true);
  const [matrix, setMatrix] = useState<CapabilityMatrixOutput | null>(null);
  const [catalog, setCatalog] = useState<AttackCatalogOutput | null>(null);
  const [orchestration, setOrchestration] = useState<OrchestrationSchemaOutput | null>(null);
  const [sandboxes, setSandboxes] = useState<SandboxOutput[]>([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingSandboxId, setEditingSandboxId] = useState<string | null>(null);
  const [form, setForm] = useState<SandboxInput>(defaultSandboxInput);

  const loadData = useCallback(async () => {
    setLoading(true);
    const [matrixResponse, catalogResponse, orchestrationResponse, sandboxResponse] = await Promise.all([
      fetchVeriguardCapabilityMatrix(),
      fetchVeriguardAttackCatalog(),
      fetchVeriguardOrchestrationSchema(),
      fetchVeriguardSandboxes(),
    ]);
    setMatrix(matrixResponse);
    setCatalog(catalogResponse);
    setOrchestration(orchestrationResponse);
    setSandboxes(sandboxResponse);
    setLoading(false);
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const selectedRule = form.sandbox_network_rules[0] ?? defaultSandboxInput.sandbox_network_rules[0];
  const formValid = useMemo(() => {
    return form.sandbox_name.trim().length > 0
      && form.sandbox_endpoint.trim().length > 0
      && selectedRule.rule_protocol.trim().length > 0
      && selectedRule.rule_cidr.trim().length > 0
      && selectedRule.rule_ports.trim().length > 0
      && form.sandbox_supported_sample_types.length > 0
      && form.sandbox_auto_restore_enabled;
  }, [form, selectedRule]);

  const openCreateDialog = () => {
    setEditingSandboxId(null);
    setForm(defaultSandboxInput);
    setDialogOpen(true);
  };

  const openEditDialog = (sandbox: SandboxOutput) => {
    setEditingSandboxId(sandbox.sandbox_id);
    setForm({
      sandbox_name: sandbox.sandbox_name,
      sandbox_description: sandbox.sandbox_description ?? '',
      sandbox_provider_type: sandbox.sandbox_provider_type,
      sandbox_endpoint: sandbox.sandbox_endpoint,
      sandbox_network_policy: sandbox.sandbox_network_policy,
      sandbox_network_rules: sandbox.sandbox_network_rules.length > 0
        ? sandbox.sandbox_network_rules
        : defaultSandboxInput.sandbox_network_rules,
      sandbox_auto_restore_enabled: sandbox.sandbox_auto_restore_enabled,
      sandbox_supported_sample_types: sandbox.sandbox_supported_sample_types,
      sandbox_status: sandbox.sandbox_status,
    });
    setDialogOpen(true);
  };

  const updateField = <K extends keyof SandboxInput>(key: K, value: SandboxInput[K]) => {
    setForm(current => ({ ...current, [key]: value }));
  };

  const updateRuleField = <K extends keyof SandboxNetworkRule>(key: K, value: SandboxNetworkRule[K]) => {
    setForm((current) => {
      const currentRule = current.sandbox_network_rules[0] ?? defaultSandboxInput.sandbox_network_rules[0];
      return {
        ...current,
        sandbox_network_rules: [{
          ...currentRule,
          [key]: value,
        }],
      };
    });
  };

  const toggleSampleType = (sampleType: SandboxSampleType) => {
    setForm((current) => {
      const exists = current.sandbox_supported_sample_types.includes(sampleType);
      const next = exists
        ? current.sandbox_supported_sample_types.filter(item => item !== sampleType)
        : [...current.sandbox_supported_sample_types, sampleType];
      return { ...current, sandbox_supported_sample_types: next };
    });
  };

  const submitSandbox = async () => {
    if (!formValid) {
      return;
    }
    if (editingSandboxId) {
      await updateVeriguardSandbox(editingSandboxId, form);
    } else {
      await createVeriguardSandbox(form);
    }
    setDialogOpen(false);
    await loadData();
  };

  const removeSandbox = async (sandboxId: string) => {
    await deleteVeriguardSandbox(sandboxId);
    await loadData();
  };

  if (loading || !matrix || !catalog || !orchestration) {
    return <Loader />;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4">Veriguard</Typography>
          <Typography variant="body2" color="text.secondary">
            IPv6 安全验证系统
          </Typography>
        </Box>
        <Tooltip title="刷新数据">
          <IconButton onClick={() => void loadData()} size="large">
            <RefreshOutlined />
          </IconButton>
        </Tooltip>
      </Stack>

      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 2 }}>
        <Section>
          <Typography variant="overline">PRD 模块</Typography>
          <Typography variant="h5">{matrix.summary.prd_module_count}</Typography>
        </Section>
        <Section>
          <Typography variant="overline">就绪模块</Typography>
          <Typography variant="h5">{matrix.summary.acceptance_ready_count}</Typography>
        </Section>
        <Section>
          <Typography variant="overline">用例模板容量</Typography>
          <Typography variant="h5">{matrix.summary.total_use_case_templates}</Typography>
        </Section>
        <Section>
          <Typography variant="overline">外部适配器</Typography>
          <Typography variant="h5">{matrix.summary.external_integration_count}</Typography>
        </Section>
      </Box>

      <Paper variant="outlined" sx={{ borderRadius: 1 }}>
        <Tabs value={tab} onChange={(_event, value: string) => setTab(value as TabValue)}>
          <Tab value="matrix" label="能力矩阵" />
          <Tab value="catalog" label="用例目录" />
          <Tab value="orchestration" label="攻击编排" />
          <Tab value="sandboxes" label="沙箱管理" />
        </Tabs>
      </Paper>

      {tab === 'matrix' && (
        <Section>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>模块</TableCell>
                <TableCell>状态</TableCell>
                <TableCell>控制项</TableCell>
                <TableCell>集成边界</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {matrix.modules.map(module => (
                <TableRow key={module.module_key}>
                  <TableCell>{module.module_name}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={module.acceptance_ready ? 'success' : 'warning'}
                      label={module.implementation_state}
                    />
                  </TableCell>
                  <TableCell>{compactList(module.controls)}</TableCell>
                  <TableCell>{compactList(module.external_integrations_required)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Section>
      )}

      {tab === 'catalog' && (
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <Section>
            <Typography variant="h6" sx={{ mb: 1 }}>流量安全验证</Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>攻击类型</TableCell>
                  <TableCell align="right">模板数</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {catalog.traffic_attack_types.map(type => (
                  <TableRow key={type.attack_type}>
                    <TableCell>{type.attack_type}</TableCell>
                    <TableCell align="right">{type.template_count}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Section>
          <Section>
            <Typography variant="h6" sx={{ mb: 1 }}>应用与服务器安全验证</Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>攻击类型</TableCell>
                  <TableCell align="right">模板数</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {catalog.host_attack_types.map(type => (
                  <TableRow key={type.attack_type}>
                    <TableCell>{type.attack_type}</TableCell>
                    <TableCell align="right">{type.template_count}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Section>
          <Section>
            <Typography variant="h6" sx={{ mb: 1 }}>自定义用例类型</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {catalog.custom_case_types.map(type => <Chip key={type} label={type} />)}
            </Stack>
          </Section>
          <Section>
            <Typography variant="h6" sx={{ mb: 1 }}>目录指标</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip label={`${catalog.total_use_case_templates} 个模板`} />
              <Chip
                color={catalog.minimum_attack_type_requirement_met ? 'success' : 'warning'}
                label="攻击类型达标"
              />
              <Chip
                color={catalog.multiple_tuple_per_case_supported ? 'success' : 'warning'}
                label="支持多四元组"
              />
            </Stack>
          </Section>
        </Box>
      )}

      {tab === 'orchestration' && (
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 2 }}>
          <Section>
            <Typography variant="h6" sx={{ mb: 1 }}>节点策略字段</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {orchestration.node_policy_fields.map(item => <Chip key={item} label={item} />)}
            </Stack>
          </Section>
          <Section>
            <Typography variant="h6" sx={{ mb: 1 }}>执行模式</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {orchestration.execution_modes.map(item => <Chip key={item} label={item} />)}
            </Stack>
          </Section>
          <Section>
            <Typography variant="h6" sx={{ mb: 1 }}>SOC 规则匹配</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {orchestration.soc_rule_match_fields.map(item => <Chip key={item} label={item} />)}
            </Stack>
          </Section>
          <Section>
            <Typography variant="h6" sx={{ mb: 1 }}>链路结果</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              {orchestration.chain_result_states.map(item => <Chip key={item} label={item} />)}
            </Stack>
          </Section>
        </Box>
      )}

      {tab === 'sandboxes' && (
        <Section>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Typography variant="h6">沙箱平台</Typography>
            <Button startIcon={<AddOutlined />} variant="contained" onClick={openCreateDialog}>
              新建
            </Button>
          </Stack>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>名称</TableCell>
                <TableCell>类型</TableCell>
                <TableCell>网络策略</TableCell>
                <TableCell>样本类型</TableCell>
                <TableCell>自动还原</TableCell>
                <TableCell>状态</TableCell>
                <TableCell align="right">操作</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sandboxes.map(sandbox => (
                <TableRow key={sandbox.sandbox_id}>
                  <TableCell>{sandbox.sandbox_name}</TableCell>
                  <TableCell>{enumLabel(sandbox.sandbox_provider_type)}</TableCell>
                  <TableCell>{enumLabel(sandbox.sandbox_network_policy)}</TableCell>
                  <TableCell>{sandbox.sandbox_supported_sample_types.length}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={sandbox.sandbox_auto_restore_enabled ? 'success' : 'error'}
                      label={sandbox.sandbox_auto_restore_enabled ? '已开启' : '未开启'}
                    />
                  </TableCell>
                  <TableCell>{enumLabel(sandbox.sandbox_status)}</TableCell>
                  <TableCell align="right">
                    <Tooltip title="编辑">
                      <IconButton onClick={() => openEditDialog(sandbox)}>
                        <EditOutlined />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="删除">
                      <IconButton onClick={() => void removeSandbox(sandbox.sandbox_id)}>
                        <DeleteOutline />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Section>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{editingSandboxId ? '编辑沙箱平台' : '新建沙箱平台'}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, pt: 1 }}>
            <TextField
              label="名称"
              value={form.sandbox_name}
              onChange={event => updateField('sandbox_name', event.target.value)}
              required
            />
            <TextField
              label="管理端点"
              value={form.sandbox_endpoint}
              onChange={event => updateField('sandbox_endpoint', event.target.value)}
              required
            />
            <FormControl>
              <InputLabel>类型</InputLabel>
              <Select
                label="类型"
                value={form.sandbox_provider_type}
                onChange={(event: SelectChangeEvent) => updateField(
                  'sandbox_provider_type',
                  event.target.value as SandboxProviderType,
                )}
              >
                {PROVIDER_TYPES.map(provider => (
                  <MenuItem key={provider} value={provider}>{enumLabel(provider)}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl>
              <InputLabel>网络策略</InputLabel>
              <Select
                label="网络策略"
                value={form.sandbox_network_policy}
                onChange={(event: SelectChangeEvent) => updateField(
                  'sandbox_network_policy',
                  event.target.value as SandboxNetworkPolicy,
                )}
              >
                {NETWORK_POLICIES.map(policy => (
                  <MenuItem key={policy} value={policy}>{enumLabel(policy)}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="描述"
              value={form.sandbox_description ?? ''}
              onChange={event => updateField('sandbox_description', event.target.value)}
              multiline
              minRows={2}
              sx={{ gridColumn: '1 / -1' }}
            />
            <FormControl>
              <InputLabel>规则方向</InputLabel>
              <Select
                label="规则方向"
                value={selectedRule.rule_direction}
                onChange={(event: SelectChangeEvent) => updateRuleField(
                  'rule_direction',
                  event.target.value as SandboxRuleDirection,
                )}
              >
                {RULE_DIRECTIONS.map(direction => (
                  <MenuItem key={direction} value={direction}>{direction}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl>
              <InputLabel>规则动作</InputLabel>
              <Select
                label="规则动作"
                value={selectedRule.rule_action}
                onChange={(event: SelectChangeEvent) => updateRuleField(
                  'rule_action',
                  event.target.value as SandboxRuleAction,
                )}
              >
                {RULE_ACTIONS.map(action => (
                  <MenuItem key={action} value={action}>{action}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="协议"
              value={selectedRule.rule_protocol}
              onChange={event => updateRuleField('rule_protocol', event.target.value)}
              required
            />
            <TextField
              label="CIDR"
              value={selectedRule.rule_cidr}
              onChange={event => updateRuleField('rule_cidr', event.target.value)}
              required
            />
            <TextField
              label="端口"
              value={selectedRule.rule_ports}
              onChange={event => updateRuleField('rule_ports', event.target.value)}
              required
            />
            <FormControl>
              <InputLabel>状态</InputLabel>
              <Select
                label="状态"
                value={form.sandbox_status}
                onChange={(event: SelectChangeEvent) => updateField(
                  'sandbox_status',
                  event.target.value as SandboxStatus,
                )}
              >
                {STATUS_TYPES.map(status => (
                  <MenuItem key={status} value={status}>{enumLabel(status)}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControlLabel
              control={(
                <Switch
                  checked={form.sandbox_auto_restore_enabled}
                  onChange={event => updateField('sandbox_auto_restore_enabled', event.target.checked)}
                />
              )}
              label="执行完成后自动还原"
            />
            <Box sx={{ gridColumn: '1 / -1' }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>样本类型</Typography>
              <Stack direction="row" gap={1} flexWrap="wrap">
                {SAMPLE_TYPES.map(sampleType => (
                  <FormControlLabel
                    key={sampleType}
                    control={(
                      <Checkbox
                        checked={form.sandbox_supported_sample_types.includes(sampleType)}
                        onChange={() => toggleSampleType(sampleType)}
                      />
                    )}
                    label={sampleTypeLabels[sampleType]}
                  />
                ))}
              </Stack>
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>取消</Button>
          <Button variant="contained" disabled={!formValid} onClick={() => void submitSandbox()}>
            保存
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default VeriguardConsole;
