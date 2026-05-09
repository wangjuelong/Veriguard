import * as R from 'ramda';
import { type FunctionComponent, useContext } from 'react';

import { updateAtomicTesting } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { type AttackChainNode, type AttackChainNodeResultOutput, type AttackChainNodeResultOverviewOutput } from '../../../../utils/api-types';
import { EndpointContext } from '../../../../utils/context/endpoint/EndpointContext';
import endpointContextForAtomicTesting from '../../../../utils/context/endpoint/EndpointContextForAtomicTesting';
import UpdateAttackChainNode from '../../common/attack_chain_nodes/UpdateAttackChainNode';
import { AttackChainNodeResultOverviewOutputContext, type AttackChainNodeResultOverviewOutputContextType } from '../AttackChainNodeResultOverviewOutputContext';

interface Props {
  atomic: AttackChainNodeResultOutput | AttackChainNodeResultOverviewOutput;
  open: boolean;
  handleClose: () => void;
}

const AtomicTestingUpdate: FunctionComponent<Props> = ({
  atomic,
  open,
  handleClose,
}) => {
  const { updateAttackChainNodeResultOverviewOutput } = useContext<AttackChainNodeResultOverviewOutputContextType>(AttackChainNodeResultOverviewOutputContext);
  const onUpdateAtomicTesting = async (data: AttackChainNode) => {
    const toUpdate = R.pipe(
      R.pick([
        'node_tags',
        'node_title',
        'node_type',
        'node_injector_contract',
        'node_description',
        'node_content',
        'node_all_teams',
        'node_documents',
        'node_assets',
        'node_asset_groups',
        'node_teams',
        'node_tags',
      ]),
    )(data);
    updateAtomicTesting(atomic.node_id, toUpdate).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
      updateAttackChainNodeResultOverviewOutput(result.data);
    });
  };

  const endpointContext = endpointContextForAtomicTesting();
  return (
    <EndpointContext.Provider value={endpointContext}>
      <UpdateAttackChainNode
        open={open}
        handleClose={handleClose}
        onUpdateAttackChainNode={onUpdateAtomicTesting}
        injectId={atomic.node_id}
        isAtomic
      />
    </EndpointContext.Provider>
  );
};

export default AtomicTestingUpdate;
