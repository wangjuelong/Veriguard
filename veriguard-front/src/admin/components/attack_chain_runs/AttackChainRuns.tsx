import { ToggleButtonGroup } from '@mui/material';
import { useState } from 'react';

import { searchAttackChainRuns } from '../../../actions/AttackChainRun';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { type AttackChainRunSimple, type SearchPaginationInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import AttackChainRunCreation from './attack_chain_run/AttackChainRunCreation';
import AttackChainRunPopover from './attack_chain_run/AttackChainRunPopover';
import SimulationList from './AttackChainRunList';
import ImportUploaderAttackChainRun from './ImportUploaderAttackChainRun';

const Simulations = () => {
  // Standard hooks
  const { t } = useFormatter();

  const [loading, setLoading] = useState<boolean>(true);
  const [attack_chain_runs, setAttackChainRuns] = useState<AttackChainRunSimple[]>([]);

  // Filters
  const availableFilterNames = [
    'attack_chain_run_kill_chain_phases',
    'attack_chain_run_name',
    'attack_chain_run_attack_chain',
    'attack_chain_run_start_date',
    'attack_chain_run_status',
    'attack_chain_run_tags',
    'attack_chain_run_updated_at',
  ];

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('attack_chain_runs', buildSearchPagination({ sorts: initSorting('attack_chain_run_updated_at', 'DESC') }));

  // Export
  const exportProps = {
    exportType: 'attack_chain_run',
    exportKeys: [
      'attack_chain_run_name',
      'attack_chain_run_subtitle',
      'attack_chain_run_description',
      'attack_chain_run_status',
      'attack_chain_run_tags',
    ],
    exportData: attack_chain_runs,
    exportFileName: `${t('Simulations')}.csv`,
  };

  const secondaryAction = (attack_chain_run: AttackChainRunSimple) => (
    <AttackChainRunPopover
      // @ts-expect-error: should pass AttackChainRun model IF we have update as action
      attack_chain_run={attack_chain_run}
      actions={['Duplicate', 'Export', 'Delete']}
      onDelete={result => setAttackChainRuns(attack_chain_runs.filter(e => (e.attack_chain_run_id !== result)))}
      inList
    />
  );

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchAttackChainRuns(input).finally(() => {
      setLoading(false);
    });
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Simulations'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setAttackChainRuns}
        entityPrefix="attack_chain_run"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <ToggleButtonGroup value="fake" exclusive>
            <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
              <ImportUploaderAttackChainRun />
            </Can>
          </ToggleButtonGroup>
        )}
      />
      <SimulationList
        attack_chain_runs={attack_chain_runs}
        queryableHelpers={queryableHelpers}
        secondaryAction={secondaryAction}
        loading={loading}
      />
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
        <AttackChainRunCreation />
      </Can>
    </>
  );
};

export default Simulations;
