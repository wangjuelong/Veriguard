import { Alert, AlertTitle } from '@mui/material';
import { Suspense, useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { interval } from 'rxjs';

import { fetchAttackChainNodeResultOverviewOutput } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type AttackChainNodeResultOverviewOutput } from '../../../../utils/api-types';
import { FIVE_SECONDS } from '../../../../utils/Time';
import { TeamContext } from '../../common/Context';
import { AttackChainNodeResultOverviewOutputContext } from '../AttackChainNodeResultOverviewOutputContext';
import AtomicTestingHeader from './AtomicTestingHeader';
import AtomicTestingRoutes from './AtomicTestingRoutes';
import teamContextForAtomicTesting from './context/TeamContextForAtomicTesting';

const interval$ = interval(FIVE_SECONDS);

const Index = () => {
  const { t } = useFormatter();
  const { injectId } = useParams() as { injectId: AttackChainNodeResultOverviewOutput['node_id'] };

  const [pristine, setPristine] = useState(true);
  const [loading, setLoading] = useState(true);
  const [injectResultOverviewOutput, setAttackChainNodeResultOverviewOutput] = useState<AttackChainNodeResultOverviewOutput>();

  useEffect(() => {
    setLoading(true);
    fetchAttackChainNodeResultOverviewOutput(injectId).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
      setAttackChainNodeResultOverviewOutput(result.data);
    }).finally(() => {
      setLoading(false);
      setPristine(false);
    });
  }, [injectId]);

  useEffect(() => {
    const subscription = interval$.subscribe(() => {
      setLoading(true);
      fetchAttackChainNodeResultOverviewOutput(injectId).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
        if (result.data.node_updated_at !== injectResultOverviewOutput?.node_updated_at) {
          setAttackChainNodeResultOverviewOutput(result.data);
        }
      }).catch(() => {
        subscription.unsubscribe();
      }).finally(() => {
        setLoading(false);
        setPristine(false);
      });
    });
    return () => {
      subscription.unsubscribe();
    };
  }, [injectResultOverviewOutput]);

  const updateAttackChainNodeResultOverviewOutput = () => {
    fetchAttackChainNodeResultOverviewOutput(injectId).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
      setAttackChainNodeResultOverviewOutput(result.data);
    });
  };

  if (pristine && loading) return <Loader />;

  if (!injectResultOverviewOutput) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Atomic testing is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }

  return (
    <TeamContext.Provider value={teamContextForAtomicTesting()}>
      <AttackChainNodeResultOverviewOutputContext.Provider value={{
        injectResultOverviewOutput,
        updateAttackChainNodeResultOverviewOutput,
      }}
      >
        <AtomicTestingHeader injectResultOverview={injectResultOverviewOutput} setAttackChainNodeResultOverview={setAttackChainNodeResultOverviewOutput} />
        <Suspense fallback={<Loader />}>
          <AtomicTestingRoutes injectResultOverview={injectResultOverviewOutput} />
        </Suspense>
      </AttackChainNodeResultOverviewOutputContext.Provider>
    </TeamContext.Provider>
  );
};

export default Index;
