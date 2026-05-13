/* eslint-disable i18next/no-literal-string -- IPv6 §3.6 ★2 攻击组合 UI 首发与既有
   VeriguardConsole / StabilityTrendView 一致使用硬编码中文；后续统一迁移 react-intl. */
import { ExpandMore } from '@mui/icons-material';
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip, Dialog,
  DialogActions, DialogContent, DialogTitle, LinearProgress, MenuItem, Pagination,
  Select, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';

import {
  type AttackCombinationHitState,
  type AttackCombinationResultOutput,
  fetchClusterMembers,
} from '../../../actions/combination/combination-actions';

const HIT_STATE_OPTIONS: ReadonlyArray<{
  value: AttackCombinationHitState | '';
  label: string;
}> = [
  {
    value: '',
    label: '全部',
  },
  {
    value: 'hit',
    label: '命中（hit）',
  },
  {
    value: 'miss',
    label: '未命中（miss）',
  },
  {
    value: 'timeout',
    label: '超时（timeout）',
  },
  {
    value: 'failed',
    label: '失败（failed）',
  },
  {
    value: 'pending',
    label: '待执行',
  },
  {
    value: 'running',
    label: '执行中',
  },
];

const HIT_STATE_COLOR: Record<AttackCombinationHitState, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  pending: 'default',
  running: 'info',
  hit: 'success',
  miss: 'warning',
  timeout: 'warning',
  failed: 'error',
};

const PAGE_SIZE = 25;

const formatDate = (iso: string | null): string => {
  if (!iso) {
    return '-';
  }
  return new Date(iso).toLocaleString();
};

type Props = {
  open: boolean;
  runId: string;
  clusterId: string;
  clusterLabel: string;
  onClose: () => void;
};

const PayloadDrilldown = ({ open, runId, clusterId, clusterLabel, onClose }: Props) => {
  const [results, setResults] = useState<AttackCombinationResultOutput[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [hitState, setHitState] = useState<AttackCombinationHitState | ''>('miss');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (open) {
      const load = async () => {
        setLoading(true);
        setError(null);
        try {
          const data = await fetchClusterMembers(runId, clusterId, {
            hit_state: hitState || undefined,
            page,
            size: PAGE_SIZE,
          });
          if (cancelled) {
            return;
          }
          setResults(data.content);
          setTotalElements(data.total_elements);
        } catch (e: unknown) {
          if (!cancelled) {
            setError(e instanceof Error ? e.message : '加载聚类成员失败');
          }
        } finally {
          if (!cancelled) {
            setLoading(false);
          }
        }
      };
      void load();
    }
    return () => {
      cancelled = true;
    };
  }, [open, runId, clusterId, hitState, page]);

  const totalPages = Math.ceil(totalElements / PAGE_SIZE) || 1;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>
        Payload 下钻 ——
        {' '}
        {clusterLabel}
        <Typography variant="caption" color="text.secondary" display="block">
          {clusterId}
        </Typography>
      </DialogTitle>
      <DialogContent>
        <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 2 }}>
          <Typography variant="body2">命中状态过滤：</Typography>
          <Select
            size="small"
            value={hitState}
            onChange={(e) => {
              setHitState((e.target.value as AttackCombinationHitState | '') || '');
              setPage(0);
            }}
            sx={{ minWidth: 200 }}
          >
            {HIT_STATE_OPTIONS.map(o => (
              <MenuItem key={o.value || 'all'} value={o.value}>{o.label}</MenuItem>
            ))}
          </Select>
          <Typography variant="caption" color="text.secondary">
            {`共 ${totalElements.toLocaleString()} 条`}
          </Typography>
        </Stack>

        {loading && <LinearProgress />}
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        {!loading && results.length === 0 && !error && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              textAlign: 'center',
              py: 4,
            }}
          >
            该过滤条件下无结果。
          </Typography>
        )}

        {results.length > 0 && (
          <>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>基础攻击</TableCell>
                  <TableCell>绕过维度</TableCell>
                  <TableCell>资产</TableCell>
                  <TableCell>命中</TableCell>
                  <TableCell align="right">重试</TableCell>
                  <TableCell>执行时间</TableCell>
                  <TableCell>Payload 样本</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {results.map(r => (
                  <TableRow key={r.attack_combination_result_id}>
                    <TableCell>{r.attack_combination_result_base_attack_type}</TableCell>
                    <TableCell>
                      <code>{r.attack_combination_result_bypass_dimension_id}</code>
                    </TableCell>
                    <TableCell>
                      <code>{r.attack_combination_result_asset_id}</code>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={r.attack_combination_result_hit_state}
                        color={HIT_STATE_COLOR[r.attack_combination_result_hit_state] ?? 'default'}
                      />
                    </TableCell>
                    <TableCell align="right">{r.attack_combination_result_retry_count}</TableCell>
                    <TableCell>{formatDate(r.attack_combination_result_executed_at)}</TableCell>
                    <TableCell sx={{ maxWidth: 320 }}>
                      <Accordion sx={{
                        'boxShadow': 'none',
                        '&:before': { display: 'none' },
                      }}
                      >
                        <AccordionSummary expandIcon={<ExpandMore />} sx={{ p: 0 }}>
                          <Typography
                            variant="caption"
                            sx={{
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              maxWidth: 280,
                            }}
                          >
                            {(r.attack_combination_result_payload_sample ?? '').slice(0, 120) || '(无)'}
                          </Typography>
                        </AccordionSummary>
                        <AccordionDetails sx={{
                          p: 0,
                          pt: 1,
                        }}
                        >
                          <Box
                            component="pre"
                            sx={{
                              p: 1,
                              bgcolor: 'background.default',
                              fontSize: 11,
                              whiteSpace: 'pre-wrap',
                              wordBreak: 'break-all',
                              maxHeight: 240,
                              overflow: 'auto',
                            }}
                          >
                            {r.attack_combination_result_payload_sample || '(空)'}
                          </Box>
                          {r.attack_combination_result_error_message && (
                            <Alert severity="error" sx={{ mt: 1 }}>
                              {r.attack_combination_result_error_message}
                            </Alert>
                          )}
                        </AccordionDetails>
                      </Accordion>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <Stack direction="row" justifyContent="center" sx={{ mt: 2 }}>
              <Pagination
                count={totalPages}
                page={page + 1}
                onChange={(_, p) => setPage(p - 1)}
                size="small"
              />
            </Stack>
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>关闭</Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadDrilldown;
