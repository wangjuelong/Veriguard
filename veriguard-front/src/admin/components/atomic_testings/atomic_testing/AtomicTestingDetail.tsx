import { Typography } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { getAttackChainNodeStatusWithGlobalExecutionTraces } from '../../../../actions/attack_chain_nodes/node-action';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type AttackChainNodeResultOverviewOutput, type AttackChainNodeStatusOutput } from '../../../../utils/api-types';
import GlobalExecutionTraces from '../../common/attack_chain_nodes/status/traces/GlobalExecutionTraces';

const AtomicTestingDetail: FunctionComponent = () => {
  const { t } = useFormatter();
  const { injectId } = useParams() as { injectId: AttackChainNodeResultOverviewOutput['node_id'] };

  const [injectStatus, setAttackChainNodeStatus] = useState<AttackChainNodeStatusOutput | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    if (injectId) {
      setLoading(true);
      getAttackChainNodeStatusWithGlobalExecutionTraces(injectId)
        .then(res => setAttackChainNodeStatus(res.data))
        .finally(() => {
          setLoading(false);
        });
    }
  }, [injectId]);

  if (loading) {
    return <Loader />;
  }

  if (!injectStatus) {
    return <Typography variant="body1">{t('No data available')}</Typography>;
  }

  return <GlobalExecutionTraces injectStatus={injectStatus} />;
};

export default AtomicTestingDetail;
