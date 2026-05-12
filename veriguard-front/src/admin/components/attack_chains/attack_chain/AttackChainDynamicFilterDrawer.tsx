/* eslint-disable i18next/no-literal-string -- Phase 12c-Biii 二开 UI 硬编码中文，未来统一 i18n 清洗。 */
import { Box, Button, Drawer, Stack, Typography } from '@mui/material';
import { type FunctionComponent } from 'react';

import {
  type AttackChainDynamicFilterInputWire,
  type AttackChainWithDynamic,
  updateAttackChainDynamicFilter,
} from '../../../../actions/attack_chains/attack_chain-actions';
import FilterField from '../../../../components/common/queryable/filter/FilterField';
import { emptyFilterGroup } from '../../../../components/common/queryable/filter/FilterUtils';
import useFiltersState from '../../../../components/common/queryable/filter/useFiltersState';
import { useFormatter } from '../../../../components/i18n';
import { type AttackChain, type NodeContract } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

const DRAWER_WIDTH = 440;

/** NodeContract 维度筛选 key（与 useRetrieveOptions/useSearchOptions 已支持的 case 对齐）. */
const NODE_CONTRACT_FILTER_KEYS = [
  'injector_contract_attack_patterns',
  'injector_contract_kill_chain_phases',
  'injector_contract_domains',
  'injector_contract_injector',
  // PR A3：6 个新字段中 5 个 filterable（rollback_steps JSONB 不可 filter）
  'injector_contract_software_category',
  'injector_contract_defense_layer',
  'injector_contract_network_protocol_family',
  'injector_contract_target_os',
  'injector_contract_network_dependent',
];

interface Props {
  attackChain: AttackChain;
  open: boolean;
  onClose: () => void;
  /** 当前命中的动态用例列表（来自 attack_chain_dynamic_contracts；用于预览计数）. */
  previewContracts?: NodeContract[];
}

/**
 * 右侧 Drawer：编辑攻击链路动态筛选条件（Phase 12c-Biii B5 follow-up）。
 *
 * 使用 key prop 强制 remount：每次 Drawer 打开时 useFiltersState 用链路
 * 当前 attack_chain_dynamic_filter 重新初始化，无需显式 reset 调用。
 */
const AttackChainDynamicFilterDrawerInner: FunctionComponent<Props> = ({
  attackChain,
  open,
  onClose,
  previewContracts = [],
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const initial = (attackChain as AttackChainWithDynamic).attack_chain_dynamic_filter ?? emptyFilterGroup;
  const [filterGroup, helpers] = useFiltersState(initial, emptyFilterGroup);

  const handleSave = async () => {
    const input: AttackChainDynamicFilterInputWire = { dynamic_filter: filterGroup };
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

        <FilterField
          entityPrefix="injector_contract"
          availableFilterNames={NODE_CONTRACT_FILTER_KEYS}
          filterGroup={filterGroup}
          helpers={helpers}
          style={{ marginTop: 12 }}
        />

        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            display: 'block',
            marginTop: 2,
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

/**
 * Wrapper that forces full remount of the inner component (and its useFiltersState)
 * each time the drawer opens or the chain changes, so filter state always
 * initialises from the chain's persisted value rather than a stale draft.
 */
const AttackChainDynamicFilterDrawer: FunctionComponent<Props> = (props) => {
  const remountKey = `${props.attackChain.attack_chain_id}-${props.open ? 'open' : 'closed'}`;
  return (
    <AttackChainDynamicFilterDrawerInner
      key={remountKey}
      {...props}
    />
  );
};

export default AttackChainDynamicFilterDrawer;
