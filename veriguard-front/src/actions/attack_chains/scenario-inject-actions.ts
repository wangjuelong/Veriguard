import type { Dispatch } from 'redux';

import { getReferential, postReferential, simplePostCall } from '../../utils/Action';
import type { AttackChainNodeInput, AttackChain, SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import * as schema from '../Schema';

export const createAttackChainNodesForAttackChain = (scenarioId: AttackChain['attack_chain_id'], inputs: AttackChainNodeInput[]) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes/bulk`;
  return postReferential(schema.arrayOfAttackChainNodes, uri, inputs)(dispatch);
};

export const fetchAttackChainAttackChainNodesSimple = (scenarioId: AttackChain['attack_chain_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes/simple`;
  return getReferential(schema.arrayOfAttackChainNodes, uri)(dispatch);
};

export const searchAttackChainAttackChainNodesSimple = (scenarioId: AttackChain['attack_chain_id'], input: SearchPaginationInput) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes/simple`;
  return simplePostCall(uri, input);
};

export const importAttackChainNodesForAttackChain = (scenarioId: AttackChain['attack_chain_id'], file: File) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes/import`;
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(uri, formData).catch((error) => {
    MESSAGING$.notifyError('Could not import nodes');
    throw error;
  });
};
