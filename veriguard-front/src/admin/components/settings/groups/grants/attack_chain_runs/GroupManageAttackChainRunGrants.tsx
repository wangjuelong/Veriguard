import { useState } from 'react';

import { searchAttackChainRuns } from '../../../../../../actions/AttackChainRun';
import { initSorting } from '../../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { AttackChainRun, SearchPaginationInput } from '../../../../../../utils/api-types';
import TableData from '../ui/TableData';
import useSimulationGrant from './useAttackChainRunGrant';

interface GGroupManageSimulationGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const GroupManageSimulationGrants = ({ groupId, onGrantChange }: GGroupManageSimulationGrantsProps) => {
  const { configs } = useSimulationGrant({
    groupId,
    onGrantChange,
  });
  const [attack_chain_runs, setSimulations] = useState<AttackChainRun[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`group-${groupId}-attack_chain_runs`, buildSearchPagination({ sorts: initSorting('attack_chain_run_updated_at', 'DESC') }));
  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchAttackChainRuns(input).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setSimulations}
        entityPrefix="attack_chain_run"
        queryableHelpers={queryableHelpers}
        disableFilters
      />
      <TableData
        datas={attack_chain_runs}
        configs={configs}
        loading={loading}
      />
    </>
  );
};

export default GroupManageSimulationGrants;
