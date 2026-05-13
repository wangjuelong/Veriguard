/* eslint-disable i18next/no-literal-string -- IPv6 §3.6 ★2 攻击组合 UI 首发与既有
   VeriguardConsole / StabilityTrendView 一致使用硬编码中文；后续统一迁移 react-intl. */
import { ExpandMore } from '@mui/icons-material';
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip,
  LinearProgress, Pagination, Paper, Stack, Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';

import {
  type AttackCombinationClusterDim,
  type AttackCombinationClusterOutput,
  fetchClusters,
} from '../../../actions/combination/combination-actions';
import PayloadDrilldown from './PayloadDrilldown';

const PAGE_SIZE = 10;
const PAYLOAD_PREVIEW_COUNT = 5;
const TOP_DISTRIBUTION_COUNT = 5;

type Props = {
  runId: string;
  dim: AttackCombinationClusterDim;
};

const ClusterTreeView = ({ runId, dim }: Props) => {
  const [clusters, setClusters] = useState<AttackCombinationClusterOutput[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [drillCluster, setDrillCluster] = useState<AttackCombinationClusterOutput | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await fetchClusters(runId, {
          dim,
          page,
          size: PAGE_SIZE,
        });
        if (cancelled) {
          return;
        }
        setClusters(data.content);
        setTotalElements(data.total_elements);
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '加载聚类失败');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [runId, dim, page]);

  // 重置分页当 dim 切换
  useEffect(() => {
    setPage(0);
  }, [dim]);

  const totalPages = Math.ceil(totalElements / PAGE_SIZE) || 1;

  const renderDistribution = (
    label: string,
    distribution: Array<Record<string, unknown>>,
  ) => {
    if (!distribution || distribution.length === 0) {
      return null;
    }
    const top = distribution.slice(0, TOP_DISTRIBUTION_COUNT);
    return (
      <Box sx={{ mt: 1 }}>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Stack
          direction="row"
          spacing={1}
          sx={{
            flexWrap: 'wrap',
            gap: 0.5,
          }}
        >
          {top.map((d, idx) => {
            const key = String(d.key ?? d.name ?? d.id ?? idx);
            const count = Number(d.count ?? d.miss_count ?? 0);
            return (
              <Chip
                key={`${key}-${idx}`}
                size="small"
                label={`${key} × ${count}`}
                variant="outlined"
              />
            );
          })}
        </Stack>
      </Box>
    );
  };

  return (
    <Box>
      {loading && <LinearProgress sx={{ mb: 1 }} />}
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {!loading && clusters.length === 0 && !error && (
        <Paper sx={{
          p: 3,
          textAlign: 'center',
        }}
        >
          <Typography variant="body2" color="text.secondary">
            该任务暂无聚类数据。任务完成后会自动触发聚类分析；若已完成可在「概览」页点击「重算聚类」。
          </Typography>
        </Paper>
      )}

      <Stack spacing={1}>
        {clusters.map((c) => {
          const sevColor = c.attack_combination_cluster_severity_color ?? '#9e9e9e';
          const sevLabel = c.attack_combination_cluster_severity_label ?? '未分级';
          const sevLevel = c.attack_combination_cluster_severity_level ?? 'unscored';
          return (
            <Paper key={c.attack_combination_cluster_id} variant="outlined">
              <Accordion sx={{
                'boxShadow': 'none',
                '&:before': { display: 'none' },
              }}
              >
                <AccordionSummary expandIcon={<ExpandMore />}>
                  <Stack direction="row" alignItems="center" spacing={2} sx={{ width: '100%' }}>
                    <Chip
                      label={sevLabel}
                      size="small"
                      sx={{
                        bgcolor: sevColor,
                        color: '#fff',
                        fontWeight: 600,
                      }}
                    />
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography variant="subtitle2">
                        {c.attack_combination_cluster_label || c.attack_combination_cluster_key}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {`${dim === 'asset' ? '资产' : '设备'} · ${c.attack_combination_cluster_key}`}
                      </Typography>
                    </Box>
                    <Chip
                      size="small"
                      label={`miss × ${c.attack_combination_cluster_miss_count.toLocaleString()}`}
                      color="warning"
                      variant="outlined"
                    />
                    <Chip
                      size="small"
                      label={`总数 ${c.attack_combination_cluster_total_in_cluster.toLocaleString()}`}
                      variant="outlined"
                    />
                    {c.attack_combination_cluster_severity_score !== null && (
                      <Typography variant="caption">
                        {`分数：${Number(c.attack_combination_cluster_severity_score).toFixed(3)}`}
                      </Typography>
                    )}
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={(ev) => {
                        ev.stopPropagation();
                        setDrillCluster(c);
                      }}
                    >
                      下钻
                    </Button>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails>
                  <Stack spacing={1}>
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        严重度：
                        {sevLevel}
                        {' '}
                        (
                        {sevLabel}
                        )
                        {' '}
                        | 计算时间：
                        {new Date(c.attack_combination_cluster_computed_at).toLocaleString()}
                      </Typography>
                    </Box>
                    {renderDistribution(
                      'Top 基础攻击类型分布',
                      c.attack_combination_cluster_top_base_attack_types,
                    )}
                    {renderDistribution(
                      'Top 绕过维度分布',
                      c.attack_combination_cluster_top_bypass_dimensions,
                    )}
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        前
                        {' '}
                        {PAYLOAD_PREVIEW_COUNT}
                        {' '}
                        Payload 样例
                      </Typography>
                      <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                        {(c.attack_combination_cluster_payload_samples ?? [])
                          .slice(0, PAYLOAD_PREVIEW_COUNT)
                          .map((p, idx) => (
                            <Box
                              key={idx}
                              component="code"
                              sx={{
                                p: 0.5,
                                fontSize: 11,
                                bgcolor: 'background.default',
                                whiteSpace: 'pre-wrap',
                                wordBreak: 'break-all',
                                display: 'block',
                              }}
                            >
                              {p.slice(0, 200)}
                              {p.length > 200 ? '…' : ''}
                            </Box>
                          ))}
                      </Stack>
                    </Box>
                  </Stack>
                </AccordionDetails>
              </Accordion>
            </Paper>
          );
        })}
      </Stack>

      {totalElements > PAGE_SIZE && (
        <Stack direction="row" justifyContent="center" sx={{ mt: 2 }}>
          <Pagination
            count={totalPages}
            page={page + 1}
            onChange={(_, p) => setPage(p - 1)}
            size="small"
          />
        </Stack>
      )}

      {drillCluster && (
        <PayloadDrilldown
          open
          runId={runId}
          clusterId={drillCluster.attack_combination_cluster_id}
          clusterLabel={
            drillCluster.attack_combination_cluster_label
            || drillCluster.attack_combination_cluster_key
          }
          onClose={() => setDrillCluster(null)}
        />
      )}
    </Box>
  );
};

export default ClusterTreeView;
