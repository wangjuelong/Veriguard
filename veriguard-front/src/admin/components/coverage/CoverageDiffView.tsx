/* eslint-disable i18next/no-literal-string -- IPv6 PR C3 边界覆盖度 UI */
import {
  Alert, Box, Button, Chip, LinearProgress, Paper, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { useState } from 'react';

import {
  type CoverageChangeType,
  type CoverageDiffOutput,
  fetchCoverageDiff,
} from '../../../actions/coverage/coverage-actions';

interface CoverageDiffViewProps { runId: string }

const CHANGE_TYPE_COLOR: Record<CoverageChangeType, 'default' | 'success' | 'error' | 'warning' | 'info'> = {
  unchanged: 'default',
  new_hit: 'success',
  new_miss: 'error',
  dropped_hit: 'warning',
  dropped_miss: 'info',
  state_changed: 'info',
};

const CHANGE_TYPE_LABEL: Record<CoverageChangeType, string> = {
  unchanged: '未变',
  new_hit: '↑ 新增覆盖',
  new_miss: '↓ 新增缺失',
  dropped_hit: '✗ 失去覆盖',
  dropped_miss: '○ 缺失格已移除',
  state_changed: '⇆ 状态切换',
};

const SummaryChip = ({
  label,
  value,
  color,
}: {
  label: string;
  value: number;
  color: 'default' | 'success' | 'error' | 'warning' | 'info';
}) => (
  <Chip
    label={`${label}: ${value}`}
    color={color}
    variant="outlined"
    size="small"
  />
);

const CoverageDiffView = ({ runId }: CoverageDiffViewProps) => {
  const [compareRunId, setCompareRunId] = useState<string>('');
  const [report, setReport] = useState<CoverageDiffOutput | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const handleCompare = async () => {
    if (!compareRunId.trim()) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const r = await fetchCoverageDiff(runId, compareRunId.trim());
      setReport(r);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载 diff 失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} alignItems="center">
        <TextField
          label="对比 run_id"
          value={compareRunId}
          onChange={e => setCompareRunId(e.target.value)}
          size="small"
          sx={{ flexGrow: 1 }}
          placeholder="输入要对比的另一个 run_id"
        />
        <Button variant="contained" onClick={handleCompare} disabled={loading || !compareRunId.trim()}>
          对比
        </Button>
      </Stack>
      {loading && <LinearProgress />}
      {error && <Alert severity="error">{error}</Alert>}
      {report && (
        <>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="body2" color="text.secondary">
              对比汇总
            </Typography>
            <Stack
              direction="row"
              spacing={2}
              sx={{
                mt: 1,
                flexWrap: 'wrap',
              }}
            >
              <SummaryChip label="未变" value={report.summary.unchanged} color="default" />
              <SummaryChip label="新增覆盖" value={report.summary.new_hit} color="success" />
              <SummaryChip label="新增缺失" value={report.summary.new_miss} color="error" />
              <SummaryChip label="失去覆盖" value={report.summary.dropped_hit} color="warning" />
              <SummaryChip label="缺失格已移除" value={report.summary.dropped_miss} color="info" />
              <SummaryChip label="状态切换" value={report.summary.state_changed} color="info" />
            </Stack>
          </Paper>
          <Paper variant="outlined" sx={{ overflowX: 'auto' }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>资产</TableCell>
                  <TableCell>策略</TableCell>
                  <TableCell>旧状态</TableCell>
                  <TableCell>新状态</TableCell>
                  <TableCell>变化</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {report.deltas.map(d => (
                  <TableRow key={`${d.asset_id}::${d.policy_id}`}>
                    <TableCell sx={{ fontSize: '0.75rem' }}>
                      {d.asset_id.slice(0, 16)}
                      …
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.75rem' }}>
                      {d.policy_id.slice(0, 16)}
                      …
                    </TableCell>
                    <TableCell>{d.old_state ?? '-'}</TableCell>
                    <TableCell>{d.new_state ?? '-'}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={CHANGE_TYPE_LABEL[d.change_type]}
                        color={CHANGE_TYPE_COLOR[d.change_type]}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        </>
      )}
      {!loading && !report && (
        <Box sx={{
          textAlign: 'center',
          py: 4,
        }}
        >
          <Typography variant="body2" color="text.secondary">
            输入另一个 run_id 后点击「对比」查看变化矩阵。
          </Typography>
        </Box>
      )}
    </Stack>
  );
};

export default CoverageDiffView;
