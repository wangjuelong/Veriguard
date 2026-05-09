import { useEffect, useState } from 'react';

import type { AttackChainNodeResultPayloadExecutionOutput, AttackChainNodeTarget } from '../../utils/api-types';
import { fetchAttackChainNodeExecutionResult } from './node-status-action';

const useFetchAttackChainNodeExecutionResult = (injectId: string, target: AttackChainNodeTarget) => {
  const [injectExecutionResult, setAttackChainNodeExecutionResult] = useState<AttackChainNodeResultPayloadExecutionOutput>();
  const [loading, setLoading] = useState(false);
  const fetch = async () => {
    if (!injectId || !target?.target_id || !target.target_type) return;
    setLoading(true);
    try {
      const result = await fetchAttackChainNodeExecutionResult(injectId, target.target_id, target.target_type);
      setAttackChainNodeExecutionResult(result.data || undefined);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    fetch();
  }, [injectId, target?.target_id, target?.target_type]);
  return ({
    injectExecutionResult,
    loading,
  });
};

export default useFetchAttackChainNodeExecutionResult;
