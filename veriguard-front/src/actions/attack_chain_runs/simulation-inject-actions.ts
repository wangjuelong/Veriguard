import type { Dispatch } from 'redux';

import { getReferential, postReferential, simplePostCall } from '../../utils/Action';
import type { AttackChainRun, AttackChainNodeInput, SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import * as schema from '../Schema';

export const createAttackChainNodesForSimulation = (simulationId: AttackChainRun['attack_chain_run_id'], inputs: AttackChainNodeInput[]) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${simulationId}/nodes/bulk`;
  return postReferential(schema.arrayOfAttackChainNodes, uri, inputs)(dispatch);
};

export const fetchAttackChainRunAttackChainNodesSimple = (exerciseId: AttackChainRun['attack_chain_run_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/simple`;
  return getReferential(schema.arrayOfAttackChainNodes, uri)(dispatch);
};

export const searchAttackChainRunAttackChainNodesSimple = (exerciseId: AttackChainRun['attack_chain_run_id'], input: SearchPaginationInput) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/simple`;
  return simplePostCall(uri, input);
};

export const importAttackChainNodesForSimulation = (simulationId: AttackChainRun['attack_chain_run_id'], file: File) => {
  const uri = `/api/attack_chain_runs/${simulationId}/nodes/import`;
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(uri, formData).catch((error) => {
    MESSAGING$.notifyError('Could not import nodes');
    throw error;
  });
};
