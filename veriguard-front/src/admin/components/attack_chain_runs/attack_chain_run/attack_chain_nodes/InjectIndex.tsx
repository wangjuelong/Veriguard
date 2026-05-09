import { type FunctionComponent, Suspense, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchAttackChainNodeResultOverviewOutput } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { type AttackChainRunsHelper } from '../../../../../actions/attack_chain_runs/attack_chain_run-helper';
import { fetchAttackChainRun } from '../../../../../actions/AttackChainRun';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type AttackChainRun as AttackChainRunType, type AttackChainNodeResultOverviewOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { INHERITED_CONTEXT } from '../../../../../utils/permissions/types';
import useSimulationPermissions from '../../../../../utils/permissions/useSimulationPermissions';
import AtomicTestingRoutes from '../../../atomic_testings/atomic_testing/AtomicTestingRoutes';
import { AttackChainNodeResultOverviewOutputContext } from '../../../atomic_testings/AttackChainNodeResultOverviewOutputContext';
import { PermissionsContext, type PermissionsContextType } from '../../../common/Context';
import AttackChainNodeIndexHeader from './AttackChainNodeIndexHeader';

const AttackChainNodeIndexComponent: FunctionComponent<{
  attack_chain_run: AttackChainRunType;
  injectResult: AttackChainNodeResultOverviewOutput;
}> = ({
  attack_chain_run,
  injectResult,
}) => {
  const permissionsContext: PermissionsContextType = {
    permissions: useSimulationPermissions(attack_chain_run.attack_chain_run_id, attack_chain_run),
    inherited_context: INHERITED_CONTEXT.SIMULATION,
  };

  const [injectResultOverviewOutput, setAttackChainNodeResultOverviewOutput] = useState<AttackChainNodeResultOverviewOutput>(injectResult);

  const updateAttackChainNodeResultOverviewOutput = (newData: AttackChainNodeResultOverviewOutput) => {
    setAttackChainNodeResultOverviewOutput(newData);
  };

  return (
    <AttackChainNodeResultOverviewOutputContext.Provider value={{
      injectResultOverviewOutput,
      updateAttackChainNodeResultOverviewOutput,
    }}
    >
      <PermissionsContext.Provider value={permissionsContext}>
        <AttackChainNodeIndexHeader injectResultOverview={injectResultOverviewOutput} attack_chain_run={attack_chain_run} />
        <Suspense fallback={<Loader />}>
          <AtomicTestingRoutes injectResultOverview={injectResultOverviewOutput} />
        </Suspense>
      </PermissionsContext.Provider>
    </AttackChainNodeResultOverviewOutputContext.Provider>
  );
};

const AttackChainNodeIndex = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: AttackChainRunType['attack_chain_run_id'] };
  const { injectId } = useParams() as { injectId: AttackChainNodeResultOverviewOutput['node_id'] };
  const attack_chain_run = useHelper((helper: AttackChainRunsHelper) => helper.getAttackChainRun(exerciseId));
  useDataLoader(() => {
    dispatch(fetchAttackChainRun(exerciseId));
  });
  const [injectResultOutput, setAttackChainNodeResultOverviewOutput] = useState<AttackChainNodeResultOverviewOutput>();

  useEffect(() => {
    fetchAttackChainNodeResultOverviewOutput(injectId).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
      setAttackChainNodeResultOverviewOutput(result.data);
    });
  }, [injectId]);

  if (attack_chain_run && injectResultOutput) {
    return <AttackChainNodeIndexComponent attack_chain_run={attack_chain_run} injectResult={injectResultOutput} />;
  }
  return <Loader />;
};

export default AttackChainNodeIndex;
