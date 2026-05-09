import { simpleCall } from '../../utils/Action';
import type { AttackChainNode } from '../../utils/api-types';
import { INJECT_URI } from '../attack_chain_nodes/node-action';

// eslint-disable-next-line import/prefer-default-export
export const fetchAttackChainNodeExecutionResult = (injectId: AttackChainNode['node_id'], targetId: string = '', targetType: string = '') => {
  const params = {
    targetId,
    targetType,
  };
  const uri = `${INJECT_URI}/${injectId}/execution-result`;
  return simpleCall(uri, { params });
};
