/* eslint-disable i18next/no-literal-string -- IPv6 PR C3 边界覆盖度 UI 与既有
   CombinationsList / StabilityTrendView 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Box, Button, Chip, LinearProgress, MenuItem, Paper, Select, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  type CoverageBaselineOutput,
  type CoverageType,
  fetchCoverageBaselines,
} from '../../../actions/coverage/coverage-actions';
import CoverageBaselineEditor from './CoverageBaselineEditor';

const COVERAGE_TYPE_OPTIONS: ReadonlyArray<{
  value: CoverageType | '';
  label: string;
}> = [
  {
    value: '',
    label: '全部',
  },
  {
    value: 'boundary',
    label: '边界资产覆盖度（§3.1）',
  },
  {
    value: 'traffic',
    label: '流量边界覆盖度（§4.1）',
  },
];

const COVERAGE_TYPE_COLOR: Record<CoverageType, 'primary' | 'secondary'> = {
  boundary: 'primary',
  traffic: 'secondary',
};

const PAGE_SIZE = 20;

const formatDate = (iso: string | null): string => {
  if (!iso) {
    return '-';
  }
  return new Date(iso).toLocaleString();
};

const CoverageBaselinesList = () => {
  const navigate = useNavigate();
  const [baselines, setBaselines] = useState<CoverageBaselineOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [coverageType, setCoverageType] = useState<CoverageType | ''>('');
  const [editorOpen, setEditorOpen] = useState<boolean>(false);

  const load = useMemo(() => async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchCoverageBaselines({
        coverage_type: coverageType || undefined,
        page: 0,
        size: PAGE_SIZE,
      });
      setBaselines(result.content);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载基线列表失败');
    } finally {
      setLoading(false);
    }
  }, [coverageType]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Box>
          <Typography variant="h4">边界覆盖度基线</Typography>
          <Typography variant="body2" color="text.secondary">
            §3.1 边界资产覆盖度 + §4.1 流量边界覆盖度。基线 = 用例集合 × 资产组；可多次评估观察趋势。
          </Typography>
        </Box>
        <Stack direction="row" spacing={2}>
          <Select
            size="small"
            value={coverageType}
            displayEmpty
            onChange={e => setCoverageType((e.target.value as CoverageType | '') || '')}
            sx={{ minWidth: 200 }}
          >
            {COVERAGE_TYPE_OPTIONS.map(o => (
              <MenuItem key={o.value || 'all'} value={o.value}>{o.label}</MenuItem>
            ))}
          </Select>
          <Button variant="contained" onClick={() => setEditorOpen(true)}>新建基线</Button>
        </Stack>
      </Stack>

      <Paper sx={{ p: 2 }}>
        {loading && <LinearProgress />}
        {error && (
          <Typography color="error" variant="body2" sx={{ mb: 1 }}>{error}</Typography>
        )}
        {!loading && baselines.length === 0 && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              textAlign: 'center',
              py: 4,
            }}
          >
            暂无基线。点击右上角「新建基线」开始第一次覆盖度评估。
          </Typography>
        )}
        {baselines.length > 0 && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>名称</TableCell>
                <TableCell>类型</TableCell>
                <TableCell align="right">用例数</TableCell>
                <TableCell>资产组</TableCell>
                <TableCell align="right">SOC 等待 (s)</TableCell>
                <TableCell>创建时间</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {baselines.map(b => (
                <TableRow
                  key={b.coverage_baseline_id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/admin/coverage/baselines/${b.coverage_baseline_id}`)}
                >
                  <TableCell>
                    <Stack>
                      <Typography variant="body2">{b.coverage_baseline_name}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {b.coverage_baseline_id}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={b.coverage_baseline_coverage_type === 'boundary' ? '边界资产' : '流量边界'}
                      color={COVERAGE_TYPE_COLOR[b.coverage_baseline_coverage_type]}
                    />
                  </TableCell>
                  <TableCell align="right">{b.coverage_baseline_case_ids.length}</TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {b.coverage_baseline_asset_group_id}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">{b.coverage_baseline_soc_query_delay_seconds}</TableCell>
                  <TableCell>{formatDate(b.coverage_baseline_created_at)}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      onClick={(ev) => {
                        ev.stopPropagation();
                        navigate(`/admin/coverage/baselines/${b.coverage_baseline_id}`);
                      }}
                    >
                      详情
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      <CoverageBaselineEditor
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        onCreated={(baseline) => {
          setEditorOpen(false);
          navigate(`/admin/coverage/baselines/${baseline.coverage_baseline_id}`);
        }}
      />
    </Box>
  );
};

export default CoverageBaselinesList;
