/* eslint-disable i18next/no-literal-string -- IPv6 PR C3 边界覆盖度 UI */
import {
  Box, LinearProgress, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow,
  Tooltip, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import { type CoverageCellOutput, type CoverageHitState, fetchCoverageMatrix } from '../../../actions/coverage/coverage-actions';
import CellDetailDrawer from './CellDetailDrawer';

interface CoverageMatrixViewProps { runId: string }

const HIT_STATE_BG: Record<CoverageHitState, string> = {
  hit: '#16a34a', // green-600
  miss: '#dc2626', // red-600
  timeout: '#9ca3af', // gray-400
  out_of_scope: '#e5e7eb', // gray-200
};

const HIT_STATE_GLYPH: Record<CoverageHitState, string> = {
  hit: '✓',
  miss: '✗',
  timeout: '⌛',
  out_of_scope: '·',
};

const PAGE_SIZE = 1000; // matrix loads up to 1000 cells per page (paginated by run/asset/policy)

const LegendItem = ({ state, label }: {
  state: CoverageHitState;
  label: string;
}) => (
  <Stack direction="row" spacing={0.5} alignItems="center">
    <Box sx={{
      width: 14,
      height: 14,
      bgcolor: HIT_STATE_BG[state],
      borderRadius: 0.5,
    }}
    />
    <Typography variant="caption">{label}</Typography>
  </Stack>
);

const Legend = () => (
  <Stack direction="row" spacing={1}>
    <LegendItem state="hit" label="有覆盖" />
    <LegendItem state="miss" label="无覆盖" />
    <LegendItem state="timeout" label="超时" />
    <LegendItem state="out_of_scope" label="不适用" />
  </Stack>
);

const CoverageMatrixView = ({ runId }: CoverageMatrixViewProps) => {
  const [cells, setCells] = useState<CoverageCellOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<CoverageCellOutput | null>(null);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError(null);
    fetchCoverageMatrix(runId, {
      page: 0,
      size: PAGE_SIZE,
    })
      .then((p) => {
        if (mounted) {
          setCells(p.content);
        }
      })
      .catch((e) => {
        if (mounted) {
          setError(e instanceof Error ? e.message : '加载矩阵失败');
        }
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [runId]);

  const { assetIds, policyIds, cellIndex } = useMemo(() => {
    const assets = new Set<string>();
    const policies = new Set<string>();
    const idx = new Map<string, CoverageCellOutput>();
    for (const c of cells) {
      assets.add(c.coverage_result_asset_id);
      policies.add(c.coverage_result_policy_id);
      idx.set(`${c.coverage_result_asset_id}::${c.coverage_result_policy_id}`, c);
    }
    return {
      assetIds: Array.from(assets).sort(),
      policyIds: Array.from(policies).sort(),
      cellIndex: idx,
    };
  }, [cells]);

  if (loading) {
    return <LinearProgress />;
  }
  if (error) {
    return <Typography color="error">{error}</Typography>;
  }
  if (cells.length === 0) {
    return (
      <Typography
        variant="body2"
        color="text.secondary"
        sx={{
          textAlign: 'center',
          py: 4,
        }}
      >
        当前 run 暂无覆盖矩阵数据。
      </Typography>
    );
  }

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Typography variant="body2" color="text.secondary">
          共
          {' '}
          {assetIds.length}
          {' '}
          个资产 ×
          {' '}
          {policyIds.length}
          {' '}
          个策略；点击单元格查看详情。
        </Typography>
        <Legend />
      </Stack>
      <Paper
        variant="outlined"
        sx={{
          overflowX: 'auto',
          maxWidth: '100%',
        }}
      >
        <Table size="small" stickyHeader sx={{ tableLayout: 'fixed' }}>
          <TableHead>
            <TableRow>
              <TableCell sx={{
                position: 'sticky',
                left: 0,
                bgcolor: 'background.paper',
                minWidth: 200,
                zIndex: 3,
              }}
              >
                资产 \ 策略
              </TableCell>
              {policyIds.map(pid => (
                <TableCell
                  key={pid}
                  align="center"
                  sx={{
                    minWidth: 80,
                    fontSize: '0.7rem',
                    wordBreak: 'break-all',
                  }}
                >
                  <Tooltip title={pid}>
                    <span>
                      {pid.slice(0, 8)}
                      …
                    </span>
                  </Tooltip>
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {assetIds.map(aid => (
              <TableRow key={aid}>
                <TableCell sx={{
                  position: 'sticky',
                  left: 0,
                  bgcolor: 'background.paper',
                  fontSize: '0.75rem',
                  wordBreak: 'break-all',
                  zIndex: 1,
                }}
                >
                  <Tooltip title={aid}>
                    <span>
                      {aid.slice(0, 16)}
                      …
                    </span>
                  </Tooltip>
                </TableCell>
                {policyIds.map((pid) => {
                  const c = cellIndex.get(`${aid}::${pid}`);
                  if (!c) {
                    return (
                      <TableCell key={pid} align="center" sx={{ bgcolor: '#f3f4f6' }}>
                        <Typography variant="caption" color="text.disabled">-</Typography>
                      </TableCell>
                    );
                  }
                  const bg = HIT_STATE_BG[c.coverage_result_hit_state];
                  return (
                    <TableCell
                      key={pid}
                      align="center"
                      onClick={() => setSelected(c)}
                      sx={{
                        'bgcolor': bg,
                        'color': c.coverage_result_hit_state === 'out_of_scope' ? 'text.secondary' : 'white',
                        'cursor': 'pointer',
                        'fontWeight': 'bold',
                        '&:hover': { opacity: 0.8 },
                      }}
                    >
                      {HIT_STATE_GLYPH[c.coverage_result_hit_state]}
                    </TableCell>
                  );
                })}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      <CellDetailDrawer cell={selected} onClose={() => setSelected(null)} />
    </Stack>
  );
};

export default CoverageMatrixView;
