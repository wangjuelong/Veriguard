/* eslint-disable i18next/no-literal-string -- Phase 12c-Biii 二开 UI 硬编码中文，未来统一 i18n 清洗。 */
import { Box, Button, Drawer, Stack, Typography } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import {
  type AttackChainDynamicFilterInputWire,
  type AttackChainWithDynamic,
  type FilterGroupWire,
  updateAttackChainDynamicFilter,
} from '../../../../actions/attack_chains/attack_chain-actions';
import { useFormatter } from '../../../../components/i18n';
import { type AttackChain, type NodeContract } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

/** 默认空 FilterGroup（与后端约定：filters 为空 → 无过滤 → 不返回动态用例）. */
const EMPTY_FILTER_GROUP: FilterGroupWire = {
  mode: 'and',
  filters: [],
};

const DRAWER_WIDTH = 440;

interface Props {
  attackChain: AttackChain;
  open: boolean;
  onClose: () => void;
  /** 当前命中的动态用例列表（来自 attack_chain_dynamic_contracts；用于预览计数）. */
  previewContracts?: NodeContract[];
}

/**
 * 右侧 Drawer：编辑攻击链路动态筛选条件（Phase 12c-Biii B5）。
 *
 * FilterGroup 编辑器集成点：
 *   目前以 JSON 调试视图（<pre>）展示当前 draftFilter，并提供"添加条件"占位。
 *   后续集成 OpenBAS FilterField / DynamicAssetField 时，替换 TODO 区块即可：
 *     import FilterField from '../../../../components/common/queryable/filter/FilterField';
 *     import useFiltersState from '../../../../components/common/queryable/filter/useFiltersState';
 *     然后将 draftFilter / setDraftFilter 换成 useFiltersState，并将 FilterGroupWire
 *     与 api-types FilterGroup 做互转（注意 Filter.values 可选 vs FilterWire.values 必填）。
 */
const AttackChainDynamicFilterDrawer: FunctionComponent<Props> = ({
  attackChain,
  open,
  onClose,
  previewContracts = [],
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [draftFilter, setDraftFilter] = useState<FilterGroupWire>(EMPTY_FILTER_GROUP);

  // 每次 Drawer 打开时重置 draftFilter 为链路当前值（或空）
  useEffect(() => {
    if (open) {
      const existing = (attackChain as AttackChainWithDynamic).attack_chain_dynamic_filter;
      setDraftFilter(existing ?? EMPTY_FILTER_GROUP);
    }
  }, [open, attackChain]);

  const handleSave = async () => {
    const input: AttackChainDynamicFilterInputWire = { dynamic_filter: draftFilter };
    await dispatch(updateAttackChainDynamicFilter(attackChain.attack_chain_id, input));
    onClose();
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ style: { width: DRAWER_WIDTH } }}
    >
      <Box sx={{
        padding: 3,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <Typography variant="h5" sx={{ marginBottom: 2 }}>
          {t('Edit dynamic filter')}
        </Typography>

        {/* ── FilterGroup 编辑器集成点 ──────────────────────────────────────────
         *  TODO (B5 follow-up)：将下方 JSON 调试视图替换为 FilterField / DynamicAssetField：
         *    <DynamicAssetField
         *      value={toApiFilterGroup(draftFilter)}
         *      onChange={(v) => setDraftFilter(toFilterGroupWire(v))}
         *      contextId={attackChain.attack_chain_id}
         *    />
         *  其中 toApiFilterGroup / toFilterGroupWire 做 nullable fields 互转。
         * ─────────────────────────────────────────────────────────────────────── */}
        <Box
          sx={{
            flex: 1,
            marginBottom: 2,
            padding: 2,
            background: 'rgba(0,0,0,0.04)',
            borderRadius: 1,
            overflow: 'auto',
          }}
        >
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              display: 'block',
              marginBottom: 1,
            }}
          >
            筛选条件（
            {t('Filter mode')}
            ：
            {draftFilter.mode}
            ）
          </Typography>
          {draftFilter.filters.length === 0 ? (
            <Typography variant="body2" color="text.disabled">
              {t('No dynamic contracts')}
            </Typography>
          ) : (
            <pre
              style={{
                margin: 0,
                fontSize: 11,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
              }}
            >
              {JSON.stringify(draftFilter.filters, null, 2)}
            </pre>
          )}
        </Box>

        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            display: 'block',
            marginBottom: 2,
          }}
        >
          {t('{n} contracts match', { n: previewContracts.length })}
        </Typography>

        <Stack direction="row" spacing={1} justifyContent="flex-end">
          <Button onClick={onClose}>{t('Cancel')}</Button>
          <Button variant="contained" onClick={handleSave}>{t('Save')}</Button>
        </Stack>
      </Box>
    </Drawer>
  );
};

export default AttackChainDynamicFilterDrawer;
