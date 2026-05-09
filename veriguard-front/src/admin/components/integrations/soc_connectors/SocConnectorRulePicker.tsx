/* eslint-disable i18next/no-literal-string -- Phase 11 二开 UI 硬编码中文，未来 Phase 12 i18n 清洗。 */
import { Search as SearchIcon } from '@mui/icons-material';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  InputAdornment,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import { type SocConnectorRuleOption } from './socConnectorTypes';

interface Props {
  open: boolean;
  /** 当前 connector ID（仅展示用，不参与过滤）. */
  connectorId: string;
  /** Connector 友好名（标题展示）. */
  connectorDisplayName: string;
  /** 可选规则集合（来自 connector.listAvailableRules()）. */
  rules: SocConnectorRuleOption[];
  /** 当 connector 离线 / 拉取失败时传入；非空时只显示提示，禁用选择. */
  loadError?: string;
  /** 已经选过的 rule_id（在列表里高亮）. */
  initialSelectedRuleId?: string;
  onCancel: () => void;
  /** 选定 → 调用方拿 rule 转 SocCorrelationRuleRef 落库. */
  onSelect: (rule: SocConnectorRuleOption) => void;
}

/**
 * SOC Connector 规则选择器（spec §6.2 复用组件）.
 *
 * 复用场景：
 * - AttackChainSettingsDrawer 配链路级 SOC 规则
 * - ParameterSetEditDialog 配默认 SOC 规则
 *
 * 行为：
 * - 渲染给定规则列表 + 顶部搜索框（按 displayName / ruleId / category 模糊匹配）
 * - 点击行 → onSelect(rule)；调用方应转成 SocCorrelationRuleRef 并 close
 * - loadError 非空 → 禁用列表 + 显示错误 Alert（不静默 fallback，spec §3.6 失败显式）
 */
const SocConnectorRulePicker = ({
  open,
  connectorId,
  connectorDisplayName,
  rules,
  loadError,
  initialSelectedRuleId,
  onCancel,
  onSelect,
}: Props) => {
  const [query, setQuery] = useState('');

  useEffect(() => {
    if (open) {
      setQuery('');
    }
  }, [open]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return rules;
    }
    return rules.filter(r =>
      r.displayName.toLowerCase().includes(q)
      || r.ruleId.toLowerCase().includes(q)
      || (r.category ?? '').toLowerCase().includes(q),
    );
  }, [rules, query]);

  return (
    <Dialog open={open} onClose={onCancel} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Stack spacing={0.5}>
          <Typography variant="h6">选择 SOC 规则</Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            <Chip label={connectorId} size="small" variant="outlined" />
            <Typography variant="body2" color="text.secondary">
              {connectorDisplayName}
            </Typography>
          </Stack>
        </Stack>
      </DialogTitle>
      <DialogContent dividers>
        {loadError && (
          <Alert severity="error" sx={{ mb: 2 }} data-testid="soc-rule-picker-error">
            {loadError}
          </Alert>
        )}
        {!loadError && (
          <TextField
            fullWidth
            size="small"
            placeholder="搜索规则名 / ID / 分类..."
            value={query}
            onChange={e => setQuery(e.target.value)}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              },
            }}
            sx={{ mb: 1.5 }}
            data-testid="soc-rule-picker-search"
          />
        )}
        {!loadError && filtered.length === 0 && (
          <Box sx={{
            p: 2,
            textAlign: 'center',
          }}
          >
            <Typography variant="body2" color="text.secondary">
              {rules.length === 0
                ? '该 connector 没有返回任何规则。请检查 SOC 平台配置或 listAvailableRules 实现。'
                : `未找到匹配「${query}」的规则。`}
            </Typography>
          </Box>
        )}
        {!loadError && filtered.length > 0 && (
          <List dense disablePadding data-testid="soc-rule-picker-list">
            {filtered.map(rule => (
              <ListItemButton
                key={rule.ruleId}
                data-testid={`soc-rule-${rule.ruleId}`}
                selected={rule.ruleId === initialSelectedRuleId}
                onClick={() => onSelect(rule)}
                sx={{
                  borderBottom: 1,
                  borderColor: 'divider',
                }}
              >
                <ListItemText
                  primary={(
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>
                        {rule.displayName}
                      </Typography>
                      {rule.category && (
                        <Chip label={rule.category} size="small" variant="outlined" />
                      )}
                    </Stack>
                  )}
                  secondary={(
                    <Typography variant="caption" color="text.secondary">
                      ID:
                      {' '}
                      {rule.ruleId}
                      {rule.description && ` · ${rule.description}`}
                    </Typography>
                  )}
                />
              </ListItemButton>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>取消</Button>
      </DialogActions>
    </Dialog>
  );
};

export default SocConnectorRulePicker;
