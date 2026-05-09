import * as R from 'ramda';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { createAtomicTesting, searchAtomicTestings } from '../../../actions/atomic_testings/atomic-testing-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ButtonCreate from '../../../components/common/ButtonCreate';
import { initSorting } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { type AtomicTestingInput, type AttackChainNodeResultOverviewOutput } from '../../../utils/api-types';
import { EndpointContext } from '../../../utils/context/endpoint/EndpointContext';
import endpointContextForAtomicTesting from '../../../utils/context/endpoint/EndpointContextForAtomicTesting';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import CreateAttackChainNode from '../common/attack_chain_nodes/CreateAttackChainNode';
import { TeamContext } from '../common/Context';
import teamContextForAtomicTesting from './atomic_testing/context/TeamContextForAtomicTesting';
import AttackChainNodeResultList from './AttackChainNodeResultList';

const AtomicTestings = () => {
  // Standard hooks
  const { t } = useFormatter();
  const navigate = useNavigate();
  const [openCreateDrawer, setOpenCreateDrawer] = useState(false);

  const onCreateAtomicTesting = async (data: AtomicTestingInput) => {
    const toCreate = R.pipe(
      R.assoc('node_tags', data.node_tags),
      R.assoc('node_title', data.node_title),
      R.assoc('node_all_teams', data.node_all_teams),
      R.assoc('node_asset_groups', data.node_asset_groups),
      R.assoc('node_assets', data.node_assets),
      R.assoc('node_content', data.node_content),
      R.assoc('node_injector_contract', data.node_injector_contract),
      R.assoc('node_description', data.node_description),
      R.assoc('node_documents', data.node_documents),
      R.assoc('node_teams', data.node_teams),
    )(data);
    await createAtomicTesting(toCreate).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
      navigate(`/admin/atomic_testings/${result.data.node_id}`);
    });
  };

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('atomic-testing', buildSearchPagination({ sorts: initSorting('node_updated_at', 'DESC') }));
  const endpointContext = endpointContextForAtomicTesting();

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Atomic testings'),
          current: true,
        }]}
      />
      <AttackChainNodeResultList
        showActions
        fetchAttackChainNodes={searchAtomicTestings}
        goTo={injectId => `/admin/atomic_testings/${injectId}`}
        queryableHelpers={queryableHelpers}
        searchPaginationInput={searchPaginationInput}
      />

      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
        <>
          <ButtonCreate onClick={() => setOpenCreateDrawer(true)} />
          <TeamContext.Provider value={teamContextForAtomicTesting()}>
            <EndpointContext.Provider value={endpointContext}>
              <CreateAttackChainNode
                title={t('Create a new atomic test')}
                onCreateAttackChainNode={onCreateAtomicTesting}
                isAtomic
                open={openCreateDrawer}
                handleClose={() => setOpenCreateDrawer(false)}
              />
            </EndpointContext.Provider>
          </TeamContext.Provider>
        </>
      </Can>
    </>
  );
};

export default AtomicTestings;
