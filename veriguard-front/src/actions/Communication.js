import { getReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchAttackChainRunCommunications = exerciseId => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/communications`;
  return getReferential(schema.arrayOfCommunications, uri)(dispatch);
};

export const fetchAttackChainNodeCommunications = (exerciseId, injectId) => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/${injectId}/communications`;
  return getReferential(schema.arrayOfCommunications, uri)(dispatch);
};
