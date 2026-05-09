import { type Dispatch } from 'redux';

import { getReferential } from '../../utils/Action';
import { arrayOfDocuments } from '../Schema';

// -- EXERCISES --

export const fetchAttackChainRunDocuments = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/documents`;
  return getReferential(arrayOfDocuments, uri)(dispatch);
};

// -- SCENARIOS --

export const fetchAttackChainDocuments = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/documents`;
  return getReferential(arrayOfDocuments, uri)(dispatch);
};
