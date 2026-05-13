/* eslint-disable i18next/no-literal-string -- IPv6 §3.6 ★2 攻击组合 UI 与既有
   CombinationTaskEditor / VeriguardConsole 一致使用硬编码中文；后续统一迁移 react-intl. */
import {
  Alert, Box, Button, Checkbox, Chip, CircularProgress, FormControlLabel,
  FormGroup, Stack, TextField, Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import {
  type BaseAttackTypeCategory,
  type BaseAttackTypeOutput,
  type BaseAttackTypeTargetLayer,
  listBaseAttackTypes,
} from '../../../actions/combination/combination-actions';

// 一次性拉取全部 base_attack_types（V17 共 275 类，500 足够单页）.
// 后端端点 size 上限 500（@Max(500)），与此常量一致.
const BASE_ATTACK_TYPES_PAGE_SIZE = 500;

// 9 大 category 的中文 label.
const CATEGORY_LABELS: Record<BaseAttackTypeCategory, string> = {
  web_injection: 'Web 注入',
  web_business: 'Web 业务漏洞',
  credential: '凭证攻击',
  network_protocol: '网络协议',
  c2: 'C2 / 远控',
  persistence: '持久化',
  privilege_escalation: '提权',
  discovery: '信息探测',
  exploit_cve: '高危 CVE',
};

// 4 类目标层的中文 label.
const TARGET_LAYER_LABELS: Record<BaseAttackTypeTargetLayer, string> = {
  boundary: '边界',
  traffic: '流量',
  host: '主机',
  application: '应用',
};

const TARGET_LAYERS: ReadonlyArray<BaseAttackTypeTargetLayer> = [
  'boundary',
  'traffic',
  'host',
  'application',
];

type Props = {
  selectedNames: string[];
  onSelectionChange: (names: string[]) => void;
  /** 是否启用：Dialog 关闭态可设为 false 以跳过 fetch. */
  enabled?: boolean;
};

const BaseAttackTypeSelector = ({
  selectedNames,
  onSelectionChange,
  enabled = true,
}: Props) => {
  const [allTypes, setAllTypes] = useState<BaseAttackTypeOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [layerFilters, setLayerFilters] = useState<Set<BaseAttackTypeTargetLayer>>(
    new Set(),
  );
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const result = await listBaseAttackTypes({
          page: 0,
          size: BASE_ATTACK_TYPES_PAGE_SIZE,
        });
        if (!cancelled) {
          setAllTypes(result.content);
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setLoadError(e instanceof Error ? e.message : '加载基础攻击类型失败');
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
  }, [enabled]);

  const selectedSet = useMemo(() => new Set(selectedNames), [selectedNames]);

  // 应用搜索 + target_layer 过滤.
  const filteredTypes = useMemo(() => {
    const lcSearch = searchTerm.trim().toLowerCase();
    return allTypes.filter((t) => {
      if (layerFilters.size > 0 && !layerFilters.has(t.base_attack_type_target_layer)) {
        return false;
      }
      if (lcSearch.length === 0) {
        return true;
      }
      return (
        t.base_attack_type_name.toLowerCase().includes(lcSearch)
        || t.base_attack_type_display_label.toLowerCase().includes(lcSearch)
      );
    });
  }, [allTypes, searchTerm, layerFilters]);

  // 按 category 分组.
  const typesByCategory = useMemo(() => {
    const grouped: Record<string, BaseAttackTypeOutput[]> = {};
    filteredTypes.forEach((t) => {
      const key = t.base_attack_type_category;
      if (!grouped[key]) {
        grouped[key] = [];
      }
      grouped[key].push(t);
    });
    return grouped;
  }, [filteredTypes]);

  const toggleType = (name: string) => {
    const next = new Set(selectedSet);
    if (next.has(name)) {
      next.delete(name);
    } else {
      next.add(name);
    }
    onSelectionChange(Array.from(next));
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
    const namesInCat = (typesByCategory[cat] ?? []).map(t => t.base_attack_type_name);
    const allSelected = namesInCat.every(n => selectedSet.has(n));
    const next = new Set(selectedSet);
    if (allSelected) {
      namesInCat.forEach(n => next.delete(n));
    } else {
      namesInCat.forEach(n => next.add(n));
    }
    onSelectionChange(Array.from(next));
  };

  const toggleLayerFilter = (layer: BaseAttackTypeTargetLayer) => {
    setLayerFilters((prev) => {
      const next = new Set(prev);
      if (next.has(layer)) {
        next.delete(layer);
      } else {
        next.add(layer);
      }
      return next;
    });
  };

  const selectAllVisible = () => {
    const next = new Set(selectedSet);
    filteredTypes.forEach(t => next.add(t.base_attack_type_name));
    onSelectionChange(Array.from(next));
  };

  const clearAllSelection = () => {
    onSelectionChange([]);
  };

  if (loading) {
    return (
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        <CircularProgress size={20} aria-label="loading-base-attack-types" />
        <Typography variant="caption" color="text.secondary">
          基础攻击类型加载中…
        </Typography>
      </Box>
    );
  }

  if (loadError) {
    return (
      <Alert severity="error">
        加载基础攻击类型失败：
        {loadError}
      </Alert>
    );
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="subtitle1">
          已选
          {' '}
          <Chip
            size="small"
            label={`${selectedSet.size} / ${allTypes.length}`}
            color={selectedSet.size > 0 ? 'primary' : 'default'}
          />
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button size="small" onClick={selectAllVisible}>全选当前</Button>
          <Button size="small" onClick={clearAllSelection}>清空</Button>
        </Stack>
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 2 }} alignItems="center" flexWrap="wrap">
        <TextField
          size="small"
          placeholder="搜索 name / display_label"
          value={searchTerm}
          onChange={e => setSearchTerm(e.target.value)}
          sx={{ minWidth: 240 }}
          inputProps={{ 'aria-label': 'search-base-attack-types' }}
        />
        <Stack direction="row" spacing={1} flexWrap="wrap">
          {TARGET_LAYERS.map(layer => (
            <Chip
              key={layer}
              label={`${TARGET_LAYER_LABELS[layer]} (${layer})`}
              color={layerFilters.has(layer) ? 'primary' : 'default'}
              onClick={() => toggleLayerFilter(layer)}
              variant={layerFilters.has(layer) ? 'filled' : 'outlined'}
              size="small"
              clickable
            />
          ))}
        </Stack>
      </Stack>

      {filteredTypes.length === 0 && (
        <Typography variant="body2" color="text.secondary">
          没有匹配的基础攻击类型。
        </Typography>
      )}

      {Object.entries(typesByCategory).map(([cat, types]) => {
        const allSel = types.every(t => selectedSet.has(t.base_attack_type_name));
        const someSel = types.some(t => selectedSet.has(t.base_attack_type_name));
        const expanded = expandedCategories.has(cat);
        const catLabel = CATEGORY_LABELS[cat as BaseAttackTypeCategory] ?? cat;
        const selectedInCat = types.filter(t => selectedSet.has(t.base_attack_type_name)).length;
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
                inputProps={{ 'aria-label': `category-${cat}-select-all` }}
              />
              <Typography sx={{
                flexGrow: 1,
                fontWeight: 600,
              }}
              >
                {catLabel}
                {' '}
                <Typography component="span" variant="caption" color="text.secondary">
                  (
                  {cat}
                  )
                </Typography>
                {' '}
                <Chip
                  size="small"
                  label={`${selectedInCat} / ${types.length}`}
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
                  {types.map(t => (
                    <FormControlLabel
                      key={t.base_attack_type_id}
                      control={(
                        <Checkbox
                          checked={selectedSet.has(t.base_attack_type_name)}
                          onChange={() => toggleType(t.base_attack_type_name)}
                          size="small"
                        />
                      )}
                      label={(
                        <span>
                          <code>{t.base_attack_type_name}</code>
                          {' — '}
                          {t.base_attack_type_display_label}
                          {' '}
                          <Typography variant="caption" color="text.secondary">
                            (
                            {t.base_attack_type_target_layer}
                            , severity=
                            {t.base_attack_type_severity_score}
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
  );
};

export default BaseAttackTypeSelector;
