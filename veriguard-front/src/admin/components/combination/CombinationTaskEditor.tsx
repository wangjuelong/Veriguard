/* eslint-disable i18next/no-literal-string -- IPv6 §3.6 ★2 攻击组合 UI 首发与既有
   VeriguardConsole / StabilityTrendView 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent,
  DialogTitle, FormControl, FormControlLabel, FormGroup, InputLabel, MenuItem,
  Select, Slider, Stack, TextField, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import {
  type AttackCombinationRunOutput,
  type BypassDimensionOutput,
  createCombinationRun,
  fetchBypassDimensions,
} from '../../../actions/combination/combination-actions';
import BaseAttackTypeSelector from './BaseAttackTypeSelector';

const RATE_LIMIT_MIN = 25;
const RATE_LIMIT_MAX = 500;
const RATE_LIMIT_DEFAULT = 100;
const CONCURRENCY_MIN = 4;
const CONCURRENCY_MAX = 64;
const CONCURRENCY_DEFAULT = 16;
const TIMEOUT_HOURS_MIN = 1;
const TIMEOUT_HOURS_MAX = 72;
const TIMEOUT_HOURS_DEFAULT = 24;
const MAX_RETRIES_DEFAULT = 3;
const MAX_RESULTS_WARNING_THRESHOLD = 300_000;
// 一次性拉取全部维度页（V11 共 120 维度，500 足够单页）.
const DIMENSIONS_PAGE_SIZE = 500;

type Props = {
  open: boolean;
  onCancel: () => void;
  onCreated: (run: AttackCombinationRunOutput) => void;
};

const CombinationTaskEditor = ({ open, onCancel, onCreated }: Props) => {
  const [name, setName] = useState<string>('');
  const [baseTypes, setBaseTypes] = useState<string[]>([]);
  const [assetIdsRaw, setAssetIdsRaw] = useState<string>('');
  const [dimensions, setDimensions] = useState<BypassDimensionOutput[]>([]);
  const [selectedDimensions, setSelectedDimensions] = useState<Set<string>>(new Set());
  const [rateLimit, setRateLimit] = useState<number>(RATE_LIMIT_DEFAULT);
  const [concurrency, setConcurrency] = useState<number>(CONCURRENCY_DEFAULT);
  const [timeoutHours, setTimeoutHours] = useState<number>(TIMEOUT_HOURS_DEFAULT);
  const [maxRetries, setMaxRetries] = useState<number>(MAX_RETRIES_DEFAULT);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingDims, setLoadingDims] = useState<boolean>(false);
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set());

  useEffect(() => {
    let cancelled = false;
    if (open) {
      const load = async () => {
        setLoadingDims(true);
        setError(null);
        try {
          const result = await fetchBypassDimensions({
            page: 0,
            size: DIMENSIONS_PAGE_SIZE,
          });
          if (cancelled) {
            return;
          }
          setDimensions(result.content);
          // 默认全选 120 维度
          setSelectedDimensions(new Set(result.content.map(d => d.bypass_dimension_id)));
        } catch (e: unknown) {
          if (!cancelled) {
            setError(e instanceof Error ? e.message : '加载绕过维度失败');
          }
        } finally {
          if (!cancelled) {
            setLoadingDims(false);
          }
        }
      };
      void load();
    }
    return () => {
      cancelled = true;
    };
  }, [open]);

  const dimensionsByCategory = useMemo(() => {
    const grouped: Record<string, BypassDimensionOutput[]> = {};
    dimensions.forEach((d) => {
      const key = d.bypass_dimension_category;
      if (!grouped[key]) {
        grouped[key] = [];
      }
      grouped[key].push(d);
    });
    return grouped;
  }, [dimensions]);

  const assetIds = useMemo(
    () => assetIdsRaw
      .split(/[\s,，；;]+/)
      .map(s => s.trim())
      .filter(s => s.length > 0),
    [assetIdsRaw],
  );

  const totalCombinations = baseTypes.length * selectedDimensions.size;
  const totalResults = totalCombinations * assetIds.length;

  const toggleDimension = (id: string) => {
    setSelectedDimensions((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const toggleCategoryExpanded = (cat: string) => {
    setExpandedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(cat)) {
        next.delete(cat);
      } else {
        next.add(cat);
      }
      return next;
    });
  };

  const toggleCategorySelectAll = (cat: string) => {
    const idsInCat = (dimensionsByCategory[cat] ?? []).map(d => d.bypass_dimension_id);
    const allSelected = idsInCat.every(id => selectedDimensions.has(id));
    setSelectedDimensions((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        idsInCat.forEach(id => next.delete(id));
      } else {
        idsInCat.forEach(id => next.add(id));
      }
      return next;
    });
  };

  const selectAllDimensions = () => {
    setSelectedDimensions(new Set(dimensions.map(d => d.bypass_dimension_id)));
  };
  const clearAllDimensions = () => {
    setSelectedDimensions(new Set());
  };

  const isValid = (
    name.trim().length > 0
    && baseTypes.length > 0
    && selectedDimensions.size > 0
    && assetIds.length > 0
  );

  const handleSubmit = async () => {
    if (!isValid) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const run = await createCombinationRun({
        name: name.trim(),
        base_attack_types: baseTypes,
        bypass_dimension_ids: Array.from(selectedDimensions),
        asset_ids: assetIds,
        rate_limit_per_second: rateLimit,
        concurrency,
        max_retries: maxRetries,
        timeout_hours: timeoutHours,
      });
      onCreated(run);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '创建任务失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="lg" fullWidth>
      <DialogTitle>新建攻击组合任务</DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>
          §3.6 ★2 攻击组合：笛卡尔积 = 基础攻击类型 × 绕过维度 × 资产。任务自动派发到执行器并入库结果，
          完成后自动聚类（资产/设备双视角）+ 三因子分级评分。
        </Alert>

        <Stack spacing={3}>
          <TextField
            label="任务名称"
            value={name}
            onChange={e => setName(e.target.value)}
            fullWidth
            required
            placeholder="如：2026Q2-IPv6 边界设备组合扫描"
          />

          <Box>
            <Typography variant="subtitle1" gutterBottom>
              基础攻击类型
              {' '}
              <span style={{ color: 'red' }}>*</span>
              {' '}
              <Typography component="span" variant="caption" color="text.secondary">
                （从 base_attack_types 库动态加载，覆盖 ≥ 250 类）
              </Typography>
            </Typography>
            <BaseAttackTypeSelector
              selectedNames={baseTypes}
              onSelectionChange={setBaseTypes}
              enabled={open}
            />
          </Box>

          <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
              <Typography variant="subtitle1">
                绕过维度
                {' '}
                <span style={{ color: 'red' }}>*</span>
                {' '}
                <Chip
                  size="small"
                  label={`已选 ${selectedDimensions.size} / ${dimensions.length}`}
                  color={selectedDimensions.size > 0 ? 'primary' : 'default'}
                />
              </Typography>
              <Stack direction="row" spacing={1}>
                <Button size="small" onClick={selectAllDimensions} disabled={loadingDims}>全选</Button>
                <Button size="small" onClick={clearAllDimensions} disabled={loadingDims}>清空</Button>
              </Stack>
            </Stack>
            {loadingDims && (
              <Typography variant="caption" color="text.secondary">维度加载中…</Typography>
            )}
            {!loadingDims && Object.entries(dimensionsByCategory).map(([cat, dims]) => {
              const allSel = dims.every(d => selectedDimensions.has(d.bypass_dimension_id));
              const someSel = dims.some(d => selectedDimensions.has(d.bypass_dimension_id));
              const expanded = expandedCategories.has(cat);
              return (
                <Box
                  key={cat}
                  sx={{
                    mb: 1,
                    border: '1px solid #ddd',
                    borderRadius: 1,
                  }}
                >
                  <Stack
                    direction="row"
                    alignItems="center"
                    sx={{
                      p: 1,
                      bgcolor: 'background.default',
                    }}
                  >
                    <Checkbox
                      checked={allSel}
                      indeterminate={!allSel && someSel}
                      onChange={() => toggleCategorySelectAll(cat)}
                    />
                    <Typography sx={{
                      flexGrow: 1,
                      fontWeight: 600,
                    }}
                    >
                      {cat}
                      {' '}
                      <Chip
                        size="small"
                        label={`${dims.filter(d => selectedDimensions.has(d.bypass_dimension_id)).length} / ${dims.length}`}
                      />
                    </Typography>
                    <Button size="small" onClick={() => toggleCategoryExpanded(cat)}>
                      {expanded ? '折叠' : '展开'}
                    </Button>
                  </Stack>
                  {expanded && (
                    <Box sx={{
                      p: 1,
                      pl: 4,
                    }}
                    >
                      <FormGroup>
                        {dims.map(d => (
                          <FormControlLabel
                            key={d.bypass_dimension_id}
                            control={(
                              <Checkbox
                                checked={selectedDimensions.has(d.bypass_dimension_id)}
                                onChange={() => toggleDimension(d.bypass_dimension_id)}
                                size="small"
                              />
                            )}
                            label={(
                              <span>
                                <code>{d.bypass_dimension_id}</code>
                                {' — '}
                                {d.bypass_dimension_name}
                                {' '}
                                <Typography variant="caption" color="text.secondary">
                                  (
                                  {d.bypass_dimension_transform_type}
                                  )
                                </Typography>
                              </span>
                            )}
                          />
                        ))}
                      </FormGroup>
                    </Box>
                  )}
                </Box>
              );
            })}
          </Box>

          <TextField
            label="目标资产 ID（一行/逗号分隔多个）"
            multiline
            minRows={2}
            maxRows={6}
            fullWidth
            required
            value={assetIdsRaw}
            onChange={e => setAssetIdsRaw(e.target.value)}
            placeholder="asset-xxx, asset-yyy 或换行分隔"
            helperText={`已识别 ${assetIds.length} 个资产 ID`}
          />

          <Stack direction="row" spacing={4}>
            <Box sx={{ flex: 1 }}>
              <Typography variant="body2">
                速率上限（req/s）：
                {' '}
                <strong>{rateLimit}</strong>
              </Typography>
              <Slider
                value={rateLimit}
                min={RATE_LIMIT_MIN}
                max={RATE_LIMIT_MAX}
                step={5}
                marks
                onChange={(_, v) => setRateLimit(v as number)}
              />
            </Box>
            <Box sx={{ flex: 1 }}>
              <Typography variant="body2">
                并发：
                {' '}
                <strong>{concurrency}</strong>
              </Typography>
              <Slider
                value={concurrency}
                min={CONCURRENCY_MIN}
                max={CONCURRENCY_MAX}
                step={2}
                marks
                onChange={(_, v) => setConcurrency(v as number)}
              />
            </Box>
          </Stack>

          <Stack direction="row" spacing={4}>
            <TextField
              label="最大重试次数"
              type="number"
              value={maxRetries}
              onChange={e => setMaxRetries(Math.max(0, Number(e.target.value)))}
              sx={{ width: 180 }}
              inputProps={{
                min: 0,
                max: 10,
              }}
            />
            <FormControl sx={{ width: 220 }}>
              <InputLabel>任务超时（小时）</InputLabel>
              <Select
                label="任务超时（小时）"
                value={timeoutHours}
                onChange={e => setTimeoutHours(Number(e.target.value))}
              >
                {[1, 6, 12, 24, 36, 48, 72]
                  .filter(h => h >= TIMEOUT_HOURS_MIN && h <= TIMEOUT_HOURS_MAX)
                  .map(h => (
                    <MenuItem key={h} value={h}>{`${h} 小时`}</MenuItem>
                  ))}
              </Select>
            </FormControl>
          </Stack>

          <Box sx={{
            p: 2,
            bgcolor: 'background.default',
            borderRadius: 1,
          }}
          >
            <Typography variant="subtitle2">实时预估</Typography>
            <Typography variant="body2">
              组合数（base × dim）：
              {' '}
              <strong>{totalCombinations.toLocaleString()}</strong>
            </Typography>
            <Typography variant="body2">
              结果总数（组合 × 资产）：
              {' '}
              <strong>{totalResults.toLocaleString()}</strong>
            </Typography>
            {totalResults > MAX_RESULTS_WARNING_THRESHOLD && (
              <Alert severity="warning" sx={{ mt: 1 }}>
                结果总数超过
                {` ${MAX_RESULTS_WARNING_THRESHOLD.toLocaleString()} `}
                行：建议拆分资产分批或缩小维度范围；否则任务运行时间可能超过超时阈值。
              </Alert>
            )}
          </Box>

          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={submitting}>取消</Button>
        <Button
          variant="contained"
          onClick={() => { void handleSubmit(); }}
          disabled={!isValid || submitting}
        >
          {submitting ? '创建中…' : '创建并启动'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default CombinationTaskEditor;
