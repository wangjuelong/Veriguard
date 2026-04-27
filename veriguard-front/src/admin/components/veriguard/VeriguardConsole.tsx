/* eslint-disable i18next/no-literal-string -- spec §6.7: M1 sandbox UI uses
   hardcoded Chinese to match existing VeriguardConsole.tsx pattern; future
   M-x will migrate to react-intl when sandbox UI stabilizes. */
import { RefreshOutlined } from '@mui/icons-material';
import {
  Box,
  Chip,
  IconButton,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';
import { type ReactNode, useCallback, useEffect, useState } from 'react';

import {
  type AttackCatalogOutput,
  type CapabilityMatrixOutput,
  fetchVeriguardAttackCatalog,
  fetchVeriguardCapabilityMatrix,
  fetchVeriguardOrchestrationSchema,
  type OrchestrationSchemaOutput,
} from '../../../actions/veriguard/veriguard-actions';
import Loader from '../../../components/Loader';
import SandboxList from './sandbox/SandboxList';

type TabValue = 'matrix' | 'catalog' | 'orchestration' | 'sandboxes';

const compactList = (items: string[]) => items.join(' / ');

const Section = ({ children }: { children: ReactNode }) => (
  <Paper
    variant="outlined"
    sx={{
      p: 2,
      borderRadius: 1,
    }}
  >
    {children}
  </Paper>
);

const VeriguardConsole = () => {
  const [tab, setTab] = useState<TabValue>('matrix');
  const [loading, setLoading] = useState(true);
  const [matrix, setMatrix] = useState<CapabilityMatrixOutput | null>(null);
  const [catalog, setCatalog] = useState<AttackCatalogOutput | null>(null);
  const [orchestration, setOrchestration] = useState<OrchestrationSchemaOutput | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    const [matrixResponse, catalogResponse, orchestrationResponse] = await Promise.all([
      fetchVeriguardCapabilityMatrix(),
      fetchVeriguardAttackCatalog(),
      fetchVeriguardOrchestrationSchema(),
    ]);
    setMatrix(matrixResponse);
    setCatalog(catalogResponse);
    setOrchestration(orchestrationResponse);
    setLoading(false);
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  if (loading || !matrix || !catalog || !orchestration) {
    return <Loader />;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
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

      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
        gap: 2,
      }}
      >
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
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: 2,
        }}
        >
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
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: 2,
        }}
        >
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

      {tab === 'sandboxes' && <SandboxList />}
    </Box>
  );
};

export default VeriguardConsole;
