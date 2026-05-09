import { type Dispatch } from 'redux';

import { putReferential, simplePostCall } from '../../utils/Action';
import { type AttackChain, type AttackChainRunUpdateTeamsInput, type SearchPaginationInput } from '../../utils/api-types';
import * as schema from '../Schema';
import { EXERCISE_URI } from './attack_chain_run-action';

export const searchAttackChainRunTeams = (exerciseId: AttackChain['attack_chain_id'], paginationInput: SearchPaginationInput, contextualOnly: boolean = false) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/teams/search?contextualOnly=${contextualOnly}`;
  return simplePostCall(uri, paginationInput);
};

export const removeAttackChainRunTeams = (exerciseId: AttackChain['attack_chain_id'], data: AttackChainRunUpdateTeamsInput) => (dispatch: Dispatch) => putReferential(
  schema.arrayOfTeams,
  `${EXERCISE_URI}/${exerciseId}/teams/remove`,
  data,
)(dispatch);

export const replaceAttackChainRunTeams = (exerciseId: AttackChain['attack_chain_id'], data: AttackChainRunUpdateTeamsInput) => (dispatch: Dispatch) => putReferential(
  schema.arrayOfTeams,
  `${EXERCISE_URI}/${exerciseId}/teams/replace`,
  data,
)(dispatch);
