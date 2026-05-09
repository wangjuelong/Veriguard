import { useState } from 'react';

import { searchAttackChains } from '../../../../../../actions/attack_chains/attack_chain-actions';
import { initSorting } from '../../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { AttackChain, SearchPaginationInput } from '../../../../../../utils/api-types';
import TableData from '../ui/TableData';
import useAttackChainGrant from './useAttackChainGrant';

interface GroupManageAttackChainGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const GroupManageAttackChainGrants = ({ groupId, onGrantChange }: GroupManageAttackChainGrantsProps) => {
  const { configs } = useAttackChainGrant({
    groupId,
    onGrantChange,
  });
  const [attack_chains, setAttackChains] = useState<AttackChain[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(`group-${groupId}-attack_chains`, buildSearchPagination({ sorts: initSorting('attack_chain_updated_at', 'DESC') }));
  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchAttackChains(input).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setAttackChains}
        entityPrefix="attack_chain"
        queryableHelpers={queryableHelpers}
        disableFilters
      />
      <TableData
        datas={attack_chains}
        configs={configs}
        loading={loading}
      />
    </>
  );
};

export default GroupManageAttackChainGrants;
